package co.ledger.lama.api

import cats.data.ReaderT
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Timer}
import co.ledger.lama.api.config.{Config, ServerConfig}
import co.ledger.lama.api.http.{HttpErrorHandler, UserHttpRoutes}
import co.ledger.lama.api.repository.PostgresUserRepository
import co.ledger.lama.api.service.UserService
import config.{Config, ServerConfig}
import co.ledger.template.http.UserHttpRoutes
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.HttpRoutes
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

object HttpServer {

  def server[F[_]: ConcurrentEffect: Timer](
      config: ServerConfig,
      routes: HttpRoutes[F]
  ): Resource[F, Server[F]] = {
    import org.http4s.implicits._
    BlazeServerBuilder[F]
      .bindHttp(config.port, config.host)
      .withHttpApp(routes.orNotFound)
      .resource
  }

  def server[F[_]: ConcurrentEffect: Timer](
      routes: HttpRoutes[F]
  ): ConfiguredResource[F, Server[F]] = {
    ReaderT(config => server(config.server, routes))
  }

  // Custom DI
  def defaultServer[F[_]: ConcurrentEffect: Timer: ContextShift]
      : ConfiguredResource[F, Server[F]] = {
    val route: ConfiguredResource[F, HttpRoutes[F]] = ReaderT {
      config: Config =>
        implicit val httpErrorHandler: HttpErrorHandler[F] =
          new HttpErrorHandler[F]
        for {
          ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
          te <- ExecutionContexts.cachedThreadPool[F] // our transaction EC
          xa <- HikariTransactor.newHikariTransactor[F](
            config.postgres.driver, // driver classname
            config.postgres.url, // connect URL
            config.postgres.user, // username
            config.postgres.password, // password
            ce, // await connection here
            Blocker.liftExecutionContext(te) // execute JDBC operations here
          )
          userRepository = new PostgresUserRepository[F](xa)
          userService = new UserService[F](userRepository)
          userRoutes = new UserHttpRoutes[F](userService).routes
        } yield userRoutes
    }
    route.flatMap(server(_))
  }

}
