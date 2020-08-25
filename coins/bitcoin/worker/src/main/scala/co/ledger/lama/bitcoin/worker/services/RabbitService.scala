package co.ledger.lama.bitcoin.worker.services

import java.util.concurrent.Executors

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO, Resource}
import co.ledger.lama.bitcoin.worker.models.{InitialSyncEvent, ReportSyncEvent}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{AMQPChannel, ExchangeName, QueueName, RoutingKey}
import fs2.Stream
import io.circe.parser.parse

class RabbitService(
    conf: Fs2RabbitConfig,
    workerQueueName: QueueName,
    lamaExchangeName: ExchangeName,
    lamaRoutingKey: RoutingKey
)(implicit
    ce: ConcurrentEffect[IO],
    cs: ContextShift[IO]
) {

  private val resource: Resource[IO, RabbitClient[IO]] =
    Resource
      .make(IO(Executors.newCachedThreadPool()))(es => IO(es.shutdown()))
      .map(Blocker.liftExecutorService)
      .evalMap(RabbitClient[IO](conf, _))

  def consumeSyncEvents: Stream[IO, InitialSyncEvent] =
    Stream
      .resource {
        for {
          client  <- resource
          channel <- client.createConnectionChannel
        } yield (client, channel)
      }
      .evalMap {
        case (client, channel) =>
          implicit val c: AMQPChannel = channel
          client.createAutoAckConsumer[String](workerQueueName)
      }
      .flatten
      .evalMap { message =>
        val parsed = parse(message.payload).flatMap(_.as[InitialSyncEvent])
        IO.fromEither(parsed)
      }

  private val publisher: IO[ReportSyncEvent => IO[Unit]] =
    resource.use { client =>
      client.createConnectionChannel.use { implicit channel =>
        client.createPublisher[ReportSyncEvent](
          lamaExchangeName,
          lamaRoutingKey
        )
      }
    }

  def reportEvent(event: ReportSyncEvent): IO[Unit] =
    publisher.flatMap(p => p(event))

}
