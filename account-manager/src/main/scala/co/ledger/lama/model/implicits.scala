package co.ledger.lama.model

import java.util.UUID

import doobie.util.{Get, Put}
import io.circe.{Decoder, Encoder}

object implicits {

  implicit val uuidEncoder: Encoder[UUID] = Encoder.encodeString.contramap(_.toString)
  implicit val uuidDecoder: Decoder[UUID] = Decoder.decodeString.map(UUID.fromString)
  implicit val uuidGet: Get[UUID]         = Get[String].tmap(UUID.fromString)
  implicit val uuidPut: Put[UUID]         = Put[String].tcontramap(_.toString)

}
