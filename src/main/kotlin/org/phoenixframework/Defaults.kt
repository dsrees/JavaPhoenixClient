package org.phoenixframework

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder

object Defaults {

  /** Default timeout of 10s */
  const val TIMEOUT: Long = 10_000

  /** Default heartbeat interval of 30s */
  const val HEARTBEAT: Long = 30_000

  /** Default reconnect algorithm. Reconnects after 1s, 2s, 5s and then 10s thereafter */
  val steppedBackOff: (Int) -> Long = { tries ->
    if (tries > 3) 10000 else listOf(1000L, 2000L, 5000L)[tries - 1]
  }

  /** The default Gson configuration to use when parsing messages */
  val gson: Gson
    get() = GsonBuilder()
        .setLenient()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
}