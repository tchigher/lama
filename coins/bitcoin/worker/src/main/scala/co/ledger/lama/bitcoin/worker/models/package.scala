package co.ledger.lama.bitcoin.worker

import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model.{AmqpMessage, AmqpProperties}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

package object models {

  sealed trait SyncEvent[S <: Status] {
    def accountId: UUID
    def syncId: UUID
    def status: S
    def payload: Payload // TODO: type it correctly
  }

  case class InitialSyncEvent(
      accountId: UUID,
      syncId: UUID,
      status: InitialStatus,
      payload: Payload
  ) extends SyncEvent[InitialStatus] {
    def reportSuccess(data: Json): ReportSyncEvent =
      ReportSyncEvent(accountId, syncId, status.success, payload.copy(data = data))

    def reportFailure(data: Json): ReportSyncEvent =
      ReportSyncEvent(accountId, syncId, status.failure, payload.copy(data = data))
  }

  object InitialSyncEvent {
    implicit val encoder: Encoder[InitialSyncEvent] = deriveEncoder[InitialSyncEvent]
    implicit val decoder: Decoder[InitialSyncEvent] = deriveDecoder[InitialSyncEvent]
  }

  case class ReportSyncEvent(
      accountId: UUID,
      syncId: UUID,
      status: ReportStatus,
      payload: Payload
  ) extends SyncEvent[ReportStatus]

  object ReportSyncEvent {
    implicit val encoder: Encoder[ReportSyncEvent] = deriveEncoder[ReportSyncEvent]
    implicit val decoder: Decoder[ReportSyncEvent] = deriveDecoder[ReportSyncEvent]

    implicit val rabbitEncoder: MessageEncoder[IO, ReportSyncEvent] =
      Kleisli[IO, ReportSyncEvent, AmqpMessage[Array[Byte]]] { s =>
        AmqpMessage(
          payload = s.asJson.noSpaces.getBytes(StandardCharsets.UTF_8),
          properties = AmqpProperties.empty
        ).pure[IO]
      }
  }

  sealed trait Status {
    def name: String
  }

  abstract class InitialStatus(
      val name: String,
      val success: ReportStatus,
      val failure: ReportStatus
  ) extends Status

  object InitialStatus {
    case object Registered
        extends InitialStatus("registered", ReportStatus.Synchronized, ReportStatus.SyncFailed)

    case object Unregistered
        extends InitialStatus("unregistered", ReportStatus.Deleted, ReportStatus.DeleteFailed)

    implicit val encoder: Encoder[InitialStatus] = Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[InitialStatus] =
      Decoder.decodeString.emap(
        fromKey(_).toRight("unable to decode status")
      )

    val all: Map[String, InitialStatus] =
      Map(
        Registered.name   -> Registered,
        Unregistered.name -> Unregistered
      )

    def fromKey(key: String): Option[InitialStatus] = all.get(key)
  }

  abstract class ReportStatus(val name: String) extends Status

  object ReportStatus {
    case object Synchronized extends ReportStatus("synchronized")
    case object SyncFailed   extends ReportStatus("sync_failed")
    case object Deleted      extends ReportStatus("deleted")
    case object DeleteFailed extends ReportStatus("delete_failed")

    implicit val encoder: Encoder[ReportStatus] = Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[ReportStatus] =
      Decoder.decodeString.emap(
        fromKey(_).toRight("unable to decode status")
      )

    val all: Map[String, ReportStatus] =
      Map(
        Synchronized.name -> Synchronized,
        SyncFailed.name   -> SyncFailed,
        Deleted.name      -> Deleted,
        DeleteFailed.name -> DeleteFailed
      )

    def fromKey(key: String): Option[ReportStatus] = all.get(key)
  }

  case class Payload(account: AccountIdentifier, data: Json = Json.obj())

  object Payload {
    implicit val encoder: Encoder[Payload] = deriveEncoder[Payload]
    implicit val decoder: Decoder[Payload] = deriveDecoder[Payload]
  }

  case class AccountIdentifier(extendedKey: String)

  object AccountIdentifier {
    implicit val encoder: Encoder[AccountIdentifier] = deriveEncoder[AccountIdentifier]
    implicit val decoder: Decoder[AccountIdentifier] = deriveDecoder[AccountIdentifier]
  }

}
