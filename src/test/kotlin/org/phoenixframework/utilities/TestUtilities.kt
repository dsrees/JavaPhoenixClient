package org.phoenixframework.utilities

import org.phoenixframework.Binding
import org.phoenixframework.Channel

fun Channel.getBindings(event: String): List<Binding> {
  return bindings.toList().filter { it.event == event }
}