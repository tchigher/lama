package co.ledger.lama

import co.ledger.lama.model.CoinFamily
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import pureconfig.module.cats._

import scala.concurrent.duration.FiniteDuration

object config {

  case class Config(
      postgres: PostgresConfig,
      server: ServerConfig,
      orchestator: OrchestratorConfig,
      rabbit: Fs2RabbitConfig
  )

  // implicit instances for a type go in to the companion object
  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
    implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
      deriveReader[Fs2RabbitNodeConfig]
    implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
  }

  case class ServerConfig(
      host: String,
      port: Int
  )

  object ServerConfig {
    implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]
  }

  case class PostgresConfig(
      url: String,
      user: String,
      password: String
  ) {
    def driver: String = "org.postgresql.Driver"
  }

  object PostgresConfig {
    implicit val postgresConfigReader: ConfigReader[PostgresConfig] = deriveReader[PostgresConfig]
  }

  case class OrchestratorConfig(
      syncEventExchangeName: ExchangeName,
      syncEventQueueName: QueueName,
      workerExchangeName: ExchangeName,
      updaters: List[UpdaterConfig]
  )

  object OrchestratorConfig {
    implicit val orchestratorConfigReader: ConfigReader[OrchestratorConfig] =
      deriveReader[OrchestratorConfig]

    implicit val exchangeNameReader: ConfigReader[ExchangeName] =
      ConfigReader.fromString(str => Right(ExchangeName(str)))

    implicit val queueNameReader: ConfigReader[QueueName] =
      ConfigReader.fromString(str => Right(QueueName(str)))

    implicit val updatersReader: ConfigReader[UpdaterConfig] =
      deriveReader[UpdaterConfig]
  }

  case class UpdaterConfig(
      coinFamily: CoinFamily,
      syncFrequency: FiniteDuration
  )
}
