package co.ledger.lama

import co.ledger.lama.model._
import co.ledger.lama.model.implicits._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import fs2.Stream

object Queries {

  def fetchSyncCandidates(coin: CoinFamily): Stream[ConnectionIO, SyncPayload] =
    sql"""SELECT * 
          FROM account_sync_status
          WHERE updated + sync_frequency < CURRENT_TIMESTAMP
          AND coin = ($coin)
          AND status IN ('registered', 'synchronized', 'failed');
          """
      .query[SyncPayload]
      .stream

  def insertSyncEvent(e: SyncEvent): ConnectionIO[Int] = {
    sql"""
         INSERT INTO account_sync_event('account_id', 'sync_id', 'status', 'blockheight', 'error_message')
         VALUES(${e.accountId}, ${e.syncId}, ${e.status}, ${e.blockHeight}, ${e.errorMessage})
         """.update.run
  }

}
