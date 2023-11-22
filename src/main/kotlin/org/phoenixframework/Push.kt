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

import java.util.concurrent.TimeUnit

/**
 * A Push represents an attempt to send a payload through a Channel for a specific event.
 */
class Push(
  /** The channel the Push is being sent through */
  val channel: Channel,
  /** The event the Push is targeting */
  val event: String,
  /** Closure that allows changing parameters sent during push */
  var payloadClosure: PayloadClosure,
  /** Duration before the message is considered timed out and failed to send */
  var timeout: Long = Defaults.TIMEOUT
) {

  /** The server's response to the Push */
  var receivedMessage: Message? = null

  /** The task to be triggered if the Push times out */
  var timeoutTask: DispatchWorkItem? = null

  /** Hooks into a Push. Where .receive("ok", callback(Payload)) are stored */
  var receiveHooks: MutableMap<String, List<((message: Message) -> Unit)>> = HashMap()

  /** True if the Push has been sent */
  var sent: Boolean = false

  /** The reference ID of the Push */
  var ref: String? = null

  /** The event that is associated with the reference ID of the Push */
  var refEvent: String? = null

  var payload: Payload
    get() = payloadClosure.invoke()
    set(value) {
      payloadClosure = { value }
    }

  constructor(
    /** The channel the Push is being sent through */
    channel: Channel,
    /** The event the Push is targeting */
    event: String,
    /** The message to be sent */
    payload: Payload = mapOf(),
    /** Duration before the message is considered timed out and failed to send */
    timeout: Long = Defaults.TIMEOUT
  ) : this(channel, event, { payload }, timeout)

  //------------------------------------------------------------------------------
  // Public
  //------------------------------------------------------------------------------
  /**
   * Resets and sends the Push
   * @param timeout Optional. The push timeout. Default is 10_000ms = 10s
   */
  fun resend(timeout: Long = Defaults.TIMEOUT) {
    this.timeout = timeout
    this.reset()
    this.send()
  }

  /**
   * Sends the Push. If it has already timed out then the call will be ignored. use
   * `resend(timeout:)` in this case.
   */
  fun send() {
    if (hasReceived("timeout")) return

    this.startTimeout()
    this.sent = true
    this.channel.socket.push(channel.topic, event, payload, ref, channel.joinRef)
  }

  /**
   * Receive a specific event when sending an Outbound message
   *
   * Example:
   *  channel
   *      .send("event", myPayload)
   *      .receive("error") { }
   */
  fun receive(status: String, callback: (Message) -> Unit): Push {
    // If the message has already be received, pass it to the callback
    receivedMessage?.let { if (hasReceived(status)) callback(it) }

    // If a previous hook for this status already exists. Just append the new hook. If not, then
    // create a new array of hooks if no previous hook is associated with status
    receiveHooks[status] = receiveHooks[status]?.plus(callback) ?: arrayListOf(callback)

    return this
  }

  //------------------------------------------------------------------------------
  // Internal
  //------------------------------------------------------------------------------
  /** Resets the Push as it was after it was first initialized. */
  internal fun reset() {
    this.cancelRefEvent()
    this.ref = null
    this.refEvent = null
    this.receivedMessage = null
    this.sent = false
  }

  /**
   * Triggers an event to be sent through the Push's parent Channel
   */
  internal fun trigger(status: String, payload: Payload) {
    this.refEvent?.let { refEvent ->
      val mutPayload = payload.toMutableMap()
      mutPayload["status"] = status

      this.channel.trigger(refEvent, mutPayload)
    }
  }

  /**
   * Schedules a timeout task which will be triggered after a specific timeout is reached
   */
  internal fun startTimeout() {
    // Cancel any existing timeout before starting a new one
    this.timeoutTask?.let { if (!it.isCancelled) this.cancelTimeout() }

    // Get the ref of the Push
    val ref = this.channel.socket.makeRef()
    val refEvent = this.channel.replyEventName(ref)

    this.ref = ref
    this.refEvent = refEvent

    // Subscribe to a reply from the server when the Push is received
    this.channel.on(refEvent) { message ->
      this.cancelRefEvent()
      this.cancelTimeout()
      this.receivedMessage = message

      // Check if there is an event receive hook to be informed
      message.status?.let { status -> matchReceive(status, message) }
    }

    // Setup and start the Timer
    this.timeoutTask = channel.socket.dispatchQueue.queue(timeout, TimeUnit.MILLISECONDS) {
      this.trigger("timeout", hashMapOf())
    }
  }

  //------------------------------------------------------------------------------
  // Private
  //------------------------------------------------------------------------------
  /**
   * Finds the receiveHook which needs to be informed of a status response and passes it the message
   *
   * @param status Status which was received. e.g. "ok", "error", etc.
   * @param message Message to pass to receive hook
   */
  private fun matchReceive(status: String, message: Message) {
    receiveHooks[status]?.forEach { it(message) }
  }

  /** Removes receive hook from Channel regarding this Push */
  private fun cancelRefEvent() {
    this.refEvent?.let { this.channel.off(it) }
  }

  /** Cancels any ongoing timeout task */
  internal fun cancelTimeout() {
    this.timeoutTask?.cancel()
    this.timeoutTask = null
  }

  /**
   * @param status Status to check if it has been received
   * @return True if the status has already been received by the Push
   */
  private fun hasReceived(status: String): Boolean {
    return receivedMessage?.status == status
  }
}