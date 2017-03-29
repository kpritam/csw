package csw.services.location.models

import akka.Done

import scala.concurrent.Future

/**
  * Returned from register calls so that client can close the connection and deregister the service
  */
trait RegistrationResult {
  /**
    * Unregisters the previously registered service.
    */
  def unregister(): Future[Done]

  /**
    * Identifies the registered component
    */
  def location: Location
}
