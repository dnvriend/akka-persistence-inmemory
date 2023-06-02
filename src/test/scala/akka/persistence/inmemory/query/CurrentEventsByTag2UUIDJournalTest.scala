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

import akka.persistence.query.{ EventEnvelope, NoOffset, TimeBasedUUID }

/**
 * This test sets the offset-mode to uuid, this means that when a NoOffset type is
 * requested, the offset type in the Envelope will be a TimeBasedUUID else it would
 * be a Sequence
 */
class CurrentEventsByTag2UUIDJournalTest extends QueryTestSpec("uuid-offset-mode.conf") {

  it should "not find events for empty journal using unknown tag using timebased uuid" in {
    withCurrentEventsByTag()("unknown", getNowUUID) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "not find an event by tag for unknown tag from uuid timestamp" in {
    val nowUUID: TimeBasedUUID = getNowUUID
    persist("my-1", "one")
    persist("my-2", "two")
    persist("my-3", "three")

    withCurrentEventsByTag()("unknown", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

    withCurrentEventsByTag()("unknown", nowUUID) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "find all events by tag for timebased uuid" in {
    val id1: TimeBasedUUID = getNowUUID
    persist("my-1", "number")
    val id2: TimeBasedUUID = getNowUUID
    persist("my-2", "number")
    val id3: TimeBasedUUID = getNowUUID
    persist("my-3", "number")

    withCurrentEventsByTag()("number", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-2", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("number", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-2", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("number", id2) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-2", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("number", id3) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("number", getNowUUID) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "find all events by tag for deleted messages for timebased uuid" in {
    val nowUUID: TimeBasedUUID = getNowUUID
    persist("my-1", "number")
    persist("my-2", "number")
    persist("my-3", "number")

    deleteMessages("my-1")
    deleteMessages("my-2")
    deleteMessages("my-3")

    withCurrentEventsByTag()("number", nowUUID) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-2", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }
  }

  it should "persist and find a tagged event with multiple tags for timebased uuids" in {
    val id1: TimeBasedUUID = getNowUUID
    persist(1, 1, "my-1", "one", "1", "prime")
    persist(2, 2, "my-1", "two", "2", "prime")
    persist(3, 3, "my-1", "three", "3", "prime")
    persist(4, 4, "my-1", "four", "4")
    persist(5, 5, "my-1", "five", "5", "prime")
    persist(1, 1, "my-2", "three", "3", "prime")
    persist(1, 1, "my-3", "three", "3", "prime")
    persist(6, 6, "my-1")
    persist(7, 7, "my-1")

    withCurrentEventsByTag()("one", NoOffset) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("one", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("prime", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 2, "a-2") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 3, "a-3") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 5, "a-5") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-2", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("3", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 3, "a-3") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-2", 1, "a-1") => }
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-3", 1, "a-1") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("4", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 4, "a-4") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("four", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 4, "a-4") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("5", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 5, "a-5") => }
      tp.expectComplete()
    }

    withCurrentEventsByTag()("five", id1) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNextPF { case EventEnvelope(TimeBasedUUID(_), "my-1", 5, "a-5") => }
      tp.expectComplete()
    }
  }
}