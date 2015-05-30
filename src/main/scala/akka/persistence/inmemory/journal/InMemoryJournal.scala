package akka.persistence.inmemory.journal

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.PersistentRepr
import akka.util.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._

trait JournalEvent

case class WriteMessage(persistenceId: String, message: PersistentRepr) extends JournalEvent

case class DeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean) extends JournalEvent

// API
case class ReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long)

case class ReadHighestSequenceNrResponse(seqNo: Long)

case class ReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)

case class ReplayMessagesResponse(messages: Seq[PersistentRepr])

// general ack
case object JournalAck

case class JournalCache(cache: Map[String, Seq[PersistentRepr]]) {
  def update(event: JournalEvent): JournalCache = event match {
    case WriteMessage(persistenceId, message) if cache.isDefinedAt(persistenceId) =>
      copy(cache = cache + (persistenceId -> (cache(persistenceId) :+ message)))

    case WriteMessage(persistenceId, message) =>
      copy(cache = cache + (persistenceId -> Seq(message)))

    case DeleteMessagesTo(persistenceId, toSequenceNr, true) if cache.isDefinedAt(persistenceId) =>
      copy(cache = cache + (persistenceId -> cache(persistenceId).filterNot(_.sequenceNr <= toSequenceNr)))

    case DeleteMessagesTo(persistenceId, toSequenceNr, false) if cache.isDefinedAt(persistenceId) =>
      val xs1 = cache(persistenceId).filterNot(_.sequenceNr <= toSequenceNr)
      val xs2 = cache(persistenceId).filter(_.sequenceNr <= toSequenceNr).map(_.update(deleted = true))
      copy(cache = cache + (persistenceId -> (xs1 ++ xs2)))

    case DeleteMessagesTo(_, _, _) => this
  }
}

class JournalActor extends Actor {
  var journal = JournalCache(Map.empty[String, Seq[PersistentRepr]])

  override def receive: Receive = {
    case event: JournalEvent =>
      journal = journal.update(event)
      sender() ! JournalAck

    case ReadHighestSequenceNr(persistenceId, fromSequenceNr) if journal.cache.get(persistenceId).exists(_.nonEmpty) =>
      sender() ! ReadHighestSequenceNrResponse(journal.cache(persistenceId).map(_.sequenceNr).max)

    case ReadHighestSequenceNr(persistenceId, fromSequenceNr) =>
      sender() ! ReadHighestSequenceNrResponse(0L)

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) if journal.cache.isDefinedAt(persistenceId) =>
      val takeMax = if (max >= java.lang.Integer.MAX_VALUE) java.lang.Integer.MAX_VALUE else max.toInt
      val messages = journal.cache(persistenceId)
        .filter(repr => repr.sequenceNr >= fromSequenceNr && repr.sequenceNr <= toSequenceNr)
        .sortBy(_.sequenceNr)
        .take(takeMax)
      sender() ! ReplayMessagesResponse(messages)

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) =>
      sender() ! ReplayMessagesResponse(Seq.empty)
  }
}

class InMemoryJournal extends AsyncWriteJournal with ActorLogging {
  implicit val timeout = Timeout(100.millis)
  implicit val ec = context.system.dispatcher
  val journal = context.actorOf(Props(new JournalActor))

  override def asyncWriteMessages(messages: Seq[PersistentRepr]): Future[Unit] = {
    log.debug("asyncWriteMessages: {}", messages)
    Future.sequence(messages.map(repr => journal ? WriteMessage(repr.persistenceId, repr)).toList).map(_ => ())
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Future[Unit] = {
    log.debug("asyncDeleteMessagesTo for processorId: {} to sequenceNr: {}, permanent: {}", persistenceId, toSequenceNr, permanent)
    (journal ? DeleteMessagesTo(persistenceId, toSequenceNr, permanent)).map(_ => ())
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug("Async read for highest sequence number for processorId: {} (hint, seek from  nr: {})", persistenceId, fromSequenceNr)
    (journal ? ReadHighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[ReadHighestSequenceNrResponse].map(_.seqNo)
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug("Async replay for processorId {}, from sequenceNr: {}, to sequenceNr: {} with max records: {}", persistenceId, fromSequenceNr, toSequenceNr, max)
    (journal ? ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max))
      .mapTo[ReplayMessagesResponse]
      .map(_.messages.foreach(replayCallback))
  }
}