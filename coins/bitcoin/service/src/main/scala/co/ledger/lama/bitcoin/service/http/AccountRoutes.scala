package co.ledger.lama.bitcoin.service.http

import cats.effect.Sync
// import co.ledger.lama.bitcoin.service.http.AccountRoutes._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.{OptionalQueryParamDecoderMatcher, QueryParamDecoderMatcher}
import org.http4s.server.Router

class AccountRoutes[F[_]: Sync] extends Http4sDsl[F] {
  val routes: HttpRoutes[F] = Router("account" -> HttpRoutes.of {
//    case req @ POST -> Root =>
//      // req.as[CreateAccountRequest]
//      ???
//    case GET -> Root / id           => ???
//    case DELETE -> Root / id        => ???
//    case GET -> Root / id / "state" => ???
    case GET -> Root / "balances"   =>
      // {
      //  "<accountId1>": "7",
      //  "<accountId2>": "14",
      //  "<accountId3>": "21"
      // }
      ???
    // return a list of observable addresses
//    case GET -> Root / id / "addresses" :? From(from) :? To(to) => ???
//    case GET -> Root / id / "addresses" / "fresh"               => ???
//    case GET -> Root / id / "utxo"                              => ???
//    case req @ POST -> Root / id / "tx"                         => ???
//    case req @ POST -> Root / id / "tx" / "send"                => ???
//    case GET -> Root / id / "txs"                               => ???
//    case GET -> Root / id / "balances" / "history"
//        :? Start(start) :? End(end) :? TimeInterval(timeInterval) =>
//      ???
  })
}

object AccountRoutes {
  object Start        extends QueryParamDecoderMatcher[String]("start")
  object End          extends QueryParamDecoderMatcher[String]("end")
  object TimeInterval extends QueryParamDecoderMatcher[String]("timeInterval")
  object From         extends OptionalQueryParamDecoderMatcher[Int]("from")
  object To           extends OptionalQueryParamDecoderMatcher[Int]("to")
}
