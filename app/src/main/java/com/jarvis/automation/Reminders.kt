package com.jarvis.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvis.app.graph
import com.jarvis.security.KillSwitch
import com.jarvis.security.SecureStore
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(val id: String, val text: String, val time: Long)

/** Reminders are stored encrypted so they can be re-armed after a reboot. */
class ReminderStore(private val store: SecureStore) {
    fun all(): List<Reminder> = runCatching {
        val arr = JSONArray(store.str(KEY, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Reminder(o.getString("id"), o.getString("text"), o.getLong("time")) } }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(r: Reminder) = write(all() + r)

    @Synchronized
    fun remove(id: String) = write(all().filterNot { it.id == id })

    private fun write(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time)) }
        store.putStr(KEY, arr.toString())
    }

    private companion object {
        const val KEY = "reminders"
    }
}

object ReminderScheduler {
    private fun pending(ctx: Context, r: Reminder): PendingIntent = PendingIntent.getBroadcast(
        ctx, r.id.hashCode(),
        Intent(ctx, ReminderReceiver::class.java).putExtra("id", r.id).putExtra("text", r.text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Inexact-while-idle alarm: needs no special permission; Android may delay it by a few minutes. */
    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.time, pending(ctx, r))
    }

    fun cancel(ctx: Context, r: Reminder) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, r))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val text = intent.getStringExtra("text").orEmpty()
        Notifier.post(context, id.hashCode(), Notifier.CH_REMINDER, "Reminder", text)
        context.graph.reminders.remove(id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        com.jarvis.app.Power.init(context)
        com.jarvis.app.Power.ensureService(context) // stays ON across reboot only if it was ON before
        val now = System.currentTimeMillis()
        context.graph.reminders.all().forEach { r ->
            if (r.time > now) ReminderScheduler.schedule(context, r)
            else {
                Notifier.post(context, r.id.hashCode(), Notifier.CH_REMINDER, "Missed reminder", r.text)
                context.graph.reminders.remove(r.id)
            }
        }
    }
}

/** Handles the Send / Cancel / STOP buttons on JARVIS notifications. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Notifier.ACTION_YES -> context.graph.confirmations.answer(true)
            Notifier.ACTION_NO -> context.graph.confirmations.answer(false)
            Notifier.ACTION_STOP -> KillSwitch.trigger()
            Notifier.ACTION_SHUTDOWN -> com.jarvis.app.Power.shutdown(context)
        }
    }
}
