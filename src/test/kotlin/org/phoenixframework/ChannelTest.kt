package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations
import org.phoenixframework.queue.ManualDispatchQueue
import org.phoenixframework.utilities.getBindings

class ChannelTest {


  @Mock lateinit var okHttpClient: OkHttpClient

  @Mock lateinit var socket: Socket
  @Mock lateinit var mockCallback: ((Message) -> Unit)

  private val kDefaultRef = "1"
  private val kDefaultTimeout = 10_000L
  private val kDefaultPayload: Payload = mapOf("one" to "two")
  private val kEmptyPayload: Payload = mapOf()

  lateinit var fakeClock: ManualDispatchQueue
  lateinit var channel: Channel

  @BeforeEach
  internal fun setUp() {
    MockitoAnnotations.initMocks(this)

    fakeClock = ManualDispatchQueue()

    whenever(socket.dispatchQueue).thenReturn(fakeClock)
    whenever(socket.makeRef()).thenReturn(kDefaultRef)
    whenever(socket.timeout).thenReturn(kDefaultTimeout)
    whenever(socket.reconnectAfterMs).thenReturn(Defaults.reconnectSteppedBackOff)
    whenever(socket.rejoinAfterMs).thenReturn(Defaults.rejoinSteppedBackOff)

    channel = Channel("topic", kDefaultPayload, socket)
  }

  @AfterEach
  internal fun tearDown() {
    fakeClock.reset()
  }

  @Nested
  @DisplayName("ChannelEvent")
  inner class ChannelEvent {
    @Test
    internal fun `isLifecycleEvent returns true for lifecycle events`() {
      assertThat(Channel.Event.isLifecycleEvent(Channel.Event.HEARTBEAT.value)).isFalse()
      assertThat(Channel.Event.isLifecycleEvent(Channel.Event.JOIN.value)).isTrue()
      assertThat(Channel.Event.isLifecycleEvent(Channel.Event.LEAVE.value)).isTrue()
      assertThat(Channel.Event.isLifecycleEvent(Channel.Event.REPLY.value)).isTrue()
      assertThat(Channel.Event.isLifecycleEvent(Channel.Event.ERROR.value)).isTrue()
      assertThat(Channel.Event.isLifecycleEvent(Channel.Event.CLOSE.value)).isTrue()
      assertThat(Channel.Event.isLifecycleEvent("random")).isFalse()
    }

    /* End ChannelEvent */
  }

  @Nested
  @DisplayName("constructor")
  inner class Constructor {
    @Test
    internal fun `sets defaults`() {
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
    internal fun `sets up joinPush with literal params`() {
      val joinPush = channel.joinPush

      assertThat(joinPush.channel).isEqualTo(channel)
      assertThat(joinPush.payload["one"]).isEqualTo("two")
      assertThat(joinPush.event).isEqualTo("phx_join")
      assertThat(joinPush.timeout).isEqualTo(10_000L)
    }

    /* End Constructor */
  }

  @Nested
  @DisplayName("onMessage")
  inner class OnMessage {
    @Test
    internal fun `returns message by default`() {
      val message = channel.onMessage.invoke(Message(ref = "original"))
      assertThat(message.ref).isEqualTo("original")
    }

    @Test
    internal fun `can be overidden`() {
      channel.onMessage { Message(ref = "changed") }

      val message = channel.onMessage.invoke(Message(ref = "original"))
      assertThat(message.ref).isEqualTo("changed")
    }

    /* End OnMessage */
  }

  @Nested
  @DisplayName("join params")
  inner class JoinParams {
    @Test
    internal fun `updating join params`() {
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

    /* End JoinParams */
  }

  @Nested
  @DisplayName("join")
  inner class Join {

    @BeforeEach
    internal fun setUp() {
      socket = spy(Socket(url ="https://localhost:4000/socket", client = okHttpClient))
      socket.dispatchQueue = fakeClock
      channel = Channel("topic", kDefaultPayload, socket)
    }

    @Test
    internal fun `sets state to joining`() {
      channel.join()
      assertThat(channel.state).isEqualTo(Channel.State.JOINING)
    }

    @Test
    internal fun `sets joinedOnce to true`() {
      assertThat(channel.joinedOnce).isFalse()

      channel.join()
      assertThat(channel.joinedOnce).isTrue()
    }

    @Test
    internal fun `throws if attempting to join multiple times`() {
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
    internal fun `triggers socket push with channel params`() {
      channel.join()
      verify(socket).push("topic", "phx_join", kDefaultPayload, kDefaultRef, channel.joinRef)
    }

    @Test
    internal fun `can set timeout on joinPush`() {
      val newTimeout = 20_000L
      val joinPush = channel.joinPush

      assertThat(joinPush.timeout).isEqualTo(kDefaultTimeout)
      channel.join(newTimeout)
      assertThat(joinPush.timeout).isEqualTo(newTimeout)
    }

    @Nested
    @DisplayName("timeout behavior")
    inner class TimeoutBehavior {

      private lateinit var joinPush: Push

      private fun receiveSocketOpen() {
        whenever(socket.isConnected).thenReturn(true)
        socket.onConnectionOpened()
      }

      @BeforeEach
      internal fun setUp() {
        joinPush = channel.joinPush
      }

      @Test
      internal fun `succeeds before timeout`() {
        val timeout = channel.timeout

        socket.connect()
        this.receiveSocketOpen()

        channel.join()
        verify(socket).push(any(), any(), any(), any(), any())
        assertThat(channel.timeout).isEqualTo(10_000)

        fakeClock.tick(100)

        joinPush.trigger("ok", kEmptyPayload)
        assertThat(channel.state).isEqualTo(Channel.State.JOINED)

        fakeClock.tick(timeout)
        verify(socket, times(1)).push(any(), any(), any(), any(), any())
      }

      @Test
      internal fun `retries with backoff after timeout`() {
        val timeout = channel.timeout

        socket.connect()
        this.receiveSocketOpen()

        channel.join().receive("timeout", mockCallback)

        verify(socket, times(1)).push(any(), eq("phx_join"), any(), any(), any())
        verify(mockCallback, never()).invoke(any())

        fakeClock.tick(timeout) // leave pushed to server
        verify(socket, times(1)).push(any(), eq("phx_leave"), any(), any(), any())
        verify(mockCallback, times(1)).invoke(any())

        fakeClock.tick(timeout + 1000) // rejoin
        verify(socket, times(2)).push(any(), eq("phx_join"), any(), any(), any())
        verify(socket, times(2)).push(any(), eq("phx_leave"), any(), any(), any())
        verify(mockCallback, times(2)).invoke(any())

        fakeClock.tick(10_000)
        joinPush.trigger("ok", kEmptyPayload)
        verify(socket, times(3)).push(any(), eq("phx_join"), any(), any(), any())
        assertThat(channel.state).isEqualTo(Channel.State.JOINED)
      }

      @Test
      internal fun `with socket and join delay`() {
        val joinPush = channel.joinPush

        channel.join()
        verify(socket, times(1)).push(any(), any(), any(), any(), any())

        // Open the socket after a delay
        fakeClock.tick(9_000)
        verify(socket, times(1)).push(any(), any(), any(), any(), any())

        // join request returns between timeouts
        fakeClock.tick(1_000)
        socket.connect()

        assertThat(channel.state).isEqualTo(Channel.State.ERRORED)
        this.receiveSocketOpen()
        joinPush.trigger("ok", kEmptyPayload)

        fakeClock.tick(1_000)
        assertThat(channel.state).isEqualTo(Channel.State.JOINED)

        verify(socket, times(3)).push(any(), any(), any(), any(), any())
      }

      @Test
      internal fun `with socket delay only`() {
        val joinPush = channel.joinPush

        channel.join()
        assertThat(channel.state).isEqualTo(Channel.State.JOINING)

        // connect socket after a delay
        fakeClock.tick(6_000)
        socket.connect()

        // open socket after delay
        fakeClock.tick(5_000)
        this.receiveSocketOpen()
        joinPush.trigger("ok", kEmptyPayload)

        joinPush.trigger("ok", kEmptyPayload)
        assertThat(channel.state).isEqualTo(Channel.State.JOINED)
      }

      /* End TimeoutBehavior */
    }

    /* End Join */
  }

  @Nested
  @DisplayName("joinPush")
  inner class JoinPush {

    private lateinit var joinPush: Push

    /* setup */
    @BeforeEach
    internal fun setUp() {
      socket = spy(Socket("https://localhost:4000/socket"))
      socket.dispatchQueue = fakeClock

      whenever(socket.isConnected).thenReturn(true)

      channel = Channel("topic", kDefaultPayload, socket)
      joinPush = channel.joinPush

      channel.join()
    }

    /* helper methods */
    private fun receivesOk() {
      fakeClock.tick(joinPush.timeout / 2)
      joinPush.trigger("ok", mapOf("a" to "b"))
    }

    private fun receivesTimeout() {
      fakeClock.tick(joinPush.timeout * 2)
    }

    private fun receivesError() {
      fakeClock.tick(joinPush.timeout / 2)
      joinPush.trigger("error", mapOf("a" to "b"))
    }

    @Nested
    @DisplayName("receives 'ok'")
    inner class ReceivesOk {
      @Test
      internal fun `sets channel state to joined`() {
        assertThat(channel.state).isNotEqualTo(Channel.State.JOINED)

        receivesOk()
        assertThat(channel.state).isEqualTo(Channel.State.JOINED)
      }

      @Test
      internal fun `triggers receive(ok) callback after ok response`() {
        joinPush.receive("ok", mockCallback)

        receivesOk()
        verify(mockCallback, times(1)).invoke(any())
      }

      @Test
      internal fun `triggers receive('ok') callback if ok response already received`() {
        receivesOk()
        joinPush.receive("ok", mockCallback)

        verify(mockCallback, times(1)).invoke(any())
      }

      @Test
      internal fun `does not trigger other receive callbacks after ok response`() {
        joinPush
            .receive("error", mockCallback)
            .receive("timeout", mockCallback)

        receivesOk()
        receivesTimeout()
        verify(mockCallback, times(0)).invoke(any())
      }

      @Test
      internal fun `clears timeoutTimer workItem`() {
        assertThat(joinPush.timeoutTask).isNotNull()

        val mockTimeoutTask = mock<DispatchWorkItem>()
        joinPush.timeoutTask = mockTimeoutTask

        receivesOk()
        verify(mockTimeoutTask).cancel()
        assertThat(joinPush.timeoutTask).isNull()
      }

      @Test
      internal fun `sets receivedMessage`() {
        assertThat(joinPush.receivedMessage).isNull()

        receivesOk()
        assertThat(joinPush.receivedMessage?.payload).isEqualTo(mapOf("status" to "ok", "a" to "b"))
        assertThat(joinPush.receivedMessage?.status).isEqualTo("ok")
      }

      @Test
      internal fun `removes channel binding`() {
        var bindings = channel.getBindings("chan_reply_1")
        assertThat(bindings).hasSize(1)

        receivesOk()
        bindings = channel.getBindings("chan_reply_1")
        assertThat(bindings).isEmpty()
      }

      @Test
      internal fun `resets channel rejoinTimer`() {
        val mockRejoinTimer = mock<TimeoutTimer>()
        channel.rejoinTimer = mockRejoinTimer

        receivesOk()
        verify(mockRejoinTimer, times(1)).reset()
      }

      @Test
      internal fun `sends and empties channel's buffered pushEvents`() {
        val mockPush = mock<Push>()
        channel.pushBuffer.add(mockPush)

        receivesOk()
        verify(mockPush).send()
        assertThat(channel.pushBuffer).isEmpty()
      }

      /* End ReceivesOk */
    }

    @Nested
    @DisplayName("receives 'timeout'")
    inner class ReceivesTimeout {
      @Test
      internal fun `sets channel state to errored`() {
        var timeoutReceived = false
        joinPush.receive("timeout") {
          timeoutReceived = true
          assertThat(channel.state).isEqualTo(Channel.State.ERRORED)
        }

        receivesTimeout()
        assertThat(timeoutReceived).isTrue()
      }

      @Test
      internal fun `triggers receive('timeout') callback after ok response`() {
        val mockCallback = mock<(Message) -> Unit>()
        joinPush.receive("timeout", mockCallback)

        receivesTimeout()
        verify(mockCallback).invoke(any())
      }

      @Test
      internal fun `does not trigger other receive callbacks after timeout response`() {
        val mockOk = mock<(Message) -> Unit>()
        val mockError = mock<(Message) -> Unit>()
        var timeoutReceived = false

        joinPush
            .receive("ok", mockOk)
            .receive("error", mockError)
            .receive("timeout") {
              verifyZeroInteractions(mockOk)
              verifyZeroInteractions(mockError)
              timeoutReceived = true
            }

        receivesTimeout()
        receivesOk()

        assertThat(timeoutReceived).isTrue()
      }

      @Test
      internal fun `schedules rejoinTimer timeout`() {
        val mockTimer = mock<TimeoutTimer>()
        channel.rejoinTimer = mockTimer

        receivesTimeout()
        verify(mockTimer).scheduleTimeout()
      }

      /* End ReceivesTimeout */
    }

    @Nested
    @DisplayName("receives 'error'")
    inner class ReceivesError {
      @Test
      internal fun `triggers receive('error') callback after error response`() {
        assertThat(channel.state).isEqualTo(Channel.State.JOINING)
        joinPush.receive("error", mockCallback)

        receivesError()
        joinPush.trigger("error", kEmptyPayload)
        verify(mockCallback, times(1)).invoke(any())
      }

      @Test
      internal fun `triggers receive('error') callback if error response already received`() {
        receivesError()

        joinPush.receive("error", mockCallback)

        verify(mockCallback).invoke(any())
      }

      @Test
      internal fun `does not trigger other receive callbacks after ok response`() {
        val mockOk = mock<(Message) -> Unit>()
        val mockError = mock<(Message) -> Unit>()
        val mockTimeout = mock<(Message) -> Unit>()
        joinPush
            .receive("ok", mockOk)
            .receive("error") {
              mockError.invoke(it)
              channel.leave()
            }
            .receive("timeout", mockTimeout)

        receivesError()
        receivesTimeout()

        verify(mockError, times(1)).invoke(any())
        verifyZeroInteractions(mockOk)
        verifyZeroInteractions(mockTimeout)
      }

      @Test
      internal fun `clears timeoutTimer workItem`() {
        val mockTask = mock<DispatchWorkItem>()
        assertThat(joinPush.timeoutTask).isNotNull()

        joinPush.timeoutTask = mockTask
        receivesError()

        verify(mockTask).cancel()
        assertThat(joinPush.timeoutTask).isNull()
      }

      @Test
      internal fun `sets receivedMessage`() {
        assertThat(joinPush.receivedMessage).isNull()

        receivesError()
        assertThat(joinPush.receivedMessage).isNotNull()
        assertThat(joinPush.receivedMessage?.status).isEqualTo("error")
        assertThat(joinPush.receivedMessage?.payload?.get("a")).isEqualTo("b")
      }

      @Test
      internal fun `removes channel binding`() {
        var bindings = channel.getBindings("chan_reply_1")
        assertThat(bindings).hasSize(1)

        receivesError()
        bindings = channel.getBindings("chan_reply_1")
        assertThat(bindings).isEmpty()
      }

      @Test
      internal fun `does not sets channel state to joined`() {
        receivesError()
        assertThat(channel.state).isNotEqualTo(Channel.State.JOINED)
      }

      @Test
      internal fun `does not trigger channel's buffered pushEvents`() {
        val mockPush = mock<Push>()
        channel.pushBuffer.add(mockPush)

        receivesError()
        verifyZeroInteractions(mockPush)
        assertThat(channel.pushBuffer).hasSize(1)
      }

      /* End ReceivesError */
    }

    /* End JoinPush */
  }

  @Nested
  @DisplayName("onError")
  inner class OnError {

    private lateinit var joinPush: Push

    /* setup */
    @BeforeEach
    internal fun setUp() {
      socket = spy(Socket("https://localhost:4000/socket"))
      socket.dispatchQueue = fakeClock

      whenever(socket.isConnected).thenReturn(true)

      channel = Channel("topic", kDefaultPayload, socket)
      joinPush = channel.joinPush

      channel.join()
      joinPush.trigger("ok", kEmptyPayload)
    }


    @Test
    internal fun `sets channel state to errored`() {
      assertThat(channel.state).isNotEqualTo(Channel.State.ERRORED)

      channel.trigger(Channel.Event.ERROR)
      assertThat(channel.state).isEqualTo(Channel.State.ERRORED)
    }

    @Test
    internal fun `does not trigger redundant errors during backoff`() {
      // Spy the channel's join push
      joinPush = spy(channel.joinPush)
      channel.joinPush = joinPush

      verify(joinPush, times(0)).send()

      channel.trigger(Channel.Event.ERROR)

      fakeClock.tick(1000)
      verify(joinPush, times(1)).send()

      channel.trigger("error")

      fakeClock.tick(1000)
      verify(joinPush, times(1)).send()
    }

    @Test
    internal fun `tries to rejoin with backoff`() {
      val mockTimer = mock<TimeoutTimer>()
      channel.rejoinTimer = mockTimer

      channel.trigger(Channel.Event.ERROR)
      verify(mockTimer).scheduleTimeout()
    }

    @Test
    internal fun `does not rejoin if leaving channel`() {
      channel.state = Channel.State.LEAVING

      // Spy the joinPush
      joinPush = spy(channel.joinPush)
      channel.joinPush = joinPush

      socket.onConnectionError(Throwable(), null)

      fakeClock.tick(1_000)
      verify(joinPush, never()).send()

      fakeClock.tick(2_000)
      verify(joinPush, never()).send()

      assertThat(channel.state).isEqualTo(Channel.State.LEAVING)
    }

    @Test
    internal fun `does not rejoin if channel is closed`() {
      channel.state = Channel.State.CLOSED

      // Spy the joinPush
      joinPush = spy(channel.joinPush)
      channel.joinPush = joinPush

      socket.onConnectionError(Throwable(), null)

      fakeClock.tick(1_000)
      verify(joinPush, never()).send()

      fakeClock.tick(2_000)
      verify(joinPush, never()).send()

      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)
    }

    @Test
    internal fun `triggers additional callbacks after join`() {
      channel.onError(mockCallback)
      joinPush.trigger("ok", kEmptyPayload)

      assertThat(channel.state).isEqualTo(Channel.State.JOINED)
      verifyZeroInteractions(mockCallback)

      channel.trigger(Channel.Event.ERROR)
      verify(mockCallback, times(1)).invoke(any())
    }

    /* End OnError */
  }

  @Nested
  @DisplayName("onClose")
  inner class OnClose {

    private lateinit var joinPush: Push

    /* setup */
    @BeforeEach
    internal fun setUp() {
      socket = spy(Socket("https://localhost:4000/socket"))
      socket.dispatchQueue = fakeClock

      whenever(socket.isConnected).thenReturn(true)

      channel = Channel("topic", kDefaultPayload, socket)
      joinPush = channel.joinPush

      channel.join()
    }


    @Test
    internal fun `sets state to closed`() {
      assertThat(channel.state).isNotEqualTo(Channel.State.CLOSED)

      channel.trigger(Channel.Event.CLOSE)
      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)
    }

    @Test
    internal fun `does not rejoin`() {
      // Spy the channel's join push
      joinPush = spy(channel.joinPush)
      channel.joinPush = joinPush

      channel.trigger(Channel.Event.CLOSE)

      fakeClock.tick(1_000)
      verify(joinPush, never()).send()

      fakeClock.tick(2_000)
      verify(joinPush, never()).send()
    }

    @Test
    internal fun `resets the rejoin timer`() {
      val mockTimer = mock<TimeoutTimer>()
      channel.rejoinTimer = mockTimer

      channel.trigger(Channel.Event.CLOSE)
      verify(mockTimer).reset()
    }

    @Test
    internal fun `removes channel from socket`() {
      channel.trigger(Channel.Event.CLOSE)
      verify(socket).remove(channel)
    }

    @Test
    internal fun `triggers additional callbacks`() {
      channel.onClose(mockCallback)
      verifyZeroInteractions(mockCallback)

      channel.trigger(Channel.Event.CLOSE)
      verify(mockCallback, times(1)).invoke(any())
    }

    /* End OnClose */
  }

  @Nested
  @DisplayName("canPush")
  inner class CanPush {
    @Test
    internal fun `returns true when socket connected and channel joined`() {
      channel.state = Channel.State.JOINED
      whenever(socket.isConnected).thenReturn(true)

      assertThat(channel.canPush).isTrue()
    }

    @Test
    internal fun `otherwise returns false`() {
      channel.state = Channel.State.JOINED
      whenever(socket.isConnected).thenReturn(false)
      assertThat(channel.canPush).isFalse()

      channel.state = Channel.State.JOINING
      whenever(socket.isConnected).thenReturn(true)
      assertThat(channel.canPush).isFalse()

      channel.state = Channel.State.JOINING
      whenever(socket.isConnected).thenReturn(false)
      assertThat(channel.canPush).isFalse()
    }

    /* End CanPush */
  }

  @Nested
  @DisplayName("on(event, callback)")
  inner class OnEventCallback {
    @Test
    internal fun `sets up callback for event`() {
      channel.trigger(event = "event", ref = kDefaultRef)

      channel.on("event", mockCallback)
      channel.trigger(event = "event", ref = kDefaultRef)
      verify(mockCallback, times(1)).invoke(any())
    }

    @Test
    internal fun `other event callbacks are ignored`() {
      val mockIgnoredCallback = mock<(Message) -> Unit>()

      channel.on("ignored_event", mockIgnoredCallback)
      channel.trigger(event = "event", ref = kDefaultRef)

      channel.on("event", mockCallback)
      channel.trigger(event = "event", ref = kDefaultRef)

      verify(mockIgnoredCallback, never()).invoke(any())
    }

    @Test
    internal fun `generates unique refs for callbacks`() {
      val ref1 = channel.on("event1") {}
      val ref2 = channel.on("event2") {}

      assertThat(ref1).isNotEqualTo(ref2)
      assertThat(ref1 + 1).isEqualTo(ref2)
    }

    /* End OnEventCallback */
  }

  @Nested
  @DisplayName("off")
  inner class Off {
    @Test
    internal fun `removes all callbacks for event`() {
      val callback1 = mock<(Message) -> Unit>()
      val callback2 = mock<(Message) -> Unit>()
      val callback3 = mock<(Message) -> Unit>()

      channel.on("event", callback1)
      channel.on("event", callback2)
      channel.on("other", callback3)

      channel.off("event")
      channel.trigger(event = "event", ref = kDefaultRef)
      channel.trigger(event = "other", ref = kDefaultRef)

      verifyZeroInteractions(callback1)
      verifyZeroInteractions(callback2)
      verify(callback3, times(1)).invoke(any())
    }

    @Test
    internal fun `removes callback by ref`() {
      val callback1 = mock<(Message) -> Unit>()
      val callback2 = mock<(Message) -> Unit>()

      val ref1 = channel.on("event", callback1)
      channel.on("event", callback2)

      channel.off("event", ref1)
      channel.trigger(event = "event", ref = kDefaultRef)

      verifyZeroInteractions(callback1)
      verify(callback2, times(1)).invoke(any())
    }

    /* End Off */
  }

  @Nested
  @DisplayName("push")
  inner class PushFunction {

    @BeforeEach
    internal fun setUp() {
      whenever(socket.isConnected).thenReturn(true)
    }

    @Test
    internal fun `sends push event when successfully joined`() {
      channel.join().trigger("ok", kEmptyPayload)
      channel.push("event", mapOf("foo" to "bar"))

      verify(socket).push("topic", "event", mapOf("foo" to "bar"), channel.joinRef, kDefaultRef)
    }

    @Test
    internal fun `enqueues push event to be sent once join has succeeded`() {
      val joinPush = channel.join()
      channel.push("event", mapOf("foo" to "bar"))

      verify(socket, never()).push(any(), any(), eq(mapOf("foo" to "bar")), any(), any())

      fakeClock.tick(channel.timeout / 2)
      joinPush.trigger("ok", kEmptyPayload)

      verify(socket).push(any(), any(), eq(mapOf("foo" to "bar")), any(), any())
    }

    @Test
    internal fun `does not push if channel join times out`() {
      val joinPush = channel.join()
      channel.push("event", mapOf("foo" to "bar"))

      verify(socket, never()).push(any(), any(), eq(mapOf("foo" to "bar")), any(), any())

      fakeClock.tick(channel.timeout * 2)
      joinPush.trigger("ok", kEmptyPayload)

      verify(socket, never()).push(any(), any(), eq(mapOf("foo" to "bar")), any(), any())
    }

    @Test
    internal fun `uses channel timeout by default`() {
      channel.join().trigger("ok", kEmptyPayload)
      channel
          .push("event", mapOf("foo" to "bar"))
          .receive("timeout", mockCallback)

      fakeClock.tick(channel.timeout / 2)
      verifyZeroInteractions(mockCallback)

      fakeClock.tick(channel.timeout)
      verify(mockCallback).invoke(any())
    }

    @Test
    internal fun `accepts timeout arg`() {
      channel.join().trigger("ok", kEmptyPayload)
      channel
          .push("event", mapOf("foo" to "bar"), channel.timeout * 2)
          .receive("timeout", mockCallback)

      fakeClock.tick(channel.timeout)
      verifyZeroInteractions(mockCallback)

      fakeClock.tick(channel.timeout * 2)
      verify(mockCallback).invoke(any())
    }

    @Test
    internal fun `does not time out after receiving 'ok'`() {
      channel.join().trigger("ok", kEmptyPayload)
      val push = channel
          .push("event", mapOf("foo" to "bar"), channel.timeout * 2)
          .receive("timeout", mockCallback)

      fakeClock.tick(channel.timeout / 2)
      verifyZeroInteractions(mockCallback)

      push.trigger("ok", kEmptyPayload)

      fakeClock.tick(channel.timeout)
      verifyZeroInteractions(mockCallback)
    }

    @Test
    internal fun `throws if channel has not been joined`() {
      var exceptionThrown = false
      try {
        channel.push("event", kEmptyPayload)
      } catch (e: Exception) {
        exceptionThrown = true
        assertThat(e.message).isEqualTo(
            "Tried to push event to topic before joining. Use channel.join() before pushing events")
      }

      assertThat(exceptionThrown).isTrue()
    }

    /* End PushFunction */
  }

  @Nested
  @DisplayName("leave")
  inner class Leave {
    @BeforeEach
    internal fun setUp() {
      whenever(socket.isConnected).thenReturn(true)
      channel.join().trigger("ok", kEmptyPayload)
    }

    @Test
    internal fun `unsubscribes from server events`() {
      val joinRef = channel.joinRef
      channel.leave()

      verify(socket).push("topic", "phx_leave", emptyMap(), joinRef, kDefaultRef)
    }

    @Test
    internal fun `closes channel on 'ok' from server`() {
      channel.leave().trigger("ok", kEmptyPayload)
      verify(socket).remove(channel)
    }

    @Test
    internal fun `sets state to closed on 'ok' event`() {
      channel.leave().trigger("ok", kEmptyPayload)
      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)
    }

    @Test
    internal fun `sets state to leaving initially`() {
      channel.leave()
      assertThat(channel.state).isEqualTo(Channel.State.LEAVING)
    }

    @Test
    internal fun `closes channel on timeout`() {
      channel.leave()
      assertThat(channel.state).isEqualTo(Channel.State.LEAVING)

      fakeClock.tick(channel.timeout)
      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)
    }

    @Test
    internal fun `triggers immediately if cannot push`() {
      whenever(socket.isConnected).thenReturn(false)
      channel.leave()
      assertThat(channel.state).isEqualTo(Channel.State.CLOSED)
    }
    /* End Leave */
  }

  @Nested
  @DisplayName("state accessors")
  inner class StateAccessors {
    @Test
    fun `isClosed returns true if state is CLOSED`() {
      channel.state = Channel.State.JOINED
      assertThat(channel.isClosed).isFalse()

      channel.state = Channel.State.CLOSED
      assertThat(channel.isClosed).isTrue()
    }

    @Test
    fun `isErrored returns true if state is ERRORED`() {
      channel.state = Channel.State.JOINED
      assertThat(channel.isErrored).isFalse()

      channel.state = Channel.State.ERRORED
      assertThat(channel.isErrored).isTrue()
    }

    @Test
    fun `isJoined returns true if state is JOINED`() {
      channel.state = Channel.State.JOINING
      assertThat(channel.isJoined).isFalse()

      channel.state = Channel.State.JOINED
      assertThat(channel.isJoined).isTrue()
    }

    @Test
    fun `isJoining returns true if state is JOINING`() {
      channel.state = Channel.State.JOINED
      assertThat(channel.isJoining).isFalse()

      channel.state = Channel.State.JOINING
      assertThat(channel.isJoining).isTrue()
    }

    @Test
    fun `isLeaving returns true if state is LEAVING`() {
      channel.state = Channel.State.JOINED
      assertThat(channel.isLeaving).isFalse()

      channel.state = Channel.State.LEAVING
      assertThat(channel.isLeaving).isTrue()
    }
    /* End StateAccessors */
  }

  @Nested
  @DisplayName("isMember")
  inner class IsMember {

    @Test
    fun `returns false if topics are different`() {
      val message = Message(topic = "other-topic")
      assertThat(channel.isMember(message)).isFalse()
    }

    @Test
    fun `drops outdated messages`() {
      channel.joinPush.ref = "9"
      val message = Message(topic = "topic", event = Channel.Event.LEAVE.value, joinRef = "7")
      assertThat(channel.isMember(message)).isFalse()
    }

    @Test
    fun `returns true if message belongs to channel`() {
      val message = Message(topic = "topic", event = "msg:new")
      assertThat(channel.isMember(message)).isTrue()
    }

    /* End IsMember */
  }

}