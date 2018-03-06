package akka.persistence.inmemory.multistorage

import akka.actor.{ ActorRef, Props }
import akka.persistence.{ PersistentActor, SnapshotOffer }
import akka.persistence.inmemory.TestSpec
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory

case class AddCommand(value: Int)
case class RemoveCommand(value: Int)
case object SnapshotCommand
case object StopCommand
case object ListCommand

abstract class TestActor(id: String) extends PersistentActor {
  var state: Set[Int] = Set()

  override def journalPluginId: String = "inmemory-journal-1"

  override def snapshotPluginId: String = "inmemory-snapshot-store-1"

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, x)         => state = x.asInstanceOf[Set[Int]]
    case x if process.isDefinedAt(x) => process(x)
  }

  override def receiveCommand: Receive = {
    case msg: AddCommand    => persist(msg) { process }
    case msg: RemoveCommand => persist(msg) { process }
    case ListCommand        => sender() ! state
    case SnapshotCommand    => saveSnapshot(state)
    case StopCommand        => context.stop(self)
    case x                  => s"Unhandled $x"
  }

  def process: Receive = {
    case AddCommand(x)    => state = state + x
    case RemoveCommand(x) => state = state - x
  }

  override def persistenceId: String = id
}

class S1TestActor(id: String) extends TestActor(id) {

  override def journalPluginId: String = "inmemory-journal-1"

  override def snapshotPluginId: String = "inmemory-snapshot-store-1"
}

class S2TestActor(id: String) extends TestActor(id) {

  override def journalPluginId: String = "inmemory-journal-2"

  override def snapshotPluginId: String = "inmemory-snapshot-store-2"
}

class MultistorageTest extends TestSpec(ConfigFactory.load("multistorage.conf")) {

  it should "run actors in different journals independed" in {

    val a1 = system.actorOf(Props(new S1TestActor("aaa")), "a1")
    val a2 = system.actorOf(Props(new S2TestActor("aaa")), "a2")

    a1 ! AddCommand(1)
    a2 ! AddCommand(2)
    a1 ! AddCommand(3)
    a2 ! AddCommand(4)

    shutdownActors(a1, a2)

    // not reuse names.  In real life, supervisor will shure that
    //  actors are died and will create same with the same names.
    // In test - we just create actor with different names and same persistenceId
    val a1n = system.actorOf(Props(new S1TestActor("aaa")), "a1n")
    val a2n = system.actorOf(Props(new S2TestActor("aaa")), "a2n")

    val a1l = TestProbe()
    val a2l = TestProbe()

    a1n.tell(ListCommand, a1l.ref)
    a2n.tell(ListCommand, a2l.ref)

    a1l.expectMsg(Set(1, 3))
    a2l.expectMsg(Set(2, 4))

    shutdownActors(a1n, a2n)

  }

  it should "restore actors from different snapshots" in {

    val b1 = system.actorOf(Props(new S1TestActor("bbb")), "b1")
    val b2 = system.actorOf(Props(new S2TestActor("bbb")), "b2")

    b1 ! AddCommand(5)
    b2 ! AddCommand(6)
    b1 ! AddCommand(7)
    b2 ! AddCommand(8)
    b1 ! SnapshotCommand
    b2 ! SnapshotCommand

    shutdownActors(b1, b2)

    val b1n = system.actorOf(Props(new S1TestActor("bbb")), "b1n")
    val b2n = system.actorOf(Props(new S2TestActor("bbb")), "b2n")

    val b1l = TestProbe()
    val b2l = TestProbe()

    b1n.tell(ListCommand, b1l.ref)
    b2n.tell(ListCommand, b2l.ref)

    b1l.expectMsg(Set(5, 7))
    b2l.expectMsg(Set(6, 8))

    shutdownActors(b1n, b2n)

  }

  def shutdownActors(refs: ActorRef*): Unit = {
    val tp = TestProbe()
    for (ref <- refs) {
      tp.watch(ref)
      ref ! StopCommand
      tp.expectTerminated(ref)
    }
  }

}
