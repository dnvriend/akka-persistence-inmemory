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

package akka.persistence.inmemory.journal

import akka.persistence.CapabilityFlag
import akka.persistence.inmemory.util.ClasspathResources
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import scala.concurrent.duration._

abstract class AbstractInMemoryJournalSpec(config: Config) extends JournalSpec(config)
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures
    with ClasspathResources {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = false

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit val ec = system.dispatcher
}

class InMemoryJournalSpec extends AbstractInMemoryJournalSpec(ConfigFactory.load("application.conf"))