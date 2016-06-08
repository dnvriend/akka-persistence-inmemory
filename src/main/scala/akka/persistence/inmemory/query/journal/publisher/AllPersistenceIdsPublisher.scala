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
import akka.event.{ LoggingAdapter, LoggingReceive }
import akka.persistence.inmemory.dao.JournalDao
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.Materializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, _ }

/**
 * allPersistenceIds which is designed to allow users to subscribe to a stream of all persistent ids in the system.
 * By default this stream should be assumed to be a "live" stream, which means that the journal should keep emitting
 * *new* persistence ids as they come into the system.
 *
 * It is impossible to emit only new persistenceIds without having state ie. knowledge of the knownIds that have been emitted previously.
 * This can be a very expensive operation memory wise when having a lot of PersistenceIds
 */
class AllPersistenceIdsPublisher(journalDao: JournalDao, refreshInterval: FiniteDuration, maxBufferSize: Int)(implicit ec: ExecutionContext, mat: Materializer, log: LoggingAdapter) extends ActorPublisher[String] with DeliveryBuffer[String] with ActorLogging {
  def determineSchedulePoll(): Unit = {
    if (buf.size < maxBufferSize && totalDemand > 0)
      context.system.scheduler.scheduleOnce(0.seconds, self, "POLL")
  }

  val checkPoller = context.system.scheduler.schedule(0.seconds, refreshInterval, self, "CHECK")

  def receive = active(Set.empty[String])

  def active(knownIds: Set[String]): Receive = LoggingReceive {
    case "POLL" ⇒
      journalDao.allPersistenceIdsSource.runFold(List.empty[String])(_ :+ _).map(_.toSet)
        .map { (ids: Set[String]) ⇒
          val xs: Vector[String] = ids.diff(knownIds).toVector
          buf = buf ++ xs
          //          log.debug(s"ids in journal: $ids, known ids: $knownIds, new known ids: ${knownIds ++ xs}, buff: $buf")
          deliverBuf()
          context.become(active(knownIds ++ xs))
        }

    case "CHECK"    ⇒ determineSchedulePoll()

    case _: Request ⇒ deliverBuf()

    case Cancel     ⇒ context.stop(self)
  }

  override def postStop(): Unit = {
    checkPoller.cancel()
    super.postStop()
  }
}
