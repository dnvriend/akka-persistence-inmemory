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
import akka.persistence.query.{AllPersistenceIds, PersistenceQuery}
import akka.stream.ActorMaterializer

class ReadJournalTest extends TestSpec {

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

  "ReadJournal" should "support AllPersistenceIds" in {
    var actor1 = system.actorOf(Props(new MyActor(1)))
    var actor2 = system.actorOf(Props(new MyActor(2)))

    val readJournal = PersistenceQuery(system).readJournalFor(InMemoryReadJournal.Identifier)

    (actor1 ? "state").futureValue shouldBe 0
    (actor2 ? "state").futureValue shouldBe 0

    readJournal.query(AllPersistenceIds).runFold(List[String]()) { (acc, s) => acc.::(s) }.futureValue.sorted shouldBe List("my-1", "my-2")

    actor1 ! 2
    (actor1 ? "state").futureValue shouldBe 2

    actor2 ! 3
    (actor2 ? "state").futureValue shouldBe 3

    readJournal.query(AllPersistenceIds).runFold(List[String]()) { (acc, s) => acc.::(s) }.futureValue.sorted shouldBe List("my-1", "my-2")
  }

  //  it should "not support EventsByPersistenceId" in {
  //    val readJournal = PersistenceQuery(system).readJournalFor(InMemoryReadJournal.Identifier)
  //    readJournal.query(EventsByPersistenceId("1", 1L, 1L)).runFold(List[String]()) { (acc, ev) => acc.::(ev.event.asInstanceOf[String])}.futureValue.sorted shouldBe List("my-1", "my-2")
  //  }

}
