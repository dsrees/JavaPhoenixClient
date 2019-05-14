package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.EOFException
import java.net.SocketException
import java.net.URL

class WebSocketTransportTest {

  @Mock lateinit var mockClient: OkHttpClient
  @Mock lateinit var mockWebSocket: WebSocket
  @Mock lateinit var mockResponse: Response

  lateinit var transport: WebSocketTransport

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    val url = URL("http://localhost:400/socket/websocket")
    transport = WebSocketTransport(url, mockClient)
  }

  @Test
  fun `connect sets ready state to CONNECTING and creates connection`() {
    whenever(mockClient.newWebSocket(any(), any())).thenReturn(mockWebSocket)

    transport.connect()
    assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CONNECTING)
    assertThat(transport.connection).isNotNull()
  }

  @Test
  fun `disconnect closes and releases the connection`() {
    transport.connection = mockWebSocket

    transport.disconnect(10, "Test reason")
    verify(mockWebSocket).close(10, "Test reason")
    assertThat(transport.connection).isNull()
  }

  @Test
  fun `send sends text through the connection`() {
    transport.connection = mockWebSocket

    transport.send("some data")
    verify(mockWebSocket).send("some data")
  }

  @Test
  fun `onOpen sets ready state to OPEN and invokes the onOpen callback`() {
    val mockClosure = mock<() -> Unit>()
    transport.onOpen = mockClosure

    assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSED)

    transport.onOpen(mockWebSocket, mockResponse)
    assertThat(transport.readyState).isEqualTo(Transport.ReadyState.OPEN)
    verify(mockClosure).invoke()
  }

  @Test
  fun `onFailure sets ready state to CLOSED and invokes onError callback`() {
    val mockClosure = mock<(Throwable, Response?) -> Unit>()
    val mockOnClose = mock<(Int) -> Unit>()
    transport.onClose = mockOnClose
    transport.onError = mockClosure

    transport.readyState = Transport.ReadyState.CONNECTING

    val throwable = Throwable()
    transport.onFailure(mockWebSocket, throwable, mockResponse)
    assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSED)
    verify(mockClosure).invoke(throwable, mockResponse)
    verifyZeroInteractions(mockOnClose)
  }

  @Test
  fun `onFailure also triggers onClose for SocketException`() {
    val mockOnError = mock<(Throwable, Response?) -> Unit>()
    val mockOnClose = mock<(Int) -> Unit>()
    transport.onClose = mockOnClose
    transport.onError = mockOnError

    val throwable = SocketException()
    transport.onFailure(mockWebSocket, throwable, mockResponse)
    verify(mockOnError).invoke(throwable, mockResponse)
    verify(mockOnClose).invoke(4000)
  }

  @Test
  fun `onFailure also triggers onClose for EOFException`() {
    val mockOnError = mock<(Throwable, Response?) -> Unit>()
    val mockOnClose = mock<(Int) -> Unit>()
    transport.onClose = mockOnClose
    transport.onError = mockOnError

    val throwable = EOFException()
    transport.onFailure(mockWebSocket, throwable, mockResponse)
    verify(mockOnError).invoke(throwable, mockResponse)
    verify(mockOnClose).invoke(4001)
  }

  @Test
  fun `onClosing sets ready state to CLOSING`() {
    transport.readyState = Transport.ReadyState.OPEN

    transport.onClosing(mockWebSocket, 10, "reason")
    assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSING)
  }

  @Test
  fun `onMessage invokes onMessage closure`() {
    val mockClosure = mock<(String) -> Unit>()
    transport.onMessage = mockClosure

    transport.onMessage(mockWebSocket, "text")
    verify(mockClosure).invoke("text")
  }

  @Test
  fun `onClosed sets readyState to CLOSED and invokes closure`() {
    val mockClosure = mock<(Int) -> Unit>()
    transport.onClose = mockClosure

    transport.readyState = Transport.ReadyState.CONNECTING

    transport.onClosed(mockWebSocket, 10, "reason")
    assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSED)
    verify(mockClosure).invoke(10)
  }
}