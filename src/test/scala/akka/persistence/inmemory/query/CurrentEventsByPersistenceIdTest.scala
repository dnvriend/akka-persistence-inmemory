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

class CurrentEventsByPersistenceIdTest extends QueryTestSpec {

  it should "not find any events for unknown pid" in
    withCurrentEventsByPersistenceId()("unkown-pid", 0L, Long.MaxValue) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

  it should "find events from an offset" in {
    persist(1, 4, "my-1")

    withCurrentEventsByPersistenceId()("my-1", 0, 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 1, 2) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 0, 3) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }

    withCurrentEventsByPersistenceId()("my-1", 1, 3) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectComplete()
    }
  }

  it should "find events for deleted messages" in {
    persist(1, 4, "my-1")

    deleteMessages("my-1", 0)

    withCurrentEventsByPersistenceId()("my-1", 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 1)

    withCurrentEventsByPersistenceId()("my-1", 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 2)

    withCurrentEventsByPersistenceId()("my-1", 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 3)

    withCurrentEventsByPersistenceId()("my-1", 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    deleteMessages("my-1", 4)

    withCurrentEventsByPersistenceId()("my-1", 1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }
}