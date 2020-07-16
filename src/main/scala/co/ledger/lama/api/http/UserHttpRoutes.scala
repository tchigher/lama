package co.ledger.lama.api.http

import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import co.ledger.lama.api.model._
import co.ledger.lama.api.service.UserService
import co.ledger.lama.api.validation.UserValidation
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class UserHttpRoutes[F[_]: Sync](userService: UserService[F])(
    implicit H: HttpErrorHandler[F]
) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    // Find all users
    case GET -> Root =>
      for {
        users <- userService.findAll
        response <- users.fold(H.handle, x => Ok(x.asJson))
      } yield response

    // Find user by username
    case GET -> Root / username =>
      for {
        user <- userService.findUser(UserName(username))
        response <- user.fold(H.handle, x => Ok(x.asJson))
      } yield response

    // Create a user
    case req @ POST -> Root =>
      req.decode[CreateUser] { createUser =>
        UserValidation
          .validateCreateUser(createUser)
          .fold(
            errors => BadRequest(errors.toList.asJson),
            user =>
              userService.addUser(user) flatMap { either =>
                either.fold(H.handle, _ => Created())
              }
          )
      }

    // Update a user
    case req @ PUT -> Root / username =>
      req.decode[UpdateUser] { updateUser =>
        UserValidation
          .validateUpdateUser(updateUser)
          .fold(
            errors => BadRequest(errors.toList.asJson),
            email =>
              userService.updateUser(User(UserName(username), email)) flatMap {
                either =>
                  either.fold(H.handle, _ => Ok())
              }
          )
      }

    // Delete a user
    case DELETE -> Root / username =>
      for {
        result <- userService.deleteUser(UserName(username))
        response <- result.fold(H.handle, _ => NoContent())
      } yield response

  }

}
