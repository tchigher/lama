package co.ledger.lama.manager

import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import dev.profunktor.fs2rabbit.model.RoutingKey
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
    def register(accountId: UUID): SyncEvent =
      SyncEvent(
        accountId,
        UUID.randomUUID(),
        SyncEvent.Status.Registered
      )

    implicit val encoder: Encoder[SyncEvent] = deriveEncoder[SyncEvent]
    implicit val decoder: Decoder[SyncEvent] = deriveDecoder[SyncEvent]

    abstract class Status(val name: String)

    object Status {
      case object Registered              extends Status("registered")
      case object WorkerAcknowledged      extends Status("worker_acked")
      case object InterpreterAcknowledged extends Status("interpreter_acked")
      case object Synchronized            extends Status("synchronized")
      case object Failed                  extends Status("failed")
      case object Unregistered            extends Status("unregistered")

      val syncCandidates: NonEmptyList[Status] =
        NonEmptyList.of(Registered, Synchronized, Failed)

      val all: Map[String, Status] =
        Map(
          Registered.name              -> Registered,
          WorkerAcknowledged.name      -> WorkerAcknowledged,
          InterpreterAcknowledged.name -> InterpreterAcknowledged,
          Synchronized.name            -> Synchronized,
          Failed.name                  -> Failed,
          Unregistered.name            -> Unregistered
        )

      def fromKey(key: String): Option[Status] = all.get(key)

      implicit val encoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

      implicit val decoder: Decoder[Status] =
        Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))

      implicit val meta: Meta[Status] =
        pgEnumStringOpt("sync_status", Status.fromKey, _.name)
    }
  }

  abstract class CoinFamily(val name: String) {
    def workerRoutingKey: RoutingKey =
      this match {
        case CoinFamily.Bitcoin => RoutingKey("bitcoin_worker")
      }
  }

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
      coinFamily: CoinFamily,
      payload: Json
  )

  object SyncPayload {
    implicit val encoder: Encoder[SyncPayload] = deriveEncoder[SyncPayload]
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
