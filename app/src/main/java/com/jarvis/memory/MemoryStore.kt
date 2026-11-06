package com.jarvis.memory

import com.jarvis.ai.ChatMsg
import com.jarvis.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Memory(val id: String, val text: String, val ts: Long)

/** LONG-TERM memory: encrypted, local, editable and deletable. */
class MemoryStore(private val store: SecureStore) {
    val memories = MutableStateFlow(load())

    private fun load(): List<Memory> = runCatching {
        val arr = JSONArray(store.str(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Memory(o.getString("id"), o.getString("text"), o.getLong("ts"))
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Memory>) {
        memories.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("ts", it.ts)) }
        store.putStr(KEY, arr.toString())
    }

    @Synchronized
    fun add(text: String): Memory {
        val m = Memory(UUID.randomUUID().toString().take(8), text.trim(), System.currentTimeMillis())
        save(memories.value + m)
        return m
    }

    @Synchronized
    fun update(id: String, text: String) {
        save(memories.value.map { if (it.id == id) it.copy(text = text.trim()) else it })
    }

    @Synchronized
    fun delete(id: String) {
        save(memories.value.filterNot { it.id == id })
    }

    @Synchronized
    fun clearAll() = save(emptyList())

    /** Keyword search; blank query returns everything (newest first). */
    fun search(query: String): List<Memory> {
        val words = tokens(query)
        val all = memories.value.sortedByDescending { it.ts }
        if (words.isEmpty()) return all
        return all.filter { m ->
            val t = m.text.lowercase()
            words.any { t.contains(it) }
        }
    }

    private fun tokens(q: String) = q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in STOP }

    private companion object {
        const val KEY = "long_term_memories"
        val STOP = setOf("the", "that", "this", "what", "who", "mera", "meri", "mere", "about", "kya", "hai", "remember", "forget", "yaad", "bhool", "and", "for", "you", "your")
    }
}

/** SHORT-TERM conversation context (RAM only, cleared when the app process ends). */
class ConversationContext {
    private val items = ArrayList<ChatMsg>()

    @Synchronized
    fun add(m: ChatMsg) {
        items.add(m)
        while (items.size > 30) items.removeAt(0)
    }

    /** Last [n] messages, always starting with a user message. */
    @Synchronized
    fun recent(n: Int): List<ChatMsg> = items.takeLast(n).dropWhile { it.role != "user" }

    @Synchronized
    fun clear() = items.clear()
}

/** TEMPORARY task state (RAM only). */
class TaskState {
    val values = ConcurrentHashMap<String, String>()
    fun clear() = values.clear()
}
