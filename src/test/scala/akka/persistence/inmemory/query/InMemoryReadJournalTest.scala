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
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer

class InMemoryReadJournalTest extends TestSpec {

  implicit val mat = ActorMaterializer()

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
      case event: Int => updateState(event)
    }
  }

  val readJournal = PersistenceQuery(system).readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

  "ReadJournal" should "support currentPersistenceIds" in {
    val actor1 = system.actorOf(Props(new MyActor(1)))
    val actor2 = system.actorOf(Props(new MyActor(2)))

    (actor1 ? "state").futureValue shouldBe 0
    (actor2 ? "state").futureValue shouldBe 0

    readJournal.currentPersistenceIds().runFold(List[String]()) { (acc, s) => acc.::(s) }.futureValue.sorted shouldBe List("my-1", "my-2")

    actor1 ! 2
    (actor1 ? "state").futureValue shouldBe 2

    actor2 ! 3
    (actor2 ? "state").futureValue shouldBe 3

    readJournal.currentPersistenceIds().runFold(List[String]()) { (acc, s) => acc.::(s) }.futureValue.sorted shouldBe List("my-1", "my-2")
  }

  it should "support currentEventsByPersistenceId" in {
    val actor1 = system.actorOf(Props(new MyActor(1)))
    actor1 ! 1
    actor1 ! 2
    actor1 ! 3

    (actor1 ? "state").futureValue shouldBe 6

    readJournal.currentEventsByPersistenceId("my-1")
      .map(ev => (ev.sequenceNr, ev.event.asInstanceOf[Int]))
      .runFold(Nil.asInstanceOf[List[(Long, Int)]]) { (acc, tuple) =>  acc.::(tuple)}
      .futureValue.sorted shouldBe List((1L, 1), (2L, 2) , (3L, 3))

    readJournal.currentEventsByPersistenceId("my-1", fromSequenceNr =  2)
      .map(ev => (ev.sequenceNr, ev.event.asInstanceOf[Int]))
      .runFold(Nil.asInstanceOf[List[(Long, Int)]]) { (acc, tuple) =>  acc.::(tuple)}
      .futureValue.sorted shouldBe List((2L, 2) , (3L, 3))

    readJournal.currentEventsByPersistenceId("my-1", toSequenceNr =  2)
      .map(ev => (ev.sequenceNr, ev.event.asInstanceOf[Int]))
      .runFold(Nil.asInstanceOf[List[(Long, Int)]]) { (acc, tuple) =>  acc.::(tuple)}
      .futureValue.sorted shouldBe List((1L, 1), (2L, 2))

  }
}
