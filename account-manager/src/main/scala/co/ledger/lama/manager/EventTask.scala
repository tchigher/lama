package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{IO, Timer}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.{SyncEvent, SyncPayload}
import co.ledger.lama.manager.utils.RabbitUtils
import com.redis.RedisClient
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.{Pipe, Stream}

import scala.concurrent.duration.FiniteDuration

trait EventTask {

  // source of candidate events
  def candidateEventsSource: Stream[IO, SyncPayload]

  // stream transformation from candidate events
  def candidateEventsPipe: Pipe[IO, SyncPayload, Unit]

  // stream which awake every tick then candidates sync events
  def candidates(tick: FiniteDuration, n: Option[Long] = None)(implicit
      t: Timer[IO]
  ): Stream[IO, Unit] = {
    val stream = Stream.awakeEvery[IO](tick)

    (n match {
      case Some(value) => stream.take(value) // useful to stop an infinite stream
      case None        => stream
    }) >> candidateEventsSource.through(candidateEventsPipe)
  }

  // source of events to report
  def reportEventsSource: Stream[IO, SyncEvent]

  // report an event
  def reportEvent(e: SyncEvent): IO[Unit]

  // stream which reports sync events
  def reports: Stream[IO, Unit] =
    reportEventsSource.evalMap(reportEvent).drain
}

class CoinTask(
    workerExchangeName: ExchangeName,
    finishedEventsExchangeName: ExchangeName,
    conf: CoinConfig,
    db: Transactor[IO],
    rabbit: RabbitClient[IO],
    redis: RedisClient
) extends EventTask {

  def candidateEventsSource: Stream[IO, SyncPayload] =
    Queries
      .fetchCandidateEvents(conf.coinFamily, conf.coin)
      .transact(db)

  private val namespace = s"${conf.coinFamily.name}.${conf.coin}"

  // publisher publishing to the worker exchange with routingKey = "coinFamily.coin"
  private val publisher =
    new RabbitPublisher[UUID, SyncPayload](
      redis,
      rabbit,
      workerExchangeName,
      RoutingKey(namespace)
    )

  def candidateEventsPipe: Pipe[IO, SyncPayload, Unit] = publisher.enqueue

  // consume event messages from rabbitmq
  def reportEventsSource: Stream[IO, SyncEvent] =
    RabbitUtils
      .createAutoAckConsumer[SyncEvent](
        rabbit,
        finishedEventsExchangeName,
        RoutingKey(namespace),
        QueueName(namespace)
      )

  // always insert the reported event in DB,
  // then, if it's a finished event, publish next pending event
  def reportEvent(e: SyncEvent): IO[Unit] =
    for {
      _ <- Queries.insertSyncEvent(e).transact(db).void
      res <-
        if (e.status.isFinished) publisher.dequeue(e.accountId)
        else IO.unit
    } yield res

}
