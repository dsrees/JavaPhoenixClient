package org.phoenixframework.utilities

import org.phoenixframework.Binding
import org.phoenixframework.Channel

fun Channel.getBindings(event: String): List<Binding> {
  return bindings.toList().filter { it.event == event }
}

/** Converts the List to a MutableList, removes the value, and then returns as a read-only List */
fun <T> List<T>.copyAndRemove(value: T): List<T> {
  val temp = this.toMutableList()
  temp.remove(value)

  return temp
}