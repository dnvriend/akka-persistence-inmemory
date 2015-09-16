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

package akka.persistence.inmemory.journal

import akka.actor.{ActorSystem, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigValueFactory, ConfigFactory}

class MyInnerCmdNotSerializable(val value: String) extends MyInnerCmd

class MyInnerCmdSerializable(val value : String) extends MyInnerCmd with Serializable

class MyCommand(val inner: MyInnerCmd) extends Serializable

trait MyInnerCmd {
  val value: String

  override def toString: String = s"${getClass.getSimpleName}:$value"
}

class JournalTest2 extends TestSpec {

  class ObjectStateActor(id: Int) extends PersistentActor {
    var state = ""

    override val persistenceId: String = "c-" + id

    def updateState(cmd: MyCommand): Unit = {
      state = cmd.inner.value
    }

    override def receiveCommand: Receive = LoggingReceive {
      case "state" ⇒
        sender() ! state

      case cmd: MyCommand ⇒
        persist(cmd) { e => updateState(e) }
    }

    override def receiveRecover: Receive = LoggingReceive {
      case cmd: MyCommand ⇒ updateState(cmd)
    }
  }

  "Counter" should "persist state" in {
    val system2 = ActorSystem("mySystem", ConfigFactory.load().withValue("inmemory-journal.doSerialize", fromAnyRef("on")))
    val objSer = new MyCommand(new MyInnerCmdSerializable("qwe"))
    val objNonSer = new MyCommand(new MyInnerCmdNotSerializable("asd"))

    val counter = system2.actorOf(Props(new ObjectStateActor(1)))
    counter ! objSer
    (counter ? "state").futureValue shouldBe objSer.inner.value
    counter ! objNonSer
    (counter ? "state").futureValue shouldBe objSer.inner.value

    val counter2 = system2.actorOf(Props(new ObjectStateActor(2)))
    counter2 ! objNonSer
    (counter2 ? "state").futureValue shouldBe ""
    counter2 ! objSer
    (counter2 ? "state").futureValue shouldBe objSer.inner.value
  }
}
