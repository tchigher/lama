package co.ledger.lama.common.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

abstract class Coin(val name: String)

object Coin {
  case object Btc extends Coin("btc")

  val all: Map[String, Coin] = Map(Btc.name -> Btc)

  def fromKey(key: String): Option[Coin] = all.get(key)

  implicit val encoder: Encoder[Coin] =
    Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[Coin] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as coin"))

  implicit val configReader: ConfigReader[Coin] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Coin", "unknown")))
}
