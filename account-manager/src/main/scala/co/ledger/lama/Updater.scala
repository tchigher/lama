package co.ledger.lama

import cats.effect.{IO, Timer}
import co.ledger.lama.config.UpdaterConfig
import co.ledger.lama.model._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, RoutingKey}
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.{Pipe, Stream}

trait Updater {

  val conf: UpdaterConfig

  def syncEventCandidates: Stream[IO, SyncPayload]

  def syncPayloadSink: Pipe[IO, SyncPayload, Unit]

  def updateTask: Stream[IO, Unit] =
    syncEventCandidates.through(syncPayloadSink)

  def updates(n: Option[Long] = None)(implicit t: Timer[IO]): Stream[IO, Unit] = {
    val stream = Stream.awakeEvery[IO](conf.syncFrequency)
    (n match {
      case Some(value) => stream.take(value)
      case None        => stream
    }) >> updateTask
  }

}

class CoinUpdater(
    val exchangeName: ExchangeName,
    val conf: UpdaterConfig,
    val db: Transactor[IO],
    val rabbitClient: RabbitClient[IO]
) extends Updater {

  def syncEventCandidates: Stream[IO, SyncPayload] =
    Queries
      .fetchSyncCandidates(conf.coinFamily)
      .transact(db)

  private val publisher: Stream[IO, SyncPayload => IO[Unit]] =
    RabbitUtils.createPublisher[SyncPayload](
      rabbitClient,
      exchangeName,
      RoutingKey(conf.coinFamily.name)
    )

  def syncPayloadSink: Pipe[IO, SyncPayload, Unit] =
    in => publisher.flatMap(p => in.evalMap(p))

}
