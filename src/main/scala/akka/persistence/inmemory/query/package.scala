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

package akka.persistence.inmemory

import akka.NotUsed
import akka.persistence.query._
import akka.stream.scaladsl.Source
import scala.language.implicitConversions

package object query {
  def toOldEnvelope(env2: EventEnvelope2): EventEnvelope = env2 match {
    case EventEnvelope2(Sequence(offset), persistenceId, sequenceNr, event) =>
      EventEnvelope(offset, persistenceId, sequenceNr, event)
  }

  implicit def newSrcToOldSrc(that: Source[EventEnvelope2, NotUsed]): Source[EventEnvelope, NotUsed] =
    that.map(toOldEnvelope)
}
