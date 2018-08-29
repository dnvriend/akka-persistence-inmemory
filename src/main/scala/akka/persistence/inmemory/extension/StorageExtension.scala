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

package akka.persistence.inmemory.extension

import akka.actor._
import akka.serialization.SerializationExtension

object StorageExtension extends ExtensionId[StorageExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StorageExtensionImpl = new StorageExtensionImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = StorageExtension
}

class StorageExtensionImpl()(implicit val system: ExtendedActorSystem) extends Extension {
  val serialization = SerializationExtension(system)

  val journalStorage: ActorRef = system.systemActorOf(Props(new InMemoryJournalStorage(serialization)), "JournalStorage")

  val snapshotStorage: ActorRef = system.systemActorOf(Props(new InMemorySnapshotStorage), "SnapshotStorage")
}
