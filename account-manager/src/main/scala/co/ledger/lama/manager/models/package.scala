package co.ledger.lama.manager

import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import doobie.postgres.implicits._
import doobie.util.Read
import doobie.util.meta.Meta
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

package object models {

  case class SyncEvent(
      accountId: UUID,
      syncId: UUID,
      status: SyncEvent.Status,
      payload: Json = Json.obj()
  )

  object SyncEvent {
    def from(accountId: UUID, status: SyncEvent.Status): SyncEvent =
      SyncEvent(
        accountId,
        UUID.randomUUID(),
        status
      )

    implicit val encoder: Encoder[SyncEvent] = deriveEncoder[SyncEvent]
    implicit val decoder: Decoder[SyncEvent] = deriveDecoder[SyncEvent]

    sealed trait Status {
      def name: String
      def isFinished: Boolean = isInstanceOf[FinalStatus]
    }

    abstract class InitialStatus(val name: String) extends Status
    abstract class FinalStatus(val name: String)   extends Status

    object Status {
      // account available for sync
      case object Registered extends InitialStatus("registered")

      // account unavailable for sync
      case object Unregistered extends InitialStatus("unregistered")

      // account sync succeed
      case object Synchronized extends FinalStatus("synchronized")

      // account deletion succeed
      case object Deleted extends FinalStatus("deleted")

      // account sync/delete failed
      case object Failed extends FinalStatus("failed")

      val candidateStatuses: NonEmptyList[Status] =
        NonEmptyList.of(Registered, Synchronized, Failed, Unregistered)

      val all: Map[String, Status] =
        Map(
          Registered.name   -> Registered,
          Unregistered.name -> Unregistered,
          Synchronized.name -> Synchronized,
          Deleted.name      -> Deleted,
          Failed.name       -> Failed
        )

      def fromKey(key: String): Option[Status] = all.get(key)

      implicit val encoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

      implicit val decoder: Decoder[Status] =
        Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))

      implicit val meta: Meta[Status] =
        pgEnumStringOpt("sync_status", Status.fromKey, _.name)
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
      pgEnumStringOpt("coin_family", CoinFamily.fromKey, _.name)

    implicit val encoder: Encoder[CoinFamily] = Encoder.encodeString.contramap(_.name)

    implicit val configReader: ConfigReader[CoinFamily] =
      ConfigReader.fromString(str =>
        CoinFamily.fromKey(str).toRight(CannotConvert(str, "CoinFamily", "unknown"))
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
      pgEnumStringOpt("coin", Coin.fromKey, _.name)

    implicit val encoder: Encoder[Coin] = Encoder.encodeString.contramap(_.name)

    implicit val configReader: ConfigReader[Coin] =
      ConfigReader.fromString(str =>
        Coin.fromKey(str).toRight(CannotConvert(str, "Coin", "unknown"))
      )
  }

  case class SyncPayload(
      accountId: UUID,
      syncId: UUID,
      extendedKey: String,
      status: SyncEvent.Status,
      payload: Json = Json.obj()
  ) extends WithRedisKey(accountId)

  object SyncPayload {
    implicit val encoder: Encoder[SyncPayload] = deriveEncoder[SyncPayload]
    implicit val decoder: Decoder[SyncPayload] = deriveDecoder[SyncPayload]
  }

  case class UpsertAccountInfoResult(accountId: UUID, syncFrequency: FiniteDuration)

  object UpsertAccountInfoResult {
    implicit val doobieRead: Read[UpsertAccountInfoResult] =
      Read[(UUID, Long)].map {
        case (accountId, syncFrequencyInSeconds) =>
          UpsertAccountInfoResult(
            accountId,
            FiniteDuration(syncFrequencyInSeconds, TimeUnit.SECONDS)
          )
      }
  }

}
