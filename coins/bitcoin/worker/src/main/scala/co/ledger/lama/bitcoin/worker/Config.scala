package co.ledger.lama.bitcoin.worker

import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import pureconfig.module.cats._

case class Config(
    workerExchangeName: ExchangeName,
    lamaExchangeName: ExchangeName,
    rabbit: Fs2RabbitConfig
) {
  val routingKey: RoutingKey = RoutingKey("bitcoin.btc")

  def queueName(exchangeName: ExchangeName): QueueName =
    QueueName(s"${exchangeName.value}.${routingKey.value}")
}

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
    ConfigReader.fromString(str => Right(ExchangeName(str)))
  implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
    deriveReader[Fs2RabbitNodeConfig]
  implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
}
