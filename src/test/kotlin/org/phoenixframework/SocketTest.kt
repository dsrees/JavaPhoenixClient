package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.net.URL

class SocketTest {

  @Mock lateinit var okHttpClient: OkHttpClient

  lateinit var connection: Transport
  lateinit var socket: Socket

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    connection = spy(WebSocketTransport(URL("https://localhost:4000/socket"), okHttpClient))

    socket = Socket("wss://localhost:4000/socket")
    socket.transport = { connection }

  }

  //------------------------------------------------------------------------------
  // Constructor
  //------------------------------------------------------------------------------
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


  //------------------------------------------------------------------------------
  // Public Properties
  //------------------------------------------------------------------------------
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

  //------------------------------------------------------------------------------
  // Public Functions
  //------------------------------------------------------------------------------
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
}