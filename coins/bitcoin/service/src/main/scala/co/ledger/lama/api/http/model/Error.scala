package co.ledger.lama.api.http.model

/**
 * @param code  for example: ''-26''
 * @param label  for example: ''Attemped to double-spend''
 */
case class Error (
  code: Int,
  label: String
)

