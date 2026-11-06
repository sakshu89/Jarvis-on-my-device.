package com.jarvis.agent

import org.json.JSONObject

enum class Risk { LOW, MEDIUM, HIGH }

data class ToolResult(val ok: Boolean, val message: String, val data: String? = null) {
    fun toPrompt(): String = buildString {
        append(if (ok) "OK: " else "FAILED: ")
        append(message)
        if (!data.isNullOrBlank()) {
            append("\nDATA:\n")
            append(data.take(3500))
        }
    }
}

fun ok(message: String, data: String? = null) = ToolResult(true, message, data)
fun fail(message: String) = ToolResult(false, message)

/** Add a capability by implementing this interface and registering it in [buildRegistry]. */
interface Tool {
    val name: String
    /** One line of purpose + the JSON args it accepts. Shown to the model. */
    val description: String
    val risk: Risk get() = Risk.LOW
    /** True only when execute() performs its own explicit user approval flow. */
    val handlesConfirmationInternally: Boolean get() = false
    val requiresAccessibility: Boolean get() = false
    val timeoutMs: Long get() = 30_000L
    suspend fun execute(args: JSONObject): ToolResult
}

class ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()
    fun register(t: Tool) {
        require(t.name.matches(Regex("[a-z][a-z0-9_]{1,63}"))) {
            "Invalid tool name '${t.name}'. Use lowercase snake_case."
        }
        require(t.description.isNotBlank() && t.description.length <= 500) {
            "Tool '${t.name}' must have a concise description (1-500 characters)."
        }
        require(!tools.containsKey(t.name)) { "Duplicate tool registration: '${t.name}'." }
        tools[t.name] = t
    }

    fun get(name: String): Tool? = tools[name]
    fun all(): List<Tool> = tools.values.toList()
    fun describe(): String = all().joinToString("\n") { "- ${it.name}: ${it.description}" }
}
