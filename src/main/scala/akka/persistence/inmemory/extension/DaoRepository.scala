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
import akka.event.{Logging, LoggingAdapter}
import akka.persistence.inmemory.dao.{JournalStorage, JournalDao, SnapshotDao}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object DaoRepository extends ExtensionId[DaoRepositoryImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DaoRepositoryImpl = new DaoRepositoryImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = DaoRepository
}

trait DaoRepository {
  def journalDao: JournalDao

  def snapshotDao: SnapshotDao
}

class DaoRepositoryImpl()(implicit val system: ExtendedActorSystem) extends DaoRepository with Extension {
  implicit val ec: ExecutionContext = system.dispatcher

  implicit val mat: Materializer = ActorMaterializer()

  implicit val timeout: Timeout = 10.seconds

  val log: LoggingAdapter = Logging(system, this.getClass)

  override val journalDao: JournalDao = JournalDao(system.actorOf(Props(new JournalStorage)))

  override val snapshotDao: SnapshotDao = ???
}
