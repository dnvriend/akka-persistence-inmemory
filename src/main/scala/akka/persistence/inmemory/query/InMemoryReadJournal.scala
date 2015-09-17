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

import akka.actor.{ExtendedActorSystem, Props}
import akka.persistence.query.ReadJournalProvider
import akka.persistence.query.scaladsl.{CurrentPersistenceIdsQuery, ReadJournal}
import akka.stream.scaladsl.Source
import com.typesafe.config.Config

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal with CurrentPersistenceIdsQuery {
  override def currentPersistenceIds(): Source[String, Unit] = {
    Source.actorPublisher[String](Props[AllPersistenceIdsPublisher])
      .mapMaterializedValue(_ ⇒ ())
      .named("allPersistenceIds")
  }
}

class InMemoryReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override val scaladslReadJournal = new InMemoryReadJournal(system, config)
  override val javadslReadJournal = null
}

