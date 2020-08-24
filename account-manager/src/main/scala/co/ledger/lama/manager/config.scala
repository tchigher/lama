package co.ledger.lama.manager

import co.ledger.lama.manager.models.{Coin, CoinFamily}
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import pureconfig.module.cats._

import scala.concurrent.duration.FiniteDuration

object config {

  case class Config(
      postgres: PostgresConfig,
      grpcServer: GrpcServerConfig,
      orchestrator: OrchestratorConfig,
      rabbit: Fs2RabbitConfig,
      redis: RedisConfig
  )

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
    implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
      deriveReader[Fs2RabbitNodeConfig]
    implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
  }

  case class GrpcServerConfig(port: Int)

  object GrpcServerConfig {
    implicit val configReader: ConfigReader[GrpcServerConfig] = deriveReader[GrpcServerConfig]
  }

  case class PostgresConfig(
      url: String,
      user: String,
      password: String
  ) {
    val driver: String = "org.postgresql.Driver"
    val poolSize: Int  = Runtime.getRuntime.availableProcessors() * 2
  }

  object PostgresConfig {
    implicit val configReader: ConfigReader[PostgresConfig] = deriveReader[PostgresConfig]
  }

  case class RedisConfig(host: String, port: Int)

  object RedisConfig {
    implicit val configReader: ConfigReader[RedisConfig] = deriveReader[RedisConfig]
  }

  case class OrchestratorConfig(
      workerExchangeName: ExchangeName,
      lamaEventsExchangeName: ExchangeName,
      coins: List[CoinConfig]
  )

  object OrchestratorConfig {
    implicit val configReader: ConfigReader[OrchestratorConfig] =
      deriveReader[OrchestratorConfig]

    implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
      ConfigReader.fromString(str => Right(ExchangeName(str)))

    implicit val coinConfigReader: ConfigReader[CoinConfig] =
      deriveReader[CoinConfig]
  }

  case class CoinConfig(
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: FiniteDuration
  ) {
    val routingKey: RoutingKey = RoutingKey(s"${coinFamily.name}.${coin.name}")

    def queueName(exchangeName: ExchangeName): QueueName =
      QueueName(s"${exchangeName.value}.${routingKey.value}")
  }
}
