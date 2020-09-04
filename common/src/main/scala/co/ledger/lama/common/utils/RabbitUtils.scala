package co.ledger.lama.common.utils

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import cats.data.Kleisli
import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import cats.implicits._
import co.ledger.lama.common.utils.ResourceUtils.{RetryPolicy, retriableResource}
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, deletion}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model._
import fs2.Stream
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object RabbitUtils {

  def createClient(
      conf: Fs2RabbitConfig
  )(implicit cs: ContextShift[IO]): Resource[IO, RabbitClient[IO]] =
    Resource
      .make(IO(Executors.newCachedThreadPool()))(es => IO(es.shutdown()))
      .map(Blocker.liftExecutorService)
      .evalMap(blocker => RabbitClient[IO](conf, blocker))

  def declareExchanges(
      R: RabbitClient[IO],
      exchanges: List[(ExchangeName, ExchangeType)]
  )(implicit t: Timer[IO]): IO[Unit] =
    retriableResource(R.createConnectionChannel, RetryPolicy.exponentialBackOff())
      .use { implicit channel =>
        exchanges
          .map {
            case (exchangeName, exchangeType) =>
              R.declareExchange(exchangeName, exchangeType)
          }
          .sequence
          .void
      }

  def declareBindings(
      R: RabbitClient[IO],
      bindings: List[(ExchangeName, RoutingKey, QueueName)]
  ): IO[Unit] =
    R.createConnectionChannel.use { implicit channel =>
      bindings
        .map {
          case (exchangeName, routingKey, queueName) =>
            R.declareQueue(DeclarationQueueConfig.default(queueName)) *>
              R.bindQueue(queueName, exchangeName, routingKey)
        }
        .sequence
        .void
    }

  def deleteBindings(R: RabbitClient[IO], queues: List[QueueName])(implicit
      cs: ContextShift[IO]
  ): IO[Unit] =
    Stream
      .resource(R.createConnectionChannel)
      .flatMap { implicit channel =>
        Stream
          .emits(queues)
          .map { queueName =>
            Stream.eval {
              R.deleteQueue(
                DeletionQueueConfig(
                  queueName,
                  deletion.Used,
                  deletion.NonEmpty
                )
              )
            }
          }
          .parJoinUnbounded
      }
      .compile
      .drain

  def deleteExchanges(R: RabbitClient[IO], exchanges: List[ExchangeName])(implicit
      cs: ContextShift[IO]
  ): IO[Unit] =
    Stream
      .resource(R.createConnectionChannel)
      .flatMap { implicit channel =>
        Stream
          .emits(exchanges)
          .map { exchangeName =>
            Stream.eval {
              R.deleteExchange(
                DeletionExchangeConfig(exchangeName, deletion.Used)
              )
            }
          }
          .parJoinUnbounded
      }
      .compile
      .drain

  def createAutoAckConsumer[A](
      R: RabbitClient[IO],
      queueName: QueueName
  )(implicit d: Decoder[A]): Stream[IO, A] =
    Stream
      .resource(R.createConnectionChannel)
      .evalMap { implicit channel =>
        R.createAutoAckConsumer[String](queueName)
      }
      .flatten
      .evalMap { message =>
        val parsed = parse(message.payload).flatMap(_.as[A])
        IO.fromEither(parsed)
      }

  def createPublisher[A](
      R: RabbitClient[IO],
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit e: Encoder[A]): Stream[IO, A => IO[Unit]] = {
    implicit val me: MessageEncoder[IO, A] =
      Kleisli[IO, A, AmqpMessage[Array[Byte]]] { s =>
        AmqpMessage(
          payload = s.asJson.noSpaces.getBytes(StandardCharsets.UTF_8),
          properties = AmqpProperties.empty
        ).pure[IO]
      }

    Stream
      .resource(R.createConnectionChannel)
      .evalMap { implicit channel =>
        R.createPublisher[A](
          exchangeName,
          routingKey
        )
      }
  }

}
