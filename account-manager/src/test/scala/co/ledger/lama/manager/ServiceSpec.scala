package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{Blocker, ContextShift, IO, Resource}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.{Coin, CoinFamily, SyncEvent}
import co.ledger.lama.manager.protobuf.BlockHeightState
import co.ledger.lama.manager.utils.UuidUtils
import co.ledger.lama.manager.{protobuf => pb}
import com.opentable.db.postgres.embedded.{EmbeddedPostgres, FlywayPreparer}
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import io.circe.Json
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

  var registeredAccountId: UUID = _
  var registeredSyncId: UUID    = _

  val registerBitcoinAccount: pb.RegisterAccountRequest =
    pb.RegisterAccountRequest("12345", pb.CoinFamily.bitcoin, pb.Coin.btc)

  it should "register a new account" in IOAssertion {
    transactor.use { db =>
      val service                     = new Service(db, conf.coins)
      val defaultBitcoinSyncFrequency = conf.coins.head.syncFrequency.toSeconds

      for {
        response <- service.registerAccount(registerBitcoinAccount, new Metadata())

        accountId     = UuidUtils.bytesToUuid(response.accountId).get
        syncId        = UuidUtils.bytesToUuid(response.syncId).get
        syncFrequency = response.syncFrequency

        event <- getLastEvent(db, accountId)
      } yield {
        registeredAccountId = accountId
        registeredSyncId = syncId

        // it should be an account uuid from extendKey, coinFamily, coin
        accountId shouldBe
          UuidUtils.fromAccountIdentifier(
            registerBitcoinAccount.extendedKey,
            CoinFamily.Bitcoin,
            Coin.Btc
          )

        // it should be the default sync frequency from the bitcoin config
        syncFrequency shouldBe defaultBitcoinSyncFrequency

        // check event
        event shouldBe Some(SyncEvent(accountId, syncId, SyncEvent.Status.Registered))
      }
    }
  }

  it should "upsert an already registered account" in IOAssertion {
    transactor.use { db =>
      val service = new Service(db, conf.coins)
      val newAccountInfo =
        registerBitcoinAccount.withSyncFrequency(10000L)

      for {
        response <- service.registerAccount(newAccountInfo, new Metadata())

        accountId     = UuidUtils.bytesToUuid(response.accountId).get
        syncId        = UuidUtils.bytesToUuid(response.syncId).get
        syncFrequency = response.syncFrequency

        event <- getLastEvent(db, accountId)
      } yield {
        // it should be the registered accountId
        accountId shouldBe registeredAccountId

        // it should be a new sync id
        syncId should not be registeredSyncId

        // the sync frequency should be updated
        syncFrequency shouldBe newAccountInfo.syncFrequency

        // check event
        event shouldBe Some(SyncEvent(accountId, syncId, SyncEvent.Status.Registered))
      }
    }
  }

  it should "register an account from a blockHeight cursor" in IOAssertion {
    transactor.use { db =>
      val service          = new Service(db, conf.coins)
      val blockHeightValue = 10L
      val accountInfoWithCursor =
        registerBitcoinAccount.withBlockHeight(BlockHeightState(blockHeightValue))

      for {
        response <- service.registerAccount(accountInfoWithCursor, new Metadata())

        accountId = UuidUtils.bytesToUuid(response.accountId).get
        syncId    = UuidUtils.bytesToUuid(response.syncId).get

        event <- getLastEvent(db, accountId)
      } yield {
        // it should be the registered accountId
        accountId shouldBe registeredAccountId

        // it should be a new sync id
        syncId should not be registeredSyncId

        // check event
        event shouldBe Some(
          SyncEvent(
            accountId,
            syncId,
            SyncEvent.Status.Registered,
            Json.obj("blockHeight" -> Json.fromLong(blockHeightValue))
          )
        )
      }
    }
  }

  var unregisteredSyncId: UUID     = _
  var unregisteredEvent: SyncEvent = _

  val unregisterAccountRequest: pb.UnregisterAccountRequest =
    pb.UnregisterAccountRequest(
      registerBitcoinAccount.extendedKey,
      registerBitcoinAccount.coinFamily,
      registerBitcoinAccount.coin
    )

  it should "unregister an account" in IOAssertion {
    transactor.use { db =>
      val service = new Service(db, conf.coins)

      for {
        response <- service.unregisterAccount(unregisterAccountRequest, new Metadata())

        accountId = UuidUtils.bytesToUuid(response.accountId).get
        syncId    = UuidUtils.bytesToUuid(response.syncId).get

        event <- getLastEvent(db, accountId)

      } yield {
        accountId shouldBe registeredAccountId
        unregisteredSyncId = syncId
        unregisteredSyncId should not be registeredSyncId

        // check event
        unregisteredEvent = event.get
        unregisteredEvent shouldBe
          SyncEvent(
            accountId,
            syncId,
            SyncEvent.Status.Unregistered
          )
      }
    }
  }

  it should "return the same response if already unregistered" in IOAssertion {
    transactor.use { db =>
      val service = new Service(db, conf.coins)
      for {
        response <- service.unregisterAccount(unregisterAccountRequest, new Metadata())

        accountId = UuidUtils.bytesToUuid(response.accountId).get
        syncId    = UuidUtils.bytesToUuid(response.syncId).get

        event <- getLastEvent(db, accountId)

      } yield {
        accountId shouldBe registeredAccountId
        syncId shouldBe unregisteredSyncId
        event shouldBe Some(unregisteredEvent)
      }
    }
  }

  private def getLastEvent(db: Transactor[IO], accountId: UUID): IO[Option[SyncEvent]] =
    Queries.getLastSyncEvent(accountId).transact(db)

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
