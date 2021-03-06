package co.ledger.lama.bitcoin.service.http.model

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * @param isValid
  */
case class AddressValidationResponse(
    isValid: Boolean
)

object AddressValidationResponse {
  implicit val encoder: Encoder[AddressValidationResponse] = deriveEncoder
}
