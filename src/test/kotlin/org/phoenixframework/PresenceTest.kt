package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class PresenceTest {

  @Mock lateinit var socket: Socket

  val listByFirst: (Map.Entry<String, PresenceMap>) -> PresenceMeta =
      { it.value["metas"]!!.first() }

  lateinit var channel: Channel
  lateinit var presence: Presence

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    whenever(socket.timeout).thenReturn(Defaults.TIMEOUT)
    whenever(socket.makeRef()).thenReturn("1")
    whenever(socket.reconnectAfterMs).thenReturn { 1_000 }
    whenever(socket.dispatchQueue).thenReturn(mock())

    channel = Channel("topic", mapOf(), socket)
    channel.joinPush.ref = "1"

    presence = Presence(channel)
  }

  /* constructor */
  @Test
  fun `constructor() sets defaults`() {
    assertThat(presence.state).isEmpty()
    assertThat(presence.pendingDiffs).isEmpty()
    assertThat(presence.channel).isEqualTo(channel)
    assertThat(presence.joinRef).isNull()
  }

  @Test
  fun `constructor() binds to channel with default arguments`() {
    assertThat(presence.channel.getBindings("presence_state")).hasSize(1)
    assertThat(presence.channel.getBindings("presence_diff")).hasSize(1)
  }

  @Test
  fun `constructor() binds to channel with custom options`() {
    val channel = Channel("topic", mapOf(), socket)
    val customOptions = Presence.Options(mapOf(
        Presence.Events.STATE to "custom_state",
        Presence.Events.DIFF to "custom_diff"))

    val p = Presence(channel, customOptions)
    assertThat(p.channel.getBindings("presence_state")).isEmpty()
    assertThat(p.channel.getBindings("presence_diff")).isEmpty()
    assertThat(p.channel.getBindings("custom_state")).hasSize(1)
    assertThat(p.channel.getBindings("custom_diff")).hasSize(1)
  }

  @Test
  fun `constructor() syncs state and diffs`() {
    val user1: PresenceMap = mutableMapOf("metas" to mutableListOf(
        mutableMapOf("id" to 1, "phx_ref" to "1")))
    val user2: PresenceMap = mutableMapOf("metas" to mutableListOf(
        mutableMapOf("id" to 2, "phx_ref" to "2")))
    val newState: PresenceState = mutableMapOf("u1" to user1, "u2" to user2)

    channel.trigger("presence_state", newState, "1")
    val s = presence.listBy(listByFirst)
    assertThat(s).hasSize(2)
    assertThat(s[0]["id"]).isEqualTo(1)
    assertThat(s[0]["phx_ref"]).isEqualTo("1")

    assertThat(s[1]["id"]).isEqualTo(2)
    assertThat(s[1]["phx_ref"]).isEqualTo("2")

    channel.trigger("presence_diff", mapOf("joins" to emptyMap(), "leaves" to mapOf("u1" to user1)))
    val l = presence.listBy(listByFirst)
    assertThat(l).hasSize(1)
    assertThat(l[0]["id"]).isEqualTo(2)
    assertThat(l[0]["phx_ref"]).isEqualTo("2")
  }

  @Test
  fun `constructor() applies pending diff if state is not yet synced`() {
    val onJoins = mutableListOf<Triple<String, PresenceMap?, PresenceMap>>()
    val onLeaves = mutableListOf<Triple<String, PresenceMap, PresenceMap>>()

    presence.onJoin { key, current, new -> onJoins.add(Triple(key, current, new)) }
    presence.onLeave { key, current, left -> onLeaves.add(Triple(key, current, left)) }

    val user1 = mutableMapOf("metas" to mutableListOf(mutableMapOf("id" to 1, "phx_ref" to "1")))
    val user2 = mutableMapOf("metas" to mutableListOf(mutableMapOf("id" to 2, "phx_ref" to "2")))
    val user3 = mutableMapOf("metas" to mutableListOf(mutableMapOf("id" to 3, "phx_ref" to "3")))

    val newState = mutableMapOf("u1" to user1, "u2" to user2)
    val leaves = mapOf("u2" to user2)

    val payload1 = mapOf("joins" to emptyMap(), "leaves" to leaves)
    channel.trigger("presence_diff", payload1, "")

    // There is no state
    assertThat(presence.listBy(listByFirst)).isEmpty()

    // pending diffs 1
    assertThat(presence.pendingDiffs).hasSize(1)
    assertThat(presence.pendingDiffs[0]["joins"]).isEmpty()
    assertThat(presence.pendingDiffs[0]["leaves"]).isEqualTo(leaves)

    channel.trigger("presence_state", newState, "")
    assertThat(onLeaves).hasSize(1)
    assertThat(onLeaves[0].first).isEqualTo("u2")
    assertThat(onLeaves[0].second["metas"]).isEmpty()
    assertThat(onLeaves[0].third["metas"]!![0]["id"]).isEqualTo(2)

    val s = presence.listBy(listByFirst)
    assertThat(s).hasSize(1)
    assertThat(s[0]["id"]).isEqualTo(1)
    assertThat(s[0]["phx_ref"]).isEqualTo("1")
    assertThat(presence.pendingDiffs).isEmpty()

    assertThat(onJoins).hasSize(2)
    assertThat(onJoins[0].first).isEqualTo("u1")
    assertThat(onJoins[0].second).isNull()
    assertThat(onJoins[0].third["metas"]!![0]["id"]).isEqualTo(1)

    assertThat(onJoins[1].first).isEqualTo("u2")
    assertThat(onJoins[1].second).isNull()
    assertThat(onJoins[1].third["metas"]!![0]["id"]).isEqualTo(2)



  }
}