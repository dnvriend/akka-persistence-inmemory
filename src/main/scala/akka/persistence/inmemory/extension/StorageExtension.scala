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
import com.typesafe.config.Config

import scala.collection.mutable

object StorageExtension extends ExtensionId[StorageExtensionImpl] with ExtensionIdProvider {
  private[extension] final val KeySpaceKey = "keyspace"
  type Keyspace = String

  override def createExtension(system: ExtendedActorSystem): StorageExtensionImpl = new StorageExtensionImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = StorageExtension

  def keyspaceFrom(config: Config): Option[Keyspace] = if (config.hasPath(KeySpaceKey)) Some(config.getString(KeySpaceKey)) else None
}

class StorageExtensionImpl()(implicit val system: ExtendedActorSystem) extends Extension {
  import StorageExtension._
  private val serialization = SerializationExtension(system)
  private val existingActors: mutable.Map[String, ActorRef] = mutable.Map.empty

  def journalStorage(keyspace: Option[Keyspace] = None): ActorRef = localNonClusteredActorSingleton(Props(new InMemoryJournalStorage(serialization)), "JournalStorage", keyspace)

  def snapshotStorage(keyspace: Option[Keyspace] = None): ActorRef = localNonClusteredActorSingleton(Props(new InMemorySnapshotStorage), "SnapshotStorage", keyspace)

  private def localNonClusteredActorSingleton(props: Props, prefix: String, keyspace: Option[Keyspace]): ActorRef = {
    val actorName = s"$prefix${keyspace.map(ks => s"@$ks").getOrElse("")}"
    existingActors.get(actorName) match {
      case Some(a) => a
      case None =>
        existingActors.synchronized {
          existingActors.get(actorName) match {
            case Some(a) => a
            case None =>
              val newActor = system.actorOf(props, actorName)
              existingActors.put(actorName, newActor)
              newActor
          }
        }
    }
  }
}
