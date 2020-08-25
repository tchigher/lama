package co.ledger.lama.bitcoin.service.http.model

/**
  * @param min  for example: ''100''
  * @param max  for example: ''500''
  */
case class RawBitcoinTransaction_estimatedSize(
    min: Option[Int],
    max: Option[Int]
)
