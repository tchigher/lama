package co.ledger.lama.bitcoin.service.http.model

/**
  * @param publicKey
  * @param chainCode
  * @param keychain
  * @param accountDerivationPath  for example: ''84'/1'/0'''
  * @param parentFingerprint  for example: ''18734cbe''
  */
case class ExtendedPublicKey(
    publicKey: Option[String],
    chainCode: Option[String],
    keychain: Option[String],
    accountDerivationPath: Option[String],
    parentFingerprint: Option[String]
)
