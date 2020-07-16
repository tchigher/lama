package co.ledger.lama.api

import co.ledger.lama.api.model.CreateUser
import co.ledger.lama.api.validation.UserValidation
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class UserValidationSpec extends AnyFunSuite with UserValidationFixture {

  forAll(invalidExamples) { createUser =>
    test(s"invalid user $createUser") {
      assert(UserValidation.validateCreateUser(createUser).isInvalid)
    }
  }

  forAll(validExamples) { createUser =>
    test(s"valid user $createUser") {
      assert(UserValidation.validateCreateUser(createUser).isValid)
    }
  }

}

trait UserValidationFixture extends ScalaCheckPropertyChecks {

  val invalidExamples = Table(
    "createUser",
    CreateUser("", ""),
    CreateUser("", "gvolpe@github.com"),
    CreateUser("gvolpe", ""),
    CreateUser("gvolpe", "g volpe@github.com")
  )

  val validExamples = Table(
    "createUser",
    CreateUser("gvolpe", "gvolpe@github.com"),
    CreateUser("asd", "asd@gmail.com"),
    CreateUser("msaib", "msabin@typelevel.org")
  )

}

