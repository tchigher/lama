package co.ledger.lama.common.utils

import cats.effect.{Async, Resource, Timer}
import fs2.{Pure, Stream}

import scala.concurrent.duration.{FiniteDuration, _}

object ResourceUtils {

  def retriableResource[F[_], O](
      resource: Resource[F, O],
      policy: RetryPolicy = RetryPolicy.linear()
  )(implicit
      T: Timer[F],
      F: Async[F]
  ): Resource[F, O] =
    Stream
      .resource(resource)
      .attempts(policy)
      .evalTap {
        case Left(value) => F.delay(println(s"Resource acquisition Failed : $value"))
        case Right(_)    => F.unit
      }
      .collectFirst {
        case Right(res) => res
      }
      .compile
      .resource
      .lastOrError

  type RetryPolicy = Stream[Pure, FiniteDuration]

  object RetryPolicy {
    def linear(delay: FiniteDuration = 1.second, maxRetry: Int = 20): RetryPolicy =
      Stream.emit(delay).repeatN(maxRetry)
    def exponential(
        initial: FiniteDuration = 50.millisecond,
        factor: Long = 2,
        maxElapsedTime: FiniteDuration = 2.minute
    ): RetryPolicy = Stream.iterate(initial)(_ * factor).takeWhile(_ < maxElapsedTime)
  }
}
