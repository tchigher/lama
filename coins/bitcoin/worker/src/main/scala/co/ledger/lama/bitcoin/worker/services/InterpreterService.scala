package co.ledger.lama.bitcoin.worker.services

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.models.Keychain.Address
import co.ledger.lama.bitcoin.worker.models.explorer.{BlockHeight, Transaction}

import scala.collection.mutable

// TODO: impl when interpreter rpc service will be available.
trait InterpreterService {

  def saveTransactions(address: Address, txs: List[Transaction]): IO[Unit]

  def removeTransactions(address: Address, blockHeightCursor: Option[BlockHeight]): IO[Unit]

}

class InterpreterServiceMock extends InterpreterService {

  var savedTransactions: mutable.Map[Address, List[Transaction]] = mutable.Map.empty

  def saveTransactions(address: Address, txs: List[Transaction]): IO[Unit] =
    IO.delay {
      savedTransactions.update(
        address,
        savedTransactions.getOrElse(address, List.empty) ++ txs
      )
    }

  def removeTransactions(address: Address, blockHeightCursor: Option[BlockHeight]): IO[Unit] =
    blockHeightCursor match {
      case Some(blockHeight) =>
        IO.delay {
          savedTransactions.update(
            address,
            savedTransactions.getOrElse(address, List.empty).filter(_.block.height < blockHeight)
          )
        }
      case None => IO.delay(savedTransactions.remove(address))
    }

}
