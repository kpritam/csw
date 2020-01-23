package csw.contract.services.data.location.endpoints

import java.util.concurrent.TimeUnit

import akka.Done
import csw.contract.services.data.location.models.Instances._
import csw.contract.services.models.DomHelpers._
import csw.contract.services.models.Endpoint
import csw.location.api.codec.LocationServiceCodecs
import csw.location.api.messages.LocationHttpMessage
import csw.location.api.messages.LocationHttpMessage.{
  Find,
  ListByComponentType,
  ListByConnectionType,
  ListByHostname,
  ListByPrefix,
  Register,
  Resolve,
  Unregister
}
import csw.location.models.{ComponentType, ConnectionType, Location, TypedConnection}
import msocket.api.codecs.BasicCodecs

import scala.concurrent.duration.FiniteDuration

object Instances extends LocationServiceCodecs with BasicCodecs {

  val registerAkka: LocationHttpMessage = Register(akkaRegistration)
  val registerHttp: LocationHttpMessage = Register(httpRegistration)
  val unregister: LocationHttpMessage   = Unregister(connection)
  val find: LocationHttpMessage         = Find(akkaConnection.asInstanceOf[TypedConnection[Location]])
  val resolve: LocationHttpMessage =
    Resolve(akkaConnection.asInstanceOf[TypedConnection[Location]], FiniteDuration(23, TimeUnit.SECONDS))
  val listByComponentType: LocationHttpMessage  = ListByComponentType(ComponentType.HCD)
  val listByHostname: LocationHttpMessage       = ListByHostname("hostname")
  val listByConnectionType: LocationHttpMessage = ListByConnectionType(ConnectionType.AkkaType)
  val listByPrefix: LocationHttpMessage         = ListByPrefix("TCS.filter.wheel")

  val done: Done                = Done
  val option: Option[Location]  = Some(akkaLocation)
  val locations: List[Location] = List(akkaLocation, httpLocation)

  val endpoints: Map[String, Endpoint] = Map(
    "register" -> Endpoint(
      requests = List(registerAkka, registerHttp),
      responses = List(registrationFailed, akkaLocation)
    ),
    "unregister" -> Endpoint(
      requests = List(unregister),
      responses = List(done)
    ),
    "unregisterAll" -> Endpoint(
      requests = List(),
      responses = List(done)
    ),
    "find" -> Endpoint(
      requests = List(find),
      responses = List(option)
    ),
    "resolve" -> Endpoint(
      requests = List(resolve),
      responses = List(option)
    ),
    "listEntries" -> Endpoint(
      requests = List(),
      responses = List(locations)
    ),
    "listByComponentType" -> Endpoint(
      requests = List(listByComponentType),
      responses = List(locations)
    ),
    "listByHostname" -> Endpoint(
      requests = List(listByHostname),
      responses = List(locations)
    ),
    "listByConnectionType" -> Endpoint(
      requests = List(listByConnectionType),
      responses = List(locations)
    ),
    "listByPrefix" -> Endpoint(
      requests = List(listByPrefix),
      responses = List(locations)
    )
  )
}