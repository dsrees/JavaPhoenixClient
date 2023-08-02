package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MessageTest {

  @Nested
  @DisplayName("json parsing")
  inner class JsonParsing {

    @Test
    internal fun `jsonParsing parses normal message`() {
      val json = """
        [null,"6","my-topic","update",{"user":"James S.","message":"This is a test"}]
      """.trimIndent()

      val message = Defaults.decode.invoke(json)

      assertThat(message.ref).isEqualTo("6")
      assertThat(message.topic).isEqualTo("my-topic")
      assertThat(message.event).isEqualTo("update")
      assertThat(message.payload).isEqualTo(mapOf("user" to "James S.", "message" to "This is a test"))
      assertThat(message.joinRef).isNull()
      assertThat(message.status).isNull()
    }

    @Test
    internal fun `jsonParsing parses a reply`() {
      val json = """
        [null,"6","my-topic","phx_reply",{"response":{"user":"James S.","message":"This is a test"},"status": "ok"}]
      """.trimIndent()

      val message = Defaults.decode.invoke(json)

      assertThat(message.ref).isEqualTo("6")
      assertThat(message.topic).isEqualTo("my-topic")
      assertThat(message.event).isEqualTo("phx_reply")
      assertThat(message.payload).isEqualTo(mapOf("user" to "James S.", "message" to "This is a test"))
      assertThat(message.joinRef).isNull()
      assertThat(message.status).isEqualTo("ok")
    }
  }
}