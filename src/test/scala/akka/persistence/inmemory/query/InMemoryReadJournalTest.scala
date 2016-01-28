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

package akka.persistence.inmemory.query

import akka.actor.{ ActorRef, Props }
import akka.event.LoggingReceive
import akka.pattern._
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.javadsl.{ TestSink ⇒ JavaSink }
import akka.stream.testkit.scaladsl.TestSink

import scala.concurrent.duration._

class JavaInMemoryReadJournalTest extends InMemoryReadJournalTest {
  val journal = PersistenceQuery.get(system).getReadJournalFor(classOf[JavaDslInMemoryReadJournal], InMemoryReadJournal.Identifier)

  val mapEventEnvelope = new akka.japi.function.Function[EventEnvelope, (Long, Int)]() {
    @throws[Exception](classOf[Exception])
    override def apply(param: EventEnvelope): (Long, Int) = (param.sequenceNr, param.event.asInstanceOf[Int])
  }

  override def currentEventsByPersistenceId(id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.currentEventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(JavaSink.probe(system), mat)

  override def eventsByPersistenceId(id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.eventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(JavaSink.probe(system), mat)

  override def currentPersistenceIds: TestSubscriber.Probe[String] =
    journal.currentPersistenceIds()
      .runWith(JavaSink.probe(system), mat)

  override def allPersistenceIds: TestSubscriber.Probe[String] =
    journal.allPersistenceIds()
      .runWith(JavaSink.probe(system), mat)
}

class ScalaInMemoryReadJournalTest extends InMemoryReadJournalTest {
  val journal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

  override def currentEventsByPersistenceId(id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.currentEventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(TestSink.probe[(Long, Int)])

  override def eventsByPersistenceId(id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.eventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(TestSink.probe[(Long, Int)])

  override def currentPersistenceIds: TestSubscriber.Probe[String] =
    journal.currentPersistenceIds().runWith(TestSink.probe[String])

  override def allPersistenceIds: TestSubscriber.Probe[String] =
    journal.allPersistenceIds().runWith(TestSink.probe[String])

  def mapEventEnvelope(event: EventEnvelope): (Long, Int) = (event.sequenceNr, event.event.asInstanceOf[Int])
}

abstract class InMemoryReadJournalTest extends TestSpec {

  def currentEventsByPersistenceId(id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)]

  def eventsByPersistenceId(id: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)]

  def currentPersistenceIds: TestSubscriber.Probe[String]

  def allPersistenceIds: TestSubscriber.Probe[String]

  case class DeleteCmd(toSequenceNr: Long = Long.MaxValue) extends Serializable

  class MyActor(id: Int) extends PersistentActor {
    override val persistenceId: String = "my-" + id

    var state: Int = 0

    override def receiveCommand: Receive = LoggingReceive {
      case "state" ⇒
        sender() ! state

      case DeleteCmd(toSequenceNr) ⇒
        deleteMessages(toSequenceNr)
        sender() ! s"deleted-$toSequenceNr"

      case event: Int ⇒
        persist(event) { (event: Int) ⇒
          updateState(event)
        }
    }

    def updateState(event: Int): Unit = {
      state = state + event
    }

    override def receiveRecover: Receive = LoggingReceive {
      case event: Int ⇒ updateState(event)
    }
  }

  def setupEmpty(persistenceId: Int): ActorRef =
    system.actorOf(Props(new MyActor(persistenceId)))

  def setup(persistenceId: Int): ActorRef = {
    val actor = setupEmpty(persistenceId)
    actor ! 1
    actor ! 2
    actor ! 3
    (actor ? "state").futureValue shouldBe 6
    actor
  }

  "ReadJournal" should "support currentPersistenceIds" in {
    withActors(setupEmpty(1), setupEmpty(2)) {
      case actor1 :: actor2 :: tail ⇒
        (actor1 ? "state").futureValue shouldBe 0
        (actor2 ? "state").futureValue shouldBe 0

        currentPersistenceIds
          .request(3)
          .expectNextUnordered("my-1", "my-2")
          .expectComplete()
          .cancel()

        actor1 ! 2
        (actor1 ? "state").futureValue shouldBe 2

        actor2 ! 3
        (actor2 ? "state").futureValue shouldBe 3

        currentPersistenceIds
          .request(3)
          .expectNextUnordered("my-1", "my-2")
          .expectComplete()
          .cancel()
    }
  }

  it should "support allPersistenceIds" in {
    withActors(setupEmpty(1), setupEmpty(2), setupEmpty(3)) {
      case actor1 :: actor2 :: actor3 :: tail ⇒
        allPersistenceIds
          .request(3)
          .expectNextUnordered("my-1", "my-2", "my-3")
          .cancel()
    }
  }

  it should "support allPersistenceIds with demand limitation" in {
    withActors(setupEmpty(1), setupEmpty(2), setupEmpty(3)) {
      case actor1 :: actor2 :: actor3 :: _ ⇒
        val probe = allPersistenceIds
        probe.request(1).expectNext("my-1")
        probe.request(2).expectNextUnordered("my-2", "my-3")
        probe.cancel()
    }
  }

  it should "support currentEventsByPersistenceId" in {
    withActors(setup(3)) {
      case actor1 :: _ ⇒
        currentEventsByPersistenceId("my-3")
          .request(4)
          .expectNextUnordered((1L, 1), (2L, 2), (3L, 3))
          .expectComplete()

        currentEventsByPersistenceId("my-3", fromSequenceNr = 2)
          .request(3)
          .expectNextUnordered((2L, 2), (3L, 3))
          .expectComplete()

        currentEventsByPersistenceId("my-3", fromSequenceNr = 3)
          .request(2)
          .expectNext((3L, 3))
          .expectComplete()

        currentEventsByPersistenceId("my-3", toSequenceNr = 2)
          .request(3)
          .expectNextUnordered((1L, 1), (2L, 2))
          .expectComplete()
    }
  }

  it should "return empty stream for cleaned journal from 0 to MaxLong" in {
    withActors(setup(31)) {
      case actor1 :: _ ⇒
        (actor1 ? DeleteCmd(3L)).futureValue shouldBe s"deleted-3"
        currentEventsByPersistenceId("my-31").request(1).expectComplete()
    }
  }

  it should "return empty stream for cleaned journal from 0 to 0" in {
    withActors(setup(32)) {
      case actor1 :: _ ⇒
        (actor1 ? DeleteCmd(3L)).futureValue shouldBe s"deleted-3"
        currentEventsByPersistenceId("my-32", 0L, 0L).request(1).expectComplete()
    }
  }

  it should "return remaining values after partial journal cleanup" in {
    withActors(setup(33)) {
      case actor1 :: _ ⇒
        (actor1 ? DeleteCmd(2L)).futureValue shouldBe s"deleted-2"
        currentEventsByPersistenceId("my-33", 0L, Long.MaxValue).request(1).expectNext((3, 3)).expectComplete()
    }
  }

  it should "return empty stream for empty journal" in {
    withActors(setupEmpty(34)) {
      case actor1 :: _ ⇒
        currentEventsByPersistenceId("my-34", 0L, Long.MaxValue).request(1).expectComplete()
    }
  }

  it should "return empty stream for journal from 0 to 0" in {
    withActors(setup(35)) {
      case actor1 :: _ ⇒
        currentEventsByPersistenceId("my-35", 0L, 0L).request(1).expectComplete()
    }
  }

  it should "return empty stream for empty journal from 0 to 0" in {
    withActors(setupEmpty(36)) {
      case actor1 :: _ ⇒
        currentEventsByPersistenceId("my-36", 0L, 0L).request(1).expectComplete()
    }
  }

  it should "return empty stream for journal from seqNo greater than highestSeqNo" in {
    withActors(setup(37)) {
      case actor1 :: _ ⇒
        currentEventsByPersistenceId("my-37", 4L, 3L).request(1).expectComplete()
    }
  }

  it should "find new events via eventsByPersistenceId" in {
    withActors(setup(4)) {
      case actor1 :: _ ⇒
        val src = eventsByPersistenceId("my-4", 0L, Long.MaxValue)
        src.request(5).expectNext((1L, 1), (2L, 2), (3L, 3))
        actor1 ! 4
        (actor1 ? "state").futureValue shouldBe 10

        src.expectNext((4L, 4))
        src.cancel()
    }
  }

  it should "find new events after stream is created via eventsByPersistenceId" in {
    withActors(setupEmpty(5)) {
      case actor1 :: _ ⇒
        val src = eventsByPersistenceId("my-5", 0L, 2L)
        src.request(2).expectNoMsg(100.millis)

        actor1 ! 1
        actor1 ! 2

        (actor1 ? "state").futureValue shouldBe 3

        src.expectNext((1L, 1), (2L, 2)).expectComplete()
    }
  }

  it should "find new events up to a sequence number via eventsByPersistenceId" in {
    withActors(setup(6)) {
      case actor1 :: _ ⇒
        val probe = eventsByPersistenceId("my-6", 0L, 4L)
        probe.request(5).expectNext((1L, 1), (2L, 2), (3L, 3))

        actor1 ! 4
        (actor1 ? "state").futureValue shouldBe 10

        probe.expectNext((4L, 4)).expectComplete()
        probe.cancel()
    }
  }

  it should "find new events after demand request via eventsByPersistenceId" in {
    withActors(setup(7)) {
      case actor :: _ ⇒
        val probe = eventsByPersistenceId("my-7")
        probe.request(2).expectNext((1L, 1), (2L, 2)).expectNoMsg(100.millis)

        actor ! 4
        (actor ? "state").futureValue shouldBe 10

        probe.expectNoMsg(100.millis).request(5).expectNext((3L, 3), (4L, 4))
        probe.cancel()
    }
  }
}
