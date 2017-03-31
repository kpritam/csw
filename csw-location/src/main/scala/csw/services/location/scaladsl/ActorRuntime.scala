package csw.services.location.scaladsl

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.stream.{ActorMaterializer, Materializer}
import csw.services.location.internal.{Settings, Terminator}

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

/**
  * `ActorRuntime` manages [[scala.concurrent.ExecutionContext]], [[akka.stream.Materializer]], `akka.cluster.Cluster`
  * and `Hostname` of an [[akka.actor.ActorSystem]]
  *
  * @note It is highly recommended that `ActorRuntime` is created for advanced usages or testing purposes only
  */
class ActorRuntime(_actorSystem: ActorSystem) {
  def this(settings: Settings) = this(ActorSystem(settings.name, settings.config))

  def this() = this(Settings())

  /**
    * Identifies the hostname where `ActorSystem` is running
    */
  val hostname: String = _actorSystem.settings.config.getString("akka.remote.netty.tcp.hostname")

  implicit val actorSystem: ActorSystem = _actorSystem
  implicit val ec: ExecutionContext = actorSystem.dispatcher
  implicit val mat: Materializer = makeMat()
  implicit val cluster = Cluster(actorSystem)

  /**
    * Replicator of current `ActorSystem`
    */
  val replicator: ActorRef = DistributedData(actorSystem).replicator

  /**
    * If no seed node is provided then the cluster is initialized for `ActorSystem`  by self joining
    */
  def initialize(): Unit = {
    val emptySeeds = actorSystem.settings.config.getStringList("akka.cluster.seed-nodes").isEmpty
    if (emptySeeds) {
      cluster.join(cluster.selfAddress)
    }
    val p = Promise[Done]
    cluster.registerOnMemberUp(p.success(Done))
    try {
      Await.result(p.future, 10.seconds)
    } catch {
      case NonFatal(ex) ⇒
        Await.result(Terminator.terminate(actorSystem), 10.seconds)
    }
  }

  /**
    * Creates an `ActorMaterializer` for current `ActorSystem`
    */
  def makeMat(): Materializer = ActorMaterializer()

  /**
    * Terminates the `ActorSystem` and disconnects from the cluster.
    *
    * @see [[csw.services.location.internal.Terminator]]
    * @return A `Future` that completes on `ActorSystem` shutdown
    */
  def terminate(): Future[Done] = Terminator.terminate(actorSystem)

  initialize()
}
