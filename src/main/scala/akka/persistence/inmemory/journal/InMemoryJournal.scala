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

import akka.actor._
import akka.pattern._
import akka.persistence.inmemory.journal.InMemoryJournal.{ SubscribePersistenceId, AllPersistenceIdsRequest, AllPersistenceIdsResponse, PersistenceIdAdded }
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, Persistence, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.util.Timeout

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

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

case class JournalCache(system: ActorSystem, cache: Map[String, (Long, Seq[PersistentRepr])]) {
  def update(event: JournalEvent): JournalCache = event match {
    case WriteMessages(persistenceId, messages) ⇒
      if (cache.isDefinedAt(persistenceId)) {
        copy(cache = cache + (persistenceId -> (messages.map(_.sequenceNr).max, cache(persistenceId)._2 ++ messages)))
      } else {
        copy(cache = cache + (persistenceId -> (messages.map(_.sequenceNr).max, messages)))
      }

    case DeleteMessagesTo(persistenceId, toSequenceNr, true) if cache.isDefinedAt(persistenceId) ⇒
      val entry = cache(persistenceId)
      copy(cache = cache + (persistenceId -> (entry._1, entry._2.filterNot(_.sequenceNr <= toSequenceNr))))

    case DeleteMessagesTo(persistenceId, toSequenceNr, false) if cache.isDefinedAt(persistenceId) ⇒
      val entry = cache(persistenceId)
      val xs1 = entry._2.filterNot(_.sequenceNr <= toSequenceNr)
      val xs2 = entry._2.filter(_.sequenceNr <= toSequenceNr).map(_.update(deleted = true))
      copy(cache = cache + (persistenceId -> (entry._1, (xs1 ++ xs2))))

    case DeleteMessagesTo(_, _, _) ⇒ this
  }
}

class JournalActor extends Actor {
  var journal = JournalCache(context.system, Map.empty[String, (Long, Seq[PersistentRepr])])

  private val eventByPersistenceIdSubscribers = new mutable.HashMap[String, mutable.Set[ActorRef]] with mutable.MultiMap[String, ActorRef]

  private var allPersistenceIdsSubscribers = Set.empty[ActorRef]

  override def receive: Receive = {
    case event: JournalEvent ⇒
      sender() ! Try({
        journal = journal.update(event)
        JournalAck
      })

    case ReadHighestSequenceNr(persistenceId, fromSequenceNr) if journal.cache.isDefinedAt(persistenceId) ⇒
      sender() ! ReadHighestSequenceNrResponse(journal.cache(persistenceId)._1)

    case ReadHighestSequenceNr(persistenceId, fromSequenceNr) ⇒
      journal = journal.copy(cache = journal.cache + (persistenceId -> (0L, Nil)))
      allPersistenceIdsSubscribers.foreach(_ ! PersistenceIdAdded(persistenceId))
      sender() ! ReadHighestSequenceNrResponse(0L)

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) if journal.cache.isDefinedAt(persistenceId) ⇒
      val takeMax = if (max >= java.lang.Integer.MAX_VALUE) java.lang.Integer.MAX_VALUE else max.toInt
      val messages = journal.cache(persistenceId)._2
        .filter(repr ⇒ repr.sequenceNr >= fromSequenceNr && repr.sequenceNr <= toSequenceNr)
        .sortBy(_.sequenceNr)
        .take(takeMax)
      sender() ! ReplayMessagesResponse(messages)

    case ReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) ⇒
      sender() ! ReplayMessagesResponse(Seq.empty)

    case SubscribePersistenceId(persistenceId) ⇒
      eventByPersistenceIdSubscribers.addBinding(persistenceId, sender())
      context.watch(sender())

    case AllPersistenceIdsRequest ⇒
      allPersistenceIdsSubscribers += sender()
      sender() ! AllPersistenceIdsResponse(journal.cache.keySet)
      context.watch(sender())

    case Terminated(subscriber) ⇒
      eventByPersistenceIdSubscribers
        .collect { case (k, s) if s.contains(subscriber) ⇒ k }
        .foreach { key ⇒ eventByPersistenceIdSubscribers.removeBinding(key, subscriber) }

      allPersistenceIdsSubscribers -= sender()
  }
}

object InMemoryJournal {
  final val Identifier = "inmemory-journal"

  final case class SubscribePersistenceId(persistenceId: String)
  final case class EventAppended(persistenceId: String)

  case object AllPersistenceIdsRequest
  final case class AllPersistenceIdsResponse(allPersistenceIds: Set[String])
  final case class PersistenceIdAdded(persistenceId: String)

  def marshal(repr: PersistentRepr)(implicit serialization: Serialization): Try[PersistentRepr] =
    serialization.serialize(repr.payload.asInstanceOf[AnyRef]).map(_ ⇒ repr)

  def findSerializer(repr: PersistentRepr)(implicit serialization: Serialization): Try[PersistentRepr] =
    Try(serialization.findSerializerFor(repr.payload.asInstanceOf[AnyRef])).map(_ ⇒ repr)

  def marshalPersistentRepresentation(repr: PersistentRepr, doSerialize: Boolean)(implicit serialization: Serialization): Try[(PersistentRepr)] =
    if (doSerialize) marshal(repr) else findSerializer(repr)

  def marshalAtomicWrite(atomicWrite: AtomicWrite, doSerialize: Boolean)(implicit serialization: Serialization): Try[WriteMessages] =
    validateMarshalledAtomicWrite(atomicWrite.persistenceId, atomicWrite.payload.map(repr ⇒ marshalPersistentRepresentation(repr, doSerialize)))

  def validateMarshalledAtomicWrite(persistenceId: String, xs: Seq[Try[PersistentRepr]]): Try[WriteMessages] =
    if (xs.exists(_.isFailure)) xs.filter(_.isFailure).head.map(_ ⇒ WriteMessages(persistenceId, Nil))
    else Try(WriteMessages(persistenceId, xs.collect { case Success(repr) ⇒ repr }))

  def writeToJournal(writeMessages: WriteMessages, journal: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[Try[Unit]] =
    (journal ? writeMessages).map(_ ⇒ Try(Unit))
}

class InMemoryJournal extends AsyncWriteJournal with ActorLogging {

  import InMemoryJournal._

  implicit val timeout: Timeout = Timeout(100.millis)
  implicit val ec: ExecutionContext = context.system.dispatcher
  implicit val serialization: Serialization = SerializationExtension(context.system)
  val journal: ActorRef = context.actorOf(Props(new JournalActor))
  val doSerialize: Boolean = Persistence(context.system).journalConfigFor(InMemoryJournal.Identifier).getBoolean("full-serialization")

  override def receivePluginInternal = {
    case m : ReadHighestSequenceNr =>
      asyncReadHighestSequenceNr(m.persistenceId, m.fromSequenceNr).pipeTo(sender)
    case AllPersistenceIdsRequest ⇒
      journal.forward(AllPersistenceIdsRequest)
    case m: SubscribePersistenceId ⇒
      journal.forward(m)
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    // every AtomicWrite contains a Seq[PersistentRepr], we have a sequence of AtomicWrite
    // and one AtomicWrite must all fail or all succeed
    // xsMarshalled is a converted sequence of AtomicWrite, that denotes whether an AtomicWrite
    // should be persisted (Try = Success) or not (Failed).
    val xsMarshalled: Seq[Try[WriteMessages]] = messages.map(atomicWrite ⇒ marshalAtomicWrite(atomicWrite, doSerialize))
    Future.sequence(xsMarshalled.map {
      case Success(xs) ⇒ writeToJournal(xs, journal)
      case Failure(t)  ⇒ Future.successful(Failure(t))
    })
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