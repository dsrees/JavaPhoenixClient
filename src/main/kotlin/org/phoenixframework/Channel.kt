package org.phoenixframework

/**
 * Represents a binding to a Channel event
 */
data class Binding(
  val event: String,
  val ref: Int,
  val callback: (Message) -> Unit
)

class Channel {
}