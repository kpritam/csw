package csw.logging.client.scaladsl
import java.net.InetAddress

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.{Config, ConfigFactory}
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

  private val defaultCapacity = 10
  private val zeroCapacity    = 0
  private val fourCapacity    = 4

  def configWithCapacity(capacity: Int): Config =
    ConfigFactory
      .parseString(s"""bounded-mailbox {
        |  mailbox-type = "akka.dispatch.BoundedMailbox"
        |  mailbox-capacity = $capacity
        |  mailbox-push-timeout-time = 0
        |}""".stripMargin)
      .withFallback(ConfigFactory.load("application.conf"))

  val logBuffer: mutable.Buffer[JsObject] = mutable.Buffer.empty[JsObject]
  val testAppender                        = new TestAppender(x ⇒ logBuffer += Json.parse(x.toString).as[JsObject])

  val hostName: String = InetAddress.getLocalHost.getHostName

  override def afterEach(): Unit = logBuffer.clear()

  def sendMessagesToActor(irisActorRef: ActorRef): Unit = {
    irisActorRef ! LogTrace
    irisActorRef ! LogDebug
    irisActorRef ! LogInfo
    irisActorRef ! LogWarn
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
    val actorSystem        = ActorSystem("test", configWithCapacity(capacity = defaultCapacity))
    lazy val loggingSystem = new LoggingSystem("logging", "version", hostName, actorSystem)

    loggingSystem.setAppenders(List(testAppender))

    val irisActorRef: ActorRef = actorSystem.actorOf(IRIS.props(IRIS.COMPONENT_NAME), name = "IRIS-Supervisor-Actor")

    sendMessagesToActor(irisActorRef)

    logBuffer.size shouldEqual 8

    Await.result(actorSystem.terminate(), 5.seconds)
  }

  test("should get no messages if mailbox capacity is zero") {
    val actorSystem        = ActorSystem("test", configWithCapacity(capacity = zeroCapacity))
    lazy val loggingSystem = new LoggingSystem("logging", "version", hostName, actorSystem)

    loggingSystem.setAppenders(List(testAppender))

    val irisActorRef: ActorRef = actorSystem.actorOf(IRIS.props(IRIS.COMPONENT_NAME), name = "IRIS-Supervisor-Actor")

    sendMessagesToActor(irisActorRef)

    logBuffer.size shouldEqual 0

    Await.result(actorSystem.terminate(), 5.seconds)
  }

  test("should get limited messages if msg count is beyond the capacity defined for mailbox") {
    val actorSystem        = ActorSystem("test", configWithCapacity(capacity = fourCapacity))
    lazy val loggingSystem = new LoggingSystem("logging", "version", hostName, actorSystem)

    loggingSystem.setAppenders(List(testAppender))

    val irisActorRef: ActorRef = actorSystem.actorOf(IRIS.props(IRIS.COMPONENT_NAME), name = "IRIS-Supervisor-Actor")

    sendMessagesToActor(irisActorRef)

    logBuffer.size shouldEqual 5

    Await.result(actorSystem.terminate(), 5.seconds)
  }

}
