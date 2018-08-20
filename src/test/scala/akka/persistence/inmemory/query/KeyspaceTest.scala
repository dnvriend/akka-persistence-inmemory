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

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence.Persistence
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl._
import com.typesafe.config.{Config, ConfigFactory}

class KeyspaceTest extends QueryTestSpec {
  val withSomeKeyspaceConfig: Config = ConfigFactory.parseString(
    """
      |inmemory-journal {
      |  # Name of the keyspace to be used by the plugin. Default value is none.
      |  keyspace = "someKeyspace"
      |}
      |
      |inmemory-read-journal {
      |  # Name of the keyspace to be used by the plugin. Default value is none.
      |  keyspace = "someKeyspace"
      |}
    """.stripMargin)

  private val systemWithSomeKeyspace: ActorSystem = ActorSystem("test", withSomeKeyspaceConfig.withFallback(system.settings.config))
  private lazy val journalWithSomeKeyspace: ActorRef = Persistence(systemWithSomeKeyspace).journalFor("inmemory-journal")
  private lazy val readJournalSomeKeyspace = PersistenceQuery(systemWithSomeKeyspace).readJournalFor("inmemory-read-journal")
      .asInstanceOf[ReadJournal with CurrentPersistenceIdsQuery]


  it should "not find any persistenceIds for different keyspace" in {
    persist("my-1")(journalWithSomeKeyspace)

    withCurrentPersistenceIds() { tp =>
      tp.request(1)
      tp.expectComplete()
    }
  }

  it should "find persistenceIds from same keyspace" in {
    persist("my-1")(journalWithSomeKeyspace)
    persist("my-2")(journalWithSomeKeyspace)
    persist("my-3")(journalWithSomeKeyspace)

    withCurrentPersistenceIds() { tp =>
      tp.request(3)
      tp.expectNextUnordered("my-1", "my-2", "my-3")
      tp.expectComplete()
    }(readJournalSomeKeyspace)
  }
}