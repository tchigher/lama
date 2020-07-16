package co.ledger.lama.api.http.model

/**
 * @param RECEIVE  for example: ''211''
 * @param SEND  for example: ''17''
 */
case class OperationCount (
  RECEIVE: Int,
  SEND: Int
)

