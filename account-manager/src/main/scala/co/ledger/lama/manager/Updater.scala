package co.ledger.lama.manager

import cats.effect.{IO, Timer}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models._
import co.ledger.lama.manager.utils.RabbitUtils
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, RoutingKey}
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.{Pipe, Stream}

trait Updater {

  val conf: CoinConfig

  // source of sync event candidates
  def syncEventCandidates: Stream[IO, SyncPayload]

  // stream transformation from sync event candidates
  def syncPayloadSink: Pipe[IO, SyncPayload, Unit]

  // source then apply the stream transformation
  def updateTask: Stream[IO, Unit] =
    syncEventCandidates.through(syncPayloadSink)

  // stream which awake every `d` in order to call the `updateTask` stream
  def updates(n: Option[Long] = None)(implicit t: Timer[IO]): Stream[IO, Unit] = {
    val stream = Stream.awakeEvery[IO](conf.syncFrequency)

    (n match {
      case Some(value) => stream.take(value) // useful to stop an infinite stream
      case None        => stream
    }) >> updateTask
  }

}

class CoinUpdater(
    val exchangeName: ExchangeName,
    val conf: CoinConfig,
    val db: Transactor[IO],
    val rabbitClient: RabbitClient[IO]
) extends Updater {

  def syncEventCandidates: Stream[IO, SyncPayload] =
    Queries
      .fetchSyncCandidates(conf.coinFamily, conf.coin)
      .transact(db)

  private val publisher: Stream[IO, SyncPayload => IO[Unit]] =
    RabbitUtils.createPublisher[SyncPayload](
      rabbitClient,
      exchangeName,
      RoutingKey(s"${conf.coinFamily.name}")
    )

  def syncPayloadSink: Pipe[IO, SyncPayload, Unit] =
    in => publisher.flatMap(p => in.evalMap(p))

}
