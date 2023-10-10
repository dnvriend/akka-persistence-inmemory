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

import scala.concurrent.duration._

class AllPersistenceIdsTest extends QueryTestSpec {
  it should "not terminate the stream when there are no pids" in
    withAllPersistenceIds() { tp =>
      tp.request(Long.MaxValue)
      tp.expectNoMsg(100.millis)
      tp.cancel()
    }

  it should "find persistenceIds" in {
    withAllPersistenceIds() { tp =>
      tp.request(Long.MaxValue)
      tp.expectNoMsg(100.millis)

      persist(1, 1, "my-1")
      tp.expectNext("my-1")
      tp.expectNoMsg(100.millis)

      persist(1, 1, "my-2")
      tp.expectNext("my-2")
      tp.expectNoMsg(100.millis)

      persist(1, 1, "my-3")
      tp.expectNext("my-3")
      tp.expectNoMsg(100.millis)

      persist(2, 10, "my-1")
      tp.expectNoMsg(100.millis)

      persist(2, 10, "my-2")
      tp.expectNoMsg(100.millis)

      persist(2, 10, "my-3")
      tp.expectNoMsg(100.millis)

      tp.cancel()
    }

    withAllPersistenceIds() { tp =>
      tp.request(Long.MaxValue)
      tp.expectNextUnordered("my-1", "my-2", "my-3")
      tp.cancel()
    }
  }
}