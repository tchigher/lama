package co.ledger.lama

import java.util.UUID

import co.ledger.lama.model.{CoinFamily, SyncPayload}

object Fixtures {

  def generateSyncPayloads(n: Int): Seq[SyncPayload] = {
    (1 to n).map { i =>
      SyncPayload(
        accountId = UUID.randomUUID(),
        syncId = UUID.randomUUID(),
        xpub = s"xpub-$i",
        coinFamily = CoinFamily.Bitcoin,
        blockHeight = i * 10L
      )
    }
  }

}
