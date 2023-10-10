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

package io.github.alstanchev.pekko.persistence.inmemory.extension

import org.apache.pekko.actor.{ Actor, ActorLogging, ActorRef, NoSerializationVerificationNeeded }
import org.apache.pekko.event.LoggingReceive
import org.apache.pekko.persistence.PersistentRepr
import io.github.alstanchev.pekko.persistence.inmemory.JournalEntry
import io.github.alstanchev.pekko.persistence.inmemory.util.UUIDs
import org.apache.pekko.persistence.query.{ NoOffset, Offset, Sequence, TimeBasedUUID }
import org.apache.pekko.serialization.Serialization

import scala.collection.immutable._
import scalaz.syntax.semigroup._
import scalaz.std.AllInstances._

object InMemoryJournalStorage {
  sealed trait JournalCommand extends NoSerializationVerificationNeeded
  case object PersistenceIds extends JournalCommand
  final case class HighestSequenceNr(persistenceId: String, fromSequenceNr: Long) extends JournalCommand
  final case class EventsByTag(tag: String, offset: Offset) extends JournalCommand
  final case class PersistenceIds(queryListOfPersistenceIds: Seq[String]) extends JournalCommand
  final case class WriteList(xs: Seq[JournalEntry]) extends JournalCommand
  final case class Delete(persistenceId: String, toSequenceNr: Long) extends JournalCommand
  final case class GetJournalEntriesExceptDeleted(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long) extends JournalCommand
  final case class GetAllJournalEntries(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long) extends JournalCommand

  /**
   * Java API
   */
  def clearJournal(): ClearJournal = ClearJournal

  sealed abstract class ClearJournal
  case object ClearJournal extends ClearJournal with JournalCommand

  def getPersistenceId(prod: (String, Vector[JournalEntry])): String = prod._1
  def getEntries(prod: (String, Vector[JournalEntry])): Vector[JournalEntry] = prod._2
  def getEventsByPid(pid: String, journal: Map[String, Vector[JournalEntry]]): Option[Vector[JournalEntry]] =
    journal.find(_._1 == pid).map(_._2)
  def getAllEvents(journal: Map[String, Vector[JournalEntry]]): Vector[JournalEntry] =
    journal.values.flatten[JournalEntry].toVector
  def getMaxSequenceNr(xs: Vector[JournalEntry]): Long = xs.map(_.sequenceNr).max
}

class InMemoryJournalStorage(serialization: Serialization) extends Actor with ActorLogging {
  import InMemoryJournalStorage._

  var ordering: Long = 0L

  def incrementAndGet: Long = {
    ordering += 1
    ordering
  }

  var journal = Map.empty[String, Vector[JournalEntry]]

  def allPersistenceIds(ref: ActorRef): Unit =
    ref ! org.apache.pekko.actor.Status.Success(journal.keySet)

  def highestSequenceNr(ref: ActorRef, persistenceId: String, fromSequenceNr: Long): Unit = {
    val highestSequenceNrJournal = getEventsByPid(persistenceId, journal).map(getMaxSequenceNr).getOrElse(0L)
    ref ! org.apache.pekko.actor.Status.Success(highestSequenceNrJournal)
  }

  def eventsByTag(ref: ActorRef, tag: String, offset: Offset): Unit = {
    def increment(offset: Long): Long = offset + 1
    def getByOffset(p: JournalEntry => Boolean): List[JournalEntry] = {
      val xs = getAllEvents(journal)
        .filter(_.tags.contains(tag)).toList
        .sortBy(_.ordering)
        .zipWithIndex.map {
          case (entry, index) =>
            entry.copy(offset = Option(increment(index)))
        }

      xs.filter(p)
    }

    val xs: List[JournalEntry] = offset match {
      case NoOffset             => getByOffset(_.offset.exists(_ >= 0L))
      case Sequence(value)      => getByOffset(_.offset.exists(_ > value))
      case value: TimeBasedUUID => getByOffset(p => UUIDs.TimeBasedUUIDOrdering.gt(p.timestamp, value))
    }

    ref ! org.apache.pekko.actor.Status.Success(xs)
  }

  def writelist(ref: ActorRef, xs: Seq[JournalEntry]): Unit = {
    val ys = xs.map(_.copy(ordering = incrementAndGet)).groupBy(_.persistenceId)
    journal = journal |+| ys

    ref ! org.apache.pekko.actor.Status.Success(())
  }

  def delete(ref: ActorRef, persistenceId: String, toSequenceNr: Long): Unit = {
    val pidEntries = journal.filter(_._1 == persistenceId)
    val notDeleted = pidEntries.mapValues(_.filterNot(_.sequenceNr <= toSequenceNr))

    val deleted = pidEntries
      .mapValues(_.filter(_.sequenceNr <= toSequenceNr).map { journalEntry =>
        val updatedRepr: PersistentRepr = journalEntry.repr.update(deleted = true)
        val byteArray: Array[Byte] = serialization.serialize(updatedRepr) match {
          case scala.util.Success(arr)   => arr
          case scala.util.Failure(cause) => throw cause
        }
        journalEntry.copy(deleted = true).copy(serialized = byteArray).copy(repr = updatedRepr)
      })

    journal = journal.filterNot(_._1 == persistenceId) |+| deleted.toMap |+| notDeleted.toMap

    ref ! org.apache.pekko.actor.Status.Success("")
  }

  def messages(ref: ActorRef, persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long, all: Boolean): Unit = {
    def toTake = if (max >= Int.MaxValue) Int.MaxValue else max.toInt
    val pidEntries: Map[String, Vector[JournalEntry]] = journal.filter(_._1 == persistenceId)
    val xs: List[JournalEntry] = pidEntries.flatMap(_._2)
      .filter(_.sequenceNr >= fromSequenceNr)
      .filter(_.sequenceNr <= toSequenceNr)
      .toList.sortBy(_.sequenceNr)

    val ys = if (all) xs else xs.filterNot(_.deleted)

    val zs = ys.take(toTake)

    ref ! org.apache.pekko.actor.Status.Success(zs)
  }

  def clear(ref: ActorRef): Unit = {
    ordering = 0L
    journal = Map.empty[String, Vector[JournalEntry]]

    ref ! org.apache.pekko.actor.Status.Success("")
  }

  override def receive: Receive = LoggingReceive {
    case PersistenceIds                                                                   => allPersistenceIds(sender())
    case HighestSequenceNr(persistenceId, fromSequenceNr)                                 => highestSequenceNr(sender(), persistenceId, fromSequenceNr)
    case EventsByTag(tag, offset)                                                         => eventsByTag(sender(), tag, offset)
    case WriteList(xs)                                                                    => writelist(sender(), xs)
    case Delete(persistenceId, toSequenceNr)                                              => delete(sender(), persistenceId, toSequenceNr)
    case GetJournalEntriesExceptDeleted(persistenceId, fromSequenceNr, toSequenceNr, max) => messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max, all = false)
    case GetAllJournalEntries(persistenceId, fromSequenceNr, toSequenceNr, max)           => messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max, all = true)
    case ClearJournal                                                                     => clear(sender())
  }
}
