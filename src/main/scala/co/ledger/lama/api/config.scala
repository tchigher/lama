package co.ledger.lama.api

object config {
  case class Config(server: ServerConfig, api: ApiConfig)

  case class ServerConfig(
      host: String,
      port: Int
  )

  case class ApiConfig(coinService: GRPCAddress)

  case class GRPCAddress(address: String, port: Int)
}
