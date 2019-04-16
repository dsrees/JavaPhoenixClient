package org.phoenixframework

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URL

enum class ReadyState {
  CONNECTING,
  OPEN,
  CLOSING,
  CLOSED
}

interface Transport {

  val readyState: ReadyState

  var onOpen: (() -> Unit)?
  var onError: ((Throwable, Response?) -> Unit)?
  var onMessage: ((String) -> Unit)?
  var onClose: ((Int) -> Unit)?

  fun connect()
  fun disconnect(code: Int, reason: String? = null)
  fun send(data: String)
}

class WebSocketTransport(
  private val url: URL,
  private val okHttpClient: OkHttpClient
) :
    WebSocketListener(),
    Transport {

  private var connection: WebSocket? = null

  override var readyState: ReadyState = ReadyState.CLOSED
  override var onOpen: (() -> Unit)? = null
  override var onError: ((Throwable, Response?) -> Unit)? = null
  override var onMessage: ((String) -> Unit)? = null
  override var onClose: ((Int) -> Unit)? = null

  override fun connect() {
    this.readyState = ReadyState.CONNECTING
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
    this.readyState = ReadyState.OPEN
    this.onOpen?.invoke()
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    this.readyState = ReadyState.CLOSED
    this.onError?.invoke(t, response)
  }

  override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    this.readyState = ReadyState.CLOSING
  }

  override fun onMessage(webSocket: WebSocket, text: String) {
    this.onMessage?.invoke(text)
  }

  override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    this.readyState = ReadyState.CLOSED
    this.onClose?.invoke(code)
  }
}