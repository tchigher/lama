package co.ledger.lama.common.models

import co.ledger.lama.common.models.Status._
import io.circe.{Decoder, Encoder}

sealed trait Status {
  def name: String
}

object Status {
  // Registered event sent to worker for sync.
  case object Registered extends WorkableStatus("registered", Synchronized, SyncFailed)

  // Unregistered event sent to worker for delete data.
  case object Unregistered extends WorkableStatus("unregistered", Deleted, DeleteFailed)

  // Published event.
  case object Published extends FlaggedStatus("published")

  // Successful sync event reported by worker.
  case object Synchronized extends ReportableStatus("synchronized") with TriggerableStatus {
    def nextWorkable: WorkableStatus = Registered
  }

  // Failed sync event reported by worker.
  case object SyncFailed extends ReportableStatus("sync_failed") with TriggerableStatus {
    def nextWorkable: WorkableStatus = Registered
  }

  // Successful delete event reported by worker.
  case object Deleted extends ReportableStatus("deleted")

  // Failed delete event reported by worker.
  case object DeleteFailed extends ReportableStatus("delete_failed") with TriggerableStatus {
    def nextWorkable: WorkableStatus = Unregistered
  }

  val all: Map[String, Status] =
    WorkableStatus.all ++ FlaggedStatus.all ++ ReportableStatus.all ++ TriggerableStatus.all

  def fromKey(key: String): Option[Status] = all.get(key)

  implicit val encoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[Status] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
}

abstract class WorkableStatus(
    val name: String,
    val success: ReportableStatus,
    val failure: ReportableStatus
) extends Status

object WorkableStatus {
  val all: Map[String, WorkableStatus] =
    Map(
      Registered.name   -> Registered,
      Unregistered.name -> Unregistered
    )

  def fromKey(key: String): Option[WorkableStatus] = all.get(key)

  implicit val encoder: Encoder[WorkableStatus] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[WorkableStatus] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
}

abstract class ReportableStatus(val name: String) extends Status

object ReportableStatus {
  val all: Map[String, ReportableStatus] =
    Map(
      Synchronized.name -> Synchronized,
      SyncFailed.name   -> SyncFailed,
      Deleted.name      -> Deleted,
      DeleteFailed.name -> DeleteFailed
    )

  def fromKey(key: String): Option[ReportableStatus] = all.get(key)

  implicit val encoder: Encoder[ReportableStatus] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[ReportableStatus] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
}

sealed trait TriggerableStatus extends Status {
  def nextWorkable: WorkableStatus
}

object TriggerableStatus {
  val all: Map[String, TriggerableStatus] =
    Map(
      Synchronized.name -> Synchronized,
      SyncFailed.name   -> SyncFailed,
      DeleteFailed.name -> DeleteFailed
    )

  def fromKey(key: String): Option[TriggerableStatus] = all.get(key)

  implicit val encoder: Encoder[TriggerableStatus] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[TriggerableStatus] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
}

abstract class FlaggedStatus(val name: String) extends Status

object FlaggedStatus {
  val all: Map[String, FlaggedStatus] =
    Map(Published.name -> Published)

  def fromKey(key: String): Option[FlaggedStatus] = all.get(key)

  implicit val encoder: Encoder[FlaggedStatus] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[FlaggedStatus] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
}
