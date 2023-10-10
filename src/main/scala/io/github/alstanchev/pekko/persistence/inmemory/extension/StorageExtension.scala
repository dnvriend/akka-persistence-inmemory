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

package io.github.alstanchev.pekko.persistence.inmemory.extension

import com.typesafe.config.Config
import org.apache.pekko.actor.{ ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }

trait StorageExtension extends Extension {
  def journalStorage(config: Config): ActorRef

  def snapshotStorage(config: Config): ActorRef
}

object StorageExtensionProvider extends ExtensionId[StorageExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StorageExtension = {
    val storageName = system.settings.config.getString("inmemory-storage.class")
    system.log.info("Using storage {}", storageName)
    val storageClass = system.dynamicAccess.getClassFor[StorageExtension](storageName).get
    val storage = storageClass.getDeclaredConstructor(classOf[ExtendedActorSystem]).newInstance(system)
    storage
  }

  override def lookup(): ExtensionId[_ <: Extension] = StorageExtensionProvider

  /**
   * Java API
   */
  override def get(as: ActorSystem): StorageExtension = apply(as)
}

