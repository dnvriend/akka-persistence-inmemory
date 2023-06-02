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

class EventsByPersistenceIdTest extends QueryTestSpec {

  val expectTime: FiniteDuration = 300.millis

  it should "not find any events for unknown pid" in {
    withEventsByPersistenceId()("unkown-pid", 0L, Long.MaxValue) { tp =>
      tp.request(1)
      tp.expectNoMsg(expectTime)
      tp.cancel()
    }
  }

  it should "find events from an Sequence" in {
    persist(1, 3, "my-1", "number")

    withEventsByPersistenceId()("my-1", 0, 0) { tp =>
      tp.request(Long.MaxValue)
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 0, 1) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 1, 1) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 1, 2) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 2, 2) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 2, 3) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 3, 3) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 0, 3) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }

    withEventsByPersistenceId()("my-1", 1, 3) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }
  }

  it should "find events for actor with pid 'my-1'" in {
    withEventsByPersistenceId()("my-1", 0, Long.MaxValue) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNoMsg(expectTime)

      persist(1, 1, "my-1")
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNoMsg(expectTime)

      persist(2, 2, "my-1")
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNoMsg(expectTime)
      tp.cancel()
    }
  }

  it should "find events for pid 'my-1' and persisting messages to other actor" in {
    withEventsByPersistenceId()("my-1", 0, Long.MaxValue) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNoMsg(expectTime)

      persist(1, 1, "my-1")
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNoMsg(expectTime)

      persist(2, 2, "my-1")
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNoMsg(expectTime)

      persist(1, 3, "my-2")
      tp.expectNoMsg(expectTime)

      persist(3, 3, "my-1")
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNoMsg(expectTime)

      tp.cancel()
    }
  }

  it should "find events for actor with pid 'my-2'" in {

    persist(1, 3, "my-2")

    withEventsByPersistenceId()("my-2", 0, Long.MaxValue) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-2", 1, "a-1"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-2", 2, "a-2"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-2", 3, "a-3"))
      tp.expectNoMsg(expectTime)

      persist(3, 6, "my-2")

      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(4), "my-2", 4, "a-4"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(5), "my-2", 5, "a-5"))
      tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(6), "my-2", 6, "a-6"))
      tp.expectNoMsg(expectTime)

      tp.cancel()
    }
  }
}