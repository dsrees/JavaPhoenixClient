package org.phoenixframework

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URL

/**
 * Interface that defines different types of Transport layers. A default {@link WebSocketTransport}
 * is provided which uses an OkHttp WebSocket to transport data between your Phoenix server.
 *
 * Future support may be added to provide your own custom Transport, such as a LongPoll
 */
interface Transport {

  /** Available ReadyStates of a {@link Transport}. */
  enum class ReadyState {

    /** The Transport is connecting to the server  */
    CONNECTING,

    /** The Transport is connected and open */
    OPEN,

    /** The Transport is closing */
    CLOSING,

    /** The Transport is closed */
    CLOSED
  }

  /** The state of the Transport. See {@link ReadyState} */
  val readyState: ReadyState


  /** Called when the Transport opens */
  var onOpen: (() -> Unit)?
  /** Called when the Transport receives an error */
  var onError: ((Throwable, Response?) -> Unit)?
  /** Called each time the Transport receives a message */
  var onMessage: ((String) -> Unit)?
  /** Called when the Transport closes */
  var onClose: ((Int) -> Unit)?


  /** Connect to the server */
  fun connect()

  /**
   * Disconnect from the Server
   *
   * @param code Status code as defined by <a
   * href="http://tools.ietf.org/html/rfc6455#section-7.4">Section 7.4 of RFC 6455</a>.
   * @param reason Reason for shutting down or {@code null}.
   */
  fun disconnect(code: Int, reason: String? = null)

  /**
   * Sends text to the Server
   */
  fun send(data: String)
}



/**
 * A WebSocket implementation of a Transport that uses a WebSocket to facilitate sending
 * and receiving data.
 *
 * @param url: URL to connect to
 * @param okHttpClient: Custom client that can be pre-configured before connecting
 */
class WebSocketTransport(
  private val url: URL,
  private val okHttpClient: OkHttpClient
) :
    WebSocketListener(),
    Transport {

  internal var connection: WebSocket? = null

  override var readyState: Transport.ReadyState = Transport.ReadyState.CLOSED
  override var onOpen: (() -> Unit)? = null
  override var onError: ((Throwable, Response?) -> Unit)? = null
  override var onMessage: ((String) -> Unit)? = null
  override var onClose: ((Int) -> Unit)? = null

  override fun connect() {
    this.readyState = Transport.ReadyState.CONNECTING
    val request = Request.Builder().url(url).build()
    connection = okHttpClient.newWebSocket(request, this)
  }

  override fun disconnect(code: Int, reason: String?) {
    connection?.close(code, reason)
    connection = null
  }

  override fun send(data: String) {
    connection?.send(data)
  }

  //------------------------------------------------------------------------------
  // WebSocket Listener
  //------------------------------------------------------------------------------
  override fun onOpen(webSocket: WebSocket, response: Response) {
    this.readyState = Transport.ReadyState.OPEN
    this.onOpen?.invoke()
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    this.readyState = Transport.ReadyState.CLOSED
    this.onError?.invoke(t, response)
  }

  override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    this.readyState = Transport.ReadyState.CLOSING
  }

  override fun onMessage(webSocket: WebSocket, text: String) {
    this.onMessage?.invoke(text)
  }

  override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    this.readyState = Transport.ReadyState.CLOSED
    this.onClose?.invoke(code)
  }
}