package co.ledger.lama.bitcoin.worker

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services._
import pureconfig.ConfigSource

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      rabbitClient <- Clients.rabbit(conf.rabbit)
      httpClient   <- Clients.htt4s
    } yield (rabbitClient, httpClient)

    resources.use {
      case (rabbitClient, httpClient) =>
        val syncEventService = new SyncEventService(
          rabbitClient,
          conf.queueName(conf.workerEventsExchangeName),
          conf.lamaEventsExchangeName,
          conf.routingKey
        )

        val keychainService = new KeychainServiceMock

        val explorerService = new ExplorerService(httpClient, conf.explorer)

        val interpreterService = new InterpreterServiceMock

        val worker = new Worker(
          syncEventService,
          keychainService,
          explorerService,
          interpreterService,
          conf.maxConcurrent
        )

        worker.run.compile.lastOrError.as(ExitCode.Success)
    }
  }

}
