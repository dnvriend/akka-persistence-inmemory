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

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingReceive
import akka.persistence.PersistentRepr

import scala.compat.Platform

object InMemoryJournalStorage {

  case class Serialized(persistenceId: String, sequenceNr: Long, serialized: Array[Byte], repr: PersistentRepr, tags: Set[String], created: Long = Platform.currentTime)

  // List[String]
  case object AllPersistenceIds

  // Long
  case class HighestSequenceNr(persistenceId: String, fromSequenceNr: Long)

  // List[Array[Byte]]
  case class EventsByTag(tag: String, offset: Long)

  // List[String]
  case class PersistenceIds(queryListOfPersistenceIds: Iterable[String])

  // Success
  case class WriteList(xs: Iterable[Serialized])

  // Success
  case class Delete(persistenceId: String, toSequenceNr: Long)

  // List[Array[Byte]]
  case class Messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)

  // Success
  case object Clear

  // wrapper
  case class JournalEntry(ordering: Long, serialized: Serialized, deleted: Boolean = false)
}

class InMemoryJournalStorage extends Actor with ActorLogging {
  import InMemoryJournalStorage._

  var ordering = new AtomicLong()

  var journal = Map.empty[String, Vector[JournalEntry]]

  def allPersistenceIds(ref: ActorRef): Unit =
    ref ! akka.actor.Status.Success(journal.keySet)

  def highestSequenceNr(ref: ActorRef, persistenceId: String, fromSequenceNr: Long): Unit = {
    val xs = journal.filter(_._1 == persistenceId)
      .values.flatMap(identity)
      .map(_.serialized.sequenceNr)
    val highestSequenceNrJournal = if (xs.nonEmpty) xs.max else 0

    ref ! akka.actor.Status.Success(highestSequenceNrJournal)
  }

  def eventsByTag(ref: ActorRef, tag: String, offset: Long): Unit = {
    val xs = journal.values.flatMap(identity)
      .filter(_.ordering >= offset)
      .filter(_.serialized.tags.exists(tags ⇒ tags.contains(tag))).toList
      .sortBy(_.ordering)

    ref ! akka.actor.Status.Success(xs)
  }

  def writelist(ref: ActorRef, xs: Iterable[Serialized]): Unit = {
    import scalaz._
    import Scalaz._
    val vect = xs.map(ser ⇒ JournalEntry(ordering.incrementAndGet(), ser)).toVector
    val ys: Map[String, Vector[JournalEntry]] = xs.headOption.map(ser ⇒ Map(ser.persistenceId → vect)).getOrElse(Map.empty)
    journal = journal |+| ys

    ref ! akka.actor.Status.Success("")
  }

  def delete(ref: ActorRef, persistenceId: String, toSequenceNr: Long): Unit = {
    import scalaz._
    import Scalaz._
    val pidEntries = journal.filter(_._1 == persistenceId)
    val deleted = pidEntries.mapValues(_.filter(_.serialized.sequenceNr <= toSequenceNr).map(_.copy(deleted = true)))
    val notDeleted = pidEntries.mapValues(_.filterNot(_.serialized.sequenceNr <= toSequenceNr))
    journal = journal.filterNot(_._1 == persistenceId) |+| deleted |+| notDeleted

    ref ! akka.actor.Status.Success("")
  }

  def messages(ref: ActorRef, persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Unit = {
    def toTake = if (max >= Int.MaxValue) Int.MaxValue else max.toInt
    val pidEntries = journal.filter(_._1 == persistenceId)
    val xs: List[JournalEntry] = pidEntries.values.flatMap(identity)
      .filterNot(_.deleted)
      .filter(_.serialized.sequenceNr >= fromSequenceNr)
      .filter(_.serialized.sequenceNr <= toSequenceNr)
      .toList.sortBy(_.serialized.sequenceNr)
      .take(toTake)

    ref ! akka.actor.Status.Success(xs)
  }

  def clear(ref: ActorRef): Unit = {
    ordering = new AtomicLong()
    journal = Map.empty[String, Vector[JournalEntry]]

    ref ! akka.actor.Status.Success("")
  }

  override def receive: Receive = LoggingReceive {
    case AllPersistenceIds                                          ⇒ allPersistenceIds(sender())
    case HighestSequenceNr(persistenceId, fromSequenceNr)           ⇒ highestSequenceNr(sender(), persistenceId, fromSequenceNr)
    case EventsByTag(tag, offset)                                   ⇒ eventsByTag(sender(), tag, offset)
    case WriteList(xs)                                              ⇒ writelist(sender(), xs)
    case Delete(persistenceId, toSequenceNr)                        ⇒ delete(sender(), persistenceId, toSequenceNr)
    case Messages(persistenceId, fromSequenceNr, toSequenceNr, max) ⇒ messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max)
    case Clear                                                      ⇒ clear(sender())
  }
}
