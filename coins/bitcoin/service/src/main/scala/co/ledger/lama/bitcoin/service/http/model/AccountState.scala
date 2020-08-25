package co.ledger.lama.bitcoin.service.http.model

/**
  * @param balance  for example: ''436186104''
  * @param operationsCount
  */
case class AccountState(
    balance: String,
    operationsCount: OperationCount
)
