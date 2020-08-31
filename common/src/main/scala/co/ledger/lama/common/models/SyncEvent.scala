package co.ledger.lama.common.models

import java.util.UUID

import co.ledger.lama.common.models.Status.Published
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

sealed trait SyncEvent extends WithKey[UUID] {
  def accountId: UUID
  def syncId: UUID
  def status: Status
  def payload: SyncEvent.Payload
  def key: UUID = accountId
}

trait WithKey[K] { def key: K }

object SyncEvent {

  def apply(
      accountId: UUID,
      syncId: UUID,
      status: Status,
      payload: SyncEvent.Payload
  ): SyncEvent =
    status match {
      case ws: WorkableStatus    => WorkableEvent(accountId, syncId, ws, payload)
      case rs: ReportableStatus  => ReportableEvent(accountId, syncId, rs, payload)
      case ts: TriggerableStatus => TriggerableEvent(accountId, syncId, ts, payload)
      case ns: FlaggedStatus     => FlaggedEvent(accountId, syncId, ns, payload)
    }

  case class Payload(
      account: AccountIdentifier,
      data: Json = Json.obj() // TODO: type it per coin and type of event?
  )

  object Payload {
    implicit val encoder: Encoder[Payload] = deriveEncoder[Payload]
    implicit val decoder: Decoder[Payload] = deriveDecoder[Payload]
  }

}

case class WorkableEvent(
    accountId: UUID,
    syncId: UUID,
    status: WorkableStatus,
    payload: SyncEvent.Payload
) extends SyncEvent {
  def asPublished: FlaggedEvent =
    FlaggedEvent(accountId, syncId, Published, payload)

  def reportSuccess(data: Json): ReportableEvent =
    ReportableEvent(accountId, syncId, status.success, payload.copy(data = data))

  def reportFailure(data: Json): ReportableEvent = {
    val updatedPayloadData = payload.data.deepMerge(data)
    ReportableEvent(accountId, syncId, status.failure, payload.copy(data = updatedPayloadData))
  }
}

object WorkableEvent {
  implicit val encoder: Encoder[WorkableEvent] = deriveEncoder[WorkableEvent]
  implicit val decoder: Decoder[WorkableEvent] = deriveDecoder[WorkableEvent]
}

case class ReportableEvent(
    accountId: UUID,
    syncId: UUID,
    status: ReportableStatus,
    payload: SyncEvent.Payload
) extends SyncEvent

object ReportableEvent {
  implicit val encoder: Encoder[ReportableEvent] = deriveEncoder[ReportableEvent]
  implicit val decoder: Decoder[ReportableEvent] = deriveDecoder[ReportableEvent]
}

case class TriggerableEvent(
    accountId: UUID,
    syncId: UUID,
    status: TriggerableStatus,
    payload: SyncEvent.Payload
) extends SyncEvent {
  def nextWorkable: WorkableEvent =
    WorkableEvent(accountId, UUID.randomUUID(), status.nextWorkable, payload)
}

object TriggerableEvent {
  implicit val encoder: Encoder[TriggerableEvent] = deriveEncoder[TriggerableEvent]
  implicit val decoder: Decoder[TriggerableEvent] = deriveDecoder[TriggerableEvent]
}

case class FlaggedEvent(
    accountId: UUID,
    syncId: UUID,
    status: FlaggedStatus,
    payload: SyncEvent.Payload
) extends SyncEvent

object FlaggedEvent {
  implicit val encoder: Encoder[FlaggedEvent] = deriveEncoder[FlaggedEvent]
  implicit val decoder: Decoder[FlaggedEvent] = deriveDecoder[FlaggedEvent]
}
