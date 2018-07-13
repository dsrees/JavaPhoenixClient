package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhxMessageTest {

    @Test
    fun getStatus_returnsPayloadStatus() {

        val payload = hashMapOf("status" to "ok", "topic" to "chat:1")

        val message = PhxMessage("ref1", "chat:1", "event1", payload)

        assertThat(message.ref).isEqualTo("ref1")
        assertThat(message.topic).isEqualTo("chat:1")
        assertThat(message.event).isEqualTo("event1")
        assertThat(message.payload["topic"]).isEqualTo("chat:1")
        assertThat(message.status).isEqualTo("ok")

    }
}