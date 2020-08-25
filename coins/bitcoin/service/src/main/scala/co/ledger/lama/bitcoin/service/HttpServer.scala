package co.ledger.lama.bitcoin.service

import cats.implicits._
import cats.data.ReaderT
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import co.ledger.lama.bitcoin.service.config.{Config, ServerConfig}
import co.ledger.lama.bitcoin.service.http.{AccountRoutes, CurrencyRoutes}
import co.ledger.lama.bitcoin.service.service.CurrencyService
import co.ledger.lama.bitcoin.protobuf.BitcoinService.BitcoinServiceFs2Grpc
import io.grpc.ManagedChannelBuilder
import org.http4s.HttpRoutes
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderSyntax

object HttpServer extends ManagedChannelBuilderSyntax {

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
    val route: ConfiguredResource[F, HttpRoutes[F]] = ReaderT { config: Config =>
      for {
        managedChannel <-
          ManagedChannelBuilder
            .forAddress(config.api.coinService.address, config.api.coinService.port)
            .resource[F]
        bitcoinServiceGrpcClient = BitcoinServiceFs2Grpc.stub(managedChannel)
        currencyService          = new CurrencyService[F](bitcoinServiceGrpcClient)
        currencyRoute            = new CurrencyRoutes[F](currencyService).routes
        accountRoute             = new AccountRoutes[F].routes
      } yield currencyRoute <+> accountRoute
    }
    route.flatMap(server(_))
  }

}
