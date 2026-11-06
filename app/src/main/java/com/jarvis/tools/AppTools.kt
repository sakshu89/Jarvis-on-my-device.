package com.jarvis.tools

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.jarvis.accessibility.UiDriver
import com.jarvis.agent.Tool
import com.jarvis.agent.ToolResult
import com.jarvis.agent.fail
import com.jarvis.agent.ok
import com.jarvis.automation.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class AppInfo(val label: String, val pkg: String)

object AppResolver {
    private val aliases = mapOf(
        "insta" to "instagram", "ig" to "instagram", "इंस्टाग्राम" to "instagram",
        "whatsapp" to "whatsapp", "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp",
        "snap" to "snapchat", "स्नैपचैट" to "snapchat",
        "tg" to "telegram", "टेलीग्राम" to "telegram",
        "yt" to "youtube", "यूट्यूब" to "youtube",
        "क्रोम" to "chrome", "जीमेल" to "gmail", "मैप्स" to "maps", "सेटिंग्स" to "settings"
    )

    fun listApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    fun resolve(ctx: Context, query: String): AppInfo? {
        val q0 = query.trim().lowercase()
        val q = (aliases[q0] ?: q0).replace(" ", "")
        if (q.isEmpty()) return null
        val apps = listApps(ctx)
        fun norm(s: String) = s.lowercase().replace(" ", "")
        return apps.firstOrNull { norm(it.label) == q }
            ?: apps.firstOrNull { norm(it.label).contains(q) }
            ?: apps.firstOrNull { it.pkg.lowercase().contains(q) }
    }
}

class OpenAppTool(private val ctx: Context) : Tool {
    override val name = "open_app"
    override val description = "Open an installed app by name. args: {\"app\":\"Instagram\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val q = args.optString("app").trim()
        if (q.isEmpty()) return fail("open_app needs an app name.")
        val app = AppResolver.resolve(ctx, q) ?: return fail("I couldn't find an installed app matching '$q'.")
        val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return fail("${app.label} has no launchable screen.")
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (!UiDriver.available) return ok("Asked Android to open ${app.label} (I can't confirm it came up without the accessibility service).")
        val up = UiDriver.waitFor(5000) { UiDriver.rawPackage() == app.pkg }
        return if (up) ok("${app.label} is open.") else fail("I requested ${app.label} but it did not reach the foreground (Android may block starting apps from the background; keep JARVIS open and try again).")
    }
}

class DeviceInfoTool(private val ctx: Context) : Tool {
    override val name = "device_info"
    override val description = "Battery, RAM, network, model, Android version and time. args: {}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val mi = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "connected"
        }
        val text = "Battery ${if (level >= 0) level * 100 / scale else "?"}%${if (charging) " (charging)" else ""}; " +
            "RAM free ${mi.availMem / 1_048_576} MB of ${mi.totalMem / 1_048_576} MB; network $net; " +
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}; " +
            "time ${SimpleDateFormat("EEE yyyy-MM-dd HH:mm", Locale.US).format(Date())}"
        return ok(text)
    }
}

class ClipboardTool(private val ctx: Context) : Tool {
    override val name = "clipboard"
    override val description =
        "Read or set the clipboard. args: {\"action\":\"get|set\",\"text\":\"(for set)\"}. Reading only works while JARVIS is in the foreground (Android limit)."

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.Main) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        when (args.optString("action")) {
            "set" -> {
                cm.setPrimaryClip(ClipData.newPlainText("JARVIS", args.optString("text")))
                ok("Copied to clipboard.")
            }
            "get" -> {
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (t.isNullOrEmpty()) fail("Clipboard is empty or Android blocked reading it (app must be in the foreground).")
                else ok("Clipboard read.", t)
            }
            else -> fail("clipboard action must be get or set.")
        }
    }
}

class NotificationTool(private val ctx: Context) : Tool {
    override val name = "notify"
    override val description = "Show a notification to the user. args: {\"title\":\"...\",\"text\":\"...\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val posted = Notifier.post(
            ctx, (System.currentTimeMillis() % 100000).toInt() + 3000, Notifier.CH_GENERAL,
            args.optString("title", "JARVIS"), args.optString("text")
        )
        return if (posted) ok("Notification shown.") else fail("Notifications are not allowed for JARVIS. Enable them in Android settings.")
    }
}

class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Evaluate math. Supports + - * / % ^ ( ) sqrt abs sin cos tan ln log. args: {\"expression\":\"(12+8)*3\"}"

    override suspend fun execute(args: JSONObject): ToolResult = try {
        val v = Calc.eval(args.optString("expression"))
        ok(BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString())
    } catch (e: Exception) {
        fail("Could not evaluate: ${e.message}")
    }
}

object Calc {
    fun eval(src: String): Double {
        val p = P(src.replace("×", "*").replace("÷", "/").replace(",", ""))
        val v = p.expr()
        p.end()
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun expr(): Double {
            var v = term()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else return v
            }
        }

        fun term(): Double {
            var v = unary()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                    val op = s[i++]
                    val r = unary()
                    v = when (op) {
                        '*' -> v * r
                        '/' -> { require(r != 0.0) { "division by zero" }; v / r }
                        else -> v % r
                    }
                } else return v
            }
        }

        fun unary(): Double {
            ws()
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return power()
        }

        fun power(): Double {
            val b = atom()
            ws()
            if (i < s.length && s[i] == '^') { i++; return b.pow(unary()) }
            return b
        }

        fun atom(): Double {
            ws()
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                ws()
                require(i < s.length && s[i] == ')') { "missing )" }
                i++
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) {
                val ns = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(ns, i).lowercase()
                require(name.isNotEmpty()) { "unexpected character at position $i" }
                ws()
                require(i < s.length && s[i] == '(') { "expected ( after $name" }
                i++
                val a = expr()
                ws()
                require(i < s.length && s[i] == ')') { "missing )" }
                i++
                return when (name) {
                    "sqrt" -> sqrt(a)
                    "abs" -> abs(a)
                    "sin" -> sin(a)
                    "cos" -> cos(a)
                    "tan" -> tan(a)
                    "ln" -> ln(a)
                    "log" -> log10(a)
                    else -> throw IllegalArgumentException("unknown function $name")
                }
            }
            return s.substring(st, i).toDouble()
        }

        fun end() {
            ws()
            require(i == s.length) { "unexpected '${s[i]}'" }
        }
    }
}
