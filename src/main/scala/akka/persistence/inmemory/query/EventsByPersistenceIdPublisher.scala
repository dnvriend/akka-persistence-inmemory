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

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.JournalProtocol._
import akka.persistence.Persistence
import akka.persistence.inmemory.journal.InMemoryJournal
import akka.persistence.query.EventEnvelope
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}

import scala.concurrent.duration._

object EventsByPersistenceIdPublisher {
  def props(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, refreshInterval: Option[FiniteDuration], maxBufSize: Int): Props = {
    refreshInterval match {
      case Some(interval) ⇒
        Props(new LiveEventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, toSequenceNr, interval, maxBufSize))
      case None ⇒
        Props(new CurrentEventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, toSequenceNr, maxBufSize))
    }
  }

  case object Continue

}

abstract class AbstractEventsByPersistenceIdPublisher(val persistenceId: String, val fromSequenceNr: Long, val maxBufSize: Int)
  extends ActorPublisher[EventEnvelope] with DeliveryBuffer[EventEnvelope] with ActorLogging {

  import EventsByPersistenceIdPublisher._

  val journal: ActorRef = Persistence(context.system).journalFor(InMemoryJournal.Identifier)

  var currSeqNo = fromSequenceNr

  def toSequenceNr: Long

  def receive = init

  def init: Receive = {
    case _: Request ⇒ receiveInitialRequest()
    case Continue ⇒ // skip, wait for first Request
    case Cancel ⇒ context.stop(self)
  }

  def receiveInitialRequest(): Unit

  def idle: Receive = {
    case Continue | _: InMemoryJournal.EventAppended ⇒
      if (timeForReplay)
        replay()

    case _: Request ⇒
      receiveIdleRequest()

    case Cancel ⇒
      context.stop(self)
  }

  def receiveIdleRequest(): Unit

  def timeForReplay: Boolean =
    (buf.isEmpty || buf.size <= maxBufSize / 2) && (currSeqNo <= toSequenceNr)

  def replay(): Unit = {
    val limit = maxBufSize - buf.size
    log.debug("request replay for persistenceId [{}] from [{}] to [{}] limit [{}]", persistenceId, currSeqNo, toSequenceNr, limit)
    journal ! ReplayMessages(currSeqNo, toSequenceNr, limit, persistenceId, self)
    context.become(replaying(limit))
  }

  def replaying(limit: Int): Receive = {
    case ReplayedMessage(p) ⇒
      buf :+= EventEnvelope(
        offset = p.sequenceNr,
        persistenceId = persistenceId,
        sequenceNr = p.sequenceNr,
        event = p.payload)
      currSeqNo = p.sequenceNr + 1
      deliverBuf()

    case RecoverySuccess(highestSeqNr) ⇒
      log.debug("replay completed for persistenceId [{}], currSeqNo [{}]", persistenceId, currSeqNo)
      receiveRecoverySuccess(highestSeqNr)

    case ReplayMessagesFailure(cause) ⇒
      log.debug("replay failed for persistenceId [{}], due to [{}]", persistenceId, cause.getMessage)
      deliverBuf()
      onErrorThenStop(cause)

    case _: Request ⇒
      deliverBuf()

    case Continue | _: InMemoryJournal.EventAppended ⇒ // skip during replay

    case Cancel ⇒
      context.stop(self)
  }

  def receiveRecoverySuccess(highestSeqNr: Long): Unit
}

class LiveEventsByPersistenceIdPublisher(persistenceId: String, fromSequenceNr: Long, override val toSequenceNr: Long,
                                          refreshInterval: FiniteDuration,
                                          maxBufSize: Int)
  extends AbstractEventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, maxBufSize) {

  import EventsByPersistenceIdPublisher._

  val tickTask =
    context.system.scheduler.schedule(refreshInterval, refreshInterval, self, Continue)(context.dispatcher)

  override def postStop(): Unit =
    tickTask.cancel()

  override def receiveInitialRequest(): Unit = {
    journal ! InMemoryJournal.SubscribePersistenceId(persistenceId)
    replay()
  }

  override def receiveIdleRequest(): Unit = {
    deliverBuf()
    if (buf.isEmpty && currSeqNo > toSequenceNr)
      onCompleteThenStop()
  }

  override def receiveRecoverySuccess(highestSeqNr: Long): Unit = {
    deliverBuf()
    if (buf.isEmpty && currSeqNo > toSequenceNr)
      onCompleteThenStop()
    context.become(idle)
  }

}

class CurrentEventsByPersistenceIdPublisher(persistenceId: String, fromSequenceNr: Long, var toSeqNr: Long, maxBufSize: Int)
  extends AbstractEventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, maxBufSize) {

  import EventsByPersistenceIdPublisher._

  override def toSequenceNr: Long = toSeqNr

  override def receiveInitialRequest(): Unit =
    replay()

  override def receiveIdleRequest(): Unit = {
    deliverBuf()
    if (buf.isEmpty && currSeqNo > toSequenceNr)
      onCompleteThenStop()
    else
      self ! Continue
  }

  override def receiveRecoverySuccess(highestSeqNr: Long): Unit = {
    deliverBuf()
    if (highestSeqNr < toSequenceNr)
      toSeqNr = highestSeqNr
    if (highestSeqNr == 0L || (buf.isEmpty && currSeqNo > toSequenceNr) || currSeqNo == fromSequenceNr)
      onCompleteThenStop()
    else
      self ! Continue // more to fetch
    context.become(idle)
  }
}