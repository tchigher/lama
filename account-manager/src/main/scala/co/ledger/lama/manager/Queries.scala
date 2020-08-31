package co.ledger.lama.manager

import java.util.UUID

import cats.data.NonEmptyList
import co.ledger.lama.common.models.{
  AccountIdentifier,
  Coin,
  CoinFamily,
  SyncEvent,
  TriggerableEvent,
  TriggerableStatus,
  WorkableEvent,
  WorkableStatus
}
import co.ledger.lama.manager.models._
import co.ledger.lama.manager.models.implicits._
import doobie.Fragments
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream
import org.postgresql.util.PGInterval

import scala.concurrent.duration.FiniteDuration

object Queries {

  def fetchPublishableEvents(
      coinFamily: CoinFamily,
      coin: Coin
  ): Stream[ConnectionIO, WorkableEvent] =
    (
      sql"""SELECT account_id, sync_id, status, payload
          FROM account_sync_status
          WHERE coin_family = $coinFamily
          AND coin = $coin
          AND """
        ++ Fragments.in(fr"status", NonEmptyList.fromListUnsafe(WorkableStatus.all.values.toList))
    )
      .query[WorkableEvent]
      .stream

  def fetchTriggerableEvents(
      coinFamily: CoinFamily,
      coin: Coin
  ): Stream[ConnectionIO, TriggerableEvent] =
    (
      sql"""SELECT account_id, sync_id, status, payload
          FROM account_sync_status
          WHERE updated + sync_frequency < CURRENT_TIMESTAMP
          AND coin_family = $coinFamily
          AND coin = $coin
          AND """
        ++ Fragments.in(
          fr"status",
          NonEmptyList.fromListUnsafe(TriggerableStatus.all.values.toList)
        )
    )
      .query[TriggerableEvent]
      .stream

  def getAccountInfo(accountIdentifier: AccountIdentifier): ConnectionIO[Option[AccountInfo]] =
    sql"""SELECT account_id, extract(epoch FROM sync_frequency)/60*60
          FROM account_info
          WHERE account_id = ${accountIdentifier.id}
         """
      .query[AccountInfo]
      .option

  def upsertAccountInfo(
      accountIdentifier: AccountIdentifier,
      syncFrequency: FiniteDuration
  ): ConnectionIO[AccountInfo] = {
    val accountId   = accountIdentifier.id
    val extendedKey = accountIdentifier.extendedKey
    val coinFamily  = accountIdentifier.coinFamily
    val coin        = accountIdentifier.coin

    val syncFrequencyInterval = new PGInterval()
    syncFrequencyInterval.setSeconds(syncFrequency.toSeconds.toDouble)

    sql"""INSERT INTO account_info(account_id, extended_key, coin_family, coin, sync_frequency)
          VALUES($accountId, $extendedKey, $coinFamily, $coin, $syncFrequencyInterval)
          ON CONFLICT (account_id)
            DO UPDATE SET sync_frequency = $syncFrequencyInterval
          RETURNING account_id, extract(epoch FROM sync_frequency)/60*60
          """
      .query[AccountInfo]
      .unique
  }

  def insertSyncEvent(e: SyncEvent): ConnectionIO[Int] =
    sql"""
         INSERT INTO account_sync_event(account_id, sync_id, status, payload)
         VALUES(${e.accountId}, ${e.syncId}, ${e.status}, ${e.payload})
         """.update.run

  def getLastSyncEvent(accountId: UUID): ConnectionIO[Option[SyncEvent]] =
    sql"""SELECT account_id, sync_id, status, payload
          FROM account_sync_status
          WHERE account_id = $accountId
          """
      .query[SyncEvent]
      .option

  def getSyncEvents(accountId: UUID): Stream[ConnectionIO, SyncEvent] =
    sql"""SELECT account_id, sync_id, status, payload
          FROM account_sync_event
          WHERE account_id = $accountId
          """
      .query[SyncEvent]
      .stream
}
