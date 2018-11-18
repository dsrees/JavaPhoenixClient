package org.phoenixframework

import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

class PhxPush(
        val channel: PhxChannel,
        val event: String,
        var payload: Payload,
        var timeout: Long
) {

    /** The server's response to the Push */
    var receivedMessage: PhxMessage? = null

    /** Timer which triggers a timeout event */
    var timeoutTimer: Timer? = null

    /** Hooks into a Push. Where .receive("ok", callback(Payload)) are stored */
    var receiveHooks: MutableMap<String, MutableList<((message: PhxMessage) -> Unit)>> = HashMap()

    /** True if the Push has been sent */
    var sent: Boolean = false

    /** The reference ID of the Push */
    var ref: String? = null

    /** The event that is associated with the reference ID of the Push */
    var refEvent: String? = null


    //------------------------------------------------------------------------------
    // Public
    //------------------------------------------------------------------------------
    /** Resend a Push */
    fun resend(timeout: Long = DEFAULT_TIMEOUT) {
        this.timeout = timeout
        this.reset()
        this.send()
    }

    /**
     * Receive a specific event when sending an Outbound message
     *
     * Example:
     *  channel
     *      .send("event", myPayload)
     *      .receive("error") { }
     */
    fun receive(status: String, callback: (message: PhxMessage) -> Unit): PhxPush {
        // If the message has already be received, pass it to the callback
        receivedMessage?.let {
            if (hasReceivedStatus(status)) {
                callback(it)
            }
        }

        // Create a new array of hooks if no previous hook is associated with status
        if (receiveHooks[status] == null) {
            receiveHooks[status] = arrayListOf(callback)
        } else {
            // A previous hook for this status already exists. Just append the new hook
            receiveHooks[status]?.add(callback)
        }

        return this
    }


    /**
     * @param payload: New payload to be sent through with the Push
     */
    fun updatePayload(payload: Payload) {
        this.payload = payload
    }

    //------------------------------------------------------------------------------
    // Internal
    //------------------------------------------------------------------------------
    /**
     * Sends the Push through the socket
     */
    fun send() {
        if (hasReceivedStatus("timeout")) {
            return
        }

        this.startTimeout()
        this.sent = true

        this.channel.socket.push(
                this.channel.topic,
                this.event,
                this.payload,
                this.ref,
                this.channel.joinRef)
    }

    /** Resets the Push as it was after initialization */
    fun reset() {
        this.cancelRefEvent()
        this.ref = null
        this.refEvent = null
        this.receivedMessage = null
        this.sent = false
    }

    /**
     * Finds the receiveHook which needs to be informed of a status response
     *
     * @param status: Status to find the hook for
     * @param message: Message to send to the matched hook
     */
    fun matchReceive(status: String, message: PhxMessage) {
        receiveHooks[status]?.forEach { it(message) }
    }

    /**
     * Reverses the result of channel.on(event, callback) that spawned the Push
     */
    fun cancelRefEvent() {
        this.refEvent?.let {
            this.channel.off(it)
        }
    }

    /**
     * Cancels any ongoing Timeout timer
     */
    fun cancelTimeout() {
        this.timeoutTimer?.cancel()
        this.timeoutTimer = null
    }

    /**
     * Starts the Timer which will trigger a timeout after a specific delay
     * in milliseconds is reached.
     */
    fun startTimeout() {
        this.timeoutTimer?.cancel()

        val ref = this.channel.socket.makeRef()
        this.ref = ref

        val refEvent = this.channel.replyEventName(ref)
        this.refEvent = refEvent

        // If a response is received  before the Timer triggers, cancel timer
        // and match the received event to it's corresponding hook.
        this.channel.on(refEvent) {
            this.cancelRefEvent()
            this.cancelTimeout()
            this.receivedMessage = it

            // Check if there is an event status available
            val message = it
            message.status?.let {
                this.matchReceive(it, message)
            }
        }

        // Start the timer. If the timer fires, then send a timeout event to the Push
        this.timeoutTimer = Timer()
        this.timeoutTimer?.schedule(timeout) {
            trigger("timeout", HashMap())
        }
    }

    /**
     * Checks if a status has already been received by the Push.
     *
     * @param status: Status to check
     * @return True if the Push has received the given status. False otherwise
     */
    fun hasReceivedStatus(status: String): Boolean {
        return receivedMessage?.status == status
    }

    /**
     * Triggers an event to be sent through the Channel
     */
    fun trigger(status: String, payload: Payload) {
        val mutPayload = payload.toMutableMap()
        mutPayload["status"] = status

        refEvent?.let {
            val message = PhxMessage(it, "", "", mutPayload)
            this.channel.trigger(message)
        }
    }
}