package csw.services.alarm.client.internal.shelve
import csw.services.alarm.api.models.Key.AlarmKey

trait UnShelvable {
  def unShelve(key: AlarmKey): Unit
}
