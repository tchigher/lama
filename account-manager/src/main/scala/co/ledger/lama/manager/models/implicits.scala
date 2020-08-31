package co.ledger.lama.manager.models

import java.util.UUID

import cats.implicits._
import co.ledger.lama.common.models._
import doobie.util.meta.Meta
import doobie.postgres.implicits._
import doobie.util.{Get, Put, Read}
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser._
import io.circe.syntax._
import org.postgresql.util.PGobject

object implicits {

  implicit val uuidEncoder: Encoder[UUID] = Encoder.encodeString.contramap(_.toString)
  implicit val uuidDecoder: Decoder[UUID] = Decoder.decodeString.map(UUID.fromString)

  implicit val jsonMeta: Meta[Json] =
    Meta.Advanced
      .other[PGobject]("json")
      .timap[Json](a => parse(a.getValue).leftMap[Json](e => throw e).merge)(a => {
        val o = new PGobject
        o.setType("json")
        o.setValue(a.noSpaces)
        o
      })

  implicit lazy val read: Read[SyncEvent] = Read[(UUID, UUID, Status, Json)].map {
    case (accountId, syncId, status, json) =>
      SyncEvent(accountId, syncId, status, json.as[SyncEvent.Payload].toTry.get)
  }

  implicit val meta: Meta[Status] =
    pgEnumStringOpt("sync_status", Status.fromKey, _.name)

  implicit val workableStatusMeta: Meta[WorkableStatus] =
    pgEnumStringOpt("sync_status", WorkableStatus.fromKey, _.name)

  implicit val reportableStatusMeta: Meta[ReportableStatus] =
    pgEnumStringOpt("sync_status", ReportableStatus.fromKey, _.name)

  implicit val flaggedStatusMeta: Meta[FlaggedStatus] =
    pgEnumStringOpt("sync_status", FlaggedStatus.fromKey, _.name)

  implicit val triggerableStatusMeta: Meta[TriggerableStatus] =
    pgEnumStringOpt("sync_status", TriggerableStatus.fromKey, _.name)

  implicit val coinMeta: Meta[Coin] =
    pgEnumStringOpt("coin", Coin.fromKey, _.name)

  implicit val coinFamilyMeta: Meta[CoinFamily] =
    pgEnumStringOpt("coin_family", CoinFamily.fromKey, _.name)

  implicit val syncPayloadGet: Get[SyncEvent.Payload] =
    jsonMeta.get.temap(_.as[SyncEvent.Payload].left.map(_.message))

  implicit val syncPayloadPut: Put[SyncEvent.Payload] =
    jsonMeta.put.contramap[SyncEvent.Payload](_.asJson)

}
