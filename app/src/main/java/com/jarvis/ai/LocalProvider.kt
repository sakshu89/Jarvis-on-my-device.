package com.jarvis.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs a model fully on the phone (no internet, no API key) using Google's MediaPipe LLM runtime.
 * Accepts .task / .bin model bundles (Gemma, Qwen, Llama 3.2, Phi, DeepSeek-R1-distill ... from the
 * "litert-community" page on Hugging Face).
 */
class LocalProvider(
    private val ctx: Context,
    private val path: String,
    private val template: String,
    private val maxTokens: Int
) : AIProvider {
    override val name = "On-device (${File(path).name})"

    override suspend fun complete(system: String, messages: List<ChatMsg>, json: Boolean): String {
        if (path.isBlank() || !File(path).exists())
            throw AIException("No on-device model yet. Open Settings -> AI PROVIDER -> ON-DEVICE and import a model file.")

        // Prefill trick: force the answer to start as the JSON object the Agent expects.
        val prefill = if (json) "{\"thought\":\"" else ""
        val prompt = LocalPrompt.build(LocalPrompt.resolve(template, path), system, messages.mergedByRole(), prefill, maxTokens)
        val out = try {
            LocalEngine.generate(ctx, path, maxTokens, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AIException(
                "On-device model failed: ${e.message ?: e.javaClass.simpleName}. " +
                    "The model may be too large for this phone's RAM, or not a valid .task/.bin bundle."
            )
        }
        val text = prefill + out
        if (text.isBlank()) throw AIException("The on-device model returned an empty answer.")
        return text
    }
    // analyzeImage: not supported by text-only models; the default in AIProvider throws a clear error.
}

/** Keeps ONE loaded model in memory (loading takes seconds), one generation at a time. */
object LocalEngine {
    private val lock = Mutex()
    private var llm: LlmInference? = null
    private var loadedPath: String? = null
    private var loadedMax = 0

    suspend fun generate(ctx: Context, path: String, maxTokens: Int, prompt: String): String = lock.withLock {
        withContext(Dispatchers.Default) {
            if (llm == null || loadedPath != path || loadedMax != maxTokens) {
                close()
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(maxTokens) // prompt + answer together
                    .build()
                llm = LlmInference.createFromOptions(ctx.applicationContext, opts)
                loadedPath = path
                loadedMax = maxTokens
            }
            val session = LlmInferenceSession.createFromOptions(
                llm!!,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.3f)
                    .build()
            )
            val finished = AtomicBoolean(false)
            try {
                coroutineScope {
                    // Best-effort STOP: if the coroutine is cancelled (kill switch), ask the engine to abort.
                    val watcher = launch {
                        try { awaitCancellation() } finally {
                            if (!finished.get()) runCatching { session.cancelGenerateResponseAsync() }
                        }
                    }
                    try {
                        session.addQueryChunk(prompt)
                        session.generateResponse()
                    } finally {
                        finished.set(true)
                        watcher.cancel()
                    }
                }
            } finally {
                runCatching { session.close() }
            }
        }
    }

    fun close() {
        runCatching { llm?.close() }
        llm = null
        loadedPath = null
    }
}

/** Chat templates. Wrong template = gibberish, so it is auto-detected from the file name and overridable. */
object LocalPrompt {
    fun resolve(setting: String, path: String): String {
        if (setting != "auto") return setting
        val n = File(path).name.lowercase()
        return when {
            "gemma" in n -> "gemma"
            "llama" in n -> "llama3"
            else -> "chatml" // Qwen, DeepSeek-R1-distill-Qwen, SmolLM ...
        }
    }

    fun build(template: String, system: String, msgs: List<ChatMsg>, prefill: String, maxTokens: Int): String {
        // Rough budget (~3 chars/token): drop the OLDEST turns until the prompt fits, keep room for the answer.
        val budgetChars = (maxTokens - 512).coerceAtLeast(512) * 3
        var turns = msgs
        while (turns.size > 1 && system.length + turns.sumOf { it.content.length } > budgetChars) turns = turns.drop(1)
        if (turns.firstOrNull()?.role == "assistant") turns = turns.drop(1)

        return when (template) {
            "gemma" -> buildString {
                // Gemma has no system role: fold it into the first user turn.
                turns.forEachIndexed { i, m ->
                    val role = if (m.role == "assistant") "model" else "user"
                    val body = if (i == 0) "$system\n\n${m.content}" else m.content
                    append("<start_of_turn>$role\n$body<end_of_turn>\n")
                }
                append("<start_of_turn>model\n").append(prefill)
            }
            "llama3" -> buildString {
                append("<|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>")
                turns.forEach { append("<|start_header_id|>${it.role}<|end_header_id|>\n\n${it.content}<|eot_id|>") }
                append("<|start_header_id|>assistant<|end_header_id|>\n\n").append(prefill)
            }
            else -> buildString { // chatml
                append("<|im_start|>system\n$system<|im_end|>\n")
                turns.forEach { append("<|im_start|>${it.role}\n${it.content}<|im_end|>\n") }
                append("<|im_start|>assistant\n").append(prefill)
            }
        }
    }
}

/** Copies a model file the user picked into the app's private storage (large file: streamed). */
object LocalModels {
    suspend fun import(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        var name = "model.task"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
        }
        name = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!(name.endsWith(".task") || name.endsWith(".bin")))
            throw AIException("Pick a .task or .bin model bundle (a .gguf or .litertlm file will not work with this engine).")
        val dir = File(ctx.filesDir, "models").apply { mkdirs() }
        val dest = File(dir, name)
        val tmp = File(dir, "$name.part")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it, 1 shl 20) }
        } ?: throw AIException("Could not open the selected file.")
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) throw AIException("Could not save the model file.")
        // Free space: remove other previously imported models.
        dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        dest.absolutePath
    }
}
