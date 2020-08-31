package co.ledger.lama.bitcoin.worker

import cats.effect.{ConcurrentEffect, IO, Timer}
import cats.implicits._
import co.ledger.lama.bitcoin.worker.models.{AddressWithTransactions, PayloadData}
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.models.Status.{Registered, Unregistered}
import co.ledger.lama.common.models.{ReportableEvent, WorkableEvent}
import fs2.Stream
import io.circe.Json
import io.circe.syntax._

class Worker(
    syncEventService: SyncEventService,
    keychainService: KeychainServiceMock,
    explorerService: ExplorerService,
    interpreterService: InterpreterServiceMock,
    maxConcurrent: Int
) {

  def run(implicit ce: ConcurrentEffect[IO], t: Timer[IO]): Stream[IO, Unit] =
    syncEventService.consumeWorkableEvents
      .evalMap { workableEvent =>
        val reportableEvent = workableEvent.status match {
          case Registered   => synchronizeAccount(workableEvent)
          case Unregistered => deleteAccount(workableEvent)
        }

        // In case of error, fallback to a reportable failed event.
        reportableEvent
          .handleErrorWith { error =>
            val payloadData = PayloadData(errorMessage = Some(error.getMessage))
            val failedEvent = workableEvent.reportFailure(payloadData.asJson)
            IO.pure(failedEvent)
          }
          // Always report the event at the end.
          .flatMap(syncEventService.reportEvent)
      }

  def synchronizeAccount(
      workableEvent: WorkableEvent
  )(implicit ce: ConcurrentEffect[IO], t: Timer[IO]): IO[ReportableEvent] = {
    val extendedKey = workableEvent.payload.account.extendedKey

    val blockHashCursor =
      workableEvent.payload.data
        .as[PayloadData]
        .toOption
        .flatMap(_.blockHash)

    // Get addresses from the keychain.
    // TODO: waiting for the paginated get addresses keychain service
    //  to fetch until next range = fresh addresses.
    keychainService
      .getAddresses(extendedKey)
      // Parallelize fetch and save transactions for each address.
      .parEvalMapUnordered(maxConcurrent) { address =>
        for {
          // Fetch transactions from the explorer.
          transactions <-
            explorerService
              .getTransactions(address, blockHashCursor)
              .compile
              .toList

          // Ask to interpreter to save fetched transactions.
          _ <- interpreterService.saveTransactions(address, transactions)
        } yield AddressWithTransactions(address, transactions)
      }
      .chunkLimit(20) // Size of a keychain batch addresses per account.
      .map { chunk =>
        val chunkList = chunk.toList
        val chunkTxs  = chunkList.flatMap(_.txs)
        // Marked addresses as used:
        // we consider the whole batch of addresses as used if there are transactions on it.
        if (chunkTxs.nonEmpty)
          Stream.eval(
            keychainService.markAddressesAsUsed(extendedKey, chunkList.map(_.address))
          ) >> Stream.emits(chunkTxs)
        else
          Stream.empty
      }
      .parJoinUnbounded // race fetch txs streams concurrently
      .compile
      .toList
      .map { txs =>
        // From all account transactions, get the most recent block.
        val lastBlock = txs.maxBy(_.block.time).block

        // New cursor state.
        val payloadData = PayloadData(
          blockHeight = Some(lastBlock.height),
          blockHash = Some(lastBlock.hash),
          txsSize = Some(txs.size)
        )

        // Create the reportable successful event.
        workableEvent.reportSuccess(payloadData.asJson)
      }
  }

  // TODO:
  //  - ask interpreter to delete account
  //  - delete keychain
  //  - report successful deleted event
  def deleteAccount(workableEvent: WorkableEvent): IO[ReportableEvent] =
    IO.pure(workableEvent.reportSuccess(Json.obj()))

}
