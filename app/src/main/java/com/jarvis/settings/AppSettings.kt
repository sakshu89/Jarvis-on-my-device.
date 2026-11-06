package com.jarvis.settings

import android.app.ActivityManager
import android.content.Context
import com.jarvis.security.SecureStore

/** All user settings, stored encrypted. API keys are entered in the app, never in source. */
class AppSettings(private val s: SecureStore) {
    var provider: String
        get() = s.str("provider", "gemini")
        set(v) = s.putStr("provider", v)

    var geminiKey: String
        get() = s.str("gemini_key")
        set(v) = s.putStr("gemini_key", v.trim())
    var geminiModel: String
        get() = s.str("gemini_model", "gemini-2.5-flash")
        set(v) = s.putStr("gemini_model", v.trim())

    var openAiKey: String
        get() = s.str("openai_key")
        set(v) = s.putStr("openai_key", v.trim())
    var openAiBase: String
        get() = s.str("openai_base", "https://api.groq.com/openai/v1")
        set(v) = s.putStr("openai_base", v.trim())
    var openAiModel: String
        get() = s.str("openai_model", "llama-3.3-70b-versatile")
        set(v) = s.putStr("openai_model", v.trim())

    /** On-device model (provider = "local"). */
    var localModelPath: String
        get() = s.str("local_model")
        set(v) = s.putStr("local_model", v.trim())
    /** auto | gemma | llama3 | chatml */
    var localTemplate: String
        get() = s.str("local_template", "auto")
        set(v) = s.putStr("local_template", v)
    /** Context window (prompt + answer). Bigger = more RAM. */
    var localMaxTokens: Int
        get() = s.str("local_max_tokens", "4096").toIntOrNull()?.coerceIn(1024, 8192) ?: 4096
        set(v) = s.putStr("local_max_tokens", v.toString())

    var githubToken: String
        get() = s.str("github_token")
        set(v) = s.putStr("github_token", v.trim())
    var githubRepo: String
        get() = s.str("github_repo")
        set(v) = s.putStr("github_repo", v.trim())

    /** Speech recognition locale: en-IN (English + Hinglish in Latin script) or hi-IN (Devanagari). */
    var sttLang: String
        get() = s.str("stt_lang", "en-IN")
        set(v) = s.putStr("stt_lang", v)

    var speakReplies: Boolean
        get() = s.bool("speak", true)
        set(v) = s.putBool("speak", v)

    /** auto | lite | balanced | high */
    var perfMode: String
        get() = s.str("perf", "auto")
        set(v) = s.putStr("perf", v)

    /** Persistent power state: true = JARVIS stays on (even after reboot) until shut down. */
    var poweredOn: Boolean
        get() = s.bool("powered_on", false)
        set(v) = s.putBool("powered_on", v)

    /** ElevenLabs cloud voice (optional). The key is entered in the app and stored encrypted. */
    var elevenEnabled: Boolean
        get() = s.bool("eleven_on", true)
        set(v) = s.putBool("eleven_on", v)
    var elevenKey: String
        get() = s.str("eleven_key")
        set(v) = s.putStr("eleven_key", v.trim())
    var elevenVoiceId: String
        get() = s.str("eleven_voice", "CwhRBWXzGAHq8TQ4Fs17")
        set(v) = s.putStr("eleven_voice", v.trim().ifBlank { "CwhRBWXzGAHq8TQ4Fs17" })
    var elevenModel: String
        get() = s.str("eleven_model", "eleven_flash_v2_5")
        set(v) = s.putStr("eleven_model", v.trim().ifBlank { "eleven_flash_v2_5" })

    /** Hands-free: offline "Jarvis" wake word while ON. */
    var handsFree: Boolean
        get() = s.bool("hands_free", false)
        set(v) = s.putBool("hands_free", v)
    var wakeModelPath: String
        get() = s.str("wake_model")
        set(v) = s.putStr("wake_model", v.trim())

    /** plasma | hud */
    var orbStyle: String
        get() = s.str("orb_style", "plasma")
        set(v) = s.putStr("orb_style", v)

    /** Full-screen black landscape core while JARVIS is ON (only STOP is shown). */
    var ambientMode: Boolean
        get() = s.bool("ambient", true)
        set(v) = s.putBool("ambient", v)

    /** Custom cloud voice: any OpenAI-compatible /audio/speech server (OpenAI, Groq, Kokoro, Piper servers...). */
    var customTtsOn: Boolean
        get() = s.bool("ctts_on", false)
        set(v) = s.putBool("ctts_on", v)
    var customTtsBase: String
        get() = s.str("ctts_base")
        set(v) = s.putStr("ctts_base", v.trim())
    var customTtsKey: String
        get() = s.str("ctts_key")
        set(v) = s.putStr("ctts_key", v.trim())
    var customTtsModel: String
        get() = s.str("ctts_model", "tts-1")
        set(v) = s.putStr("ctts_model", v.trim().ifBlank { "tts-1" })
    var customTtsVoice: String
        get() = s.str("ctts_voice", "alloy")
        set(v) = s.putStr("ctts_voice", v.trim().ifBlank { "alloy" })

    /** Phone (Android TTS) voice name, speed and pitch. Blank name = system default. */
    var phoneVoice: String
        get() = s.str("phone_voice")
        set(v) = s.putStr("phone_voice", v.trim())
    var phoneRate: String
        get() = s.str("phone_rate", "1.0")
        set(v) = s.putStr("phone_rate", v)
    var phonePitch: String
        get() = s.str("phone_pitch", "1.0")
        set(v) = s.putStr("phone_pitch", v)

    var firstRunDone: Boolean
        get() = s.bool("first_run_done", false)
        set(v) = s.putBool("first_run_done", v)

    /** True when the selected provider is ready (API key present, or on-device model file imported). */
    fun hasAiKey(): Boolean = when (provider) {
        "openai" -> openAiKey.isNotBlank() || openAiBase.startsWith("http://127.0.0.1") || openAiBase.startsWith("http://localhost")
        "local" -> localModelPath.isNotBlank() && java.io.File(localModelPath).exists()
        else -> geminiKey.isNotBlank()
    }
}

/** Adaptive visual profile so one APK runs on 2 GB phones and flagships. */
data class PerfProfile(
    val name: String,
    val animate: Boolean,
    val minFrameSeconds: Float,
    val glowLayers: Int,
    val bars: Boolean,
    val grid: Boolean
)

object Perf {
    fun resolve(ctx: Context, mode: String): PerfProfile {
        val chosen = if (mode == "auto") {
            val mi = ActivityManager.MemoryInfo()
            ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
            val gb = mi.totalMem / 1_000_000_000.0
            when {
                gb < 3.2 -> "lite"
                gb >= 7.0 -> "high"
                else -> "balanced"
            }
        } else mode
        return when (chosen) {
            "lite" -> PerfProfile("Lite", animate = false, minFrameSeconds = 0.05f, glowLayers = 0, bars = false, grid = false)
            "high" -> PerfProfile("High", animate = true, minFrameSeconds = 0f, glowLayers = 3, bars = true, grid = true)
            else -> PerfProfile("Balanced", animate = true, minFrameSeconds = 1f / 45f, glowLayers = 1, bars = true, grid = false)
        }
    }
}
