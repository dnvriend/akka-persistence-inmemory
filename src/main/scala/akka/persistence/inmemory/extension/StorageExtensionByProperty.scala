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

import scala.collection.JavaConverters._

private object StorageExtensionByProperty {
  final val PropertyKeysKey = "inmemory-storage.property-keys"
}

class StorageExtensionByProperty()(implicit val system: ExtendedActorSystem) extends StorageExtension with ActorSingletonSupport {
  import StorageExtensionByProperty._
  private val serialization = SerializationExtension(system)
  private val propertyKeys = if (system.settings.config.hasPath(PropertyKeysKey)) system.settings.config.getStringList(PropertyKeysKey).asScala else Nil

  override def journalStorage(config: Config): ActorRef = localNonClusteredActorSingleton(system, Props(new InMemoryJournalStorage(serialization)), s"JournalStorage${keyspace(config)}")

  override def snapshotStorage(config: Config): ActorRef = localNonClusteredActorSingleton(system, Props(new InMemorySnapshotStorage), s"SnapshotStorage${keyspace(config)}")

  private def keyspace(config: Config): String = propertyKeys.flatMap(key => if (config.hasPath(key)) Some(config.getString(key)) else None).mkString("@", "@", "")
}