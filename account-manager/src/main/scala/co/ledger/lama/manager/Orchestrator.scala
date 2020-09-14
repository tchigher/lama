package co.ledger.lama.manager

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.lama.common.utils.RabbitUtils
import co.ledger.lama.manager.config.{CoinConfig, OrchestratorConfig}
import com.redis.RedisClient
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeType
import doobie.util.transactor.Transactor
import fs2.Stream

import scala.concurrent.duration._

trait Orchestrator {

  val tasks: List[SyncEventTask]

  // duration to awake every 'd' candidates stream
  val awakeEvery: FiniteDuration = 5.seconds

  def run(
      stopAtNbTick: Option[Long] = None
  )(implicit c: Concurrent[IO], t: Timer[IO]): Stream[IO, Unit] =
    Stream
      .emits(tasks)
      .map { task =>
        val publishPipeline = task.publishEvents(awakeEvery, stopAtNbTick)
        val reportPipeline  = task.reportEvents
        val triggerPipelune = task.trigger(awakeEvery)

        // Race all inner streams simultaneously.
        publishPipeline
          .concurrently(reportPipeline)
          .concurrently(triggerPipelune)
      }
      .parJoinUnbounded

}

class CoinOrchestrator(
    val conf: OrchestratorConfig,
    val db: Transactor[IO],
    val rabbit: RabbitClient[IO],
    val redis: RedisClient
)(implicit cs: ContextShift[IO])
    extends Orchestrator {

  // Declare rabbitmq exchanges and bindings used by workers and the orchestrator.
  private def declareExchangesBindings(coinConf: CoinConfig): IO[Unit] = {
    val workerExchangeName = conf.workerEventsExchangeName
    val eventsExchangeName = conf.lamaEventsExchangeName

    val exchanges = List(
      (workerExchangeName, ExchangeType.Topic),
      (eventsExchangeName, ExchangeType.Topic)
    )

    val bindings = List(
      (eventsExchangeName, coinConf.routingKey, coinConf.queueName(eventsExchangeName)),
      (workerExchangeName, coinConf.routingKey, coinConf.queueName(workerExchangeName))
    )

    RabbitUtils.declareExchanges(rabbit, exchanges) *>
      RabbitUtils.declareBindings(rabbit, bindings)
  }

  val tasks: List[CoinSyncEventTask] = {
    // Declare exchanges and bindings immediately.
    conf.coins
      .map(declareExchangesBindings)
      .parSequence
      .unsafeRunSync()

    conf.coins
      .map { coinConf =>
        new CoinSyncEventTask(
          conf.workerEventsExchangeName,
          conf.lamaEventsExchangeName,
          coinConf,
          db,
          rabbit,
          redis
        )
      }
  }

}
