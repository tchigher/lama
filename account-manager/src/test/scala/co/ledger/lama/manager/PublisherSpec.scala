package co.ledger.lama.manager
import java.util.UUID

import cats.effect.IO
import com.redis.RedisClient
import fs2.Stream
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import redis.embedded.RedisServer

import scala.collection.mutable

class PublisherSpec extends AnyFlatSpecLike with Matchers with Inspectors with BeforeAndAfterAll {

  val redisServer              = new RedisServer(6380)
  val redisClient: RedisClient = new RedisClient("localhost", 6380)

  val nbEvents: Int = 10

  def publishers: Seq[TestPublisher] =
    (1 to 5).map(i => new TestPublisher(redisClient, nbEvents, i))

  forAll(publishers) { publisher =>
    val maxOnGoingEvents   = publisher.maxOnGoingEvents
    val events             = publisher.events
    val countPendingEvents = nbEvents - maxOnGoingEvents

    it should s" have $maxOnGoingEvents published events and $countPendingEvents pending events" in IOAssertion {
      Stream
        .emits(events)
        .evalMap(publisher.enqueue)
        .compile
        .drain
        .map { _ =>
          publisher.countPendingEvents shouldBe Some(countPendingEvents)
          publisher.publishedEvents should have size maxOnGoingEvents
          assert(publisher.publishedEvents.containsSlice(publisher.events.take(maxOnGoingEvents)))
        }
    }
  }

  forAll(publishers) { publisher =>
    val maxOnGoingEvents = publisher.maxOnGoingEvents
    val events           = publisher.events

    it should s"publish events $maxOnGoingEvents by $maxOnGoingEvents" in IOAssertion {
      Stream
        .emits(events)
        .evalMap(publisher.enqueue)
        .zipWithIndex
        .evalMap {
          case (_, index) =>
            val publishedEvents   = publisher.publishedEvents
            val pendingEventsSize = publisher.countPendingEvents

            // at each iteration, call dequeue to publish next event
            publisher.dequeue(publisher.key).map { _ =>
              (index, pendingEventsSize, publishedEvents)
            }
        }
        .map {
          case (index, pendingEventsSize, publishedEvents) =>
            pendingEventsSize shouldBe Some(0)
            publishedEvents shouldBe publisher.events.take(index.toInt + 1)
        }
        .compile
        .drain
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    redisServer.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    redisServer.stop()
  }

}

case class TestEvent(accountId: UUID, eventId: String) extends WithRedisKey(accountId)

object TestEvent {

  implicit val decoder: Decoder[TestEvent] = deriveDecoder[TestEvent]
  implicit val encoder: Encoder[TestEvent] = deriveEncoder[TestEvent]

}

class TestPublisher(
    val redis: RedisClient,
    val nbEvents: Int,
    override val maxOnGoingEvents: Int
)(implicit
    val enc: Encoder[TestEvent],
    val dec: Decoder[TestEvent]
) extends Publisher[UUID, TestEvent] {
  import Publisher._

  val key: UUID = UUID.randomUUID()

  val events: Seq[TestEvent] = (1 to nbEvents).map(i => TestEvent(key, s"event$i"))

  var publishedEvents: mutable.Seq[TestEvent] = mutable.Seq.empty

  def publish(event: TestEvent): IO[Unit] =
    IO.pure {
      publishedEvents = publishedEvents ++ Seq(event)
    }

  def countPendingEvents: Option[Long] = redis.llen(pendingEventsKey(key))

}
