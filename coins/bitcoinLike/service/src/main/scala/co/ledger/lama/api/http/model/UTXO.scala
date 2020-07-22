package co.ledger.lama.api.http.model

/**
 * @param hash  for example: ''b463b9103f84cc5b67f4c0ea89668062d06d73df4c60d4388d78f04c0fe243f1''
 * @param outputIndex  for example: ''1''
 * @param address scriptPubKey address for example: ''2NDGDZy2DYjPnzkKCqC7ocmUq2iUugXmWk2''
 * @param height Block height for example: ''1486471''
 * @param confirmations  for example: ''2''
 * @param amount  for example: ''1000''
 */
case class UTXO (
  hash: String,
  outputIndex: Int,
  address: String,
  height: Int,
  confirmations: Int,
  amount: String
)

