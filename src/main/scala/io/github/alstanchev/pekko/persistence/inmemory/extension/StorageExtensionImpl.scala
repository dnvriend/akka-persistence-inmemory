package io.github.alstanchev.pekko.persistence.inmemory.extension

import org.apache.pekko.actor.{ ActorRef, ExtendedActorSystem, Props }
import org.apache.pekko.serialization.{ Serialization, SerializationExtension }
import com.typesafe.config.Config

class StorageExtensionImpl()(implicit val system: ExtendedActorSystem) extends StorageExtension with ActorSingletonSupport {
  private val serialization: Serialization = SerializationExtension(system)

  override def journalStorage(config: Config): ActorRef = localNonClusteredActorSingleton(system, Props(new InMemoryJournalStorage(serialization)), "JournalStorage")

  override def snapshotStorage(config: Config): ActorRef = localNonClusteredActorSingleton(system, Props(new InMemorySnapshotStorage), "SnapshotStorage")
}
