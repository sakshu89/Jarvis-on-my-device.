package com.jarvis.security

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

/**
 * Global emergency stop. Every tool and every UI-automation wait loop calls [check],
 * so a trigger cancels pending automation at the next step.
 */
object KillSwitch {
    @Volatile
    var stopped: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun trigger() {
        stopped = true
        listeners.forEach { l -> runCatching { l() } }
    }

    fun reset() {
        stopped = false
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    /** Throws a CancellationException when the emergency stop is active. */
    fun check() {
        if (stopped) throw CancellationException("Emergency stop")
    }
}
