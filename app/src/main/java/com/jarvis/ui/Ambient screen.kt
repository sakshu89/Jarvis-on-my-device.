package com.jarvis.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jarvis.app.graph
import com.jarvis.settings.PerfProfile

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Shown while JARVIS is ON: pure black (AMOLED pixels fully off), core centred, landscape, system bars hidden.
 * Only STOP is visible. A pending approval card appears only while one is waiting (sending always needs it).
 * Back = show the normal controls.
 */
@Composable
fun AmbientScreen(vm: JarvisViewModel, profile: PerfProfile, onMic: () -> Unit, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    BackHandler(onBack = onExit)

    DisposableEffect(activity) {
        val a = activity
        if (a == null) {
            onDispose { }
        } else {
            val prevOrientation = a.requestedOrientation
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val ctrl = WindowInsetsControllerCompat(a.window, a.window.decorView)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 28) {
                a.window.attributes = a.window.attributes.also {
                    it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            onDispose {
                a.requestedOrientation = prevOrientation
                ctrl.show(WindowInsetsCompat.Type.systemBars())
                a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 28) {
                    a.window.attributes = a.window.attributes.also {
                        it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }

    val orbStyle = ctx.graph.settings.orbStyle
    val tap = Modifier
        .align(Alignment.Center)
        .fillMaxHeight(0.96f)
        .aspectRatio(1f)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onMic)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (orbStyle == "hud") {
            Orb(status = vm.status, level = vm.level, profile = profile, modifier = tap)
        } else {
            PlasmaOrb(status = vm.status, level = vm.level, on = vm.powered, profile = profile, modifier = tap)
        }

        // The ONLY control: STOP (cancels the running task).
        Box(Modifier.align(Alignment.TopEnd).displayCutoutPadding().padding(16.dp)) {
            HudButton("■ STOP", { vm.emergencyStop() }, color = Jx.red, filled = true)
        }

        // Safety: an outgoing message must be approved, so the card shows only while one is waiting.
        vm.pendingConfirm?.let { p ->
            Box(Modifier.align(Alignment.BottomCenter).displayCutoutPadding().padding(16.dp).widthIn(max = 520.dp)) {
                HudPanel(Modifier, accent = Jx.green) {
                    Mono("CONFIRM", Jx.green, 11, true)
                    Mono(p, Jx.text, 13, modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HudButton("SEND", { vm.answerConfirm(true) }, color = Jx.green, filled = true)
                        HudButton("CANCEL", { vm.answerConfirm(false) }, color = Jx.red)
                    }
                }
            }
        }
    }
}
