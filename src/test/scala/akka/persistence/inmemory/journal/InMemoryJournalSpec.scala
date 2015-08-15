package akka.persistence.inmemory.journal

import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

class InMemoryJournalSpec extends JournalSpec(
  config = ConfigFactory.load("application.conf"))