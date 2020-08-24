package co.ledger.lama.manager

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import cats.implicits._
import co.ledger.lama.manager.config.Config
import co.ledger.lama.manager.models.{AccountIdentifier, Coin, CoinFamily}
import co.ledger.lama.manager.utils.RabbitUtils
import com.redis.RedisClient
import dev.profunktor.fs2rabbit.config.deletion
import dev.profunktor.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterAll, TestSuite}
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

trait TestResources extends TestSuite with BeforeAndAfterAll {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  val accountTest: AccountIdentifier = AccountIdentifier("12345", CoinFamily.Bitcoin, Coin.Btc)

  private val dbUrl      = conf.postgres.url
  private val dbUser     = conf.postgres.user
  private val dbPassword = conf.postgres.password

  val transactor: Resource[IO, Transactor[IO]] = for {
    ce <- ExecutionContexts.fixedThreadPool[IO](conf.postgres.poolSize)
    te <- ExecutionContexts.cachedThreadPool[IO]
    db <- HikariTransactor.newHikariTransactor[IO](
      conf.postgres.driver,
      dbUrl,
      dbUser,
      dbPassword,
      ce,
      Blocker.liftExecutionContext(te)
    )
  } yield db

  val rabbit: Resource[IO, RabbitClient[IO]] = RabbitUtils.createClient(conf.rabbit)

  val redis: Resource[IO, RedisClient] =
    Resource.fromAutoCloseable(IO(new RedisClient(conf.redis.host, conf.redis.port)))

  def appResources: Resource[IO, (Transactor[IO], RedisClient, RabbitClient[IO])] =
    for {
      db           <- transactor
      redisClient  <- redis
      rabbitClient <- rabbit
    } yield (db, redisClient, rabbitClient)

  private val flyway: Flyway = Flyway
    .configure()
    .dataSource(dbUrl, dbUser, dbPassword)
    .locations(s"classpath:/db/migration")
    .load

  private def cleanDb(): IO[Unit] =
    IO(flyway.clean()) *> IO(flyway.migrate())

  private def cleanRedis(): IO[Unit] =
    redis.use { client =>
      IO(
        client.del(
          Publisher.onGoingEventsCounterKey(accountTest.id),
          Publisher.pendingEventsKey(accountTest.id)
        )
      ).void
    }

  private def cleanRabbit(): IO[Unit] =
    rabbit.use { client =>
      val coinConfs          = conf.orchestrator.coins
      val eventsExchangeName = conf.orchestrator.eventsExchangeName
      val workerExchangeName = conf.orchestrator.workerExchangeName

      client.createConnectionChannel.use { implicit channel =>
        val deleteQueues = coinConfs
          .map { coinConf =>
            client.deleteQueue(
              DeletionQueueConfig(
                coinConf.queueName(eventsExchangeName),
                deletion.Used,
                deletion.NonEmpty
              )
            ) &>
              client.deleteQueue(
                DeletionQueueConfig(
                  coinConf.queueName(workerExchangeName),
                  deletion.Used,
                  deletion.NonEmpty
                )
              )
          }
          .sequence
          .void

        deleteQueues *>
          (client.deleteExchange(DeletionExchangeConfig(eventsExchangeName, deletion.Used)) &>
            client.deleteExchange(DeletionExchangeConfig(workerExchangeName, deletion.Used)))
      }
    }

  override def beforeAll(): Unit = {
    super.beforeAll()
    val cleanUp = cleanDb() &> cleanRedis() &> cleanRabbit()
    cleanUp.unsafeRunSync()
  }

}
