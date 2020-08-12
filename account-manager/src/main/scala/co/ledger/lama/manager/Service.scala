package co.ledger.lama.manager

import java.util.concurrent.TimeUnit

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.manager.Exceptions.{CoinConfigurationException, MalformedProtobufException}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.SyncEvent.Status
import co.ledger.lama.manager.models.{Coin, CoinFamily, SyncEvent}
import co.ledger.lama.manager.protobuf.{
  AccountManagerServiceFs2Grpc,
  RegisterAccountRequest,
  RegisterAccountResult,
  UnregisterAccountRequest,
  UnregisterAccountResult
}
import co.ledger.lama.manager.utils.UuidUtils
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.Json
import io.grpc.Metadata

import scala.concurrent.duration.FiniteDuration

class Service(val db: Transactor[IO], val coinConfigs: List[CoinConfig])
    extends AccountManagerServiceFs2Grpc[IO, Metadata] {

  def definition(implicit ce: ConcurrentEffect[IO]) = AccountManagerServiceFs2Grpc.bindService(this)

  def registerAccount(
      request: RegisterAccountRequest,
      ctx: Metadata
  ): IO[RegisterAccountResult] = {
    val coinFamily = CoinFamily.fromProtobuf(request.coinFamily)
    val coin       = Coin.fromProtobuf(request.coin)
    val cursor     = cursorToJson(request)

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

        // create then insert the registered event
        syncEvent = SyncEvent.registered(accountId, cursor)
        _ <- Queries.insertSyncEvent(syncEvent)

      } yield (accountId, syncEvent.syncId, syncFrequency)

      response <-
        // run queries and return an sync event result
        queries
          .transact(db)
          .map {
            case (accountId, syncId, syncFrequency) =>
              RegisterAccountResult(
                UuidUtils.uuidToBytes(accountId),
                UuidUtils.uuidToBytes(syncId),
                syncFrequency.toSeconds
              )
          }
    } yield response
  }

  def unregisterAccount(
      request: UnregisterAccountRequest,
      ctx: Metadata
  ): IO[UnregisterAccountResult] = {
    val accountId =
      UuidUtils.fromAccountIdentifier(
        request.extendedKey,
        CoinFamily.fromProtobuf(request.coinFamily),
        Coin.fromProtobuf(request.coin)
      )

    for {
      existing <-
        Queries
          .getLastSyncEvent(accountId)
          .transact(db)
          .map(_.filter(e => e.status == Status.Unregistered || e.status == Status.Deleted))

      result <- existing match {
        case Some(e) =>
          IO.pure(
            UnregisterAccountResult(
              UuidUtils.uuidToBytes(e.accountId),
              UuidUtils.uuidToBytes(e.syncId)
            )
          )

        case _ =>
          // create then insert an unregistered event
          val event = SyncEvent.unregistered(accountId)
          Queries
            .insertSyncEvent(event)
            .transact(db)
            .map(_ =>
              UnregisterAccountResult(
                UuidUtils.uuidToBytes(event.accountId),
                UuidUtils.uuidToBytes(event.syncId)
              )
            )
      }
    } yield result
  }

  def cursorToJson(request: protobuf.RegisterAccountRequest): Json = {
    if (request.cursor.isBlockHeight)
      Json.obj(
        "blockHeight" -> Json.fromLong(
          request.cursor.blockHeight
            .map(_.state)
            .getOrElse(throw MalformedProtobufException(request))
        )
      )
    else
      Json.obj()
  }

}
