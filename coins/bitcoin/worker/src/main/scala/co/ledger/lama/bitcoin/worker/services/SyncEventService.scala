package co.ledger.lama.bitcoin.worker.services

import cats.effect.IO
import co.ledger.lama.common.models.{ReportableEvent, WorkableEvent}
import co.ledger.lama.common.utils.RabbitUtils
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import fs2.Stream

class SyncEventService(
    rabbitClient: RabbitClient[IO],
    workerQueueName: QueueName,
    lamaExchangeName: ExchangeName,
    lamaRoutingKey: RoutingKey
) {

  def consumeWorkableEvents: Stream[IO, WorkableEvent] =
    RabbitUtils.createAutoAckConsumer[WorkableEvent](rabbitClient, workerQueueName)

  private val publisher: Stream[IO, ReportableEvent => IO[Unit]] =
    RabbitUtils.createPublisher[ReportableEvent](rabbitClient, lamaExchangeName, lamaRoutingKey)

  def reportEvent(event: ReportableEvent): IO[Unit] =
    publisher.evalMap(p => p(event)).compile.drain

}
