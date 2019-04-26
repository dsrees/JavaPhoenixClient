package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
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


  lateinit var channel: Channel

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    whenever(timerPool.schedule(any(), any(), any())).thenReturn(scheduledFuture)

    whenever(socket.timerPool).thenReturn(timerPool)
    whenever(socket.makeRef()).thenReturn(kDefaultRef)
    whenever(socket.timeout).thenReturn(kDefaultTimeout)
    whenever(socket.reconnectAfterMs).thenReturn(reconnectAfterMs)

    channel = Channel("topic", mapOf("one" to "two"), socket)
  }

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
}