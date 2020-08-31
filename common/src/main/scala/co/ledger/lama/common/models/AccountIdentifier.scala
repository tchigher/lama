package co.ledger.lama.common.models

import java.util.UUID

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AccountIdentifier(extendedKey: String, coinFamily: CoinFamily, coin: Coin) {
  def id: UUID = UUID.nameUUIDFromBytes((extendedKey + coinFamily.name + coin.name).getBytes)
}

object AccountIdentifier {
  implicit val encoder: Encoder[AccountIdentifier] = deriveEncoder[AccountIdentifier]
  implicit val decoder: Decoder[AccountIdentifier] = deriveDecoder[AccountIdentifier]
}
