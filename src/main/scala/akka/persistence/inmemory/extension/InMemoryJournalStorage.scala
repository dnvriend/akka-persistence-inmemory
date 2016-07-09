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

package akka.persistence.inmemory
package extension

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingReceive
import scalaz.std.AllInstances._
import scalaz.syntax.all._

object InMemoryJournalStorage {
  sealed trait JournalCommand
  case object AllPersistenceIds extends JournalCommand
  final case class HighestSequenceNr(persistenceId: String, fromSequenceNr: Long) extends JournalCommand
  final case class EventsByTag(tag: String, offset: Long) extends JournalCommand
  final case class PersistenceIds(queryListOfPersistenceIds: Seq[String]) extends JournalCommand
  final case class WriteList(xs: Seq[JournalEntry]) extends JournalCommand
  final case class Delete(persistenceId: String, toSequenceNr: Long) extends JournalCommand
  final case class GetJournalEntriesExceptDeleted(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long) extends JournalCommand
  final case class GetAllJournalEntries(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long) extends JournalCommand
  case object ClearJournal extends JournalCommand
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
      .map(_.sequenceNr)
    val highestSequenceNrJournal = if (xs.nonEmpty) xs.max else 0

    ref ! akka.actor.Status.Success(highestSequenceNrJournal)
  }

  def eventsByTag(ref: ActorRef, tag: String, offset: Long): Unit = {
    val xs = journal.values.flatMap(identity)
      .filter(_.ordering >= offset)
      .filter(_.tags.exists(tags ⇒ tags.contains(tag))).toList
      .sortBy(_.ordering)

    ref ! akka.actor.Status.Success(xs)
  }

  def writelist(ref: ActorRef, xs: Seq[JournalEntry]): Unit = {
    val ys = xs.map(_.copy(ordering = ordering.incrementAndGet())).groupBy(_.persistenceId)
    journal = journal |+| ys

    ref ! akka.actor.Status.Success("")
  }

  def delete(ref: ActorRef, persistenceId: String, toSequenceNr: Long): Unit = {
    val pidEntries = journal.filter(_._1 == persistenceId)
    val deleted = pidEntries.mapValues(_.filter(_.sequenceNr <= toSequenceNr).map(_.copy(deleted = true)))
    val notDeleted = pidEntries.mapValues(_.filterNot(_.sequenceNr <= toSequenceNr))
    journal = journal.filterNot(_._1 == persistenceId) |+| deleted |+| notDeleted

    ref ! akka.actor.Status.Success("")
  }

  def messages(ref: ActorRef, persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long, all: Boolean): Unit = {
    def toTake = if (max >= Int.MaxValue) Int.MaxValue else max.toInt
    val pidEntries = journal.filter(_._1 == persistenceId)
    val xs: List[JournalEntry] = pidEntries.values.flatMap(identity)
      .filter(_.sequenceNr >= fromSequenceNr)
      .filter(_.sequenceNr <= toSequenceNr)
      .toList.sortBy(_.sequenceNr)

    val ys = if (all) xs else xs.filterNot(_.deleted)

    val zs = ys.take(toTake)

    ref ! akka.actor.Status.Success(zs)
  }

  def clear(ref: ActorRef): Unit = {
    ordering = new AtomicLong()
    journal = Map.empty[String, Vector[JournalEntry]]

    ref ! akka.actor.Status.Success("")
  }

  override def receive: Receive = LoggingReceive {
    case AllPersistenceIds                                                                ⇒ allPersistenceIds(sender())
    case HighestSequenceNr(persistenceId, fromSequenceNr)                                 ⇒ highestSequenceNr(sender(), persistenceId, fromSequenceNr)
    case EventsByTag(tag, offset)                                                         ⇒ eventsByTag(sender(), tag, offset)
    case WriteList(xs)                                                                    ⇒ writelist(sender(), xs)
    case Delete(persistenceId, toSequenceNr)                                              ⇒ delete(sender(), persistenceId, toSequenceNr)
    case GetJournalEntriesExceptDeleted(persistenceId, fromSequenceNr, toSequenceNr, max) ⇒ messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max, all = false)
    case GetAllJournalEntries(persistenceId, fromSequenceNr, toSequenceNr, max)           ⇒ messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max, all = true)
    case ClearJournal                                                                     ⇒ clear(sender())
  }
}
