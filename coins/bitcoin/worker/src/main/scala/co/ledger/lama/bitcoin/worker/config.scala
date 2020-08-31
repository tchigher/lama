package co.ledger.lama.bitcoin.worker

import cats.implicits._
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import org.http4s.Uri
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._
import pureconfig.module.cats._

import scala.concurrent.duration.FiniteDuration

object config {

  case class Config(
      workerEventsExchangeName: ExchangeName,
      lamaEventsExchangeName: ExchangeName,
      rabbit: Fs2RabbitConfig,
      explorer: ExplorerConfig
  ) {
    val routingKey: RoutingKey = RoutingKey("bitcoin.btc")

    def queueName(exchangeName: ExchangeName): QueueName =
      QueueName(s"${exchangeName.value}.${routingKey.value}")

    val maxConcurrent: Int = Runtime.getRuntime.availableProcessors() * 2
  }

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
    implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
      ConfigReader.fromString(str => Right(ExchangeName(str)))
    implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
      deriveReader[Fs2RabbitNodeConfig]
    implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
  }

  case class ExplorerConfig(uri: Uri, txsBatchSize: Int, timeout: FiniteDuration)

  object ExplorerConfig {
    implicit val configReader: ConfigReader[ExplorerConfig] = deriveReader[ExplorerConfig]
    implicit val uriReader: ConfigReader[Uri] = ConfigReader[String].emap { s =>
      Uri.fromString(s).leftMap(t => CannotConvert(s, "Uri", t.getMessage))
    }
  }

}
