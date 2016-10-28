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
import akka.persistence.query.{ EventEnvelope, EventEnvelope2, Offset, Sequence }
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

class InMemoryReadJournal(config: Config)(implicit val system: ExtendedActorSystem) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with CurrentEventsByTagQuery2
    with EventsByTagQuery
    with EventsByTagQuery2 {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: Materializer = ActorMaterializer()
  private implicit val log: LoggingAdapter = Logging(system, this.getClass)
  private val serialization = SerializationExtension(system)
  private val journal: ActorRef = StorageExtension(system).journalStorage
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

  override def allPersistenceIds(): Source[String, NotUsed] =
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
    Source.fromFuture((journal ? InMemoryJournalStorage.GetAllJournalEntries(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserialization)
      .map(repr => EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))

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

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    currentEventsByTag(tag, Sequence(offset))

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope2, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.EventsByTag(tag, offset.value))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserializationWithOrdering)
      .map {
        case (ordering, repr) => EventEnvelope2(Sequence(ordering), repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    eventsByTag(tag, Sequence(offset))

  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope2, NotUsed] =
    Source.unfoldAsync[Long, Seq[EventEnvelope2]](offset.value) { (from: Long) =>
      def nextFromOffset(xs: Seq[EventEnvelope2]): Long = {
        if (xs.isEmpty) from else xs.map(_.offset.value).max + 1
      }
      Source.tick(refreshInterval, 0.seconds, 0).take(1).flatMapConcat(_ => currentEventsByTag(tag, Sequence(from))
        .take(maxBufferSize)).runWith(Sink.seq).map { xs =>
        val newFromSeqNr: Long = nextFromOffset(xs)
        Some((newFromSeqNr, xs))
      }
    }.mapConcat(identity)

  private def deserialize(serialized: Array[Byte]) =
    Source.fromFuture(Future.fromTry(serialization.deserialize(serialized, classOf[PersistentRepr])))

  private def adaptFromJournal(repr: PersistentRepr): Seq[PersistentRepr] =
    eventAdapters.get(repr.payload.getClass).fromJournal(repr.payload, repr.manifest).events map { adaptedPayload =>
      repr.withPayload(adaptedPayload)
    }

  private def deserializeJournalEntry(entry: JournalEntry): Source[PersistentRepr, NotUsed] =
    deserialize(entry.serialized).map(_.update(deleted = entry.deleted)).mapConcat(adaptFromJournal)

  private val deserialization = Flow[JournalEntry]
    .flatMapConcat(deserializeJournalEntry)

  private val deserializationWithOrdering = Flow[JournalEntry]
    .flatMapConcat(entry => deserializeJournalEntry(entry).map((entry.ordering, _)))
}
