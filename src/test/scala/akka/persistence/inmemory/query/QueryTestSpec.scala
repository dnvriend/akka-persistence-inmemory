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

import java.util.UUID

import akka.actor.ActorRef
import akka.persistence.JournalProtocol.{ DeleteMessagesTo, WriteMessageSuccess, WriteMessages, WriteMessagesSuccessful }
import akka.persistence.inmemory.TestSpec
import akka.persistence.inmemory.extension.InMemoryJournalStorage.ClearJournal
import akka.persistence.inmemory.extension.StorageExtensionProvider
import akka.persistence.journal.Tagged
import akka.persistence.query.scaladsl._
import akka.persistence.query.{ EventEnvelope, Offset, PersistenceQuery }
import akka.persistence.{ DeleteMessagesSuccess, _ }
import akka.stream.scaladsl.Sink
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestProbe

import scala.collection.immutable.Seq
import scala.concurrent.duration.{ FiniteDuration, _ }

abstract class QueryTestSpec(config: String = "application.conf") extends TestSpec(config) {

  case class DeleteCmd(toSequenceNr: Long = Long.MaxValue) extends Serializable

  final val ExpectNextTimeout = 10.second

  protected var senderProbe: TestProbe = _
  private var _writerUuid: String = _

  /**
   * Returns the current writer uuid
   */
  def writerUuid: String = _writerUuid

  def withTags(payload: Any, tags: String*) = Tagged(payload, Set(tags: _*))

  implicit lazy val defaultJournal: ActorRef = Persistence(system).journalFor("inmemory-journal")

  implicit lazy val defaultReadJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
    .asInstanceOf[ReadJournal with CurrentPersistenceIdsQuery with PersistenceIdsQuery with CurrentEventsByPersistenceIdQuery with CurrentEventsByTagQuery with EventsByPersistenceIdQuery with EventsByTagQuery]

  def withCurrentPersistenceIds(within: FiniteDuration = 10.seconds)(f: TestSubscriber.Probe[String] => Unit)(implicit readJournal: CurrentPersistenceIdsQuery): Unit = {
    val tp = readJournal.currentPersistenceIds().runWith(TestSink.probe[String])
    tp.within(within)(f(tp))
  }

  def withAllPersistenceIds(within: FiniteDuration = 10.seconds)(f: TestSubscriber.Probe[String] => Unit)(implicit readJournal: PersistenceIdsQuery): Unit = {
    val tp = readJournal.persistenceIds().runWith(TestSink.probe[String])
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(within: FiniteDuration = 10.seconds)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit)(implicit readJournal: CurrentEventsByPersistenceIdQuery): Unit = {
    val tp = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(within: FiniteDuration = 10.seconds)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit)(implicit readJournal: EventsByPersistenceIdQuery): Unit = {
    val tp = readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  //  def withCurrentEventsByTag(within: FiniteDuration = 10.seconds)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit)(implicit readJournal: CurrentEventsByTagQuery): Unit = {
  //    val tp = readJournal.currentEventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
  //    tp.within(within)(f(tp))
  //  }

  def withCurrentEventsByTag(within: FiniteDuration = 10.seconds)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit)(implicit readJournal: CurrentEventsByTagQuery): Unit = {
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  //  def withEventsByTag(within: FiniteDuration = 10.seconds)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit)(implicit readJournal: EventsByTagQuery): Unit = {
  //    val tp = readJournal.eventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
  //    tp.within(within)(f(tp))
  //  }

  def withEventsByTag(within: FiniteDuration = 10.seconds)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit)(implicit readJournal: EventsByTagQuery): Unit = {
    val tp = readJournal.eventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def currentEventsByTagAsList(tag: String, offset: Offset)(implicit readJournal: CurrentEventsByTagQuery): List[EventEnvelope] =
    readJournal.currentEventsByTag(tag, offset).runWith(Sink.seq).futureValue.toList

  /**
   * Persists a single event for a persistenceId with optionally
   * a number of tags.
   */
  def persist(pid: String, tags: String*)(implicit journal: ActorRef): String = {
    writeMessages(journal, 1, 1, pid, senderProbe.ref, writerUuid, tags: _*)
    pid
  }

  /**
   * Persist a number of events for a persistenceId with optionally
   * a number of tags. For example, persist(1, 2, pid, tags) will
   * persist two events with seqno 1 and 2. persist(3,3, pid, tags) will
   * persist a single event with seqno 3. The value associated with the
   * event is 'a-seqno' eg. persist(3, 3, pid, tags) will store value 'a-3'.
   */
  def persist(from: Int, to: Int, pid: String, tags: String*)(implicit journal: ActorRef): String = {
    writeMessages(journal, from, to, pid, senderProbe.ref, writerUuid, tags: _*)
    pid
  }

  private def writeMessages(journal: ActorRef, fromSnr: Int, toSnr: Int, pid: String, sender: ActorRef, writerUuid: String, tags: String*): Unit = {
    def persistentRepr(sequenceNr: Long) =
      PersistentRepr(
        payload = if (tags.isEmpty) s"a-$sequenceNr" else Tagged(s"a-$sequenceNr", Set(tags: _*)),
        sequenceNr = sequenceNr,
        persistenceId = pid,
        sender = sender,
        writerUuid = writerUuid
      )

    val msgs: Seq[PersistentEnvelope] = (fromSnr to toSnr).map(i => AtomicWrite(persistentRepr(i)))

    val probe = TestProbe()

    journal ! WriteMessages(msgs, probe.ref, 1)

    probe.expectMsg(1.hour, WriteMessagesSuccessful)
    fromSnr to toSnr foreach { seqNo =>
      probe.expectMsgPF(1.hour) {
        case WriteMessageSuccess(PersistentImpl(payload, `seqNo`, `pid`, _, _, `sender`, `writerUuid`, _, _), _) =>
          val id = s"a-$seqNo"
          payload should matchPattern {
            case `id`            =>
            case Tagged(`id`, _) =>
          }
        //          println(s"==> written '$payload', for pid: '$pid', seqNo: '$seqNo'")
      }
    }
  }

  /**
   * Deletes messages from the journal.
   */
  def deleteMessages(persistenceId: String, toSequenceNr: Long = Long.MaxValue)(implicit waitAtMost: FiniteDuration = 1.second): Unit = {
    val probe = TestProbe()
    defaultJournal ! DeleteMessagesTo(persistenceId, toSequenceNr, probe.ref)
    probe.expectMsgType[DeleteMessagesSuccess](waitAtMost)
  }

  override protected def beforeEach(): Unit = {
    import akka.pattern.ask
    senderProbe = TestProbe()
    _writerUuid = UUID.randomUUID.toString
    (StorageExtensionProvider(system).journalStorage(system.settings.config) ? ClearJournal).toTry should be a Symbol("success")
    super.beforeEach()
  }

  override protected def afterAll(): Unit = {
    system.terminate().toTry should be a Symbol("success")
  }
}