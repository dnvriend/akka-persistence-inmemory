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

package io.github.alstanchev.pekko.persistence.inmemory.journal

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.event.{Logging, LoggingAdapter}
import org.apache.pekko.pattern.ask
import io.github.alstanchev.pekko.persistence.inmemory.JournalEntry
import io.github.alstanchev.pekko.persistence.inmemory.extension.{InMemoryJournalStorage, StorageExtensionProvider}
import org.apache.pekko.persistence.journal.{AsyncWriteJournal, Tagged}
import org.apache.pekko.persistence.{AtomicWrite, PersistentRepr}
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.{ActorMaterializer, Materializer}
import org.apache.pekko.util.Timeout
import com.typesafe.config.Config
import io.github.alstanchev.pekko.persistence.inmemory.extension.InMemoryJournalStorage

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InMemoryAsyncWriteJournal(config: Config) extends AsyncWriteJournal {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.SECONDS) -> SECONDS)
  val serialization = SerializationExtension(system)

  val journal: ActorRef = StorageExtensionProvider(system).journalStorage(config)

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

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    val source = Source(immutable.Iterable(messages));

    Source(messages).via(serializer).mapAsync(1) {
      case Success(xs)    => (journal ? InMemoryJournalStorage.WriteList(xs)).map(_ => Success(()))
      case Failure(cause) => Future.successful(Failure(cause))
    }.runWith(Sink.seq)
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    (journal ? InMemoryJournalStorage.Delete(persistenceId, toSequenceNr)).map(_ => ())

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    (journal ? InMemoryJournalStorage.HighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[Long]

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: (PersistentRepr) => Unit): Future[Unit] =
    Source.future((journal ? InMemoryJournalStorage.GetJournalEntriesExceptDeleted(persistenceId, fromSequenceNr, toSequenceNr, max)).mapTo[List[JournalEntry]])
      .mapConcat(identity)
      .via(deserialization)
      .runForeach(recoveryCallback)
      .map(_ => ())

  private val deserialization = Flow[JournalEntry].flatMapConcat { entry =>
    Source.future(Future.fromTry(serialization.deserialize(entry.serialized, classOf[PersistentRepr])))
      .map(_.update(deleted = entry.deleted))
  }
}
