package com.jarvis.agent

import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.accessibility.UiDriver
import com.jarvis.security.KillSwitch

/**
 * Non-interactive checks that must pass immediately before every tool execution.
 * Risk-based user approval is handled by Agent so every tool uses one consistent flow.
 */
class PermissionGate {
    fun check(tool: Tool): String? {
        if (KillSwitch.stopped) return "Emergency stop is active."
        if (tool.requiresAccessibility) {
            if (!JarvisAccessibilityService.isConnected) {
                return "The accessibility service is not running. Enable JARVIS in Settings > Accessibility > Installed apps. " +
                    "On Android 13+ a sideloaded app first needs 'Allow restricted settings' in its App info page."
            }
            UiDriver.blockedReason()?.let { return it }
        }
        return null
    }
}
