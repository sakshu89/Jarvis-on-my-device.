package com.jarvis.tools

import android.content.Context
import com.jarvis.agent.Risk
import com.jarvis.agent.Tool
import com.jarvis.agent.ToolResult
import com.jarvis.agent.fail
import com.jarvis.agent.ok
import com.jarvis.app.Graph
import com.jarvis.automation.Reminder
import com.jarvis.automation.ReminderScheduler
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MemoryTool(private val g: Graph) : Tool {
    override val name = "memory"
    override val description =
        "Long-term local memory. args: {\"action\":\"remember|recall|forget|list|update\",\"text\":\"fact (remember/update)\",\"query\":\"keywords (recall/forget)\",\"id\":\"memory id (update/forget)\"}. " +
            "For forget, query \"last\" removes the most recent memory."

    override suspend fun execute(args: JSONObject): ToolResult {
        val m = g.memory
        return when (args.optString("action")) {
            "remember" -> {
                val t = args.optString("text").trim()
                if (t.isEmpty()) fail("Nothing to remember.") else ok("Saved to memory: $t").also { m.add(t) }
            }
            "recall", "list" -> {
                val r = m.search(args.optString("query")).take(15)
                if (r.isEmpty()) ok("I have no matching memories.") else ok("Memories:", r.joinToString("\n") { "[${it.id}] ${it.text}" })
            }
            "update" -> {
                val id = args.optString("id")
                if (m.memories.value.none { it.id == id }) fail("No memory with id '$id'.")
                else ok("Updated.").also { m.update(id, args.optString("text")) }
            }
            "forget" -> {
                val id = args.optString("id")
                val q = args.optString("query")
                val targets = when {
                    id.isNotBlank() -> m.memories.value.filter { it.id == id }
                    q.equals("last", true) -> listOfNotNull(m.memories.value.maxByOrNull { it.ts })
                    else -> m.search(q).take(5).takeIf { q.isNotBlank() }.orEmpty()
                }
                if (targets.isEmpty()) fail("I found no matching memory to forget.")
                else {
                    targets.forEach { m.delete(it.id) }
                    ok("Forgot ${targets.size} memory item(s):", targets.joinToString("\n") { it.text })
                }
            }
            else -> fail("memory action must be remember, recall, list, update or forget.")
        }
    }
}

class ReminderTool(private val ctx: Context, private val g: Graph) : Tool {
    override val name = "reminder"
    override val description =
        "Set or list reminders (notification at the time). args: {\"action\":\"set|list|cancel\",\"text\":\"what\",\"at\":\"yyyy-MM-dd HH:mm local time\" or \"in_minutes\":30,\"id\":\"(cancel)\"}. " +
            "Android may delay it by a few minutes when the phone is dozing."

    override suspend fun execute(args: JSONObject): ToolResult {
        when (args.optString("action", "set")) {
            "list" -> {
                val l = g.reminders.all().sortedBy { it.time }
                if (l.isEmpty()) return ok("No pending reminders.")
                val f = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
                return ok("Pending reminders:", l.joinToString("\n") { "[${it.id}] ${f.format(Date(it.time))} - ${it.text}" })
            }
            "cancel" -> {
                val r = g.reminders.all().firstOrNull { it.id == args.optString("id") } ?: return fail("No reminder with that id.")
                ReminderScheduler.cancel(ctx, r)
                g.reminders.remove(r.id)
                return ok("Cancelled: ${r.text}")
            }
        }
        val text = args.optString("text").trim()
        if (text.isEmpty()) return fail("A reminder needs text.")
        val time: Long = when {
            args.has("in_minutes") -> System.currentTimeMillis() + args.getLong("in_minutes") * 60_000
            args.optString("at").isNotBlank() -> runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(args.optString("at"))!!.time
            }.getOrElse { return fail("Time must look like 2026-11-07 20:00.") }
            else -> return fail("Give either at or in_minutes.")
        }
        if (time <= System.currentTimeMillis()) return fail("That time is already in the past.")
        val r = Reminder(UUID.randomUUID().toString().take(8), text, time)
        g.reminders.add(r)
        ReminderScheduler.schedule(ctx, r)
        val f = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
        return ok("Reminder set for ${f.format(Date(time))}: $text")
    }
}

/** Files are confined to JARVIS's private folder; names cannot contain paths. */
class FileTool(private val ctx: Context, private val g: Graph) : Tool {
    override val name = "file"
    override val description =
        "Read/write small text notes in JARVIS's private folder. args: {\"action\":\"read|write|append|list|delete\",\"name\":\"notes.txt\",\"content\":\"...\"}"
    override val risk = Risk.MEDIUM

    private val dir get() = File(ctx.filesDir, "user_files").also { it.mkdirs() }
    private fun safe(n: String) = n.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "note.txt" }

    override suspend fun execute(args: JSONObject): ToolResult {
        val action = args.optString("action")
        if (action == "list") {
            val l = dir.listFiles()?.map { "${it.name} (${it.length()} bytes)" }.orEmpty()
            return if (l.isEmpty()) ok("No files yet.") else ok("Files:", l.joinToString("\n"))
        }
        val f = File(dir, safe(args.optString("name")))
        return when (action) {
            "read" -> if (f.exists()) ok("Contents of ${f.name}:", f.readText().take(20_000)) else fail("${f.name} does not exist.")
            "write" -> { f.writeText(args.optString("content").take(100_000)); ok("Wrote ${f.name}.") }
            "append" -> { f.appendText(args.optString("content").take(100_000)); ok("Appended to ${f.name}.") }
            "delete" -> {
                if (!f.exists()) return fail("${f.name} does not exist.")
                if (!g.confirmations.ask("Delete the file ${f.name}?")) return fail("The user declined; nothing was deleted.")
                f.delete()
                ok("Deleted ${f.name}.")
            }
            else -> fail("file action must be read, write, append, list or delete.")
        }
    }
}
