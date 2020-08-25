package co.ledger.lama.bitcoin.worker

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.services._
import fs2.Stream

class Worker(
    rabbit: RabbitService,
    keychain: KeychainService,
    explorer: ExplorerService,
    interpreter: InterpreterService
) {

  def run: Stream[IO, Unit] = ???

}
