package co.ledger.lama.api

import scala.util.Try

object model {

  case class Email(value: String) extends AnyVal
  case class UserName(value: String) extends AnyVal

  case class User(username: UserName, email: Email)

  // Business errors
  sealed trait ApiError extends Product with Serializable
  case class UserNotFound(username: UserName) extends ApiError
  case class UserAlreadyExist(username: UserName) extends ApiError
  case class OtherError(msg: String) extends ApiError

  // Http model
  case class CreateUser(username: String, email: String)
  case class UpdateUser(email: String)

  object Currency extends Enumeration {
    val BitcoinUnspecified, BitcoinTestnet3, BitcoinMainnet, BitcoinRegtest = Value

    def unapply(arg: String): Option[Value] = {
      Try(withName(arg)).toOption
    }
  }

}
