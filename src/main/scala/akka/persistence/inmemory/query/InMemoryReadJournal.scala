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

package akka.persistence.inmemory.query

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ ExtendedActorSystem, Props }
import akka.event.Logging
import akka.persistence.query.scaladsl._
import akka.persistence.query.{ EventEnvelope, ReadJournalProvider }
import akka.stream.scaladsl.Source
import com.typesafe.config.Config

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery {

  import scala.concurrent.duration._
  val logger = Logging(system, this.getClass)
  val readJournalRefreshIntervalInMillis: FiniteDuration = Duration(config.getDuration("refresh-interval").toMillis, TimeUnit.MILLISECONDS)
  logger.debug(s"Using inmemory-read-journal.refresh-interval: $readJournalRefreshIntervalInMillis for pushing new items to the stream")

  /**
   * The stream is not completed when it reaches the end of the currently used `persistenceIds`,
   * but it continues to push new `persistenceIds` when new persistent actors are created.
   */
  override def allPersistenceIds(): Source[String, NotUsed] = {
    Source.actorPublisher[String](Props(classOf[AllPersistenceIdsPublisher], true))
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named("allPersistenceIds")
  }

  /**
   * Same type of query as [[AllPersistenceIdsQuery#allPersistenceIds]] but the stream
   * is completed immediately when it reaches the end of the "result set".
   */
  override def currentPersistenceIds(): Source[String, NotUsed] = {
    Source.actorPublisher[String](Props(classOf[AllPersistenceIdsPublisher], false))
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named("currentPersistenceIds")
  }

  /**
   * Same type of query as [[EventsByPersistenceIdQuery#eventsByPersistenceId]]
   * but the event stream is completed immediately when it reaches the end of
   * the "result set". Events that are stored after the query is completed are
   * not included in the event stream.
   */
  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    Source.actorPublisher[EventEnvelope](EventsByPersistenceIdPublisher.props(persistenceId, fromSequenceNr, toSequenceNr, None, 100))
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named(s"currentEventsByPersistenceId-$persistenceId")
  }

  /**
   * Query events for a specific `PersistentActor` identified by `persistenceId`.
   *
   * You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr`
   * or use `0L` and `Long.MaxValue` respectively to retrieve all events.
   *
   * The returned event stream should be ordered by sequence number.
   *
   * The stream is not completed when it reaches the end of the currently stored events,
   * but it continues to push new events when new events are persisted.
   */
  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long = 0L,
    toSequenceNr: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    Source.actorPublisher[EventEnvelope](EventsByPersistenceIdPublisher.props(persistenceId, fromSequenceNr, toSequenceNr, Option(readJournalRefreshIntervalInMillis), 100))
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named(s"eventsByPersistenceId-$persistenceId")
  }
}

class InMemoryReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override val scaladslReadJournal = new InMemoryReadJournal(system, config)
  override val javadslReadJournal = null
}
