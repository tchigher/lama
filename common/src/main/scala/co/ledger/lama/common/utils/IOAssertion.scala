package co.ledger.lama.common.utils

import cats.effect.IO

object IOAssertion {
  def apply[A](ioa: IO[A]): A = ioa.unsafeRunSync()
}
