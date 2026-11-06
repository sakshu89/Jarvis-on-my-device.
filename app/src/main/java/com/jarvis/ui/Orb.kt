package com.jarvis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jarvis.settings.PerfProfile
import com.jarvis.ui.Status.ERROR
import com.jarvis.ui.Status.IDLE
import com.jarvis.ui.Status.LISTENING
import com.jarvis.ui.Status.SPEAKING
import com.jarvis.ui.Status.THINKING
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class Status { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

fun Status.color(): Color = when (this) {
    IDLE -> Jx.cyan.copy(alpha = 0.75f)
    LISTENING -> Jx.cyan
    THINKING -> Jx.violet
    SPEAKING -> Jx.green
    ERROR -> Jx.red
}

private fun DrawScope.glowArc(col: Color, topLeft: Offset, sz: Size, start: Float, sweep: Float, width: Float, layers: Int) {
    for (i in layers downTo 1) {
        drawArc(col.copy(alpha = 0.07f * (layers - i + 1)), start, sweep, false, topLeft, sz, style = Stroke(width * (1 + i * 1.4f), cap = StrokeCap.Round))
    }
    drawArc(col, start, sweep, false, topLeft, sz, style = Stroke(width, cap = StrokeCap.Round))
}

/**
 * Original HUD "core". Time-based animation driven by the frame clock, so it is smooth at
 * 60/90/120 Hz alike. The [PerfProfile] decides how much is drawn (Lite = static, no glow).
 */
@Composable
fun Orb(status: Status, level: Float, profile: PerfProfile, modifier: Modifier = Modifier) {
    val color = status.color()
    var t by remember { mutableFloatStateOf(0f) }
    var angle by remember { mutableFloatStateOf(0f) }
    val speed = when (status) { IDLE -> 12f; LISTENING -> 30f; THINKING -> 150f; SPEAKING -> 45f; ERROR -> 4f }
    val spd by rememberUpdatedState(speed)
    val lvl by rememberUpdatedState(level)

    LaunchedEffect(profile) {
        if (!profile.animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (last == 0L) last = n
                else {
                    val dt = min((n - last) / 1_000_000_000f, 0.1f)
                    if (dt >= profile.minFrameSeconds) {
                        angle = (angle + spd * dt) % 360f
                        t += dt
                        last = n
                    }
                }
            }
        }
    }

    Canvas(modifier) {
        val c = center
        val r = min(size.width, size.height) / 2f * 0.94f
        val a = angle
        val tt = t
        val lv = lvl.coerceIn(0f, 1f)
        val dp = density

        if (profile.grid) {
            val step = 28 * dp
            var x = 0f
            while (x < size.width) { drawLine(Jx.cyan.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, size.height), dp); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Jx.cyan.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), dp); y += step }
        }

        // outer hairline ring + tick ring
        drawCircle(color.copy(alpha = 0.25f), r, c, style = Stroke(1.5f * dp))
        val ticks = if (profile.animate) 60 else 12
        for (i in 0 until ticks) {
            val ang = (i * 360f / ticks + a * 0.2f) * (PI.toFloat() / 180f)
            val long = i % 5 == 0
            val r1 = r * 0.93f
            val r2 = r * (if (long) 0.89f else 0.91f)
            drawLine(color.copy(alpha = if (long) 0.7f else 0.35f), c + Offset(cos(ang) * r1, sin(ang) * r1), c + Offset(cos(ang) * r2, sin(ang) * r2), dp)
        }

        // rotating arc segments
        val arcR = r * 0.82f
        val tl = Offset(c.x - arcR, c.y - arcR)
        val sz = Size(arcR * 2, arcR * 2)
        for (k in 0 until 3) glowArc(color, tl, sz, a + k * 120f, 70f, 4 * dp, profile.glowLayers)

        // counter-rotating dashed ring
        val dashR = r * 0.68f
        drawCircle(
            color.copy(alpha = 0.55f), dashR, c,
            style = Stroke(2 * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * dp, 10 * dp), -a * 0.8f))
        )

        // voice bars
        if (profile.bars) {
            val n = 48
            val base = r * 0.50f
            for (i in 0 until n) {
                val ang = (i * 360f / n) * (PI.toFloat() / 180f)
                val wave = 0.5f + 0.5f * sin(tt * 6f + i * 0.7f)
                val len = 3 * dp + lv * r * 0.16f * wave
                drawLine(
                    color.copy(alpha = 0.8f), c + Offset(cos(ang) * base, sin(ang) * base),
                    c + Offset(cos(ang) * (base + len), sin(ang) * (base + len)), 2 * dp, StrokeCap.Round
                )
            }
        }

        // core
        val coreR = r * (0.40f + 0.06f * lv + 0.012f * sin(tt * 3f))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.25f), Color.Transparent), c, coreR * 1.5f), coreR * 1.5f, c)
        drawCircle(color, r * 0.30f, c, style = Stroke(1.5f * dp))
    }
}
