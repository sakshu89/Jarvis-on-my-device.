package com.jarvis.app

import android.app.Application
import android.content.Context
import com.jarvis.agent.ConfirmationManager
import com.jarvis.agent.PermissionGate
import com.jarvis.agent.ToolRegistry
import com.jarvis.agent.Agent
import com.jarvis.agent.buildRegistry
import com.jarvis.automation.Notifier
import com.jarvis.automation.ReminderStore
import com.jarvis.memory.ConversationContext
import com.jarvis.memory.MemoryStore
import com.jarvis.memory.TaskState
import com.jarvis.security.ActionLog
import com.jarvis.security.SecureStore
import com.jarvis.settings.AppSettings

/** Tiny manual dependency container (no DI framework: smaller APK, faster builds). */
class Graph(val app: Application) {
    val secure: SecureStore by lazy { SecureStore(app) }
    val settings: AppSettings by lazy { AppSettings(secure) }
    val memory: MemoryStore by lazy { MemoryStore(secure) }
    val reminders: ReminderStore by lazy { ReminderStore(secure) }
    val conversation = ConversationContext()
    val taskState = TaskState()
    val actionLog: ActionLog by lazy { ActionLog(app) }
    val confirmations = ConfirmationManager(app)
    val gate = PermissionGate()
    val registry: ToolRegistry by lazy { buildRegistry(this) }
    val agent: Agent by lazy { Agent(this) }
    /** One process-wide controller (UI + service share it), so voice commands work even when the screen is closed. */
    val controller: com.jarvis.ui.JarvisViewModel by lazy { com.jarvis.ui.JarvisViewModel(app) }
}

class JarvisApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = Graph(this)
        Power.init(this)
        Notifier.createChannels(this)
    }

    companion object {
        lateinit var instance: JarvisApp
            private set
    }
}

val Context.graph: Graph get() = (applicationContext as JarvisApp).graph
