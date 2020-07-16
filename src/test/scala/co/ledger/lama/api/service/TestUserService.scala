package co.ledger.lama.api.service

import cats.effect.IO
import cats.effect.concurrent.Ref
import co.ledger.template.{TestUsers, model}
import co.ledger.template.model.UserName
import co.ledger.template.repository.algebra.UserRepository

object TestUserService {

  val testRepo: IO[UserRepository[IO]] = Ref.of[IO, Map[model.UserName, model.User]](TestUsers.users.map(u => (u.username, u)).toMap).map{ state =>
    new UserRepository[IO] {
      override def findUser(username: UserName): IO[Option[model.User]] =
        state.get.map(_.get(username))

      override def addUser(user: model.User): IO[Unit] =
        state.update(l => l + (user.username -> user))

      override def updateUser(user: model.User): IO[Unit] =
        state.update(l => l.updated(user.username, user))

      override def deleteUser(username: UserName): IO[Unit] =
        state.update(m => m - username)
    }
  }

  val service: IO[UserService[IO]] = testRepo.map(new UserService[IO](_))

}
