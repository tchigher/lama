package co.ledger.lama.bitcoin.service.http.model

/**
  * @param address  for example: ''19rpjEgDaPUwkeyuD7JHKUkTyxFHAmnorm''
  * @param addressDerivationPath  for example: ''0/21''
  */
case class DerivedAddress(
    address: String,
    addressDerivationPath: String
)
