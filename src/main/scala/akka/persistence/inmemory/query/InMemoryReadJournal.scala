package akka.persistence.inmemory.query

import akka.persistence.query.{Hint, Query}
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal extends ReadJournal {
  override def query[T, M](q: Query[T, M], hints: Hint*): Source[T, M] = ???
}
