package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.net.URL
import java.util.concurrent.TimeUnit

class SocketTest {

  @Mock lateinit var okHttpClient: OkHttpClient
  @Mock lateinit var mockDispatchQueue: DispatchQueue

  lateinit var connection: Transport
  lateinit var socket: Socket

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    connection = spy(WebSocketTransport(URL("https://localhost:4000/socket"), okHttpClient))

    socket = Socket("wss://localhost:4000/socket")
    socket.transport = { connection }
    socket.dispatchQueue = mockDispatchQueue

  }

  /* constructor */
  @Test
  fun `constructor sets defaults`() {
    val socket = Socket("wss://localhost:4000/socket")

    assertThat(socket.params).isNull()
    assertThat(socket.channels).isEmpty()
    assertThat(socket.sendBuffer).isEmpty()
    assertThat(socket.ref).isEqualTo(0)
    assertThat(socket.endpoint).isEqualTo("wss://localhost:4000/socket/websocket")
    assertThat(socket.stateChangeCallbacks.open).isEmpty()
    assertThat(socket.stateChangeCallbacks.close).isEmpty()
    assertThat(socket.stateChangeCallbacks.error).isEmpty()
    assertThat(socket.stateChangeCallbacks.message).isEmpty()
    assertThat(socket.timeout).isEqualTo(Defaults.TIMEOUT)
    assertThat(socket.heartbeatInterval).isEqualTo(Defaults.HEARTBEAT)
    assertThat(socket.logger).isNull()
    assertThat(socket.reconnectAfterMs(1)).isEqualTo(1000)
    assertThat(socket.reconnectAfterMs(2)).isEqualTo(2000)
    assertThat(socket.reconnectAfterMs(3)).isEqualTo(5000)
    assertThat(socket.reconnectAfterMs(4)).isEqualTo(10000)
    assertThat(socket.reconnectAfterMs(5)).isEqualTo(10000)
  }

  @Test
  fun `constructor overrides some defaults`() {
    val socket = Socket("wss://localhost:4000/socket/", mapOf("one" to 2))
    socket.timeout = 40_000
    socket.heartbeatInterval = 60_000
    socket.logger = { }
    socket.reconnectAfterMs = { 10 }

    assertThat(socket.params).isEqualTo(mapOf("one" to 2))
    assertThat(socket.endpoint).isEqualTo("wss://localhost:4000/socket/websocket")
    assertThat(socket.timeout).isEqualTo(40_000)
    assertThat(socket.heartbeatInterval).isEqualTo(60_000)
    assertThat(socket.logger).isNotNull()
    assertThat(socket.reconnectAfterMs(1)).isEqualTo(10)
    assertThat(socket.reconnectAfterMs(2)).isEqualTo(10)
  }

  @Test
  fun `constructor constructs with a valid URL`() {
    // Test different schemes
    assertThat(Socket("http://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket")

    assertThat(Socket("https://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("https://localhost:4000/socket/websocket")

    assertThat(Socket("ws://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket")

    assertThat(Socket("wss://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("https://localhost:4000/socket/websocket")

    // test params
    val singleParam = hashMapOf("token" to "abc123")
    assertThat(Socket("ws://localhost:4000/socket/websocket", singleParam).endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket?token=abc123")

    val multipleParams = hashMapOf("token" to "abc123", "user_id" to 1)
    assertThat(
        Socket("http://localhost:4000/socket/websocket", multipleParams).endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket?user_id=1&token=abc123")

    // test params with spaces
    val spacesParams = hashMapOf("token" to "abc 123", "user_id" to 1)
    assertThat(Socket("wss://localhost:4000/socket/websocket", spacesParams).endpointUrl.toString())
        .isEqualTo("https://localhost:4000/socket/websocket?user_id=1&token=abc%20123")
  }


  /* protocol */
  @Test
  fun `protocol returns wss when protocol is https`() {
    val socket = Socket("https://example.com/")
    assertThat(socket.protocol).isEqualTo("wss")
  }

  @Test
  fun `protocol returns ws when protocol is http`() {
    val socket = Socket("http://example.com/")
    assertThat(socket.protocol).isEqualTo("ws")
  }

  @Test
  fun `protocol returns value if not https or http`() {
    val socket = Socket("wss://example.com/")
    assertThat(socket.protocol).isEqualTo("wss")
  }

  /* isConnected */
  @Test
  fun `isConnected returns false if connection is null`() {
    assertThat(socket.isConnected).isFalse()
  }

  @Test
  fun `isConnected is false if state is not open`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.CLOSING)

    socket.connection = connection
    assertThat(socket.isConnected).isFalse()
  }

  @Test
  fun `isConnected is true if state open`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

    socket.connection = connection
    assertThat(socket.isConnected).isTrue()
  }


  /* connect() */
  @Test
  fun `connect() establishes websocket connection with endpoint`() {
    socket.connect()
    assertThat(socket.connection).isNotNull()
  }

  @Test
  fun `connect() sets callbacks for connection`() {
    var open = 0
    socket.onOpen { open += 1 }

    var close = 0
    socket.onClose { close += 1 }

    var lastError: Throwable? = null
    var lastResponse: Response? = null
    socket.onError { throwable, response ->
      lastError = throwable
      lastResponse = response
    }

    var lastMessage: Message? = null
    socket.onMessage { lastMessage = it }

    socket.connect()

    socket.connection?.onOpen?.invoke()
    assertThat(open).isEqualTo(1)

    socket.connection?.onClose?.invoke(1000)
    assertThat(close).isEqualTo(1)

    socket.connection?.onError?.invoke(Throwable(), null)
    assertThat(lastError).isNotNull()
    assertThat(lastResponse).isNull()

    val data = mapOf(
        "topic" to "topic",
        "event" to "event",
        "payload" to mapOf("go" to true),
        "status" to "status"
    )

    val json = Defaults.gson.toJson(data)
    socket.connection?.onMessage?.invoke(json)
    assertThat(lastMessage?.payload).isEqualTo(mapOf("go" to true))
  }

  @Test
  fun `connect() removes callbacks`() {
    var open = 0
    socket.onOpen { open += 1 }

    var close = 0
    socket.onClose { close += 1 }

    var lastError: Throwable? = null
    var lastResponse: Response? = null
    socket.onError { throwable, response ->
      lastError = throwable
      lastResponse = response
    }

    var lastMessage: Message? = null
    socket.onMessage { lastMessage = it }

    socket.removeAllCallbacks()
    socket.connect()

    socket.connection?.onOpen?.invoke()
    assertThat(open).isEqualTo(0)

    socket.connection?.onClose?.invoke(1000)
    assertThat(close).isEqualTo(0)

    socket.connection?.onError?.invoke(Throwable(), null)
    assertThat(lastError).isNull()
    assertThat(lastResponse).isNull()

    val data = mapOf(
        "topic" to "topic",
        "event" to "event",
        "payload" to mapOf("go" to true),
        "status" to "status"
    )

    val json = Defaults.gson.toJson(data)
    socket.connection?.onMessage?.invoke(json)
    assertThat(lastMessage?.payload).isNull()
  }

  @Test
  fun `connect() does not connect if already connected`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
    socket.connect()
    socket.connect()

    verify(connection, times(1)).connect()
  }

  /* disconnect */
  @Test
  fun `disconnect() removes existing connection`() {
    socket.connect()
    socket.disconnect()

    assertThat(socket.connection).isNull()
    verify(connection).disconnect(WS_CLOSE_NORMAL)
  }

  @Test
  fun `disconnect() calls callback`() {
    val mockCallback = mock<() -> Unit> {}

    socket.disconnect(callback = mockCallback)
    verify(mockCallback).invoke()
  }

  @Test
  fun `disconnect() calls connection close callback`() {
    socket.connect()
    socket.disconnect(10, "reason")
    verify(connection).disconnect(10, "reason")
  }

  @Test
  fun `disconnect() resets reconnect timer`() {
    val mockTimer = mock<TimeoutTimer>()
    socket.reconnectTimer = mockTimer

    socket.disconnect()
    verify(mockTimer).reset()
  }

  @Test
  fun `disconnect() cancels and releases heartbeat timer`() {
    val mockTask = mock<DispatchWorkItem>()
    socket.heartbeatTask = mockTask

    socket.disconnect()
    verify(mockTask).cancel()
    assertThat(socket.heartbeatTask).isNull()
  }

  @Test
  fun `disconnect() does nothing if not connected`() {
    socket.disconnect()
    verifyZeroInteractions(connection)
  }

  /* channel */
  @Test
  fun `channel() returns channel with given topic and params`() {
    val channel = socket.channel("topic", mapOf("one" to "two"))

    assertThat(channel.socket).isEqualTo(socket)
    assertThat(channel.topic).isEqualTo("topic")
    assertThat(channel.params["one"]).isEqualTo("two")
  }

  @Test
  fun `channel() adds channel to socket's channel list`() {
    assertThat(socket.channels).isEmpty()

    val channel = socket.channel("topic", mapOf("one" to "two"))

    assertThat(socket.channels).hasSize(1)
    assertThat(socket.channels.first()).isEqualTo(channel)
  }

  @Test
  fun `remove() removes given channel from channels`() {
    val channel1 = socket.channel("topic-1")
    val channel2 = socket.channel("topic-2")

    channel1.joinPush.ref = "1"
    channel2.joinPush.ref = "2"

    socket.remove(channel1)
    assertThat(socket.channels).doesNotContain(channel1)
    assertThat(socket.channels).contains(channel2)
  }

  /* push */
  @Test
  fun `push() sends data to connection when connected`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

    socket.connect()
    socket.push("topic", "event", mapOf("one" to "two"), "ref", "join-ref")

    val expect =
        "{\"topic\":\"topic\",\"event\":\"event\",\"payload\":{\"one\":\"two\"},\"ref\":\"ref\",\"join_ref\":\"join-ref\"}"
    verify(connection).send(expect)
  }

  @Test
  fun `push() excludes ref information if not passed`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

    socket.connect()
    socket.push("topic", "event", mapOf("one" to "two"))

    val expect = "{\"topic\":\"topic\",\"event\":\"event\",\"payload\":{\"one\":\"two\"}}"
    verify(connection).send(expect)
  }

  @Test
  fun `push() buffers data when not connected`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.CLOSED)
    socket.connect()

    socket.push("topic", "event1", mapOf("one" to "two"))
    verify(connection, never()).send(any())
    assertThat(socket.sendBuffer).hasSize(1)

    socket.push("topic", "event2", mapOf("one" to "two"))
    verify(connection, never()).send(any())
    assertThat(socket.sendBuffer).hasSize(2)

    socket.sendBuffer.forEach { it.invoke() }
    verify(connection, times(2)).send(any())
  }

  /* makeRef */
  @Test
  fun `makeRef() returns next message ref`() {
    assertThat(socket.ref).isEqualTo(0)
    assertThat(socket.makeRef()).isEqualTo("1")
    assertThat(socket.ref).isEqualTo(1)
    assertThat(socket.makeRef()).isEqualTo("2")
    assertThat(socket.ref).isEqualTo(2)
  }

  @Test
  fun `makeRef() resets to 0 if it hits max int`() {
    socket.ref = Int.MAX_VALUE

    assertThat(socket.makeRef()).isEqualTo("0")
    assertThat(socket.ref).isEqualTo(0)
  }

  /* sendHeartbeat */
  @Test
  fun `sendHeartbeat() closes socket when heartbeat is not ack'd within heartbeat window`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
    socket.connect()

    socket.sendHeartbeat()
    verify(connection, never()).disconnect(any(), any())
    assertThat(socket.pendingHeartbeatRef).isNotNull()

    socket.sendHeartbeat()
    verify(connection).disconnect(WS_CLOSE_NORMAL, "Heartbeat timed out")
    assertThat(socket.pendingHeartbeatRef).isNull()
  }

  @Test
  fun `sendHeartbeat() pushes heartbeat data when connected`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
    socket.connect()

    socket.sendHeartbeat()

    val expected = "{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"1\"}"
    assertThat(socket.pendingHeartbeatRef).isEqualTo(socket.ref.toString())
    verify(connection).send(expected)
  }

  @Test
  fun `sendHeartbeat() does nothing when not connected`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.CLOSED)
    socket.connect()
    socket.sendHeartbeat()

    verify(connection, never()).disconnect(any(), any())
    verify(connection, never()).send(any())
  }

  /* flushSendBuffer */
  @Test
  fun `flushSendBuffer() invokes callbacks in buffer when connected`() {
    var oneCalled = 0
    socket.sendBuffer.add { oneCalled += 1 }
    var twoCalled = 0
    socket.sendBuffer.add { twoCalled += 1 }
    val threeCalled = 0

    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

    // does nothing if not connected
    socket.flushSendBuffer()
    assertThat(oneCalled).isEqualTo(0)

    // connect
    socket.connect()

    // sends once connected
    socket.flushSendBuffer()
    assertThat(oneCalled).isEqualTo(1)
    assertThat(twoCalled).isEqualTo(1)
    assertThat(threeCalled).isEqualTo(0)
  }

  @Test
  fun `flushSendBuffer() empties send buffer`() {
    socket.sendBuffer.add { }

    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
    socket.connect()

    assertThat(socket.sendBuffer).isNotEmpty()
    socket.flushSendBuffer()

    assertThat(socket.sendBuffer).isEmpty()
  }

  /* onConnectionOpen */
  @Test
  fun `onConnectionOpened() flushes the send buffer`() {
    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
    socket.connect()

    var oneCalled = 0
    socket.sendBuffer.add { oneCalled += 1 }

    socket.onConnectionOpened()
    assertThat(oneCalled).isEqualTo(1)
    assertThat(socket.sendBuffer).isEmpty()
  }

  @Test
  fun `onConnectionOpened() resets reconnect timer`() {
    val mockTimer = mock<TimeoutTimer>()
    socket.reconnectTimer = mockTimer

    socket.onConnectionOpened()
    verify(mockTimer).reset()
  }

  @Test
  fun `onConnectionOpened() resets the heartbeat`() {
    val mockTask = mock<DispatchWorkItem>()
    socket.heartbeatTask = mockTask

    socket.onConnectionOpened()
    verify(mockTask).cancel()
    verify(mockDispatchQueue).queue(any(), any(), any())
  }

  @Test
  fun `onConnectionOpened() invokes all onOpen callbacks`() {
    var oneCalled = 0
    socket.onOpen { oneCalled += 1 }
    var twoCalled = 0
    socket.onOpen { twoCalled += 1 }
    var threeCalled = 0
    socket.onClose { threeCalled += 1 }

    socket.onConnectionOpened()
    assertThat(oneCalled).isEqualTo(1)
    assertThat(twoCalled).isEqualTo(1)
    assertThat(threeCalled).isEqualTo(0)
  }

  /* resetHeartbeat */
  @Test
  fun `resetHeartbeat() clears any pending heartbeat`() {
    socket.pendingHeartbeatRef = "1"
    socket.resetHeartbeat()

    assertThat(socket.pendingHeartbeatRef).isNull()
  }

  @Test
  fun `resetHeartbeat() does not schedule heartbeat if skipHeartbeat == true`() {
    socket.skipHeartbeat = true
    socket.resetHeartbeat()

    verifyZeroInteractions(mockDispatchQueue)
  }

  @Test
  fun `resetHeartbeat() creates a future heartbeat task`() {
    val mockTask = mock<DispatchWorkItem>()
    whenever(mockDispatchQueue.queue(any(), any(), any())).thenReturn(mockTask)

    whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
    socket.connect()
    socket.heartbeatInterval = 5_000

    assertThat(socket.heartbeatTask).isNull()
    socket.resetHeartbeat()

    assertThat(socket.heartbeatTask).isNotNull()
    argumentCaptor<() -> Unit> {
      verify(mockDispatchQueue).queue(eq(5_000L), eq(TimeUnit.MILLISECONDS), capture())

      // fire the task
      allValues.first().invoke()

      val expected = "{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"1\"}"
      verify(connection).send(expected)
    }
  }

  /* onConnectionClosed */
  @Test
  fun `onConnectionClosed() it does not schedule reconnectTimer timeout if normal close`() {
    val mockTimer = mock<TimeoutTimer>()
    socket.reconnectTimer = mockTimer

    socket.onConnectionClosed(WS_CLOSE_NORMAL)
    verify(mockTimer, never()).scheduleTimeout()
  }

  @Test
  fun `onConnectionClosed schedules reconnectTimer if not normal close`() {
    val mockTimer = mock<TimeoutTimer>()
    socket.reconnectTimer = mockTimer

    socket.onConnectionClosed(1001)
    verify(mockTimer).scheduleTimeout()
  }

  @Test
  fun `onConnectionClosed() cancels heartbeat task`() {
    val mockTask = mock<DispatchWorkItem>()
    socket.heartbeatTask = mockTask

    socket.onConnectionClosed(1000)
    verify(mockTask).cancel()
    assertThat(socket.heartbeatTask).isNull()
  }

  @Test
  fun `onConnectionClosed() triggers onClose callbacks`() {
    var oneCalled = 0
    socket.onClose { oneCalled += 1 }
    var twoCalled = 0
    socket.onClose { twoCalled += 1 }
    var threeCalled = 0
    socket.onOpen { threeCalled += 1 }

    socket.onConnectionClosed(1000)
    assertThat(oneCalled).isEqualTo(1)
    assertThat(twoCalled).isEqualTo(1)
    assertThat(threeCalled).isEqualTo(0)
  }

  @Test
  fun `onConnectionClosed() triggers channel error`() {
    val channel = mock<Channel>()
    socket.channels.add(channel)

    socket.onConnectionClosed(1001)
    verify(channel).trigger("phx_error")
  }

  /* onConnectionError */
  @Test
  fun `onConnectionError() triggers onClose callbacks`() {
    var oneCalled = 0
    socket.onError { _, _ -> oneCalled += 1 }
    var twoCalled = 0
    socket.onError { _, _ -> twoCalled += 1 }
    var threeCalled = 0
    socket.onOpen { threeCalled += 1 }

    socket.onConnectionError(Throwable(), null)
    assertThat(oneCalled).isEqualTo(1)
    assertThat(twoCalled).isEqualTo(1)
    assertThat(threeCalled).isEqualTo(0)
  }

  @Test
  fun `onConnectionError() triggers channel error`() {
    val channel = mock<Channel>()
    socket.channels.add(channel)

    socket.onConnectionError(Throwable(), null)
    verify(channel).trigger("phx_error")
  }

  @Test
  fun `onConnectionMessage() parses raw messages and triggers channel event`() {
    val targetChannel = mock<Channel>()
    whenever(targetChannel.isMember(any())).thenReturn(true)
    val otherChannel = mock<Channel>()
    whenever(otherChannel.isMember(any())).thenReturn(false)

    socket.channels.add(targetChannel)
    socket.channels.add(otherChannel)

    val rawMessage =
        "{\"topic\":\"topic\",\"event\":\"event\",\"payload\":{\"one\":\"two\"},\"status\":\"ok\"}"
    socket.onConnectionMessage(rawMessage)

    verify(targetChannel).trigger(message = any())
    verify(otherChannel, never()).trigger(message = any())
  }

  @Test
  fun `onConnectionMessage() invokes onMessage callbacks`() {
    var message: Message? = null
    socket.onMessage { message = it }

    val rawMessage =
        "{\"topic\":\"topic\",\"event\":\"event\",\"payload\":{\"one\":\"two\"},\"status\":\"ok\"}"
    socket.onConnectionMessage(rawMessage)

    assertThat(message?.topic).isEqualTo("topic")
    assertThat(message?.event).isEqualTo("event")
  }

  @Test
  fun `onConnectionMessage() clears pending heartbeat`() {
    socket.pendingHeartbeatRef = "5"

    val rawMessage =
        "{\"topic\":\"topic\",\"event\":\"event\",\"payload\":{\"one\":\"two\"},\"ref\":\"5\"}"
    socket.onConnectionMessage(rawMessage)
    assertThat(socket.pendingHeartbeatRef).isNull()
  }
}