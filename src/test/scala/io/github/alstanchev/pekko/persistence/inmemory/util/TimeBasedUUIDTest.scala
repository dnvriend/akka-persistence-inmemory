package io.github.alstanchev.pekko.persistence.inmemory.util

import io.github.alstanchev.pekko.persistence.inmemory.TestSpec

import java.time.{ OffsetDateTime, ZoneOffset }
import org.apache.pekko.persistence.query.TimeBasedUUID

class TimeBasedUUIDTest extends TestSpec {
  it should "compare correctly two TimeBasedUUIDs that differ by a second using Ordering type class'" in {
    val one = TimeBasedUUID(UUIDs.startOf(OffsetDateTime.of(2002, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toEpochSecond * 1000))
    val two = TimeBasedUUID(UUIDs.startOf(OffsetDateTime.of(2002, 1, 1, 0, 0, 1, 0, ZoneOffset.UTC).toEpochSecond * 1000))
    UUIDs.TimeBasedUUIDOrdering.lt(one, two) shouldBe true
    UUIDs.TimeBasedUUIDOrdering.gt(two, one) shouldBe true
  }
}
