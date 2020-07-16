package co.ledger.lama.api

import co.ledger.template.model.{Email, User, UserName}

object TestUsers {

  val users = List(
    User(UserName("gvolpe"), Email("gvolpe@github.com")),
    User(UserName("tpolecat"), Email("tpolecat@github.com")),
    User(UserName("msabin"), Email("msabin@github.com"))
  )

}
