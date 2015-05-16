package akka.persistence.inmemory.snapshot

import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorLogging
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class InMemorySnapshotStore extends SnapshotStore with ActorLogging {
  implicit val ec = context.system.dispatcher

  val snapshots: scala.collection.mutable.Map[SnapshotMetadata, Any] = new ConcurrentHashMap[SnapshotMetadata, Any].asScala

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = Future {
    log.debug("loading for persistenceId: {}, criteria: {}", persistenceId, criteria)
    snapshots.keySet.toList.filter { meta =>
      meta.persistenceId == persistenceId && meta.sequenceNr <= criteria.maxSequenceNr
    }.filterNot(_.timestamp > criteria.maxTimestamp)
      .sortBy(_.sequenceNr)
      .reverse.headOption
      .flatMap { (meta: SnapshotMetadata) =>
      snapshots.get(meta) map { (snapshot: Any) =>
        SelectedSnapshot(meta, snapshot)
      }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = Future {
    log.debug("Saving metadata: {}, snapshot: {}", metadata, snapshot)
    snapshots.put(metadata, snapshot)
  }

  override def saved(metadata: SnapshotMetadata): Unit =
    log.debug("Saved: {}", metadata)

  override def delete(metadata: SnapshotMetadata): Unit = {
    log.debug("Deleting: {}", metadata)
    snapshots.remove(metadata)
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    log.debug("Deleting for persistenceId: {} and criteria: {}", persistenceId, criteria)
    snapshots.keySet.toList.filter { meta =>
      meta.persistenceId == persistenceId && meta.sequenceNr <= criteria.maxSequenceNr
    }.foreach(snapshots.remove)
  }
}
