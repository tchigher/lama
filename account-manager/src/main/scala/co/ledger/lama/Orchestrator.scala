package co.ledger.lama

import cats.effect.{Concurrent, IO, Timer}
import co.ledger.lama.config.OrchestratorConfig
import co.ledger.lama.model.SyncEvent
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.RoutingKey
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream

trait Orchestrator {

  val updaters: List[Updater]

  def inserter(e: SyncEvent): IO[Unit]

  def syncEventSource: Stream[IO, SyncEvent]

  def syncEventInserts: Stream[IO, Unit] =
    syncEventSource.evalMap(inserter).drain

  def run(n: Option[Long] = None)(implicit c: Concurrent[IO], t: Timer[IO]): Stream[IO, Unit] =
    Stream
      .emits(updaters)
      .map(_.updates(n))
      .parJoinUnbounded
      .concurrently(syncEventInserts)

}

class CoinSyncOrchestrator(
    val conf: OrchestratorConfig,
    val db: Transactor[IO],
    val rabbitClient: RabbitClient[IO]
) extends Orchestrator {

  val updaters: List[Updater] =
    conf.updaters.map(new CoinUpdater(conf.workerExchangeName, _, db, rabbitClient))

  def inserter(se: SyncEvent): IO[Unit] = Queries.insertSyncEvent(se).transact(db).void

  def syncEventSource: Stream[IO, SyncEvent] =
    RabbitUtils
      .createAutoAckConsumer[SyncEvent](
        rabbitClient,
        conf.syncEventExchangeName,
        RoutingKey("*"),
        conf.syncEventQueueName
      )

}
