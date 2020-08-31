package co.ledger.lama.manager

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import co.ledger.lama.common.utils.RabbitUtils
import co.ledger.lama.manager.config.Config
import com.redis.RedisClient
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

      // redis client
      redisClient <-
        Resource.fromAutoCloseable(IO(new RedisClient(conf.redis.host, conf.redis.port)))

      // create the orchestrator
      orchestrator = new CoinOrchestrator(
        conf.orchestrator,
        db,
        rabbitClient,
        redisClient
      )

      // define rpc service definitions
      serviceDefinitions = List(
        new Service(db, conf.orchestrator.coins).definition
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
