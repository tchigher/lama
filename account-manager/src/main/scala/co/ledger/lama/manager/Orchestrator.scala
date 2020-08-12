package co.ledger.lama.manager

import cats.effect.{Concurrent, IO, Timer}
import co.ledger.lama.manager.config.OrchestratorConfig
import com.redis.RedisClient
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import doobie.util.transactor.Transactor
import fs2.Stream
import scala.concurrent.duration._

trait Orchestrator {

  // tasks which have candidates and reports streams
  val tasks: List[EventTask]

  // duration to awake every 'd' candidates stream
  val awakeEvery: FiniteDuration = 5.seconds

  def run(n: Option[Long] = None)(implicit c: Concurrent[IO], t: Timer[IO]): Stream[IO, Unit] = {

    val candidateEventsPipeline =
      Stream
        .emits(tasks)                     // emit tasks
        .map(_.candidates(awakeEvery, n)) // for each, call candidates stream
        .parJoinUnbounded                 // race all inner streams simultaneously

    val reportsFinishedEventsPipeline =
      Stream
        .emits(tasks)     // emit tasks
        .map(_.reports)   // for each, call reports stream
        .parJoinUnbounded // race all inner streams simultaneously

    // race the stream of candidate events
    // and also, at the same, race the stream of report events.
    candidateEventsPipeline
      .concurrently(reportsFinishedEventsPipeline)
  }

}

class CoinOrchestrator(
    val conf: OrchestratorConfig,
    val db: Transactor[IO],
    val rabbit: RabbitClient[IO],
    val redis: RedisClient
) extends Orchestrator {

  val tasks: List[CoinTask] =
    conf.coins.map(
      new CoinTask(conf.workerExchangeName, conf.eventsExchangeName, _, db, rabbit, redis)
    )

}
