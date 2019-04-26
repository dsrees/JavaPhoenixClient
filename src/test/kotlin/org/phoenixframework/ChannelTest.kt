package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor

class ChannelTest {

  @Mock lateinit var socket: Socket
  @Mock lateinit var timerPool: ScheduledThreadPoolExecutor
  @Mock lateinit var scheduledFuture: ScheduledFuture<*>
  @Mock lateinit var reconnectAfterMs: (Int) -> Long

  private val kDefaultRef = "1"
  private val kDefaultTimeout = 10_000L
  private val kDefaultPayload: Payload = mapOf("one" to "two")

  lateinit var channel: Channel

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    whenever(timerPool.schedule(any(), any(), any())).thenReturn(scheduledFuture)

    whenever(socket.timerPool).thenReturn(timerPool)
    whenever(socket.makeRef()).thenReturn(kDefaultRef)
    whenever(socket.timeout).thenReturn(kDefaultTimeout)
    whenever(socket.reconnectAfterMs).thenReturn(reconnectAfterMs)

    channel = Channel("topic", kDefaultPayload, socket)
  }

  /* constructor */
  @Test
  fun `constructor() sets defaults`() {
    assertThat(channel.state).isEqualTo(Channel.State.CLOSED)
    assertThat(channel.topic).isEqualTo("topic")
    assertThat(channel.params["one"]).isEqualTo("two")
    assertThat(channel.socket).isEqualTo(socket)
    assertThat(channel.timeout).isEqualTo(10_000L)
    assertThat(channel.joinedOnce).isFalse()
    assertThat(channel.joinPush).isNotNull()
    assertThat(channel.pushBuffer).isEmpty()
  }

  @Test
  fun `constructor() sets up joinPush with literal params`() {
    val joinPush = channel.joinPush

    assertThat(joinPush.channel).isEqualTo(channel)
    assertThat(joinPush.payload["one"]).isEqualTo("two")
    assertThat(joinPush.event).isEqualTo("phx_join")
    assertThat(joinPush.timeout).isEqualTo(10_000L)
  }

  /* onMessage */
  @Test
  fun `onMessage() returns message by default`() {
    val message = channel.onMessage.invoke(Message(ref = "original"))
    assertThat(message.ref).isEqualTo("original")
  }

  @Test
  fun `onMessage() can be overidden`() {
    channel.onMessage { Message(ref = "changed") }

    val message = channel.onMessage.invoke(Message(ref = "original"))
    assertThat(message.ref).isEqualTo("changed")
  }

  /* join params */
  @Test
  fun `updating join params`() {
    val params = mapOf("value" to 1)
    val change = mapOf("value" to 2)

    channel = Channel("topic", params, socket)
    val joinPush = channel.joinPush

    assertThat(joinPush.channel).isEqualTo(channel)
    assertThat(joinPush.payload["value"]).isEqualTo(1)
    assertThat(joinPush.event).isEqualTo("phx_join")
    assertThat(joinPush.timeout).isEqualTo(10_000L)

    channel.params = change
    assertThat(joinPush.channel).isEqualTo(channel)
    assertThat(joinPush.payload["value"]).isEqualTo(2)
    assertThat(channel.params["value"]).isEqualTo(2)
    assertThat(joinPush.event).isEqualTo("phx_join")
    assertThat(joinPush.timeout).isEqualTo(10_000L)
  }

  /* join */
  @Test
  fun `join() sets state to joining"`() {
    channel.join()
    assertThat(channel.state).isEqualTo(Channel.State.JOINING)
  }

  @Test
  fun `join() sets joinedOnce to true`() {
    assertThat(channel.joinedOnce).isFalse()

    channel.join()
    assertThat(channel.joinedOnce).isTrue()
  }

  @Test
  fun `join() throws if attempting to join multiple times`() {
    var exceptionThrown = false
    try {
      channel.join()
      channel.join()
    } catch (e: Exception) {
      exceptionThrown = true
      assertThat(e).isInstanceOf(IllegalStateException::class.java)
      assertThat(e.message).isEqualTo(
          "Tried to join channel multiple times. `join()` can only be called once per channel")
    }

    assertThat(exceptionThrown).isTrue()
  }

  @Test
  fun `join() triggers socket push with channel params`() {
    channel.join()
    verify(socket).push("topic", "phx_join", kDefaultPayload, kDefaultRef, channel.joinRef)
  }

  @Test
  fun `join() can set timeout on joinPush`() {
    val newTimeout = 20_000L
    val joinPush = channel.joinPush

    assertThat(joinPush.timeout).isEqualTo(kDefaultTimeout)
    channel.join(newTimeout)
    assertThat(joinPush.timeout).isEqualTo(newTimeout)
  }

  /* timeout */
  @Test
  fun `succeeds before timeout`() {
    channel.join()
    verify(socket).push(any(), any(), any(), any(), any())




  }
}