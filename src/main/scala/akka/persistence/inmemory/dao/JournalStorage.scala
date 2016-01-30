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

import akka.actor.Status.Success
import akka.actor.{ ActorLogging, Actor, ActorRef }
import akka.event.LoggingReceive
import akka.persistence.inmemory.serialization.{ SerializationFacade, Serialized }

import scala.collection.mutable

object JournalStorage {

  // List[String]
  case object AllPersistenceIds

  // Int
  case object CountJournal

  // List[Array[Byte]]
  case class EventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long)

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

}

class JournalStorage extends Actor with ActorLogging {

  import JournalStorage._

  val journal = new mutable.HashMap[String, mutable.Set[Serialized]] with mutable.MultiMap[String, Serialized]

  val deleted_to = new mutable.HashMap[String, mutable.Set[Long]] with mutable.MultiMap[String, Long]

  def allPersistenceIds(ref: ActorRef): Unit = {
    def determine: Set[String] = journal.keySet.toSet
    ref ! determine
  }

  def countJournal(ref: ActorRef): Unit = {
    def determine: Int = journal.values.foldLeft(0) { case (c, s) ⇒ c + s.size }
    ref ! determine
  }

  def eventsByPersistenceIdAndTag(ref: ActorRef, persistenceId: String, tag: String, offset: Long): Unit = {
    def determine: Option[List[Serialized]] = for {
      xs ← journal.get(persistenceId)
    } yield (for {
      x ← xs
      if x.tags.exists(tags ⇒ SerializationFacade.decodeTags(tags, ",") contains tag)
    } yield x).toList.sortBy(_.sequenceNr)

    ref ! determine.getOrElse(Nil)
  }

  def highestSequenceNr(ref: ActorRef, persistenceId: String, fromSequenceNr: Long): Unit = {
    val determineJournal: Option[Long] = for {
      xs ← journal.get(persistenceId)
    } yield (for {
      x ← xs
      if x.sequenceNr >= fromSequenceNr
    } yield x.sequenceNr).max(Ordering.Long)

    val determineDeletedTo: Option[Long] =
      deleted_to.get(persistenceId).map(_.max(Ordering.Long))

    log.debug("--> [highestSequenceNr] Journal: " + determineJournal + ", deletedTo: " + determineDeletedTo + " deleted_to: " + deleted_to)

    val highest = (determineJournal, determineDeletedTo) match {
      case (Some(x), Some(y)) if x > y ⇒ x
      case (Some(x), Some(y)) if y > x ⇒ y
      case (Some(x), Some(y))          ⇒ y
      case (Some(x), None)             ⇒ x
      case (None, Some(y))             ⇒ y
      case (None, None)                ⇒ 0
      case _                           ⇒ 0
    }

    log.debug("--> [highestSequenceNr] Sending: " + highest)
    ref ! highest
  }

  def eventsByTag(ref: ActorRef, tag: String, offset: Long): Unit = {
    def determine: List[Serialized] = (for {
      xs ← journal.values
      x ← xs
      if x.tags.exists(tags ⇒ SerializationFacade.decodeTags(tags, ",") contains tag)
    } yield x).toList

    ref ! determine
  }

  /**
   * Returns the persistenceIds that are available on request of a query list of persistence ids
   */
  def persistenceIds(ref: ActorRef, queryListOfPersistenceIds: Iterable[String]): Unit = {
    def determine: List[String] = (for {
      pid ← journal.keySet
      if queryListOfPersistenceIds.toList contains pid
    } yield pid).toList

    ref ! determine
  }

  def writelist(ref: ActorRef, xs: Iterable[Serialized]): Unit = {
    xs.foreach { x ⇒
      journal.addBinding(x.persistenceId, x)
      log.debug("--> [writelist] Adding: " + x)
    }
    ref ! Success("")
  }

  def delete(ref: ActorRef, persistenceId: String, toSequenceNr: Long): Unit = {
    for {
      xs ← journal.get(persistenceId)
      x ← xs
      if x.sequenceNr <= toSequenceNr
    } {
      log.debug("--> [delete] Removing: " + x)
      journal.removeBinding(persistenceId, x)
      deleted_to.addBinding(persistenceId, x.sequenceNr)
    }

    Thread.sleep(500)
    ref ! Success("")
  }

  def messages(ref: ActorRef, persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Unit = {
    def toTake = if (max >= Int.MaxValue) Int.MaxValue else max.toInt
    def determine: Option[List[Serialized]] = for {
      xs ← journal.get(persistenceId)
    } yield (for {
      x ← xs
      if x.sequenceNr >= fromSequenceNr && x.sequenceNr <= toSequenceNr
    } yield x).toList.sortBy(_.sequenceNr).take(toTake)

    ref ! determine.getOrElse(Nil)
  }

  override def receive: Receive = LoggingReceive {
    case AllPersistenceIds                                          ⇒ allPersistenceIds(sender())
    case CountJournal                                               ⇒ countJournal(sender())
    case EventsByPersistenceIdAndTag(persistenceId, tag, offset)    ⇒ eventsByPersistenceIdAndTag(sender(), persistenceId, tag, offset)
    case HighestSequenceNr(persistenceId, fromSequenceNr)           ⇒ highestSequenceNr(sender(), persistenceId, fromSequenceNr)
    case EventsByTag(tag, offset)                                   ⇒ eventsByTag(sender(), tag, offset)
    case PersistenceIds(queryListOfPersistenceIds)                  ⇒ persistenceIds(sender(), queryListOfPersistenceIds)
    case WriteList(xs)                                              ⇒ writelist(sender(), xs)
    case Delete(persistenceId, toSequenceNr)                        ⇒ delete(sender(), persistenceId, toSequenceNr)
    case Messages(persistenceId, fromSequenceNr, toSequenceNr, max) ⇒ messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max)
    case msg                                                        ⇒ println("--> Dropping msg: " + msg.getClass.getName)
  }
}
