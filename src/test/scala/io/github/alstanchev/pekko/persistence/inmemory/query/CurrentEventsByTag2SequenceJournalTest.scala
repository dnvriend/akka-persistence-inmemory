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

package io.github.alstanchev.pekko.persistence.inmemory.query

import org.apache.pekko.persistence.query.{ EventEnvelope, NoOffset, Sequence }

/**
 * This test sets the offset-mode to sequence, this means that when a NoOffset type is
 * requested, the offset type in the Envelope will be a Sequence
 */
class CurrentEventsByTag2SequenceJournaTest extends QueryTestSpec {
  it should "not find an event by tag for unknown tag" in {
    persist("my-1", "one")
    persist("my-2", "two")
    persist("my-3", "three")

    withCurrentEventsByTag2()("unknown", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("unknown", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "find all events by tag" in {
    persist("my-1", "number")
    persist("my-2", "number")
    persist("my-3", "number")

    withCurrentEventsByTag2()("number", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("number", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("number", Sequence(1)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("number", Sequence(2)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("number", Sequence(3)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("number", Sequence(4)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "find all events by tag for deleted messages" in {
    persist("my-1", "number")
    persist("my-2", "number")
    persist("my-3", "number")

    deleteMessages("my-1")
    deleteMessages("my-2")
    deleteMessages("my-3")

    withCurrentEventsByTag2()("number", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }
    withCurrentEventsByTag2()("number", Sequence(1)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }
    withCurrentEventsByTag2()("number", Sequence(2)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }
    withCurrentEventsByTag2()("number", Sequence(3)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
    withCurrentEventsByTag2()("number", Sequence(4)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "persist and find a tagged event with multiple tags" in {
    persist(1, 1, "my-1", "one", "1", "prime")
    persist(2, 2, "my-1", "two", "2", "prime")
    persist(3, 3, "my-1", "three", "3", "prime")
    persist(4, 4, "my-1", "four", "4")
    persist(5, 5, "my-1", "five", "5", "prime")
    persist(1, 1, "my-2", "three", "3", "prime")
    persist(1, 1, "my-3", "three", "3", "prime")
    persist(6, 6, "my-1")
    persist(7, 7, "my-1")

    withCurrentEventsByTag2()("one", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectComplete()
    }
    withCurrentEventsByTag2()("one", Sequence(1)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 5, "a-5"))
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(0)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 5, "a-5"))
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(1)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, "a-2"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 5, "a-5"))
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(2)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 5, "a-5"))
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(3)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(4), "my-1", 5, "a-5"))
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(4)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(5), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(5)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(6), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(6)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("prime", Sequence(7)) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("3", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 3, "a-3"))
      tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, "a-1"))
      tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, "a-1"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("4", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("four", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 4, "a-4"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("5", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 5, "a-5"))
      tp.expectComplete()
    }

    withCurrentEventsByTag2()("five", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext(EventEnvelope(Sequence(1), "my-1", 5, "a-5"))
      tp.expectComplete()
    }
  }
}