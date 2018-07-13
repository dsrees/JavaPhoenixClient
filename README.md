# JavaPhoenixClient


## Java Client for Phoenix Channels

JavaPhoenixClient is a Kotlin implementation of the `[phoenix.js] client used to manage Phoenix channels.


```kotlin

fun connectToChatRoom() {

    // Create the Socket
    val params = hashMapOf("token" to "abc123")
    val socket = PhxSocket("http://localhost:4000/socket/websocket", multipleParams

    // Listen to events on the Socket
    socket.logger = { Log.d("TAG", it) }
    socket.onOpen { Timber.d("Socket Opened") }
    socket.onClose { Timber.d("Socket Closed") }
    socket.onError { Timber.d(it, "Socket Error") }

    socket.connect()

    // Join channels and listen to events
    val chatroom = socket.channel("chatroom:general")
    chatroom.on("new_message") {
        // `it` is a PhxMessage object
        val payload = it.payload
    }

    chatroom.join()
            .receive("ok") { /* Joined the chatroom */ }
            .receive("error") { /* failed to join the chatroom */ }
}
```
