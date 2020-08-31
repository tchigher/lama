package co.ledger.lama.bitcoin.worker.services

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.models.Keychain.Address
import fs2.Stream

import scala.collection.mutable

// TODO: impl when keychain rpc service will be available.
trait KeychainService {

  def create(extendedKey: String): IO[Unit]

  def getAddresses(extendedKey: String): Stream[IO, Address]

  def markAddressesAsUsed(extendedKey: String, addresses: List[Address]): IO[Unit]

}

class KeychainServiceMock extends KeychainService {

  var usedAddresses: mutable.Map[String, List[Address]] = mutable.Map.empty

  def create(extendedKey: String): IO[Unit] = IO.unit

  def getAddresses(extendedKey: String): Stream[IO, Address] =
    Stream.emits(
      List(
        "1MZbRqZGpiSWGRLg8DUdVrDKHwNe1oesUZ",
        "1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ",
        "1MfeDvj5AUBG4xVMrx1xPgmYdXQrzHtW5b",
        "1GgX4cGLiqF9p4Sd1XcPQhEAAhNDA4wLYS",
        "1Q2Bv9X4yCTNn1P1tmFuWpijHvT3xYt3F",
        "1G7g5zxfjWCSJRuNKVasVczrZNowQRwbij",
        "1MFjwXsibXbvVzkE4chJrhbczDivpbbVTE",
        "1HFzpigeFDZGp45peU4NAHLgyMxiGj1GzT",
        "17xsjFyLgbWrjauC8F5hyaaaWdf6L6Y6L4",
        "1Hc7EofusKsUrNPhbp1PUMkH6wfDohfDBd",
        "1Mj9jzHtAyVvM9Y274LCcfLBBBfwRiDK9V",
        "1Ng5FPQ1rUbEHak8Qcjy6BRJhjF1n3AVR6",
        "145Tdk8ntZQa5kgyLheL835z6yukHjbEKF",
        "16hG8pC6D4gRmRvfHT3zHGcED9FMocN4hG",
        "1NQd72r3kUESTAMvDjaJU1Gk842HPcPVQQ",
        "1JiBkCdhc3P4by29kLzraz4CuwjAvTA96H",
        "1MXLmPcLRoQAWZqfgxtvhvUWLDQ3We2sUJ",
        "1DRCwCw8HjeRsRi4wyfJzqgBeNBJTdvvx1",
        "1NTG6NWQq1DZYZf8VQ58FBGGDwA9deM7Aq",
        "1JMbu32pdVu6FvKbmrJMTSJSWFcJJ47JtY",
        "13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ",
        "19rpjEgDaPUwkeyuD7JHKUkTyxFHAmnorm",
        "1D2R9GQu541rmUKY5kz6gjWuX2kfEusRre",
        "1B3g4WxFBJtPh6azgQdRs5f7zwXhcocELc",
        "12AdRB44ctyTaQiLgthz7WMFJ7dFNornmA",
        "1KHyosZPVXxVBaQ7qtRjPUWWt911rAkfg6",
        "1KConohwqXnB87BYpp2n7GfrPRhPqa471a",
        "1BGCPcrzx3G48eY7vhpc7UEtJbpXW3mZ1t",
        "14er8aopUkpX4KcL9rx7GU2t8zbFANQyC3",
        "1LPR9mGFJrWkiMPj2HWfnBA5weEeKV2arY",
        "15M1GcHsakzQtxkVDcw92siMk3c3Ap3C5h",
        "1GWfouhfoTHctEeUCMd1tcF2cdkfuaSXdh",
        "1CyAcL6Kd5pWzFucQE2Ev527FEQ9dTtPJ1",
        "1AxhDoozM9VfsktCKVN7kp6UkaqVq65rHF",
        "1Aj3Gi1j5UsvZh4ccjaqdnogPMWy54Z5ii"
      )
    )

  def markAddressesAsUsed(extendedKey: String, addresses: List[Address]): IO[Unit] =
    IO.delay {
      usedAddresses.update(
        extendedKey,
        usedAddresses.getOrElse(extendedKey, List.empty) ++ addresses
      )
    }

}
