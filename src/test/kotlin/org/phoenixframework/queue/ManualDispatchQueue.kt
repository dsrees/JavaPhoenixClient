package org.phoenixframework.queue

import org.phoenixframework.DispatchQueue
import org.phoenixframework.DispatchWorkItem
import java.util.concurrent.TimeUnit

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

    // calculate what time to advance to
    val advanceTo = tickTime + durationInMs

    // Filter all work items that are due to be fired and have not been
    // cancelled. Return early if there are no items to fire
    var pastDueWorkItems = workItems.filter { it.isPastDue(advanceTo) && !it.isCancelled }

    // Keep looping until there are no more work items that are passed the advance to time
    while (pastDueWorkItems.isNotEmpty()) {

      // Perform all work items that are due
      pastDueWorkItems.forEach {
        tickTime = it.deadline
        it.perform()
      }

      // Remove all work items that are past due or canceled
      workItems.removeAll { it.isPastDue(tickTime) || it.isCancelled }
      pastDueWorkItems = workItems.filter { it.isPastDue(advanceTo) && !it.isCancelled }
    }

    // Now that all work has been performed, advance the clock
    this.tickTime = advanceTo

  }

  override fun queue(delay: Long, unit: TimeUnit, runnable: () -> Unit): DispatchWorkItem {
    // Converts the given unit and delay to the unit used by this class
    val delayInMs = tickTimeUnit.convert(delay, unit)
    val deadline = tickTime + delayInMs

    val workItem = ManualDispatchWorkItem(runnable, deadline)
    workItems.add(workItem)

    return workItem
  }

  override fun queueAtFixedRate(
    delay: Long,
    period: Long,
    unit: TimeUnit,
    runnable: () -> Unit
  ): DispatchWorkItem {

    val delayInMs = tickTimeUnit.convert(delay, unit)
    val periodInMs = tickTimeUnit.convert(period, unit)
    val deadline = tickTime + delayInMs

    val workItem =
        ManualDispatchWorkItem(runnable, deadline, periodInMs)
    workItems.add(workItem)

    return workItem
  }
}