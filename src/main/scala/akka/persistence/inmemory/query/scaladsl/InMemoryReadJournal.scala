/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.inmemory
package query
package scaladsl

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ ActorRef, ExtendedActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import akka.pattern.ask
import akka.persistence.{ Persistence, PersistentRepr }
import akka.persistence.inmemory.extension.{ InMemoryJournalStorage, StorageExtension }
import akka.persistence.inmemory.util.UUIDs
import akka.persistence.query._
import akka.persistence.query.scaladsl._
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.typesafe.config.Config

import scala.collection.immutable.{ Iterable, Seq }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal(config: Config, journal: ActorRef)(implicit val system: ExtendedActorSystem) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with PersistenceIdsQuery
  with CurrentEventsByPersistenceIdQuery
  with EventsByPersistenceIdQuery
  with CurrentEventsByTagQuery
  with EventsByTagQuery {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: Materializer = ActorMaterializer()
  private implicit val log: LoggingAdapter = Logging(system, this.getClass)
  private val serialization = SerializationExtension(system)
  private val offsetMode: String = config.getString("offset-mode").toLowerCase()
  private implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.MILLISECONDS) -> MILLISECONDS)
  private val refreshInterval: FiniteDuration = config.getDuration("refresh-interval", TimeUnit.MILLISECONDS) -> MILLISECONDS
  private val maxBufferSize: Int = Try(config.getString("max-buffer-size").toInt).getOrElse(config.getInt("max-buffer-size"))

  // As event adapters are *no* first class citizins in akka-persistence-query
  // this workaround has to be implemented.
  // see akka ticket: #18050 and #21065
  // and akka-persistence-cassandra ticket: #116
  //
  // basically registering the used write-plugin in the inmemory-read-journal configuration section
  // then looking up that plugin-id and getting configured event adapters for that write plugin id
  // then
  private val writePluginId = config.getString("write-plugin")
  private val eventAdapters = Persistence(system).adaptersFor(writePluginId)

  log.debug(
    """
      |ask-timeout: {}
      |refresh-interval: {}
      |max-buffer-size: {}
    """.stripMargin, timeout, refreshInterval, maxBufferSize
  )

  override def currentPersistenceIds(): Source[String, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.AllPersistenceIds).mapTo[Set[String]])
      .mapConcat(identity)

  override def persistenceIds(): Source[String, NotUsed] =
    Source.repeat(0).flatMapConcat(_ => Source.tick(refreshInterval, 0.seconds, 0).take(1).flatMapConcat(_ => currentPersistenceIds()))
      .statefulMapConcat[String] { () =>
        var knownIds = Set.empty[String]
        def next(id: String): Iterable[String] = {
          val xs = Set(id).diff(knownIds)
          knownIds += id
          xs
        }
        (id) => next(id)
      }

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.GetJournalEntriesExceptDeleted(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserialization)
      .map(repr => EventEnvelope(Sequence(repr.sequenceNr), repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.unfoldAsync[Long, Seq[EventEnvelope]](Math.max(1, fromSequenceNr)) { (from: Long) =>
      def nextFromSeqNr(xs: Seq[EventEnvelope]): Long = {
        if (xs.isEmpty) from else xs.map(_.sequenceNr).max + 1
      }

      from match {
        case x if x > toSequenceNr => Future.successful(None)
        case _ =>
          Source.tick(refreshInterval, 0.seconds, 0).take(1).flatMapConcat(_ =>
            currentEventsByPersistenceId(persistenceId, from, toSequenceNr)
              .take(maxBufferSize)).runWith(Sink.seq).map { xs =>
            val newFromSeqNr = nextFromSeqNr(xs)
            Some((newFromSeqNr, xs))
          }
      }
    }.mapConcat(identity)

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.EventsByTag(tag, offset))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserializationWithOffset(offset))
      .map {
        case (offset, repr) => EventEnvelope(offset, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    Source.unfoldAsync[Offset, Seq[EventEnvelope]](offset) { (from: Offset) =>
      def nextFromOffset(xs: Seq[EventEnvelope]): Offset = {
        if (xs.isEmpty) from else xs.last.offset match {
          case Sequence(n)         => Sequence(n)
          case TimeBasedUUID(time) => TimeBasedUUID(UUIDs.startOf(UUIDs.unixTimestamp(time) + 1))
        }
      }
      ticker.flatMapConcat(_ => currentEventsByTag(tag, from)
        .take(maxBufferSize)).runWith(Sink.seq).map { xs =>
        val next = nextFromOffset(xs)
        Some((next, xs))
      }
    }.mapConcat(identity)

  // ticker
  val ticker = Source.tick(refreshInterval, 0.seconds, 0).take(1)

  //
  // deserialization
  //
  private def deserialize(serialized: Array[Byte]) =
    Source.fromFuture(Future.fromTry(serialization.deserialize(serialized, classOf[PersistentRepr])))

  private val deserialization = Flow[JournalEntry]
    .flatMapConcat(deserializeJournalEntry)

  private def adaptFromJournal(repr: PersistentRepr): Seq[PersistentRepr] =
    eventAdapters
      .get(repr.payload.getClass)
      .fromJournal(repr.payload, repr.manifest)
      .events
      .map(adaptedPayload => repr.withPayload(adaptedPayload))

  private def deserializeJournalEntry(entry: JournalEntry): Source[PersistentRepr, NotUsed] =
    deserialize(entry.serialized).map(_.update(deleted = entry.deleted)).mapConcat(adaptFromJournal)

  def determineOffset(offset: Offset, entry: JournalEntry): Offset = {
    def sequence = Sequence(entry.offset.getOrElse(throw new IllegalStateException("No offset in stream")))
    offset match {
      case _: Sequence                          => sequence
      case _: TimeBasedUUID                     => entry.timestamp
      case _ if offsetMode.contains("sequence") => sequence
      case _                                    => entry.timestamp
    }
  }

  private def deserializationWithOffset(offset: Offset): Flow[JournalEntry, (Offset, PersistentRepr), NotUsed] = Flow[JournalEntry]
    .flatMapConcat(entry =>
      deserializeJournalEntry(entry)
        .map(repr => (determineOffset(offset, entry), repr)))
}
