package org.phoenixframework

class PhxMessage {


    /** The unique string ref. Empty if not present */
    val ref: String

    /** The ref sent during a join event. Empty if not present. */
    val joinRef: String?

    /** Property "topic" is never used */
    val topic: String

    /** Property "topic" is never used */
    val event: String

    /** Property "topic" is never used */
    val payload: Map<String, Any>

    /**
     * Convenience var to access the message's payload's status. Equivalent
     * to checking message.payload["status"] yourself
     */
    val status: String?
        get() = payload.get("status") as? String


    constructor(ref: String = "",
                topic: String = "",
                event: String = "",
                payload: Payload = HashMap(),
                joinRef: String? = null) {
        this.ref = ref
        this.topic = topic
        this.event = event
        this.payload = payload
        this.joinRef = joinRef
    }

    override fun toString(): String {
        return "Message(ref='$ref', joinRef=$joinRef, topic='$topic', event='$event', payload=$payload)"
    }


}