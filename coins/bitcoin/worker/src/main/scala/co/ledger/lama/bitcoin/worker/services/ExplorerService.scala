package co.ledger.lama.bitcoin.worker.services

import cats.effect.{ConcurrentEffect, IO, Timer}
import co.ledger.lama.bitcoin.worker.config.ExplorerConfig
import co.ledger.lama.bitcoin.worker.models.explorer._
import io.circe.Decoder
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.Method
import fs2.{Chunk, Pull, Stream}

class ExplorerService(httpClient: Client[IO], conf: ExplorerConfig) {

  def getCurrentBlock: IO[Block] =
    httpClient.expect[Block](conf.uri.withPath("/blocks/current"))

  def getBlock(hash: BlockHash): IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"/blocks/$hash"))

  def getBlock(height: BlockHeight): IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"/blocks/$height"))

  def getTransactions(
      address: String,
      blockHash: Option[BlockHash]
  )(implicit
      ce: ConcurrentEffect[IO],
      t: Timer[IO]
  ): Stream[IO, Transaction] =
    fetchPaginatedTransactions(httpClient, address, blockHash).stream
      .flatMap(res => Stream.emits(res.txs))
      .timeout(conf.timeout)

  private def getTransactionsRequest(address: String, blockHash: Option[BlockHash]) = {
    val baseUri =
      conf.uri
        .withPath(s"/addresses/$address/transactions")
        .withQueryParam("no_token", true)
        .withQueryParam("batch_size", conf.txsBatchSize)

    Request[IO](
      Method.GET,
      blockHash match {
        case Some(value) => baseUri.withQueryParam("block_hash", value)
        case None        => baseUri
      }
    )

  }

  private def fetchPaginatedTransactions(
      client: Client[IO],
      address: String,
      blockHash: Option[BlockHash]
  )(implicit
      decoder: Decoder[GetTransactionsResponse]
  ): Pull[IO, GetTransactionsResponse, Unit] =
    Pull
      .eval(client.expect[GetTransactionsResponse](getTransactionsRequest(address, blockHash)))
      .flatMap { res =>
        if (res.truncated) {
          // The explorer returns batch_size + 1 txs.
          // So, we need to drop the last tx to avoid having duplicate txs.
          val fixedRes      = res.copy(txs = res.txs.dropRight(1))
          val lastBlockHash = res.txs.lastOption.map(_.block.hash)
          Pull.output(Chunk(fixedRes)) >>
            fetchPaginatedTransactions(client, address, lastBlockHash)
        } else {
          Pull.output(Chunk(res))
        }
      }

}
