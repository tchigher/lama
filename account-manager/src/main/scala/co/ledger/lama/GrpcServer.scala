package co.ledger.lama

import cats.effect.{IO, Resource}
import co.ledger.lama.config.GrpcServerConfig
import io.grpc.{Server, ServerBuilder, ServerServiceDefinition}
import org.lyranthe.fs2_grpc.java_runtime.implicits._

object GrpcServer {

  def defaultServer(
      conf: GrpcServerConfig,
      services: List[ServerServiceDefinition]
  ): Resource[IO, Server] = {
    services
      .foldLeft(ServerBuilder.forPort(conf.port)) {
        case (builder, service) =>
          builder.addService(service)
      }
      .resource[IO]
  }

}
