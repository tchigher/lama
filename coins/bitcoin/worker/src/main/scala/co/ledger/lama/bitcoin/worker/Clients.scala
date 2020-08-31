package co.ledger.lama.bitcoin.worker

import java.util.concurrent.Executors

import cats.effect.{ConcurrentEffect, ContextShift, IO}
import co.ledger.lama.common.utils.RabbitUtils
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object Clients {

  def rabbit(conf: Fs2RabbitConfig)(implicit cs: ContextShift[IO]) =
    RabbitUtils.createClient(conf)

  def htt4s(implicit ce: ConcurrentEffect[IO]) =
    BlazeClientBuilder[IO](
      ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)
    ).resource

}
