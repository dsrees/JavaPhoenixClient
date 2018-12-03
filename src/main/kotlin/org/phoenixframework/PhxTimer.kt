package org.phoenixframework

import java.util.*
import kotlin.concurrent.schedule

class PhxTimer(
        private val callback: () -> Unit,
        private val timerCalculation: (tries: Int) -> Long
) {

    // The underlying Java timer
    private var timer: Timer? = null
    // How many tries the Timer has attempted
    private var tries: Int = 0


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

        // Start up a new Timer
        val timeout = timerCalculation(tries)
        this.timer = Timer()
        this.timer?.schedule(timeout) {
            tries += 1
            callback()
        }
    }

    //------------------------------------------------------------------------------
    // Private
    //------------------------------------------------------------------------------
    private fun clearTimer() {
        this.timer?.cancel()
        this.timer = null
    }
}
