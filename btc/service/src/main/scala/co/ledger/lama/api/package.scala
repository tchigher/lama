package co.ledger.lama

import cats.data.ReaderT
import cats.effect.Resource
import co.ledger.lama.api.config.Config

package object api {

  type Configured[F[_], T] = ReaderT[F, Config, T]
  // '?' is brought by type projector compiler plugin
  type ConfiguredResource[F[_], T] = Configured[Resource[F, ?], T]

}
