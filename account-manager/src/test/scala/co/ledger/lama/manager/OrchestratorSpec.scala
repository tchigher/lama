package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.common.models._
import co.ledger.lama.common.utils.IOAssertion
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
    val nbAccounts: Int            = 10
    val takeNbElements: Int        = 3
    val awakeEvery: FiniteDuration = 0.5.seconds
    val orchestrator               = new FakeOrchestrator(nbAccounts, awakeEvery)

    orchestrator.run(Some(takeNbElements)).compile.drain.map { _ =>
      orchestrator.tasks.foreach { t =>
        t.publishedEvents.keys should have size nbAccounts
        t.publishedEvents.values.foreach(_ should have size takeNbElements)
        t.reportedEvents should have size nbAccounts
        t.triggeredEvents should have size takeNbElements * nbAccounts
      }
    }
  }

}

class FakeOrchestrator(nbEvents: Int, override val awakeEvery: FiniteDuration)
    extends Orchestrator {

  val syncEvents: Seq[WorkableEvent] =
    (1 to nbEvents).map { i =>
      val accountIdentifier = AccountIdentifier(s"xpub-$i", CoinFamily.Bitcoin, Coin.Btc)
      WorkableEvent(
        accountId = UUID.randomUUID(),
        syncId = UUID.randomUUID(),
        status = Status.Registered,
        payload = SyncEvent.Payload(accountIdentifier)
      )
    }

  val tasks: List[FakeSyncEventTask] = List(new FakeSyncEventTask(syncEvents))

}

class FakeSyncEventTask(events: Seq[WorkableEvent]) extends SyncEventTask {

  var reportedEvents: mutable.Seq[SyncEvent] = mutable.Seq.empty

  var publishedEvents: mutable.Map[UUID, List[SyncEvent]] = mutable.Map.empty

  var triggeredEvents: mutable.Seq[SyncEvent] = mutable.Seq.empty

  def publishableEvents: Stream[IO, WorkableEvent] = Stream.emits(events)

  def publishEventsPipe: Pipe[IO, WorkableEvent, Unit] =
    _.evalMap(sp =>
      IO.pure(
        publishedEvents.update(
          sp.accountId,
          publishedEvents.getOrElse(sp.accountId, List.empty) :+ sp
        )
      )
    )

  def reportableEvents: Stream[IO, ReportableEvent] =
    Stream.emits(
      events.map(sp => ReportableEvent(sp.accountId, sp.syncId, Status.Synchronized, sp.payload))
    )

  def reportEventsPipe: Pipe[IO, ReportableEvent, Unit] =
    _.evalMap { e =>
      IO.pure { reportedEvents = reportedEvents :+ e }
    }

  def triggerableEvents: Stream[IO, TriggerableEvent] =
    Stream.emits(
      events.map(e => TriggerableEvent(e.accountId, e.syncId, Status.Synchronized, e.payload))
    )

  def triggerEventsPipe: Pipe[IO, TriggerableEvent, Unit] =
    _.evalMap { e =>
      IO.pure {
        triggeredEvents = triggeredEvents :+ e.nextWorkable
      }
    }
}
