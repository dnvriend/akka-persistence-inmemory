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

package akka.persistence.inmemory.dao

import akka.actor.{ ActorRef, Actor }
import akka.event.LoggingReceive
import akka.persistence.PersistentRepr
import akka.persistence.inmemory.serialization.Serialized

import scala.collection.mutable

object JournalStorage {
  case object AllPersistenceIds // List[String]
  case object CountJournal
  case class EventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long)
  case class HighestSequenceNr(persistenceId: String, fromSequenceNr: Long)
  case class EventsByTag(tag: String, offset: Long)
  case class PersistenceIds(queryListOfPersistenceIds: Iterable[String])
  case class WriteList(xs: Iterable[Serialized])
  case class Delete(persistenceId: String, toSequenceNr: Long)
  case class Messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
}

class JournalStorage extends Actor {
  import JournalStorage._
  val journal = new mutable.HashMap[String, mutable.Set[PersistentRepr]] with mutable.MultiMap[String, PersistentRepr]

  def allPersistenceIds(theSender: ActorRef): Unit =
    theSender ! journal.keySet.toSet

  def countJournal(theSender: ActorRef): Unit =
    theSender ! journal.values.foldLeft(0) { case (c, s) ⇒ c + s.size }

  def eventsByPersistenceIdAndTag(sender: ActorRef, persistenceId: String, tag: String, offset: Long): Unit =
    ???

  override def receive: Receive = LoggingReceive {
    case AllPersistenceIds                                       ⇒ allPersistenceIds(sender())
    case CountJournal                                            ⇒ countJournal(sender())
    case EventsByPersistenceIdAndTag(persistenceId, tag, offset) ⇒ eventsByPersistenceIdAndTag(sender(), persistenceId, tag, offset)
    case _                                                       ⇒
  }
}
