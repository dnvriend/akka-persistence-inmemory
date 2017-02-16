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

package akka.persistence.inmemory.extension

import java.text.SimpleDateFormat

import akka.persistence.inmemory.TestSpec
import akka.persistence.query.{Sequence, TimeBasedUUID}
import akka.persistence.inmemory.util.UUIDs

// see: http://alvinalexander.com/scala/how-sort-scala-sequences-seq-list-array-buffer-vector-ordering-ordered
class OffsetSortingTest extends TestSpec {
  it should "sort a Sequence offset" in {
    val xs = Vector(Sequence(3), Sequence(1), Sequence(2))
    xs.sorted shouldBe Vector(Sequence(1), Sequence(2), Sequence(3))
    xs.sorted(Ordering[Sequence].reverse) shouldBe Vector(Sequence(3), Sequence(2), Sequence(1))

    // you get get the implicit Ordering
    val ordering = implicitly[Ordering[Sequence]]
    // or
    val ordering2 = Ordering[Sequence]
    xs.sorted(ordering) shouldBe Vector(Sequence(1), Sequence(2), Sequence(3))
    xs.sorted(ordering2) shouldBe Vector(Sequence(1), Sequence(2), Sequence(3))
  }

  //  it should "sort a timebaseduuid offset" in {
  //    val sdf = new SimpleDateFormat("yyyy-MM-dd")
  //    val uuid1 = UUIDs.startOf(sdf.parse("2000-01-01").getTime)
  //    val uuid2 = UUIDs.startOf(sdf.parse("2010-01-01").getTime)
  //    val uuid3 = UUIDs.startOf(sdf.parse("2020-01-01").getTime)
  //
  //    val xs = Vector(TimeBasedUUID(uuid3), TimeBasedUUID(uuid1), TimeBasedUUID(uuid2))
  //    xs.sorted shouldBe Vector(TimeBasedUUID(uuid1), TimeBasedUUID(uuid2), TimeBasedUUID(uuid3))
  //  }

  it should "filter a sequence offset" in {
    val xs = Vector(Sequence(3), Sequence(1), Sequence(2))
    xs.filter(_ > Sequence(2)) shouldBe Vector(Sequence(3))
  }

  //  it should "filter a timebaseduuid offset" in {
  //    val sdf = new SimpleDateFormat("yyyy-MM-dd")
  //    val uuid1 = UUIDs.startOf(sdf.parse("2000-01-01").getTime)
  //    val uuid2 = UUIDs.startOf(sdf.parse("2010-01-01").getTime)
  //    val uuid3 = UUIDs.startOf(sdf.parse("2020-01-01").getTime)
  //
  //    val xs = Vector(TimeBasedUUID(uuid3), TimeBasedUUID(uuid1), TimeBasedUUID(uuid2))
  //    // filter uses the Ordering[A] implementation
  //    xs.filter(_ > TimeBasedUUID(uuid2)) shouldBe Vector(TimeBasedUUID(uuid3))
  //  }
}
