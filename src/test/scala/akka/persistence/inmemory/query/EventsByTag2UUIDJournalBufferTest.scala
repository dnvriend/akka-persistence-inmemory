package akka.persistence.inmemory.query

import akka.persistence.query.{EventEnvelope2, NoOffset, Offset, TimeBasedUUID}

import scala.concurrent.duration._

class EventsByTag2UUIDJournalBufferTest extends QueryTestSpec("uuid-offset-mode-buffer.conf") {

  final val NoMsgTime: FiniteDuration = 500.millis
  final val BatchSize: Int = 1000

  // Attempt to reproduce https://github.com/dnvriend/akka-persistence-inmemory/issues/55
  // This test may not fail even if implementation is broken, since it depends on timing and performance.
  // To reproduce issue you perhaps need to play with test's configuration, but on my box it fails 10 out of 10.
  // Successful run took about 11 seconds on my box.
  it should "step over buffer's bound correctly" in {
    persist(1, BatchSize, "my-1", "number")

    withEventsByTag2(30.seconds)("number", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      for (i <- 1 to BatchSize) {
        tp.expectNextPF {
          case EventEnvelope2(TimeBasedUUID(_), "my-1", seq, _) =>
            seq shouldBe i
        }
      }
      tp.expectNoMsg(NoMsgTime)
    }
  }
}
