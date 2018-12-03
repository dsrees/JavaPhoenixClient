package org.phoenixframework

import com.google.gson.annotations.SerializedName

data class PhxMessage(
        /** The unique string ref. Empty if not present */
        @SerializedName("ref")
        val ref: String = "",

        /** The message topic */
        @SerializedName("topic")
        val topic: String = "",

        /** The message event name, for example "phx_join" or any other custom name */
        @SerializedName("event")
        val event: String = "",

        /** The payload of the message */
        @SerializedName("payload")
        val payload: Payload = HashMap(),

        /** The ref sent during a join event. Empty if not present. */
        @SerializedName("join_ref")
        val joinRef: String? = null) {


    /**
     * Convenience var to access the message's payload's status. Equivalent
     * to checking message.payload["status"] yourself
     */
    val status: String?
        get() = payload["status"] as? String
}
