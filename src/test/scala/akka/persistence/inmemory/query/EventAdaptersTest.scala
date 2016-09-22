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

import akka.actor.{ Actor, ActorRef }
import akka.persistence.JournalProtocol._
import akka.persistence.{ AtomicWrite, PersistentEnvelope, PersistentImpl, PersistentRepr }
import akka.persistence.journal._
import akka.persistence.query.EventEnvelope
import akka.testkit.TestProbe

import scala.collection.immutable.Seq

object EventAdaptersTest {
  // set of behavior to distinguish it from Event to make sure that Adapter worked
  case class Event(value: String) {
    def adapted = EventAdapted(value)
  }
  case class EventAdapted(value: String) {
    def restored = EventRestored(value)
  }
  case class EventRestored(value: String)

  class TestReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq = {
      event match {
        case e: EventAdapted => EventSeq.single(e.restored)
      }
    }
  }
  class TestWriteEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""

    override def toJournal(event: Any): Any = {
      event match {
        case e: Event => e.adapted
      }
    }
  }
}

class EventAdaptersTest extends QueryTestSpec {
  import EventAdaptersTest._

  it should "apply event adapters when persists event and when read all events by persistenceId" in {
    val persistedId = "my-1"
    val event = Event("evt-1")
    val Seq(expectedEvent) = new TestReadEventAdapter().fromJournal(event.adapted, "").events
    val seqNo = 10
    val offset = seqNo

    persist(seqNo, persistedId, event)

    withEventsByPersistenceId()(persistedId, seqNo, offset) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(offset, persistedId, seqNo, expectedEvent))
      tp.expectComplete()
    }
  }

  it should "apply event adapters when read tagged events by tag" in {
    val persistedId = "my-2"
    val event = Event("evt-2")
    val seqNo = 20
    val offset = 1
    val tag = "some-tag"

    // have to adapt tagged event by hand
    persist(seqNo, persistedId, Tagged(event.adapted, Set(tag)))

    withCurrentEventsByTag()(tag, offset) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(ExpectNextTimeout, EventEnvelope(offset, persistedId, seqNo, event.adapted.restored))
      tp.expectComplete()
    }
  }

  it should "apply event adapters when replay messages" in {
    val persistedId = "my-3"
    val event = Event("evt-3")
    val seqNo = 10

    persist(seqNo, persistedId, event)

    val receiverProbe = TestProbe()
    journal ! ReplayMessages(1, Long.MaxValue, Long.MaxValue, persistedId, receiverProbe.ref)

    val expectedReplayedMessage = ReplayedMessage(
      PersistentImpl(event.adapted.restored, seqNo, persistedId, "", deleted = false, Actor.noSender, writerUuid)
    )
    receiverProbe.expectMsg(expectedReplayedMessage)
    receiverProbe.expectMsg(RecoverySuccess(highestSequenceNr = seqNo))
  }

  def persist(seqNo: Int, pid: String, payload: Any): Unit = {
    persist(seqNo, pid, senderProbe.ref, writerUuid, payload: Any)
  }

  def persist(seqNo: Int, pid: String, sender: ActorRef, writerUuid: String, payload: Any): Unit = {

    def persistentRepr(sequenceNr: Long) =
      PersistentRepr(
        payload = payload,
        sequenceNr = sequenceNr,
        persistenceId = pid,
        sender = sender,
        writerUuid = writerUuid
      )

    val msgs: Seq[PersistentEnvelope] = Seq(AtomicWrite(persistentRepr(seqNo)))

    val probe = TestProbe()

    journal ! WriteMessages(msgs, probe.ref, 1)

    probe.expectMsg(WriteMessagesSuccessful)
    probe.expectMsgPF() {
      case WriteMessageSuccess(PersistentImpl(`payload`, `seqNo`, `pid`, _, _, `sender`, `writerUuid`), _) =>
    }
  }
}
