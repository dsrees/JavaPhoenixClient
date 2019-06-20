package org.phoenixframework

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.phoenixframework.utilities.getBindings

class PresenceTest {

  @Mock lateinit var socket: Socket

  private val fixJoins: PresenceState = mutableMapOf(
      "u1" to mutableMapOf("metas" to listOf(mapOf("id" to 1, "phx_ref" to "1.2"))))
  private val fixLeaves: PresenceState = mutableMapOf(
      "u2" to mutableMapOf("metas" to listOf(mapOf("id" to 2, "phx_ref" to "2"))))
  private val fixState: PresenceState = mutableMapOf(
      "u1" to mutableMapOf("metas" to listOf(mapOf("id" to 1, "phx_ref" to "1"))),
      "u2" to mutableMapOf("metas" to listOf(mapOf("id" to 2, "phx_ref" to "2"))),
      "u3" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3")))
  )

  private val listByFirst: (Map.Entry<String, PresenceMap>) -> PresenceMeta =
      { it.value["metas"]!!.first() }

  lateinit var channel: Channel
  lateinit var presence: Presence

  @BeforeEach
  internal fun setUp() {
    MockitoAnnotations.initMocks(this)

    whenever(socket.timeout).thenReturn(Defaults.TIMEOUT)
    whenever(socket.makeRef()).thenReturn("1")
    whenever(socket.reconnectAfterMs).thenReturn { 1_000 }
    whenever(socket.rejoinAfterMs).thenReturn(Defaults.rejoinSteppedBackOff)
    whenever(socket.dispatchQueue).thenReturn(mock())

    channel = Channel("topic", mapOf(), socket)
    channel.joinPush.ref = "1"

    presence = Presence(channel)
  }

  @Nested
  @DisplayName("constructor")
  inner class Constructor {

    @Test
    internal fun `sets defaults`() {
      assertThat(presence.state).isEmpty()
      assertThat(presence.pendingDiffs).isEmpty()
      assertThat(presence.channel).isEqualTo(channel)
      assertThat(presence.joinRef).isNull()
    }

    @Test
    internal fun `binds to channel with default arguments`() {
      assertThat(presence.channel.getBindings("presence_state")).hasSize(1)
      assertThat(presence.channel.getBindings("presence_diff")).hasSize(1)
    }

    @Test
    internal fun `binds to channel with custom options`() {
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
    internal fun `syncs state and diffs`() {
      val user1: PresenceMap = mutableMapOf("metas" to mutableListOf(
          mapOf("id" to 1, "phx_ref" to "1")))
      val user2: PresenceMap = mutableMapOf("metas" to mutableListOf(
          mapOf("id" to 2, "phx_ref" to "2")))
      val newState: PresenceState = mutableMapOf("u1" to user1, "u2" to user2)

      channel.trigger("presence_state", newState, "1")
      val s = presence.listBy(listByFirst)
      assertThat(s).hasSize(2)
      assertThat(s[0]["id"]).isEqualTo(1)
      assertThat(s[0]["phx_ref"]).isEqualTo("1")

      assertThat(s[1]["id"]).isEqualTo(2)
      assertThat(s[1]["phx_ref"]).isEqualTo("2")

      channel.trigger("presence_diff",
          mapOf("joins" to emptyMap(), "leaves" to mapOf("u1" to user1)))
      val l = presence.listBy(listByFirst)
      assertThat(l).hasSize(1)
      assertThat(l[0]["id"]).isEqualTo(2)
      assertThat(l[0]["phx_ref"]).isEqualTo("2")
    }

    @Test
    internal fun `applies pending diff if state is not yet synced`() {
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

      // disconnect then reconnect
      assertThat(presence.isPendingSyncState).isFalse()
      channel.joinPush.ref = "2"
      assertThat(presence.isPendingSyncState).isTrue()


      channel.trigger("presence_diff",
          mapOf("joins" to mapOf(), "leaves" to mapOf("u1" to user1)))
      val d = presence.listBy(listByFirst)
      assertThat(d).hasSize(1)
      assertThat(d[0]["id"]).isEqualTo(1)
      assertThat(d[0]["phx_ref"]).isEqualTo("1")


      channel.trigger("presence_state",
          mapOf("u1" to user1, "u3" to user3))
      val s2 = presence.listBy(listByFirst)
      assertThat(s2).hasSize(1)
      assertThat(s2[0]["id"]).isEqualTo(3)
      assertThat(s2[0]["phx_ref"]).isEqualTo("3")
    }
    /* End Constructor */
  }

  @Nested
  @DisplayName("syncState")
  inner class SyncState {

    @Test
    internal fun `syncs empty state`() {
      val newState: PresenceState = mutableMapOf(
          "u1" to mutableMapOf("metas" to listOf(mapOf("id" to 1, "phx_ref" to "1"))))
      var state: PresenceState = mutableMapOf()
      val stateBefore = state

      Presence.syncState(state, newState)
      assertThat(state).isEqualTo(stateBefore)

      state = Presence.syncState(state, newState)
      assertThat(state).isEqualTo(newState)
    }

    @Test
    internal fun `onJoins new presences and onLeaves left presences`() {
      val newState = fixState
      var state = mutableMapOf(
          "u4" to mutableMapOf("metas" to listOf(mapOf("id" to 4, "phx_ref" to "4"))))

      val joined: PresenceDiff = mutableMapOf()
      val left: PresenceDiff = mutableMapOf()

      val onJoin: OnJoin = { key, current, newPres ->
        val joinState: PresenceState = mutableMapOf("newPres" to newPres)
        current?.let { c -> joinState["current"] = c }

        joined[key] = joinState
      }

      val onLeave: OnLeave = { key, current, leftPres ->
        left[key] = mutableMapOf("current" to current, "leftPres" to leftPres)
      }

      val stateBefore = state
      Presence.syncState(state, newState, onJoin, onLeave)
      assertThat(state).isEqualTo(stateBefore)

      state = Presence.syncState(state, newState, onJoin, onLeave)
      assertThat(state).isEqualTo(newState)

      // asset equality in joined
      val joinedExpectation: PresenceDiff = mutableMapOf(
          "u1" to mutableMapOf("newPres" to mutableMapOf(
              "metas" to listOf(mapOf("id" to 1, "phx_ref" to "1")))),
          "u2" to mutableMapOf("newPres" to mutableMapOf(
              "metas" to listOf(mapOf("id" to 2, "phx_ref" to "2")))),
          "u3" to mutableMapOf("newPres" to mutableMapOf(
              "metas" to listOf(mapOf("id" to 3, "phx_ref" to "3"))))
      )

      assertThat(joined).isEqualTo(joinedExpectation)

      // assert equality in left
      val leftExpectation: PresenceDiff = mutableMapOf(
          "u4" to mutableMapOf(
              "current" to mutableMapOf(
                  "metas" to mutableListOf()),
              "leftPres" to mutableMapOf(
                  "metas" to listOf(mapOf("id" to 4, "phx_ref" to "4"))))
      )
      assertThat(left).isEqualTo(leftExpectation)
    }

    @Test
    internal fun `onJoins only newly added metas`() {
      var state = mutableMapOf(
          "u3" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3"))))
      val newState = mutableMapOf(
          "u3" to mutableMapOf("metas" to listOf(
              mapOf("id" to 3, "phx_ref" to "3"),
              mapOf("id" to 3, "phx_ref" to "3.new")
          )))

      val joined: PresenceDiff = mutableMapOf()
      val left: PresenceDiff = mutableMapOf()

      val onJoin: OnJoin = { key, current, newPres ->
        val joinState: PresenceState = mutableMapOf("newPres" to newPres)
        current?.let { c -> joinState["current"] = c }

        joined[key] = joinState
      }

      val onLeave: OnLeave = { key, current, leftPres ->
        left[key] = mutableMapOf("current" to current, "leftPres" to leftPres)
      }

      state = Presence.syncState(state, newState, onJoin, onLeave)
      assertThat(state).isEqualTo(newState)

      // asset equality in joined
      val joinedExpectation: PresenceDiff = mutableMapOf(
          "u3" to mutableMapOf(
              "newPres" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3.new"))),
              "current" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3")))

          ))
      assertThat(joined).isEqualTo(joinedExpectation)

      // assert equality in left
      assertThat(left).isEmpty()
    }

    @Test
    internal fun `onLeaves only newly removed metas`() {
      val newState = mutableMapOf(
          "u3" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3"))))
      var state = mutableMapOf(
          "u3" to mutableMapOf("metas" to listOf(
              mapOf("id" to 3, "phx_ref" to "3"),
              mapOf("id" to 3, "phx_ref" to "3.left")
          )))

      val joined: PresenceDiff = mutableMapOf()
      val left: PresenceDiff = mutableMapOf()

      val onJoin: OnJoin = { key, current, newPres ->
        val joinState: PresenceState = mutableMapOf("newPres" to newPres)
        current?.let { c -> joinState["current"] = c }

        joined[key] = joinState
      }

      val onLeave: OnLeave = { key, current, leftPres ->
        left[key] = mutableMapOf("current" to current, "leftPres" to leftPres)
      }

      state = Presence.syncState(state, newState, onJoin, onLeave)
      assertThat(state).isEqualTo(newState)

      // asset equality in joined
      val leftExpectation: PresenceDiff = mutableMapOf(
          "u3" to mutableMapOf(
              "leftPres" to mutableMapOf(
                  "metas" to listOf(mapOf("id" to 3, "phx_ref" to "3.left"))),
              "current" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3")))

          ))
      assertThat(left).isEqualTo(leftExpectation)

      // assert equality in left
      assertThat(joined).isEmpty()
    }

    @Test
    internal fun `syncs both joined and left metas`() {
      val newState = mutableMapOf(
          "u3" to mutableMapOf("metas" to listOf(
              mapOf("id" to 3, "phx_ref" to "3"),
              mapOf("id" to 3, "phx_ref" to "3.new")
          )))

      var state = mutableMapOf(
          "u3" to mutableMapOf("metas" to listOf(
              mapOf("id" to 3, "phx_ref" to "3"),
              mapOf("id" to 3, "phx_ref" to "3.left")
          )))

      val joined: PresenceDiff = mutableMapOf()
      val left: PresenceDiff = mutableMapOf()

      val onJoin: OnJoin = { key, current, newPres ->
        val joinState: PresenceState = mutableMapOf("newPres" to newPres)
        current?.let { c -> joinState["current"] = c }

        joined[key] = joinState
      }

      val onLeave: OnLeave = { key, current, leftPres ->
        left[key] = mutableMapOf("current" to current, "leftPres" to leftPres)
      }

      state = Presence.syncState(state, newState, onJoin, onLeave)
      assertThat(state).isEqualTo(newState)

      // asset equality in joined
      val joinedExpectation: PresenceDiff = mutableMapOf(
          "u3" to mutableMapOf(
              "newPres" to mutableMapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3.new"))),
              "current" to mutableMapOf("metas" to listOf(
                  mapOf("id" to 3, "phx_ref" to "3"),
                  mapOf("id" to 3, "phx_ref" to "3.left")))
          ))
      assertThat(joined).isEqualTo(joinedExpectation)

      // assert equality in left
      val leftExpectation: PresenceDiff = mutableMapOf(
          "u3" to mutableMapOf(
              "leftPres" to mutableMapOf(
                  "metas" to listOf(mapOf("id" to 3, "phx_ref" to "3.left"))),
              "current" to mutableMapOf("metas" to listOf(
                  mapOf("id" to 3, "phx_ref" to "3"),
                  mapOf("id" to 3, "phx_ref" to "3.new")))
          ))
      assertThat(left).isEqualTo(leftExpectation)
    }

    /* end SyncState */
  }

  @Nested
  @DisplayName("syncDiff")
  inner class SyncDiff {

    @Test
    internal fun `syncs empty state`() {
      val joins: PresenceState = mutableMapOf(
          "u1" to mutableMapOf("metas" to
              listOf(mapOf("id" to 1, "phx_ref" to "1"))))
      var state: PresenceState = mutableMapOf()

      Presence.syncDiff(state, mutableMapOf("joins" to joins, "leaves" to mutableMapOf()))
      assertThat(state).isEmpty()

      state = Presence.syncDiff(state, mutableMapOf("joins" to joins, "leaves" to mutableMapOf()))
      assertThat(state).isEqualTo(joins)
    }

    @Test
    internal fun `removes presence when meta is empty and adds additional meta`() {
      var state = fixState
      val diff: PresenceDiff = mutableMapOf("joins" to fixJoins, "leaves" to fixLeaves)
      state = Presence.syncDiff(state, diff)

      val expectation: PresenceState = mutableMapOf(
          "u1" to mutableMapOf("metas" to
              listOf(
                  mapOf("id" to 1, "phx_ref" to "1"),
                  mapOf("id" to 1, "phx_ref" to "1.2")
              )
          ),
          "u3" to mutableMapOf("metas" to
              listOf(mapOf("id" to 3, "phx_ref" to "3"))
          )
      )

      assertThat(state).isEqualTo(expectation)
    }

    @Test
    internal fun `removes meta while leaving key if other metas exist`() {
      var state = mutableMapOf(
          "u1" to mutableMapOf("metas" to listOf(
              mapOf("id" to 1, "phx_ref" to "1"),
              mapOf("id" to 1, "phx_ref" to "1.2")
          )))

      val leaves = mutableMapOf(
          "u1" to mutableMapOf("metas" to listOf(
              mapOf("id" to 1, "phx_ref" to "1")
          )))
      val diff: PresenceDiff = mutableMapOf("joins" to mutableMapOf(), "leaves" to leaves)
      state = Presence.syncDiff(state, diff)

      val expectedState = mutableMapOf(
          "u1" to mutableMapOf("metas" to listOf(mapOf("id" to 1, "phx_ref" to "1.2"))))
      assertThat(state).isEqualTo(expectedState)
    }

    /* End SyncDiff */
  }

  @Nested
  @DisplayName("listBy")
  inner class ListBy {

    @Test
    internal fun `lists full presence by default`() {
      presence.state = fixState

      val listExpectation = listOf(
          mapOf("metas" to listOf(mapOf("id" to 1, "phx_ref" to "1"))),
          mapOf("metas" to listOf(mapOf("id" to 2, "phx_ref" to "2"))),
          mapOf("metas" to listOf(mapOf("id" to 3, "phx_ref" to "3")))
      )

      assertThat(presence.list()).isEqualTo(listExpectation)
    }

    @Test
    internal fun `lists with custom function`() {
      val state: PresenceState = mutableMapOf(
          "u1" to mutableMapOf("metas" to listOf(
              mapOf("id" to 1, "phx_ref" to "1.first"),
              mapOf("id" to 1, "phx_ref" to "1.second"))
          )
      )

      presence.state = state
      val listBy = presence.listBy { it.value["metas"]!!.first() }
      assertThat(listBy).isEqualTo(listOf(mapOf("id" to 1, "phx_ref" to "1.first")))
    }
  }

}