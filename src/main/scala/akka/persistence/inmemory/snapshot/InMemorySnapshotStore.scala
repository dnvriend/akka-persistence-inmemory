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
package snapshot

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.persistence.inmemory.extension.InMemorySnapshotStorage._
import akka.persistence.inmemory.extension.StorageExtension
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class InMemorySnapshotStore(config: Config) extends SnapshotStore {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.SECONDS) -> SECONDS)
  val serialization = SerializationExtension(system)

  val snapshots: ActorRef = StorageExtension(system).snapshotStorage

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val SnapshotEntryOption: Future[Option[snapshotEntry]] = criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) =>
        (snapshots ? SnapshotForMaxSequenceNr(persistenceId, Long.MaxValue)).mapTo[Option[snapshotEntry]]
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) =>
        (snapshots ? SnapshotForMaxTimestamp(persistenceId, maxTimestamp)).mapTo[Option[snapshotEntry]]
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) =>
        (snapshots ? SnapshotForMaxSequenceNr(persistenceId, maxSequenceNr)).mapTo[Option[snapshotEntry]]
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) =>
        (snapshots ? SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)).mapTo[Option[snapshotEntry]]
      case _ => Future.successful(None)
    }

    Source.fromFuture(SnapshotEntryOption).flatMapConcat { entryOption =>
      Source(entryOption.toList).flatMapConcat { snapshotEntry =>
        Source.fromFuture(Future.fromTry(serialization.deserialize(snapshotEntry.snapshot, classOf[Snapshot])))
          .map { snapshot =>
            SelectedSnapshot(
              SnapshotMetadata(
                snapshotEntry.persistenceId,
                snapshotEntry.sequenceNumber,
                snapshotEntry.created
              ),
              snapshot.data
            )
          }
      }
    }.runWith(Sink.headOption)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = for {
    snapshot <- Future.fromTry(serialization.serialize(Snapshot(snapshot)))
    _ <- snapshots ? Save(metadata.persistenceId, metadata.sequenceNr, metadata.timestamp, snapshot)
  } yield ()

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    (snapshots ? Delete(metadata.persistenceId, metadata.sequenceNr)).map(_ => ())

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = criteria match {
    case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) =>
      (snapshots ? DeleteAllSnapshots(persistenceId)).map(_ => ())
    case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) =>
      (snapshots ? DeleteUpToMaxTimestamp(persistenceId, maxTimestamp)).map(_ => ())
    case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) =>
      (snapshots ? DeleteUpToMaxSequenceNr(persistenceId, maxSequenceNr)).map(_ => ())
    case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) =>
      (snapshots ? DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)).map(_ => ())
    case _ => Future.successful(())
  }
}
