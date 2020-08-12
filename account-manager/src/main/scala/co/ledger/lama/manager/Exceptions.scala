package co.ledger.lama.manager

import co.ledger.lama.manager.models.{Coin, CoinFamily}

object Exceptions {

  case class CoinConfigurationException(coinFamily: CoinFamily, coin: Coin)
      extends Exception(s"Could not found config for $coinFamily - $coin")

  case object RedisUnexpectedException extends Exception("Unexpected exception from Redis")
}
