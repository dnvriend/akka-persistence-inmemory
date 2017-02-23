package akka.persistence.inmemory.query

import java.util.UUID

import akka.persistence.inmemory.TestSpec
import org.scalatest.Ignore

import scala.concurrent.Future

@Ignore
class ConcurrentUUIDTest extends TestSpec {
  def getNow(x: Any): Future[UUID] = Future(akka.persistence.inmemory.nowUuid)
  it should "get uuids concurrently" in {
    Future.sequence((1 to 1000).map(getNow)).futureValue
  }
}
