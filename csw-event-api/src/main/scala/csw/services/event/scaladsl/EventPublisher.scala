package csw.services.event.scaladsl

import akka.Done
import akka.actor.Cancellable
import akka.stream.scaladsl.Source
import csw.messages.events.Event
import csw.services.event.exceptions.PublishFailure

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * An EventPublisher interface to publish events. The published events are published on a key determined by [[csw.messages.events.EventKey]]
 * in the [[csw.messages.events.Event]] model. This key can be used by the subscribers using [[csw.services.event.scaladsl.EventSubscriber]]
 * interface to subscribe to the events.
 */
trait EventPublisher {

  /**
   * Publish a single [[csw.messages.events.Event]]
   *
   * At the time of invocation, in case the underlying server is not available, [[csw.services.event.exceptions.EventServerNotAvailable]] exception is thrown,
   * in all other cases [[csw.services.event.exceptions.PublishFailure]] exception is thrown which wraps the underlying exception and
   * also provides the handle to the event which was failed to be published
   *
   * @param event an event to be published
   * @return a future which completes when the event is published
   */
  def publish(event: Event): Future[Done]

  /**
   * Publish from a stream of [[csw.messages.events.Event]]
   *
   * At the time of invocation, in case the underlying server is not available, [[csw.services.event.exceptions.EventServerNotAvailable]] exception is thrown and the stream is
   * stopped after logging appropriately. In all other cases of exception, the stream receives a [[csw.services.event.exceptions.PublishFailure]] exception
   * which wraps the underlying exception. The stream resumes to publish remaining elements in case of this exception.
   *
   * @param source a [[akka.stream.scaladsl.Source]] of events to be published
   * @tparam Mat represents the type of materialized value as defined in the source to be obtained on running the stream
   * @return the materialized value obtained on running the stream
   */
  def publish[Mat](source: Source[Event, Mat]): Mat

  /**
   * Publish from a stream of [[csw.messages.events.Event]], and execute `onError` callback for each event for which publishing failed
   *
   * At the time of invocation, in case the underlying server is not available, [[csw.services.event.exceptions.EventServerNotAvailable]] exception is thrown and the stream is
   * stopped after logging appropriately. In all other cases of exception, the stream receives a [[csw.services.event.exceptions.PublishFailure]] exception
   * which wraps the underlying exception and also provides the handle to the event which was failed to be published.
   * The provided callback is executed on the failed element and the stream resumes to publish remaining elements.
   *
   * @param source a [[akka.stream.scaladsl.Source]] of events to be published
   * @param onError a callback to execute for each event for which publishing failed
   * @tparam Mat represents the type of materialized value as defined in the source to be obtained on running the stream
   * @return the materialized value obtained on running the stream
   */
  def publish[Mat](source: Source[Event, Mat], onError: PublishFailure ⇒ Unit): Mat

  /**
   * Publish [[csw.messages.events.Event]] from an `eventGenerator` function, which will be executed at `every` frequency. `Cancellable` can be used to cancel
   * the execution of `eventGenerator` function.
   *
   * At the time of invocation, in case the underlying server is not available, [[csw.services.event.exceptions.EventServerNotAvailable]] exception is thrown and the stream is
   * stopped after logging appropriately. In all other cases of exception, the stream receives a [[csw.services.event.exceptions.PublishFailure]] exception
   * which wraps the underlying exception. The generator resumes to publish remaining elements in case of this exception.
   *
   * @param eventGenerator a function which can generate an event to be published at `every` frequency
   * @param every frequency with which the events are to be published
   * @return a handle to cancel the event generation through `eventGenerator`
   */
  def publish(eventGenerator: => Event, every: FiniteDuration): Cancellable

  /**
   * Publish [[csw.messages.events.Event]] from an `eventGenerator` function, which will be executed at `every` frequency. Also, provide `onError` callback
   * for each event for which publishing failed.
   *
   * At the time of invocation, in case the underlying server is not available, [[csw.services.event.exceptions.EventServerNotAvailable]] exception is thrown and the stream is
   * stopped after logging appropriately. In all other cases of exception, the stream receives a [[csw.services.event.exceptions.PublishFailure]] exception
   * which wraps the underlying exception and also provides the handle to the event which was failed to be published.
   * The provided callback is executed on the failed element and the generator resumes to publish remaining elements.
   *
   * @note any exception thrown from `eventGenerator` or `onError` callback is expected
   * to be handled by component developers.
   * @param eventGenerator a function which can generate an event to be published at `every` frequency
   * @param every frequency with which the events are to be published
   * @param onError a callback to execute for each event for which publishing failed
   * @return a handle to cancel the event generation through `eventGenerator`
   */
  def publish(eventGenerator: ⇒ Event, every: FiniteDuration, onError: PublishFailure ⇒ Unit): Cancellable

  /**
   * Shuts down the connection for this publisher. Using any api of publisher after shutdown should give exceptions.
   * This method should be called while the component is shutdown gracefully.
   *
   * Any exception that occurs will cause the future to complete with a Failure.
   *
   * @return a future which completes when the underlying connection is shut down
   */
  private[event] def shutdown(): Future[Done]
}
