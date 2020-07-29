package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{Blocker, ContextShift, IO, Resource}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.{CoinFamily, Coin}
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

  private val db: EmbeddedPostgres =
    EmbeddedPostgres.start()

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

  var accountId1: Option[UUID] = None
  var syncId1: Option[UUID]    = None

  it should "register an account" in IOAssertion {
    transactor.use { db =>
      val service     = new Service(db, conf.coins)
      val extendedKey = "xpub"
      val accountInfo = pb.AccountInfoRequest(extendedKey, pb.CoinFamily.bitcoin, pb.Coin.btc)

      val defaultBitcoinSyncFrequency = conf.coins.headOption.map(_.syncFrequency.toSeconds)

      service
        .registerAccountToSync(accountInfo, new Metadata())
        .map { response =>
          accountId1 = UuidUtils.bytesToUuid(response.accountId)
          syncId1 = UuidUtils.bytesToUuid(response.syncId)

          // should be an account uuid from extendKey, coinFamily, coin
          accountId1 shouldBe Some(
            UuidUtils.fromAccountIdentifier(
              extendedKey,
              CoinFamily.Bitcoin,
              Coin.Btc
            )
          )

          // should be a new sync id
          syncId1 should not be None

          // should be the default sync frequency from the bitcoin config
          Some(response.syncFrequency) shouldBe defaultBitcoinSyncFrequency
        }
    }
  }

  it should "upsert an already registered account" in IOAssertion {
    transactor.use { db =>
      val service          = new Service(db, conf.coins)
      val extendedKey      = "xpub"
      val newSyncFrequency = 10000L
      val accountInfo =
        pb.AccountInfoRequest(extendedKey, pb.CoinFamily.bitcoin, pb.Coin.btc, newSyncFrequency)

      service
        .registerAccountToSync(accountInfo, new Metadata())
        .map { response =>
          // update existing account id 1
          UuidUtils.bytesToUuid(response.accountId) shouldBe accountId1

          // but it should be a new sync id
          UuidUtils.bytesToUuid(response.syncId) should not be syncId1

          // and sync frequency should be updated
          response.syncFrequency shouldBe newSyncFrequency
        }
    }
  }

}

case class TestServiceConfig(coins: List[CoinConfig])
