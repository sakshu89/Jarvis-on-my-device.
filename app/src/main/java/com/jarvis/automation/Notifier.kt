package com.jarvis.automation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvis.R
import com.jarvis.app.MainActivity

object Notifier {
    const val CH_GENERAL = "jarvis_general"
    const val CH_CONFIRM = "jarvis_confirm"
    const val CH_WORK = "jarvis_work"
    const val CH_REMINDER = "jarvis_reminders"
    const val NID_CONFIRM = 2001
    const val NID_WORK = 2002
    const val NID_POWER = 2003
    const val CH_POWER = "jarvis_power"
    const val ACTION_SHUTDOWN = "com.jarvis.action.SHUTDOWN"

    const val ACTION_YES = "com.jarvis.action.YES"
    const val ACTION_NO = "com.jarvis.action.NO"
    const val ACTION_STOP = "com.jarvis.action.STOP"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_GENERAL, "JARVIS", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_CONFIRM, "Confirmations", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_WORK, "Task running", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_POWER, "JARVIS online", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "Reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, action.hashCode(),
        Intent(ctx, ActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, id: Int, channel: String, title: String, text: String): Boolean {
        if (!canPost(ctx)) return false
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
        return true
    }

    @SuppressLint("MissingPermission")
    fun showConfirm(ctx: Context, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_CONFIRM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("JARVIS needs your OK")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Approve", action(ctx, ACTION_YES))
            .addAction(0, "Cancel", action(ctx, ACTION_NO))
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NID_CONFIRM, n)
    }

    /** Ongoing "working" notification with a STOP button, usable while another app is on screen. */
    @SuppressLint("MissingPermission")
    fun showWorking(ctx: Context, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_WORK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("JARVIS is working")
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .addAction(0, "STOP", action(ctx, ACTION_STOP))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NID_WORK, n)
    }

    /** Persistent "JARVIS is ON" notification (also the foreground-service notification). */
    fun powerNotification(ctx: Context, text: String = "Say \"shutdown\" or tap SHUT DOWN to turn it fully off.") = NotificationCompat.Builder(ctx, CH_POWER)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("JARVIS is ON")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openApp(ctx))
        .addAction(0, "SHUT DOWN", action(ctx, ACTION_SHUTDOWN))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    fun clear(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
    fun clearAll(ctx: Context) {
        clear(ctx, NID_CONFIRM)
        clear(ctx, NID_WORK)
    }
}
