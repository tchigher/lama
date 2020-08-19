package co.ledger.lama.manager

import co.ledger.lama.manager.models.{AccountIdentifier, Coin, CoinFamily}

object Exceptions {

  case class CoinConfigurationException(coinFamily: CoinFamily, coin: Coin)
      extends Exception(s"Could not found config for $coinFamily - $coin")

  case object RedisUnexpectedException extends Exception("Unexpected exception from Redis")

  case class MalformedProtobufException(message: scalapb.GeneratedMessage)
      extends Exception(s"Malformed protobuf: ${message.toProtoString}")

  case class AccountNotFoundException(accountIdentifier: AccountIdentifier)
      extends Exception(s"Account not found: $accountIdentifier")

}
