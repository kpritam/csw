package csw.services.event.internal.commons.serviceresolver

import java.net.URI

import csw.services.event.internal.commons.EventServiceConnection
import csw.services.location.scaladsl.LocationService

import scala.async.Async._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{ExecutionContext, Future}

/**
 * Resolves the location of server hosting the Event service
 */
class EventServiceLocationResolver(locationService: LocationService)(implicit ec: ExecutionContext) extends EventServiceResolver {

  def uri: Future[URI] = async {
    val location = await(locationService.resolve(EventServiceConnection.value, 5.seconds)).getOrElse(
      throw new RuntimeException(
        s"event service connection=${EventServiceConnection.value.name} can not be resolved"
      )
    )
    location.uri
  }
}