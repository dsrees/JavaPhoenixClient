# JavaPhoenixClient

[![Maven Central](https://img.shields.io/maven-central/v/com.github.dsrees/JavaPhoenixClient.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.dsrees%22%20AND%20a:%22JavaPhoenixClient%22)
[![Build Status](https://travis-ci.com/dsrees/JavaPhoenixClient.svg?branch=master)](https://travis-ci.com/dsrees/JavaPhoenixClient)
[![codecov](https://codecov.io/gh/dsrees/JavaPhoenixClient/branch/master/graph/badge.svg)](https://codecov.io/gh/dsrees/JavaPhoenixClient)


JavaPhoenixClient is a Kotlin implementation of the [phoenix.js](https://hexdocs.pm/phoenix/js/) client used to manage Phoenix channels.


### Basic Usage

```kotlin

fun connectToChatRoom() {

    // Create the Socket
    val params = hashMapOf("token" to "abc123")
    val socket = Socket("http://localhost:4000/socket/websocket", params)

    // Listen to events on the Socket
    socket.logger = { Log.d("TAG", it) }
    socket.onOpen { Log.d("TAG", "Socket Opened") }
    socket.onClose { Log.d("TAG", "Socket Closed") }
    socket.onError { throwable, response -> Log.d(throwable, "TAG", "Socket Error ${response?.code}") }

    socket.connect()

    // Join channels and listen to events
    val chatroom = socket.channel("chatroom:general")
    chatroom.on("new_message") { message ->
        val payload = message.payload
        ...
    }

    chatroom.join()
            .receive("ok") { /* Joined the chatroom */ }
            .receive("error") { /* failed to join the chatroom */ }
}
```


If you need to provide dynamic parameters that can change between calls to `connect()`, then you can  pass a closure to the constructor

```kotlin

// Create the Socket
var authToken = "abc"
val socket = Socket("http://localhost:4000/socket/websocket", { mapOf("token" to authToken) })

// Connect with query parameters "?token=abc"
socket.connect()


// later in time, connect with query parameters "?token=xyz"
authToken = "xyz"
socket.connect() // or internal reconnect logic kicks in
```


You can also inject your own OkHttp Client into the Socket to provide your own configuration
```kotlin
// Configure your own OkHttp Client
val client = OkHttpClient.Builder()
    .connectTimeout(1000, TimeUnit.MILLISECONDS)
    .build()

// Create Socket with your custom instances
val params = hashMapOf("token" to "abc123")
val socket = Socket("http://localhost:4000/socket/websocket",
    params,
    client)
```

By default, the client use GSON to encode and decode JSON. If you prefer to manage this yourself, you
can provide custom encode/decode functions in the `Socket` constructor.

```kotlin

// Configure your own GSON instance
val gson = Gson.Builder().create()
val encoder: EncodeClosure = {
    // Encode a Map into JSON using your custom GSON instance or another JSON library
    // of your choice (Moshi, etc)
}
val decoder: DecodeClosure = {
    // Decode a JSON String into a `Message` object using your custom JSON library 
}

// Create Socket with your custom instances
val params = hashMapOf("token" to "abc123")
val socket = Socket("http://localhost:4000/socket/websocket",
    params,
    encoder,
    decoder)
```





### Installation

JavaPhoenixClient is hosted on MavenCentral. You'll need to make sure you declare `mavenCentral()` as one of your repositories

```
repositories {
    mavenCentral()
}
```

and then add the library. See [releases](https://github.com/dsrees/JavaPhoenixClient/releases) for the latest version
```$xslt
dependencies {
    implementation 'com.github.dsrees:JavaPhoenixClient:0.7.0'
}
```


### Feedback
Please submit in issue if you have any problems or questions! PRs are also welcome.


This library is built to mirror the [phoenix.js](https://hexdocs.pm/phoenix/js/) and [SwiftPhoenixClient](https://github.com/davidstump/SwiftPhoenixClient) libraries.
