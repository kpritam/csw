package sampler.metadata

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import csw.event.api.scaladsl.EventSubscriber
import csw.event.client.internal.redis.{RedisGlobalSubscriber, RedisSubscriber}
import csw.location.client.ActorSystemFactory
import csw.params.events.{Event, EventKey}
import io.lettuce.core.{RedisClient, RedisURI}
import sampler.metadata.SamplerUtil.{printAggregates, recordHistogram}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class MetadataPSubscribeSampler(
    globalSubscriber: RedisGlobalSubscriber,
    eventSubscriber: EventSubscriber
)(implicit actorSystem: ActorSystem[_]) {

  var currentState = new ConcurrentHashMap[EventKey, Event]() // mutable map with variable ref
  val snapshotMap  = new ConcurrentHashMap[String, ConcurrentHashMap[EventKey, Event]]()

  var numberOfSnapshots                   = 0d
  val eventTimeDiffList: ListBuffer[Long] = scala.collection.mutable.ListBuffer()
  val snapshotTimeList: ListBuffer[Long]  = scala.collection.mutable.ListBuffer()

  def snapshot(obsEvent: Event): Unit = {
    val startTime = System.currentTimeMillis()
    //swap approach
//    lastSnapshot = currentState
//    currentState = new ConcurrentHashMap(lastSnapshot) //change it with new Map and clone previous snapshot.

    //clone approach
    val lastSnapshot: ConcurrentHashMap[EventKey, Event] = new ConcurrentHashMap(currentState)
    snapshotMap.put("some-exposure-id", lastSnapshot) //obsEvent.eventKey => exposureid

    val endTime = System.currentTimeMillis()

    val event         = lastSnapshot.getOrDefault(EventKey("ESW.filter.wheel"), Event.badEvent())
    val eventTimeDiff = Duration.between(event.eventTime.value, obsEvent.eventTime.value).toMillis
    val snapshotTime  = endTime - startTime

    //Event time diff
    eventTimeDiffList.addOne(Math.abs(eventTimeDiff))

    //Snapshot time diff
    snapshotTimeList.addOne(snapshotTime)
    recordHistogram(Math.abs(eventTimeDiff), Math.abs(snapshotTime))

    println(s"${lastSnapshot.size} ,$eventTimeDiff ,$snapshotTime")
  }

  def subscribeObserveEvents(): Future[Done] =
    eventSubscriber.subscribe(Set(EventKey("esw.observe.expstr"))).drop(50).take(1000).runForeach(snapshot)

  def start(): Future[Done] = {
    //print aggregates
    CoordinatedShutdown(actorSystem).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "print aggregates") { () =>
      printAggregates(eventTimeDiffList, snapshotTimeList)
      Future.successful(Done)
    }

    val subscribeFuture = subscribeObserveEvents() // fire in background

    globalSubscriber
      .subscribeAll()
      .runForeach { e => currentState.put(e.eventKey, e) }

    subscribeFuture
  }
}

object MetadataPSubscribeSampler extends App {

  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol())

  private val redisClient: RedisClient = RedisClient.create()

  private val eventualRedisURI: Future[RedisURI] =
    Future.successful(RedisURI.Builder.sentinel("localhost", 26379, "eventServer").build())

  private val subscriber = new RedisSubscriber(eventualRedisURI, redisClient)

  private val globalSubscriber: RedisGlobalSubscriber = RedisGlobalSubscriber.make(redisClient, eventualRedisURI)

  private val sampler = new MetadataPSubscribeSampler(globalSubscriber, subscriber)

  Await.result(sampler.start(), 20.minutes)
  system.terminate()
  redisClient.shutdown()
}
