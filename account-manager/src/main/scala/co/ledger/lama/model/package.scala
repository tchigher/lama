package co.ledger.lama

import java.util.UUID

import dev.profunktor.fs2rabbit.model.RoutingKey
import doobie.util.{Get, Put}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import cats.implicits._
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

package object model {

  case class SyncEvent(
      accountId: UUID,
      syncId: UUID,
      status: SyncEvent.Status,
      blockHeight: Long,
      errorMessage: Option[String] = None
  )

  object SyncEvent {
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

      def all: Map[String, Status] =
        Map(
          Registered.name              -> Registered,
          WorkerAcknowledged.name      -> WorkerAcknowledged,
          InterpreterAcknowledged.name -> InterpreterAcknowledged,
          Synchronized.name            -> Synchronized,
          Failed.name                  -> Failed,
          Unregistered.name            -> Unregistered
        )

      def fromString(str: String): Option[Status] = all.get(str)

      implicit val statusEncoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

      implicit val statusGet: Get[Status] =
        Get[String].temap(fromString(_).toRight("unable to get status"))

      implicit val statusPut: Put[Status] = Put[String].tcontramap(_.name)

      implicit val statusDecoder: Decoder[Status] =
        Decoder.decodeString.emap(fromString(_).toRight("unable to decode status"))
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

    def all: Map[String, CoinFamily] = Map(Bitcoin.name -> Bitcoin)

    def fromKey(key: String): Option[CoinFamily] = all.get(key)

    implicit val coinFamilyGet: Get[CoinFamily] =
      Get[String].temap(fromKey(_).toRight("unable to get coin family"))

    implicit val coinFamilyPut: Put[CoinFamily] = Put[String].tcontramap(_.name)

    implicit val encoder: Encoder[CoinFamily] = Encoder.encodeString.contramap(_.name)

    implicit val coinFamilyReader: ConfigReader[CoinFamily] =
      ConfigReader.fromString(str =>
        CoinFamily.fromKey(str).toRight(CannotConvert(str, "CoinFamily", "unknown"))
      )
  }

  case class SyncPayload(
      accountId: UUID,
      syncId: UUID,
      xpub: String,
      coinFamily: CoinFamily,
      blockHeight: Long
  )

  object SyncPayload {
    implicit val encoder: Encoder[SyncPayload] = deriveEncoder[SyncPayload]
  }

}
