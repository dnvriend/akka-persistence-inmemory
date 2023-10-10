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

package io.github.alstanchev.pekko.persistence.inmemory.query

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.persistence.Persistence
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.persistence.query.scaladsl._

class StorageExtensionByPropertyTest extends QueryTestSpec("storage-by-property.conf") {

  private lazy val journalWithSomeKeyspace: ActorRef = Persistence(system).journalFor("inmemory-journal-some-other")
  private lazy val readJournalSomeKeyspace = PersistenceQuery(system).readJournalFor("inmemory-read-journal-some-other")
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