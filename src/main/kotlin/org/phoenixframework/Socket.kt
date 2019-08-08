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

import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.URL
import java.util.concurrent.TimeUnit

/** Alias for a JSON mapping */
typealias Payload = Map<String, Any>

/** Data class that holds callbacks assigned to the socket */
internal class StateChangeCallbacks {

  var open: List<() -> Unit> = ArrayList()
    private set
  var close: List<() -> Unit> = ArrayList()
    private set
  var error: List<(Throwable, Response?) -> Unit> = ArrayList()
    private set
  var message: List<(Message) -> Unit> = ArrayList()
    private set

  /** Safely adds an onOpen callback */
  fun onOpen(callback: () -> Unit) {
    this.open = this.open.copyAndAdd(callback)
  }

  /** Safely adds an onClose callback */
  fun onClose(callback: () -> Unit) {
    this.close = this.close.copyAndAdd(callback)
  }

  /** Safely adds an onError callback */
  fun onError(callback: (Throwable, Response?) -> Unit) {
    this.error = this.error.copyAndAdd(callback)
  }

  /** Safely adds an onMessage callback */
  fun onMessage(callback: (Message) -> Unit) {
    this.message = this.message.copyAndAdd(callback)
  }

  /** Clears all stored callbacks */
  fun release() {
    open = emptyList()
    close = emptyList()
    error = emptyList()
    message = emptyList()
  }
}

/** Converts the List to a MutableList, adds the value, and then returns as a read-only List */
fun <T> List<T>.copyAndAdd(value: T): List<T> {
  val temp = this.toMutableList()
  temp.add(value)

  return temp
}


/** RFC 6455: indicates a normal closure */
const val WS_CLOSE_NORMAL = 1000

/** RFC 6455: indicates that the connection was closed abnormally */
const val WS_CLOSE_ABNORMAL = 1006

/**
 * Connects to a Phoenix Server
 */
class Socket(
  url: String,
  params: Payload? = null,
  private val gson: Gson = Defaults.gson,
  private val client: OkHttpClient = OkHttpClient.Builder().build()
) {

  //------------------------------------------------------------------------------
  // Public Attributes
  //------------------------------------------------------------------------------
  /**
   * The string WebSocket endpoint (ie `"ws://example.com/socket"`,
   * `"wss://example.com"`, etc.) that was passed to the Socket during
   * initialization. The URL endpoint will be modified by the Socket to
   * include `"/websocket"` if missing.
   */
  val endpoint: String

  /** The fully qualified socket URL */
  val endpointUrl: URL

  /**
   * The optional params to pass when connecting. Must be set when
   * initializing the Socket. These will be appended to the URL.
   */
  val params: Payload? = params

  /** Timeout to use when opening a connection */
  var timeout: Long = Defaults.TIMEOUT

  /** Interval between sending a heartbeat, in ms */
  var heartbeatIntervalMs: Long = Defaults.HEARTBEAT

  /** Interval between socket reconnect attempts, in ms */
  var reconnectAfterMs: ((Int) -> Long) = Defaults.reconnectSteppedBackOff

  /** Interval between channel rejoin attempts, in ms */
  var rejoinAfterMs: ((Int) -> Long) = Defaults.rejoinSteppedBackOff

  /** The optional function to receive logs */
  var logger: ((String) -> Unit)? = null

  /** Disables heartbeats from being sent. Default is false. */
  var skipHeartbeat: Boolean = false

  //------------------------------------------------------------------------------
  // Internal Attributes
  //------------------------------------------------------------------------------
  /**
   * All timers associated with a socket will share the same pool. Used for every Channel or
   * Push that is sent through or created by a Socket instance. Different Socket instances will
   * create individual thread pools.
   */
//  internal var timerPool: ScheduledExecutorService = ScheduledThreadPoolExecutor(8)
  internal var dispatchQueue: DispatchQueue = ScheduledDispatchQueue()

  //------------------------------------------------------------------------------
  // Private Attributes
  // these are marked as `internal` so that they can be accessed during tests
  //------------------------------------------------------------------------------
  /** Returns the type of transport to use. Potentially expose for custom transports */
  internal var transport: (URL) -> Transport = { WebSocketTransport(it, client) }

  /** Collection of callbacks for socket state changes */
  internal val stateChangeCallbacks: StateChangeCallbacks = StateChangeCallbacks()

  /** Collection of unclosed channels created by the Socket */
  internal var channels: List<Channel> = ArrayList()

  /** Buffers messages that need to be sent once the socket has connected */
  internal var sendBuffer: MutableList<() -> Unit> = ArrayList()

  /** Ref counter for messages */
  internal var ref: Int = 0

  /** Task to be triggered in the future to send a heartbeat message */
  internal var heartbeatTask: DispatchWorkItem? = null

  /** Ref counter for the last heartbeat that was sent */
  internal var pendingHeartbeatRef: String? = null

  /** Timer to use when attempting to reconnect */
  internal var reconnectTimer: TimeoutTimer

  /** True if the Socket closed cleaned. False if not (connection timeout, heartbeat, etc) */
  internal var closeWasClean = false

  //------------------------------------------------------------------------------
  // Connection Attributes
  //------------------------------------------------------------------------------
  /** The underlying WebSocket connection */
  internal var connection: Transport? = null

  //------------------------------------------------------------------------------
  // Initialization
  //------------------------------------------------------------------------------
  init {
    var mutableUrl = url

    // Ensure that the URL ends with "/websocket"
    if (!mutableUrl.contains("/websocket")) {
      // Do not duplicate '/' in path
      if (mutableUrl.last() != '/') {
        mutableUrl += "/"
      }

      // append "websocket" to the path
      mutableUrl += "websocket"
    }

    // Store the endpoint before changing the protocol
    this.endpoint = mutableUrl

    // Silently replace web socket URLs with HTTP URLs.
    if (url.regionMatches(0, "ws:", 0, 3, ignoreCase = true)) {
      mutableUrl = "http:" + url.substring(3)
    } else if (url.regionMatches(0, "wss:", 0, 4, ignoreCase = true)) {
      mutableUrl = "https:" + url.substring(4)
    }

    // If there are query params, append them now
    var httpUrl = HttpUrl.parse(mutableUrl) ?: throw IllegalArgumentException("invalid url: $url")
    params?.let {
      val httpBuilder = httpUrl.newBuilder()
      it.forEach { (key, value) ->
        httpBuilder.addQueryParameter(key, value.toString())
      }

      httpUrl = httpBuilder.build()
    }

    // Store the URL that will be used to establish a connection
    this.endpointUrl = httpUrl.url()

    // Create reconnect timer
    this.reconnectTimer = TimeoutTimer(
        dispatchQueue = dispatchQueue,
        timerCalculation = reconnectAfterMs,
        callback = {
          this.logItems("Socket attempting to reconnect")
          this.teardown { this.connect() }
        })
  }

  //------------------------------------------------------------------------------
  // Public Properties
  //------------------------------------------------------------------------------
  /** @return The socket protocol being used. e.g. "wss", "ws" */
  val protocol: String
    get() = when (endpointUrl.protocol) {
      "https" -> "wss"
      "http" -> "ws"
      else -> endpointUrl.protocol
    }

  /** @return True if the connection exists and is open */
  val isConnected: Boolean
    get() = this.connection?.readyState == Transport.ReadyState.OPEN

  //------------------------------------------------------------------------------
  // Public
  //------------------------------------------------------------------------------
  fun connect() {
    // Do not attempt to connect if already connected
    if (isConnected) return

    // Reset the clean close flag when attempting to connect
    this.closeWasClean = false

    this.connection = this.transport(endpointUrl)
    this.connection?.onOpen = { onConnectionOpened() }
    this.connection?.onClose = { code -> onConnectionClosed(code) }
    this.connection?.onError = { t, r -> onConnectionError(t, r) }
    this.connection?.onMessage = { m -> onConnectionMessage(m) }
    this.connection?.connect()
  }

  fun disconnect(
    code: Int = WS_CLOSE_NORMAL,
    reason: String? = null,
    callback: (() -> Unit)? = null
  ) {
    // The socket was closed cleanly by the User
    this.closeWasClean = true

    // Reset any reconnects and teardown the socket connection
    this.reconnectTimer.reset()
    this.teardown(code, reason, callback)

  }

  fun onOpen(callback: (() -> Unit)) {
    this.stateChangeCallbacks.onOpen(callback)
  }

  fun onClose(callback: () -> Unit) {
    this.stateChangeCallbacks.onClose(callback)
  }

  fun onError(callback: (Throwable, Response?) -> Unit) {
    this.stateChangeCallbacks.onError(callback)
  }

  fun onMessage(callback: (Message) -> Unit) {
    this.stateChangeCallbacks.onMessage(callback)
  }

  fun removeAllCallbacks() {
    this.stateChangeCallbacks.release()
  }

  fun channel(topic: String, params: Payload = mapOf()): Channel {
    val channel = Channel(topic, params, this)
    this.channels = this.channels.copyAndAdd(channel)

    return channel
  }

  fun remove(channel: Channel) {
    // To avoid a ConcurrentModificationException, filter out the channels to be
    // removed instead of calling .remove() on the list, thus returning a new list
    // that does not contain the channel that was removed.
    this.channels = channels
        .filter { it.joinRef != channel.joinRef }
  }

  //------------------------------------------------------------------------------
  // Internal
  //------------------------------------------------------------------------------
  internal fun push(
    topic: String,
    event: String,
    payload: Payload,
    ref: String? = null,
    joinRef: String? = null
  ) {

    val callback: (() -> Unit) = {
      val body = mutableMapOf<String, Any>()
      body["topic"] = topic
      body["event"] = event
      body["payload"] = payload

      ref?.let { body["ref"] = it }
      joinRef?.let { body["join_ref"] = it }

      val data = gson.toJson(body)
      connection?.let { transport ->
        this.logItems("Push: Sending $data")
        transport.send(data)
      }
    }

    if (isConnected) {
      // If the socket is connected, then execute the callback immediately.
      callback.invoke()
    } else {
      // If the socket is not connected, add the push to a buffer which will
      // be sent immediately upon connection.
      sendBuffer.add(callback)
    }
  }

  /** @return the next message ref, accounting for overflows */
  internal fun makeRef(): String {
    this.ref = if (ref == Int.MAX_VALUE) 0 else ref + 1
    return ref.toString()
  }

  fun logItems(body: String) {
    logger?.invoke(body)
  }

  //------------------------------------------------------------------------------
  // Private
  //------------------------------------------------------------------------------
  private fun teardown(
    code: Int = WS_CLOSE_NORMAL,
    reason: String? = null,
    callback: (() -> Unit)? = null
  ) {
    // Disconnect the transport
    this.connection?.onClose = null
    this.connection?.disconnect(code, reason)
    this.connection = null

    // Heartbeats are no longer needed
    this.heartbeatTask?.cancel()
    this.heartbeatTask = null

    // Since the connections onClose was null'd out, inform all state callbacks
    // that the Socket has closed
    this.stateChangeCallbacks.close.forEach { it.invoke() }
    callback?.invoke()
  }

  /** Triggers an error event to all connected Channels */
  private fun triggerChannelError() {
    this.channels.forEach { channel ->
      // Only trigger a channel error if it is in an "opened" state
      if (!(channel.isErrored || channel.isLeaving || channel.isClosed)) {
        channel.trigger(Channel.Event.ERROR.value)
      }
    }
  }

  /** Send all messages that were buffered before the socket opened */
  internal fun flushSendBuffer() {
    if (isConnected && sendBuffer.isNotEmpty()) {
      this.sendBuffer.forEach { it.invoke() }
      this.sendBuffer.clear()
    }
  }

  //------------------------------------------------------------------------------
  // Heartbeat
  //------------------------------------------------------------------------------
  internal fun resetHeartbeat() {
    // Clear anything related to the previous heartbeat
    this.pendingHeartbeatRef = null
    this.heartbeatTask?.cancel()
    this.heartbeatTask = null

    // Do not start up the heartbeat timer if skipHeartbeat is true
    if (skipHeartbeat) return
    val delay = heartbeatIntervalMs
    val period = heartbeatIntervalMs

    heartbeatTask =
        dispatchQueue.queueAtFixedRate(delay, period, TimeUnit.MILLISECONDS) { sendHeartbeat() }
  }

  internal fun sendHeartbeat() {
    // Do not send if the connection is closed
    if (!isConnected) return

    // If there is a pending heartbeat ref, then the last heartbeat was
    // never acknowledged by the server. Close the connection and attempt
    // to reconnect.
    pendingHeartbeatRef?.let {
      pendingHeartbeatRef = null
      logItems("Transport: Heartbeat timeout. Attempt to re-establish connection")

      // Close the socket, flagging the closure as abnormal
      this.abnormalClose("heartbeat timeout")
      return
    }

    // The last heartbeat was acknowledged by the server. Send another one
    this.pendingHeartbeatRef = this.makeRef()
    this.push(
        topic = "phoenix",
        event = Channel.Event.HEARTBEAT.value,
        payload = mapOf(),
        ref = pendingHeartbeatRef)
  }

  private fun abnormalClose(reason: String) {
    this.closeWasClean = false

    /*
      We use NORMAL here since the client is the one determining to close the connection. However,
      we keep a flag `closeWasClean` set to false so that the client knows that it should attempt
      to reconnect.
     */
    this.connection?.disconnect(WS_CLOSE_NORMAL, reason)
  }

  //------------------------------------------------------------------------------
  // Connection Transport Hooks
  //------------------------------------------------------------------------------
  internal fun onConnectionOpened() {
    this.logItems("Transport: Connected to $endpoint")

    // Reset the closeWasClean flag now that the socket has been connected
    this.closeWasClean = false

    // Send any messages that were waiting for a connection
    this.flushSendBuffer()

    // Reset how the socket tried to reconnect
    this.reconnectTimer.reset()

    // Restart the heartbeat timer
    this.resetHeartbeat()

    // Inform all onOpen callbacks that the Socket has opened
    this.stateChangeCallbacks.open.forEach { it.invoke() }
  }

  internal fun onConnectionClosed(code: Int) {
    this.logItems("Transport: close")
    this.triggerChannelError()

    // Prevent the heartbeat from triggering if the socket closed
    this.heartbeatTask?.cancel()
    this.heartbeatTask = null

    // Only attempt to reconnect if the socket did not close normally
    if (!this.closeWasClean) {
      this.reconnectTimer.scheduleTimeout()
    }

    // Inform callbacks the socket closed
    this.stateChangeCallbacks.close.forEach { it.invoke() }
  }

  internal fun onConnectionMessage(rawMessage: String) {
    this.logItems("Receive: $rawMessage")

    // Parse the message as JSON
    val message = gson.fromJson(rawMessage, Message::class.java)

    // Clear heartbeat ref, preventing a heartbeat timeout disconnect
    if (message.ref == pendingHeartbeatRef) pendingHeartbeatRef = null

    // Dispatch the message to all channels that belong to the topic
    this.channels
        .filter { it.isMember(message) }
        .forEach { it.trigger(message) }

    // Inform all onMessage callbacks of the message
    this.stateChangeCallbacks.message.forEach { it.invoke(message) }
  }

  internal fun onConnectionError(t: Throwable, response: Response?) {
    this.logItems("Transport: error $t")

    // Send an error to all channels
    this.triggerChannelError()

    // Inform any state callbacks of the error
    this.stateChangeCallbacks.error.forEach { it.invoke(t, response) }
  }

}