package co.ledger.lama.api.service

import cats.data.EitherT
import cats.effect.Async
import cats.syntax.applicativeError._
import cats.syntax.functor._
import co.ledger.lama.api.model.{ApiError, User, UserName}
import co.ledger.template.model._
import co.ledger.template.repository.algebra.UserRepository

class UserService[F[_]: Async](userRepo: UserRepository[F]) {

  def findUser(username: UserName): F[ApiError Either User] =
    userRepo.findUser(username) map { maybeUser =>
      maybeUser.toRight[ApiError](UserNotFound(username))
    }

  def findAll: F[ApiError Either List[User]] =
    EitherT.rightT(List.empty[User]).value

  def addUser(user: User): F[ApiError Either Unit] =
    userRepo
      .addUser(user)
      .attemptT
      .leftMap[ApiError](e => OtherError(e.getMessage))
      .value

  def updateUser(user: User): F[ApiError Either Unit] =
    userRepo
      .updateUser(user)
      .attemptT
      .leftMap[ApiError](e => OtherError(e.getMessage))
      .value

  def deleteUser(username: UserName): F[ApiError Either Unit] =
    userRepo
      .deleteUser(username)
      .attemptT
      .leftMap[ApiError](e => OtherError(e.getMessage))
      .value
}
