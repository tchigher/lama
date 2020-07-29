package co.ledger.lama.manager

import java.util.concurrent.TimeUnit

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.manager.Exceptions.CoinConfigurationException
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.{Coin, CoinFamily, SyncEvent}
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountInfoResponse,
  AccountManagerServiceFs2Grpc
}
import co.ledger.lama.manager.utils.UuidUtils
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.grpc.Metadata

import scala.concurrent.duration.FiniteDuration

class Service(val db: Transactor[IO], val coinConfigs: List[CoinConfig])
    extends AccountManagerServiceFs2Grpc[IO, Metadata] {

  def registerAccountToSync(request: AccountInfoRequest, ctx: Metadata): IO[AccountInfoResponse] = {
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

        // create the registered sync event
        syncEvent = SyncEvent.register(accountId)

        // insert the sync event
        _ <- Queries.insertSyncEvent(syncEvent)
      } yield (accountId, syncEvent.syncId, syncFrequency)

      response <-
        // run queries and return an account info response
        queries
          .transact(db)
          .map {
            case (accountId, syncId, syncFrequency) =>
              AccountInfoResponse(
                UuidUtils.uuidToBytes(accountId),
                UuidUtils.uuidToBytes(syncId),
                syncFrequency.toSeconds
              )
          }
    } yield response
  }

  def definition(implicit ce: ConcurrentEffect[IO]) = AccountManagerServiceFs2Grpc.bindService(this)

}
