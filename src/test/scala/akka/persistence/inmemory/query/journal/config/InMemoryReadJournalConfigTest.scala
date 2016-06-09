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

package akka.persistence.inmemory.query.journal.config

import akka.persistence.inmemory.TestSpec
import com.typesafe.config.{ Config, ConfigFactory }
import scala.concurrent.duration._

class InMemoryReadJournalConfigTest extends TestSpec {
  val config: Config = ConfigFactory.parseString(
    """
      |inmemory-read-journal {
      |  # Implementation class of the InMemory ReadJournalProvider
      |  class = "akka.persistence.inmemory.query.journal.InMemoryJournalProvider"
      |
      |  # New events are retrieved (polled) with this interval.
      |  refresh-interval = "3s"
      |
      |  # How many events to fetch in one query (replay) and keep buffered until they
      |  # are delivered downstreams.
      |  max-buffer-size = "500"
      |}
    """.stripMargin
  )

  it should "parse config" in {
    InMemoryReadJournalConfig(config.getConfig("inmemory-read-journal")) shouldBe InMemoryReadJournalConfig(3.seconds, 500)
  }
}
