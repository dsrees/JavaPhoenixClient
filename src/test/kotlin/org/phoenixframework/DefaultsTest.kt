package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class DefaultsTest {

  @Test
  internal fun `default timeout is 10_000`() {
    assertThat(Defaults.TIMEOUT).isEqualTo(10_000)
  }

  @Test
  internal fun `default heartbeat is 30_000`() {
    assertThat(Defaults.HEARTBEAT).isEqualTo(30_000)
  }

  @Test
  internal fun `default reconnectAfterMs returns all values`() {
    val reconnect = Defaults.reconnectSteppedBackOff

    assertThat(reconnect(1)).isEqualTo(10)
    assertThat(reconnect(2)).isEqualTo(50)
    assertThat(reconnect(3)).isEqualTo(100)
    assertThat(reconnect(4)).isEqualTo(150)
    assertThat(reconnect(5)).isEqualTo(200)
    assertThat(reconnect(6)).isEqualTo(250)
    assertThat(reconnect(7)).isEqualTo(500)
    assertThat(reconnect(8)).isEqualTo(1_000)
    assertThat(reconnect(9)).isEqualTo(2_000)
    assertThat(reconnect(10)).isEqualTo(5_000)
    assertThat(reconnect(11)).isEqualTo(5_000)
  }

  @Test
  internal fun `default rejoinAfterMs returns all values`() {
    val reconnect = Defaults.rejoinSteppedBackOff

    assertThat(reconnect(1)).isEqualTo(1_000)
    assertThat(reconnect(2)).isEqualTo(2_000)
    assertThat(reconnect(3)).isEqualTo(5_000)
    assertThat(reconnect(4)).isEqualTo(10_000)
    assertThat(reconnect(5)).isEqualTo(10_000)
  }

  @Test
  internal fun `decoder converts json array into message`() {
    val v2Json = """
      [null,null,"room:lobby","shout",{"message":"Hi","name":"Tester"}]
    """.trimIndent()

    val message = Defaults.decode(v2Json)
    assertThat(message.joinRef).isNull()
    assertThat(message.ref).isEqualTo("")
    assertThat(message.topic).isEqualTo("room:lobby")
    assertThat(message.event).isEqualTo("shout")
    assertThat(message.payload).isEqualTo(mapOf("message" to "Hi", "name" to "Tester"))
  }

  @Test
  internal fun `decoder provides raw json payload`() {
    val v2Json = """
      [1,2,"room:lobby","shout",{"message":"Hi","name":"Tester","count":15,"ratio":0.2}]
    """.trimIndent()

    val message = Defaults.decode(v2Json)
    assertThat(message.joinRef).isEqualTo("1")
    assertThat(message.ref).isEqualTo("2")
    assertThat(message.topic).isEqualTo("room:lobby")
    assertThat(message.event).isEqualTo("shout")
    assertThat(message.payloadJson).isEqualTo("{\"message\":\"Hi\",\"name\":\"Tester\",\"count\":15,\"ratio\":0.2}")
    assertThat(message.payload).isEqualTo(mapOf(
      "message" to "Hi",
      "name" to "Tester",
      "count" to 15.0, // Note that this is a bug and should eventually be removed
      "ratio" to 0.2
    ))
  }

  @Test
  internal fun `decoder decodes a status`() {
    val v2Json = """
      [1,2,"room:lobby","phx_reply",{"response":{"message":"Hi","name":"Tester","count":15,"ratio":0.2},"status":"ok"}]
    """.trimIndent()

    val message = Defaults.decode(v2Json)
    assertThat(message.joinRef).isEqualTo("1")
    assertThat(message.ref).isEqualTo("2")
    assertThat(message.topic).isEqualTo("room:lobby")
    assertThat(message.event).isEqualTo("phx_reply")
    assertThat(message.payloadJson).isEqualTo("{\"message\":\"Hi\",\"name\":\"Tester\",\"count\":15,\"ratio\":0.2}")
    assertThat(message.payload).isEqualTo(mapOf(
      "message" to "Hi",
      "name" to "Tester",
      "count" to 15.0, // Note that this is a bug and should eventually be removed
      "ratio" to 0.2
    ))
  }

  @Test
  internal fun `decoder decodes a non-json payload`() {
    val v2Json = """
      [1,2,"room:lobby","phx_reply",{"response":"hello","status":"ok"}]
    """.trimIndent()

    val message = Defaults.decode(v2Json)
    assertThat(message.payloadJson).isEqualTo("\"hello\"")
    assertThat(message.payload).isEqualTo(mapOf(
      "response" to "hello",
      "status" to "ok"
    ))
  }

  @Test
  internal fun `encode converts message into json`() {
    val body = listOf(null, null, "topic", "event", mapOf("one" to "two"))
    assertThat(Defaults.encode(body))
      .isEqualTo("[null,null,\"topic\",\"event\",{\"one\":\"two\"}]")
  }
}