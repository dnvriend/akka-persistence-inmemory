package akka.persistence.inmemory.query

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.Persistence
import akka.persistence.inmemory.journal.InMemoryJournal
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}

class AllPersistenceIdsPublisher extends ActorPublisher[String] with DeliveryBuffer[String] with ActorLogging {

  val journal: ActorRef = Persistence(context.system).journalFor(InMemoryJournal.Identifier)

  def receive = init

  def init: Receive = {
    case _: Request ⇒
      journal ! InMemoryJournal.AllPersistenceIdsRequest
      context.become(active)
    case Cancel ⇒ context.stop(self)
  }

  def active: Receive = {
    case InMemoryJournal.AllPersistenceIdsResponse(allPersistenceIds) ⇒
      buf ++= allPersistenceIds
      deliverBuf()
      if (buf.isEmpty) onCompleteThenStop()

    case _: Request ⇒
      deliverBuf()
      if (buf.isEmpty) onCompleteThenStop()

    case Cancel ⇒ context.stop(self)
  }

}

