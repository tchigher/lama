package co.ledger.lama.manager

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import co.ledger.lama.manager.config.Config
import co.ledger.lama.manager.utils.RabbitUtils
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import pureconfig.ConfigSource

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      // our connect EC
      ce <- ExecutionContexts.fixedThreadPool[IO](conf.postgres.poolSize)

      // our transaction EC
      te <- ExecutionContexts.cachedThreadPool[IO]

      db <- HikariTransactor.newHikariTransactor[IO](
        conf.postgres.driver,            // driver classname
        conf.postgres.url,               // connect URL
        conf.postgres.user,              // username
        conf.postgres.password,          // password
        ce,                              // await connection here
        Blocker.liftExecutionContext(te) // execute JDBC operations here
      )

      // rabbitmq client
      rabbitClient <- RabbitUtils.createClient(conf.rabbit)

      // create the orchestrator
      orchestrator = new CoinSyncOrchestrator(
        conf.orchestrator,
        db,
        rabbitClient
      )

      // define rpc service definitions
      serviceDefinitions = List(
        new Service(db, conf.orchestrator.updaters).definition
      )

      // create the grpc server
      grpcServer <- GrpcServer.defaultServer(conf.grpcServer, serviceDefinitions)
    } yield (grpcServer, orchestrator)

    // start the grpc server and run the orchestrator stream
    resources
      .use {
        case (server, orchestrator) =>
          IO(server.start()).flatMap { _ =>
            orchestrator.run().compile.drain
          }
      }
      .as(ExitCode.Success)
  }

}
