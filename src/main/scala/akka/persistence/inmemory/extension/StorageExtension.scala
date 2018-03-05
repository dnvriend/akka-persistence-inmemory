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

import java.util.concurrent.ConcurrentHashMap

import akka.actor._
import akka.serialization.SerializationExtension
import com.typesafe.config.Config

import scala.concurrent.Future

object StorageExtension extends ExtensionId[StorageExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StorageExtensionImpl = new StorageExtensionImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = StorageExtension

  def storageId(config: Config): String =
    if (config.hasPath("storage-id")) config.getString("storage-id") else DefaultStorageId

  final val DefaultStorageId = "Storage"

}

class StorageExtensionImpl()(implicit val system: ExtendedActorSystem) extends Extension {
  val serialization = SerializationExtension(system)

  private val journalStorages = new ConcurrentHashMap[String, ActorRef]()
  private val snapshotStorages = new ConcurrentHashMap[String, ActorRef]()

  private def retrieveStorage(storages: ConcurrentHashMap[String, ActorRef], name: String, props: Props, prefix: String): ActorRef = {
    Option(storages.get(name)) match {
      case Some(ref) =>
        ref
      case None =>
        val ref = system.actorOf(props, prefix + name)
        storages.put(name, ref)
        ref
    }
  }

  def retrieveJournalStorage(name: String = StorageExtension.DefaultStorageId): ActorRef = {
    retrieveStorage(journalStorages, name, Props(new InMemoryJournalStorage(serialization)), "Journal")
  }

  def retrieveSnapshotStorage(name: String = StorageExtension.DefaultStorageId): ActorRef = {
    retrieveStorage(snapshotStorages, name, Props(new InMemorySnapshotStorage), "Snapshot")
  }

  val defaultJournalStorage = retrieveJournalStorage()
  val defaultSnapshotStorate = retrieveSnapshotStorage()

}
