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

import java.util.concurrent.TimeUnit

/**
 * A Timer class that schedules a callback to be called in the future. Can be configured
 * to use a custom retry pattern, such as exponential backoff.
 */
class TimeoutTimer(
  private val dispatchQueue: DispatchQueue,
  private val callback: () -> Unit,
  private val timerCalculation: (tries: Int) -> Long
) {

  /** How many tries the Timer has attempted */
  private var tries: Int = 0

  /** The task that has been scheduled to be executed  */
  private var workItem: DispatchWorkItem? = null

  /**
   * Resets the Timer, clearing the number of current tries and stops
   * any scheduled timeouts.
   */
  fun reset() {
    this.tries = 0
    this.clearTimer()
  }

  /** Cancels any previous timeouts and scheduled a new one */
  fun scheduleTimeout() {
    this.clearTimer()

    // Schedule a task to be performed after the calculated timeout in milliseconds
    val timeout = timerCalculation(tries + 1)
    this.workItem = dispatchQueue.queue(timeout, TimeUnit.MILLISECONDS) {
      this.tries += 1
      this.callback.invoke()
    }
  }

  //------------------------------------------------------------------------------
  // make it open for disable retry mechanism
  //------------------------------------------------------------------------------
  fun clearTimer() {
    // Cancel the task from completing, allowing it to fi
    this.workItem?.cancel()
    this.workItem = null
  }
}