package com.jarvis.app

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.jarvis.voice.WakeWord
import com.jarvis.voice.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.jarvis.automation.Notifier
import com.jarvis.security.KillSwitch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * JARVIS power state. Once ON it stays ON (survives closing the app, process death and reboot)
 * until the user says shutdown / stop (when idle) / taps SHUT DOWN. Then it is truly OFF:
 * service stopped, mic closed, nothing runs, nothing restarts until powered on again.
 */
object Power {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on
    val isOn: Boolean get() = _on.value

    fun init(ctx: Context) { _on.value = ctx.graph.settings.poweredOn }

    /** Starts the keep-alive service. Safe to call repeatedly; never throws. */
    fun ensureService(ctx: Context, mic: Boolean = false) {
        if (!isOn) return
        // mic=true only from the foreground UI: Android only allows a microphone service to be started while the app is visible.
        try { ContextCompat.startForegroundService(ctx, Intent(ctx, JarvisService::class.java).putExtra(JarvisService.EXTRA_MIC, mic)) } catch (_: Exception) {}
    }

    fun powerOn(ctx: Context) {
        ctx.graph.settings.poweredOn = true
        _on.value = true
        KillSwitch.reset()
        ensureService(ctx, mic = true)
    }

    fun shutdown(ctx: Context) {
        ctx.graph.settings.poweredOn = false
        _on.value = false          // set first so listeners can tell this is a shutdown
        KillSwitch.trigger()       // cancel anything still running
        ctx.stopService(Intent(ctx, JarvisService::class.java))
        Notifier.clearAll(ctx)
        Notifier.clear(ctx, Notifier.NID_POWER)
    }
}

/**
 * Foreground service = the "always on" presence. Sticky: Android restarts it if the process is killed.
 * With hands-free enabled it also runs the offline "Jarvis" wake-word listener (microphone service type).
 */
class JarvisService : Service() {
    companion object { const val EXTRA_MIC = "mic" }

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var engine: WakeWordEngine? = null
    private var enginePath = ""
    private var micMode = false
    private var idleSince = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    private fun micAllowed() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun handsFreeReady(): Boolean {
        val s = applicationContext.graph.settings
        return s.handsFree && s.wakeModelPath.isNotBlank() && File(s.wakeModelPath).exists() && micAllowed()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_MIC, false) == true) micMode = true
        val s = applicationContext.graph.settings
        val listening = micMode && handsFreeReady()
        val text = when {
            listening -> "Listening for \"Jarvis\". Say \"shutdown\" or tap SHUT DOWN to turn off."
            s.handsFree -> "Open JARVIS once to start hands-free listening. Say \"shutdown\" to turn off."
            else -> "Say \"shutdown\" or tap SHUT DOWN to turn it fully off."
        }
        val n = Notifier.powerNotification(this, text)
        when {
            Build.VERSION.SDK_INT >= 34 -> startForeground(
                Notifier.NID_POWER, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or (if (listening) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            )
            Build.VERSION.SDK_INT >= 30 && listening -> startForeground(Notifier.NID_POWER, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else -> startForeground(Notifier.NID_POWER, n)
        }
        if (!Power.isOn) { stopSelf(); return START_NOT_STICKY }
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { sc ->
                sc.launch { while (isActive) { delay(700); reconcile() } }
            }
        }
        return START_STICKY
    }

    /** Polls once per ~0.7 s: wake-word listener runs only when ON, hands-free is set up, and JARVIS itself is idle. */
    private fun reconcile() {
        val s = applicationContext.graph.settings
        val want = Power.isOn && micMode && handsFreeReady()
        if (!want) { engine?.pause(); idleSince = 0; return }
        if (engine == null || enginePath != s.wakeModelPath) {
            engine?.release()
            enginePath = s.wakeModelPath
            engine = WakeWordEngine(enginePath) { applicationContext.graph.controller.onWake() }
        }
        val now = SystemClock.elapsedRealtime()
        if (WakeWord.busy.value) { idleSince = 0; engine?.pause() }
        else {
            if (idleSince == 0L) idleSince = now
            if (now - idleSince > 800) engine?.resume() // small gap so it never hears JARVIS's own voice
        }
    }

    override fun onDestroy() {
        scope?.cancel(); scope = null
        engine?.release(); engine = null
        super.onDestroy()
    }
}
