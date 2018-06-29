package csw.services.command.internal

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import csw.messages.{CommandResponseManagerMessage, SupervisorMessage}
import csw.messages.CommandResponseManagerMessage
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.logging.scaladsl.LoggerFactory

/**
 * The factory for creating [[csw.services.command.internal.CommandResponseManagerBehavior]]
 */
private[csw] class CommandResponseManagerFactory {

  def make(
      ctx: ActorContext[SupervisorMessage],
      commandResponseManagerActor: ActorRef[CommandResponseManagerMessage]
  ): CommandResponseManager = new CommandResponseManager(commandResponseManagerActor)(ctx.system)

  def makeBehavior(loggerFactory: LoggerFactory): Behavior[CommandResponseManagerMessage] =
    Behaviors.setup[CommandResponseManagerMessage](ctx ⇒ new CommandResponseManagerBehavior(ctx, loggerFactory))

}
