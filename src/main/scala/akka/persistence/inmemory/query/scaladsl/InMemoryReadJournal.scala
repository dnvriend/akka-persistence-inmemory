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
import akka.actor.{ ActorRef, ExtendedActorSystem, Props }
import akka.pattern.ask
import akka.persistence.PersistentRepr
import akka.persistence.inmemory.extension.{ InMemoryJournalStorage, StorageExtension }
import akka.persistence.inmemory.query.{ EventsByPersistenceIdPublisher, EventsByTagPublisher }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Merge, Source, Zip }
import akka.stream.{ ActorMaterializer, ClosedShape, Materializer, SourceShape }
import akka.util.Timeout
import com.typesafe.config.Config

import scala.collection.immutable.Iterable
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
    with EventsByTagQuery {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.SECONDS) → SECONDS)
  val serialization = SerializationExtension(system)
  val journal: ActorRef = StorageExtension(system).journalStorage
  val refreshInterval: FiniteDuration = config.getDuration("refresh-interval", TimeUnit.SECONDS) → SECONDS
  val maxBufferSize: Int = Try(config.getString("max-buffer-size").toInt).getOrElse(config.getInt("max-buffer-size"))

  override def currentPersistenceIds(): Source[String, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.AllPersistenceIds).mapTo[Set[String]]).mapConcat(identity)

  override def allPersistenceIds(): Source[String, NotUsed] =
    Source.tick(0.seconds, refreshInterval, "")
      .flatMapConcat(_ ⇒ currentPersistenceIds())
      .statefulMapConcat[String] { () ⇒
        var knownIds = Set.empty[String]
        def next(id: String): Iterable[String] = {
          val xs = Set(id).diff(knownIds)
          knownIds += id
          xs
        }
        (id) ⇒ next(id)
      }.mapMaterializedValue(_ ⇒ NotUsed)

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.GetAllJournalEntries(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserialization)
      .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.actorPublisher[EventEnvelope](Props(new EventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, toSequenceNr, refreshInterval, maxBufferSize, this)))
      .mapMaterializedValue(_ ⇒ NotUsed)

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    Source.fromFuture((journal ? InMemoryJournalStorage.EventsByTag(tag, offset))
      .mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserializationWithOrdering)
      .map {
        case (ordering, repr) ⇒ EventEnvelope(ordering, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    Source.actorPublisher[EventEnvelope](Props(new EventsByTagPublisher(tag, offset.toInt, refreshInterval, maxBufferSize, this)))
      .mapMaterializedValue(_ ⇒ NotUsed)

  private def deserialize(serialized: Array[Byte]) =
    Source.fromFuture(Future.fromTry(serialization.deserialize(serialized, classOf[PersistentRepr])))

  private val deserialization = Flow[JournalEntry].flatMapConcat {
    entry ⇒
      deserialize(entry.serialized).map(_.update(deleted = entry.deleted))
  }

  private val deserializationWithOrdering = Flow[JournalEntry].flatMapConcat {
    entry ⇒
      deserialize(entry.serialized).map(_.update(deleted = entry.deleted)).map((entry.ordering, _))
  }
}