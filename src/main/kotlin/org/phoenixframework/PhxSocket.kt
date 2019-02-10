package org.phoenixframework

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URL
import java.util.Timer
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

typealias Payload = Map<String, Any>

/** Default timeout set to 10s */
const val DEFAULT_TIMEOUT: Long = 10000

/** Default heartbeat interval set to 30s */
const val DEFAULT_HEARTBEAT: Long = 30000

open class PhxSocket(
        url: String,
        params: Payload? = null,
        private val client: OkHttpClient = OkHttpClient.Builder().build()
) : WebSocketListener() {

    //------------------------------------------------------------------------------
    // Public Attributes
    //------------------------------------------------------------------------------
    /** Timeout to use when opening connections */
    var timeout: Long = DEFAULT_TIMEOUT

    /** Interval between sending a heartbeat */
    var heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT

    /** Interval between socket reconnect attempts */
    var reconnectAfterMs: ((tries: Int) -> Long) = closure@{
        return@closure if (it >= 3) 100000 else longArrayOf(1000, 2000, 5000)[it]
    }

    /** Hook for custom logging into the client */
    var logger: ((msg: String) -> Unit)? = null

    /** Disable sending Heartbeats by setting to true */
    var skipHeartbeat: Boolean = false

    /**
     * Socket will attempt to reconnect if the Socket was closed. Will not
     * reconnect if the Socket errored (e.g. connection refused.) Default
     * is set to true
     */
    var autoReconnect: Boolean = true


    //------------------------------------------------------------------------------
    // Private Attributes
    //------------------------------------------------------------------------------
    /// Collection of callbacks for onOpen socket events
    private var onOpenCallbacks: MutableList<() -> Unit> = ArrayList()

    /// Collection of callbacks for onClose socket events
    private var onCloseCallbacks: MutableList<() -> Unit> = ArrayList()

    /// Collection of callbacks for onError socket events
    private var onErrorCallbacks: MutableList<(Throwable, Response?) -> Unit> = ArrayList()

    /// Collection of callbacks for onMessage socket events
    private var onMessageCallbacks: MutableList<(PhxMessage) -> Unit> = ArrayList()

    /// Collection on channels created for the Socket
    private var channels: MutableList<PhxChannel> = ArrayList()

    /// Buffers messages that need to be sent once the socket has connected
    private var sendBuffer: MutableList<() -> Unit> = ArrayList()

    /// Ref counter for messages
    private var ref: Int = 0

    /// Internal endpoint that the Socket is connecting to
    var endpoint: URL

    /// Timer that triggers sending new Heartbeat messages
    private var heartbeatTimer: Timer? = null

    /// Ref counter for the last heartbeat that was sent
    private var pendingHeartbeatRef: String? = null

    /// Timer to use when attempting to reconnect
    private var reconnectTimer: PhxTimer? = null


    private val gson: Gson = GsonBuilder()
            .setLenient()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

    private val request: Request

    /// WebSocket connection to the server
    private var connection: WebSocket? = null


    init {

        // Silently replace web socket URLs with HTTP URLs.
        var mutableUrl = url
        if (url.regionMatches(0, "ws:", 0, 3, ignoreCase = true)) {
            mutableUrl = "http:" + url.substring(3)
        } else if (url.regionMatches(0, "wss:", 0, 4, ignoreCase = true)) {
            mutableUrl = "https:" + url.substring(4)
        }

        var httpUrl = HttpUrl.parse(mutableUrl) ?: throw IllegalArgumentException("invalid url: $url")

        // If there are query params, append them now
        params?.let {
            val httpBuilder = httpUrl.newBuilder()
            it.forEach { (key, value) ->
                httpBuilder.addQueryParameter(key, value.toString())
            }

            httpUrl = httpBuilder.build()
        }

        reconnectTimer = PhxTimer(
            callback = {
                disconnect().also {
                    connect()
                }
            },
            timerCalculation = reconnectAfterMs
        )

        // Hold reference to where the Socket is pointing to
        this.endpoint = httpUrl.url()

        // Create the request and client that will be used to connect to the WebSocket
        request = Request.Builder().url(httpUrl).build()
    }


    //------------------------------------------------------------------------------
    // Public
    //------------------------------------------------------------------------------
    /** True if the Socket is currently connected */
    val isConnected: Boolean
        get() = connection != null

    /**
     * Disconnects the Socket
     */
    fun disconnect() {
        connection?.close(1000, null)
        connection = null

    }

    /**
     * Connects the Socket. The params passed to the Socket on initialization
     * will be sent through the connection. If the Socket is already connected,
     * then this call will be ignored.
     */
    fun connect() {
        // Do not attempt to reconnect if already connected
        if (isConnected) return
        connection = client.newWebSocket(request, this)
    }

    /**
     * Registers a callback for connection open events
     *
     * Example:
     *  socket.onOpen {
     *      print("Socket Connection Opened")
     *  }
     *
     * @param callback: Callback to register
     */
    fun onOpen(callback: () -> Unit) {
        this.onOpenCallbacks.add(callback)
    }


    /**
     * Registers a callback for connection close events
     *
     *  Example:
     *      socket.onClose {
     *          print("Socket Connection Closed")
     *      }
     *
     * @param callback: Callback to register
     */
    fun onClose(callback: () -> Unit) {
        this.onCloseCallbacks.add(callback)
    }

    /**
     * Registers a callback for connection error events
     *
     * Example:
     *     socket.onError { error, response ->
     *         print("Socket Connection Error")
     *     }
     *
     * @param callback: Callback to register
     */
    fun onError(callback: (Throwable?, Response?) -> Unit) {
        this.onErrorCallbacks.add(callback)
    }

    /**
     * Registers a callback for connection message events
     *
     * Example:
     *     socket.onMessage { [unowned self] (message) in
     *         print("Socket Connection Message")
     *     }
     *
     * @param callback: Callback to register
     */
    fun onMessage(callback: (PhxMessage) -> Unit) {
        this.onMessageCallbacks.add(callback)
    }


    /**
     * Releases all stored callback hooks (onError, onOpen, onClose, etc.) You should
     * call this method when you are finished when the Socket in order to release
     * any references held by the socket.
     */
    fun removeAllCallbacks() {
        this.onOpenCallbacks.clear()
        this.onCloseCallbacks.clear()
        this.onErrorCallbacks.clear()
        this.onMessageCallbacks.clear()
    }

    /**
     * Removes the Channel from the socket. This does not cause the channel to inform
     * the server that it is leaving so you should call channel.leave() first.
     */
    fun remove(channel: PhxChannel) {
        this.channels = channels
                .filter { it.joinRef != channel.joinRef }
                .toMutableList()
    }

    /**
     * Initializes a new Channel with the given topic
     *
     * Example:
     *  val channel = socket.channel("rooms", params)
     */
    fun channel(topic: String, params: Payload? = null): PhxChannel {
        val channel = PhxChannel(topic, params ?: HashMap(), this)
        this.channels.add(channel)
        return channel
    }

    /**
     * Sends data through the Socket
     */
    open fun push(topic: String,
                  event: String,
                  payload: Payload,
                  ref: String? = null,
                  joinRef: String? = null) {

        val callback: (() -> Unit) = {
            val body: MutableMap<String, Any> = HashMap()
            body["topic"] = topic
            body["event"] = event
            body["payload"] = payload

            ref?.let { body["ref"] = it }
            joinRef?.let { body["join_ref"] = it }

            val data = gson.toJson(body)
            connection?.let {
                this.logItems("Push: Sending $data")
                it.send(data)
            }
        }

        // If the socket is connected, then execute the callback immediately
        if (isConnected) {
            callback()
        } else {
            // If the socket is not connected, add the push to a buffer which
            // will be sent immediately upon connection
            this.sendBuffer.add(callback)
        }
    }


    /**
     * @return the next message ref, accounting for overflows
     */
    open fun makeRef(): String {
        val newRef = this.ref + 1
        this.ref = if (newRef == Int.MAX_VALUE) 0 else newRef

        return newRef.toString()
    }

    //------------------------------------------------------------------------------
    // Internal
    //------------------------------------------------------------------------------
    fun logItems(body: String) {
        logger?.let {
            it(body)
        }
    }


    //------------------------------------------------------------------------------
    // Private
    //------------------------------------------------------------------------------

    /** Triggers a message when the socket is opened */
    private fun onConnectionOpened() {
        this.logItems("Transport: Connected to $endpoint")
        this.flushSendBuffer()
        this.reconnectTimer?.reset()

        // start sending heartbeats if enabled {
        if (!skipHeartbeat) startHeartbeatTimer()

        // Inform all onOpen callbacks that the Socket as opened
        this.onOpenCallbacks.forEach { it() }
    }

    /** Triggers a message when the socket is closed */
    private fun onConnectionClosed() {
        this.logItems("Transport: close")
        this.triggerChannelError()

        // Terminate any ongoing heartbeats
        this.heartbeatTimer?.cancel()

        // Attempt to reconnect the socket
        if (autoReconnect) reconnectTimer?.scheduleTimeout()

        // Inform all onClose callbacks that the Socket closed
        this.onCloseCallbacks.forEach { it() }
    }

    /** Triggers a message when an error comes through the Socket */
    private fun onConnectionError(t: Throwable, response: Response?) {
        this.logItems("Transport: error")

        // Inform all onError callbacks that an error occurred
        this.onErrorCallbacks.forEach { it(t, response) }

        // Inform all channels that a socket error occurred
        this.triggerChannelError()

        // There was an error, violently cancel the connection. This is a safe operation
        // since the underlying WebSocket will no longer return messages to the Connection
        // after a Failure
        connection?.cancel()
        connection = null
    }

    /** Triggers a message to the correct Channel when it comes through the Socket */
    private fun onConnectionMessage(rawMessage: String) {
        this.logItems("Receive: $rawMessage")

        val message = gson.fromJson(rawMessage, PhxMessage::class.java)

        // Dispatch the message to all channels that belong to the topic
        this.channels
                .filter { it.isMember(message) }
                .forEach { it.trigger(message) }

        // Inform all onMessage callbacks of the message
        this.onMessageCallbacks.forEach { it(message) }

        // Check if this message was a pending heartbeat
        if (message.ref == pendingHeartbeatRef) {
            this.logItems("Received Pending Heartbeat")
            this.pendingHeartbeatRef = null
        }
    }

    /** Triggers an error event to all connected Channels */
    private fun triggerChannelError() {
        val errorMessage = PhxMessage(event = PhxChannel.PhxEvent.ERROR.value)
        this.channels.forEach { it.trigger(errorMessage) }
    }

    /** Send all messages that were buffered before the socket opened */
    private fun flushSendBuffer() {
        if (isConnected && sendBuffer.count() > 0) {
            this.sendBuffer.forEach { it() }
            this.sendBuffer.clear()
        }
    }


    //------------------------------------------------------------------------------
    // Timers
    //------------------------------------------------------------------------------
    /** Initializes a 30s */
    fun startHeartbeatTimer() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null;

        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(heartbeatIntervalMs, heartbeatIntervalMs) {
            if (!isConnected) return@schedule

            pendingHeartbeatRef?.let {
                pendingHeartbeatRef = null
                logItems("Transport: Heartbeat timeout. Attempt to re-establish connection")
                disconnect()
                return@schedule
            }

            pendingHeartbeatRef = makeRef()
            push("phoenix", PhxChannel.PhxEvent.HEARTBEAT.value, HashMap(), pendingHeartbeatRef)
        }
    }


    //------------------------------------------------------------------------------
    // WebSocketListener
    //------------------------------------------------------------------------------
    override fun onOpen(webSocket: WebSocket?, response: Response?) {
        this.onConnectionOpened()

    }

    override fun onMessage(webSocket: WebSocket?, text: String?) {
        text?.let {
            this.onConnectionMessage(it)
        }
    }

    override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
        this.onConnectionClosed()
    }

    override fun onFailure(webSocket: WebSocket?, t: Throwable, response: Response?) {
        this.onConnectionError(t, response)
    }
}
