package akka.persistence.inmemory.journal

import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorLogging
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{PersistentConfirmation, PersistentId, PersistentRepr}

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.Future

class InMemoryJournal extends AsyncWriteJournal with ActorLogging {
  implicit val ec = context.system.dispatcher
  val journal: scala.collection.mutable.Map[String, List[PersistentRepr]] = new ConcurrentHashMap[String, List[PersistentRepr]].asScala

  override def asyncWriteMessages(messages: Seq[PersistentRepr]): Future[Unit] = Future {
    log.debug("writeMessages for {} persistent messages", messages.size)
    messages.foreach { repr =>
      import repr._
      val journalForPersistenceId = journal.get(persistenceId)
      if (journalForPersistenceId.isEmpty) {
        journal.put(processorId, List(repr))
      } else {
        journalForPersistenceId foreach (xs => journal.put(processorId, repr :: xs))
      }
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Future[Unit] = Future {
    log.debug("asyncDeleteMessagesTo for processorId: {} to sequenceNr: {}, permanent: {}", persistenceId, toSequenceNr, permanent)
    if (permanent) {
      journal.get(persistenceId) foreach { list =>
        journal.put(persistenceId, list.filterNot(_.sequenceNr <= toSequenceNr))
      }
    } else {
      journal.get(persistenceId) foreach { list =>
        journal.put(persistenceId, list.map { repr => if (repr.sequenceNr <= toSequenceNr) repr.update(deleted = true) else repr })
      }
    }
  }

  @scala.deprecated("writeConfirmations will be removed, since Channels will be removed.")
  override def asyncWriteConfirmations(confirmations: Seq[PersistentConfirmation]): Future[Unit] = Future {
    log.debug("writeConfirmations for {} messages", confirmations.size)
    confirmations.foreach { confirmation =>
      import confirmation._
      journal.get(persistenceId).foreach { list =>
        journal.put(persistenceId, list.map { msg =>
          if (msg.sequenceNr == sequenceNr) {
            val confirmationIds = msg.confirms :+ confirmation.channelId
            msg.update(confirms = confirmationIds)
          } else msg
        })
      }
    }
  }

  @scala.deprecated("asyncDeleteMessages will be removed.")
  override def asyncDeleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Future[Unit] = Future {
    log.debug("Async delete {} messages, permanent: {}", messageIds.size, permanent)
    messageIds.foreach { persistentId =>
      import persistentId._
      if (permanent) {
        journal.get(processorId) foreach { list =>
          journal.put(processorId, list.filterNot(_.sequenceNr == sequenceNr))
        }
      } else {
        journal.get(processorId) foreach { list =>
          journal.put(processorId, list.map { repr =>
            if (repr.sequenceNr == sequenceNr) repr.update(deleted = true) else repr
          })
        }
      }
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future {
    log.debug("Async read for highest sequence number for processorId: {} (hint, seek from  nr: {})", persistenceId, fromSequenceNr)
    journal.get(persistenceId) match {
      case None | Some(Nil) => 0
      case Some(list) => list.map(_.sequenceNr).max
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = Future {
    log.debug("Async replay for processorId {}, from sequenceNr: {}, to sequenceNr: {} with max records: {}", persistenceId, fromSequenceNr, toSequenceNr, max)
    journal.get(persistenceId) foreach { list =>
      val takeMax = if (max >= java.lang.Integer.MAX_VALUE) java.lang.Integer.MAX_VALUE else max.toInt
      list.filter { repr =>
        repr.sequenceNr >= fromSequenceNr && repr.sequenceNr <= toSequenceNr
      }.sortBy(_.sequenceNr)
        .take(takeMax).foreach(replayCallback)
    }
  }
}