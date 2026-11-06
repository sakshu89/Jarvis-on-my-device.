package com.jarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Passive service: it does nothing on its own. It only hands the system's accessibility
 * handle to [UiDriver], which is called by tools when the user gives a command.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intentionally empty */ }

    override fun onInterrupt() { /* nothing to interrupt */ }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set
        val isConnected: Boolean get() = instance != null
    }
}
