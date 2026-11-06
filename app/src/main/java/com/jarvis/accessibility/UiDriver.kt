package com.jarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.security.KillSwitch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Thin, safe wrapper around the accessibility service. All automation goes through here. */
object UiDriver {
    // JARVIS refuses to read or operate these (security by design, not bypassed).
    private val BLOCKED = listOf(
        "bank", "paytm", "phonepe", "paisa", "upi", "wallet", "pay", "bhim", "mobikwik", "freecharge", "npci", "zerodha", "groww",
"bitwarden", "lastpass", "1password", "keepass", "authenticator"
        
    )

    private val svc get() = JarvisAccessibilityService.instance
    val available: Boolean get() = svc != null

    fun rawPackage(): String? = svc?.rootInActiveWindow?.packageName?.toString()

    fun blockedReason(): String? {
        val p = rawPackage()?.lowercase() ?: return null
        return if (BLOCKED.any { p.contains(it) }) "JARVIS does not operate banking, payment or password apps ($p)." else null
    }

    /** Active window root, or null when unavailable or a blocked app is on screen. */
    fun root(): AccessibilityNodeInfo? {
        if (blockedReason() != null) return null
        return svc?.rootInActiveWindow
    }

    fun label(n: AccessibilityNodeInfo): String {
        if (n.isPassword) return ""
        return (n.text ?: n.contentDescription ?: n.hintText)?.toString().orEmpty()
    }

    fun allNodes(): List<AccessibilityNodeInfo> {
        val r = root() ?: return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>()
        walk(r, 0, out)
        return out
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, out: MutableList<AccessibilityNodeInfo>) {
        if (depth > 35 || out.size > 1500) return
        out.add(n)
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            walk(c, depth + 1, out)
        }
    }

    fun visible(): List<AccessibilityNodeInfo> = allNodes().filter { it.isVisibleToUser }

    fun findByText(query: String, exact: Boolean = false): List<AccessibilityNodeInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return visible().filter { n ->
            val l = label(n).lowercase()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            (if (exact) l == q else l.contains(q)) || id == q || id.endsWith("/$q")
        }.sortedBy { if (label(it).equals(query.trim(), ignoreCase = true)) 0 else 1 }
    }

    /** Clicks the node, or its nearest clickable ancestor, or taps its centre as a fallback. */
    suspend fun click(n: AccessibilityNodeInfo): Boolean {
        KillSwitch.check()
        if (blockedReason() != null) return false
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            depth++
        }
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    fun setText(n: AccessibilityNodeInfo, text: String): Boolean {
        KillSwitch.check()
        if (blockedReason() != null) return false
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    /** Input field. Prefers [hints]; otherwise the bottom-most one (chat boxes live at the bottom). */
    fun bottomEditable(hints: List<String> = emptyList()): AccessibilityNodeInfo? {
        val eds = visible().filter { it.isEditable }
        if (eds.isEmpty()) return null
        for (h in hints) {
            eds.firstOrNull { label(it).lowercase().contains(h) || it.viewIdResourceName?.lowercase()?.contains(h) == true }
                ?.let { return it }
        }
        return eds.maxByOrNull { n -> Rect().also { n.getBoundsInScreen(it) }.bottom }
    }

    /** Focused input, else the top-most one (search bars live at the top). */
    fun searchEditable(): AccessibilityNodeInfo? {
        root()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        return visible().filter { it.isEditable }.minByOrNull { n -> Rect().also { n.getBoundsInScreen(it) }.top }
    }

    suspend fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 350): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)

    private suspend fun gesture(p: Path, ms: Long): Boolean {
        KillSwitch.check()
        if (blockedReason() != null) return false
        val s = svc ?: return false
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
        return suspendCancellableCoroutine { cont ->
            val dispatched = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(d: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    fun global(action: Int): Boolean {
        if (KillSwitch.stopped || blockedReason() != null) return false
        return svc?.performGlobalAction(action) ?: false
    }

    fun screenSize(): Pair<Int, Int> {
        val m = svc?.resources?.displayMetrics ?: return 1080 to 2400
        return m.widthPixels to m.heightPixels
    }

    suspend fun waitFor(timeoutMs: Long, stepMs: Long = 200, cond: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            KillSwitch.check()
            if (cond()) return true
            delay(stepMs)
        }
        return cond()
    }

    /** Text summary of what is on screen (passwords are never included). */
    fun dump(filter: String? = null, max: Int = 60): String {
        val nodes = visible()
            .filter { label(it).isNotBlank() || it.isClickable || it.isEditable }
            .filter { filter.isNullOrBlank() || label(it).contains(filter, ignoreCase = true) }
        return nodes.take(max).joinToString("\n") { n ->
            val r = Rect()
            n.getBoundsInScreen(r)
            val kind = when {
                n.isEditable -> "input"
                n.isClickable -> "button"
                else -> "text"
            }
            "[$kind] \"${label(n).take(60)}\" id=${n.viewIdResourceName?.substringAfter('/') ?: "-"} @${r.centerX()},${r.centerY()}"
        }
    }

    /** Saves a screenshot into the app's private storage (Android 11+). Secure screens come out black. */
    suspend fun screenshot(): File? {
        val s = svc ?: return null
        if (Build.VERSION.SDK_INT < 30) return null
        if (blockedReason() != null) return null
        return suspendCancellableCoroutine { cont ->
            s.takeScreenshot(Display.DEFAULT_DISPLAY, s.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val file = try {
                        val hb = result.hardwareBuffer
                        val bmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                        hb.close()
                        val dir = File(s.filesDir, "screenshots").also { it.mkdirs() }
                        val f = File(dir, "shot_${System.currentTimeMillis()}.png")
                        f.outputStream().use { bmp?.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        f.takeIf { bmp != null }
                    } catch (e: Exception) {
                        null
                    }
                    if (cont.isActive) cont.resume(file)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }
}
