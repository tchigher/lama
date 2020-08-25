package co.ledger.lama.bitcoin.service.http.model

/**
  * @param SLOW  for example: ''27047''
  * @param MEDIUM  for example: ''30000''
  * @param FAST  for example: ''50000''
  */
case class Fees(
    SLOW: String,
    MEDIUM: String,
    FAST: String
)
