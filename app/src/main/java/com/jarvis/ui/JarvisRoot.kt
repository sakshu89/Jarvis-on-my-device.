package com.jarvis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.jarvis.app.graph
import com.jarvis.security.PermissionStatus
import com.jarvis.settings.Perf

@Composable
private fun Chip(text: String, good: Boolean) {
    Mono(text, if (good) Jx.green else Jx.amber, 10, true, Modifier.padding(end = 8.dp))
}

@Composable
fun JarvisRoot(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val g = ctx.graph
    var tab by rememberSaveable { mutableStateOf(if (g.settings.firstRunDone) "home" else "setup") }
    var perfTick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    @Suppress("UNUSED_VARIABLE") val refresh = resume
    val profile = remember(perfTick) { Perf.resolve(ctx, g.settings.perfMode) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.onMic() else vm.banner = "Microphone permission denied. Enable it in the SETUP tab."
    }
    val onMic = {
        if (vm.micGranted()) vm.onMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Ambient mode: while JARVIS is ON show only the black, landscape, centred core + STOP.
    var ambient by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(vm.powered) { if (vm.powered) ambient = true }
    val showAmbient = vm.powered && ambient && g.settings.ambientMode

    if (showAmbient) AmbientScreen(vm, profile, onMic) { ambient = false } else
    Column(Modifier.fillMaxSize().background(Jx.bg).statusBarsPadding().navigationBarsPadding()) {
        // HUD top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Mono("J.A.R.V.I.S", Jx.cyan, 18, true)
                Row {
                    Chip(if (PermissionStatus.accessibility(ctx)) "A11Y ON" else "A11Y OFF", PermissionStatus.accessibility(ctx))
                    Chip(if (PermissionStatus.microphone(ctx)) "MIC OK" else "MIC OFF", PermissionStatus.microphone(ctx))
                    Chip(if (g.settings.hasAiKey()) "AI KEY OK" else "NO AI KEY", g.settings.hasAiKey())
                    Chip(profile.name.uppercase(), true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.powered && g.settings.ambientMode) {
                    HudButton("◉ AMBIENT", { ambient = true })
                    Spacer(Modifier.width(8.dp))
                }
                HudButton("■ STOP", { vm.emergencyStop() }, color = Jx.red, filled = true)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "setup" -> PermissionsScreen(firstRun = !g.settings.firstRunDone) {
                    g.settings.firstRunDone = true
                    tab = if (g.settings.hasAiKey()) "home" else "settings"
                }
                "home" -> HomeScreen(vm, profile, onMic)
                "history" -> HistoryScreen()
                "memory" -> MemoryScreen()
                "settings" -> SettingsScreen { perfTick++ }
            }
        }

        if (!(tab == "setup" && !g.settings.firstRunDone)) {
            Row(Modifier.fillMaxWidth().background(Jx.panel).padding(vertical = 6.dp)) {
                listOf("home" to "CORE", "history" to "LOG", "memory" to "MEMORY", "setup" to "SETUP", "settings" to "CONFIG").forEach { (id, label) ->
                    Box(Modifier.weight(1f).clickable { tab = id }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Mono(label, if (tab == id) Jx.cyan else Jx.dim, 12, tab == id)
                    }
                }
            }
        }
    }
}
