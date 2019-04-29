package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.stubbing.Answer

class ChannelTest {

  @Mock lateinit var socket: Socket


  private val kDefaultRef = "1"
  private val kDefaultTimeout = 10_000L
  private val kDefaultPayload: Payload = mapOf("one" to "two")
  private val kEmptyPayload: Payload = mapOf()
  private val reconnectAfterMs: (Int) -> Long = Defaults.steppedBackOff

  lateinit var fakeClock: ManualDispatchQueue
  lateinit var channel: Channel

  var mutableRef = 0
  var mutableRefAnswer: Answer<String> = Answer {
    mutableRef += 1
    mutableRef.toString()
  }


  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    mutableRef = 0
    fakeClock = ManualDispatchQueue()

    whenever(socket.dispatchQueue).thenReturn(fakeClock)
    whenever(socket.makeRef()).thenReturn(kDefaultRef)
    whenever(socket.timeout).thenReturn(kDefaultTimeout)
    whenever(socket.reconnectAfterMs).thenReturn(reconnectAfterMs)

    channel = Channel("topic", kDefaultPayload, socket)
  }

  @After
  fun tearDown() {
    fakeClock.reset()
  }


  //------------------------------------------------------------------------------
  // Utility Methods
  //------------------------------------------------------------------------------
  /// Utility method to easily filter the bindings for a channel by their event
  private fun getBindings(event: String): List<Binding>? {
    return channel.bindings.toList().filter { it.event == event }
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

  /* timeout behavior */
  @Test
  fun `succeeds before timeout`() {
    val joinPush = channel.joinPush
    val timeout = channel.timeout

    channel.join()
    verify(socket).push(any(), any(), any(), any(), any())

    fakeClock.tick(timeout / 2)

    joinPush.trigger("ok", kEmptyPayload)
    assertThat(channel.state).isEqualTo(Channel.State.JOINED)

    fakeClock.tick(timeout)
    verify(socket, times(1)).push(any(), any(), any(), any(), any())
  }

  @Test
  fun `retries with backoff after timeout`() {
    var ref = 0
    whenever(socket.isConnected).thenReturn(true)
    whenever(socket.makeRef()).thenAnswer {
      ref += 1
      ref.toString()
    }

    val joinPush = channel.joinPush
    val timeout = channel.timeout

    channel.join()
    verify(socket, times(1)).push(any(), any(), any(), any(), any())

    fakeClock.tick(timeout) // leave push sent to the server
    verify(socket, times(2)).push(any(), any(), any(), any(), any())

    fakeClock.tick(1_000) // begin stepped backoff
    verify(socket, times(3)).push(any(), any(), any(), any(), any())

    fakeClock.tick(2_000)
    verify(socket, times(4)).push(any(), any(), any(), any(), any())

    fakeClock.tick(5_000)
    verify(socket, times(5)).push(any(), any(), any(), any(), any())

    fakeClock.tick(10_000)
    verify(socket, times(6)).push(any(), any(), any(), any(), any())

    joinPush.trigger("ok", kEmptyPayload)
    assertThat(channel.state).isEqualTo(Channel.State.JOINED)

    fakeClock.tick(10_000)
    verify(socket, times(6)).push(any(), any(), any(), any(), any())
    assertThat(channel.state).isEqualTo(Channel.State.JOINED)
  }

  @Test
  fun `with socket and join delay`() {
    whenever(socket.isConnected).thenReturn(false)
    val joinPush = channel.joinPush

    channel.join()
    verify(socket, times(1)).push(any(), any(), any(), any(), any())

    // Open the socket after a delay
    fakeClock.tick(9_000)
    verify(socket, times(1)).push(any(), any(), any(), any(), any())

    // join request returns between timeouts
    fakeClock.tick(1_000)

    whenever(socket.isConnected).thenReturn(true)
    joinPush.trigger("ok", kEmptyPayload)

    assertThat(channel.state).isEqualTo(Channel.State.ERRORED)

    fakeClock.tick(1_000)
    assertThat(channel.state).isEqualTo(Channel.State.JOINING)

    joinPush.trigger("ok", kEmptyPayload)
    assertThat(channel.state).isEqualTo(Channel.State.JOINED)

    verify(socket, times(3)).push(any(), any(), any(), any(), any())
  }

  @Test
  fun `with socket delay only`() {
    whenever(socket.isConnected).thenReturn(false)
    val joinPush = channel.joinPush

    channel.join()

    // connect socket after a delay
    fakeClock.tick(6_000)
    whenever(socket.isConnected).thenReturn(true)

    fakeClock.tick(5_000)
    joinPush.trigger("ok", kEmptyPayload)

    fakeClock.tick(2_000)
    assertThat(channel.state).isEqualTo(Channel.State.JOINING)

    joinPush.trigger("ok", kEmptyPayload)
    assertThat(channel.state).isEqualTo(Channel.State.JOINED)
  }

  /* Join Push */
  private fun setupJoinPushTests() {
    whenever(socket.isConnected).thenReturn(true)
    whenever(socket.makeRef()).thenAnswer(mutableRefAnswer)
    channel.join()
  }

  private fun receivesOk(joinPush: Push) {
    fakeClock.tick(joinPush.timeout / 2)
    joinPush.trigger("ok", mapOf("a" to "b"))
  }

  private fun receivesTimeout(joinPush: Push) {
    fakeClock.tick(joinPush.timeout * 2)
  }

  private fun receivesError(joinPush: Push) {
    fakeClock.tick(joinPush.timeout / 2)
    joinPush.trigger("error", mapOf("a" to "b"))
  }

  /* receives 'ok' */
  @Test
  fun `joinPush - receivesOk - sets channel state to joined`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    assertThat(channel.state).isNotEqualTo(Channel.State.JOINED)

    receivesOk(joinPush)
    assertThat(channel.state).isEqualTo(Channel.State.JOINED)
  }

  @Test
  fun `joinPush - receivesOk - triggers receive(ok) callback after ok response`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockCallback = mock<(Message) -> Unit>()
    joinPush.receive("ok", mockCallback)

    receivesOk(joinPush)
    verify(mockCallback, times(1)).invoke(any())
  }

  @Test
  fun `joinPush - receivesOk - triggers receive('ok') callback if ok response already received`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    receivesOk(joinPush)

    val mockCallback = mock<(Message) -> Unit>()
    joinPush.receive("ok", mockCallback)

    verify(mockCallback, times(1)).invoke(any())
  }

  @Test
  fun `joinPush - receivesOk - does not trigger other receive callbacks after ok response`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockCallback = mock<(Message) -> Unit>()
    joinPush
        .receive("error", mockCallback)
        .receive("timeout", mockCallback)

    receivesOk(joinPush)
    receivesTimeout(joinPush)
    verify(mockCallback, times(0)).invoke(any())
  }

  @Test
  fun `joinPush - receivesOk - clears timeoutTimer workItem`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    assertThat(joinPush.timeoutTask).isNotNull()

    val mockTimeoutTask = mock<DispatchWorkItem>()
    joinPush.timeoutTask = mockTimeoutTask

    receivesOk(joinPush)
    verify(mockTimeoutTask).cancel()
    assertThat(joinPush.timeoutTask).isNull()
  }

  @Test
  fun `joinPush - receivesOk - sets receivedMessage`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    assertThat(joinPush.receivedMessage).isNull()

    receivesOk(joinPush)
    assertThat(joinPush.receivedMessage?.payload).isEqualTo(mapOf("status" to "ok", "a" to "b"))
    assertThat(joinPush.receivedMessage?.status).isEqualTo("ok")
  }

  @Test
  fun `joinPush - receivesOk - removes channel binding`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    var bindings = getBindings("chan_reply_1")
    assertThat(bindings).hasSize(1)

    receivesOk(joinPush)
    bindings = getBindings("chan_reply_1")
    assertThat(bindings).isEmpty()
  }

  @Test
  fun `joinPush - receivesOk - resets channel rejoinTimer`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockRejoinTimer = mock<TimeoutTimer>()
    channel.rejoinTimer = mockRejoinTimer

    receivesOk(joinPush)
    verify(mockRejoinTimer, times(1)).reset()
  }

  @Test
  fun `joinPush - receivesOk - sends and empties channel's buffered pushEvents`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockPush = mock<Push>()
    channel.pushBuffer.add(mockPush)

    receivesOk(joinPush)
    verify(mockPush).send()
    assertThat(channel.pushBuffer).isEmpty()
  }

  /* receives 'timeout' */
  @Test
  fun `joinPush - receives 'timeout' - sets channel state to errored`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    receivesTimeout(joinPush)
    assertThat(channel.state).isEqualTo(Channel.State.ERRORED)
  }

  @Test
  fun `joinPush - receives 'timeout' - triggers receive('timeout') callback after ok response`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockCallback = mock<(Message) -> Unit>()
    joinPush.receive("timeout", mockCallback)

    receivesTimeout(joinPush)
    verify(mockCallback).invoke(any())
  }

  @Test
  fun `joinPush - receives 'timeout' - does not trigger other receive callbacks after timeout response`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockCallback = mock<(Message) -> Unit>()
    joinPush
        .receive("ok", mockCallback)
        .receive("error", mockCallback)

    receivesTimeout(joinPush)
    receivesOk(joinPush)
    verifyZeroInteractions(mockCallback)
  }

  @Test
  fun `joinPush - receives 'timeout' - schedules rejoinTimer timeout`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockTimer = mock<TimeoutTimer>()
    channel.rejoinTimer = mockTimer

    receivesTimeout(joinPush)
    verify(mockTimer).scheduleTimeout()
  }


  /* receives 'error' */
  @Test
  fun `joinPush - receives 'error' - triggers receive('error') callback after error response`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockCallback = mock<(Message) -> Unit>()
    joinPush.receive("error", mockCallback)

    receivesError(joinPush)
    verify(mockCallback).invoke(any())
  }

  @Test
  fun `joinPush - receives 'error' - triggers receive('error') callback if error response already received"`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    receivesError(joinPush)

    val mockCallback = mock<(Message) -> Unit>()
    joinPush.receive("error", mockCallback)

    verify(mockCallback).invoke(any())
  }

  @Test
  fun `joinPush - receives 'error' - does not trigger other receive callbacks after ok response`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockCallback = mock<(Message) -> Unit>()
    joinPush
        .receive("ok", mockCallback)
        .receive("timeout", mockCallback)

    receivesError(joinPush)
    receivesTimeout(joinPush)
    verifyZeroInteractions(mockCallback)
  }

  @Test
  fun `joinPush - receives 'error' - clears timeoutTimer workItem`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockTask = mock<DispatchWorkItem>()
    assertThat(joinPush.timeoutTask).isNotNull()

    joinPush.timeoutTask = mockTask
    receivesError(joinPush)

    verify(mockTask).cancel()
    assertThat(joinPush.timeoutTask).isNull()
  }

  @Test
  fun `joinPush - receives 'error' - sets receivedMessage`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    assertThat(joinPush.receivedMessage).isNull()

    receivesError(joinPush)
    assertThat(joinPush.receivedMessage).isNotNull()
    assertThat(joinPush.receivedMessage?.status).isEqualTo("error")
    assertThat(joinPush.receivedMessage?.payload?.get("a")).isEqualTo("b")
  }

  @Test
  fun `joinPush - receives 'error' - removes channel binding`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    var bindings = getBindings("chan_reply_1")
    assertThat(bindings).hasSize(1)

    receivesError(joinPush)
    bindings = getBindings("chan_reply_1")
    assertThat(bindings).isEmpty()
  }

  @Test
  fun `joinPush - receives 'error' - does not sets channel state to joined`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    receivesError(joinPush)
    assertThat(channel.state).isNotEqualTo(Channel.State.JOINED)
  }

  @Test
  fun `joinPush - receives 'error' - does not trigger channel's buffered pushEvents`() {
    setupJoinPushTests()
    val joinPush = channel.joinPush

    val mockPush = mock<Push>()
    channel.pushBuffer.add(mockPush)

    receivesError(joinPush)
    verifyZeroInteractions(mockPush)
    assertThat(channel.pushBuffer).hasSize(1)
  }
}