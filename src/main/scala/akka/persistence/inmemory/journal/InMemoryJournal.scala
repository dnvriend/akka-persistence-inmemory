package akka.persistence.inmemory.journal

import akka.actor.ActorLogging
import akka.persistence.{PersistentConfirmation, PersistentId, PersistentRepr}
import akka.persistence.journal.AsyncWriteJournal

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.Future

class InMemoryJournal extends AsyncWriteJournal with ActorLogging {
  implicit val ec = context.system.dispatcher
  val journal: scala.collection.mutable.Map[String, List[PersistentRepr]] = new ConcurrentHashMap[String, List[PersistentRepr]].asScala

  override def asyncWriteMessages(messages: Seq[PersistentRepr]): Future[Unit] = {
    Future[Unit] {
      val mess = messages
      log.debug("writeMessages for {} persistent messages", mess.size)
      mess.foreach { repr =>
        import repr._
        journal.get(persistenceId) match {
          case None => journal.put(processorId, List(repr))
          case Some(list) => journal.put(processorId, repr :: list)
        }
      }
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Future[Unit] = {
    Future[Unit] {
      val perm = permanent
      val pid = persistenceId
      val toSeq = toSequenceNr
      log.debug("asyncDeleteMessagesTo for processorId: {} to sequenceNr: {}, permanent: {}", pid, toSeq, perm)
      perm match {
        case true =>
          journal.get(pid) match {
            case None =>
            case Some(list) => journal.put(pid, list.filterNot(_.sequenceNr <= toSeq))
          }
        case false =>
          journal.get(pid) match {
            case None =>
            case Some(list) => journal.put(pid, list.map { repr =>
              if(repr.sequenceNr <= toSeq) repr.update(deleted = true) else repr
            })
          }
      }
    }
  }

  @scala.deprecated("writeConfirmations will be removed, since Channels will be removed.")
  override def asyncWriteConfirmations(confirmations: Seq[PersistentConfirmation]): Future[Unit] = {
    Future[Unit] {
      val confirms = confirmations
      log.debug("writeConfirmations for {} messages", confirms.size)
      confirms.foreach { confirmation =>
        import confirmation._
        journal.get(persistenceId) match {
          case None =>
          case Some(list) =>
            journal.put(persistenceId, list.map { msg =>
              if(msg.sequenceNr == sequenceNr) {
                val confirmationIds = msg.confirms :+ confirmation.channelId
                msg.update(confirms = confirmationIds)
              } else msg
            })
        }
      }
    }
  }

  @scala.deprecated("asyncDeleteMessages will be removed.")
  override def asyncDeleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Future[Unit] = {
    Future[Unit] {
      val mids = messageIds
      val perm = permanent
      log.debug("Async delete {} messages, permanent: {}", mids.size, perm)

      mids.foreach { persistentId =>
        import persistentId._
        perm match {
          case true =>
            journal.get(processorId) match {
              case None =>
              case Some(list) => journal.put(processorId, list.filterNot(_.sequenceNr == sequenceNr))
            }
          case false =>
            journal.get(processorId) match {
              case None =>
              case Some(list) => journal.put(processorId, list.map { repr =>
                if(repr.sequenceNr == sequenceNr) repr.update(deleted = true) else repr
              })
            }
        }
      }
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Future[Long] {
      val pid = persistenceId
      val fromSeq = fromSequenceNr
      log.debug("Async read for highest sequence number for processorId: {} (hint, seek from  nr: {})", pid, fromSeq)
      journal.get(pid) match {
        case None => 0
        case Some(list) => list.map(_.sequenceNr).max
      }
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    Future[Unit] {
      val pid = persistenceId
      val fromSeq = fromSequenceNr
      val toSeq  = toSequenceNr
      val limit = max
      val replay = replayCallback

      log.debug("Async replay for processorId {}, from sequenceNr: {}, to sequenceNr: {} with max records: {}", pid, fromSeq, toSeq, limit)

      journal.get(pid) match {
        case None =>
        case Some(list) =>
          val takeMax = if(limit >= java.lang.Integer.MAX_VALUE) java.lang.Integer.MAX_VALUE else limit.toInt
          list.filter { repr =>
          repr.sequenceNr >= fromSeq && repr.sequenceNr <= toSeq
        }.sortBy(_.sequenceNr)
          .take(takeMax).foreach(replay)
      }
    }
  }
}