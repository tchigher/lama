package co.ledger.lama.bitcoin.worker

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.worker.services._
import pureconfig.ConfigSource

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val rabbitService = new RabbitService(
      conf.rabbit,
      conf.queueName(conf.workerExchangeName),
      conf.lamaExchangeName,
      conf.routingKey
    )

    val keychainService    = new KeychainService
    val explorerService    = new ExplorerService
    val interpreterService = new InterpreterService

    val worker = new Worker(rabbitService, keychainService, explorerService, interpreterService)

    worker.run.compile.lastOrError.as(ExitCode.Success)
  }

}
