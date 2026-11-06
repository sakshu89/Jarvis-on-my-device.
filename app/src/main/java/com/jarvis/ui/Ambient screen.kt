
package com.jarvis.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

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
 * Ambient JARVIS screen.
 * Black background, centered orb, landscape orientation,
 * hidden system bars and STOP control.
 */
@Composable
fun AmbientScreen(
    vm: JarvisViewModel,
    profile: PerfProfile,
    onMic: () -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

    BackHandler(onBack = onExit)

    DisposableEffect(activity) {
        val a = activity

        if (a == null) {
            onDispose { }
        } else {
            val prevOrientation = a.requestedOrientation

            a.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            val ctrl = WindowInsetsControllerCompat(
                a.window,
                a.window.decorView
            )

            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            ctrl.hide(WindowInsetsCompat.Type.systemBars())

            a.window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )

            if (Build.VERSION.SDK_INT >= 28) {
                a.window.attributes =
                    a.window.attributes.also {
                        it.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams
                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }

            onDispose {
                a.requestedOrientation = prevOrientation

                ctrl.show(WindowInsetsCompat.Type.systemBars())

                a.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )

                if (Build.VERSION.SDK_INT >= 28) {
                    a.window.attributes =
                        a.window.attributes.also {
                            it.layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams
                                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        }
                }
            }
        }
    }

    val orbStyle = ctx.graph.settings.orbStyle

    val tap = Modifier
        .fillMaxHeight(0.96f)
        .aspectRatio(1f)
        .clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
            indication = null,
            onClick = onMic
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {

        if (orbStyle == "hud") {
            Orb(
                status = vm.status,
                level = vm.level,
                profile = profile,
                modifier = tap
            )
        } else {
            AmbientNebula(
                status = vm.status,
                level = vm.level,
                on = vm.powered,
                profile = profile,
                modifier = tap
            )
        }

        // STOP button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .displayCutoutPadding()
                .padding(16.dp)
        ) {
            HudButton(
                "■ STOP",
                { vm.emergencyStop() },
                color = Jx.red,
                filled = true
            )
        }

        // Pending confirmation card
        vm.pendingConfirm?.let { p ->

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .displayCutoutPadding()
                    .padding(16.dp)
                    .widthIn(max = 520.dp)
            ) {
                HudPanel(
                    Modifier,
                    accent = Jx.green
                ) {
                    Mono(
                        "CONFIRM",
                        Jx.green,
                        11,
                        true
                    )

                    Mono(
                        p,
                        Jx.text,
                        13,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudButton(
                            "SEND",
                            { vm.answerConfirm(true) },
                            color = Jx.green,
                            filled = true
                        )

                        HudButton(
                            "CANCEL",
                            { vm.answerConfirm(false) },
                            color = Jx.red
                        )
                    }
                }
            }
        }
    }
}

/*
 * AmbientNebula:
 * A swirling cloud of glowing particles.
 *
 * on = false: collapses to a dim ember.
 * on = true: intro animation starts.
 * level: microphone/voice level.
 * status: controls animation speed.
 */

private const val NEB_TWO_PI = 6.2831855f

private val NEB_HUE_U = floatArrayOf(
    0f, 0.35f, 0.55f, 0.64f, 0.78f, 0.90f, 1f
)

private val NEB_HUE_D = floatArrayOf(
    218f, 222f, 275f, 318f, 352f, 382f, 398f
)

private fun nebHueAt(u: Float): Float {
    val x = u.coerceIn(0f, 1f)

    for (i in 1 until NEB_HUE_U.size) {
        if (x <= NEB_HUE_U[i]) {
            val f =
                (x - NEB_HUE_U[i - 1]) /
                (NEB_HUE_U[i] - NEB_HUE_U[i - 1])

            return (
                NEB_HUE_D[i - 1] +
                (NEB_HUE_D[i] - NEB_HUE_D[i - 1]) * f
            ) % 360f
        }
    }

    return NEB_HUE_D[NEB_HUE_D.size - 1] % 360f
}

/**
 * Triangle wave from 0 to 1 and back to 0.
 */
private fun nebPingPong(x: Float): Float {
    val f = x - floor(x)
    return 1f - abs(2f * f - 1f)
}

private fun nebHsv(
    h: Float,
    s: Float,
    v: Float,
    a: Float = 1f
): Color {

    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val i = floor(hh).toInt()
    val f = hh - i

    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))

    return when (i % 6) {
        0 -> Color(v, t, p, a)
        1 -> Color(q, v, p, a)
        2 -> Color(p, v, t, a)
        3 -> Color(p, q, v, a)
        4 -> Color(t, p, v, a)
        else -> Color(v, p, q, a)
    }
}

private class NebCloud(
    n: Int,
    seed: Int
) {

    val r = FloatArray(n)
    val th = FloatArray(n)
    val w = FloatArray(n)
    val ph = FloatArray(n)
    val tw = FloatArray(n)
    val lift = FloatArray(n)
    val tier = IntArray(n)

    init {
        val rng = Random(seed)

        for (i in 0 until n) {

            val u = rng.nextFloat()

            var rad =
                0.03f + 0.97f * u.pow(1.7f)

            if (rng.nextFloat() < 0.07f) {
                rad *= 1f + rng.nextFloat() * 0.5f
            }

            r[i] = rad

            th[i] = rng.nextFloat() * NEB_TWO_PI

            w[i] =
                0.55f / (0.2f + rad).pow(0.8f)

            ph[i] = rng.nextFloat() * NEB_TWO_PI

            tw[i] =
                1.5f + rng.nextFloat() * 3f

            lift[i] =
                rng.nextFloat() * 2f - 1f

            val q = rng.nextFloat()

            tier[i] =
                if (q < 0.6f) 0
                else if (q < 0.9f) 1
                else 2
        }
    }
}

@Composable
private fun AmbientNebula(
    status: Status,
    level: Float,
    on: Boolean,
    profile: PerfProfile,
    modifier: Modifier = Modifier
) {

    val n = when (profile.name) {
        "Lite" -> 900
        "High" -> 4500
        else -> 2600
    }

    val cloud = remember(n) {
        NebCloud(n, 42)
    }

    val buf = remember(n) {
        Array(3) {
            FloatArray(n * 2)
        }
    }

    val paints = remember {
        Array(3) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeCap = Paint.Cap.ROUND
                xfermode = PorterDuffXfermode(
                    PorterDuff.Mode.ADD
                )
            }
        }
    }

    var t by remember {
        mutableFloatStateOf(0f)
    }

    var swirl by remember {
        mutableFloatStateOf(0f)
    }

    var pw by remember {
        mutableFloatStateOf(if (on) 1f else 0f)
    }

    val speed = when (status) {
        Status.IDLE -> 1f
        Status.LISTENING -> 1.3f
        Status.THINKING -> 2.4f
        Status.SPEAKING -> 1.6f
        Status.ERROR -> 0.4f
    }

    val spd by rememberUpdatedState(speed)
    val lvl by rememberUpdatedState(level)
    val target by rememberUpdatedState(if (on) 1f else 0f)
    val isErr by rememberUpdatedState(status == Status.ERROR)

    // Restart intro whenever JARVIS is powered on.
    LaunchedEffect(on, profile.animate) {
        if (on) {
            t = if (profile.animate) 0f else 6.5f
        }
    }

    // Animation loop.
    LaunchedEffect(profile) {

        if (!profile.animate) {
            return@LaunchedEffect
        }

        var last = 0L

        while (true) {

            withFrameNanos { nanos ->

                if (last == 0L) {
                    last = nanos
                } else {

                    val dt = min(
                        (nanos - last) / 1_000_000_000f,
                        0.1f
                    )

                    if (dt >= profile.minFrameSeconds) {

                        t += dt

                        swirl += dt * spd

                        pw += (target - pw) *
                            min(
                                1f,
                                dt * (
                                    if (target > pw) 2.5f
                                    else 3.5f
                                )
                            )

                        last = nanos
                    }
                }
            }
        }
    }

    Canvas(modifier) {

        val dp = density
        val c = center

        val p = (
            if (profile.animate) pw else target
        ).coerceIn(0f, 1f)

        val tt = t

        val sw = if (profile.animate) swirl else 6.5f

        val lv = lvl.coerceIn(0f, 1f)

        val rx = min(
            size.width * 0.48f,
            size.height * 0.95f
        )

        val u = if (isErr) {
            0.78f
        } else {
            nebPingPong(tt / 22f)
        }

        val hue = nebHueAt(u)

        val tint = nebHsv(
            hue,
            0.85f,
            1f
        )

        // Ember always visible.
        val emberR = rx * 0.12f

        drawCircle(
            Brush.radialGradient(
                listOf(
                    tint.copy(
                        alpha = 0.5f * (1f - p) + 0.001f
                    ),
                    Color.Transparent
                ),
                c,
                emberR
            ),
            emberR,
            c
        )

        if (p < 0.02f) {
            return@Canvas
        }

        // Cloud expansion.
        val grow =
            (0.04f + 0.9f * (1f - exp(-tt / 2.4f))) *
            (1f + 0.25f * (1f - exp(-tt / 9f)))

        val radius =
            rx * grow * p * (1f + 0.06f * lv)

        val driftX =
            -0.12f * radius * (1f - exp(-tt / 8f))

        // Soft background glow.
        val glowR = radius * 0.6f

        drawCircle(
            Brush.radialGradient(
                listOf(
                    tint.copy(alpha = 0.09f * p),
                    Color.Transparent
                ),
                c,
                glowR
            ),
            glowR,
            c
        )

        // Particle positions.
        val tilt = -0.16f
        val ct = cos(tilt)
        val st = sin(tilt)
        val flat = 0.52f

        val counts = IntArray(3)

        for (i in 0 until n) {

            if (
                sin(
                    tt * cloud.tw[i] + cloud.ph[i]
                ) < -0.55f
            ) {
                continue
            }

            val ang =
                cloud.th[i] + cloud.w[i] * sw

            val rr = cloud.r[i]

            var x = rr * cos(ang)
            var y = rr * sin(ang) * flat

            x += 0.035f *
                sin(tt * 0.9f + cloud.ph[i] * 3f)

            y += 0.03f *
                cos(tt * 0.8f + cloud.ph[i] * 2f) +
                cloud.lift[i] * 0.05f

            val xr = x * ct - y * st
            val yr = x * st + y * ct

            val tr = cloud.tier[i]
            val k = counts[tr]

            buf[tr][k] =
                c.x + xr * radius + driftX

            buf[tr][k + 1] =
                c.y + yr * radius

            counts[tr] = k + 2
        }

        val sizes = floatArrayOf(
            0.9f,
            1.3f,
            1.8f
        )

        val cols = arrayOf(
            nebHsv(hue, 0.95f, 0.9f, 0.7f * p),
            nebHsv(hue + 8f, 0.8f, 1f, 0.9f * p),
            nebHsv(hue + 15f, 0.35f, 1f, 1f * p)
        )

        drawIntoCanvas { canvas ->

            val nc = canvas.nativeCanvas

            for (j in 0 until 3) {

                if (counts[j] > 0) {

                    paints[j].color =
                        cols[j].toArgb()

                    paints[j].strokeWidth =
                        sizes[j] * dp

                    nc.drawPoints(
                        buf[j],
                        0,
                        counts[j],
                        paints[j]
                    )
                }
            }
        }

        // Bright central clump.
        val coreR = radius * 0.16f

        val coreA =
            (0.25f + 0.4f * lv) * p

        drawCircle(
            Brush.radialGradient(
                listOf(
                    nebHsv(hue, 0.3f, 1f, coreA),
                    Color.Transparent
                ),
                c,
                coreR
            ),
            coreR,
            c
        )

        // Glowing translucent sphere.
        val sv = (
            (tt - 3f) / 1.6f
        ).coerceIn(0f, 1f)

        if (sv > 0.01f) {

            val e =
                sv * sv * (3f - 2f * sv)

            val a = e * p

            val moved =
                1f - exp(-max(tt - 3f, 0f) / 5f)

            val sc = Offset(
                c.x +
                    radius * 0.05f -
                    radius * 0.5f * moved +
                    driftX,

                c.y +
                    radius * 0.03f * sin(tt * 0.5f)
            )

            val rs =
                radius *
                0.17f *
                (0.92f + 0.08f * sin(tt * 1.3f)) *
                (1f + 0.25f * lv)

            val sphere =
                nebHsv(hue + 12f, 0.7f, 1f)

            drawCircle(
                Brush.radialGradient(
                    listOf(
                        sphere.copy(alpha = 0.12f * a),
                        Color.Transparent
                    ),
                    sc,
                    rs * 2.2f
                ),
                rs * 2.2f,
                sc
            )

            drawCircle(
                Brush.radialGradient(
                    listOf(
                        sphere.copy(alpha = 0.02f * a),
                        sphere.copy(alpha = 0.12f * a)
                    ),
                    sc,
                    rs
                ),
                rs,
                sc
            )

            drawCircle(
                sphere.copy(alpha = 0.55f * a),
                rs,
                sc,
                style = Stroke(1.5f * dp)
            )

            drawArc(
                color = Color.White.copy(alpha = 0.55f * a),
                startAngle = (tt * 40f) % 360f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(
                    sc.x - rs * 0.85f,
                    sc.y - rs * 0.85f
                ),
                size = Size(
                    rs * 1.7f,
                    rs * 1.7f
                ),
                style = Stroke(1.5f * dp)
            )
        }
    }
}
