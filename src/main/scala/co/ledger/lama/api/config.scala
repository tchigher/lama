package co.ledger.lama.api

import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

object config {
  case class Config(postgres: PostgresConfig, server: ServerConfig)

  // implicit instances for a type go in to the companion object
  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
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

}
