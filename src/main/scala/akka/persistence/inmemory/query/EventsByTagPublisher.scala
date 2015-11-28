/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.inmemory.query

import akka.actor.Props
import akka.persistence.inmemory.journal.InMemoryJournal
import akka.persistence.query.journal.leveldb.{ EventsByTagPublisher ⇒ LevelDbEventsByTagPublisher }

import scala.concurrent.duration.FiniteDuration

object EventsByTagPublisher {
  def props(tag: String, fromOffset: Long, toOffset: Long, refreshInterval: Option[FiniteDuration], maxBufSize: Int): Props =
    LevelDbEventsByTagPublisher.props(tag, fromOffset, toOffset, refreshInterval, maxBufSize, InMemoryJournal.Identifier)
}
