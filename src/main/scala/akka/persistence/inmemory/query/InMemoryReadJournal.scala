/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.inmemory.query

import akka.actor.{ExtendedActorSystem, Props}
import akka.persistence.query._
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.duration.DurationInt

object InMemoryReadJournal {
  final val Identifier = "inmemory-read-journal"
}

class InMemoryReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal {

  implicit val timeout = Timeout(100.millis)
  implicit val ec = system.dispatcher

  override def query[T, M](q: Query[T, M], hints: Hint*): Source[T, M] = q match {
    case AllPersistenceIds ⇒ allPersistenceIds(hints)
    case unsupported ⇒ Source.failed[T](new UnsupportedOperationException(s"Query $unsupported not supported by ${getClass.getName}")).mapMaterializedValue(_ ⇒ noMaterializedValue)
  }

  def allPersistenceIds(hints: Seq[Hint]): Source[String, Unit] = {
    Source.actorPublisher[String](Props[AllPersistenceIdsPublisher])
      .mapMaterializedValue(_ ⇒ ())
      .named("allPersistenceIds")
  }

  private def noMaterializedValue[M]: M = null.asInstanceOf[M]

}
