package co.ledger.lama.manager.utils

import java.util.UUID

import co.ledger.lama.manager.protobuf
import co.ledger.lama.common.models._
import io.circe.parser.parse

object ProtobufUtils {

  def from(pb: protobuf.AccountInfoRequest): AccountIdentifier =
    AccountIdentifier(
      pb.extendedKey,
      from(pb.coinFamily),
      from(pb.coin)
    )

  def from(pb: protobuf.RegisterAccountRequest): AccountIdentifier =
    AccountIdentifier(
      pb.extendedKey,
      from(pb.coinFamily),
      from(pb.coin)
    )

  def from(accountId: UUID, pb: protobuf.SyncEvent): Option[SyncEvent] =
    for {
      syncId  <- UuidUtils.bytesToUuid(pb.syncId)
      status  <- Status.fromKey(pb.status)
      payload <- parse(new String(pb.payload.toByteArray)).flatMap(_.as[SyncEvent.Payload]).toOption
    } yield {
      SyncEvent(
        accountId,
        syncId,
        status,
        payload
      )
    }

  def from(pb: protobuf.CoinFamily): CoinFamily =
    CoinFamily.fromKey(pb.name).get

  def from(pb: protobuf.Coin): Coin =
    Coin.fromKey(pb.name).get

}
