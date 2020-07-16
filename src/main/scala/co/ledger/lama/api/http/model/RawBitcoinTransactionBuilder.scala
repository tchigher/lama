package co.ledger.lama.api.http.model

/**
 * @param recipients  for example: ''["13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ","2MvTqvxVezArZAGErYrSpPEgsqiFr7VmkPg"]''
 * @param feesPerByte  for example: ''100''
 * @param amount  for example: ''7000000''
 * @param utxoPickingStrategy 
 */
case class RawBitcoinTransactionBuilder (
  recipients: List[String],
  feesPerByte: Option[String],
  amount: String,
  utxoPickingStrategy: String
)

