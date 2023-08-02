package org.phoenixframework.queue

import org.phoenixframework.DispatchWorkItem

class ManualDispatchWorkItem(
  private val runnable: () -> Unit,
  var deadline: Long,
  private val period: Long = 0
) : DispatchWorkItem, Comparable<ManualDispatchWorkItem> {

  private var performCount = 0

  // Test
  fun isPastDue(tickTime: Long): Boolean {
    return this.deadline <= tickTime
  }

  fun perform() {
    if (isCancelled) return
    runnable.invoke()
    performCount += 1

    // If the task is repeatable, then schedule the next deadline after the given period
    deadline += period
  }

  // DispatchWorkItem
  override var isCancelled: Boolean = false

  override fun cancel() {
    this.isCancelled = true
  }

  override fun compareTo(other: ManualDispatchWorkItem): Int {
    return deadline.compareTo(other.deadline)
  }
}
