package csw.command.api.scaladsl

import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{ProcessSequenceError, Sequence}

import scala.concurrent.Future

trait SequencerCommandService {
  def submit(sequence: Sequence): Future[Either[ProcessSequenceError, SubmitResponse]]
}
