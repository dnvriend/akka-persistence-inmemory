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
package query.scaladsl

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ ActorRef, ExtendedActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import akka.pattern.ask
import akka.persistence.{ Persistence, PersistentRepr }
import akka.persistence.inmemory.extension.{ InMemoryJournalStorage, StorageExtension }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.EventWriter.WriteEvent
import akka.persistence.query.scaladsl._
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.typesafe.config.Config

import scala.collection.immutable
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
    with EventsByTagQuery
    with EventWriter {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  implicit val log: LoggingAdapter = Logging(system, this.getClass)
  val serialization = SerializationExtension(system)
  val journal: ActorRef = StorageExtension(system).journalStorage
  implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.MILLISECONDS) -> MILLISECONDS)
  val refreshInterval: FiniteDuration = config.getDuration("refresh-interval", TimeUnit.MILLISECONDS) -> MILLISECONDS
  val maxBufferSize: Int = Try(config.getString("max-buffer-size").toInt).getOrElse(config.getInt("max-buffer-size"))

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
    Source.fromFuture((journal ? InMemoryJournalStorage.EventsByTag(tag, offset))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserializationWithOrdering)
      .map {
        case (ordering, repr) => EventEnvelope(ordering, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    Source.unfoldAsync[Long, Seq[EventEnvelope]](offset) { (from: Long) =>
      def nextFromOffset(xs: Seq[EventEnvelope]): Long = {
        if (xs.isEmpty) from else xs.map(_.offset).max + 1
      }
      Source.tick(refreshInterval, 0.seconds, 0).take(1).flatMapConcat(_ => currentEventsByTag(tag, from)
        .take(maxBufferSize)).runWith(Sink.seq).map { xs =>
        val newFromSeqNr = nextFromOffset(xs)
        Some((newFromSeqNr, xs))
      }
    }.mapConcat(identity)

  private def deserialize(serialized: Array[Byte]) =
    Source.fromFuture(Future.fromTry(serialization.deserialize(serialized, classOf[PersistentRepr])))

  private val persistence = Persistence(system)

  /**
   * Use default `journalPluginId` from config `akka.persistence.journal.plugin` to obtain event adapters.
   */
  private val eventAdapters = persistence.adaptersFor(journalPluginId = "")

  private[akka] final def adaptFromJournal(repr: PersistentRepr): immutable.Seq[PersistentRepr] =
    eventAdapters.get(repr.payload.getClass).fromJournal(repr.payload, repr.manifest).events map { adaptedPayload =>
      repr.withPayload(adaptedPayload)
    }

  private def deserializeJournalEntry(entry: JournalEntry): Source[PersistentRepr, NotUsed] =
    deserialize(entry.serialized).map(_.update(deleted = entry.deleted)).mapConcat(adaptFromJournal)

  private val deserialization = Flow[JournalEntry]
    .flatMapConcat(deserializeJournalEntry)

  private val deserializationWithOrdering = Flow[JournalEntry]
    .flatMapConcat(entry => deserializeJournalEntry(entry).map((entry.ordering, _)))

  override def eventWriter: Flow[WriteEvent, WriteEvent, NotUsed] = Flow[WriteEvent].flatMapConcat {
    case write @ WriteEvent(repr, tags) =>
      Source.fromFuture(Future.fromTry(serialization.serialize(repr)))
        .map(arr => JournalEntry(repr.persistenceId, repr.sequenceNr, arr, repr, write.tags))
        .mapAsyncUnordered(8)(entry => journal ? InMemoryJournalStorage.WriteList(List(entry)))
        .map(_ => write)
  }
}
