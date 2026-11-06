package com.jarvis.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import com.jarvis.agent.Tool
import com.jarvis.agent.ToolResult
import com.jarvis.agent.fail
import com.jarvis.agent.ok
import com.jarvis.accessibility.UiDriver
import com.jarvis.ai.Http
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val YT = "com.google.android.youtube"
private const val YT_MUSIC = "com.google.android.apps.youtube.music"
private const val MAPS = "com.google.android.apps.maps"

private fun Context.launch(i: Intent): Boolean = try {
    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: Exception) { false }

/** Works offline. Answers time/date for the phone or for another time zone. */
class TimeDateTool : Tool {
    override val name = "time_date"
    override val description = "Current time and date. args: {} or {\"timezone\":\"Asia/Tokyo\"} for another place"

    override suspend fun execute(args: JSONObject): ToolResult {
        val id = args.optString("timezone").trim()
        val tz = if (id.isEmpty()) TimeZone.getDefault() else TimeZone.getTimeZone(id)
        if (id.isNotEmpty() && tz.id == "GMT" && !id.equals("GMT", true) && !id.equals("UTC", true))
            return fail("I don't know the time zone '$id'. Use a name like Asia/Tokyo or America/New_York.")
        val now = Date()
        val t = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = tz }.format(now)
        val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).apply { timeZone = tz }.format(now)
        return ok("It's $t on $d (${tz.id}).")
    }
}

/** Plays a song/video on YouTube (or YouTube Music). Resolves the first real video; falls back to clicking the first result. */
class PlayMediaTool(private val ctx: Context) : Tool {
    override val name = "play_media"
    override val description =
        "Play a song or video on YouTube or YouTube Music. args: {\"query\":\"song / artist / video name\",\"app\":\"youtube|youtube_music (default youtube)\"}"
    override val timeoutMs = 45_000L

    override suspend fun execute(args: JSONObject): ToolResult {
        val q = args.optString("query").trim()
        if (q.isEmpty()) return fail("play_media needs a song or video name.")
        val music = args.optString("app").lowercase().replace(" ", "_").contains("music")

        if (music) {
            val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(YT_MUSIC)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .putExtra(SearchManager.QUERY, q)
            if (ctx.launch(i)) return ok("Asking YouTube Music to play \"$q\".")
            // not installed -> fall through to YouTube
        }

        val id = findVideoId(q)
        if (id != null) {
            val direct = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).setPackage(YT)
            if (ctx.launch(direct)) return ok("Playing \"$q\" on YouTube.")
            if (ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))))
                return ok("Playing \"$q\" on YouTube (in the browser, the YouTube app is not installed).")
        }

        // Could not resolve a video id: open the search results in the YouTube app, then click the first video.
        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(q)
        val opened = ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(YT)) ||
            ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        if (!opened) return fail("I could not open YouTube.")

        if (UiDriver.available) {
            delay(3500)
            repeat(5) {
                val node = UiDriver.visible().firstOrNull { UiDriver.label(it).lowercase().contains("play video") }
                if (node != null && UiDriver.click(node)) return ok("Playing \"$q\" on YouTube.")
                delay(1500)
            }
        }
        return ok("I opened YouTube search results for \"$q\"; tap the video you want. (Turn on the JARVIS accessibility service so I can tap it for you.)")
    }

    /** First regular video of the YouTube results page. No API key needed. */
    private suspend fun findVideoId(q: String): String? = try {
        val html = Http.request(
            "GET", "https://www.youtube.com/results?search_query=" + Uri.encode(q),
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
                "Accept-Language" to "en-US,en;q=0.9",
                "Cookie" to "CONSENT=YES+1; SOCS=CAI"
            ),
            timeoutMs = 8_000
        )
        Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
            ?: Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
    } catch (_: Exception) { null }
}

/** Turn-by-turn navigation in Google Maps. */
class NavigateTool(private val ctx: Context) : Tool {
    override val name = "navigate_to"
    override val description =
        "Start navigation to a place in Google Maps. args: {\"destination\":\"Connaught Place Delhi\",\"mode\":\"driving|walking|bicycling|two_wheeler (default driving)\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val dest = args.optString("destination").trim()
        if (dest.isEmpty()) return fail("navigate_to needs a destination.")
        val m = when (args.optString("mode").lowercase()) {
            "walking", "walk" -> "w"; "bicycling", "cycling", "bike" -> "b"; "two_wheeler", "motorcycle", "scooter" -> "l"; else -> "d"
        }
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(dest)}&mode=$m")).setPackage(MAPS)
        if (ctx.launch(nav)) return ok("Navigation to $dest started in Google Maps.")
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(dest)}"))
        return if (ctx.launch(geo)) ok("Opened a map for $dest (Google Maps is not installed).") else fail("No maps app found.")
    }
}

class SetTimerTool(private val ctx: Context) : Tool {
    override val name = "set_timer"
    override val description = "Start a countdown timer in the clock app. args: {\"seconds\":300,\"label\":\"tea\"}"

    override suspend fun execute(args: JSONObject): ToolResult {
        val s = args.optInt("seconds", 0)
        if (s !in 1..86_400) return fail("Timer must be between 1 second and 24 hours.")
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "JARVIS timer")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (ctx.launch(i)) ok("Timer set for ${if (s >= 60) "${s / 60} min ${s % 60} sec" else "$s sec"}.") else fail("No clock app handled the timer.")
    }
}

class SetAlarmTool(private val ctx: Context) : Tool {
    override val name = "set_alarm"
    override val description = "Set an alarm in the clock app. args: {\"hour\":7,\"minute\":30,\"label\":\"gym\"} (24-hour clock)"

    override suspend fun execute(args: JSONObject): ToolResult {
        val h = args.optInt("hour", -1); val m = args.optInt("minute", 0)
        if (h !in 0..23 || m !in 0..59) return fail("Alarm needs hour 0-23 and minute 0-59.")
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "JARVIS alarm")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (ctx.launch(i)) ok("Alarm set for %02d:%02d.".format(h, m)) else fail("No clock app handled the alarm.")
    }
}
