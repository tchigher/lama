package co.ledger.lama

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import co.ledger.lama.config.Config
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import pureconfig.ConfigSource

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val orchestratorResource = for {
      ce <- ExecutionContexts.fixedThreadPool[IO](conf.postgres.poolSize) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[IO] // our transaction EC
      db <- HikariTransactor.newHikariTransactor[IO](
        conf.postgres.driver,            // driver classname
        conf.postgres.url,               // connect URL
        conf.postgres.user,              // username
        conf.postgres.password,          // password
        ce,                              // await connection here
        Blocker.liftExecutionContext(te) // execute JDBC operations here
      )
      rabbitClient <- RabbitUtils.createClient(conf.rabbit)
    } yield new CoinSyncOrchestrator(conf.orchestrator, db, rabbitClient)

    GrpcServer
      .defaultServer(conf.grpcServer, List.empty) // define rpc service definitions
      .evalMap(server => IO(server.start()))      // start the server
      .flatMap(_ => orchestratorResource)
      .use(_.run().compile.drain) // run the orchestrator stream
      .as(ExitCode.Success)
  }

}
