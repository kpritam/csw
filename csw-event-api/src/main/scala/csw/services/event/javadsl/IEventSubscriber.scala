package csw.services.event.javadsl

import java.time.Duration
import java.util
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import akka.actor.typed.ActorRef
import akka.stream.javadsl.Source
import csw.messages.events.{Event, EventKey}
import csw.messages.params.models.Subsystem
import csw.services.event.scaladsl.SubscriptionMode

/**
 * An EventSubscriber interface to subscribe events. The events can be subscribed on [[csw.messages.events.EventKey]]. All events
 * published on this key will be received by subscribers.
 */
trait IEventSubscriber {

  /**
   * Subscribe to multiple Event Keys and get a single stream of events for all event keys. The latest events available for the given
   * Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @return a [[akka.stream.javadsl.Source]] of [[csw.messages.events.Event]]. The materialized value of the source provides an
   *         [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribe(eventKeys: util.Set[EventKey]): Source[Event, IEventSubscription]

  /**
   * Subscribe to multiple eventKeys and receive events at `every` frequency based on one of the given `mode` (RateAdapter or RateLimiter). The latest events
   * available for the given Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will be received for
   * those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param every the duration which determines the frequency with which events are received
   * @param mode an appropriate [[csw.services.event.scaladsl.SubscriptionMode]] to control the behavior of rate of events w.r.t. the given frequency.
   *             Refer the API documentation for SubscriptionMode for more details
   * @return a [[akka.stream.javadsl.Source]] of [[csw.messages.events.Event]]. The materialized value of the source provides an
   *         [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribe(eventKeys: util.Set[EventKey], every: Duration, mode: SubscriptionMode): Source[Event, IEventSubscription]

  /**
   * Subscribes an asynchronous callback function to events from multiple eventKeys. The callback is of event => completable future
   * type, so that blocking operation within callback can be placed in the completable future (separate thread than main thread). The latest events available
   * for the given Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param callback a function to execute asynchronously on each received event
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribeAsync(eventKeys: util.Set[EventKey], callback: Event ⇒ CompletableFuture[_]): IEventSubscription

  /**
   * [[csw.services.event.javadsl.IEventSubscriber#subscribeAsync]] overload for receiving event at a `every` frequency based on one of the give `mode`. The latest
   * events available for the given Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param callback a function to execute on each received event
   * @param every the duration which determines the frequency with which events are received
   * @param mode an appropriate [[csw.services.event.scaladsl.SubscriptionMode]] to control the behavior of rate of events w.r.t. the given frequency.
   *             Refer the API documentation for SubscriptionMode for more details
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribeAsync(
      eventKeys: util.Set[EventKey],
      callback: Event ⇒ CompletableFuture[_],
      every: Duration,
      mode: SubscriptionMode
  ): IEventSubscription

  /**
   * Subscribes a callback function to events from multiple event keys. Note that any exception thrown from `callback` is expected to be handled by
   * component developers. The latest events available for the given Event Keys will be received first. If event is not published for one or more event keys,
   * `invalid event` will be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param callback a consumer which defines an operation to execute on each received event
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribeCallback(eventKeys: util.Set[EventKey], callback: Consumer[Event]): IEventSubscription

  /**
   * [[csw.services.event.javadsl.IEventSubscriber#subscribeCallback]] overload for receiving event at a `every` frequency based on one of the give `mode`.
   * The latest events available for the given Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will
   * be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param callback a consumer which defines an operation to execute on each received event
   * @param every the duration which determines the frequency with which events are received
   * @param mode an appropriate [[csw.services.event.scaladsl.SubscriptionMode]] to control the behavior of rate of events w.r.t. the given frequency.
   *             Refer the API documentation for SubscriptionMode for more details
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribeCallback(
      eventKeys: util.Set[EventKey],
      callback: Consumer[Event],
      every: Duration,
      mode: SubscriptionMode
  ): IEventSubscription

  /**
   * Subscribes an actor to events from multiple event keys. The latest events available for the given Event Keys will be received first.
   * If event is not published for one or more event keys, `invalid event` will be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param actorRef an actorRef of an actor which handles each received event
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribeActorRef(eventKeys: util.Set[EventKey], actorRef: ActorRef[Event]): IEventSubscription

  /**
   * [[csw.services.event.javadsl.IEventSubscriber#subscribeActorRef]] overload for receiving event at a `every` frequency based on one of the give `mode`.
   * The latest events available for the given Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will be
   * received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @param actorRef an actorRef of an actor to which each received event is redirected
   * @param every the duration which determines the frequency with which events are received
   * @param mode an appropriate [[csw.services.event.scaladsl.SubscriptionMode]] to control the behavior of rate of events w.r.t. the given frequency.
   *             Refer the API documentation for SubscriptionMode for more details
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def subscribeActorRef(
      eventKeys: util.Set[EventKey],
      actorRef: ActorRef[Event],
      every: Duration,
      mode: SubscriptionMode
  ): IEventSubscription

  /**
   * Subscribe to events from Event Keys specified using a subsystem and a pattern to match the remaining Event Key
   *
   * @param subsystem a valid [[csw.messages.params.models.Subsystem]] which represents the source of the events
   * @param pattern   Subscribes the client to the given patterns. Supported glob-style patterns:
   *                  - h?llo subscribes to hello, hallo and hxllo
   *                  - h*llo subscribes to hllo and heeeello
   *                  - h[ae]llo subscribes to hello and hallo, but not hillo
   *                  Use \ to escape special characters if you want to match them verbatim.
   * @return a [[akka.stream.javadsl.Source]] of [[csw.messages.events.Event]]. The materialized value of the source provides an [[csw.services.event.javadsl.IEventSubscription]]
   *         which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def pSubscribe(subsystem: Subsystem, pattern: String): Source[Event, IEventSubscription]

  /**
   * Subscribes a callback to events from Event Keys specified using a subsystem and a pattern to match the remaining Event Key
   *
   * @param subsystem a valid [[csw.messages.params.models.Subsystem]] which represents the source of the events
   * @param pattern   Subscribes the client to the given patterns. Supported glob-style patterns:
                      - h?llo subscribes to hello, hallo and hxllo
                      - h*llo subscribes to hllo and heeeello
                      - h[ae]llo subscribes to hello and hallo, but not hillo
                      Use \ to escape special characters if you want to match them verbatim.
   * @param callback a consumer which defines an operation to execute on each received event
   * @return an [[csw.services.event.javadsl.IEventSubscription]] which can be used to unsubscribe from all the Event Keys which were subscribed to
   */
  def pSubscribe(subsystem: Subsystem, pattern: String, callback: Consumer[Event]): IEventSubscription

  /**
   * Get latest events for multiple Event Keys. If an event is not published for any Event Key, then `invalid event` is returned for that Event Key.
   * The latest events available for the given Event Keys will be received first. If event is not published for one or more event keys, `invalid event` will
   * be received for those Event Keys.
   *
   * @param eventKeys a set of [[csw.messages.events.EventKey]] to subscribe to
   * @return a completable future which completes with a set of latest [[csw.messages.events.Event]] for the provided Event Keys
   */
  def get(eventKeys: util.Set[EventKey]): CompletableFuture[util.Set[Event]]

  /**
   * Get latest event for the given Event Key. If an event is not published for any eventKey, then `invalid event` is returned for that Event Key.
   *
   * @param eventKey an [[csw.messages.events.EventKey]] to subscribe to
   * @return a completable future which completes with the latest [[csw.messages.events.Event]] for the provided Event Key
   */
  def get(eventKey: EventKey): CompletableFuture[Event]
}
