package co.ledger.lama.manager

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.manager.models.SyncEvent.Status
import co.ledger.lama.manager.models.{SyncEvent, SyncPayload}
import fs2.{Pipe, Stream}
import io.circe.Json
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
        t.sentSyncPayloadsByAccountId.keys should have size nbAccounts
        t.sentSyncPayloadsByAccountId.values.foreach(_ should have size takeNbElements)
        t.reportedEvents should have size nbAccounts
      }
    }
  }

}

class FakeOrchestrator(nbEvents: Int, override val awakeEvery: FiniteDuration)
    extends Orchestrator {

  val syncPayloads: Seq[SyncPayload] =
    (1 to nbEvents).map { i =>
      SyncPayload(
        accountId = UUID.randomUUID(),
        syncId = UUID.randomUUID(),
        extendedKey = s"xpub-$i",
        status = Status.Registered,
        payload = Json.obj()
      )
    }

  val tasks: List[FakeTask] = List(new FakeTask(syncPayloads))

}

class FakeTask(events: Seq[SyncPayload]) extends EventTask {

  var reportedEvents: mutable.Seq[SyncEvent] = mutable.Seq.empty

  var sentSyncPayloadsByAccountId: mutable.Map[UUID, List[SyncPayload]] = mutable.Map.empty

  def candidateEventsSource: Stream[IO, SyncPayload] = Stream.emits(events)

  def candidateEventsPipe: Pipe[IO, SyncPayload, Unit] =
    _.evalMap(sp =>
      IO.pure(
        sentSyncPayloadsByAccountId.update(
          sp.accountId,
          sentSyncPayloadsByAccountId.getOrElse(sp.accountId, List.empty) :+ sp
        )
      )
    )

  def reportEventsSource: Stream[IO, SyncEvent] =
    Stream.emits(
      events.map(sp =>
        SyncEvent(sp.accountId, sp.syncId, SyncEvent.Status.Synchronized, sp.payload)
      )
    )

  def reportEvent(e: SyncEvent): IO[Unit] =
    IO.pure { reportedEvents = reportedEvents :+ e }
}
