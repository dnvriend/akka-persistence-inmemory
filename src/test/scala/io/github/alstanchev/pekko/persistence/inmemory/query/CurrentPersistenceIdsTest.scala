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

import org.apache.pekko.persistence.inmemory.query.QueryTestSpec

class CurrentPersistenceIdsTest extends QueryTestSpec {

  it should "not find any persistenceIds for empty journal" in
    withCurrentPersistenceIds() { tp =>
      tp.request(1)
      tp.expectComplete()
    }

  it should "find persistenceIds" in {
    persist("my-1")
    persist("my-2")
    persist("my-3")

    withCurrentPersistenceIds() { tp =>
      tp.request(3)
      tp.expectNextUnordered("my-1", "my-2", "my-3")
      tp.expectComplete()
    }
  }

  it should "find persistenceIds for deleted messages" in {
    persist("my-1")
    persist("my-2")
    persist("my-3")

    withCurrentPersistenceIds() { tp =>
      tp.request(3)
      tp.expectNextUnordered("my-1", "my-2", "my-3")
      tp.expectComplete()
    }

    deleteMessages("my-1")
    deleteMessages("my-2")
    deleteMessages("my-3")

    withCurrentPersistenceIds() { tp =>
      tp.request(3)
      tp.expectNextUnordered("my-1", "my-2", "my-3")
      tp.expectComplete()
    }
  }
}