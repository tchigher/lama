package co.ledger.lama.api

import cats.effect.{ExitCode, IO, IOApp}
import config.Config
import pureconfig.ConfigSource
import pureconfig.generic.auto._

// The only place where the Effect is defined. You could change it for `TaskApp` and `monix.eval.Task` for example.
object App extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val config = ConfigSource.default.loadOrThrow[Config]
    HttpServer
      .defaultServer[IO]
      .run(config)
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

}
