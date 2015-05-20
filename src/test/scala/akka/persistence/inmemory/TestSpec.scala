package akka.persistence.inmemory

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.event.{Logging, LoggingAdapter}
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{FlatSpec, Matchers, OptionValues, TryValues}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

trait TestSpec extends FlatSpec with Matchers with ScalaFutures with TryValues with OptionValues with Eventually {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 5.seconds)
  implicit val timeout = Timeout(5.seconds)

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
    actors.foreach { (actor: ActorRef) =>
      actor ! PoisonPill
      probe watch actor
    }
  }

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
