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

import akka.actor.Props
import akka.event.LoggingReceive
import akka.pattern._
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink

class InMemoryReadJournalTest extends TestSpec {
  val readJournal: InMemoryReadJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

  class MyActor(id: Int) extends PersistentActor {
    override val persistenceId: String = "my-" + id

    var state: Int = 0

    override def receiveCommand: Receive = LoggingReceive {
      case "state" ⇒
        sender() ! state

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

  def mapEventEnvelope: PartialFunction[EventEnvelope, (Long, Int)] = {
    case EventEnvelope(offset, persistenceId, sequenceNr, event: Int) ⇒ (sequenceNr, event)
    case _                                                            ⇒ throw new RuntimeException("Unexpected event type")
  }

  def currentEventsByPersistenceId(journal: InMemoryReadJournal, id: String, fromSequenceNr: Int = 0, toSequenceNr: Long = Long.MaxValue): TestSubscriber.Probe[(Long, Int)] =
    journal.currentEventsByPersistenceId(id, fromSequenceNr, toSequenceNr)
      .map(mapEventEnvelope)
      .runWith(TestSink.probe[(Long, Int)])

  def currentPersistenceIds(journal: InMemoryReadJournal): TestSubscriber.Probe[String] =
    journal.currentPersistenceIds().runWith(TestSink.probe[String])

  def allPersistenceIds(journal: InMemoryReadJournal): TestSubscriber.Probe[String] =
    journal.allPersistenceIds().runWith(TestSink.probe[String])

  "ReadJournal" should "support currentPersistenceIds" in {
    val actor1 = system.actorOf(Props(new MyActor(1)))
    val actor2 = system.actorOf(Props(new MyActor(2)))

    (actor1 ? "state").futureValue shouldBe 0
    (actor2 ? "state").futureValue shouldBe 0

    currentPersistenceIds(readJournal)
      .request(3)
      .expectNextUnordered("my-1", "my-2")
      .expectComplete()

    actor1 ! 2
    (actor1 ? "state").futureValue shouldBe 2

    actor2 ! 3
    (actor2 ? "state").futureValue shouldBe 3

    currentPersistenceIds(readJournal)
      .request(3)
      .expectNextUnordered("my-1", "my-2")
      .expectComplete()

    cleanup(actor1, actor2)
  }

  it should "support allPersistenceIds" in {
    val source = allPersistenceIds(readJournal)

    val actor1 = system.actorOf(Props(new MyActor(1)))
    source.request(3).expectNext("my-1")

    val actor2 = system.actorOf(Props(new MyActor(2)))
    source.expectNext("my-2")

    source.cancel()
    val actor3 = system.actorOf(Props(new MyActor(3)))

    source.expectNoMsg()

    cleanup(actor1, actor2, actor3)
  }

  it should "support currentEventsByPersistenceId" in {
    val actor3 = system.actorOf(Props(new MyActor(3)))
    actor3 ! 1
    actor3 ! 2
    actor3 ! 3

    (actor3 ? "state").futureValue shouldBe 6

    currentEventsByPersistenceId(readJournal, "my-3")
      .request(4)
      .expectNextUnordered((1L, 1), (2L, 2), (3L, 3))
      .expectComplete()

    currentEventsByPersistenceId(readJournal, "my-3", fromSequenceNr = 2)
      .request(3)
      .expectNextUnordered((2L, 2), (3L, 3))
      .expectComplete()

    currentEventsByPersistenceId(readJournal, "my-3", fromSequenceNr = 3)
      .request(2)
      .expectNext((3L, 3))
      .expectComplete()

    currentEventsByPersistenceId(readJournal, "my-3", toSequenceNr = 2)
      .request(3)
      .expectNextUnordered((1L, 1), (2L, 2))
      .expectComplete()

    cleanup(actor3)
  }
}
