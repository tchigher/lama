package co.ledger.lama.manager

import cats.effect.{Concurrent, IO, Timer}
import co.ledger.lama.manager.config.OrchestratorConfig
import co.ledger.lama.manager.models.SyncEvent
import co.ledger.lama.manager.utils.RabbitUtils
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.RoutingKey
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream

trait Orchestrator {

  // updaters which have a stream constructor
  val updaters: List[Updater]

  // inserter of sync event
  def inserter(e: SyncEvent): IO[Unit]

  // source of sync events
  def syncEventSource: Stream[IO, SyncEvent]

  // from sync events source, insert them with the `inserter` function
  def syncEventInserts: Stream[IO, Unit] =
    syncEventSource.evalMap(inserter).drain

  def run(n: Option[Long] = None)(implicit c: Concurrent[IO], t: Timer[IO]): Stream[IO, Unit] =
    Stream
      .emits(updaters)                // emit updaters
      .map(_.updates(n))              // for each updaters, create a stream
      .parJoinUnbounded               // race all inner streams simultaneously
      .concurrently(syncEventInserts) // also, at the same time, source sync events and insert them

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
