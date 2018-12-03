package org.phoenixframework

import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


class PhxChannel(
        val topic: String,
        val params: Payload,
        val socket: PhxSocket
) {


    /** Enumeration of the different states a channel can exist in */
    enum class PhxState(value: String) {
        CLOSED("closed"),
        ERRORED("errored"),
        JOINED("joined"),
        JOINING("joining"),
        LEAVING("leaving")
    }

    /** Enumeration of a variety of Channel specific events */
    enum class PhxEvent(val value: String) {
        HEARTBEAT("heartbeat"),
        JOIN("phx_join"),
        LEAVE("phx_leave"),
        REPLY("phx_reply"),
        ERROR("phx_error"),
        CLOSE("phx_close");

        companion object {
            fun isLifecycleEvent(event: String): Boolean {
                return when (event) {
                    JOIN.value,
                    LEAVE.value,
                    REPLY.value,
                    ERROR.value,
                    CLOSE.value -> true
                    else -> false
                }
            }
        }
    }


    var state: PhxChannel.PhxState
    val bindings: ConcurrentHashMap<String, ConcurrentLinkedQueue<Pair<Int, (PhxMessage) -> Unit>>>
    var bindingRef: Int
    var timeout: Long
    var joinedOnce: Boolean
    var joinPush: PhxPush
    var pushBuffer: MutableList<PhxPush>
    var rejoinTimer: PhxTimer? = null
    var onMessage: (message: PhxMessage) -> PhxMessage = onMessage@{
        return@onMessage it
    }


    init {
        this.state = PhxChannel.PhxState.CLOSED
        this.bindings = ConcurrentHashMap()
        this.bindingRef = 0
        this.timeout = socket.timeout
        this.joinedOnce = false
        this.pushBuffer = ArrayList()
        this.joinPush = PhxPush(this,
                PhxEvent.JOIN.value, params, timeout)

        // Create the Rejoin Timer that will be used in rejoin attempts
        this.rejoinTimer = PhxTimer({
            this.rejoinTimer?.scheduleTimeout()
            if (this.socket.isConnected) {
                rejoin()
            }
        }, socket.reconnectAfterMs)

        // Perform once the Channel is joined
        this.joinPush.receive("ok") {
            this.state = PhxState.JOINED
            this.rejoinTimer?.reset()
            this.pushBuffer.forEach { it.send() }
            this.pushBuffer = ArrayList()
        }

        // Perform if the Push to join timed out
        this.joinPush.receive("timeout") {
            // Do not handle timeout if joining. Handled differently
            if (this.isJoining) {
                return@receive
            }
            this.socket.logItems("Channel: timeouts $topic, $joinRef after $timeout ms")

            val leavePush = PhxPush(this, PhxEvent.LEAVE.value, HashMap(), timeout)
            leavePush.send()

            this.state = PhxState.ERRORED
            this.joinPush.reset()
            this.rejoinTimer?.scheduleTimeout()
        }

        // Clean up when the channel closes
        this.onClose {
            this.rejoinTimer?.reset()
            this.socket.logItems("Channel: close $topic")
            this.state = PhxState.CLOSED
            this.socket.remove(this)
        }

        // Handles an error, attempts to rejoin
        this.onError {
            if (this.isLeaving || !this.isClosed) {
                this.socket.logItems("Channel: error $topic")
                this.state = PhxState.ERRORED
                this.rejoinTimer?.scheduleTimeout()
            }
        }

        // Handles when a reply from the server comes back
        this.on(PhxEvent.REPLY) {
            val replyEventName = this.replyEventName(it.ref)
            val replyMessage = PhxMessage(it.ref, it.topic, replyEventName, it.payload, it.joinRef)
            this.trigger(replyMessage)
        }
    }


    //------------------------------------------------------------------------------
    // Public
    //------------------------------------------------------------------------------
    /**
     * Joins the channel
     *
     * @param joinParams: Overrides the params given when channel was initialized
     * @param timeout: Overrides the default timeout
     * @return Push which receive hooks can be applied to
     */
    fun join(joinParams: Payload? = null, timeout: Long? = null): PhxPush {
        if (joinedOnce) {
            throw IllegalStateException("Tried to join channel multiple times. `join()` can only be called once per channel")
        }

        joinParams?.let {
            this.joinPush.updatePayload(joinParams)
        }

        this.joinedOnce = true
        this.rejoin(timeout)
        return joinPush
    }

    /**
     * Hook into channel close
     *
     * @param callback: Callback to be informed when the channel closes
     * @return the ref counter of the subscription
     */
    fun onClose(callback: (msg: PhxMessage) -> Unit): Int {
        return this.on(PhxEvent.CLOSE, callback)
    }

    /**
     * Hook into channel error
     *
     * @param callback: Callback to be informed when the channel errors
     * @return the ref counter of the subscription
     */
    fun onError(callback: (msg: PhxMessage) -> Unit): Int {
        return this.on(PhxEvent.ERROR, callback)
    }

    /**
     * Convenience method to take the Channel.Event enum. Same as channel.on(string)
     */
    fun on(event: PhxChannel.PhxEvent, callback: (PhxMessage) -> Unit): Int {
        return this.on(event.value, callback)
    }

    /**
     * Subscribes on channel events
     *
     * Subscription returns the ref counter which can be used later to
     * unsubscribe the exact event listener
     *
     * Example:
     *  val ref1 = channel.on("event", do_stuff)
     *  val ref2 = channel.on("event", do_other_stuff)
     *  channel.off("event", ref1)
     *
     * This example will unsubscribe the "do_stuff" callback but not
     * the "do_other_stuff" callback.
     *
     * @param event: Name of the event to subscribe to
     * @param callback: Receives payload of the event
     * @return: The subscriptions ref counter
     */
    fun on(event: String, callback: (PhxMessage) -> Unit): Int {
        val ref = bindingRef
        this.bindingRef = ref + 1

        this.bindings.getOrPut(event) { ConcurrentLinkedQueue() }
                .add(ref to callback)

        return ref
    }

    /**
     * Unsubscribe from channel events. If ref counter is not provided, then
     * all subscriptions for the event will be removed.
     *
     * Example:
     *  val ref1 = channel.on("event", do_stuff)
     *  val ref2 = channel.on("event", do_other_stuff)
     *  channel.off("event", ref1)
     *
     * This example will unsubscribe the "do_stuff" callback but not
     * the "do_other_stuff" callback.
     *
     * @param event: Event to unsubscribe from
     * @param ref: Optional. Ref counter returned when subscribed to event
     */
    fun off(event: String, ref: Int? = null) {
        // Remove any subscriptions that match the given event and ref ID. If no ref
        // ID is given, then remove all subscriptions for an event.
        if (ref != null) {
            this.bindings[event]?.removeIf{ ref == it.first }
        } else {
            this.bindings.remove(event)
        }
    }

    /**
     * Push a payload to the Channel
     *
     * @param event: Event to push
     * @param payload: Payload to push
     * @param timeout: Optional timeout. Default will be used
     * @return [PhxPush] that can be hooked into
     */
    fun push(event: String, payload: Payload, timeout: Long = DEFAULT_TIMEOUT): PhxPush {
        if (!joinedOnce) {
            // If the Channel has not been joined, throw an exception
            throw RuntimeException("Tried to push $event to $topic before joining. Use channel.join() before pushing events")
        }

        val pushEvent = PhxPush(this, event, payload, timeout)
        if (canPush) {
            pushEvent.send()
        } else {
            pushEvent.startTimeout()
            pushBuffer.add(pushEvent)
        }

        return pushEvent
    }

    /**
     * Leaves a channel
     *
     * Unsubscribe from server events and instructs Channel to terminate on Server
     *
     * Triggers .onClose() hooks
     *
     * To receive leave acknowledgements, use the receive hook to bind to the server ack
     *
     * Example:
     *  channel.leave().receive("ok) { print("left channel") }
     *
     * @param timeout: Optional timeout. Default will be used
     */
    fun leave(timeout: Long = DEFAULT_TIMEOUT): PhxPush {
        this.state = PhxState.LEAVING

        val onClose: ((PhxMessage) -> Unit) = {
            this.socket.logItems("Channel: leave $topic")
            this.trigger(it)
        }

        val leavePush = PhxPush(this, PhxEvent.LEAVE.value, HashMap(), timeout)
        leavePush
                .receive("ok", onClose)
                .receive("timeout", onClose)

        leavePush.send()
        if (!canPush) {
            leavePush.trigger("ok", HashMap())
        }

        return leavePush
    }

    /**
     * Override message hook. Receives all events for specialized message
     * handling before dispatching to the channel callbacks
     *
     * @param callback: Callback which will receive the inbound message before
     * it is dispatched to other callbacks. Must return a Message object.
     */
    fun onMessage(callback: (message: PhxMessage) -> PhxMessage) {
        this.onMessage = callback
    }


    //------------------------------------------------------------------------------
    // Internal
    //------------------------------------------------------------------------------
    /** Checks if an event received by the socket belongs to the Channel */
    fun isMember(message: PhxMessage): Boolean {
        if (message.topic != this.topic) { return false }

        val isLifecycleEvent = PhxEvent.isLifecycleEvent(message.event)

        // If the message is a lifecycle event and it is not a join for this channel, drop the outdated message
        if (message.joinRef != null && isLifecycleEvent && message.joinRef != this.joinRef) {
            this.socket.logItems("Channel: Dropping outdated message. ${message.topic}")
            return false
        }

        return true
    }

    /** Sends the payload to join the Channel */
    fun sendJoin(timeout: Long) {
        this.state = PhxState.JOINING
        this.joinPush.resend(timeout)

    }

    /** Rejoins the Channel */
    fun rejoin(timeout: Long? = null) {
        this.sendJoin(timeout ?: this.timeout)
    }

    /**
     * Triggers an event to the correct event binding created by `channel.on("event")
     *
     * @param message: Message that was received that will be sent to the correct binding
     */
    fun trigger(message: PhxMessage) {
        val handledMessage = onMessage(message)
        this.bindings[message.event]?.forEach { it.second(handledMessage) }
    }

    /**
     * @param ref: The ref of the reply push event
     * @return the name of the event
     */
    fun replyEventName(ref: String): String {
        return "chan_reply_$ref"
    }

    /** The ref sent during the join message. */
    val joinRef: String
        get() = joinPush.ref ?: ""

    /**
     * @return True if the Channel can push messages, meaning the socket
     * is connected and the channel is joined
     */
    val canPush: Boolean
        get() = this.socket.isConnected && this.isJoined

    /** @return: True if the Channel has been closed */
    val isClosed: Boolean
        get() = state == PhxState.CLOSED

    /** @return: True if the Channel experienced an error */
    val isErrored: Boolean
        get() = state == PhxState.ERRORED

    /** @return: True if the channel has joined */
    val isJoined: Boolean
        get() = state == PhxState.JOINED

    /** @return: True if the channel has requested to join */
    val isJoining: Boolean
        get() = state == PhxState.JOINING

    /** @return: True if the channel has requested to leave */
    val isLeaving: Boolean
        get() = state == PhxState.LEAVING
}
