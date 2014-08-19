package akka.persistence.inmemory.snapshot

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

class InMemorySnapshotStoreSpec extends SnapshotStoreSpec {
  lazy val config = ConfigFactory.load("application.conf")
}