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

package akka.persistence.inmemory.query.journal.scaladsl

import akka.NotUsed
import akka.actor.{ ActorSystem, Props }
import akka.event.LoggingAdapter
import akka.persistence.inmemory.dao.JournalDao
import akka.persistence.inmemory.query.journal.config.InMemoryReadJournalConfig
import akka.persistence.inmemory.query.journal.publisher.{ AllPersistenceIdsPublisher, EventsByPersistenceIdPublisher, EventsByTagPublisher }
import akka.persistence.inmemory.serialization.SerializationFacade
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import scala.concurrent.{ ExecutionContext, Future }

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal(config: InMemoryReadJournalConfig, journalDao: JournalDao, serializationFacade: SerializationFacade)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext, log: LoggingAdapter) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery {

  override def currentPersistenceIds(): Source[String, NotUsed] =
    Source.fromFuture(journalDao.allPersistenceIds).mapConcat(identity)

  override def allPersistenceIds(): Source[String, NotUsed] =
    Source.actorPublisher[String](Props(new AllPersistenceIdsPublisher(journalDao, config.refreshInterval, config.maxBufferSize))).mapMaterializedValue(_ ⇒ NotUsed)

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    journalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.actorPublisher[EventEnvelope](Props(new EventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, toSequenceNr, journalDao, serializationFacade, config.refreshInterval, config.maxBufferSize))).mapMaterializedValue(_ ⇒ NotUsed)

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journalDao.eventsByTag(tag, offset)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .zipWith(Source(Stream.from(Math.max(1, offset.toInt)))) { // Needs a better way
        case (repr, i) ⇒ EventEnvelope(i, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    Source.actorPublisher[EventEnvelope](Props(new EventsByTagPublisher(tag, offset.toInt, journalDao, serializationFacade, config.refreshInterval, config.maxBufferSize))).mapMaterializedValue(_ ⇒ NotUsed)
}