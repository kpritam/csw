package csw.logging.client.scaladsl
import java.net.InetAddress

import akka.actor.typed
import akka.actor.typed.{ActorRef, SpawnProtocol}
import com.typesafe.config.{Config, ConfigFactory}
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.logging.client.components.IRIS
import csw.logging.client.components.IRIS._
import csw.logging.client.internal.LoggingSystem
import csw.logging.client.utils.TestAppender
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

class MailboxRequirementTest extends FunSuite with Matchers with BeforeAndAfterEach {

  private val defaultCapacity = 8
  private val oneCapacity     = 1

  def configWithCapacity(capacity: Int): Config =
    ConfigFactory
      .parseString(s"""bounded-mailbox.mailbox-capacity = $capacity""")
      .withFallback(ConfigFactory.load("application.conf"))

  val logBuffer: mutable.Buffer[JsObject] = mutable.Buffer.empty[JsObject]
  val testAppender                        = new TestAppender(x ⇒ logBuffer += Json.parse(x.toString).as[JsObject])

  val hostName: String = InetAddress.getLocalHost.getHostName

  override def afterEach(): Unit = logBuffer.clear()

  def sendMessagesToActor(irisActorRef: ActorRef[IRISLogMessages]): Unit = {
    irisActorRef ! LogError
    irisActorRef ! LogError
    irisActorRef ! LogError
    irisActorRef ! LogError
    irisActorRef ! LogFatal
    irisActorRef ! LogFatal
    irisActorRef ! LogFatal
    irisActorRef ! LogFatal
    Thread.sleep(600)
  }

  test("should get all messages if msg count is under the capacity defined for mailbox") {
    val actorSystem        = typed.ActorSystem(SpawnProtocol.behavior, "test", configWithCapacity(capacity = defaultCapacity))
    lazy val loggingSystem = new LoggingSystem("logging", "version", hostName, actorSystem)

    loggingSystem.setAppenders(List(testAppender))

    val irisActorRef: ActorRef[IRISLogMessages] =
      actorSystem.spawn(IRIS.behavior(IRIS.COMPONENT_NAME), name = "IRIS-Supervisor-Actor")

    sendMessagesToActor(irisActorRef)

    logBuffer.size shouldEqual 8

    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 5.seconds)
  }

  // This shows that log actor is configured with the given capacity for Mailbox and it's a bounded Mailbox
  test("should get less than eight messages if mailbox capacity is one") {
    val actorSystem        = typed.ActorSystem(SpawnProtocol.behavior, "test", configWithCapacity(capacity = oneCapacity))
    lazy val loggingSystem = new LoggingSystem("logging", "version", hostName, actorSystem)

    loggingSystem.setAppenders(List(testAppender))

    val irisActorRef: ActorRef[IRISLogMessages] =
      actorSystem.spawn(IRIS.behavior(IRIS.COMPONENT_NAME), name = "IRIS-Supervisor-Actor")

    sendMessagesToActor(irisActorRef)

    logBuffer.foreach(println)
    logBuffer.size < 8 shouldBe true

    actorSystem.terminate
    Await.result(actorSystem.whenTerminated, 5.seconds)
  }
}
