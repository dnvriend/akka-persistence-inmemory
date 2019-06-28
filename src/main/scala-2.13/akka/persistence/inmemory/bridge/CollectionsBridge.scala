package akka.persistence.inmemory.bridge

import scala.collection.MapView
import scala.collection.immutable.Map

/**
  * Trait for cross-scala build
  */
trait CollectionsBridge {
  implicit def mapViewToMap[K, V](mapView: MapView[K, V]): Map[K, V] = {
    mapView.toMap
  }
}
