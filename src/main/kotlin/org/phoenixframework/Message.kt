/*
 * Copyright (c) 2019 Daniel Rees <daniel.rees18@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.phoenixframework


data class Message(
  /** The ref sent during a join event. Empty if not present. */
  val joinRef: String? = null,

  /** The unique string ref. Empty if not present */
  val ref: String = "",

  /** The message topic */
  val topic: String = "",

  /** The message event name, for example "phx_join" or any other custom name */
  val event: String = "",

  /** The raw payload of the message. It is recommended that you use `payload` instead. */
  internal val rawPayload: Payload = HashMap(),

  /** The payload, as a json string */
  val payloadJson: String = ""
) {

  /** The payload of the message */
  @Suppress("UNCHECKED_CAST")
  val payload: Payload
    get() = rawPayload["response"] as? Payload ?: rawPayload

  /**
   * Convenience var to access the message's payload's status. Equivalent
   * to checking message.payload["status"] yourself
   */
  val status: String?
    get() = rawPayload["status"] as? String
}
