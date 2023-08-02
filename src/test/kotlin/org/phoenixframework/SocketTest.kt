package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.net.URL
import java.util.concurrent.TimeUnit

class SocketTest {

  @Mock lateinit var okHttpClient: OkHttpClient
  @Mock lateinit var mockDispatchQueue: DispatchQueue

  lateinit var connection: Transport
  lateinit var socket: Socket

  @BeforeEach
  internal fun setUp() {
    MockitoAnnotations.initMocks(this)

    connection = spy(WebSocketTransport(URL("https://localhost:4000/socket"), okHttpClient))

    socket = Socket("wss://localhost:4000/socket")
    socket.transport = { connection }
    socket.dispatchQueue = mockDispatchQueue
  }

  @Nested
  @DisplayName("constructor")
  inner class Constructor {
    @Test
    internal fun `sets defaults`() {
      val socket = Socket("wss://localhost:4000/socket")

      assertThat(socket.paramsClosure.invoke()).isNull()
      assertThat(socket.channels).isEmpty()
      assertThat(socket.sendBuffer).isEmpty()
      assertThat(socket.ref).isEqualTo(0)
      assertThat(socket.endpoint).isEqualTo("wss://localhost:4000/socket/websocket")
      assertThat(socket.vsn).isEqualTo(Defaults.VSN)
      assertThat(socket.stateChangeCallbacks.open).isEmpty()
      assertThat(socket.stateChangeCallbacks.close).isEmpty()
      assertThat(socket.stateChangeCallbacks.error).isEmpty()
      assertThat(socket.stateChangeCallbacks.message).isEmpty()
      assertThat(socket.timeout).isEqualTo(Defaults.TIMEOUT)
      assertThat(socket.heartbeatIntervalMs).isEqualTo(Defaults.HEARTBEAT)
      assertThat(socket.logger).isNull()
      assertThat(socket.reconnectAfterMs(1)).isEqualTo(10)
      assertThat(socket.reconnectAfterMs(2)).isEqualTo(50)
      assertThat(socket.reconnectAfterMs(3)).isEqualTo(100)
      assertThat(socket.reconnectAfterMs(4)).isEqualTo(150)
      assertThat(socket.reconnectAfterMs(5)).isEqualTo(200)
      assertThat(socket.reconnectAfterMs(6)).isEqualTo(250)
      assertThat(socket.reconnectAfterMs(7)).isEqualTo(500)
      assertThat(socket.reconnectAfterMs(8)).isEqualTo(1_000)
      assertThat(socket.reconnectAfterMs(9)).isEqualTo(2_000)
      assertThat(socket.reconnectAfterMs(10)).isEqualTo(5_000)
      assertThat(socket.reconnectAfterMs(11)).isEqualTo(5_000)
    }

    @Test
    internal fun `overrides some defaults`() {
      val socket = Socket("wss://localhost:4000/socket/", mapOf("one" to 2))
      socket.timeout = 40_000
      socket.heartbeatIntervalMs = 60_000
      socket.logger = { }
      socket.reconnectAfterMs = { 10 }

      assertThat(socket.paramsClosure?.invoke()).isEqualTo(mapOf("one" to 2))
      assertThat(socket.endpoint).isEqualTo("wss://localhost:4000/socket/websocket")
      assertThat(socket.timeout).isEqualTo(40_000)
      assertThat(socket.heartbeatIntervalMs).isEqualTo(60_000)
      assertThat(socket.logger).isNotNull()
      assertThat(socket.reconnectAfterMs(1)).isEqualTo(10)
      assertThat(socket.reconnectAfterMs(2)).isEqualTo(10)
    }

    @Test
    internal fun `constructs with a valid URL`() {
      // Test different schemes
      assertThat(Socket("http://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket?vsn=2.0.0")

      assertThat(Socket("https://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("https://localhost:4000/socket/websocket?vsn=2.0.0")

      assertThat(Socket("ws://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket?vsn=2.0.0")

      assertThat(Socket("wss://localhost:4000/socket/websocket").endpointUrl.toString())
        .isEqualTo("https://localhost:4000/socket/websocket?vsn=2.0.0")

      // test params
      val singleParam = hashMapOf("token" to "abc123")
      assertThat(Socket("ws://localhost:4000/socket/websocket", singleParam).endpointUrl.toString())
        .isEqualTo("http://localhost:4000/socket/websocket?vsn=2.0.0&token=abc123")

      val multipleParams = hashMapOf("token" to "abc123", "user_id" to 1)
      assertThat(
        Socket("http://localhost:4000/socket/websocket", multipleParams).endpointUrl.toString()
      )
        .isEqualTo("http://localhost:4000/socket/websocket?vsn=2.0.0&user_id=1&token=abc123")

      // test params with spaces
      val spacesParams = hashMapOf("token" to "abc 123", "user_id" to 1)
      assertThat(
        Socket("wss://localhost:4000/socket/websocket", spacesParams).endpointUrl.toString()
      )
        .isEqualTo("https://localhost:4000/socket/websocket?vsn=2.0.0&user_id=1&token=abc%20123")
    }

    /* End Constructor */
  }

  @Nested
  @DisplayName("protocol")
  inner class Protocol {
    @Test
    internal fun `returns wss when protocol is https`() {
      val socket = Socket("https://example.com/")
      assertThat(socket.protocol).isEqualTo("wss")
    }

    @Test
    internal fun `returns ws when protocol is http`() {
      val socket = Socket("http://example.com/")
      assertThat(socket.protocol).isEqualTo("ws")
    }

    @Test
    internal fun `returns value if not https or http`() {
      val socket = Socket("wss://example.com/")
      assertThat(socket.protocol).isEqualTo("wss")
    }

    /* End Protocol */
  }

  @Nested
  @DisplayName("isConnected")
  inner class IsConnected {
    @Test
    internal fun `returns false if connection is null`() {
      assertThat(socket.isConnected).isFalse()
    }

    @Test
    internal fun `is false if state is not open`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.CLOSING)

      socket.connection = connection
      assertThat(socket.isConnected).isFalse()
    }

    @Test
    internal fun `is true if state open`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

      socket.connection = connection
      assertThat(socket.isConnected).isTrue()
    }

    /* End IsConnected */
  }

  @Nested
  @DisplayName("connect")
  inner class Connect {
    @Test
    internal fun `establishes websocket connection with endpoint`() {
      socket.connect()
      assertThat(socket.connection).isNotNull()
    }

    @Test
    internal fun `accounts for changing parameters`() {
      val transport = mock<(URL) -> Transport>()
      whenever(transport.invoke(any())).thenReturn(connection)

      var token = "a"
      val socket = Socket("wss://localhost:4000/socket", { mapOf("token" to token) })
      socket.transport = transport

      socket.connect()
      argumentCaptor<URL> {
        verify(transport).invoke(capture())
        assertThat(firstValue.query).isEqualTo("vsn=2.0.0&token=a")

        token = "b"
        socket.disconnect()
        socket.connect()
        verify(transport, times(2)).invoke(capture())
        assertThat(lastValue.query).isEqualTo("vsn=2.0.0&token=b")
      }
    }

    @Test
    internal fun `sets callbacks for connection`() {
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

      val data = listOf(null, null, "topic", "event", mapOf("go" to true))

      val json = Defaults.gson.toJson(data)
      socket.connection?.onMessage?.invoke(json)
      assertThat(lastMessage?.payload).isEqualTo(mapOf("go" to true))
    }

    @Test
    internal fun `removes callbacks`() {
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

      val data = listOf(null, null, "topic", "event", mapOf("go" to true))

      val json = Defaults.gson.toJson(data)
      socket.connection?.onMessage?.invoke(json)
      assertThat(lastMessage?.payload).isNull()
    }

    @Test
    internal fun `does not connect if already connected`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
      socket.connect()
      socket.connect()

      verify(connection, times(1)).connect()
    }

    /* End Connect */
  }

  @Nested
  @DisplayName("disconnect")
  inner class Disconnect {
    @Test
    internal fun `removes existing connection`() {
      socket.connect()
      socket.disconnect()

      assertThat(socket.connection).isNull()
      verify(connection).disconnect(WS_CLOSE_NORMAL)
    }

    @Test
    internal fun `flags the socket as closed cleanly`() {
      assertThat(socket.closeWasClean).isFalse()

      socket.disconnect()
      assertThat(socket.closeWasClean).isTrue()
    }

    @Test
    internal fun `calls callback`() {
      val mockCallback = mock<() -> Unit> {}

      socket.disconnect(callback = mockCallback)
      verify(mockCallback).invoke()
    }

    @Test
    internal fun `calls connection close callback`() {
      socket.connect()
      socket.disconnect(10, "reason")
      verify(connection).disconnect(10, "reason")
    }

    @Test
    internal fun `resets reconnect timer`() {
      val mockTimer = mock<TimeoutTimer>()
      socket.reconnectTimer = mockTimer

      socket.disconnect()
      verify(mockTimer).reset()
    }

    @Test
    internal fun `cancels and releases heartbeat timer`() {
      val mockTask = mock<DispatchWorkItem>()
      socket.heartbeatTask = mockTask

      socket.disconnect()
      verify(mockTask).cancel()
      assertThat(socket.heartbeatTask).isNull()
    }

    @Test
    internal fun `does nothing if not connected`() {
      socket.disconnect()
      verifyNoInteractions(connection)
    }

    /* End Disconnect */
  }

  @Nested
  @DisplayName("channel")
  inner class NewChannel {
    @Test
    internal fun `returns channel with given topic and params`() {
      val channel = socket.channel("topic", mapOf("one" to "two"))

      assertThat(channel.socket).isEqualTo(socket)
      assertThat(channel.topic).isEqualTo("topic")
      assertThat(channel.params["one"]).isEqualTo("two")
    }

    @Test
    internal fun `adds channel to socket's channel list`() {
      assertThat(socket.channels).isEmpty()

      val channel = socket.channel("topic", mapOf("one" to "two"))

      assertThat(socket.channels).hasSize(1)
      assertThat(socket.channels.first()).isEqualTo(channel)
    }

    /* End Channel */
  }

  @Nested
  @DisplayName("remove")
  inner class Remove {
    @Test
    internal fun `removes given channel from channels`() {
      val channel1 = socket.channel("topic-1")
      val channel2 = socket.channel("topic-2")

      channel1.joinPush.ref = "1"
      channel2.joinPush.ref = "2"

      socket.remove(channel1)
      assertThat(socket.channels).doesNotContain(channel1)
      assertThat(socket.channels).contains(channel2)
    }

    @Test
    internal fun `does not throw exception when iterating over channels`() {
      val channel1 = socket.channel("topic-1")
      val channel2 = socket.channel("topic-2")

      channel1.joinPush.ref = "1"
      channel2.joinPush.ref = "2"

      channel1.join().trigger("ok", emptyMap())
      channel2.join().trigger("ok", emptyMap())

      var chan1Called = false
      channel1.onError { chan1Called = true }

      var chan2Called = false
      channel2.onError {
        chan2Called = true
        socket.remove(channel2)
      }

      // This will trigger an iteration over the socket.channels list which will trigger
      // channel2.onError. That callback will attempt to remove channel2 during iteration
      // which would throw a ConcurrentModificationException if the socket.remove method
      // is implemented incorrectly.
      socket.onConnectionError(IllegalStateException(), null)

      // Assert that both on all error's got called even when a channel was removed
      assertThat(chan1Called).isTrue()
      assertThat(chan2Called).isTrue()

      assertThat(socket.channels).doesNotContain(channel2)
      assertThat(socket.channels).contains(channel1)
    }

    /* End Remove */
  }

  @Nested
  @DisplayName("release")
  inner class Release {
    @Test
    internal fun `Clears any callbacks with the matching refs`() {
      socket.stateChangeCallbacks.onOpen("1") {}
      socket.stateChangeCallbacks.onOpen("2") {}
      socket.stateChangeCallbacks.onClose("1") {}
      socket.stateChangeCallbacks.onClose("2") {}
      socket.stateChangeCallbacks.onError("1") { _: Throwable, _: Response? -> }
      socket.stateChangeCallbacks.onError("2") { _: Throwable, _: Response? -> }
      socket.stateChangeCallbacks.onMessage("1") { }
      socket.stateChangeCallbacks.onMessage("2") { }

      socket.stateChangeCallbacks.release(listOf("1"))

      assertThat(socket.stateChangeCallbacks.open).doesNotContain("1")
      assertThat(socket.stateChangeCallbacks.close).doesNotContain("1")
      assertThat(socket.stateChangeCallbacks.error).doesNotContain("1")
      assertThat(socket.stateChangeCallbacks.message).doesNotContain("1")
    }
  }

  @Nested
  @DisplayName("push")
  inner class Push {
    @Test
    internal fun `sends data to connection when connected`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

      socket.connect()
      socket.push("topic", "event", mapOf("one" to "two"), "ref", "join-ref")

      val expected = "[\"join-ref\",\"ref\",\"topic\",\"event\",{\"one\":\"two\"}]"
      verify(connection).send(expected)
    }

    @Test
    internal fun `excludes ref information if not passed`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

      socket.connect()
      socket.push("topic", "event", mapOf("one" to "two"))

      val expected = "[null,null,\"topic\",\"event\",{\"one\":\"two\"}]"
      verify(connection).send(expected)
    }

    @Test
    internal fun `buffers data when not connected`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.CLOSED)
      socket.connect()

      socket.push("topic", "event1", mapOf("one" to "two"))
      verify(connection, never()).send(any())
      assertThat(socket.sendBuffer).hasSize(1)

      socket.push("topic", "event2", mapOf("one" to "two"))
      verify(connection, never()).send(any())
      assertThat(socket.sendBuffer).hasSize(2)

      socket.sendBuffer.forEach { it.second.invoke() }
      verify(connection, times(2)).send(any())
    }

    /* End Push */
  }

  @Nested
  @DisplayName("makeRef")
  inner class MakeRef {
    @Test
    internal fun `returns next message ref`() {
      assertThat(socket.ref).isEqualTo(0)
      assertThat(socket.makeRef()).isEqualTo("1")
      assertThat(socket.ref).isEqualTo(1)
      assertThat(socket.makeRef()).isEqualTo("2")
      assertThat(socket.ref).isEqualTo(2)
    }

    @Test
    internal fun `resets to 0 if it hits max int`() {
      socket.ref = Int.MAX_VALUE

      assertThat(socket.makeRef()).isEqualTo("0")
      assertThat(socket.ref).isEqualTo(0)
    }

    /* End MakeRef */
  }

  @Nested
  @DisplayName("sendHeartbeat")
  inner class SendHeartbeat {

    @BeforeEach
    internal fun setUp() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
      socket.connect()
    }

    @Test
    internal fun `closes socket when heartbeat is not ack'd within heartbeat window`() {
      socket.sendHeartbeat()
      verify(connection, never()).disconnect(any(), any())
      assertThat(socket.pendingHeartbeatRef).isNotNull()

      socket.sendHeartbeat()
      verify(connection).disconnect(WS_CLOSE_NORMAL, "heartbeat timeout")
      assertThat(socket.pendingHeartbeatRef).isNull()
    }

    @Test
    internal fun `pushes heartbeat data when connected`() {
      socket.sendHeartbeat()

      val expected = "[null,\"1\",\"phoenix\",\"heartbeat\",{}]"
      assertThat(socket.pendingHeartbeatRef).isEqualTo(socket.ref.toString())
      verify(connection).send(expected)
    }

    @Test
    internal fun `does nothing when not connected`() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.CLOSED)
      socket.sendHeartbeat()

      verify(connection, never()).disconnect(any(), any())
      verify(connection, never()).send(any())
    }

    /* End SendHeartbeat */
  }

  @Nested
  @DisplayName("flushSendBuffer")
  inner class FlushSendBuffer {
    @Test
    internal fun `invokes callbacks in buffer when connected`() {
      var oneCalled = 0
      socket.sendBuffer.add(Pair("0", { oneCalled += 1 }))
      var twoCalled = 0
      socket.sendBuffer.add(Pair("1", { twoCalled += 1 }))
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
    internal fun `empties send buffer`() {
      socket.sendBuffer.add(Pair(null, {}))

      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
      socket.connect()

      assertThat(socket.sendBuffer).isNotEmpty()
      socket.flushSendBuffer()

      assertThat(socket.sendBuffer).isEmpty()
    }

    /* End FlushSendBuffer */
  }

  @Nested
  @DisplayName("removeFromSendBuffer")
  inner class RemoveFromSendBuffer {
    @Test
    internal fun `removes a callback with matching ref`() {
      var oneCalled = 0
      socket.sendBuffer.add(Pair("0", { oneCalled += 1 }))
      var twoCalled = 0
      socket.sendBuffer.add(Pair("1", { twoCalled += 1 }))

      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)

      // connect
      socket.connect()

      socket.removeFromSendBuffer("0")

      // sends once connected
      socket.flushSendBuffer()
      assertThat(oneCalled).isEqualTo(0)
      assertThat(twoCalled).isEqualTo(1)
    }
  }

  @Nested
  @DisplayName("resetHeartbeat")
  inner class ResetHeartbeat {
    @Test
    internal fun `clears any pending heartbeat`() {
      socket.pendingHeartbeatRef = "1"
      socket.resetHeartbeat()

      assertThat(socket.pendingHeartbeatRef).isNull()
    }

    @Test
    fun `does not schedule heartbeat if skipHeartbeat == true`() {
      socket.skipHeartbeat = true
      socket.resetHeartbeat()

      verifyNoInteractions(mockDispatchQueue)
    }

    @Test
    internal fun `creates a future heartbeat task`() {
      val mockTask = mock<DispatchWorkItem>()
      whenever(mockDispatchQueue.queueAtFixedRate(any(), any(), any(), any())).thenReturn(mockTask)

      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
      socket.connect()
      socket.heartbeatIntervalMs = 5_000

      assertThat(socket.heartbeatTask).isNull()
      socket.resetHeartbeat()

      assertThat(socket.heartbeatTask).isNotNull()
      argumentCaptor<() -> Unit> {
        verify(mockDispatchQueue).queueAtFixedRate(
          eq(5_000L), eq(5_000L),
          eq(TimeUnit.MILLISECONDS), capture()
        )

        // fire the task
        allValues.first().invoke()

        val expected = "[null,\"1\",\"phoenix\",\"heartbeat\",{}]"
        verify(connection).send(expected)
      }
    }

    /* End ResetHeartbeat */
  }

  @Nested
  @DisplayName("onConnectionOpened")
  inner class OnConnectionOpened {

    @BeforeEach
    internal fun setUp() {
      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
      socket.connect()
    }

    @Test
    internal fun `flushes the send buffer`() {
      var oneCalled = 0
      socket.sendBuffer.add(Pair("1", { oneCalled += 1 }))

      socket.onConnectionOpened()
      assertThat(oneCalled).isEqualTo(1)
      assertThat(socket.sendBuffer).isEmpty()
    }

    @Test
    internal fun `resets reconnect timer`() {
      val mockTimer = mock<TimeoutTimer>()
      socket.reconnectTimer = mockTimer

      socket.onConnectionOpened()
      verify(mockTimer).reset()
    }

    @Test
    internal fun `resets the heartbeat`() {
      val mockTask = mock<DispatchWorkItem>()
      socket.heartbeatTask = mockTask

      socket.onConnectionOpened()
      verify(mockTask).cancel()
      verify(mockDispatchQueue).queueAtFixedRate(any(), any(), any(), any())
    }

    @Test
    internal fun `invokes all onOpen callbacks`() {
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

    /* End OnConnectionOpened */
  }

  @Nested
  @DisplayName("onConnectionClosed")
  inner class OnConnectionClosed {

    private lateinit var mockTimer: TimeoutTimer

    @BeforeEach
    internal fun setUp() {
      mockTimer = mock()
      socket.reconnectTimer = mockTimer

      whenever(connection.readyState).thenReturn(Transport.ReadyState.OPEN)
      socket.connect()
    }

    @Test
    internal fun `schedules reconnectTimer timeout if normal close`() {
      socket.onConnectionClosed(WS_CLOSE_NORMAL)
      verify(mockTimer).scheduleTimeout()
    }

    @Test
    internal fun `does not schedule reconnectTimer timeout if normal close after explicit disconnect`() {
      socket.disconnect()
      verify(mockTimer, never()).scheduleTimeout()
    }

    @Test
    internal fun `schedules reconnectTimer if not normal close`() {
      socket.onConnectionClosed(1001)
      verify(mockTimer).scheduleTimeout()
    }

    @Test
    internal fun `schedules reconnectTimer timeout if connection cannot be made after a previous clean disconnect`() {
      socket.disconnect()
      socket.connect()

      socket.onConnectionClosed(1001)
      verify(mockTimer).scheduleTimeout()
    }

    @Test
    internal fun `cancels heartbeat task`() {
      val mockTask = mock<DispatchWorkItem>()
      socket.heartbeatTask = mockTask

      socket.onConnectionClosed(1000)
      verify(mockTask).cancel()
      assertThat(socket.heartbeatTask).isNull()
    }

    @Test
    internal fun `triggers onClose callbacks`() {
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
    internal fun `triggers channel error if joining`() {
      val channel = socket.channel("topic")
      val spy = spy(channel)

      // Use the spy instance instead of the Channel instance
      socket.channels = socket.channels.minus(channel)
      socket.channels = socket.channels.plus(spy)

      spy.join()
      assertThat(spy.state).isEqualTo(Channel.State.JOINING)

      socket.onConnectionClosed(1001)
      verify(spy).trigger("phx_error")
    }

    @Test
    internal fun `triggers channel error if joined`() {
      val channel = socket.channel("topic")
      val spy = spy(channel)

      // Use the spy instance instead of the Channel instance
      socket.channels = socket.channels.minus(channel)
      socket.channels = socket.channels.plus(spy)

      spy.join().trigger("ok", emptyMap())

      assertThat(channel.state).isEqualTo(Channel.State.JOINED)

      socket.onConnectionClosed(1001)
      verify(spy).trigger("phx_error")
    }

    @Test
    internal fun `does not trigger channel error after leave`() {
      val channel = socket.channel("topic")
      val spy = spy(channel)

      // Use the spy instance instead of the Channel instance
      socket.channels = socket.channels.minus(channel)
      socket.channels = socket.channels.plus(spy)

      spy.join().trigger("ok", emptyMap())
      spy.leave()

      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)

      socket.onConnectionClosed(1001)
      verify(spy, never()).trigger("phx_error")
    }

    /* End OnConnectionClosed */
  }

  @Nested
  @DisplayName("onConnectionError")
  inner class OnConnectionError {
    @Test
    internal fun `triggers onClose callbacks`() {
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
    internal fun `triggers channel error if joining`() {
      val channel = socket.channel("topic")
      val spy = spy(channel)

      // Use the spy instance instead of the Channel instance
      socket.channels = socket.channels.minus(channel)
      socket.channels = socket.channels.plus(spy)

      spy.join()
      assertThat(spy.state).isEqualTo(Channel.State.JOINING)

      socket.onConnectionError(Throwable(), null)
      verify(spy).trigger("phx_error")
    }

    @Test
    internal fun `triggers channel error if joined`() {
      val channel = socket.channel("topic")
      val spy = spy(channel)

      // Use the spy instance instead of the Channel instance
      socket.channels = socket.channels.minus(channel)
      socket.channels = socket.channels.plus(spy)

      spy.join().trigger("ok", emptyMap())

      assertThat(channel.state).isEqualTo(Channel.State.JOINED)

      socket.onConnectionError(Throwable(), null)
      verify(spy).trigger("phx_error")
    }

    @Test
    internal fun `does not trigger channel error after leave`() {
      val channel = socket.channel("topic")
      val spy = spy(channel)

      // Use the spy instance instead of the Channel instance
      socket.channels = socket.channels.minus(channel)
      socket.channels = socket.channels.plus(spy)

      spy.join().trigger("ok", emptyMap())
      spy.leave()

      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)

      socket.onConnectionError(Throwable(), null)
      verify(spy, never()).trigger("phx_error")
    }

    /* End OnConnectionError */
  }

  @Nested
  @DisplayName("onConnectionMessage")
  inner class OnConnectionMessage {


    @Test
    internal fun `parses raw messages and triggers channel event`() {
      val targetChannel = mock<Channel>()
      whenever(targetChannel.isMember(any())).thenReturn(true)
      val otherChannel = mock<Channel>()
      whenever(otherChannel.isMember(any())).thenReturn(false)

      socket.channels = socket.channels.plus(targetChannel)
      socket.channels = socket.channels.minus(otherChannel)

      val rawMessage = "[null,null,\"topic\",\"event\",{\"one\":\"two\",\"status\":\"ok\"}]"
      socket.onConnectionMessage(rawMessage)

      verify(targetChannel).trigger(message = any())
      verify(otherChannel, never()).trigger(message = any())
    }

    @Test
    internal fun `invokes onMessage callbacks`() {
      var message: Message? = null
      socket.onMessage { message = it }

      val rawMessage = "[null,null,\"topic\",\"event\",{\"one\":\"two\",\"status\":\"ok\"}]"
      socket.onConnectionMessage(rawMessage)

      assertThat(message?.topic).isEqualTo("topic")
      assertThat(message?.event).isEqualTo("event")
    }

    @Test
    internal fun `clears pending heartbeat`() {
      socket.pendingHeartbeatRef = "5"

      val rawMessage = "[null,\"5\",\"topic\",\"event\",{\"one\":\"two\",\"status\":\"ok\"}]"
      socket.onConnectionMessage(rawMessage)
      assertThat(socket.pendingHeartbeatRef).isNull()
    }

    /* End OnConnectionMessage */
  }

  @Nested
  @DisplayName("ConcurrentModificationException")
  inner class ConcurrentModificationExceptionTests {

    @Test
    internal fun `onOpen does not throw`() {
      var oneCalled = 0
      var twoCalled = 0
      socket.onOpen {
        socket.onOpen { twoCalled += 1 }
        oneCalled += 1
      }

      socket.onConnectionOpened()
      assertThat(oneCalled).isEqualTo(1)
      assertThat(twoCalled).isEqualTo(0)

      socket.onConnectionOpened()
      assertThat(oneCalled).isEqualTo(2)
      assertThat(twoCalled).isEqualTo(1)
    }

    @Test
    internal fun `onClose does not throw`() {
      var oneCalled = 0
      var twoCalled = 0
      socket.onClose {
        socket.onClose { twoCalled += 1 }
        oneCalled += 1
      }

      socket.onConnectionClosed(1000)
      assertThat(oneCalled).isEqualTo(1)
      assertThat(twoCalled).isEqualTo(0)

      socket.onConnectionClosed(1001)
      assertThat(oneCalled).isEqualTo(2)
      assertThat(twoCalled).isEqualTo(1)
    }

    @Test
    internal fun `onError does not throw`() {
      var oneCalled = 0
      var twoCalled = 0
      socket.onError { _, _ ->
        socket.onError { _, _ -> twoCalled += 1 }
        oneCalled += 1
      }

      socket.onConnectionError(Throwable(), null)
      assertThat(oneCalled).isEqualTo(1)
      assertThat(twoCalled).isEqualTo(0)

      socket.onConnectionError(Throwable(), null)
      assertThat(oneCalled).isEqualTo(2)
      assertThat(twoCalled).isEqualTo(1)
    }

    @Test
    internal fun `onMessage does not throw`() {
      var oneCalled = 0
      var twoCalled = 0
      socket.onMessage {
        socket.onMessage { twoCalled += 1 }
        oneCalled += 1
      }

      val message = "[null,null,\"room:lobby\",\"shout\",{\"message\":\"Hi\",\"name\":\"Tester\"}]"
      socket.onConnectionMessage(message)
      assertThat(oneCalled).isEqualTo(1)
      assertThat(twoCalled).isEqualTo(0)

      socket.onConnectionMessage(message)
      assertThat(oneCalled).isEqualTo(2)
      assertThat(twoCalled).isEqualTo(1)
    }

    @Test
    internal fun `does not throw when adding channel`() {
      var oneCalled = 0
      socket.onOpen {
        val channel = socket.channel("foo")
        oneCalled += 1
      }

      socket.onConnectionOpened()
      assertThat(oneCalled).isEqualTo(1)
    }

    /* End ConcurrentModificationExceptionTests */
  }
}
