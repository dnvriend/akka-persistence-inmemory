package akka.persistence.inmemory.journal

import akka.persistence.journal.LegacyJournalSpec
import com.typesafe.config.ConfigFactory

class InMemoryJournalSpec extends LegacyJournalSpec {
  lazy val config = ConfigFactory.load("application.conf")
}
