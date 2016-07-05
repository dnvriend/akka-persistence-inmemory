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

package akka.persistence.inmemory.dao

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Status.Success
import akka.actor.{ ActorRef, Props }
import akka.persistence.inmemory.TestSpec
import akka.persistence.inmemory.dao.InMemoryJournalStorage.{ AllPersistenceIds, Delete, Messages, WriteList }
import akka.persistence.inmemory.serialization.Serialized
import akka.testkit.TestProbe

class InMemoryJournalStorageTest extends TestSpec {
  var seq = new AtomicInteger()

  def next = seq.incrementAndGet()

  override protected def beforeEach(): Unit = {
    seq = new AtomicInteger()
    super.beforeEach()
  }

  def event(pid: String): Serialized =
    Serialized(pid, next, Array.empty, None)

  def withJournalStorage(f: (ActorRef, TestProbe) ⇒ Unit): Unit = {
    val ref = system.actorOf(Props[InMemoryJournalStorage])
    val tp = TestProbe()
    try f(ref, tp) finally killActors(ref)
  }

  it should "store events" in withJournalStorage { (ref, tp) ⇒
    tp.send(ref, WriteList(List(event("my-1"), event("my-1"), event("my-1"), event("my-1"))))
    tp.expectMsg(Success(""))
  }

  it should "get messages" in withJournalStorage { (ref, tp) ⇒
    tp.send(ref, WriteList(List(event("my-1"), event("my-1"), event("my-1"), event("my-1"))))
    tp.expectMsg(Success(""))
    tp.send(ref, Messages("my-1", 0, Long.MaxValue, Long.MaxValue))
    tp.expectMsgPF() {
      case List(_, _, _, _) ⇒
    }

    tp.send(ref, Messages("my-1", 4, Long.MaxValue, Long.MaxValue))
    tp.expectMsgPF() {
      case List(_) ⇒
    }
  }

  it should "delete events" in withJournalStorage { (ref, tp) ⇒
    tp.send(ref, WriteList(List(event("my-1"), event("my-1"), event("my-1"), event("my-1"))))
    tp.expectMsg(Success(""))
    tp.send(ref, Delete("my-1", 3))
    tp.expectMsg(Success(""))
    tp.send(ref, Messages("my-1", 0, Long.MaxValue, Long.MaxValue))
    tp.expectMsgPF() {
      case List(_) ⇒
    }
  }

  it should "return pids" in withJournalStorage { (ref, tp) ⇒
    tp.send(ref, WriteList(List(event("my-1"), event("my-1"), event("my-1"), event("my-1"))))
    tp.expectMsg(Success(""))
    tp.send(ref, AllPersistenceIds)
    tp.expectMsg(Set("my-1"))
    tp.send(ref, WriteList(List(event("my-2"), event("my-2"))))
    tp.expectMsg(Success(""))
    tp.send(ref, AllPersistenceIds)
    tp.expectMsg(Set("my-1", "my-2"))
  }

}
