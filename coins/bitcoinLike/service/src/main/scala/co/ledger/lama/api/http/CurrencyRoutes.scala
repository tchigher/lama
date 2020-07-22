package co.ledger.lama.api.http

import cats.implicits._
import cats.effect.Sync
import co.ledger.lama.api.http.CurrencyRoutes.Address
import co.ledger.lama.api.http.model.AddressValidationResponse
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import co.ledger.lama.api.model.Currency
import co.ledger.lama.api.service.CurrencyService
import org.http4s.circe.CirceEntityCodec._

class CurrencyRoutes[F[_]: Sync](currencyService: CurrencyService[F]) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / Currency(currency) / "validate" :? Address(address) =>
      currencyService
        .validateAddress(currency, address)
        .map(AddressValidationResponse(_))
        .flatMap(Ok(_))
    case GET -> Root / Currency(currency) / "fees" => ???
    case POST -> Root / Currency(currency) / "extended-public-key" => ???
  }

}

object CurrencyRoutes {
  object Address extends QueryParamDecoderMatcher[String]("address")
}
