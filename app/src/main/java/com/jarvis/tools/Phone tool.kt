package com.jarvis.tools

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.jarvis.accessibility.JarvisAccessibilityService
import com.jarvis.accessibility.UiDriver
import com.jarvis.agent.Risk
import com.jarvis.agent.Tool
import com.jarvis.agent.ToolResult
import com.jarvis.agent.fail
import com.jarvis.agent.ok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Calls a saved contact (by name) or a phone number. Asks the user to approve every call (MEDIUM risk). */
class CallContactTool(private val ctx: Context) : Tool {
    override val name = "call_contact"
    override val description =
        "Call a saved contact or a phone number. args: {\"name\":\"Mom\"} or {\"number\":\"+911234567890\"}"
    override val risk = Risk.MEDIUM
    override val timeoutMs = 20_000L

    private fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    override suspend fun execute(args: JSONObject): ToolResult {
        val name = args.optString("name").trim()
        var number = args.optString("number").filter { it.isDigit() || it == '+' }
        var label = if (name.isNotEmpty()) name else number

        if (number.length < 3) {
            if (name.isEmpty()) return fail("call_contact needs a contact name or a phone number.")
            if (!has(Manifest.permission.READ_CONTACTS))
                return fail("Contacts permission is off. Open JARVIS > SETUP > Contacts & Phone and tap ALLOW.")
            val found = withContext(Dispatchers.IO) { findContact(name) }
                ?: return fail("I couldn't find a contact named '$name'.")
            label = found.first
            number = found.second
        }
        if (number.length < 3) return fail("That contact has no usable phone number.")

        val uri = Uri.parse("tel:" + Uri.encode(number))
        return try {
            if (has(Manifest.permission.CALL_PHONE)) {
                ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ok("Calling $label.")
            } else {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ok("I opened the dialer with $label's number; tap the green call button. (Allow Phone permission in SETUP so I can dial directly.)")
            }
        } catch (e: Exception) {
            fail("I could not start the call: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Best match by saved name: exact, then starts-with, then contains. */
    private fun findContact(query: String): Pair<String, String>? {
        val rows = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$query%"),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val p = c.getString(1) ?: continue
                rows.add(n to p)
            }
        }
        if (rows.isEmpty()) return null
        val q = query.lowercase()
        return rows.firstOrNull { it.first.lowercase() == q }
            ?: rows.firstOrNull { it.first.lowercase().startsWith(q) }
            ?: rows.minByOrNull { it.first.length }
    }
}

/** Locks the screen (same as pressing the power button). Needs the accessibility service, Android 9+. */
class LockPhoneTool : Tool {
    override val name = "lock_phone"
    override val description = "Lock the phone screen right now. args: {}"

    override suspend fun execute(args: JSONObject): ToolResult {
        if (Build.VERSION.SDK_INT < 28) return fail("Locking the screen needs Android 9 or newer.")
        val s = JarvisAccessibilityService.instance
            ?: return fail("Turn on the JARVIS accessibility service first (SETUP screen).")
        return if (s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) ok("Phone locked.")
        else fail("Android refused to lock the screen.")
    }
}

/**
 * Wakes the screen. If the phone has NO secure lock it also swipes the lock screen away.
 * Android never lets an app bypass a PIN / pattern / password / fingerprint / face lock, and JARVIS
 * deliberately does not store or type your PIN, so with a secure lock you unlock it yourself.
 */
class UnlockPhoneTool(private val ctx: Context) : Tool {
    override val name = "unlock_phone"
    override val description =
        "Wake the screen and swipe up. Works only if the phone has no PIN/pattern/password; otherwise it wakes the screen and the user unlocks with fingerprint, face or PIN. args: {}"

    @Suppress("DEPRECATION")
    override suspend fun execute(args: JSONObject): ToolResult {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (!pm.isInteractive) {
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "jarvis:wake"
            )
            wl.acquire(5_000L)
            delay(800)
        }
        if (!km.isKeyguardLocked) return ok("The phone is awake and unlocked.")

        if (km.isDeviceSecure) {
            return ok(
                "The screen is on. Your phone has a PIN/pattern/password or biometric lock, and Android does not let any app " +
                    "bypass it, so please unlock with your fingerprint, face or PIN. " +
                    "Tip: Settings > Security > Smart Lock can keep it unlocked near your Bluetooth device or at home."
            )
        }
        if (!UiDriver.available)
            return ok("The screen is on. Swipe up to unlock (turn on the JARVIS accessibility service and I can swipe for you).")

        val (w, h) = UiDriver.screenSize()
        UiDriver.swipe(w / 2f, h * 0.85f, w / 2f, h * 0.2f, 300)
        delay(900)
        return if (!km.isKeyguardLocked) ok("Phone unlocked.") else fail("I woke the screen but could not swipe the lock screen away.")
    }
}
