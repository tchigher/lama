package co.ledger.lama.bitcoin.service.http.model

/**
  * @param hex  for example: ''0100000001f3...''
  * @param signatures
  * @param addressDerivationPaths
  */
case class SendTransactionRequest(
    hex: String,
    signatures: List[String],
    addressDerivationPaths: List[String]
)
