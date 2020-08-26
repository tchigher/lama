package co.ledger.lama.bitcoin.worker

import java.util.concurrent.Executors

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      rabbitClient <-
        Resource
          .make(IO(Executors.newCachedThreadPool()))(es => IO(es.shutdown()))
          .map(Blocker.liftExecutorService)
          .evalMap(RabbitClient[IO](conf.rabbit, _))

      httpClient <- BlazeClientBuilder[IO](
        ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)
      ).resource

    } yield (rabbitClient, httpClient)

    resources.use {
      case (rabbitClient, httpClient) =>
        val syncEventService = new SyncEventService(
          rabbitClient,
          conf.queueName(conf.workerEventsExchangeName),
          conf.lamaEventsExchangeName,
          conf.routingKey
        )

        val keychainService = new KeychainService

        val explorerService = new ExplorerService(httpClient, conf.explorer)

        val interpreterService = new InterpreterService

        val worker = new Worker(
          syncEventService,
          keychainService,
          explorerService,
          interpreterService
        )

        worker.run.compile.lastOrError.as(ExitCode.Success)
    }
  }

}
