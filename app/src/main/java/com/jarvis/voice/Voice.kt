package com.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.jarvis.app.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Speech-to-text. Must be used from the main thread. Locale en-IN covers English + Hinglish; hi-IN gives Devanagari. */
class VoiceInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    val level = MutableStateFlow(0f)
    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(lang: String, onPartialText: (String) -> Unit, onFinalText: (String) -> Unit, onFail: (String) -> Unit) {
        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                level.value = 0f
            }

            override fun onError(error: Int) {
                level.value = 0f
                release(r)
                onFail(explain(error))
            }

            override fun onResults(results: Bundle?) {
                level.value = 0f
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release(r)
                onFinalText(t)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartialText)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        r.startListening(i)
    }

    /** Finish listening and deliver what was heard so far. */
    fun stop() {
        rec?.stopListening()
    }

    fun cancel() {
        rec?.let { runCatching { it.cancel(); it.destroy() } }
        rec = null
        level.value = 0f
    }

    private fun release(r: SpeechRecognizer) {
        Handler(Looper.getMainLooper()).post {
            runCatching { r.destroy() }
            if (rec === r) rec = null
        }
    }

    private fun explain(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the orb and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs internet (or an offline language pack for this language)."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy. Try again in a moment."
        SpeechRecognizer.ERROR_CLIENT -> ""
        else -> "Speech recognition error ($code)."
    }
}

/** Text-to-speech with Hindi / English (India) voices when the phone has them installed. */
class VoiceOutput(ctx: Context) : TextToSpeech.OnInitListener {
    private val appCtx = ctx.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appCtx, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var elevenJob: Job? = null
    private var player: MediaPlayer? = null
    /** One-line problem report (e.g. bad ElevenLabs key); the UI shows it as a banner. */
    val notice = MutableStateFlow<String?>(null)

    @Volatile
    private var ready = false
    val speaking = MutableStateFlow(false)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                speaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                speaking.value = false
            }
        })
    }

    fun speak(text: String, lang: String) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]"), "").trim()
        if (clean.isEmpty()) { speaking.value = false; return }
        val st = appCtx.graph.settings
        if (st.elevenEnabled && st.elevenKey.isNotBlank()) {
            stop()
            speaking.value = true
            val key = st.elevenKey; val voice = st.elevenVoiceId; val model = st.elevenModel
            elevenJob = scope.launch {
                try {
                    val f = fetchEleven(clean.take(1500), key, voice, model)
                    play(f)
                    speaking.value = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notice.value = "ElevenLabs voice failed (${e.message}). Using the phone voice instead."
                    speakLocal(clean, lang)
                }
            }
            return
        }
        speakLocal(clean, lang)
    }

    private suspend fun fetchEleven(text: String, key: String, voiceId: String, model: String): File = withContext(Dispatchers.IO) {
        val url = URL("https://api.elevenlabs.io/v1/text-to-speech/${Uri.encode(voiceId)}?output_format=mp3_44100_64")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.setRequestProperty("xi-api-key", key)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "audio/mpeg")
            c.doOutput = true
            val body = JSONObject().put("text", text).put("model_id", model)
                .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.75))
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                throw IOException(when (code) {
                    401 -> "the API key was rejected"
                    402, 429 -> "quota or rate limit reached"
                    404 -> "voice ID not found"
                    else -> "HTTP $code"
                })
            }
            val f = File.createTempFile("tts", ".mp3", appCtx.cacheDir)
            c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } }
            f
        } finally {
            c.disconnect()
        }
    }

    private suspend fun play(f: File) = suspendCancellableCoroutine<Unit> { cont ->
        val mp = MediaPlayer()
        val done = AtomicBoolean(false)
        fun finish() {
            if (!done.compareAndSet(false, true)) return
            runCatching { mp.release() }
            if (player === mp) player = null
            f.delete()
        }
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(f.absolutePath)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finish(); if (cont.isActive) cont.resume(Unit) }
            mp.setOnErrorListener { _, _, _ -> finish(); if (cont.isActive) cont.resumeWithException(IOException("audio playback failed")); true }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            finish()
            cont.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { mp.stop() }; finish() }
    }

    private fun speakLocal(clean: String, lang: String) {
        val t = tts
        if (!ready || t == null) {
            speaking.value = false
            return
        }
        val loc = if (lang == "hi") Locale("hi", "IN") else Locale("en", "IN")
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.US)
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.nanoTime()}")
    }

    fun stop() {
        elevenJob?.cancel(); elevenJob = null
        tts?.stop()
        speaking.value = false
    }

    fun shutdown() {
        elevenJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/** Replaceable wake-word seam. Only push-to-talk ships: Android restricts background microphone use. */
interface WakeWordDetector {
    val name: String
    fun start(onWake: () -> Unit)
    fun stop()
}

class PushToTalkOnly : WakeWordDetector {
    override val name = "Push-to-talk (tap the orb)"
    override fun start(onWake: () -> Unit) {}
    override fun stop() {}
}
