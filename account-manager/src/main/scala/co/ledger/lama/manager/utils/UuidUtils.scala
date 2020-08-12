package co.ledger.lama.manager.utils

import java.nio.ByteBuffer
import java.util.UUID

import co.ledger.lama.manager.models.{CoinFamily, Coin}
import com.google.protobuf.ByteString

object UuidUtils {

  def uuidToBytes(uuid: UUID): ByteString = {
    val buffer = ByteBuffer.allocate(16) // uuid = 16 bits
    buffer.putLong(uuid.getMostSignificantBits)  // put the most sig bits first
    buffer.putLong(uuid.getLeastSignificantBits) // then put the least sig bits
    buffer.rewind()                              // rewind: the position is set to zero and the mark is discarded
    ByteString.copyFrom(buffer)
  }

  def bytesToUuid(bytes: ByteString): Option[UUID] = {
    val buffer = bytes.asReadOnlyByteBuffer()
    if (buffer.capacity() != 16) None
    else Some(new UUID(buffer.getLong(0), buffer.getLong(8)))
  }

  def fromAccountIdentifier(extendedKey: String, coinFamily: CoinFamily, coin: Coin): UUID =
    UUID.nameUUIDFromBytes((extendedKey + coinFamily.name + coin.name).getBytes)

}
