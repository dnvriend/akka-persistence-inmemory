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

package io.github.alstanchev.pekko.persistence.inmemory.util

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.testkit.TestSubscriber
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.scalatest.Suite

import scala.collection.immutable._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

trait PekkoStreamUtils extends AnyFlatSpecLike with Matchers { _: Suite =>
  implicit def mat: Materializer
  implicit def system: ActorSystem

  implicit class SourceOps[A, M](src: Source[A, M]) {
    def testProbe(f: TestSubscriber.Probe[A] => Unit): Unit =
      f(src.runWith(TestSink.probe(system)))
  }

  implicit class TestProbeOps[A](tp: TestSubscriber.Probe[A]) {
    def assertNext(right: PartialFunction[Any, _]) = tp.requestNext() should matchPattern(right)
  }

  def withIteratorSrc[T](start: Int = 0)(f: Source[Int, NotUsed] => Unit): Unit =
    f(Source.fromIterator(() => Iterator from start))

  def fromCollectionProbe[A](xs: Seq[A])(f: TestSubscriber.Probe[A] => Unit): Unit =
    f(Source(xs).runWith(TestSink.probe(system)))
}
