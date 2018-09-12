package csw.services.location.api.formats

import akka.Done
import csw.messages.extensions.Formats
import csw.messages.extensions.Formats.MappableFormat
import csw.services.location.api.models.{AkkaLocation, Registration}
import julienrf.json.derived
import play.api.libs.json.{__, Format, Json, OFormat}

trait LocationJsonSupport extends ActorSystemDependentFormats {
  implicit val akkaLocationFormat: Format[AkkaLocation]  = Json.format[AkkaLocation]
  implicit val registrationFormat: OFormat[Registration] = derived.flat.oformat((__ \ "type").format[String])
  implicit val doneFormat: Format[Done]                  = Formats.of[String].bimap[Done](_ => "done", _ => Done)
}