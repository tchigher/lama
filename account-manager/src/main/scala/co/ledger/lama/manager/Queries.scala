package co.ledger.lama.manager

import co.ledger.lama.manager.models._
import co.ledger.lama.manager.models.implicits._
import co.ledger.lama.manager.utils.UuidUtils
import doobie.postgres.implicits.UuidType
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.Fragments
import fs2.Stream
import org.postgresql.util.PGInterval

import scala.concurrent.duration.FiniteDuration

object Queries {

  def fetchSyncCandidates(coinFamily: CoinFamily, coin: Coin): Stream[ConnectionIO, SyncPayload] =
    (
      sql"""SELECT *
          FROM account_sync_status
          WHERE updated + sync_frequency < CURRENT_TIMESTAMP
          AND coin_family = $coinFamily
          AND coin = $coin
          AND """
        ++ Fragments.in(fr"status", SyncEvent.Status.syncCandidates)
    )
      .query[SyncPayload]
      .stream

  def upsertAccountInfo(
      extendedKey: String,
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: FiniteDuration
  ): ConnectionIO[UpsertAccountInfoResult] = {
    val syncFrequencyInterval = new PGInterval()
    syncFrequencyInterval.setSeconds(syncFrequency.toSeconds.toDouble)

    val accountId = UuidUtils.fromAccountIdentifier(extendedKey, coinFamily, coin)

    sql"""INSERT INTO account_info(account_id, extended_key, coin_family, coin, sync_frequency)
          VALUES($accountId, $extendedKey, $coinFamily, $coin, $syncFrequencyInterval)
          ON CONFLICT ON CONSTRAINT account_info_extended_key_coin_family_coin_key
            DO UPDATE SET sync_frequency = $syncFrequencyInterval
          RETURNING account_id, extract(epoch FROM sync_frequency)/60*60
          """
      .query[UpsertAccountInfoResult]
      .unique
  }

  def insertSyncEvent(e: SyncEvent): ConnectionIO[Int] =
    sql"""
         INSERT INTO account_sync_event(account_id, sync_id, status, payload)
         VALUES(${e.accountId}, ${e.syncId}, ${e.status}, ${e.payload})
         """.update.run

}
