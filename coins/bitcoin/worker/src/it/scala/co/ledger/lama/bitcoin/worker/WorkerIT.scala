package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.models.PayloadData
import co.ledger.lama.bitcoin.worker.services.{
  ExplorerService,
  InterpreterServiceMock,
  KeychainServiceMock,
  SyncEventService
}
import co.ledger.lama.common.models.{
  AccountIdentifier,
  Coin,
  CoinFamily,
  ReportableEvent,
  Status,
  SyncEvent,
  WorkableEvent
}
import co.ledger.lama.common.utils.{IOAssertion, RabbitUtils}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import io.circe.syntax._
import org.http4s.client.Client
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

class WorkerIT extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  val rabbit: Resource[IO, RabbitClient[IO]] = Clients.rabbit(conf.rabbit)

  val resources: Resource[IO, (RabbitClient[IO], Client[IO])] = for {
    rabbitClient <- rabbit
    httpClient   <- Clients.htt4s
  } yield (rabbitClient, httpClient)

  IOAssertion {
    setupRabbit() *>
      resources.use {
        case (rabbitClient, httpClient) =>
          val syncEventService = new SyncEventService(
            rabbitClient,
            conf.queueName(conf.workerEventsExchangeName),
            conf.lamaEventsExchangeName,
            conf.routingKey
          )

          val keychainService = new KeychainServiceMock

          val explorerService = new ExplorerService(httpClient, conf.explorer)

          val interpreterService = new InterpreterServiceMock

          val worker = new Worker(
            syncEventService,
            keychainService,
            explorerService,
            interpreterService,
            conf.maxConcurrent
          )

          val accountManager = new SimpleAccountManager(
            rabbitClient,
            conf.queueName(conf.lamaEventsExchangeName),
            conf.workerEventsExchangeName,
            conf.routingKey
          )

          val extendedKey =
            "xpub6CMDks73kuNu3p827zgf6q6aNfpdeTLHFQsM4ssEBDMYHYJYHJ7tmMuTdZPk5456YZeoTaCRHNnbEK2GK5wkztQQCpVKEHb2tnm9N1gz1ZT"

          val account = AccountIdentifier(extendedKey, CoinFamily.Bitcoin, Coin.Btc)

          val syncId = UUID.randomUUID()

          val registeredEvent =
            WorkableEvent(account.id, syncId, Status.Registered, SyncEvent.Payload(account))

          Stream
            .eval {
              accountManager.publishWorkableEvent(registeredEvent) *>
                accountManager.consumeReportableEvent
            }
            .concurrently(worker.run)
            .take(1)
            .compile
            .last
            .map { reportableEvent =>
              it should "have 35 addresses for the account" in {
                keychainService.usedAddresses.values.flatten.size shouldBe 35
              }

              it should "have a synchronized reportable event" in {
                reportableEvent shouldBe Some(
                  registeredEvent.reportSuccess(
                    PayloadData(
                      blockHeight = Some(644553L),
                      blockHash =
                        Some("0000000000000000000c44bf26af3b5b3c97e5aed67407fd551a90bc175de5a0"),
                      txsSize = Some(100)
                    ).asJson
                  )
                )
              }
            }
      }
  }

  def setupRabbit(): IO[Unit] =
    rabbit.use { client =>
      for {
        _ <- RabbitUtils.deleteBindings(
          client,
          List(
            conf.queueName(conf.workerEventsExchangeName),
            conf.queueName(conf.lamaEventsExchangeName)
          )
        )
        _ <- RabbitUtils.deleteExchanges(
          client,
          List(conf.workerEventsExchangeName, conf.lamaEventsExchangeName)
        )
        _ <- RabbitUtils.declareExchanges(
          client,
          List(
            (conf.workerEventsExchangeName, ExchangeType.Topic),
            (conf.lamaEventsExchangeName, ExchangeType.Topic)
          )
        )
        res <- RabbitUtils.declareBindings(
          client,
          List(
            (
              conf.workerEventsExchangeName,
              conf.routingKey,
              conf.queueName(conf.workerEventsExchangeName)
            ),
            (
              conf.lamaEventsExchangeName,
              conf.routingKey,
              conf.queueName(conf.lamaEventsExchangeName)
            )
          )
        )
      } yield res
    }

}

class SimpleAccountManager(
    rabbit: RabbitClient[IO],
    lamaEventsQueueName: QueueName,
    workerEventsExchangeName: ExchangeName,
    routingKey: RoutingKey
) {

  private lazy val consumer: Stream[IO, ReportableEvent] =
    RabbitUtils.createAutoAckConsumer[ReportableEvent](rabbit, lamaEventsQueueName)

  private lazy val publisher: Stream[IO, WorkableEvent => IO[Unit]] =
    RabbitUtils.createPublisher[WorkableEvent](rabbit, workerEventsExchangeName, routingKey)

  def consumeReportableEvent: IO[ReportableEvent] =
    consumer.take(1).compile.last.map(_.get)

  def publishWorkableEvent(e: WorkableEvent): IO[Unit] =
    publisher.evalMap(p => p(e)).compile.drain

}
