/*
 * Copyright 2016 Dennis Vriend
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

import akka.actor.{ ActorRef, ActorSystem }
import akka.persistence.inmemory.extension.StorageExtension
import akka.persistence.journal.{ AsyncWriteJournal, Tagged }
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.typesafe.config.Config
import akka.pattern.ask
import akka.persistence.inmemory.dao.InMemoryJournalStorage
import akka.persistence.inmemory.dao.InMemoryJournalStorage.{ JournalEntry, Serialized }
import akka.persistence.inmemory.util.TrySeq
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Flow, Sink, Source }

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class InMemoryAsyncWriteJournal(config: Config) extends AsyncWriteJournal {
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system
  implicit val mat: Materializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(10.seconds)
  val serialization = SerializationExtension(system)

  val journal: ActorRef = StorageExtension(system).journalStorage

  def serialize(persistentRepr: PersistentRepr): Try[(Array[Byte], Set[String])] = persistentRepr.payload match {
    case Tagged(payload, tags) ⇒
      serialization.serialize(persistentRepr.withPayload(payload)).map((_, tags))
    case _ ⇒ serialization.serialize(persistentRepr).map((_, Set.empty[String]))
  }

  def toSerialized(repr: PersistentRepr, arr: Array[Byte], tags: Set[String]): Serialized =
    Serialized(repr.persistenceId, repr.sequenceNr, arr, repr, tags)

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] =
    Source(messages)
      .map(write ⇒ write.payload.map(repr ⇒ serialize(repr).map { case (arr, tags) ⇒ toSerialized(repr, arr, tags) }))
      .map(TrySeq.sequence)
      .map(_.map(xs ⇒ (journal ? InMemoryJournalStorage.WriteList(xs)).map(_ ⇒ ())))
      .mapAsync(1) {
        case Success(future) ⇒ future.map(Success(_))
        case Failure(t)      ⇒ Future.successful(Failure(t))
      }.runWith(Sink.seq)

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    (journal ? InMemoryJournalStorage.Delete(persistenceId, toSequenceNr)).map(_ ⇒ ())

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    (journal ? InMemoryJournalStorage.HighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[Long]

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: (PersistentRepr) ⇒ Unit): Future[Unit] =
    Source.fromFuture((journal ? InMemoryJournalStorage.Messages(persistenceId, fromSequenceNr, toSequenceNr, max)).mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .map(_.serialized.serialized)
      .map(serialization.deserialize(_, classOf[PersistentRepr]))
      .mapAsync(1)(Future.fromTry)
      .runForeach(recoveryCallback)
      .map(_ ⇒ ())

}
