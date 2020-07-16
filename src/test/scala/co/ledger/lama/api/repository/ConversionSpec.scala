package co.ledger.lama.api.repository

import co.ledger.lama.api.model.{Email, User, UserName}
import org.scalacheck._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ConversionSpec extends UserArbitraries with AnyFlatSpecLike with Matchers {

  forAll { dto: UserDTO =>
    it should s"convert a dto: $dto into an User" in {
      dto.toUser should be(User(UserName(dto._2), Email(dto._3)))
    }
  }

}

trait UserArbitraries extends ScalaCheckPropertyChecks {

  implicit val userDtoArbitraries: Arbitrary[UserDTO] = Arbitrary[UserDTO] {
    for {
      i <- Gen.posNum[Int]
      u <- Gen.alphaStr
      e <- Gen.alphaStr
    } yield (i, u, e)
  }

}
