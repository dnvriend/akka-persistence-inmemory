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

import java.util.UUID

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.Persistence
import akka.persistence.inmemory.journal.InMemoryJournal
import akka.persistence.inmemory.journal.InMemoryJournal.ResetJournal
import akka.persistence.inmemory.query.InMemoryReadJournal
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{ FlatSpec, Matchers, OptionValues, TryValues }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Try

trait TestSpec extends FlatSpec with Matchers with ScalaFutures with TryValues with OptionValues with Eventually {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 3.seconds)
  implicit val timeout = Timeout(30.seconds)

  val journal: ActorRef = Persistence(system).journalFor(InMemoryJournal.Identifier)

  /**
   * TestKit-based probe which allows sending, reception and reply.
   */
  def probe: TestProbe = TestProbe()

  /**
   * Returns a random UUID
   */
  def randomId = UUID.randomUUID.toString.take(5)

  /**
   * Sends the PoisonPill command to an actor and waits for it to die
   */
  def cleanup(actors: ActorRef*): Unit = {
    val probe = TestProbe()
    actors.foreach { (actor: ActorRef) ⇒
      actor ! PoisonPill
      probe watch actor
      probe.expectTerminated(actor)
    }
  }

  /**
   * Clears the journal
   */
  def clearJournal: Unit = {
    journal ! ResetJournal
  }

  def withActors(actors: ActorRef*)(pf: PartialFunction[List[ActorRef], Unit]): Unit = {
    pf.applyOrElse(actors.toList, PartialFunction.empty)
    cleanup(actors: _*)
    clearJournal
  }

  def mapEventEnvelope: PartialFunction[EventEnvelope, (Long, Int)] = {
    case EventEnvelope(offset, persistenceId, sequenceNr, event: Int) ⇒ (sequenceNr, event)
    case _                                                            ⇒ throw new RuntimeException("Unexpected event type")
  }

  def currentEventsByPersistenceId(journal: InMemoryReadJournal, id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.currentEventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(TestSink.probe[(Long, Int)])

  def eventsByPersistenceId(journal: InMemoryReadJournal, id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.eventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(TestSink.probe[(Long, Int)])

  def currentPersistenceIds(journal: InMemoryReadJournal): TestSubscriber.Probe[String] =
    journal.currentPersistenceIds().runWith(TestSink.probe[String])

  def allPersistenceIds(journal: InMemoryReadJournal): TestSubscriber.Probe[String] =
    journal.allPersistenceIds().runWith(TestSink.probe[String])

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

  implicit class MustBeWord[T](self: T) {
    def mustBe(pf: PartialFunction[T, Unit]): Unit =
      if (!pf.isDefinedAt(self)) throw new TestFailedException("Unexpected: " + self, 0)
  }
}
