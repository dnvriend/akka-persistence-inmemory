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

package akka.persistence.inmemory.journal

import akka.actor.{ ActorSystem, Props }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef

class MyCmd(val inner: MyInnerCmd) extends Serializable

class MyInnerCmd(val value: String)

class InMemoryJournalTestFullSerialization extends TestSpec {

  class ObjectStateActor(id: Int) extends PersistentActor {
    var state: List[String] = Nil

    override val persistenceId: String = "c-" + id

    def updateState(cmd: MyCmd): Unit = {
      state = cmd.inner.value :: state
    }

    override def receiveCommand: Receive = LoggingReceive {
      case "state" ⇒
        sender() ! state

      case cmd: MyCmd ⇒
        persist(cmd) { e ⇒ updateState(e) }

      case "atomically_fail" ⇒
        // all events will be persisted, else none
        val objSer = new MyCmd(new MyInnerCmd("qwe") with Serializable)
        val objNonSer = new MyCmd(new MyInnerCmd("asd"))
        persistAll(List(objSer, objNonSer, objSer)) { e ⇒ updateState(e) }

      case "atomically_success" ⇒
        // all events will be persisted, else none
        val objSer = new MyCmd(new MyInnerCmd("qwe") with Serializable)
        persistAll(List(objSer, objSer, objSer)) { e ⇒ updateState(e) }
    }

    override def receiveRecover: Receive = LoggingReceive {
      case cmd: MyCmd ⇒ updateState(cmd)
    }
  }

  val objSer = new MyCmd(new MyInnerCmd("qwe") with Serializable)
  val objNonSer = new MyCmd(new MyInnerCmd("asd"))

  "full serialization" should "be performed when flag ON" in {
    val system2 = ActorSystem("mySystem", ConfigFactory.load().withValue("inmemory-journal.full-serialization", fromAnyRef("on")))

    val counter = system2.actorOf(Props(new ObjectStateActor(1)))
    counter ! objSer
    (counter ? "state").futureValue shouldBe List(objSer.inner.value)
    counter ! objNonSer
    (counter ? "state").futureValue shouldBe List(objSer.inner.value)

    val counter2 = system2.actorOf(Props(new ObjectStateActor(2)))
    counter2 ! objNonSer
    (counter2 ? "state").futureValue shouldBe Nil
    counter2 ! objSer
    (counter2 ? "state").futureValue shouldBe List(objSer.inner.value)
    system2.terminate().futureValue
  }

  it should "not be performed by default" in {
    var actor = system.actorOf(Props(new ObjectStateActor(1)))
    actor ! objNonSer
    eventually {
      (actor ? "state").futureValue shouldBe List(objNonSer.inner.value)
    }
    cleanup(actor)
    actor = system.actorOf(Props(new ObjectStateActor(1)))
    eventually {
      (actor ? "state").futureValue shouldBe List(objNonSer.inner.value)
    }
  }

  it should "ignore messages that can not be written to the journal atomically" in {
    val system2 = ActorSystem("mySystem", ConfigFactory.load().withValue("inmemory-journal.full-serialization", fromAnyRef("on")))
    var actor = system2.actorOf(Props(new ObjectStateActor(2)))
    actor ! "atomically_fail"
    eventually {
      (actor ? "state").futureValue shouldBe Nil
    }
    cleanup(actor)
    actor = system2.actorOf(Props(new ObjectStateActor(2)))
    eventually {
      (actor ? "state").futureValue shouldBe Nil
    }
    system2.terminate().futureValue
  }

  it should "not ignore messages that can not be written to the journal atomically" in {
    val system2 = ActorSystem("mySystem", ConfigFactory.load().withValue("inmemory-journal.full-serialization", fromAnyRef("on")))
    var actor = system2.actorOf(Props(new ObjectStateActor(2)))
    actor ! "atomically_success"
    eventually {
      (actor ? "state").futureValue shouldBe List(objSer.inner.value, objSer.inner.value, objSer.inner.value)
    }
    cleanup(actor)
    actor = system2.actorOf(Props(new ObjectStateActor(2)))
    eventually {
      (actor ? "state").futureValue shouldBe List(objSer.inner.value, objSer.inner.value, objSer.inner.value)
    }
    system2.terminate().futureValue
  }
}
