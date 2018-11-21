package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PhxChannelTest {

    private val defaultRef = "1"

    @Spy
    var socket: PhxSocket = PhxSocket("http://localhost:4000/socket/websocket")
    lateinit var channel: PhxChannel

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.doReturn(defaultRef).`when`(socket).makeRef()

        socket.timeout = 1234
        channel = PhxChannel("topic", hashMapOf("one" to "two"), socket)
    }


    //------------------------------------------------------------------------------
    // Constructor
    //------------------------------------------------------------------------------
    @Test
    fun `constructor sets defaults`() {
        assertThat(channel.isClosed).isTrue()
        assertThat(channel.topic).isEqualTo("topic")
        assertThat(channel.params["one"]).isEqualTo("two")
        assertThat(channel.socket).isEqualTo(socket)
        assertThat(channel.timeout).isEqualTo(1234)
        assertThat(channel.joinedOnce).isFalse()
        assertThat(channel.pushBuffer).isEmpty()
    }

    @Test
    fun `constructor sets up joinPush with params`() {
        val joinPush = channel.joinPush

        assertThat(joinPush.channel).isEqualTo(channel)
        assertThat(joinPush.payload["one"]).isEqualTo("two")
        assertThat(joinPush.event).isEqualTo(PhxChannel.PhxEvent.JOIN.value)
        assertThat(joinPush.timeout).isEqualTo(1234)
    }


    //------------------------------------------------------------------------------
    // Join
    //------------------------------------------------------------------------------
    @Test
    fun `it sets the state to joining`() {
        channel.join()
        assertThat(channel.isJoining).isTrue()
    }

    @Test
    fun `it updates the join parameters`() {
        channel.join(hashMapOf("one" to "three"))

        val joinPush = channel.joinPush
        assertThat(joinPush.payload["one"]).isEqualTo("three")
    }

    @Test
    fun `it sets joinedOnce to true`() {
        assertThat(channel.joinedOnce).isFalse()

        channel.join()
        assertThat(channel.joinedOnce).isTrue()
    }

    @Test(expected = IllegalStateException::class)
    fun `it throws if attempting to join multiple times`() {
        channel.join()
        channel.join()
    }



    //------------------------------------------------------------------------------
    // .off()
    //------------------------------------------------------------------------------
    @Test
    fun `it removes all callbacks for events`() {
        Mockito.doReturn(defaultRef).`when`(socket).makeRef()

        var aCalled = false
        var bCalled = false
        var cCalled = false

        channel.on("event") { aCalled = true }
        channel.on("event") { bCalled = true }
        channel.on("other") { cCalled = true }

        channel.off("event")

        channel.trigger(PhxMessage(event = "event", ref = defaultRef))
        channel.trigger(PhxMessage(event = "other", ref = defaultRef))

        assertThat(aCalled).isFalse()
        assertThat(bCalled).isFalse()
        assertThat(cCalled).isTrue()
    }

    @Test
    fun `it removes callbacks by its ref`() {
        var aCalled = false
        var bCalled = false

        val aRef = channel.on("event") { aCalled = true }
        channel.on("event") { bCalled = true }


        channel.off("event", aRef)

        channel.trigger(PhxMessage(event = "event", ref = defaultRef))

        assertThat(aCalled).isFalse()
        assertThat(bCalled).isTrue()
    }

    @Test
    fun `Issue 22`() {
        // This reproduces a concurrent modification exception.  The original cause is most likely as follows:
        // 1.  Push (And receive) messages very quickly
        // 2.  PhxChannel.push, calls PhxPush.send()
        // 3.  PhxPush calls startTimeout().
        // 4.  PhxPush.startTimeout() calls this.channel.on(refEvent) - This modifies the bindings list
        // 5.  any trigger (possibly from a timeout) can be iterating through the binding list that was modified in step 4.

        val f1 = CompletableFuture.runAsync {
            for (i in 0..1000) {
                channel.on("event-$i") { /** do nothing **/ }
            }
        }
        val f3 = CompletableFuture.runAsync {
            for (i in 0..1000) {
                channel.trigger(PhxMessage(event = "event-$i", ref = defaultRef))
            }
        }

        CompletableFuture.allOf(f1, f3).get(10, TimeUnit.SECONDS)
    }
}
