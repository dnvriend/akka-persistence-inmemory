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

package io.github.alstanchev.pekko.persistence.inmemory.snapshot

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.{ ActorRef, ActorSystem }
import org.apache.pekko.pattern.ask
import org.apache.pekko.persistence.serialization.Snapshot
import org.apache.pekko.persistence.snapshot.SnapshotStore
import org.apache.pekko.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.stream.{ ActorMaterializer, Materializer }
import org.apache.pekko.util.Timeout
import com.typesafe.config.Config
import io.github.alstanchev.pekko.persistence.inmemory.SnapshotEntry
import io.github.alstanchev.pekko.persistence.inmemory.extension.InMemorySnapshotStorage._
import io.github.alstanchev.pekko.persistence.inmemory.extension.StorageExtensionProvider

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.OptionT
import scalaz.std.AllInstances._

class InMemorySnapshotStore(config: Config) extends SnapshotStore {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(config.getDuration("ask-timeout", TimeUnit.SECONDS) -> SECONDS)
  val serialization = SerializationExtension(system)

  val snapshots: ActorRef = StorageExtensionProvider(system).snapshotStorage(config)

  def deserialize(snapshotEntry: SnapshotEntry): Future[Option[Snapshot]] =
    Future.fromTry(serialization.deserialize(snapshotEntry.snapshot, classOf[Snapshot])).map(Option(_))

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val maybeEntry: Future[Option[SnapshotEntry]] = criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) =>
        (snapshots ? SnapshotForMaxSequenceNr(persistenceId, Long.MaxValue)).mapTo[Option[SnapshotEntry]]
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) =>
        (snapshots ? SnapshotForMaxTimestamp(persistenceId, maxTimestamp)).mapTo[Option[SnapshotEntry]]
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) =>
        (snapshots ? SnapshotForMaxSequenceNr(persistenceId, maxSequenceNr)).mapTo[Option[SnapshotEntry]]
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) =>
        (snapshots ? SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)).mapTo[Option[SnapshotEntry]]
      case _ => Future.successful(None)
    }

    val result = for {
      entry <- OptionT(maybeEntry)
      snapshot <- OptionT(deserialize(entry))
    } yield SelectedSnapshot(SnapshotMetadata(entry.persistenceId, entry.sequenceNumber, entry.created), snapshot.data)

    result.run
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
