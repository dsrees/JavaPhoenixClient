package org.phoenixframework

class PhxMessage(
        /** The unique string ref. Empty if not present */
        val ref: String = "",
        /** Property "topic" is never used */
        val topic: String = "",
        /** Property "topic" is never used */
        val event: String = "",
        /** Property "topic" is never used */
        val payload: Payload = HashMap(),
        /** The ref sent during a join event. Empty if not present. */
        val joinRef: String? = null) {


    /**
     * Convenience var to access the message's payload's status. Equivalent
     * to checking message.payload["status"] yourself
     */
    val status: String?
        get() = payload["status"] as? String


    override fun toString(): String {
        return "Message(ref='$ref', joinRef=$joinRef, topic='$topic', event='$event', payload=$payload)"
    }

}