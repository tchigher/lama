package co.ledger.lama.manager

import cats.effect.IO
import co.ledger.lama.common.models._
import co.ledger.lama.common.models.SyncEvent.Payload
import co.ledger.lama.common.utils.{IOAssertion, RabbitUtils}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.protobuf
import co.ledger.lama.manager.utils.UuidUtils.bytesToUuid
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeName
import doobie.implicits._
import fs2.Stream
import io.circe.Json
import io.grpc.Metadata
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class AccountManagerIT extends AnyFlatSpecLike with Matchers with TestResources {

  IOAssertion {
    appResources.use {
      case (db, redisClient, rabbitClient) =>
        val service = new Service(db, conf.orchestrator.coins)

        val coinOrchestrator =
          new CoinOrchestrator(conf.orchestrator, db, rabbitClient, redisClient)

        val worker = new SimpleWorker(
          rabbitClient,
          conf.orchestrator.workerEventsExchangeName,
          conf.orchestrator.lamaEventsExchangeName,
          conf.orchestrator.coins.head
        )

        val nbEvents = 12

        val registerAccountRequest = protobuf.RegisterAccountRequest(
          accountTest.extendedKey,
          protobuf.CoinFamily.bitcoin,
          protobuf.Coin.btc
        )

        val unregisterAccountRequest = protobuf.UnregisterAccountRequest(
          accountTest.extendedKey,
          protobuf.CoinFamily.bitcoin,
          protobuf.Coin.btc
        )

        def runTests(): IO[Unit] =
          for {
            // Register an account.
            registeredResult <- service.registerAccount(registerAccountRequest, new Metadata())

            registeredAccountId = bytesToUuid(registeredResult.accountId).get
            registeredSyncId    = bytesToUuid(registeredResult.syncId).get

            messageSent1 <- worker.consumeWorkableEvent()

            // Report a successful sync event with a new cursor.
            syncedCursorJson = Json.obj("blockHeight" -> Json.fromLong(123456789))
            _ <- worker.publishReportableEvent(
              messageSent1.reportSuccess(syncedCursorJson)
            )

            messageSent2 <- worker.consumeWorkableEvent()

            // Report a failed sync event with an error message.
            syncFailedErrorJson = Json.obj("errorMessage" -> Json.fromString("failed to sync"))
            _ <- worker.publishReportableEvent(
              messageSent2.reportFailure(syncedCursorJson.deepMerge(syncFailedErrorJson))
            )

            // Unregister an account.
            unregisteredResult <-
              service.unregisterAccount(unregisterAccountRequest, new Metadata())

            unregisteredAccountId = bytesToUuid(unregisteredResult.accountId).get
            unregisteredSyncId    = bytesToUuid(unregisteredResult.syncId).get

            messageSent3 <- worker.consumeWorkableEvent()

            // Report a failed delete event with an error message.
            deleteFailedErrorJson =
              Json.obj("errorMessage" -> Json.fromString("failed to delete data"))
            _ <- worker.publishReportableEvent(
              messageSent3.reportFailure(deleteFailedErrorJson)
            )

            messageSent4 <- worker.consumeWorkableEvent()

            // Report a successful delete event.
            _ <- worker.publishReportableEvent(
              messageSent4.reportSuccess(Json.obj())
            )

            // Fetch all sync events.
            syncEvents <-
              Queries
                .getSyncEvents(accountTest.id)
                .take(nbEvents)
                .compile
                .toList
                .transact(db)
          } yield {
            it should "have consumed messages from worker" in {
              messageSent1 shouldBe WorkableEvent(
                accountTest.id,
                registeredSyncId,
                Status.Registered,
                Payload(accountTest)
              )

              messageSent2 shouldBe WorkableEvent(
                accountTest.id,
                messageSent2.syncId,
                Status.Registered,
                Payload(accountTest, syncedCursorJson)
              )

              messageSent3 shouldBe WorkableEvent(
                accountTest.id,
                unregisteredSyncId,
                Status.Unregistered,
                Payload(accountTest)
              )

              messageSent4 shouldBe WorkableEvent(
                accountTest.id,
                messageSent4.syncId,
                Status.Unregistered,
                Payload(accountTest, deleteFailedErrorJson)
              )
            }

            it should s"have $nbEvents inserted events" in {
              syncEvents should have size nbEvents
            }

            it should "succeed to register an account" in {
              registeredAccountId shouldBe accountTest.id
            }

            it should "have (registered -> published -> synchronized) events for the first iteration" in {
              val eventsBatch1 = syncEvents.slice(0, 3)
              eventsBatch1 shouldBe List(
                WorkableEvent(
                  accountTest.id,
                  registeredSyncId,
                  Status.Registered,
                  Payload(accountTest)
                ),
                FlaggedEvent(
                  accountTest.id,
                  registeredSyncId,
                  Status.Published,
                  Payload(accountTest)
                ),
                ReportableEvent(
                  accountTest.id,
                  registeredSyncId,
                  Status.Synchronized,
                  Payload(accountTest, syncedCursorJson)
                )
              )
            }

            it should "have (registered -> published -> sync_failed) events for the next iteration" in {
              val eventsBatch2 = syncEvents.slice(3, 6)
              eventsBatch2 shouldBe List(
                WorkableEvent(
                  accountTest.id,
                  messageSent2.syncId,
                  Status.Registered,
                  Payload(accountTest, syncedCursorJson)
                ),
                FlaggedEvent(
                  accountTest.id,
                  messageSent2.syncId,
                  Status.Published,
                  Payload(accountTest, syncedCursorJson)
                ),
                ReportableEvent(
                  accountTest.id,
                  messageSent2.syncId,
                  Status.SyncFailed,
                  Payload(accountTest, syncedCursorJson.deepMerge(syncFailedErrorJson))
                )
              )
            }

            it should "succeed to unregister an account" in {
              unregisteredAccountId shouldBe accountTest.id
            }

            it should "have (unregistered -> published -> delete_failed) events for the next iteration" in {
              val eventsBatch3 = syncEvents.slice(6, 9)
              eventsBatch3 shouldBe List(
                WorkableEvent(
                  accountTest.id,
                  messageSent3.syncId,
                  Status.Unregistered,
                  Payload(accountTest)
                ),
                FlaggedEvent(
                  accountTest.id,
                  messageSent3.syncId,
                  Status.Published,
                  Payload(accountTest)
                ),
                ReportableEvent(
                  accountTest.id,
                  messageSent3.syncId,
                  Status.DeleteFailed,
                  Payload(accountTest, deleteFailedErrorJson)
                )
              )
            }

            it should "have (unregistered -> published -> deleted) events at the end" in {
              val eventsBatch4 = syncEvents.slice(9, 12)
              eventsBatch4 shouldBe List(
                WorkableEvent(
                  accountTest.id,
                  messageSent4.syncId,
                  Status.Unregistered,
                  Payload(accountTest, deleteFailedErrorJson)
                ),
                FlaggedEvent(
                  accountTest.id,
                  messageSent4.syncId,
                  Status.Published,
                  Payload(accountTest, deleteFailedErrorJson)
                ),
                ReportableEvent(
                  accountTest.id,
                  messageSent4.syncId,
                  Status.Deleted,
                  Payload(accountTest)
                )
              )
            }
          }

        coinOrchestrator
          .run(stopAtNbTick = Some(nbEvents))    // run the orchestrator
          .concurrently(Stream.eval(runTests())) // and run tests at the same time
          .timeout(5.minutes)
          .compile
          .drain
    }
  }

}

class SimpleWorker(
    rabbit: RabbitClient[IO],
    inExchangeName: ExchangeName,
    outExchangeName: ExchangeName,
    coinConf: CoinConfig
) {

  private val consumer: Stream[IO, WorkableEvent] =
    RabbitUtils.createAutoAckConsumer[WorkableEvent](rabbit, coinConf.queueName(inExchangeName))

  private val publisher: Stream[IO, ReportableEvent => IO[Unit]] =
    RabbitUtils.createPublisher[ReportableEvent](rabbit, outExchangeName, coinConf.routingKey)

  def consumeWorkableEvent(): IO[WorkableEvent] =
    consumer.take(1).compile.last.map(_.get)

  def publishReportableEvent(e: ReportableEvent): IO[Unit] =
    publisher.evalMap(p => p(e)).compile.drain

}
