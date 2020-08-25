package co.ledger.lama.bitcoin.service.http.model

import java.util.UUID

/**
  * @param accountId
  * @param currency
  * @param extendedPublicKey  for example: ''zpubDDTG...FeF6mSjZ''
  * @param keychain  for example: ''BIP84''
  * @param chain
  */
case class AccountView(
    accountId: UUID,
    currency: String,
    extendedPublicKey: String,
    keychain: String,
    chain: String
)
