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

import com.google.gson.annotations.SerializedName

class Message(
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
