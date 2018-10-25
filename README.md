# JavaPhoenixClient

[ ![Download](https://api.bintray.com/packages/drees/java-phoenix-client/JavaPhoenixClient/images/download.svg) ](https://bintray.com/drees/java-phoenix-client/JavaPhoenixClient/_latestVersion) 
[![Build Status](https://travis-ci.com/dsrees/JavaPhoenixClient.svg?branch=master)](https://travis-ci.com/dsrees/JavaPhoenixClient)


JavaPhoenixClient is a Kotlin implementation of the [phoenix.js](https://hexdocs.pm/phoenix/js/) client used to manage Phoenix channels.


### Basic Usage

```kotlin

fun connectToChatRoom() {

    // Create the Socket
    val params = hashMapOf("token" to "abc123")
    val socket = PhxSocket("http://localhost:4000/socket/websocket", multipleParams)

    // Listen to events on the Socket
    socket.logger = { Log.d("TAG", it) }
    socket.onOpen { Log.d("TAG", "Socket Opened") }
    socket.onClose { Log.d("TAG", "Socket Closed") }
    socket.onError { throwable, response -> Log.d(throwable, "TAG", "Socket Error ${response?.code}") }

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

You can also inject your own OkHttp Client into the Socket to provide your own configuration
```kotlin
// Create the Socket with a pre-configured OkHttp Client
val client = OkHttpClient.Builder()
    .connectTimeout(1000, TimeUnit.MILLISECONDS)
    .build()
    
val params = hashMapOf("token" to "abc123")
val socket = PhxSocket("http://localhost:4000/socket/websocket", 
                       multipleParams,
                       client)
```


### Installation

JavaPhoenixClient is hosted on JCenter. You'll need to make sure you declare `jcenter()` as one of your repositories
 
```
repositories {
    jcenter()
}
```

and then add the library. See [releases](https://github.com/dsrees/JavaPhoenixClient/releases) for the latest version
```$xslt
dependencies {
    implementation 'com.github.dsrees:JavaPhoenixClient:0.1.2'
}
```


### Feedback
Please submit in issue if you have any problems!



This library is built to mirror the [phoenix.js](https://hexdocs.pm/phoenix/js/) and [SwiftPhoenixClient](https://github.com/davidstump/SwiftPhoenixClient) libraries.



