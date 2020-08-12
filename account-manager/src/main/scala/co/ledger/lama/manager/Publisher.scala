package co.ledger.lama.manager

import cats.effect.IO
import co.ledger.lama.manager.Exceptions.RedisUnexpectedException
import co.ledger.lama.manager.utils.RabbitUtils
import com.redis.RedisClient
import com.redis.serialization.{Format, Parse}
import com.redis.serialization.Parse.Implicits._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, RoutingKey}
import fs2.{Pipe, Stream}
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax._

import scala.annotation.nowarn

abstract class WithRedisKey[K](val key: K)

/**
  * Publisher publishing events sequentially.
  * Redis is used as a FIFO queue to guarantee the sequence.
  */
trait Publisher[K, V <: WithRedisKey[K]] {

  // max concurrent ongoing events
  val maxOnGoingEvents: Int = 1

  // stored keys
  def pendingEventsKey(key: K): String = s"pending_events_$key"
  def onGoingEventsKey(key: K): String = s"on_going_events_$key"

  // redis client
  def redis: RedisClient

  // the inner publish function
  def publish(event: V): IO[Unit]

  // implicits for serializing data as json and storing it as binary in redis
  implicit val dec: Decoder[V]
  implicit val enc: Encoder[V]

  implicit val parse: Parse[V] =
    Parse { bytes =>
      decode[V](new String(bytes)) match {
        case Right(v) => v
        case Left(e)  => throw e
      }
    }

  @nowarn
  implicit val fmt: Format =
    Format {
      case v: V => v.asJson.noSpaces.getBytes()
    }

  // If the counter of ongoing events for the key has reached max ongoing events, add the event to the pending list.
  // Otherwise, publish and increment the counter of ongoing events.
  def enqueue: Pipe[IO, V, Unit] =
    _.evalMap { e =>
      hasMaxOnGoingEvents(e.key).flatMap {
        case true =>
          // enqueue pending events in redis
          rpushPendingEvents(e)
        case false =>
          // publish and increment the counter of ongoing events
          publish(e)
            .flatMap(_ => incrOnGoingEvents(e.key))
      }.void
    }

  // Remove the top pending event of a key and take the next pending event.
  // If next pending event exists, publish it.
  def dequeue(key: K): IO[Unit] =
    for {
      countOnGoingEvents <- decrOnGoingEvents(key)
      nextEvent <-
        if (countOnGoingEvents < maxOnGoingEvents)
          lpopPendingEvents(key)
        else IO.pure(None)
      result <- nextEvent match {
        case Some(next) => publish(next)
        case None       => IO.unit
      }
    } yield result

  // check if the counter of ongoing events has reached the max.
  private def hasMaxOnGoingEvents(key: K): IO[Boolean] =
    IO(redis.get[Int](onGoingEventsKey(key)).exists(_ >= maxOnGoingEvents))

  // https://redis.io/commands/incr
  // Increment the counter of ongoing events for a key and return the value after.
  private def incrOnGoingEvents(key: K): IO[Long] =
    IO.fromOption(redis.incr(onGoingEventsKey(key)))(RedisUnexpectedException)

  /// https://redis.io/commands/decr
  // Decrement the counter of ongoing events for a key and return the value after.
  private def decrOnGoingEvents(key: K): IO[Long] =
    IO.fromOption(redis.decr(onGoingEventsKey(key)))(RedisUnexpectedException)

  // https://redis.io/commands/rpush
  // Add an event at the last and return the length after.
  private def rpushPendingEvents(event: V): IO[Long] =
    IO.fromOption(redis.rpush(pendingEventsKey(event.key), event))(RedisUnexpectedException)

  // https://redis.io/commands/lpop
  // Remove the first from a key and if exists, return the next one.
  private def lpopPendingEvents(key: K): IO[Option[V]] =
    IO(redis.lpop[V](pendingEventsKey(key)))

}

class RabbitPublisher[K, V <: WithRedisKey[K]](
    val redis: RedisClient,
    rabbit: RabbitClient[IO],
    exchangeName: ExchangeName,
    routingKey: RoutingKey
)(implicit val enc: Encoder[V], val dec: Decoder[V])
    extends Publisher[K, V] {

  def publish(event: V): IO[Unit] =
    publisher.evalMap(p => p(event)).compile.drain

  private val publisher: Stream[IO, V => IO[Unit]] =
    RabbitUtils.createPublisher[V](
      rabbit,
      exchangeName,
      routingKey
    )

}
