package com.github.dsrees.chatexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import org.phoenixframework.Channel
import org.phoenixframework.Socket

class MainActivity : AppCompatActivity() {

  companion object {
    const val TAG = "MainActivity"
  }

  private val messagesAdapter = MessagesAdapter()
  private val socket = Socket("ws://localhost:4000/socket/websocket")
  private val topic = "rooms:lobby"

  private lateinit var lobbyChannel: Channel

  private val username: String
    get() = username_input.text.toString()

  private val message: String
    get() = message_input.text.toString()


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)


    val layoutManager = LinearLayoutManager(this)
    layoutManager.stackFromEnd = true

    messages_recycler_view.layoutManager = layoutManager
    messages_recycler_view.adapter = messagesAdapter

    socket.onOpen {
      this.addText("Socket Opened")
      connect_button.text = "Disconnect"
    }

    socket.onClose {
      this.addText("Socket Closed")
      connect_button.text = "Connect"
    }

    socket.onError { throwable, response ->
      Log.e(TAG, "Socket Errored $response", throwable)
      this.addText("Socket Error")
    }


    connect_button.setOnClickListener {
      if (socket.isConnected) {
        this.disconnectAndLeave()
      } else {
        this.connectAndJoin()
      }
    }

    send_button.setOnClickListener { sendMessage() }
  }


  private fun sendMessage() {
    val payload = mapOf("user" to username, "body" to message)

    message_input.text.clear()


    this.lobbyChannel.push("new:msg", payload)
      .receive("ok") {
        Log.d(TAG, "success $it")

      }
      .receive("error") {
        Log.d(TAG, "error $it")
      }

  }


  private fun disconnectAndLeave() {
    // Be sure the leave the channel or call socket.remove(lobbyChannel)
    lobbyChannel.leave()
    socket.disconnect {
      this.addText("Socket Disconnected")
    }
  }

  private fun connectAndJoin() {
    val channel = socket.channel(topic, mapOf("status" to "joining"))
    channel.on("join") {
      this.addText("You joined the room")
    }

    channel.on("new:msg") { message ->
      val payload = message.payload
      val username = payload["user"] as? String
      val body = payload["body"] as? String


      if (username != null && body != null) {
        this.addText("[$username] $body")
      }
    }

    channel.on("user:entered") {
      this.addText("[anonymous entered]")
    }

    this.lobbyChannel = channel
    this.lobbyChannel
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
    this.messagesAdapter.add(message)
  }

}
