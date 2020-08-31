package co.ledger.lama.manager

import java.util.UUID
import java.util.concurrent.TimeUnit

import doobie.util.Read
import doobie.postgres.implicits._

import scala.concurrent.duration.FiniteDuration

package object models {

  case class AccountInfo(id: UUID, syncFrequency: FiniteDuration)

  object AccountInfo {
    implicit val doobieRead: Read[AccountInfo] =
      Read[(UUID, Long)].map {
        case (accountId, syncFrequencyInSeconds) =>
          AccountInfo(
            accountId,
            FiniteDuration(syncFrequencyInSeconds, TimeUnit.SECONDS)
          )
      }
  }

}
