package co.ledger.lama.api.http

import cats.effect.IO
import co.ledger.template.IOAssertion
import co.ledger.template.TestUsers._
import co.ledger.template.http.ResponseBodyUtils._
import co.ledger.template.model.{CreateUser, UserName}
import co.ledger.template.service.TestUserService
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.{EntityEncoder, HttpRoutes, Method, Request, Status, Uri}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class UserHttpEndpointSpec extends UserHttpRoutesFixture with AnyFlatSpecLike with Matchers {

  implicit val errorHandler = new HttpErrorHandler[IO]

  val httpRoutes: IO[HttpRoutes[IO]] = TestUserService.service.map(new UserHttpRoutes[IO](_).routes)

  implicit def createUserEncoder: EntityEncoder[IO, CreateUser] = jsonEncoderOf[IO, CreateUser]

  forAll(examples) { (username, expectedStatus, expectedBody) =>
    it should s"find the user with username: ${username.value}" in IOAssertion {
      val request = Request[IO](uri = Uri(path = s"/${username.value}"))
      httpRoutes.flatMap(_.run(request).value).flatMap { task =>
        task.fold(IO(fail("Empty response")) *> IO.unit) { response =>
          IO(response.status        should be (expectedStatus)) *>
          IO(response.body.asString should be (expectedBody))
        }
      }
    }
  }

  it should "Create a user" in IOAssertion {
    val req = Request[IO](method = Method.POST).withEntity(CreateUser("root", "root@unix.org"))
    for {
      routes <- httpRoutes
      task  <- routes(req).value
      _     <- task.fold(IO(fail("Empty response")) *> IO.unit) {response =>
        IO(response.status should be (Status.Created))
      }
    } yield ()
  }

}

trait UserHttpRoutesFixture extends ScalaCheckPropertyChecks {

  private val user1 = users.head
  private val user2 = users.tail.head
  private val user3 = users.last

  val examples = Table(
    ("username", "expectedStatus", "expectedBody"),
    (user1.username, Status.Ok, s"""{"username":"${user1.username.value}","email":"${user1.email.value}"}"""),
    (user2.username, Status.Ok, s"""{"username":"${user2.username.value}","email":"${user2.email.value}"}"""),
    (user3.username, Status.Ok, s"""{"username":"${user3.username.value}","email":"${user3.email.value}"}"""),
    (UserName("xxx"), Status.NotFound, "User not found xxx")
  )
}
