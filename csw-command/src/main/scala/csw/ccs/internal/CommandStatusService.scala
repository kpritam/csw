package csw.ccs.internal

import akka.typed.Behavior
import akka.typed.scaladsl.ActorContext
import csw.ccs.models.CommandStatusServiceState
import csw.messages.CommandStatusMessages._
import csw.messages.ccs.commands.{CommandResponse, CommandResultType}
import csw.messages.params.models.RunId
import csw.messages.{Add, CommandStatusMessages, UpdateCommand}
import csw.services.logging.scaladsl.ComponentLogger

class CommandStatusService(
    ctx: ActorContext[CommandStatusMessages],
    componentName: String
) extends ComponentLogger.MutableActor[CommandStatusMessages](ctx, componentName) {

  var commandStatus: CommandStatusServiceState = CommandStatusServiceState(Map.empty)

  override def onMessage(msg: CommandStatusMessages): Behavior[CommandStatusMessages] = {
    msg match {
      case Query(runId, replyTo)          ⇒ replyTo ! commandStatus.get(runId)
      case Add(runId, replyTo)            ⇒ commandStatus.add(runId, replyTo)
      case UpdateCommand(commandResponse) ⇒ updateCommandStatus(commandResponse)
      case Subscribe(runId, replyTo)      ⇒ commandStatus.subscribe(runId, replyTo)
      case UnSubscribe(runId, replyTo)    ⇒ commandStatus.unSubscribe(runId, replyTo)
    }
    this
  }

  def updateCommandStatus(commandResponse: CommandResponse): Unit = {
    commandStatus.updateCommandStatus(commandResponse)
    publishToSubscribers(commandResponse.runId)
  }

  private def publishToSubscribers(runId: RunId): Unit = {
    val commandState = commandStatus.cmdToCmdStatus(runId)
    commandState.subscribers.foreach(_ ! commandState.commandStatus.currentCmdStatus)
  }
}
