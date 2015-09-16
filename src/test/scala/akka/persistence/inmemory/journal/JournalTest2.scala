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

import akka.actor.Props
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec

class JournalTest2 extends TestSpec {

  class MyInnerCmd(val value : String)

  class MyCommand(val inner: MyInnerCmd) extends Serializable {
    def update(event: String) = new MyCommand(new MyInnerCmd(event))
  }

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

    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      super.onPersistRejected(cause, event, seqNr)
      cause.printStackTrace()
    }
  }

  "Counter" should "persist state" in {
    val obj = new MyCommand(new MyInnerCmd("qwe"))
    val counter = system.actorOf(Props(new ObjectStateActor(1)))
    (counter ? "state").futureValue shouldBe ""
    counter ! obj
    (counter ? "state").futureValue shouldBe obj.inner.value
  }
}
