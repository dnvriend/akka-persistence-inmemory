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
import akka.actor.{ ActorLogging, ActorRef, Actor }
import akka.persistence.inmemory.dao.SnapshotDao.SnapshotData
import scala.collection.mutable

object SnapshotStorage {
  // Success
  case class Delete(persistenceId: String, sequenceNr: Long)

  // Success
  case class DeleteAllSnapshots(persistenceId: String)

  // Success
  case class DeleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long)

  // Success
  case class DeleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long)

  // Success
  case class DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long)

  // Success
  case object Clear

  // Success
  case class Save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte])

  // [Option[SnapshotData]]
  case class SnapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long)

  // [Option[SnapshotData]]
  case class SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long)

  // [Option[SnapshotData]]
  case class SnapshotForMaxTimestamp(persistenceId: String, timestamp: Long)
}

class SnapshotStorage extends Actor with ActorLogging {
  import SnapshotStorage._

  val snapshot = new mutable.HashMap[String, mutable.Set[SnapshotData]] with mutable.MultiMap[String, SnapshotData]

  def logger(msg: AnyRef)(implicit line: sourcecode.Line, file: sourcecode.File) = log.debug(s"${file.value.split("/").reverse.head}:${line.value} $msg")

  def clear(ref: ActorRef): Unit = {
    logger("[Clear]")
    ref ! Success("")
  }

  def delete(ref: ActorRef, persistenceId: String, sequenceNr: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.sequenceNumber == sequenceNr
    } {
      snapshot.removeBinding(persistenceId, x)
      logger(s"[deleting]: $x, rest: ${snapshot.get(persistenceId)}")
    }
    logger(s"s[delete]: pid: $persistenceId, seqno: $sequenceNr, rest: ${snapshot.get(persistenceId)}")
    ref ! Success("")
  }

  def deleteUpToMaxSequenceNr(ref: ActorRef, persistenceId: String, maxSequenceNr: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.sequenceNumber <= maxSequenceNr
    } {
      logger(s"[deleting]: $x, rest: ${snapshot.get(persistenceId)}")
      snapshot.removeBinding(persistenceId, x)
    }

    logger(s"[deleteUpToMaxSequenceNr]: pid: $persistenceId, maxSeqNo: $maxSequenceNr")
    ref ! Success("")
  }

  def deleteAllSnapshots(ref: ActorRef, persistenceId: String): Unit = {
    snapshot.remove(persistenceId)
    logger(s"[deleteAllSnapshots]: $persistenceId")
    ref ! Success("")
  }

  def deleteUpToMaxTimestamp(ref: ActorRef, persistenceId: String, maxTimestamp: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.created <= maxTimestamp
    } {
      logger(s"[deleting]: $x, rest: ${snapshot.get(persistenceId)}")
      snapshot.removeBinding(persistenceId, x)
    }

    logger(s"[deleteUpToMaxTimestamp]: pid: $persistenceId, maxTimestamp: $maxTimestamp")
    ref ! Success("")
  }

  def deleteUpToMaxSequenceNrAndMaxTimestamp(ref: ActorRef, persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.sequenceNumber <= maxSequenceNr && x.created <= maxTimestamp
    } {
      snapshot.removeBinding(persistenceId, x)
      logger(s"[deleting]: $x rest: ${snapshot.get(persistenceId)}")
    }
    logger(s"[deleteUpToMaxSequenceNrAndMaxTimestamp]: pid: $persistenceId, maxSeqNo: $maxSequenceNr, maxTimestamp: $maxTimestamp")
    ref ! Success("")
  }

  def save(ref: ActorRef, persistenceId: String, sequenceNr: Long, timestamp: Long, data: Array[Byte]): Unit = {
    snapshot.addBinding(persistenceId, SnapshotData(persistenceId, sequenceNr, timestamp, data))
    logger(s"[Save]: Saving snapshot: pid: $persistenceId, seqnr: $sequenceNr, timestamp: $timestamp")
    ref ! Success("")
  }

  def snapshotFor(ref: ActorRef, persistenceId: String)(p: SnapshotData ⇒ Boolean): Unit = {
    val determine: Option[SnapshotData] = snapshot.get(persistenceId).flatMap(_.find(p))
    logger(s"[snapshotFor]: pid: $persistenceId: returning: $determine")
    ref ! determine
  }

  def snapshotForMaxSequenceNr(ref: ActorRef, persistenceId: String, sequenceNr: Long): Unit = {
    val determine = snapshot.get(persistenceId).flatMap(xs ⇒ xs.filter(_.sequenceNumber <= sequenceNr).toList.sortBy(_.sequenceNumber).reverse.headOption)
    logger(s"[snapshotForMaxSequenceNr]: pid: $persistenceId, seqno: $sequenceNr, returning: $determine")
    ref ! determine
  }

  def snapshotForMaxSequenceNrAndMaxTimestamp(ref: ActorRef, persistenceId: String, sequenceNr: Long, timestamp: Long): Unit = {
    logger(s"[snapshotForMaxSequenceNrAndMaxTimestamp]: pid: $persistenceId, seqno: $sequenceNr, timestamp: $timestamp")
    snapshotFor(ref, persistenceId)(snap ⇒ snap.sequenceNumber == sequenceNr)
  }

  def snapshotForMaxTimestamp(ref: ActorRef, persistenceId: String, timestamp: Long): Unit =
    snapshotFor(ref, persistenceId)(_.created < timestamp)

  override def receive: Receive = {
    case Delete(persistenceId: String, sequenceNr: Long)                                                        ⇒ delete(sender(), persistenceId, sequenceNr)
    case DeleteAllSnapshots(persistenceId: String)                                                              ⇒ deleteAllSnapshots(sender(), persistenceId)
    case DeleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long)                                      ⇒ deleteUpToMaxTimestamp(sender(), persistenceId, maxTimestamp)
    case DeleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long)                                    ⇒ deleteUpToMaxSequenceNr(sender(), persistenceId, maxSequenceNr)
    case DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long) ⇒ deleteUpToMaxSequenceNrAndMaxTimestamp(sender(), persistenceId, maxSequenceNr, maxTimestamp)
    case Clear                                                                                                  ⇒ clear(sender())
    case Save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte])                  ⇒ save(sender(), persistenceId, sequenceNr, timestamp, snapshot)
    case SnapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long)                                      ⇒ snapshotForMaxSequenceNr(sender(), persistenceId, sequenceNr)
    case SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long)      ⇒ snapshotForMaxSequenceNrAndMaxTimestamp(sender(), persistenceId, sequenceNr, timestamp)
    case SnapshotForMaxTimestamp(persistenceId: String, timestamp: Long)                                        ⇒ snapshotForMaxTimestamp(sender(), persistenceId, timestamp)
  }
}
