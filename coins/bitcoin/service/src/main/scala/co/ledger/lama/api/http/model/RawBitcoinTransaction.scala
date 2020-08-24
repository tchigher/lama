package co.ledger.lama.api.http.model

/**
 * @param estimatedSize 
 * @param fees  for example: ''5000''
 * @param hex  for example: ''0100000001f3...''
 */
case class RawBitcoinTransaction (
  estimatedSize: RawBitcoinTransaction_estimatedSize,
  fees: String,
  hex: String
)

