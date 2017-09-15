package csw.common.framework.internal.component

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior, PostStop, Signal}
import csw.common.ccs.CommandStatus
import csw.common.framework.models.CommandMessage.{Oneway, Submit}
import csw.common.framework.models.CommonMessage.UnderlyingHookFailed
import csw.common.framework.models.FromComponentLifecycleMessage.Running
import csw.common.framework.models.IdleMessage.Initialize
import csw.common.framework.models.RunningMessage.{DomainMessage, Lifecycle}
import csw.common.framework.models.ToComponentLifecycleMessage.{GoOffline, GoOnline}
import csw.common.framework.models.{RunningMessage, _}
import csw.common.framework.scaladsl.ComponentHandlers
import csw.services.logging.scaladsl.ComponentLogger

import scala.async.Async.{async, await}
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.Try

object ComponentBehavior {
  val shutdownTimeout: FiniteDuration = 10.seconds
}

class ComponentBehavior[Msg <: DomainMessage: ClassTag](
    ctx: ActorContext[ComponentMessage],
    supervisor: ActorRef[FromComponentLifecycleMessage],
    lifecycleHandlers: ComponentHandlers[Msg]
) extends ComponentLogger.TypedActor[ComponentMessage](ctx, lifecycleHandlers.componentName) {

  implicit val ec: ExecutionContext = ctx.executionContext

  var mode: ComponentMode = ComponentMode.Idle

  ctx.self ! Initialize

  def onMessage(msg: ComponentMessage): Behavior[ComponentMessage] = {
    log.debug(s"Component in mode :[$mode] received message :[$msg]")
    (mode, msg) match {
      case (_, msg: CommonMessage)                      ⇒ onCommon(msg)
      case (ComponentMode.Idle, msg: IdleMessage)       ⇒ onIdle(msg)
      case (ComponentMode.Running, msg: RunningMessage) ⇒ onRun(msg)
      case _                                            ⇒ log.error(s"Unexpected message :[$msg] received by component in mode :[$mode]")
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[ComponentMessage]] = {
    case PostStop ⇒
      log.warn(s"Component is shutting down")
      val shutdownResult = Try {
        Await.result(lifecycleHandlers.onShutdown(), ComponentBehavior.shutdownTimeout)
      }
      //log exception if onShutdown handler fails and proceed with `Shutdown` or `Restart`
      shutdownResult.failed.foreach(throwable ⇒ log.error(throwable.getMessage, ex = throwable))
      this
  }

  private def onCommon(msg: CommonMessage): Unit = {
    msg match {
      case UnderlyingHookFailed(exception) ⇒
        log.error(exception.getMessage, ex = exception)
        throw exception
    }
  }

  private def onIdle(x: IdleMessage): Unit = x match {
    case Initialize ⇒
      async {
        await(lifecycleHandlers.initialize())
        log.debug(s"Component is changing state from [$mode] to [${ComponentMode.Running}]")
        mode = ComponentMode.Running
        lifecycleHandlers.isOnline = true
        supervisor ! Running(ctx.self)
      }.failed.foreach(throwable ⇒ ctx.self ! UnderlyingHookFailed(throwable))
  }

  private def onRun(runningMessage: RunningMessage): Unit = runningMessage match {
    case Lifecycle(message) ⇒ onLifecycle(message)
    case x: Msg             ⇒ lifecycleHandlers.onDomainMsg(x)
    case x: CommandMessage  ⇒ onRunningCompCommandMessage(x)
    case msg                ⇒ log.error(s"Component cannot handle message :[$msg]")
  }

  private def onLifecycle(message: ToComponentLifecycleMessage): Unit = message match {
    case GoOnline ⇒
      if (!lifecycleHandlers.isOnline) {
        lifecycleHandlers.isOnline = true
        lifecycleHandlers.onGoOnline()
        log.debug(s"Component is Online")
      }
    case GoOffline ⇒
      if (lifecycleHandlers.isOnline) {
        lifecycleHandlers.isOnline = false
        lifecycleHandlers.onGoOffline()
        log.debug(s"Component is Offline")
      }
  }

  def onRunningCompCommandMessage(message: CommandMessage): Unit = {
    val newMessage: CommandMessage = message match {
      case x: Oneway ⇒ x.copy(replyTo = ctx.spawnAnonymous(Actor.ignore))
      case x: Submit ⇒ x
    }
    val validation              = lifecycleHandlers.onControlCommand(newMessage)
    val validationCommandResult = CommandStatus.validationAsCommandStatus(validation)
    message.replyTo ! validationCommandResult
  }

}
