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

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import java.net.URL

object Defaults {

  /** Default timeout of 10s */
  const val TIMEOUT: Long = 10_000

  /** Default heartbeat interval of 30s */
  const val HEARTBEAT: Long = 30_000

  /** Default JSON Serializer Version set to 2.0.0 */
  const val VSN: String = "2.0.0"

  /** Default reconnect algorithm for the socket */
  val reconnectSteppedBackOff: (Int) -> Long = { tries ->
    if (tries > 9) 5_000 else listOf(
      10L, 50L, 100L, 150L, 200L, 250L, 500L, 1_000L, 2_000L
    )[tries - 1]
  }

  /** Default rejoin algorithm for individual channels */
  val rejoinSteppedBackOff: (Int) -> Long = { tries ->
    if (tries > 3) 10_000 else listOf(1_000L, 2_000L, 5_000L)[tries - 1]
  }

  /** The default Gson configuration to use when parsing messages */
  val gson: Gson
    get() = GsonBuilder()
      .setLenient()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .create()

  /**
   * Default JSON decoder, backed by GSON, that takes JSON and converts it
   * into a Message object.
   */
  @Suppress("UNCHECKED_CAST")
  val decode: DecodeClosure = { rawMessage ->

    var message = rawMessage
    message = message.removeRange(0, 1) // remove '['

    val joinRef = message.takeWhile { it != ',' } // take join ref, 'null' or '5'
    message = message.removeRange(0, joinRef.length) // remove join ref
    message = message.removeRange(0, 1) // remove ','

    val ref = message.takeWhile { it != ',' } // take ref, 'null' or '5'
    message = message.removeRange(0, ref.length) // remove ref
    message = message.removeRange(0, 2) // remove ',"'

    val topic = message.takeWhile { it != '"' }
    message = message.removeRange(0, topic.length)
    message = message.removeRange(0, 3) // remove '","'

    val event = message.takeWhile { it != '"' }
    message = message.removeRange(0, event.length)
    message = message.removeRange(0, 2) // remove '",'

    val remaining = message.removeRange(message.length - 1, message.length)

    val jsonObj = gson.fromJson(remaining, JsonObject::class.java)
    val response = jsonObj.get("response")
    val payload =  response?.let { gson.toJson(response) } ?: remaining

    val anyType = object : TypeToken<Map<String, Any>>() {}.type
    val result = gson.fromJson<Map<String, Any>>(remaining, anyType)

    // vsn=2.0.0 message structure
    // [join_ref, ref, topic, event, payload]
    Message(
      joinRef = joinRef.toIntOrNull()?.toString(),
      ref = ref.toIntOrNull()?.toString() ?: "",
      topic = topic,
      event = event,
      rawPayload = result,
      payloadJson = payload
    )
  }

  /**
   * Default JSON encoder, backed by GSON, that takes a Map<String, Any> and
   * converts it into a JSON String.
   */
  val encode: EncodeClosure = { payload ->
    gson.toJson(payload)
  }

  /**
   * Takes an endpoint and a params closure given by the User and constructs a URL that
   * is ready to be sent to the Socket connection.
   *
   * Will convert "ws://" and "wss://" to http/s which is what OkHttp expects.
   *
   * @throws IllegalArgumentException if [endpoint] is not a valid URL endpoint.
   */
  internal fun buildEndpointUrl(
    endpoint: String,
    paramsClosure: PayloadClosure,
    vsn: String
  ): URL {
    var mutableUrl = endpoint
    // Silently replace web socket URLs with HTTP URLs.
    if (endpoint.regionMatches(0, "ws:", 0, 3, ignoreCase = true)) {
      mutableUrl = "http:" + endpoint.substring(3)
    } else if (endpoint.regionMatches(0, "wss:", 0, 4, ignoreCase = true)) {
      mutableUrl = "https:" + endpoint.substring(4)
    }

    // Add the VSN query parameter
    var httpUrl = HttpUrl.parse(mutableUrl)
      ?: throw IllegalArgumentException("invalid url: $endpoint")
    val httpBuilder = httpUrl.newBuilder()
    httpBuilder.addQueryParameter("vsn", vsn)

    // Append any additional query params
    paramsClosure.invoke()?.let {
      it.forEach { (key, value) ->
        httpBuilder.addQueryParameter(key, value.toString())
      }
    }

    // Return the [URL] that will be used to establish a connection
    return httpBuilder.build().url()
  }
}