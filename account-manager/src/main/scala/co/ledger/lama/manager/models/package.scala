package co.ledger.lama.manager

import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import co.ledger.lama.manager.models.implicits._
import co.ledger.lama.manager.utils.UuidUtils
import doobie.postgres.implicits._
import doobie.util.{Get, Put, Read}
import doobie.util.meta.Meta
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

package object models {

  case class SyncEvent(
      accountId: UUID,
      syncId: UUID,
      status: SyncEvent.Status,
      payload: SyncEvent.Payload
  ) extends WithRedisKey(accountId)

  object SyncEvent {
    def registered(account: AccountIdentifier, cursor: Json): SyncEvent =
      SyncEvent(account.id, UUID.randomUUID(), Status.Registered, Payload(account, cursor))

    def unregistered(account: AccountIdentifier): SyncEvent =
      SyncEvent(account.id, UUID.randomUUID(), Status.Unregistered, Payload(account))

    def nextFromTriggerable(e: SyncEvent, trigger: TriggerableStatus): SyncEvent =
      SyncEvent(e.accountId, UUID.randomUUID(), trigger.nextStatus, e.payload)

    def fromProtobuf(accountId: UUID, pb: protobuf.SyncEvent): Option[SyncEvent] =
      for {
        syncId  <- UuidUtils.bytesToUuid(pb.syncId)
        status  <- Status.fromKey(pb.status)
        payload <- parse(new String(pb.payload.toByteArray)).flatMap(_.as[Payload]).toOption
      } yield {
        SyncEvent(
          accountId,
          syncId,
          status,
          payload
        )
      }

    implicit val encoder: Encoder[SyncEvent] = deriveEncoder[SyncEvent]
    implicit val decoder: Decoder[SyncEvent] = deriveDecoder[SyncEvent]

    sealed trait Status {
      def name: String
    }

    sealed trait TriggerableStatus extends Status {
      val nextStatus: PublishableStatus
    }

    abstract class PublishableStatus(val name: String) extends Status
    abstract class ReportableStatus(val name: String)  extends Status

    object Status {
      // Registered event sent to worker for sync.
      case object Registered extends PublishableStatus("registered")

      // Unregistered event sent to worker for delete data.
      case object Unregistered extends PublishableStatus("unregistered")

      // Published event.
      case object Published extends Status {
        val name: String = "published"
      }

      // Successful sync event reported by worker.
      case object Synchronized extends ReportableStatus("synchronized") with TriggerableStatus {
        val nextStatus: PublishableStatus = Registered
      }

      // Failed sync event reported by worker.
      case object SyncFailed extends ReportableStatus("sync_failed") with TriggerableStatus {
        val nextStatus: PublishableStatus = Registered
      }

      // Successful delete event reported by worker.
      case object Deleted extends ReportableStatus("deleted")

      // Failed delete event reported by worker.
      case object DeleteFailed extends ReportableStatus("delete_failed") with TriggerableStatus {
        val nextStatus: PublishableStatus = Unregistered
      }

      val all: Map[String, Status] =
        Map(
          Registered.name   -> Registered,
          Unregistered.name -> Unregistered,
          Published.name    -> Published,
          Synchronized.name -> Synchronized,
          SyncFailed.name   -> SyncFailed,
          Deleted.name      -> Deleted,
          DeleteFailed.name -> DeleteFailed
        )

      val sendableStatuses: NonEmptyList[Status] =
        NonEmptyList.fromListUnsafe(all.values.filter(_.isInstanceOf[PublishableStatus]).toList)

      val triggerableStatuses: NonEmptyList[Status] =
        NonEmptyList.fromListUnsafe(all.values.filter(_.isInstanceOf[TriggerableStatus]).toList)

      def fromKey(key: String): Option[Status] = all.get(key)

      implicit val encoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

      implicit val decoder: Decoder[Status] =
        Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))

      implicit val meta: Meta[Status] =
        pgEnumStringOpt("sync_status", Status.fromKey, _.name)
    }

    case class Payload(account: AccountIdentifier, data: Json = Json.obj())

    object Payload {
      implicit val encoder: Encoder[Payload] = deriveEncoder[Payload]
      implicit val decoder: Decoder[Payload] = deriveDecoder[Payload]

      implicit val doobieGet: Get[Payload] = jsonMeta.get.temap(_.as[Payload].left.map(_.message))
      implicit val doobiePut: Put[Payload] = jsonMeta.put.contramap[Payload](_.asJson)
    }
  }

  abstract class CoinFamily(val name: String)

  object CoinFamily {
    case object Bitcoin extends CoinFamily("bitcoin")

    val all: Map[String, CoinFamily] = Map(Bitcoin.name -> Bitcoin)

    def fromKey(key: String): Option[CoinFamily] = all.get(key)

    def fromProtobuf(pb: protobuf.CoinFamily): CoinFamily =
      fromKey(pb.name).get

    implicit val meta: Meta[CoinFamily] =
      pgEnumStringOpt("coin_family", fromKey, _.name)

    implicit val encoder: Encoder[CoinFamily] =
      Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[CoinFamily] =
      Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as coin family"))

    implicit val configReader: ConfigReader[CoinFamily] =
      ConfigReader.fromString(str =>
        fromKey(str).toRight(CannotConvert(str, "CoinFamily", "unknown"))
      )
  }

  abstract class Coin(val name: String)

  object Coin {
    case object Btc extends Coin("btc")

    val all: Map[String, Coin] = Map(Btc.name -> Btc)

    def fromKey(key: String): Option[Coin] = all.get(key)

    def fromProtobuf(pb: protobuf.Coin): Coin =
      fromKey(pb.name).get

    implicit val meta: Meta[Coin] =
      pgEnumStringOpt("coin", fromKey, _.name)

    implicit val encoder: Encoder[Coin] =
      Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[Coin] =
      Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as coin"))

    implicit val configReader: ConfigReader[Coin] =
      ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Coin", "unknown")))
  }

  case class AccountInfo(id: UUID, syncFrequency: FiniteDuration)

  object AccountInfo {
    implicit val doobieRead: Read[AccountInfo] =
      Read[(UUID, Long)].map {
        case (accountId, syncFrequencyInSeconds) =>
          AccountInfo(
            accountId,
            FiniteDuration(syncFrequencyInSeconds, TimeUnit.SECONDS)
          )
      }
  }

  case class AccountIdentifier(extendedKey: String, coinFamily: CoinFamily, coin: Coin) {
    val id: UUID = UuidUtils.fromAccountIdentifier(extendedKey, coinFamily, coin)
  }

  object AccountIdentifier {
    implicit val encoder: Encoder[AccountIdentifier] = deriveEncoder[AccountIdentifier]
    implicit val decoder: Decoder[AccountIdentifier] = deriveDecoder[AccountIdentifier]

    def fromProtobuf(pb: protobuf.AccountInfoRequest): AccountIdentifier =
      AccountIdentifier(
        pb.extendedKey,
        CoinFamily.fromProtobuf(pb.coinFamily),
        Coin.fromProtobuf(pb.coin)
      )

    def fromProtobuf(pb: protobuf.RegisterAccountRequest): AccountIdentifier =
      AccountIdentifier(
        pb.extendedKey,
        CoinFamily.fromProtobuf(pb.coinFamily),
        Coin.fromProtobuf(pb.coin)
      )
  }

}
