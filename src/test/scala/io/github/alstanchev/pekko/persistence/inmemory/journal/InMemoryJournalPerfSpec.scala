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

package io.github.alstanchev.pekko.persistence.inmemory.journal

import org.apache.pekko.persistence.CapabilityFlag
import org.apache.pekko.persistence.journal.JournalPerfSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.Ignore

import scala.concurrent.duration._

@Ignore
class InMemoryJournalPerfSpec extends JournalPerfSpec(ConfigFactory.load("application.conf")) {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  override def awaitDurationMillis: Long = 60.minutes.toMillis
}
