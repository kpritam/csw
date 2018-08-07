package csw.command.internal

import akka.actor.typed.scaladsl.{ActorContext, MutableBehavior}
import akka.actor.typed.{ActorRef, Behavior}
import csw.command.messages.CommandResponseManagerMessage
import csw.command.messages.CommandResponseManagerMessage._
import csw.command.models.{CommandCorrelation, CommandResponseManagerState}
import csw.params.commands.CommandResponse.CommandNotAvailable
import csw.params.commands.CommandResultType.{Final, Intermediate}
import csw.params.commands.{CommandResponse, CommandResultType}
import csw.params.core.models.Id
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.messages.CommandResponseManagerMessage
import csw.messages.commands._
import csw.messages.params.models.Id
import csw.messages.CommandResponseManagerMessage._
import csw.messages.commands.Responses._
import csw.services.logging.scaladsl.{Logger, LoggerFactory}

/**
 * The Behavior of a Command Response Manager, represented as a mutable behavior. This behavior will be created as an actor.
 * There will be one CommandResponseManger for a given component which will provide an interface to interact with the status
 * and result of a submitted command.
 *
 * This class defines the behavior of CommandResponseManagerActor and is responsible for adding/updating/querying command result.
 * When component receives command of type Submit, then framework (ComponentBehavior - TLA) will add a entry of this command
 * with the [[csw.messages.commands.CommandResponse.SubmitResponse]] returned into the CommandResponseManager.
 *
 * In case of short running or immediate command,
 * submit response will be of type final result which can either be of type
 * [[csw.messages.commands.CommandResultType.Positive]] or [[csw.messages.commands.CommandResultType.Negative]]
 *
 * In case of long running command, validation response will be of type [[csw.messages.commands.CommandResultType.Intermediate]]
 * then it is the responsibility of component writer to update its final command status later on
 * with [[csw.messages.commands.CommandResponse.SubmitResponse]] which will either be
 * [[csw.messages.commands.CommandResultType.Positive]] or [[csw.messages.commands.CommandResultType.Negative]]
 *
 * CommandResponseManager also provides subscribe API.
 * One of the use case for this is when Assembly splits top level command into two sub commands and forwards them to two different HCD's.
 * In this case, Assembly can register its interest in the final [[csw.messages.commands.CommandResponse.SubmitResponse]]
 * from two HCD's when these sub commands completes, using subscribe API. And once Assembly receives final submit response
 * from both the HCD's then it can update Top level command with final [[csw.messages.commands.CommandResponse.SubmitResponse]]
 * In this case, Assembly can register its interest in the final [[csw.params.commands.CommandResponse]]
 * from two HCD's when these sub commands completes, using subscribe API. And once Assembly receives final command response
 * from both the HCD's then it can update Top level command with final [[csw.params.commands.CommandResponse]]
 *
 * @param ctx             The Actor Context under which the actor instance of this behavior is created
 * @param loggerFactory   The factory for creating [[csw.logging.scaladsl.Logger]] instance
 */
private[command] class CommandResponseManagerBehavior(
    ctx: ActorContext[CommandResponseManagerMessage],
    loggerFactory: LoggerFactory
) extends MutableBehavior[CommandResponseManagerMessage] {
  private val log: Logger = loggerFactory.getLogger(ctx)

  private[command] var commandResponseManagerState: CommandResponseManagerState = CommandResponseManagerState(Map.empty)
  private[command] var commandCoRelation: CommandCorrelation                    = CommandCorrelation(Map.empty, Map.empty)

  override def onMessage(msg: CommandResponseManagerMessage): Behavior[CommandResponseManagerMessage] = {
    msg match {
      case AddOrUpdateCommand(runId, cmdStatus)         ⇒ addOrUpdateCommand(runId, cmdStatus)
      case AddSubCommand(parentRunId, childRunId)       ⇒ commandCoRelation = commandCoRelation.add(parentRunId, childRunId)
      case UpdateSubCommand(subCommandRunId, cmdStatus) ⇒ updateSubCommand(subCommandRunId, cmdStatus)
      case Query(runId, replyTo)                        ⇒ replyTo ! commandResponseManagerState.get(runId)
      case Subscribe(runId, replyTo)                    ⇒ subscribe(runId, replyTo)
      case Unsubscribe(runId, subscriber) ⇒
        commandResponseManagerState = commandResponseManagerState.unSubscribe(runId, subscriber)
      case SubscriberTerminated(subscriber) ⇒
        commandResponseManagerState = commandResponseManagerState.removeSubscriber(subscriber)
      case GetCommandCorrelation(replyTo)          ⇒ replyTo ! commandCoRelation
      case GetCommandResponseManagerState(replyTo) ⇒ replyTo ! commandResponseManagerState
    }
    this
  }

  // This is where the command is initially added. Note that every Submit is added as "Started"/Intermediate
  private def addOrUpdateCommand(runId: Id, commandResponse: SubmitResponse): Unit =
    commandResponseManagerState.get(runId) match {
      case _: CommandNotAvailable ⇒ commandResponseManagerState = commandResponseManagerState.add(runId, commandResponse)
      case _                      ⇒ updateCommand(runId, commandResponse)
    }

  private def updateCommand(runId: Id, updateResponse: SubmitResponse): Unit = {
    val currentResponse: Response = commandResponseManagerState.get(runId)
    if (/*isCommandResultType(currentResponse) == CommandResultType.Intermediate && */ currentResponse != updateResponse) {
      commandResponseManagerState = commandResponseManagerState.updateCommandStatus(updateResponse)
      publishToSubscribers(updateResponse, commandResponseManagerState.cmdToCmdStatus(updateResponse.runId).subscribers)
    } else { println(s"Don't do the update for current: $currentResponse and update: $updateResponse") }
    /*
    currentResponse match {
      case Started(_) =>
        println("Yes I got here to Started publish")
        commandResponseManagerState = commandResponseManagerState.updateCommandStatus(updateResponse)
//        publishToSubscribers(updateResponse, commandResponseManagerState.cmdToCmdStatus(updateResponse.runId).subscribers)
        doPublish(updateResponse, commandResponseManagerState.cmdToCmdStatus(updateResponse.runId).subscribers)
    }
     */
    /*
    if (/*isCommandResultType(currentResponse) == CommandResultType.Intermediate && */ currentResponse != updateResponse) {
      commandResponseManagerState = commandResponseManagerState.updateCommandStatus(updateResponse)
      publishToSubscribers(updateResponse, commandResponseManagerState.cmdToCmdStatus(updateResponse.runId).subscribers)
    } else { println(s"Don't do the update for current: $currentResponse and update: $updateResponse")}
   */
  }

  private def updateSubCommand(subCommandRunId: Id, commandResponse: SubmitResponse): Unit = {
    // If the sub command has a parent command, fetch the current status of parent command from command status service
    commandCoRelation
      .getParent(commandResponse.runId)
      .foreach(parentId ⇒ updateParent(parentId, commandResponse))
  }

  private def updateParent(parentRunId: Id, childCommandResponse: SubmitResponse): Unit =
    (isCommandResultType(commandResponseManagerState.get(parentRunId)), isCommandResultType(childCommandResponse)) match {
      // If the child command receives a negative result, the result of the parent command need not wait for the
      // result from other sub commands
      case (CommandResultType.Intermediate, CommandResultType.Negative) ⇒
        updateCommand(parentRunId, CommandResponse.withRunId(parentRunId, childCommandResponse))
      // If the child command receives a positive result, the result of the parent command needs to be evaluated based
      // on the result from other sub commands
      case (CommandResultType.Intermediate, CommandResultType.Positive) ⇒
        updateParentForChild(parentRunId, childCommandResponse)
      case _ ⇒ log.debug("Parent Command is already updated with a Final response. Ignoring this update.")
    }

  private def updateParentForChild(parentRunId: Id, childCommandResponse: SubmitResponse): Unit =
    isCommandResultType(childCommandResponse) match {
      case _: CommandResultType.Final ⇒
        commandCoRelation = commandCoRelation.remove(parentRunId, childCommandResponse.runId)
        if (!commandCoRelation.hasChildren(parentRunId))
          updateCommand(parentRunId, CommandResponse.withRunId(parentRunId, childCommandResponse))
      case _ ⇒ log.debug("Validation response will not affect status of Parent command.")
    }

  private def publishToSubscribers(commandResponse: SubmitResponse, subscribers: Set[ActorRef[SubmitResponse]]): Unit = {

    isCommandResultType(commandResponse) match {
      case _: CommandResultType.Final ⇒ doPublish(commandResponse, subscribers)

      case _ => println("Don't publish started")
    }
    /*
      case CommandResultType.Intermediate ⇒
        // Do not send updates for validation response as it is sent by the framework
        println("Don't publish started")
        log.debug("Validation response will not affect status of Parent command.")
   */
  }

  private def doPublish(commandResponse: SubmitResponse, subscribers: Set[ActorRef[SubmitResponse]]): Unit =
    subscribers.foreach(_ ! commandResponse)

  private def subscribe(runId: Id, replyTo: ActorRef[SubmitResponse]): Unit = {
    ctx.watchWith(replyTo, SubscriberTerminated(replyTo))
    commandResponseManagerState = commandResponseManagerState.subscribe(runId, replyTo)
    commandResponseManagerState.get(runId) match {
      case sr: SubmitResponse => publishToSubscribers(sr, Set(replyTo))
      case _                  => log.debug("Failed to find runId for subscribe.")
    }
  }
}