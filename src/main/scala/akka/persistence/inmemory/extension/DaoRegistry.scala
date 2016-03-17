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
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.inmemory.dao.{ JournalDao, SnapshotDao }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object DaoRegistry extends ExtensionId[DaoRegistryImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DaoRegistryImpl = new DaoRegistryImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = DaoRegistry
}

trait DaoRegistry {
  def journalDao: JournalDao

  def snapshotDao: SnapshotDao
}

class DaoRegistryImpl()(implicit val system: ExtendedActorSystem) extends DaoRegistry with Extension {
  implicit val ec: ExecutionContext = system.dispatcher

  implicit val mat: Materializer = ActorMaterializer()

  implicit val timeout: Timeout = 10.seconds

  val log: LoggingAdapter = Logging(system, this.getClass)

  override val journalDao: JournalDao = JournalDao(StorageExtension(system).journalStorage)

  override val snapshotDao: SnapshotDao = SnapshotDao(StorageExtension(system).snapshotStorage)
}
