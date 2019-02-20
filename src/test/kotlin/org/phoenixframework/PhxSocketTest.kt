package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import okhttp3.WebSocket
import org.junit.Test
import org.mockito.Mockito

class PhxSocketTest {

    @Test
    fun init_buildsUrlProper() {
        assertThat(PhxSocket("http://localhost:4000/socket/websocket").endpoint.toString())
                .isEqualTo("http://localhost:4000/socket/websocket")

        assertThat(PhxSocket("https://localhost:4000/socket/websocket").endpoint.toString())
                .isEqualTo("https://localhost:4000/socket/websocket")

        assertThat(PhxSocket("ws://localhost:4000/socket/websocket").endpoint.toString())
                .isEqualTo("http://localhost:4000/socket/websocket")

        assertThat(PhxSocket("wss://localhost:4000/socket/websocket").endpoint.toString())
                .isEqualTo("https://localhost:4000/socket/websocket")


        // test params
        val singleParam = hashMapOf("token" to "abc123")
        assertThat(PhxSocket("ws://localhost:4000/socket/websocket", singleParam).endpoint.toString())
                .isEqualTo("http://localhost:4000/socket/websocket?token=abc123")


        val multipleParams = hashMapOf("token" to "abc123", "user_id" to 1)
        assertThat(PhxSocket("http://localhost:4000/socket/websocket", multipleParams).endpoint.toString())
                .isEqualTo("http://localhost:4000/socket/websocket?user_id=1&token=abc123")


        // test params with spaces
        val spacesParams = hashMapOf("token" to "abc 123", "user_id" to 1)
        assertThat(PhxSocket("wss://localhost:4000/socket/websocket", spacesParams).endpoint.toString())
                .isEqualTo("https://localhost:4000/socket/websocket?user_id=1&token=abc%20123")
    }

    @Test
    fun isConnected_isTrue_WhenSocketConnected() {
        val mockSocket = Mockito.mock(WebSocket::class.java)
        val socket = PhxSocket("http://localhost:4000/socket/websocket")

        socket.onOpen(mockSocket, null)

        assertThat(socket.isConnected).isTrue()
    }

    @Test
    fun isConnected_isFalse_WhenSocketNotYetConnected() {
        val socket = PhxSocket("http://localhost:4000/socket/websocket")


        assertThat(socket.isConnected).isFalse()
    }

    @Test
    fun isConnected_isFalse_WhenSocketFailed() {
        val mockSocket = Mockito.mock(WebSocket::class.java)
        val socket = PhxSocket("http://localhost:4000/socket/websocket")

        socket.onFailure(mockSocket, RuntimeException(), null)


        assertThat(socket.isConnected).isFalse()
    }

    @Test
    fun isConnected_isFalse_WhenSocketClosed() {
        val mockSocket = Mockito.mock(WebSocket::class.java)
        val socket = PhxSocket("http://localhost:4000/socket/websocket")

        socket.onClosed(mockSocket, 0, "closed")


        assertThat(socket.isConnected).isFalse()
    }
}
