/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.inmemory.query

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import akka.actor.{ ExtendedActorSystem, Props }
import akka.persistence.query.scaladsl._
import akka.persistence.query.{ EventEnvelope, ReadJournalProvider }
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery {

  override def allPersistenceIds(): Source[String, Unit] = {
    Source.actorPublisher[String](Props(classOf[AllPersistenceIdsPublisher], true))
      .mapMaterializedValue(_ ⇒ ())
      .named("allPersistenceIds")
  }

  override def currentPersistenceIds(): Source[String, Unit] = {
    Source.actorPublisher[String](Props(classOf[AllPersistenceIdsPublisher], false))
      .mapMaterializedValue(_ ⇒ ())
      .named("currentPersistenceIds")
  }

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, Unit] = {
    Source.actorPublisher[EventEnvelope](EventsByPersistenceIdPublisher.props(persistenceId, fromSequenceNr, toSequenceNr, None, 100))
      .mapMaterializedValue(_ ⇒ ())
      .named(s"currentEventsByPersistenceId-$persistenceId")
  }

  import scala.concurrent.duration._

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long = 0L,
    toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, Unit] = {
    val refreshInterval = Duration(config.getDuration("refresh-interval").toMillis, TimeUnit.MILLISECONDS)
    Source.actorPublisher[EventEnvelope](EventsByPersistenceIdPublisher.props(persistenceId, fromSequenceNr, toSequenceNr, Some(refreshInterval), 100))
      .mapMaterializedValue(_ ⇒ ())
      .named(s"eventsByPersistenceId-$persistenceId")
  }

  override def currentEventsByTag(tag: String, offset: Long = 0L): Source[EventEnvelope, Unit] = {
    Source.actorPublisher[EventEnvelope](EventsByTagPublisher.props(tag, offset, Long.MaxValue, None, 100))
      .mapMaterializedValue(_ ⇒ ())
      .named("currentEventsByTag-" + URLEncoder.encode(tag, ByteString.UTF_8))
  }

  override def eventsByTag(tag: String, offset: Long = 0L): Source[EventEnvelope, Unit] = {
    val refreshInterval = Duration(config.getDuration("refresh-interval").toMillis, TimeUnit.MILLISECONDS)
    Source.actorPublisher[EventEnvelope](EventsByTagPublisher.props(tag, offset, Long.MaxValue, Some(refreshInterval), 100))
      .mapMaterializedValue(_ ⇒ ())
      .named("eventsByTag-" + URLEncoder.encode(tag, ByteString.UTF_8))
  }
}

class InMemoryReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override val scaladslReadJournal = new InMemoryReadJournal(system, config)
  override val javadslReadJournal = null
}

