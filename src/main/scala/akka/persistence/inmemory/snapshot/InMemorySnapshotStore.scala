package akka.persistence.inmemory.snapshot

import akka.actor.ActorLogging
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SnapshotMetadata, Persistence, SelectedSnapshot, SnapshotSelectionCriteria}
import akka.serialization.SerializationExtension
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.concurrent.Future

class InMemorySnapshotStore extends SnapshotStore with ActorLogging {
  implicit val system = context.system
  val extension = Persistence(context.system)
  val serialization = SerializationExtension(context.system)
  implicit val executionContext = context.system.dispatcher

  val snapshots: scala.collection.mutable.Map[SnapshotMetadata, Any] = new ConcurrentHashMap[SnapshotMetadata, Any].asScala

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    Future[Option[SelectedSnapshot]] {
      val pid = persistenceId
      val crit = criteria
      log.debug("loading for persistenceId: {}, criteria: {}", pid, crit)
      val snapshotEntries = snapshots.keySet.toList.filter { meta =>
        meta.persistenceId == persistenceId && meta.sequenceNr <= criteria.maxSequenceNr
      }.filterNot(_.timestamp > criteria.maxTimestamp)
       .sortBy(_.sequenceNr)
       .reverse.headOption

      snapshotEntries match {
        case None => None
        case Some(meta) => snapshots.get(meta) match {
          case None => None
          case Some(value) => Some(SelectedSnapshot(meta, value))
        }
      }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    Future[Unit] {
      val meta = metadata
      val snap = snapshot
      log.debug("Saving metadata: {}, snapshot: {}", meta, snap)
      snapshots.put(meta, snap)
    }
  }

  override def saved(metadata: SnapshotMetadata): Unit = log.debug("Saved: {}", metadata)

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
