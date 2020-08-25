package co.ledger.lama.bitcoin.service.http.model

/**
  * @param currency
  * @param extendedPublicKey  for example: ''zpubDDTG...FeF6mSjZ''
  * @param keychain  for example: ''BIP84''
  * @param chain
  */
case class CreateAccountRequest(
    currency: String,
    extendedPublicKey: String,
    keychain: String,
    chain: String
)
