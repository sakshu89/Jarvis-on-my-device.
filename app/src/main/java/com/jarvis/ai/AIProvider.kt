package com.jarvis.ai

import com.jarvis.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMsg(val role: String, val content: String) // role: "user" | "assistant"

class AIException(message: String) : Exception(message)

/** Swap models/vendors by implementing this interface. */
interface AIProvider {
    val name: String
    suspend fun complete(system: String, messages: List<ChatMsg>, json: Boolean): String

    /** Analyze a user-approved image. Providers without vision support should fail explicitly. */
    suspend fun analyzeImage(system: String, prompt: String, imageBytes: ByteArray, mimeType: String): String =
        throw AIException("The configured AI provider does not implement image input.")
}

object ProviderFactory {
    fun create(s: AppSettings): AIProvider = when (s.provider) {
        "openai" -> OpenAICompatProvider(s.openAiBase, s.openAiKey, s.openAiModel)
        "local" -> LocalProvider(com.jarvis.app.JarvisApp.instance, s.localModelPath, s.localTemplate, s.localMaxTokens)
        else -> GeminiProvider(s.geminiKey, s.geminiModel)
    }
}

/** A llama.cpp / Ollama server running on this same phone (Termux). Only this address may use plain http. */
internal fun isLocalUrl(u: String) = u.startsWith("http://127.0.0.1") || u.startsWith("http://localhost")

/** Merges consecutive same-role messages (some APIs require strict alternation). */
internal fun List<ChatMsg>.mergedByRole(): List<ChatMsg> {
    val out = ArrayList<ChatMsg>()
    for (m in this) {
        val last = out.lastOrNull()
        if (last != null && last.role == m.role) out[out.size - 1] = last.copy(content = last.content + "\n\n" + m.content)
        else out.add(m)
    }
    return out
}

class HttpException(val code: Int, body: String) : Exception("HTTP $code: $body")

object Http {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 180_000
    ): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, text.take(300))
            text
        } finally {
            c.disconnect()
        }
    }
}

class GeminiProvider(private val key: String, private val model: String) : AIProvider {
    override val name = "Gemini ($model)"

    override suspend fun complete(system: String, messages: List<ChatMsg>, json: Boolean): String {
        if (key.isBlank()) throw AIException("No Gemini API key yet. Open Settings and paste one.")
        val contents = JSONArray()
        messages.mergedByRole().forEach { m ->
            contents.put(
                JSONObject()
                    .put("role", if (m.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.content)))
            )
        }
        val gen = JSONObject().put("temperature", 0.3)
        if (json) gen.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", gen)
        val res = try {
            Http.request(
                "POST",
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
                body.toString()
            )
        } catch (e: HttpException) {
            throw AIException(explain(e.code, e.message.orEmpty()))
        }
        val parts = JSONObject(res).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            for (i in 0 until (parts?.length() ?: 0)) append(parts!!.getJSONObject(i).optString("text"))
        }
        if (text.isBlank()) throw AIException("Gemini returned an empty answer (it may have been blocked by a safety filter).")
        return text
    }

    override suspend fun analyzeImage(system: String, prompt: String, imageBytes: ByteArray, mimeType: String): String {
        if (key.isBlank()) throw AIException("No Gemini API key yet. Open Settings and paste one.")
        if (imageBytes.isEmpty() || imageBytes.size > 8 * 1024 * 1024) throw AIException("Image is empty or larger than 8 MB.")
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", mimeType)
                .put("data", android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP))))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
        val res = try {
            Http.request("POST", "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                mapOf("Content-Type" to "application/json", "x-goog-api-key" to key), body.toString())
        } catch (e: HttpException) {
            throw AIException(explain(e.code, e.message.orEmpty()))
        }
        val outParts = JSONObject(res).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val answer = buildString { for (i in 0 until (outParts?.length() ?: 0)) append(outParts!!.getJSONObject(i).optString("text")) }
        if (answer.isBlank()) throw AIException("Gemini returned no image analysis.")
        return answer
    }

    private fun explain(code: Int, msg: String) = when (code) {
        400, 403 -> "Gemini rejected the request ($code). Check the API key and model name in Settings. $msg"
        404 -> "Gemini model '$model' was not found. Change the model name in Settings."
        429 -> "Gemini rate limit or quota reached. Wait a bit or check your plan."
        else -> "Gemini error: $msg"
    }
}

/** Works with any OpenAI-compatible endpoint (Groq, OpenRouter, a local server, ...). */
class OpenAICompatProvider(private val baseUrl: String, private val key: String, private val model: String) : AIProvider {
    override val name = "OpenAI-compatible ($model)"

    override suspend fun complete(system: String, messages: List<ChatMsg>, json: Boolean): String {
        if (key.isBlank() && !isLocalUrl(baseUrl)) throw AIException("No API key yet. Open Settings and paste one.")
        if (!baseUrl.startsWith("https://") && !isLocalUrl(baseUrl)) throw AIException("Base URL must start with https:// (plain http:// is only allowed for a server on this phone, e.g. http://127.0.0.1:8080/v1).")
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        messages.mergedByRole().forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.3).put("max_tokens", 300)
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))
        val res = try {
            Http.request(
                "POST",
                baseUrl.trimEnd('/') + "/chat/completions",
                mapOf("Content-Type" to "application/json", "Authorization" to "Bearer ${key.ifBlank { "local" }}"),
                body.toString()
            )
        } catch (e: HttpException) {
            throw AIException(
                when (e.code) {
                    401, 403 -> "The API key was rejected. Check it in Settings."
                    404 -> "Endpoint or model not found. Check base URL and model name."
                    429 -> "Rate limit or quota reached."
                    else -> "AI provider error: ${e.message}"
                }
            )
        }
        val text = JSONObject(res).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        if (text.isBlank()) throw AIException("The AI provider returned an empty answer.")
        return text
    }

    override suspend fun analyzeImage(system: String, prompt: String, imageBytes: ByteArray, mimeType: String): String {
        if (key.isBlank() && !isLocalUrl(baseUrl)) throw AIException("No API key yet. Open Settings and paste one.")
        if (!baseUrl.startsWith("https://") && !isLocalUrl(baseUrl)) throw AIException("Base URL must start with https:// (plain http:// is only allowed for a server on this phone, e.g. http://127.0.0.1:8080/v1).")
        if (imageBytes.isEmpty() || imageBytes.size > 8 * 1024 * 1024) throw AIException("Image is empty or larger than 8 MB.")
        val dataUrl = "data:$mimeType;base64," + android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl).put("detail", "high")))
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", content))
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.2)
        val res = try {
            Http.request("POST", baseUrl.trimEnd('/') + "/chat/completions",
                mapOf("Content-Type" to "application/json", "Authorization" to "Bearer ${key.ifBlank { "local" }}"), body.toString())
        } catch (e: HttpException) {
            throw AIException(when (e.code) {
                401, 403 -> "The API key was rejected. Check it in Settings."
                404 -> "Endpoint or model not found, or the model may not support vision."
                429 -> "Rate limit or quota reached."
                else -> "AI provider error: ${e.message}"
            })
        }
        val answer = JSONObject(res).optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
        if (answer.isBlank()) throw AIException("The AI provider returned no image analysis.")
        return answer
    }
}
