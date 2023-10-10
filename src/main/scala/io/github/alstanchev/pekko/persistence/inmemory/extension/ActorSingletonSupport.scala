package io.github.alstanchev.pekko.persistence.inmemory.extension

import org.apache.pekko.actor.{ ActorRef, ExtendedActorSystem, Props }

import scala.collection.mutable

private[extension] trait ActorSingletonSupport {
  private val existingActors: mutable.Map[String, ActorRef] = mutable.Map.empty

  final def localNonClusteredActorSingleton(system: ExtendedActorSystem, props: Props, actorName: String): ActorRef = {
    existingActors.get(actorName) match {
      case Some(a) => a
      case None =>
        existingActors.synchronized {
          existingActors.get(actorName) match {
            case Some(a) => a
            case None =>
              val newActor = system.actorOf(props, actorName)
              existingActors.put(actorName, newActor)
              newActor
          }
        }
    }
  }

}
