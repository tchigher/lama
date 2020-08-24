package co.ledger.lama.manager

import cats.effect.IO
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.SyncEvent.{Payload, Status}
import co.ledger.lama.manager.models._
import co.ledger.lama.manager.protobuf
import co.ledger.lama.manager.utils.UuidUtils.bytesToUuid
import co.ledger.lama.manager.utils.RabbitUtils
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
          conf.orchestrator.workerExchangeName,
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

            messageSent1 <- worker.consumeMessage()

            // Report a successful sync event with a new cursor.
            syncedCursorJson = Json.obj("blockHeight" -> Json.fromLong(123456789))
            _ <- worker.reportEvent(
              messageSent1.copy(
                status = Status.Synchronized,
                payload = messageSent1.payload.copy(data = syncedCursorJson)
              )
            )

            messageSent2 <- worker.consumeMessage()

            // Report a failed sync event with an error message.
            syncFailedErrorJson = Json.obj("errorMessage" -> Json.fromString("failed to sync"))
            _ <- worker.reportEvent(
              messageSent2.copy(
                status = Status.SyncFailed,
                payload =
                  messageSent2.payload.copy(data = syncedCursorJson.deepMerge(syncFailedErrorJson))
              )
            )

            // Unregister an account.
            unregisteredResult <-
              service.unregisterAccount(unregisterAccountRequest, new Metadata())

            unregisteredAccountId = bytesToUuid(unregisteredResult.accountId).get
            unregisteredSyncId    = bytesToUuid(unregisteredResult.syncId).get

            messageSent3 <- worker.consumeMessage()

            // Report a failed delete event with an error message.
            deleteFailedErrorJson =
              Json.obj("errorMessage" -> Json.fromString("failed to delete data"))
            _ <- worker.reportEvent(
              messageSent3.copy(
                status = Status.DeleteFailed,
                payload = messageSent3.payload.copy(data = deleteFailedErrorJson)
              )
            )

            messageSent4 <- worker.consumeMessage()

            // Report a successful delete event.
            _ <- worker.reportEvent(
              messageSent4.copy(
                status = Status.Deleted,
                payload = Payload(accountTest)
              )
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
              messageSent1 shouldBe SyncEvent(
                accountTest.id,
                registeredSyncId,
                Status.Registered,
                Payload(accountTest)
              )

              messageSent2 shouldBe SyncEvent(
                accountTest.id,
                messageSent2.syncId,
                Status.Registered,
                Payload(accountTest, syncedCursorJson)
              )

              messageSent3 shouldBe SyncEvent(
                accountTest.id,
                unregisteredSyncId,
                Status.Unregistered,
                Payload(accountTest)
              )

              messageSent4 shouldBe SyncEvent(
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
                SyncEvent(
                  accountTest.id,
                  registeredSyncId,
                  Status.Registered,
                  Payload(accountTest)
                ),
                SyncEvent(
                  accountTest.id,
                  registeredSyncId,
                  Status.Published,
                  Payload(accountTest)
                ),
                SyncEvent(
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
                SyncEvent(
                  accountTest.id,
                  messageSent2.syncId,
                  Status.Registered,
                  Payload(accountTest, syncedCursorJson)
                ),
                SyncEvent(
                  accountTest.id,
                  messageSent2.syncId,
                  Status.Published,
                  Payload(accountTest, syncedCursorJson)
                ),
                SyncEvent(
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
                SyncEvent(
                  accountTest.id,
                  messageSent3.syncId,
                  Status.Unregistered,
                  Payload(accountTest)
                ),
                SyncEvent(
                  accountTest.id,
                  messageSent3.syncId,
                  Status.Published,
                  Payload(accountTest)
                ),
                SyncEvent(
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
                SyncEvent(
                  accountTest.id,
                  messageSent4.syncId,
                  Status.Unregistered,
                  Payload(accountTest, deleteFailedErrorJson)
                ),
                SyncEvent(
                  accountTest.id,
                  messageSent4.syncId,
                  Status.Published,
                  Payload(accountTest, deleteFailedErrorJson)
                ),
                SyncEvent(
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

  private val consumer: Stream[IO, SyncEvent] =
    RabbitUtils.createAutoAckConsumer[SyncEvent](rabbit, coinConf.queueName(inExchangeName))

  private val publisher: Stream[IO, SyncEvent => IO[Unit]] =
    RabbitUtils.createPublisher[SyncEvent](rabbit, outExchangeName, coinConf.routingKey)

  def consumeMessage(): IO[SyncEvent] =
    consumer.take(1).compile.last.map(_.get)

  def reportEvent(e: SyncEvent): IO[Unit] =
    publisher.evalMap(p => p(e)).compile.drain

}
