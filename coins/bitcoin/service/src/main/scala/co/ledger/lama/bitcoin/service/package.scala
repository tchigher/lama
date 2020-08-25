package co.ledger.lama.bitcoin

import cats.data.ReaderT
import cats.effect.Resource
import co.ledger.lama.bitcoin.service.config.Config

package object service {

  type Configured[F[_], T] = ReaderT[F, Config, T]
  // '?' is brought by type projector compiler plugin
  type ConfiguredResource[F[_], T] = Configured[Resource[F, ?], T]

}
