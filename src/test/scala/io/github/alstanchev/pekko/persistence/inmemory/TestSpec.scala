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

package io.github.alstanchev.pekko.persistence.inmemory

import java.text.SimpleDateFormat
import java.util.UUID
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{ ActorRef, ActorSystem, PoisonPill }
import org.apache.pekko.event.{ Logging, LoggingAdapter }
import org.apache.pekko.persistence.query.TimeBasedUUID
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.testkit.TestSubscriber
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.apache.pekko.stream.{ ActorMaterializer, Materializer }
import org.apache.pekko.testkit.TestProbe
import org.apache.pekko.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import io.github.alstanchev.pekko.persistence.inmemory.util.{ ClasspathResources, UUIDs }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.compat.Platform
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Try

abstract class TestSpec(config: Config) extends AnyFlatSpec
  with Matchers
  with ScalaFutures
  with Eventually
  with ClasspathResources
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  def this(config: String = "application.conf") = this(ConfigFactory.load(config))

  implicit val system: ActorSystem = ActorSystem("test", config)
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 60.minutes, interval = 300.millis)
  implicit val timeout = Timeout(60.minutes)
  val serialization = SerializationExtension(system)

  def now: Long = Platform.currentTime
  def getNowUUID: TimeBasedUUID = TimeBasedUUID(UUIDs.timeBased())
  def getTimeBasedUUIDFromTimestamp(timestamp: Long): TimeBasedUUID =
    TimeBasedUUID(UUIDs.startOf(timestamp))
  def getTimestamp(format: String): Long =
    new SimpleDateFormat("yyyy-MM-dd").parse(format).getTime

  final val DATE_FORMAT_TWO_THOUSAND = "2000-01-01"
  final val DATE_FORMAT_TWO_THOUSAND_AND_TEN = "2010-01-01"
  final val DATE_FORMAT_TWO_THOUSAND_AND_TWENTY = "2010-01-01"

  val uuid_two_thousand: TimeBasedUUID = getTimeBasedUUIDFromTimestamp(getTimestamp(DATE_FORMAT_TWO_THOUSAND))
  val uuid_two_thousand_and_ten: TimeBasedUUID = getTimeBasedUUIDFromTimestamp(getTimestamp(DATE_FORMAT_TWO_THOUSAND_AND_TEN))
  val uuid_two_thousand_and_twenty: TimeBasedUUID = getTimeBasedUUIDFromTimestamp(getTimestamp(DATE_FORMAT_TWO_THOUSAND_AND_TWENTY))

  def randomUuid = UUID.randomUUID

  def randomId = randomUuid.toString.take(5)

  def killActors(actors: ActorRef*): Unit = {
    val tp = TestProbe()
    actors.foreach { (actor: ActorRef) =>
      tp watch actor
      actor ! PoisonPill
      tp.expectTerminated(actor)
    }
  }

  def withTestProbe[A](src: Source[A, NotUsed])(f: TestSubscriber.Probe[A] => Unit): Unit =
    f(src.runWith(TestSink.probe(system)))

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

}
