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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jarvis.settings.PerfProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val HOT = Color(0xFFFFD08A)
private val WHITE = Color(0xFFFFF6E0)

fun Status.plasma(): Color = when (this) {
    Status.IDLE -> Color(0xFFFF7A00)
    Status.LISTENING -> Color(0xFFFFA52E)
    Status.THINKING -> Color(0xFFFF4A1C)
    Status.SPEAKING -> Color(0xFFFFB84D)
    Status.ERROR -> Color(0xFFFF1744)
}

private class Pt(val x: Float, val y: Float, val z: Float, val r: Float, val ph: Float)
private class P3 { var x = 0f; var y = 0f; var z = 0f }

private fun sphere(rng: Random, n: Int, shell: Boolean): List<Pt> = List(n) {
    val u = rng.nextFloat() * 2f - 1f
    val phi = rng.nextFloat() * 2f * PI.toFloat()
    val s = sqrt(1f - u * u)
    val r = if (shell) 0.55f + 0.45f * sqrt(rng.nextFloat()) else 0.55f + 0.45f * rng.nextFloat()
    Pt(s * cos(phi), s * sin(phi), u, r, rng.nextFloat() * 6.28f)
}

private val ringTilt = floatArrayOf(0.25f, 1.0f, 1.9f, 2.6f)
private val ringRoll = floatArrayOf(0f, 0.9f, 1.8f, 2.7f)
private val ringRad = floatArrayOf(1.0f, 0.86f, 0.72f, 0.95f)

/**
 * Fiery plasma core: glowing nucleus, flickering spokes, tilted orbit rings and a particle shell.
 * [on] = false -> collapses to a dim ember; true -> bursts out and stays alive until switched off.
 */
@Composable
fun PlasmaOrb(status: Status, level: Float, on: Boolean, profile: PerfProfile, modifier: Modifier = Modifier) {
    val tint = status.plasma()
    val nParticles = when (profile.name) { "Lite" -> 140; "High" -> 650; else -> 380 }
    val nSpokes = when (profile.name) { "Lite" -> 24; "High" -> 72; else -> 48 }
    val particles = remember(nParticles) { sphere(Random(7), nParticles, true) }
    val spokes = remember(nSpokes) { sphere(Random(11), nSpokes, false) }

    var t by remember { mutableFloatStateOf(0f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pw by remember { mutableFloatStateOf(if (on) 1f else 0f) }
    val speed = when (status) { Status.IDLE -> 0.25f; Status.LISTENING -> 0.5f; Status.THINKING -> 2.2f; Status.SPEAKING -> 0.7f; Status.ERROR -> 0.08f }
    val spd by rememberUpdatedState(speed)
    val lvl by rememberUpdatedState(level)
    val target by rememberUpdatedState(if (on) 1f else 0f)

    LaunchedEffect(profile) {
        if (!profile.animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (last == 0L) last = n
                else {
                    val dt = min((n - last) / 1_000_000_000f, 0.1f)
                    if (dt >= profile.minFrameSeconds) {
                        yaw += spd * dt
                        t += dt
                        pw += (target - pw) * min(1f, dt * (if (target > pw) 2.2f else 3.5f))
                        last = n
                    }
                }
            }
        }
    }

    Canvas(modifier) {
        val dp = density
        val c = center
        val p = if (profile.animate) pw.coerceIn(0f, 1f) else target
        val tt = t
        val lv = lvl.coerceIn(0f, 1f)
        val base = min(size.width, size.height) / 2f * 0.92f
        val R = base * (0.18f + 0.82f * p)
        val cy = cos(yaw); val sy = sin(yaw)
        val pitch = 0.55f + 0.25f * sin(tt * 0.3f)
        val cp = cos(pitch); val sp = sin(pitch)
        val o = P3()
        fun proj(x: Float, y: Float, z: Float) {
            val x1 = x * cy + z * sy
            val z1 = -x * sy + z * cy
            o.x = c.x + x1 * R
            o.y = c.y + (y * cp - z1 * sp) * R
            o.z = y * sp + z1 * cp
        }

        // Ember (always visible, even when off)
        val ember = 0.35f + 0.15f * sin(tt * 1.6f)
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = ember * (1f - p) + 0.001f), Color.Transparent), c, base * 0.22f), base * 0.22f, c)
        drawCircle(HOT.copy(alpha = 0.5f * (1f - p) + 0.001f), 3f * dp, c)
        if (p < 0.02f) return@Canvas

        val a = p // global alpha

        // Particle shell (3 depth buckets -> 3 draw calls)
        val far = ArrayList<Offset>(); val mid = ArrayList<Offset>(); val near = ArrayList<Offset>()
        val breath = 1f + 0.03f * sin(tt * 1.4f) + 0.10f * lv
        for (q in particles) {
            val rr = q.r * breath * (1f + 0.04f * sin(tt * 1.7f + q.ph))
            proj(q.x * rr, q.y * rr, q.z * rr)
            val pt = Offset(o.x, o.y)
            when { o.z < -0.33f -> far.add(pt); o.z < 0.33f -> mid.add(pt); else -> near.add(pt) }
        }
        drawPoints(far, PointMode.Points, tint.copy(alpha = 0.35f * a), 1.3f * dp, StrokeCap.Round)
        drawPoints(mid, PointMode.Points, tint.copy(alpha = 0.65f * a), 1.8f * dp, StrokeCap.Round)
        drawPoints(near, PointMode.Points, HOT.copy(alpha = 0.9f * a), 2.3f * dp, StrokeCap.Round)

        // Spokes (flickering radial lines)
        val r0 = 0.07f
        for (s in spokes) {
            val flick = 0.78f + 0.22f * sin(tt * 3.1f + s.ph * 2f) + 0.35f * lv * sin(tt * 9f + s.ph)
            val len = (s.r * flick).coerceIn(0.2f, 1.25f)
            proj(s.x * r0, s.y * r0, s.z * r0); val sx = o.x; val sy2 = o.y
            proj(s.x * len, s.y * len, s.z * len)
            val depth = (o.z + 1f) / 2f
            val e = Offset(o.x, o.y)
            if (profile.glowLayers > 0) drawLine(tint.copy(alpha = 0.10f * a), Offset(sx, sy2), e, 5f * dp, StrokeCap.Round)
            drawLine(tint.copy(alpha = (0.35f + 0.5f * depth) * a), Offset(sx, sy2), e, 1.2f * dp, StrokeCap.Round)
        }

        // Orbit rings with a travelling bright arc
        val seg = 72
        for (i in 0 until 4) {
            val tilt = ringTilt[i] + 0.15f * sin(tt * 0.4f + i)
            val roll = ringRoll[i] + tt * 0.12f * (i + 1) * (if (i % 2 == 0) 1f else -1f)
            val ct = cos(tilt); val st = sin(tilt); val cr = cos(roll); val sr = sin(roll)
            val ring = Path(); val hot = Path()
            val hotStart = ((tt * (14f + 6f * i) + i * 17f).toInt()) % seg
            for (k in 0..seg) {
                val th = k * 2f * PI.toFloat() / seg
                val x = cos(th) * ringRad[i]; val y0 = sin(th) * ringRad[i]
                val y = y0 * ct; val z = y0 * st
                proj(x * cr - y * sr, x * sr + y * cr, z)
                if (k == 0) ring.moveTo(o.x, o.y) else ring.lineTo(o.x, o.y)
                val rel = (k - hotStart + seg) % seg
                if (rel < 16) { if (rel == 0) hot.moveTo(o.x, o.y) else hot.lineTo(o.x, o.y) }
            }
            drawPath(ring, tint.copy(alpha = 0.45f * a), style = Stroke(1.3f * dp))
            if (profile.glowLayers > 0) drawPath(hot, tint.copy(alpha = 0.25f * a), style = Stroke(9f * dp, cap = StrokeCap.Round))
            drawPath(hot, HOT.copy(alpha = 0.95f * a), style = Stroke(3.2f * dp, cap = StrokeCap.Round))
        }

        // Power-on shockwave
        if (p < 0.98f && target > 0.5f) drawCircle(tint.copy(alpha = (1f - p) * 0.6f), base * (0.3f + 0.8f * p), c, style = Stroke(2f * dp))

        // Nucleus
        val pulse = 1f + 0.10f * sin(tt * 4f) + 0.45f * lv
        val glowR = base * 0.55f * pulse * p
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f * a), tint.copy(alpha = 0.12f * a), Color.Transparent), c, glowR), glowR, c)
        val midR = base * 0.17f * pulse * p
        drawCircle(Brush.radialGradient(listOf(HOT.copy(alpha = a), tint.copy(alpha = 0.5f * a), Color.Transparent), c, midR), midR, c)
        drawCircle(WHITE.copy(alpha = a), base * 0.05f * pulse * p, c)
    }
}
