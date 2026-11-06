package com.jarvis.agent

import com.jarvis.app.Graph
import com.jarvis.automation.SendMessageTool
import com.jarvis.tools.NavigateTool
import com.jarvis.tools.PlayMediaTool
import com.jarvis.tools.SetAlarmTool
import com.jarvis.tools.SetTimerTool
import com.jarvis.tools.TimeDateTool
import com.jarvis.tools.AccessibilityActionTool
import com.jarvis.tools.AnalyzeScreenTool
import com.jarvis.tools.BackTool
import com.jarvis.tools.BrowserTool
import com.jarvis.tools.CalculatorTool
import com.jarvis.tools.CallContactTool
import com.jarvis.tools.LockPhoneTool
import com.jarvis.tools.UnlockPhoneTool
import com.jarvis.tools.ClipboardTool
import com.jarvis.tools.DeviceInfoTool
import com.jarvis.tools.FileTool
import com.jarvis.tools.FindUiElementTool
import com.jarvis.tools.GitHubTool
import com.jarvis.tools.MemoryTool
import com.jarvis.tools.NotificationTool
import com.jarvis.tools.OpenAppTool
import com.jarvis.tools.ReminderTool
import com.jarvis.tools.ScreenshotTool
import com.jarvis.tools.SwipeTool
import com.jarvis.tools.TapTool
import com.jarvis.tools.TypeTextTool
import com.jarvis.tools.WebSearchTool

/** The complete list of things JARVIS can do. Nothing outside this list is reachable by the model. */
fun buildRegistry(g: Graph): ToolRegistry = ToolRegistry().apply {
    val app = g.app
    register(OpenAppTool(app))
    register(TimeDateTool())
    register(PlayMediaTool(app))
    register(NavigateTool(app))
    register(SetTimerTool(app))
    register(SetAlarmTool(app))
    register(SendMessageTool(g))
    register(CallContactTool(app))
    register(LockPhoneTool())
    register(UnlockPhoneTool(app))
    register(FindUiElementTool())
    register(AccessibilityActionTool())
    register(TapTool())
    register(SwipeTool())
    register(TypeTextTool())
    register(BackTool())
    register(ScreenshotTool())
    register(AnalyzeScreenTool(g))
    register(WebSearchTool())
    register(BrowserTool(app))
    register(ClipboardTool(app))
    register(NotificationTool(app))
    register(ReminderTool(app, g))
    register(FileTool(app, g))
    register(GitHubTool(g))
    register(CalculatorTool())
    register(DeviceInfoTool(app))
    register(MemoryTool(g))
}
