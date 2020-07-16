package co.ledger.lama.api.repository

import cats.effect.Async
import cats.syntax.applicativeError._
import cats.syntax.functor._
import co.ledger.lama.api.model.{User, UserName}
import co.ledger.template.model
import co.ledger.template.model._
import co.ledger.template.repository.algebra.UserRepository
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.util.query.Query0
import doobie.util.transactor.Transactor
import doobie.util.update.Update0

// It requires a created database `users` with db user `postgres` and password `postgres`. See `users.sql` file in resources.
class PostgresUserRepository[F[_]: Async](xa: Transactor[F])
    extends UserRepository[F] {

  override def findUser(username: UserName): F[Option[User]] = {
    val statement: ConnectionIO[UserDTO] =
      UserStatement.findUser(username).unique

    // You might have more than one query involving joins. In such case a for-comprehension would be better
    val program: ConnectionIO[User] = statement.map(_.toUser)

    program.map(Option.apply).transact(xa).recoverWith {
      case UnexpectedEnd =>
        Async[F].delay(None) // In case the user is not unique in your db. Check out Doobie's docs.
    }
  }

  override def addUser(user: User): F[Unit] = {
    UserStatement.addUser(user).run.transact(xa).void
  }
  override def deleteUser(username: UserName): F[Unit] = {
    UserStatement.deleteUser(username).run.transact(xa).void
  }
  override def updateUser(user: User): F[Unit] = {
    UserStatement.updateUser(user).run.transact(xa).void
  }

}

object UserStatement {

  def findUser(username: UserName): Query0[UserDTO] = {
    sql"SELECT * FROM api_user WHERE username=${username.value}"
      .query[UserDTO]
  }

  def addUser(user: User): Update0 = {
    sql"INSERT INTO api_user (username, email) values (${user.username.value}, ${user.email.value})".update
  }

  def updateUser(user: User): Update0 = {
    sql"UPDATE api_user SET email = ${user.email.value} where username = ${user.username.value}".update
  }

  def deleteUser(username: UserName): Update0 = {
    sql"DELETE FROM api_user WHERE username = ${username.value}".update
  }

}
