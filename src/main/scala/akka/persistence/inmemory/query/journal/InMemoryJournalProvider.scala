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

package akka.persistence.inmemory.query.journal

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.inmemory.extension.DaoRegistry
import akka.persistence.inmemory.query.journal.config.InMemoryReadJournalConfig
import akka.persistence.inmemory.serialization.{ AkkaSerializationProxy, SerializationFacade }
import akka.persistence.query.ReadJournalProvider
import akka.serialization.SerializationExtension
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

class InMemoryJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  implicit val log: LoggingAdapter = Logging.getLogger(system, getClass)
  implicit val sys: ActorSystem = system
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val queryPluginConfig = InMemoryReadJournalConfig(config)
  val journalDao = DaoRegistry(system).journalDao
  val serializationFacade = new SerializationFacade(new AkkaSerializationProxy(SerializationExtension(system)), ",")
  override val scaladslReadJournal = new scaladsl.InMemoryReadJournal(queryPluginConfig, journalDao, serializationFacade)
  override val javadslReadJournal = new javadsl.InMemoryReadJournal(scaladslReadJournal)
}
