package com.jarvis.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.jarvis.ai.AIException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** True while JARVIS itself is using the mic/speaker (listening, thinking, speaking). The wake-word listener pauses then. */
object WakeWord {
    val busy = MutableStateFlow(false)
}

/**
 * Offline wake word using Vosk, restricted to a tiny grammar ("jarvis", "hey jarvis" ...), so it is light
 * and nothing leaves the phone. All Vosk calls run on ONE worker thread (stop() must never be called from
 * Vosk's own callback thread).
 */
class WakeWordEngine(private val modelPath: String, private val onWake: () -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var rec: Recognizer? = null
    private var svc: SpeechService? = null
    private var running = false      // worker-confined
    private var wanted = false
    @Volatile private var lastFire = 0L

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = check(hypothesis)
        override fun onResult(hypothesis: String?) = check(hypothesis)
        override fun onFinalResult(hypothesis: String?) = check(hypothesis)
        override fun onError(exception: Exception?) {}
        override fun onTimeout() {}
    }

    private fun check(json: String?) {
        if (json.isNullOrBlank()) return
        val text = try {
            JSONObject(json).let { it.optString("partial") + " " + it.optString("text") }
        } catch (_: Exception) { return }
        if (!text.contains("jarvis", ignoreCase = true)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFire < 2500) return
        lastFire = now
        // Release the mic BEFORE the command recogniser starts; stop() from the worker, not from this callback thread.
        worker.execute {
            stopInternal()
            main.post(onWake)
        }
    }

    private fun ensureLoaded(): Boolean {
        if (rec != null) return true
        return try {
            val m = Model(modelPath)
            model = m
            rec = Recognizer(m, 16000f, "[\"jarvis\", \"hey jarvis\", \"hi jarvis\", \"okay jarvis\", \"[unk]\"]")
            true
        } catch (_: Throwable) { false }
    }

    private fun startInternal() {
        if (running || !ensureLoaded()) return
        try {
            if (svc == null) svc = SpeechService(rec, 16000f)
            running = svc!!.startListening(listener)
        } catch (_: Throwable) { running = false }
    }

    private fun stopInternal() {
        if (!running) return
        runCatching { svc?.stop() }
        running = false
    }

    fun resume() { wanted = true; worker.execute { startInternal() } }
    fun pause() { wanted = false; worker.execute { stopInternal() } }

    fun release() {
        worker.execute {
            stopInternal()
            runCatching { svc?.shutdown() }; svc = null
            runCatching { rec?.close() }; rec = null
            runCatching { model?.close() }; model = null
        }
        worker.shutdown()
    }
}

/** Imports a Vosk model .zip (e.g. vosk-model-small-en-us-0.15.zip) into private storage and returns the model folder. */
object WakeModels {
    suspend fun import(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val root = File(ctx.filesDir, "vosk").apply { deleteRecursively(); mkdirs() }
        val rootPath = root.canonicalPath + File.separator
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val out = File(root, e.name)
                    if (!out.canonicalPath.startsWith(rootPath)) throw AIException("Unsafe zip file.")
                    if (e.isDirectory) out.mkdirs()
                    else { out.parentFile?.mkdirs(); out.outputStream().use { zis.copyTo(it, 1 shl 16) } }
                    e = zis.nextEntry
                }
            }
        } ?: throw AIException("Could not open the selected file.")
        root.walkTopDown().firstOrNull { it.isDirectory && File(it, "am").isDirectory && File(it, "conf").isDirectory }
            ?.absolutePath ?: throw AIException("This zip is not a Vosk model (no am/ and conf/ folders inside).")
    }
}
