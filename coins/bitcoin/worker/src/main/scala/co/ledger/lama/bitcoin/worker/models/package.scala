package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.worker.models.Keychain.Address
import co.ledger.lama.bitcoin.worker.models.explorer.{BlockHash, BlockHeight, Transaction}
import io.circe.generic.extras._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}

package object models {

  case class PayloadData(
      blockHeight: Option[BlockHeight] = None,
      blockHash: Option[BlockHash] = None,
      txsSize: Option[Int] = None,
      errorMessage: Option[String] = None
  )

  object PayloadData {
    implicit val encoder: Encoder[PayloadData] = deriveEncoder[PayloadData]
    implicit val decoder: Decoder[PayloadData] = deriveDecoder[PayloadData]
  }

  case class AddressWithTransactions(address: Address, txs: List[Transaction])

  object explorer {

    implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

    type BlockHash   = String
    type BlockHeight = Long

    case class Block(
        hash: BlockHash,
        height: BlockHeight,
        time: String,
        txs: Option[Seq[BlockHash]]
    )

    object Block {
      implicit val encoder: Encoder[Block] = deriveEncoder[Block]
      implicit val decoder: Decoder[Block] = deriveDecoder[Block]
    }

    case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

    object GetTransactionsResponse {
      implicit val encoder: Encoder[GetTransactionsResponse] =
        deriveEncoder[GetTransactionsResponse]
      implicit val decoder: Decoder[GetTransactionsResponse] =
        deriveDecoder[GetTransactionsResponse]
    }

    @ConfiguredJsonCodec case class Transaction(
        id: String,
        hash: String,
        receivedAt: String,
        lockTime: Long,
        fees: BigInt,
        inputs: Seq[Json],
        outputs: Seq[Json],
        block: Block,
        confirmations: Int
    )

    object Transaction {
      implicit val encoder: Encoder[Transaction] = deriveEncoder[Transaction]
      implicit val decoder: Decoder[Transaction] = deriveDecoder[Transaction]
    }

  }

  object Keychain {
    type Address = String
  }

}
