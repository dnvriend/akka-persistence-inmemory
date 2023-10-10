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

import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.persistence.query.ReadJournalProvider
import com.typesafe.config.Config
import io.github.alstanchev.pekko.persistence.inmemory.extension.StorageExtensionProvider

class InMemoryReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override val scaladslReadJournal: scaladsl.InMemoryReadJournal = new scaladsl.InMemoryReadJournal(config, StorageExtensionProvider(system).journalStorage(config))(system)

  override val javadslReadJournal: javadsl.InMemoryReadJournal = new javadsl.InMemoryReadJournal(scaladslReadJournal)
}
