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
package query

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.persistence.query.scaladsl.CurrentPersistenceIdsQuery
import akka.stream.Materializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.scaladsl.Sink

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, _ }

object AllPersistenceIdsPublisher {
  sealed trait AllPersistenceIdsPublisherCommand
  case object GetAllPersistenceIds extends AllPersistenceIdsPublisherCommand
  case object BecomePolling extends AllPersistenceIdsPublisherCommand
  case object DetermineSchedulePoll extends AllPersistenceIdsPublisherCommand
}

class AllPersistenceIdsPublisher(refreshInterval: FiniteDuration, maxBufferSize: Int, query: CurrentPersistenceIdsQuery)(implicit ec: ExecutionContext, mat: Materializer) extends ActorPublisher[String] with DeliveryBuffer[String] with ActorLogging {
  import AllPersistenceIdsPublisher._

  def determineSchedulePoll(): Unit =
    if (buf.size < maxBufferSize && totalDemand > 0) context.system.scheduler.scheduleOnce(0.seconds, self, BecomePolling)

  val checkPoller = context.system.scheduler.schedule(0.seconds, refreshInterval, self, DetermineSchedulePoll)

  def receive = active(Set.empty[String])

  def polling(knownIds: Set[String]): Receive = LoggingReceive {
    case GetAllPersistenceIds ⇒
      query.currentPersistenceIds().take(Math.max(0, maxBufferSize - buf.size))
        .runWith(Sink.seq).map { ids ⇒
          val xs: Vector[String] = ids.toSet.diff(knownIds)
          buf = buf ++ xs
          deliverBuf()
          context.become(active(knownIds ++ xs))
        }.recover {
          case t: Throwable ⇒
            log.error(t, "Error while polling allPersistenceIds")
            onError(t)
            context.stop(self)
        }

    case Cancel ⇒ context.stop(self)
  }

  def active(knownIds: Set[String]): Receive = LoggingReceive {
    case BecomePolling ⇒
      context.become(polling(knownIds))
      self ! GetAllPersistenceIds

    case DetermineSchedulePoll if (buf.size - totalDemand) < 0 ⇒ determineSchedulePoll()

    case DetermineSchedulePoll                                 ⇒ deliverBuf()

    case Request(req)                                          ⇒ deliverBuf()

    case Cancel                                                ⇒ context.stop(self)
  }

  override def postStop(): Unit = {
    checkPoller.cancel()
    super.postStop()
  }
}
