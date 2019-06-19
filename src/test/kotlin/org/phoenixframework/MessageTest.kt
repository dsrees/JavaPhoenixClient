package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MessageTest {

  @Nested
  @DisplayName("status")
  inner class Status {

    @Test
    internal fun `returns the status from the payload`() {
      val payload = mapOf("one" to "two", "status" to "ok")
      val message = Message("ref", "topic", "event", payload, null)

      assertThat(message.ref).isEqualTo("ref")
      assertThat(message.topic).isEqualTo("topic")
      assertThat(message.event).isEqualTo("event")
      assertThat(message.payload).isEqualTo(payload)
      assertThat(message.joinRef).isNull()
      assertThat(message.status).isEqualTo("ok")
    }
  }
}