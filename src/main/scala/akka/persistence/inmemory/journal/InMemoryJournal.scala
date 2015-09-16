/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.inmemory.journal

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.pattern.ask
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.SerializationExtension
import akka.util.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait JournalEvent

case class WriteMessages(persistenceId: String, messages: Seq[PersistentRepr]) extends JournalEvent

case class DeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean = true) extends JournalEvent

// API
case class ReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long)

case class ReadHighestSequenceNrResponse(seqNo: Long)

case class ReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)

case class ReplayMessagesResponse(messages: Seq[PersistentRepr])

// general ack
case object JournalAck

case class JournalCache(system: ActorSystem, cache: Map[String, Seq[PersistentRepr]]) {
  val serialization = SerializationExtension(system)

  def update(event: JournalEvent): JournalCache = event match {
    case WriteMessages(persistenceId, messages) ⇒
//      messages.foreach(m ⇒ serialization.findSerializerFor(m.payload.asInstanceOf[AnyRef]))
      messages.foreach(m ⇒ serialization.serialize(m.payload.asInstanceOf[AnyRef]))
      if (cache.isDefinedAt(persistenceId)) {
        copy(cache = cache + (persistenceId -> (cache(persistenceId) ++ messages)))
      } else {
        copy(cache = cache + (persistenceId -> messages))
      }

    case DeleteMessagesTo(persistenceId, toSequenceNr, true) if cache.isDefinedAt(persistenceId) ⇒
      copy(cache = cache + (persistenceId -> cache(persistenceId).filterNot(_.sequenceNr <= toSequenceNr)))

    case DeleteMessagesTo(persistenceId, toSequenceNr, false) if cache.isDefinedAt(persistenceId) ⇒
      val xs1 = cache(persistenceId).filterNot(_.sequenceNr <= toSequenceNr)
      val xs2 = cache(persistenceId).filter(_.sequenceNr <= toSequenceNr).map(_.update(deleted = true))
      copy(cache = cache + (persistenceId -> (xs1 ++ xs2)))

    case DeleteMessagesTo(_, _, _) ⇒ this
  }
}

class JournalActor extends Actor {
  var journal = JournalCache(context.system, Map.empty[String, Seq[PersistentRepr]])

  override def receive: Receive = {
    case event: JournalEvent ⇒
      sender() ! Try({
        journal = journal.update(event)
        JournalAck
      })

    case ReadHighestSequenceNr(persistenceId, fromSequenceNr) if journal.cache.get(persistenceId).exists(_.nonEmpty) ⇒
      sender() ! ReadHighestSequenceNrResponse(journal.cache(persistenceId).map(_.sequenceNr).max)

    case ReadHighestSequenceNr(persistenceId, fromSequenceNr) ⇒
      sender() ! ReadHighestSequenceNrResponse(0L)

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) if journal.cache.isDefinedAt(persistenceId) ⇒
      val takeMax = if (max >= java.lang.Integer.MAX_VALUE) java.lang.Integer.MAX_VALUE else max.toInt
      val messages = journal.cache(persistenceId)
        .filter(repr ⇒ repr.sequenceNr >= fromSequenceNr && repr.sequenceNr <= toSequenceNr)
        .sortBy(_.sequenceNr)
        .take(takeMax)
      sender() ! ReplayMessagesResponse(messages)

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) ⇒
      sender() ! ReplayMessagesResponse(Seq.empty)
  }
}

class InMemoryJournal extends AsyncWriteJournal with ActorLogging {
  implicit val timeout = Timeout(100.millis)
  implicit val ec = context.system.dispatcher
  val journal = context.actorOf(Props(new JournalActor))

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    log.debug("Async write messages: {}", messages)
    Future.sequence(messages.map(m ⇒ journal ? WriteMessages(m.persistenceId, m.payload))).mapTo[Seq[Try[Unit]]]
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    log.debug("Async delete messages for processorId: {} to sequenceNr: {}", persistenceId, toSequenceNr)
    (journal ? DeleteMessagesTo(persistenceId, toSequenceNr)).map(_ ⇒ ())
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug("Async read for highest sequence number for processorId: {} (hint, seek from  nr: {})", persistenceId, fromSequenceNr)
    (journal ? ReadHighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[ReadHighestSequenceNrResponse].map(_.seqNo)
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) ⇒ Unit): Future[Unit] = {
    log.debug("Async replay for processorId {}, from sequenceNr: {}, to sequenceNr: {} with max records: {}", persistenceId, fromSequenceNr, toSequenceNr, max)
    (journal ? ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max))
      .mapTo[ReplayMessagesResponse]
      .map(_.messages.foreach(replayCallback))
  }
}
