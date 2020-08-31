package co.ledger.lama.manager

import java.util.UUID

import co.ledger.lama.common.models.{AccountIdentifier, Coin, CoinFamily}
import co.ledger.lama.manager.utils.UuidUtils
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UuidUtilsSpec extends AnyFunSuite with Matchers {

  test("uuid to bytes") {
    val uuids = Gen.listOfN(1000, Gen.uuid).sample.get
    uuids.foreach { uuid =>
      val bytes = UuidUtils.uuidToBytes(uuid)
      assert(UuidUtils.bytesToUuid(bytes).contains(uuid))
    }
  }

  test("account identifier to uuid") {
    assert(
      AccountIdentifier("xpub", CoinFamily.Bitcoin, Coin.Btc).id ==
        UUID.fromString("281f7c1c-f92f-3144-a6b2-514d9a2080e4")
    )
  }

}
