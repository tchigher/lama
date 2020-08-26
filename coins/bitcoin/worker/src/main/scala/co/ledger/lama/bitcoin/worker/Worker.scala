package co.ledger.lama.bitcoin.worker

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.services._
import fs2.Stream

class Worker(
    rabbit: SyncEventService,
    keychain: KeychainService,
    explorer: ExplorerService,
    interpreter: InterpreterService
) {

  def run: Stream[IO, Unit] = Stream.emit(println("run"))

}
