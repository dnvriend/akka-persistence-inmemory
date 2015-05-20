package akka.persistence.inmemory.journal

import akka.actor.{PoisonPill, Props}
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec
import akka.pattern.ask

class JournalTest extends TestSpec {

  case class CounterState(counter: Long) {
    def update(event: String): CounterState = event match {
      case "add" => copy(counter = counter + 1)
      case "sub" => copy(counter = counter - 1)
      case "rst" => copy(counter = 0)
    }
  }

  class Counter extends PersistentActor {
    var state = CounterState(0)

    override val persistenceId: String = "counter"

    def updateState(event: String): Unit = {
      state = state.update(event)
    }

    override def receiveCommand: Receive = {
      case "state" =>
        sender() ! state.counter

      case event: String =>
        persist(event) { event =>
          updateState(event)
        }
    }

    override def receiveRecover: Receive = {
      case event: String => state = state.update(event)
    }
  }


  "Counter" should "persist state" in {
    var counter = system.actorOf(Props(new Counter))
    counter ! "add"
    counter ! "add"
    (counter ? "state").futureValue shouldBe 2
    counter ! PoisonPill
    counter = system.actorOf(Props(new Counter))
    (counter ? "state").futureValue shouldBe 2
  }
}
