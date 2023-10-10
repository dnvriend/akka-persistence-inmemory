package io.github.alstanchev.pekko.persistence.inmemory.query

import org.apache.pekko.persistence.inmemory.TestSpec

import java.util.UUID
import org.scalatest.Ignore

import scala.concurrent.Future

@Ignore
class ConcurrentUUIDTest extends TestSpec {
  def getNow(x: Any): Future[UUID] = Future(org.apache.pekko.persistence.inmemory.nowUuid)
  it should "get uuids concurrently" in {
    Future.sequence((1 to 1000).map(getNow)).futureValue
  }
}
