package co.ledger.lama.api

import co.ledger.lama.api.model.User
import co.ledger.template.model._

package object repository {

  type UserDTO = (Int, String, String)

  implicit class UserConversions(dto: UserDTO) {
    def toUser: User = User(
      username = UserName(dto._2),
      email = Email(dto._3)
    )
  }

}
