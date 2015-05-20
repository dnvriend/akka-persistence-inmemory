package akka.persistence.inmemory.snapshot

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

trait SnapshotEvent

case class SaveSnapshot(metadata: SnapshotMetadata, snapshot: Any) extends SnapshotEvent

case class DeleteSnapshotByMetadata(metadata: SnapshotMetadata) extends SnapshotEvent

case class DeleteSnapshotByCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria) extends SnapshotEvent

// API
case class LoadSnapshot(persistenceId: String, criteria: SnapshotSelectionCriteria)

case class LoadSnapshotResult(selectedSnapshot: Option[SelectedSnapshot])

// general ack
case object SnapshotAck

case class SnapshotCache(cache: Map[SnapshotMetadata, Any]) {
  def update(event: SnapshotEvent): SnapshotCache = event match {
    case SaveSnapshot(metadata, snapshot) =>
      copy(cache = cache + (metadata -> snapshot))

    case DeleteSnapshotByMetadata(metadata) =>
      copy(cache = cache - metadata)

    case DeleteSnapshotByCriteria(persistenceId, criteria) =>
      copy(cache = cache.filterNot {
        case (meta, _) =>
          meta.persistenceId == persistenceId && meta.sequenceNr <= criteria.maxSequenceNr
      })
  }
}

class SnapshotActor extends Actor {
  var snapshots = SnapshotCache(Map.empty[SnapshotMetadata, Any])

  override def receive: Receive = {
    case event: SnapshotEvent =>
      snapshots = snapshots.update(event)
      sender() ! SnapshotAck

    case LoadSnapshot(persistenceId, criteria) =>
      val ss = snapshots.cache.filter {
        case (meta, _) =>
          meta.persistenceId == persistenceId &&
          meta.sequenceNr <= criteria.maxSequenceNr &&
          meta.timestamp <= criteria.maxTimestamp
      }.map {
        case (meta, snap) => SelectedSnapshot(meta, snap)
      }.toSeq
        .sortBy(_.metadata.sequenceNr)
        .reverse
        .headOption

      sender() ! LoadSnapshotResult(ss)
  }
}

class InMemorySnapshotStore extends SnapshotStore with ActorLogging {
  implicit val timeout = Timeout(100.seconds)
  implicit val ec = context.system.dispatcher
  val snapshots = context.actorOf(Props(new SnapshotActor))

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug("loading for persistenceId: {}, criteria: {}", persistenceId, criteria)
    (snapshots ? LoadSnapshot(persistenceId, criteria)).mapTo[LoadSnapshotResult].map(_.selectedSnapshot)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug("Saving metadata: {}, snapshot: {}", metadata, snapshot)
    (snapshots ? SaveSnapshot(metadata, snapshot)).map(_ => ())
  }

  override def saved(metadata: SnapshotMetadata): Unit =
    log.debug("Saved: {}", metadata)

  override def delete(metadata: SnapshotMetadata): Unit = {
    log.debug("Deleting: {}", metadata)
    snapshots ! DeleteSnapshotByMetadata(metadata)
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    log.debug("Deleting for persistenceId: {} and criteria: {}", persistenceId, criteria)
    snapshots ! DeleteSnapshotByCriteria(persistenceId, criteria)
  }
}
