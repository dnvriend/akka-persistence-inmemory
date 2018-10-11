package akka.persistence.inmemory.extension

import akka.actor.{ActorRef, ExtendedActorSystem, Props}
import akka.serialization.{Serialization, SerializationExtension}

class StorageExtensionImpl()(implicit val system: ExtendedActorSystem) extends StorageExtension {
  private val serialization: Serialization = SerializationExtension(system)

  override val journalStorage: ActorRef = system.systemActorOf(Props(new InMemoryJournalStorage(serialization)), "JournalStorage")

  override val snapshotStorage: ActorRef = system.systemActorOf(Props(new InMemorySnapshotStorage), "SnapshotStorage")
}
