package co.ledger.lama
import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.model.{SyncEvent, SyncPayload}
import fs2.{Pipe, Stream}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class OrchestratorSpec extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  it should "succeed" in IOAssertion {
    val nbAccounts: Int                    = 10
    val awakeEveryInterval: FiniteDuration = 0.5.seconds
    val takeNbElements: Int                = 3
    val orchestrator                       = new FakeOrchestrator(nbAccounts, awakeEveryInterval)

    orchestrator.run(Some(takeNbElements)).compile.drain.map { _ =>
      orchestrator.updaters.foreach { u =>
        u.sentSyncPayloadsByAccountId.keys should have size nbAccounts
        u.sentSyncPayloadsByAccountId.values.foreach(_ should have size takeNbElements)
      }
      orchestrator.insertedEvents should have size nbAccounts
    }
  }

}

class FakeOrchestrator(nbEvents: Int, awakeEveryInterval: FiniteDuration)(implicit
    t: Timer[IO]
) extends Orchestrator {

  var insertedEvents: mutable.Seq[SyncEvent] = mutable.Seq.empty

  val syncPayloads: Seq[SyncPayload] = Fixtures.generateSyncPayloads(nbEvents)

  val updaters: List[FakeUpdater] = List(new FakeUpdater(syncPayloads, awakeEveryInterval))

  def inserter(se: SyncEvent): IO[Unit] =
    IO.pure { insertedEvents = insertedEvents :+ se }

  def syncEventSource: Stream[IO, SyncEvent] =
    Stream.emits(
      syncPayloads.map(sp =>
        SyncEvent(sp.accountId, sp.syncId, SyncEvent.Status.Synchronized, sp.blockHeight)
      )
    )

}

class FakeUpdater(events: Seq[SyncPayload], val awakeEveryInterval: FiniteDuration)
    extends Updater {

  var sentSyncPayloadsByAccountId: mutable.Map[UUID, List[SyncPayload]] = mutable.Map.empty

  def syncEventCandidates: Stream[IO, SyncPayload] = Stream.emits(events)

  def syncPayloadSink: Pipe[IO, SyncPayload, Unit] =
    _.evalMap(sp =>
      IO.pure(
        sentSyncPayloadsByAccountId.update(
          sp.accountId,
          sentSyncPayloadsByAccountId.getOrElse(sp.accountId, List.empty) :+ sp
        )
      )
    )

}
