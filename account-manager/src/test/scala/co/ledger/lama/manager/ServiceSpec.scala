package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{Blocker, ContextShift, IO, Resource}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.{Coin, CoinFamily}
import co.ledger.lama.manager.protobuf.AccountInfoRequest
import co.ledger.lama.manager.utils.UuidUtils
import co.ledger.lama.manager.{protobuf => pb}
import com.opentable.db.postgres.embedded.{EmbeddedPostgres, FlywayPreparer}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.grpc.Metadata
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext

class ServiceSpec extends AnyFlatSpecLike with Matchers with BeforeAndAfterAll {

  val db: EmbeddedPostgres =
    EmbeddedPostgres.start()

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val conf: TestServiceConfig = ConfigSource.default.loadOrThrow[TestServiceConfig]

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      te <- ExecutionContexts.cachedThreadPool[IO] // our transaction EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",               // driver classname
        db.getJdbcUrl("postgres", "postgres"), // connect URL
        "postgres",                            // username
        "",                                    // password
        ce,                                    // await connection here
        Blocker.liftExecutionContext(te)       // execute JDBC operations here
      )
    } yield xa

  var registerAccountId: Option[UUID] = None
  var syncId1: Option[UUID]           = None

  val bitcoinAccount: AccountInfoRequest =
    pb.AccountInfoRequest("12345", pb.CoinFamily.bitcoin, pb.Coin.btc)

  it should "register an account" in IOAssertion {
    transactor.use { db =>
      val service                     = new Service(db, conf.coins)
      val defaultBitcoinSyncFrequency = conf.coins.headOption.map(_.syncFrequency.toSeconds)

      service
        .registerAccountToSync(bitcoinAccount, new Metadata())
        .map { response =>
          // should be an account uuid from extendKey, coinFamily, coin
          registerAccountId = UuidUtils.bytesToUuid(response.accountId)
          registerAccountId shouldBe Some(
            UuidUtils.fromAccountIdentifier(
              bitcoinAccount.extendedKey,
              CoinFamily.Bitcoin,
              Coin.Btc
            )
          )

          // should be a new sync id
          syncId1 = UuidUtils.bytesToUuid(response.syncId)
          syncId1 should not be None

          // should be the default sync frequency from the bitcoin config
          Some(response.syncFrequency) shouldBe defaultBitcoinSyncFrequency
        }
    }
  }

  it should "upsert an already registered account" in IOAssertion {
    transactor.use { db =>
      val service = new Service(db, conf.coins)
      val newAccountInfo =
        bitcoinAccount.withSyncFrequency(10000L)

      service
        .registerAccountToSync(newAccountInfo, new Metadata())
        .map { response =>
          // update existing registered account
          UuidUtils.bytesToUuid(response.accountId) shouldBe registerAccountId

          // but it should be a new sync id
          UuidUtils.bytesToUuid(response.syncId) should not be oneOf(None, syncId1)

          // and sync frequency should be updated
          response.syncFrequency shouldBe newAccountInfo.syncFrequency
        }
    }
  }

  var unregisterSyncId: Option[UUID] = None

  it should "unregister an account" in IOAssertion {
    transactor.use { db =>
      new Service(db, conf.coins)
        .unregisterAccountToSync(bitcoinAccount, new Metadata())
        .map { response =>
          UuidUtils.bytesToUuid(response.accountId) shouldBe registerAccountId
          unregisterSyncId = UuidUtils.bytesToUuid(response.syncId)
          unregisterSyncId should not be oneOf(None, syncId1)
        }
    }
  }

  it should "return the same response if already unregister" in IOAssertion {
    transactor.use { db =>
      new Service(db, conf.coins)
        .unregisterAccountToSync(bitcoinAccount, new Metadata())
        .map { response =>
          UuidUtils.bytesToUuid(response.accountId) shouldBe registerAccountId
          UuidUtils.bytesToUuid(response.syncId) shouldBe unregisterSyncId
        }
    }
  }

  private val migrateDB: IO[Unit] =
    IO {
      // Run migration
      FlywayPreparer
        .forClasspathLocation("db/migration")
        .prepare(db.getPostgresDatabase)
    }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    migrateDB.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    db.close()
  }

}

case class TestServiceConfig(coins: List[CoinConfig])
