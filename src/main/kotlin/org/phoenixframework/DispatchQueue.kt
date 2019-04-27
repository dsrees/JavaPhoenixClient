package org.phoenixframework

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

//------------------------------------------------------------------------------
// Dispatch Queue Interfaces
//------------------------------------------------------------------------------
/**
 * Interface which abstracts away scheduling future tasks, allowing fake instances
 * to be injected and manipulated during tests
 */
interface DispatchQueue {
  /** Queue a Runnable to be executed after a given time unit delay */
  fun queue(delay: Long, unit: TimeUnit, runnable: () -> Unit): DispatchWorkItem
}

/** Abstracts away a future task */
interface DispatchWorkItem {
  /** True if the work item has been cancelled */
  val isCancelled: Boolean

  /** Cancels the item from executing */
  fun cancel()
}

//------------------------------------------------------------------------------
// Scheduled Dispatch Queue
//------------------------------------------------------------------------------
/**
 * A DispatchQueue that uses a ScheduledThreadPoolExecutor to schedule tasks to be executed
 * in the future.
 *
 * Uses a default pool size of 8. Custom values can be provided during construction
 */
class ScheduledDispatchQueue(poolSize: Int = 8) : DispatchQueue {

  private var scheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(poolSize)

  override fun queue(delay: Long, unit: TimeUnit, runnable: () -> Unit): DispatchWorkItem {
    val scheduledFuture = scheduledThreadPoolExecutor.schedule(runnable, delay, unit)
    return ScheduledDispatchWorkItem(scheduledFuture)
  }
}

/**
 * A DispatchWorkItem that wraps a ScheduledFuture<*> created by a ScheduledDispatchQueue
 */
class ScheduledDispatchWorkItem(private val scheduledFuture: ScheduledFuture<*>) : DispatchWorkItem {

  override val isCancelled: Boolean
    get() = this.scheduledFuture.isCancelled

  override fun cancel() {
    this.scheduledFuture.cancel(true)
  }
}
