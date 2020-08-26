package co.ledger.lama.bitcoin.worker.services

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.models.{InitialSyncEvent, ReportSyncEvent}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import fs2.Stream
import io.circe.parser.parse

class SyncEventService(
    rabbitClient: RabbitClient[IO],
    workerQueueName: QueueName,
    lamaExchangeName: ExchangeName,
    lamaRoutingKey: RoutingKey
) {

  def consumeSyncEvents: Stream[IO, InitialSyncEvent] =
    Stream
      .resource(rabbitClient.createConnectionChannel)
      .evalMap { implicit channel =>
        rabbitClient.createAutoAckConsumer[String](workerQueueName)
      }
      .flatten
      .evalMap { message =>
        val parsed = parse(message.payload).flatMap(_.as[InitialSyncEvent])
        IO.fromEither(parsed)
      }

  private val publisher: IO[ReportSyncEvent => IO[Unit]] =
    rabbitClient.createConnectionChannel.use { implicit channel =>
      rabbitClient.createPublisher[ReportSyncEvent](
        lamaExchangeName,
        lamaRoutingKey
      )
    }

  def reportEvent(event: ReportSyncEvent): IO[Unit] =
    publisher.flatMap(p => p(event))

}
