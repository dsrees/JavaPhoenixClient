package org.phoenixframework

import java.util.concurrent.TimeUnit

//------------------------------------------------------------------------------
// Dispatch Queue
//------------------------------------------------------------------------------
class ManualDispatchQueue : DispatchQueue {

  private var tickTime: Long = 0
  private val tickTimeUnit: TimeUnit = TimeUnit.MILLISECONDS
  private var workItems: MutableList<ManualDispatchWorkItem> = mutableListOf()

  fun reset() {
    this.tickTime = 0
    this.workItems = mutableListOf()
  }

  fun tick(duration: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    val durationInMs = tickTimeUnit.convert(duration, unit)

    // Advance the fake clock
    this.tickTime += durationInMs

    // Filter all work items that are due to be fired and have not been
    // cancelled. Return early if there are no items to fire
    val pastDueWorkItems = workItems.filter { it.deadline <= this.tickTime && !it.isCancelled }

    // if no items are due, then return early
    if (pastDueWorkItems.isEmpty()) return

    // Perform all work items that are due
    pastDueWorkItems.forEach { it.perform() }

    // Remove all work items that are past due or canceled
    workItems.removeAll { it.deadline <= this.tickTime || it.isCancelled }
  }


  override fun queue(delay: Long, unit: TimeUnit, runnable: () -> Unit): DispatchWorkItem {
    // Converts the given unit and delay to the unit used by this class
    val delayInMs = tickTimeUnit.convert(delay, unit)
    val deadline = tickTime + delayInMs

    val workItem = ManualDispatchWorkItem(runnable, deadline)
    workItems.add(workItem)

    return workItem
  }
}

//------------------------------------------------------------------------------
// Work Item
//------------------------------------------------------------------------------
class ManualDispatchWorkItem(
  private val runnable: () -> Unit,
  val deadline: Long
) : DispatchWorkItem {

  override var isCancelled: Boolean = false

  override fun cancel() { this.isCancelled = true  }

  fun perform() {
    if (isCancelled) return
    runnable.invoke()
  }
}

//------------------------------------------------------------------------------
// Channel Extension
//------------------------------------------------------------------------------
fun Channel.getBindings(event: String): List<Binding> {
  return bindings.toList().filter { it.event == event }
}