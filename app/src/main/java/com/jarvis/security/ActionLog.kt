package com.jarvis.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

data class LogEntry(val time: Long, val tool: String, val detail: String, val status: String)

/** Local history of everything JARVIS attempted. Capped at 200 entries. */
class ActionLog(ctx: Context) {
    private val file = File(ctx.filesDir, "action_log.jsonl")
    val entries = MutableStateFlow<List<LogEntry>>(load())

    private fun load(): List<LogEntry> = runCatching {
        if (!file.exists()) emptyList()
        else file.readLines().mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                LogEntry(o.getLong("t"), o.getString("tool"), o.getString("d"), o.getString("s"))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(tool: String, detail: String, status: String) {
        val list = (entries.value + LogEntry(System.currentTimeMillis(), tool, detail.take(160), status.take(160))).takeLast(MAX)
        entries.value = list
        persist(list)
    }

    @Synchronized
    fun clear() {
        entries.value = emptyList()
        runCatching { file.delete() }
    }

    private fun persist(list: List<LogEntry>) {
        runCatching {
            file.writeText(list.joinToString("\n") {
                JSONObject().put("t", it.time).put("tool", it.tool).put("d", it.detail).put("s", it.status).toString()
            })
        }
    }

    private companion object {
        const val MAX = 200
    }
}
