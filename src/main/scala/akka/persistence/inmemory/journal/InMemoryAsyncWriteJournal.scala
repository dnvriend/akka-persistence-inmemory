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

package akka.persistence.inmemory
package journal

import java.util.concurrent.TimeUnit

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import akka.pattern.ask
import akka.persistence.inmemory.extension.{ InMemoryJournalStorage, StorageExtension }
import akka.persistence.journal.{ AsyncWriteJournal, Tagged }
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class InMemoryAsyncWriteJournal(config: Config) extends AsyncWriteJournal {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.SECONDS) -> SECONDS)
  val serialization = SerializationExtension(system)

  val journal: ActorRef = StorageExtension(system).journalStorage

  private def serialize(persistentRepr: PersistentRepr): Try[(Array[Byte], Set[String])] = persistentRepr.payload match {
    case Tagged(payload, tags) =>
      serialization.serialize(persistentRepr.withPayload(payload)).map((_, tags))
    case _ => serialization.serialize(persistentRepr).map((_, Set.empty[String]))
  }

  private def payload(persistentRepr: PersistentRepr): PersistentRepr = persistentRepr.payload match {
    case Tagged(payload, _) => persistentRepr.withPayload(payload)
    case _                  => persistentRepr
  }

  private def toJournalEntry(tuple: (Array[Byte], Set[String]), repr: PersistentRepr): JournalEntry = tuple match {
    case (arr, tags) => JournalEntry(repr.persistenceId, repr.sequenceNr, arr, repr, tags)
  }

  val serializer = Flow[AtomicWrite].flatMapConcat { write =>
    Source(write.payload).flatMapConcat { repr =>
      Source.fromFuture(Future.fromTry(serialize(repr)))
        .map(toJournalEntry(_, payload(repr)))
    }.fold(Try(List.empty[JournalEntry])) {
      case (Success(xs), e) => Success(xs :+ e)
      case (c, _)           => c
    }.recover {
      case cause => Failure(cause)
    }
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] =
    Source(messages).via(serializer).mapAsync(1) {
      case Success(xs)    => (journal ? InMemoryJournalStorage.WriteList(xs)).map(_ => Success())
      case Failure(cause) => Future.successful(Failure(cause))
    }.runWith(Sink.seq)

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    (journal ? InMemoryJournalStorage.Delete(persistenceId, toSequenceNr)).map(_ => ())

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    (journal ? InMemoryJournalStorage.HighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[Long]

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: (PersistentRepr) => Unit): Future[Unit] =
    Source.fromFuture((journal ? InMemoryJournalStorage.GetJournalEntriesExceptDeleted(persistenceId, fromSequenceNr, toSequenceNr, max)).mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserialization)
      .runForeach(recoveryCallback)
      .map(_ => ())

  private val deserialization = Flow[JournalEntry].flatMapConcat { entry =>
    Source.fromFuture(Future.fromTry(serialization.deserialize(entry.serialized, classOf[PersistentRepr])))
      .map(_.update(deleted = entry.deleted))
  }
}
