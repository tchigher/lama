package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.common.models._
import co.ledger.lama.common.utils.RabbitUtils
import co.ledger.lama.manager.config.CoinConfig
import com.redis.RedisClient
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeName
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.{Pipe, Stream}

import scala.concurrent.duration.FiniteDuration

trait SyncEventTask {

  // Source of publishable events.
  def publishableEvents: Stream[IO, WorkableEvent]

  // Publish events pipe transformation:
  // Stream[IO, SyncEvent] => Stream[IO, Unit].
  def publishEventsPipe: Pipe[IO, WorkableEvent, Unit]

  // Awake every tick, source publishable events then publish.
  def publishEvents(tick: FiniteDuration, stopAtNbTick: Option[Long] = None)(implicit
      t: Timer[IO]
  ): Stream[IO, Unit] =
    tickerStream(tick, stopAtNbTick) >> publishableEvents.through(publishEventsPipe)

  // Source of reportable events.
  def reportableEvents: Stream[IO, ReportableEvent]

  // Report events pipe transformation:
  // Stream[IO, SyncEvent] => Stream[IO, Unit].
  def reportEventsPipe: Pipe[IO, ReportableEvent, Unit]

  // Source reportable events then report.
  def reportEvents: Stream[IO, Unit] =
    reportableEvents.through(reportEventsPipe)

  // Source of triggerable events.
  def triggerableEvents: Stream[IO, TriggerableEvent]

  // Trigger events pipe transformation:
  // Stream[IO, SyncEvent] => Stream[IO, Unit].
  def triggerEventsPipe: Pipe[IO, TriggerableEvent, Unit]

  // Awake every tick, source triggerable events then trigger.
  def trigger(tick: FiniteDuration)(implicit
      t: Timer[IO]
  ): Stream[IO, Unit] =
    tickerStream(tick) >> triggerableEvents.through(triggerEventsPipe)

  private def tickerStream(tick: FiniteDuration, stopAtNbTick: Option[Long] = None)(implicit
      t: Timer[IO]
  ): Stream[IO, FiniteDuration] = {
    val stream = Stream.awakeEvery[IO](tick)
    stopAtNbTick match {
      case Some(value) => stream.take(value) // useful to stop an infinite stream
      case None        => stream
    }
  }

}

class CoinSyncEventTask(
    workerExchangeName: ExchangeName,
    eventsExchangeName: ExchangeName,
    conf: CoinConfig,
    db: Transactor[IO],
    rabbit: RabbitClient[IO],
    redis: RedisClient
)(implicit cs: ContextShift[IO])
    extends SyncEventTask {

  // Fetch publishable events from database.
  def publishableEvents: Stream[IO, WorkableEvent] =
    Queries
      .fetchPublishableEvents(conf.coinFamily, conf.coin)
      .transact(db)

  // Publisher publishing to the worker exchange with routingKey = "coinFamily.coin".
  private val publisher =
    new RabbitPublisher[UUID, WorkableEvent](
      redis,
      rabbit,
      workerExchangeName,
      conf.routingKey
    )

  // Publish events to the worker exchange queue, mark as published then insert.
  def publishEventsPipe: Pipe[IO, WorkableEvent, Unit] =
    _.evalMap { e =>
      publisher.enqueue(e) &>
        Queries
          .insertSyncEvent(e.asPublished)
          .transact(db)
          .void
    }

  // Consume reportable events from the events exchange queue.
  def reportableEvents: Stream[IO, ReportableEvent] =
    RabbitUtils
      .createAutoAckConsumer[ReportableEvent](
        rabbit,
        conf.queueName(eventsExchangeName)
      )

  // Insert reportable events in database and publish next pending event.
  def reportEventsPipe: Pipe[IO, ReportableEvent, Unit] =
    _.evalMap { e =>
      Queries.insertSyncEvent(e).transact(db).void *>
        publisher.dequeue(e.accountId)
    }

  // Fetch triggerable events from database.
  def triggerableEvents: Stream[IO, TriggerableEvent] =
    Queries
      .fetchTriggerableEvents(conf.coinFamily, conf.coin)
      .transact(db)

  // From triggerable events, construct next events then insert.
  def triggerEventsPipe: Pipe[IO, TriggerableEvent, Unit] =
    _.evalMap { e =>
      Queries.insertSyncEvent(e.nextWorkable).transact(db).void
    }

}
