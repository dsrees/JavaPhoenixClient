package org.phoenixframework

object Defaults {

  /** Default timeout of 10s */
  const val TIMEOUT: Long = 10_000

  /** Default heartbeat interval of 30s */
  const val HEARTBEAT: Long = 30_000

  /** Default reconnect algorithm. Reconnects after 1s, 2s, 5s and then 10s thereafter */
  val steppedBackOff: (Int) -> Long = { tries ->
    if (tries > 3) 10000 else listOf(1000L, 2000L, 5000L)[tries - 1]
  }
}