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

package akka.persistence.inmemory.query

import akka.persistence.query.{ EventEnvelope, Sequence }

import scala.concurrent.duration._

class EventsByTagTest extends QueryTestSpec {

  final val NoMsgTime: FiniteDuration = 300.millis

  it should "find events for one tag starting with empty journal" in {
    withEventsByTag(10.seconds)("one", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNoMsg(NoMsgTime)

      persist(1, 1, "my-1", "one") // 1
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNoMsg(NoMsgTime)

      persist(1, 1, "my-2", "one") // 2

      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNoMsg(NoMsgTime)

      persist(1, 1, "my-3", "one") // 3
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectNoMsg(NoMsgTime)

      persist(2, 2, "my-1", "two") // 4
      tp.expectNoMsg(NoMsgTime)

      persist(2, 2, "my-2", "two") // 5
      tp.expectNoMsg(NoMsgTime)

      persist(2, 2, "my-3", "two") // 6
      tp.expectNoMsg(NoMsgTime)

      persist(3, 3, "my-1", "one") // 7
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 3, "a-3"))
      tp.expectNoMsg(NoMsgTime)

      persist(3, 3, "my-2", "one") // 8
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 3, "a-3"))
      tp.expectNoMsg(NoMsgTime)

      persist(3, 3, "my-3", "one") // 9
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 3, "a-3"))
      tp.expectNoMsg(NoMsgTime)

      persist(4, 4, "my-1", "two") // 10
      tp.expectNoMsg(NoMsgTime)

      tp.cancel()
    }
  }

  it should "find events for one tag, starting with non-empty journal" in {
    persist(1, 1, "my-1", "number") // 1
    persist(1, 1, "my-2", "number") // 2
    persist(1, 1, "my-3", "number") // 3

    withEventsByTag()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectNoMsg(NoMsgTime)

      persist(2, 2, "my-1", "number") // 4
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, "a-2"))

      persist(3, 3, "my-1", "number") // 5
      tp.expectNext(EventEnvelope(Sequence(5), "my-1", 3, "a-3"))

      persist(4, 4, "my-1", "number") // 6
      tp.expectNext(EventEnvelope(Sequence(6), "my-1", 4, "a-4"))

      persist(2, 2, "my-2", "number") // 7
      tp.expectNext(EventEnvelope(Sequence(7), "my-2", 2, "a-2"))

      persist(2, 2, "my-3", "number") // 8
      tp.expectNext(EventEnvelope(Sequence(8), "my-3", 2, "a-2"))

      tp.cancel()
    }
  }

  it should "find deleted events" in {
    persist(1, 4, "my-1", "number")

    deleteMessages("my-1", 0)

    withCurrentEventsByTag()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 1)

    withCurrentEventsByTag()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 2)

    withCurrentEventsByTag()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 3)

    withCurrentEventsByTag()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 4)

    withCurrentEventsByTag()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }
  }
}

