package io.github.alstanchev.pekko.persistence.inmemory.util

import java.util.UUID

import org.apache.pekko.persistence.query.TimeBasedUUID

import scala.compat.Platform
import scala.util.Random

object UUIDs {
  implicit val TimeBasedUUIDOrdering = new Ordering[TimeBasedUUID] {
    override def compare(x: TimeBasedUUID, y: TimeBasedUUID): Int = {
      val xuuid: UUID = x.value
      val yuuid: UUID = y.value
      val comparedByTimestamp: Int = xuuid.timestamp().compare(yuuid.timestamp())
      if (comparedByTimestamp == 0) {
        val comparedByClock: Int = xuuid.clockSequence().compare(yuuid.clockSequence())
        if (comparedByClock == 0) {
          xuuid.node().compare(yuuid.node())
        } else comparedByClock
      } else comparedByTimestamp
    }
  }

  final val MIN_CLOCK_SEQ_AND_NODE: Long = 0x8080808080808080L
  final val NODE: Long = 256475483242313L

  def startOf(timestamp: Long): UUID = {
    new UUID(makeMSB(UUIDUtil.fromUnixTimestamp(timestamp)), MIN_CLOCK_SEQ_AND_NODE)
  }

  def timeBased(): UUID = {
    new UUID(makeMSB(UUIDUtil.getCurrentTimestamp()), ClockSeqAndNode)
  }

  final val ClockSeqAndNode: Long = {
    val clock: Long = new Random(Platform.currentTime).nextLong()
    0L |
      (clock & 0x0000000000003FFFL) << 48 |
      0x8000000000000000L |
      NODE
  }

  def makeMSB(timestamp: Long): Long = {
    0L |
      (0x00000000ffffffffL & timestamp) << 32 |
      (0x0000ffff00000000L & timestamp) >>> 16 |
      (0x0fff000000000000L & timestamp) >>> 48 |
      0x0000000000001000L
  }

  def unixTimestamp(uuid: UUID): Long = {
    require(uuid.version() == 1, s"Can only retrieve the unix timestamp for version 1 uuid (provided version ${uuid.version})")
    val timestamp = uuid.timestamp()
    (timestamp / 10000) + UUIDUtil.START_EPOCH
  }
}
