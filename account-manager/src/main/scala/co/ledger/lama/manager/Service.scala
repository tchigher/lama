package co.ledger.lama.manager

import java.util.concurrent.TimeUnit

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.manager.Exceptions.CoinConfigurationException
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.{Coin, CoinFamily, SyncEvent}
import co.ledger.lama.manager.models.SyncEvent.Status
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountInfoResult,
  AccountManagerServiceFs2Grpc
}
import co.ledger.lama.manager.utils.UuidUtils
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.grpc.Metadata

import scala.concurrent.duration.FiniteDuration

class Service(val db: Transactor[IO], val coinConfigs: List[CoinConfig])
    extends AccountManagerServiceFs2Grpc[IO, Metadata] {

  def definition(implicit ce: ConcurrentEffect[IO]) = AccountManagerServiceFs2Grpc.bindService(this)

  def registerAccountToSync(
      request: AccountInfoRequest,
      ctx: Metadata
  ): IO[AccountInfoResult] = {
    val coinFamily = CoinFamily.fromProtobuf(request.coinFamily)
    val coin       = Coin.fromProtobuf(request.coin)

    val syncFrequencyFromRequest =
      if (request.syncFrequency > 0L)
        Some(FiniteDuration(request.syncFrequency, TimeUnit.SECONDS))
      else
        None

    for {
      // get the sync frequency from the request
      // or fallback to the default one from the coin configuration
      syncFrequency <- IO.fromOption {
        syncFrequencyFromRequest orElse
          coinConfigs
            .find(c => c.coinFamily == coinFamily && c.coin == coin)
            .map(_.syncFrequency)
      }(CoinConfigurationException(coinFamily, coin))

      // build queries
      queries = for {
        // upsert the account info
        accountInfo <-
          Queries
            .upsertAccountInfo(
              request.extendedKey,
              coinFamily,
              coin,
              syncFrequency
            )
        accountId     = accountInfo.accountId
        syncFrequency = accountInfo.syncFrequency

        // create the sync event
        syncEvent = SyncEvent.from(accountId, Status.Registered)

        // insert the sync event
        _ <- Queries.insertSyncEvent(syncEvent)
      } yield (accountId, syncEvent.syncId, syncFrequency)

      response <-
        // run queries and return an account info response
        queries
          .transact(db)
          .map {
            case (accountId, syncId, syncFrequency) =>
              AccountInfoResult(
                UuidUtils.uuidToBytes(accountId),
                UuidUtils.uuidToBytes(syncId),
                syncFrequency.toSeconds
              )
          }
    } yield response
  }

  def unregisterAccountToSync(
      request: AccountInfoRequest,
      ctx: Metadata
  ): IO[AccountInfoResult] = {
    val coinFamily = CoinFamily.fromProtobuf(request.coinFamily)
    val coin       = Coin.fromProtobuf(request.coin)
    val accountId  = UuidUtils.fromAccountIdentifier(request.extendedKey, coinFamily, coin)

    for {
      existing <-
        Queries
          .getLastSyncEvent(accountId)
          .transact(db)
          .map(_.filter(e => e.status == Status.Unregistered || e.status == Status.Deleted))

      result <- existing match {
        case Some(e) =>
          IO.pure(
            AccountInfoResult(
              UuidUtils.uuidToBytes(e.accountId),
              UuidUtils.uuidToBytes(e.syncId)
            )
          )

        case _ =>
          // create the sync event
          val syncEvent = SyncEvent.from(accountId, Status.Unregistered)

          // insert the sync event
          Queries
            .insertSyncEvent(syncEvent)
            .transact(db)
            .map(_ =>
              AccountInfoResult(
                UuidUtils.uuidToBytes(syncEvent.accountId),
                UuidUtils.uuidToBytes(syncEvent.syncId)
              )
            )
      }
    } yield result
  }

}
