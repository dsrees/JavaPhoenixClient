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
