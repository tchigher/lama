package co.ledger.lama.manager.models

import java.util.UUID

import cats.implicits._
import doobie.util.meta.Meta
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json}
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

}
