package csw.services.event.perf

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ThrottleMode
import akka.stream.scaladsl.{Keep, Source}
import csw.messages.events.{EventName, SystemEvent}
import csw.services.event.perf.EventUtils._
import csw.services.event.scaladsl.EventPublisher

import scala.concurrent.{ExecutionContextExecutor, Future}

class Publisher(testSettings: TestSettings, testConfigs: TestConfigs, id: Int)(implicit val system: ActorSystem) {
  import testSettings._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val wiring = new TestWiring(system)
  import testConfigs._
  private val payload: Array[Byte]      = ("0" * payloadSize).getBytes("utf-8")
  private val publisher: EventPublisher = wiring.publisher

  private def source(eventName: EventName): Source[SystemEvent, Future[Done]] =
    Source(1L to totalMessages + warmupCount)
      .throttle(throttlingElements, throttlingDuration, throttlingElements, ThrottleMode.shaping)
      .map { id ⇒
        event(eventName, id, payload)
      }
      .watchTermination()(Keep.right)

  def startPublishing(): Future[Done] =
    for {
      _   ← publisher.publish(source(EventName(s"$testEventS-$id")))
      end ← publisher.publish(event(EventName(s"$endEventS-$id")))
    } yield end

}
