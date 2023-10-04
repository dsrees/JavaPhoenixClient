package com.github.dsrees.chatexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.dsrees.chatexample.databinding.ActivityMainBinding
import org.phoenixframework.Channel
import org.phoenixframework.Socket

class MainActivity : AppCompatActivity() {

  companion object {
    const val TAG = "MainActivity"
  }

  private lateinit var binding: ActivityMainBinding

  private val messagesAdapter = MessagesAdapter()
  private val layoutManager = LinearLayoutManager(this)


  // Use when connecting to https://github.com/dwyl/phoenix-chat-example
  private val socket = Socket("https://phoenix-chat.fly.dev/socket/websocket")
  private val topic = "room:lobby"

  // Use when connecting to local server
 // private val socket = Socket("ws://10.0.2.2:4000/socket/websocket")
 // private val topic = "rooms:lobby"

  private var lobbyChannel: Channel? = null

  private val username: String
    get() = binding.usernameInput.text.toString()

  private val message: String
    get() = binding.messageInput.text.toString()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    layoutManager.stackFromEnd = true

    binding.messagesRecyclerView.layoutManager = layoutManager
    binding.messagesRecyclerView.adapter = messagesAdapter

    socket.onOpen {
      this.addText("Socket Opened")
      runOnUiThread { binding.connectButton.text = "Disconnect" }
    }

    socket.onClose {
      this.addText("Socket Closed")
      runOnUiThread { binding.connectButton.text = "Connect" }
    }

    socket.onError { throwable, response ->
      Log.e(TAG, "Socket Errored $response", throwable)
      this.addText("Socket Error")
    }

    socket.logger = {
      Log.d(TAG, "SOCKET $it")
    }


    binding.connectButton.setOnClickListener {
      if (socket.isConnected) {
        this.disconnectAndLeave()
      } else {
        this.disconnectAndLeave()
        this.connectAndJoin()
      }
    }

    binding.sendButton.setOnClickListener { sendMessage() }
  }

  private fun sendMessage() {
    val payload = mapOf("user" to username, "body" to message)
    this.lobbyChannel?.push("new:msg", payload)
        ?.receive("ok") { Log.d(TAG, "success $it") }
        ?.receive("error") { Log.d(TAG, "error $it") }

    binding.messageInput.text.clear()
  }

  private fun disconnectAndLeave() {
    // Be sure the leave the channel or call socket.remove(lobbyChannel)
    lobbyChannel?.leave()
    socket.disconnect { this.addText("Socket Disconnected") }
  }

  private fun connectAndJoin() {
    val channel = socket.channel(topic, mapOf("status" to "joining"))
    channel.on("join") {
      this.addText("You joined the room")
    }

    channel.on("new:msg") { message ->
      val payload = message.payload
      val username = payload["user"] as? String
      val body = payload["body"]


      if (username != null && body != null) {
        this.addText("[$username] $body")
      }
    }

    channel.on("user:entered") {
      this.addText("[anonymous entered]")
    }

    this.lobbyChannel = channel
    channel
        .join()
        .receive("ok") {
          this.addText("Joined Channel")
        }
        .receive("error") {
          this.addText("Failed to join channel: ${it.payload}")
        }


    this.socket.connect()
  }

  private fun addText(message: String) {
    runOnUiThread {
      this.messagesAdapter.add(message)
      layoutManager.smoothScrollToPosition(binding.messagesRecyclerView, null, messagesAdapter.itemCount)
    }
  }
}
