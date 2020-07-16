package co.ledger.lama.api.repository

import cats.effect.{Blocker, IO}
import doobie.h2.H2Transactor
import co.ledger.template.IOAssertion
import co.ledger.template.TestUsers._

class UserRepositorySpec extends RepositorySpec {

  test("Find a user"){
    IOAssertion {
      H2Transactor.newH2Transactor[IO](dbUrl, dbUser, dbPass, ec, Blocker.liftExecutionContext(ec)).use { xa =>
        val repo = new PostgresUserRepository[IO](xa)
        for {
          user <- repo.findUser(users.head.username)
        } yield {
          user.fold(fail("User not found")) { u =>
            assert(u.username.value == users.head.username.value)
            assert(u.email.value == users.head.email.value)
          }
        }
      }
    }
  }

  test("NOT find a user"){
    IOAssertion {
      H2Transactor.newH2Transactor[IO](dbUrl, dbUser, dbPass, ec, Blocker.liftExecutionContext(ec)).use { xa =>
        val repo = new PostgresUserRepository[IO](xa)
        for {
          user <- repo.findUser(users.last.username)
        } yield {
          assert(user.isEmpty)
        }
      }
    }
  }

  test("find user query") {
    check(UserStatement.findUser(users.head.username))
  }
  test("add user query") {
    check(UserStatement.addUser(users.head))
  }
  test("update user query") {
    check(UserStatement.updateUser(users.head))
  }
  test("delete user query") {
    check(UserStatement.deleteUser(users.head.username))
  }

}
