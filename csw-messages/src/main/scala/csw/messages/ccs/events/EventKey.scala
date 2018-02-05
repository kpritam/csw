package csw.messages.ccs.events

import play.api.libs.json.{Json, OFormat}

case class EventKey(key: String) {
  override def toString: String = key
}

object EventKey {
  implicit val format: OFormat[EventKey] = Json.format[EventKey]
}
