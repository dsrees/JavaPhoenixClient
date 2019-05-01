/*
 * Copyright (c) 2019 Daniel Rees <daniel.rees18@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.phoenixframework

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a binding to a Channel event
 */
data class Binding(
  val event: String,
  val ref: Int,
  val callback: (Message) -> Unit
)

/**
 * Represents a Channel bound to a given topic
 */
class Channel(
  val topic: String,
  params: Payload,
  internal val socket: Socket
) {

  //------------------------------------------------------------------------------
  // Channel Nested Enums
  //------------------------------------------------------------------------------
  /** States of a Channel */
  enum class State() {
    CLOSED,
    ERRORED,
    JOINED,
    JOINING,
    LEAVING
  }

  /** Channel specific events */
  enum class Event(val value: String) {
    HEARTBEAT("heartbeat"),
    JOIN("phx_join"),
    LEAVE("phx_leave"),
    REPLY("phx_reply"),
    ERROR("phx_error"),
    CLOSE("phx_close");

    companion object {
      /** True if the event is one of Phoenix's channel lifecycle events */
      fun isLifecycleEvent(event: String): Boolean {
        return when (event) {
          JOIN.value,
          LEAVE.value,
          REPLY.value,
          ERROR.value,
          CLOSE.value -> true
          else -> false
        }
      }
    }
  }

  //------------------------------------------------------------------------------
  // Channel Attributes
  //------------------------------------------------------------------------------
  /** Current state of the Channel */
  internal var state: State

  /** Collection of event bindings. */
  internal val bindings: ConcurrentLinkedQueue<Binding>

  /** Tracks event binding ref counters */
  internal var bindingRef: Int

  /** Timeout when attempting to join a Channel */
  internal var timeout: Long

  /** Params passed in through constructions and provided to the JoinPush */
  var params: Payload = params
    set(value) {
      joinPush.payload = value
      field = value
    }

  /** Set to true once the channel has attempted to join */
  internal var joinedOnce: Boolean

  /** Push to send then attempting to join */
  internal var joinPush: Push

  /** Buffer of Pushes that will be sent once the Channel's socket connects */
  internal var pushBuffer: MutableList<Push>

  /** Timer to attempt rejoins */
  internal var rejoinTimer: TimeoutTimer

  /**
   * Optional onMessage hook that can be provided. Receives all event messages for specialized
   * handling before dispatching to the Channel event callbacks.
   */
  internal var onMessage: (Message) -> Message = { it }

  init {
    this.state = State.CLOSED
    this.bindings = ConcurrentLinkedQueue()
    this.bindingRef = 0
    this.timeout = socket.timeout
    this.joinedOnce = false
    this.pushBuffer = mutableListOf()
    this.rejoinTimer = TimeoutTimer(
        dispatchQueue = socket.dispatchQueue,
        callback = { rejoinUntilConnected() },
        timerCalculation = socket.reconnectAfterMs)

    // Setup Push to be sent when joining
    this.joinPush = Push(
        channel = this,
        event = Event.JOIN.value,
        payload = params,
        timeout = timeout)

    // Perform once the Channel has joined
    this.joinPush.receive("ok") {
      // Mark the Channel as joined
      this.state = State.JOINED

      // Reset the timer, preventing it from attempting to join again
      this.rejoinTimer.reset()

      // Send any buffered messages and clear the buffer
      this.pushBuffer.forEach { it.send() }
      this.pushBuffer.clear()
    }

    // Perform if Channel timed out while attempting to join
    this.joinPush.receive("timeout") {

      // Only handle a timeout if the Channel is in the 'joining' state
      if (!this.isJoining) return@receive

      this.socket.logItems("Channel: timeouts $topic, $joinRef after $timeout ms")

      // Send a Push to the server to leave the Channel
      val leavePush = Push(
          channel = this,
          event = Event.LEAVE.value,
          timeout = this.timeout)
      leavePush.send()

      // Mark the Channel as in an error and attempt to rejoin
      this.state = State.ERRORED
      this.joinPush.reset()
      this.rejoinTimer.scheduleTimeout()
    }

    // Clean up when the channel closes
    this.onClose {
      // Reset any timer that may be on-going
      this.rejoinTimer.reset()

      // Log that the channel was left
      this.socket.logItems("Channel: close $topic")

      // Mark the channel as closed and remove it from the socket
      this.state = State.CLOSED
      this.socket.remove(this)
    }

    // Handles an error, attempts to rejoin
    this.onError {
      // Do not emit error if the channel is in the process of leaving
      // or if it has already closed
      if (this.isLeaving || this.isClosed) return@onError

      // Log that the channel received an error
      this.socket.logItems("Channel: error $topic")

      // Mark the channel as errored and attempt to rejoin
      this.state = State.ERRORED
      this.rejoinTimer.scheduleTimeout()
    }

    // Perform when the join reply is received
    this.on(Event.REPLY) { message ->
      this.trigger(replyEventName(message.ref), message.payload, message.ref, message.joinRef)
    }
  }

  //------------------------------------------------------------------------------
  // Public Properties
  //------------------------------------------------------------------------------
  /** The ref sent during the join message. */
  val joinRef: String? get() = joinPush.ref

  /** @return True if the Channel can push messages */
  val canPush: Boolean
    get() = this.socket.isConnected && this.isJoined

  /** @return: True if the Channel has been closed */
  val isClosed: Boolean
    get() = state == State.CLOSED

  /** @return: True if the Channel experienced an error */
  val isErrored: Boolean
    get() = state == State.ERRORED

  /** @return: True if the channel has joined */
  val isJoined: Boolean
    get() = state == State.JOINED

  /** @return: True if the channel has requested to join */
  val isJoining: Boolean
    get() = state == State.JOINING

  /** @return: True if the channel has requested to leave */
  val isLeaving: Boolean
    get() = state == State.LEAVING

  //------------------------------------------------------------------------------
  // Public
  //------------------------------------------------------------------------------
  fun join(timeout: Long = this.timeout): Push {
    // Ensure that `.join()` is called only once per Channel instance
    if (joinedOnce) {
      throw IllegalStateException(
          "Tried to join channel multiple times. `join()` can only be called once per channel")
    }

    // Join the channel
    this.joinedOnce = true
    this.rejoin(timeout)
    return joinPush
  }

  fun onClose(callback: (Message) -> Unit): Int {
    return this.on(Event.CLOSE, callback)
  }

  fun onError(callback: (Message) -> Unit): Int {
    return this.on(Event.ERROR, callback)
  }

  fun onMessage(callback: (Message) -> Message) {
    this.onMessage = callback
  }

  fun on(event: Event, callback: (Message) -> Unit): Int {
    return this.on(event.value, callback)
  }

  fun on(event: String, callback: (Message) -> Unit): Int {
    val ref = bindingRef
    this.bindingRef = ref + 1

    this.bindings.add(Binding(event, ref, callback))
    return ref
  }

  fun off(event: String, ref: Int? = null) {
    this.bindings.removeAll { bind ->
      bind.event == event && (ref == null || ref == bind.ref)
    }
  }

  fun push(event: String, payload: Payload, timeout: Long = this.timeout): Push {
    if (!joinedOnce) {
      // If the Channel has not been joined, throw an exception
      throw RuntimeException(
          "Tried to push $event to $topic before joining. Use channel.join() before pushing events")
    }

    val pushEvent = Push(this, event, payload, timeout)

    if (canPush) {
      pushEvent.send()
    } else {
      pushEvent.startTimeout()
      pushBuffer.add(pushEvent)
    }

    return pushEvent
  }

  fun leave(timeout: Long = this.timeout): Push {
    // Can push is dependent upon state == JOINED. Once we set it to LEAVING, then canPush
    // will return false, so instead store it _before_ starting the leave
    val canPush = this.canPush

    // Now set the state to leaving
    this.state = State.LEAVING

    // Perform the same behavior if the channel leaves successfully or not
    val onClose: ((Message) -> Unit) = {
      this.socket.logItems("Channel: leave $topic")
      this.trigger(Event.CLOSE, mapOf("reason" to "leave"))
    }

    // Push event to send to the server
    val leavePush = Push(
        channel = this,
        event = Event.LEAVE.value,
        timeout = timeout)

    leavePush
        .receive("ok", onClose)
        .receive("timeout", onClose)
    leavePush.send()

    // If the Channel cannot send push events, trigger a success locally
    if (!canPush) leavePush.trigger("ok", hashMapOf())

    return leavePush
  }

  //------------------------------------------------------------------------------
  // Internal
  //------------------------------------------------------------------------------
  /** Checks if a Message's event belongs to this Channel instance */
  internal fun isMember(message: Message): Boolean {
    if (message.topic != this.topic) return false

    val isLifecycleEvent = Event.isLifecycleEvent(message.event)

    // If the message is a lifecycle event and it is not a join for this channel, drop the outdated message
    if (message.joinRef != null && isLifecycleEvent && message.joinRef != this.joinRef) {
      this.socket.logItems("Channel: Dropping outdated message. ${message.topic}")
      return false
    }

    return true
  }

  internal fun trigger(
    event: Event,
    payload: Payload = hashMapOf(),
    ref: String = "",
    joinRef: String? = null
  ) {
    this.trigger(event.value, payload, ref, joinRef)
  }

  internal fun trigger(
    event: String,
    payload: Payload = hashMapOf(),
    ref: String = "",
    joinRef: String? = null
  ) {
    this.trigger(Message(ref, topic, event, payload, joinRef))
  }

  internal fun trigger(message: Message) {
    // Inform the onMessage hook of the message
    val handledMessage = this.onMessage(message)

    // Inform all matching event bindings of the message
    this.bindings
        .filter { it.event == message.event }
        .forEach { it.callback(handledMessage) }
  }

  /** Create an event with a given ref */
  internal fun replyEventName(ref: String): String {
    return "chan_reply_$ref"
  }

  //------------------------------------------------------------------------------
  // Private
  //------------------------------------------------------------------------------
  /** Will continually attempt to rejoin the Channel on a timer. */
  private fun rejoinUntilConnected() {
    this.rejoinTimer.scheduleTimeout()
    if (this.socket.isConnected) this.rejoin()
  }

  /** Sends the Channel's joinPush to the Server */
  private fun sendJoin(timeout: Long) {
    this.state = State.JOINING
    this.joinPush.resend(timeout)
  }

  /** Rejoins the Channel e.g. after a disconnect */
  private fun rejoin(timeout: Long = this.timeout) {
    this.sendJoin(timeout)
  }
}