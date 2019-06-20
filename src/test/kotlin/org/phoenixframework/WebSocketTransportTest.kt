package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.net.SocketException
import java.net.URL

class WebSocketTransportTest {

  @Mock lateinit var mockClient: OkHttpClient
  @Mock lateinit var mockWebSocket: WebSocket
  @Mock lateinit var mockResponse: Response

  private lateinit var transport: WebSocketTransport

  @BeforeEach
  internal fun setUp() {
    MockitoAnnotations.initMocks(this)

    val url = URL("http://localhost:400/socket/websocket")
    transport = WebSocketTransport(url, mockClient)
  }

  @Nested
  @DisplayName("connect")
  inner class Connect {
    @Test
    internal fun `sets ready state to CONNECTING and creates connection`() {
      whenever(mockClient.newWebSocket(any(), any())).thenReturn(mockWebSocket)

      transport.connect()
      assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CONNECTING)
      assertThat(transport.connection).isNotNull()
    }

    /* End Connect */
  }

  @Nested
  @DisplayName("disconnect")
  inner class Disconnect {
    @Test
    internal fun `closes and releases connection`() {
      transport.connection = mockWebSocket

      transport.disconnect(10, "Test reason")
      verify(mockWebSocket).close(10, "Test reason")
      assertThat(transport.connection).isNull()
    }

    /* End Disconnect */
  }

  @Nested
  @DisplayName("send")
  inner class Send {
    @Test
    internal fun `sends text through the connection`() {
      transport.connection = mockWebSocket

      transport.send("some data")
      verify(mockWebSocket).send("some data")
    }

    /* End Send */
  }

  @Nested
  @DisplayName("onOpen")
  inner class OnOpen {
    @Test
    internal fun `sets ready state to OPEN and invokes the onOpen callback`() {
      val mockClosure = mock<() -> Unit>()
      transport.onOpen = mockClosure

      assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSED)

      transport.onOpen(mockWebSocket, mockResponse)
      assertThat(transport.readyState).isEqualTo(Transport.ReadyState.OPEN)
      verify(mockClosure).invoke()
    }

    /* End OnOpen */
  }


  @Nested
  @DisplayName("onFailure")
  inner class OnFailure {
    @Test
    internal fun `sets ready state to CLOSED and invokes onError callback`() {
      val mockClosure = mock<(Throwable, Response?) -> Unit>()
      transport.onError = mockClosure

      transport.readyState = Transport.ReadyState.CONNECTING

      val throwable = Throwable()
      transport.onFailure(mockWebSocket, throwable, mockResponse)
      assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSED)
      verify(mockClosure).invoke(throwable, mockResponse)
    }

    @Test
    internal fun `also triggers onClose`() {
      val mockOnError = mock<(Throwable, Response?) -> Unit>()
      val mockOnClose = mock<(Int) -> Unit>()
      transport.onClose = mockOnClose
      transport.onError = mockOnError

      val throwable = SocketException()
      transport.onFailure(mockWebSocket, throwable, mockResponse)
      verify(mockOnError).invoke(throwable, mockResponse)
      verify(mockOnClose).invoke(1006)
    }

    /* End OnFailure */
  }

  @Nested
  @DisplayName("onClosing")
  inner class OnClosing {
    @Test
    internal fun `sets ready state to CLOSING`() {
      transport.readyState = Transport.ReadyState.OPEN

      transport.onClosing(mockWebSocket, 10, "reason")
      assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSING)
    }

    /* End OnClosing */
  }

  @Nested
  @DisplayName("onMessage")
  inner class OnMessage {
    @Test
    internal fun `invokes onMessage closure`() {
      val mockClosure = mock<(String) -> Unit>()
      transport.onMessage = mockClosure

      transport.onMessage(mockWebSocket, "text")
      verify(mockClosure).invoke("text")
    }

    /* End OnMessage*/
  }


  @Nested
  @DisplayName("onClosed")
  inner class OnClosed {
    @Test
    internal fun `sets readyState to CLOSED and invokes closure`() {
      val mockClosure = mock<(Int) -> Unit>()
      transport.onClose = mockClosure

      transport.readyState = Transport.ReadyState.CONNECTING

      transport.onClosed(mockWebSocket, 10, "reason")
      assertThat(transport.readyState).isEqualTo(Transport.ReadyState.CLOSED)
      verify(mockClosure).invoke(10)
    }

    /* End OnClosed */
  }
}