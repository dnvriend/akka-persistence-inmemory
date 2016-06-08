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

package akka.persistence.inmemory.query.journal.publisher

import akka.actor.ActorLogging
import akka.event.LoggingAdapter
import akka.persistence.inmemory.dao.JournalDao
import akka.persistence.query.EventEnvelope
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.Materializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class EventsByPersistenceIdAndTagPublisher(persistenceId: String, tag: String, journalDao: JournalDao, refreshInterval: FiniteDuration, maxBufferSize: Int)(implicit ec: ExecutionContext, mat: Materializer, log: LoggingAdapter) extends ActorPublisher[EventEnvelope] with DeliveryBuffer[EventEnvelope] with ActorLogging {

  def determineSchedulePoll(): Unit = {
    if (buf.size < maxBufferSize && totalDemand > 0)
      context.system.scheduler.scheduleOnce(0.seconds, self, "POLL")
  }

  val checkPoller = context.system.scheduler.schedule(0.seconds, refreshInterval, self, "CHECK")

  def receive = active(0)

  def active(offset: Long): Receive = {
    case "POLL" ⇒
      journalDao.eventsByPersistenceIdAndTag(persistenceId, tag, offset)
    //    case InMemoryJournal.EventAppended(envelope) ⇒
    //      buf :+= envelope
    //      deliverBuf()

    case "CHECK"    ⇒ determineSchedulePoll()

    case _: Request ⇒ deliverBuf()

    case Cancel     ⇒ context.stop(self)
  }

  override def postStop(): Unit = {
    checkPoller.cancel()
    super.postStop()
  }
}
