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

case class SnapshotCache(cache: Map[SnapshotCacheKey, SnapshotCacheValue]) {

  def find(persistenceId: String, criteria: SnapshotSelectionCriteria): Option[SelectedSnapshot] = {
    cache.filter {
      case (k, v) =>
        k.persistenceId == persistenceId &&
          k.sequenceNr <= criteria.maxSequenceNr &&
          v.metadata.timestamp <= criteria.maxTimestamp
    }.map {
      case (k, v) => SelectedSnapshot(v.metadata, v.snapshot)
    }.toSeq
      .sortBy(_.metadata.sequenceNr)
      .reverse
      .headOption
  }

  def update(event: SnapshotEvent): SnapshotCache = event match {
    case SaveSnapshot(metadata, snapshot) =>
      copy(cache = cache + (new SnapshotCacheKey(metadata) -> new SnapshotCacheValue(metadata, snapshot)))

    case DeleteSnapshotByMetadata(metadata) =>
      copy(cache = cache - new SnapshotCacheKey(metadata))

    case DeleteSnapshotByCriteria(persistenceId, criteria) =>
      copy(cache = cache.filterNot {
        case (k, v) =>
          k.persistenceId == persistenceId &&
            k.sequenceNr <= criteria.maxSequenceNr &&
            v.metadata.timestamp <= criteria.maxTimestamp
      })
  }
}

case class SnapshotCacheKey(persistenceId: String, sequenceNr: Long) {
  def this(metadata: SnapshotMetadata) = this(metadata.persistenceId, metadata.sequenceNr)
}

case class SnapshotCacheValue(metadata: SnapshotMetadata, snapshot: Any)


class SnapshotActor extends Actor {
  var snapshots = SnapshotCache(Map.empty[SnapshotCacheKey, SnapshotCacheValue])

  override def receive: Receive = {
    case event: SnapshotEvent =>
      snapshots = snapshots.update(event)
      sender() ! SnapshotAck

    case LoadSnapshot(persistenceId, criteria) =>
      val ss = snapshots.find(persistenceId, criteria)
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

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    log.debug("Deleting: {}", metadata)
    (snapshots ? DeleteSnapshotByMetadata(metadata)).map(_ => ())
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug("Deleting for persistenceId: {} and criteria: {}", persistenceId, criteria)
    (snapshots ? DeleteSnapshotByCriteria(persistenceId, criteria)).map(_ => ())
  }
}
