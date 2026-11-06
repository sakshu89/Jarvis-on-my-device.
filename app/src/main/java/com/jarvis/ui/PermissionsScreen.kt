package com.jarvis.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvis.security.PermissionStatus

/** Increments every time the app returns to the foreground, so status rows refresh. */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return tick
}

private fun Context.open(i: Intent) = runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

@Composable
private fun PermRow(title: String, ok: Boolean, why: String, fix: String?, actionLabel: String?, onAction: (() -> Unit)?) {
    HudPanel(Modifier.fillMaxWidth(), accent = if (ok) Jx.green else Jx.amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(if (ok) "✓" else "✗", if (ok) Jx.green else Jx.amber, 18, true, Modifier.padding(end = 10.dp))
            Mono(title, Jx.text, 14, true)
        }
        Mono(why, Jx.dim, 12, modifier = Modifier.padding(top = 4.dp))
        if (!ok && fix != null) Mono(fix, Jx.amber, 12, modifier = Modifier.padding(top = 4.dp))
        if (!ok && onAction != null && actionLabel != null) {
            HudButton(actionLabel, onAction, Modifier.padding(top = 8.dp))
        }
    }
}

/** First-run setup and permanent permission diagnostics. Every permission is explained before it is requested. */
@Composable
fun PermissionsScreen(firstRun: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick2 -> tick2.hashCode() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick2 -> tick2.hashCode() }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> r.hashCode() }
    var refresh by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val key = tick + refresh

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(if (firstRun) "FIRST-RUN SETUP" else "PERMISSION DIAGNOSTICS")
        Mono(
            "JARVIS only asks for what it needs. Each item says why. Nothing here lets JARVIS act without a command from you.",
            Jx.dim, 12
        )

        PermRow(
            "Microphone", PermissionStatus.microphone(ctx),
            "Needed to hear your voice commands (push-to-talk: it only listens after you tap the orb).",
            "Tap Allow, or enable it in App info > Permissions.", "ALLOW"
        ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); refresh++ }

        PermRow(
            "Notifications", PermissionStatus.notifications(ctx),
            "Used for reminders, the Send / Cancel confirmation and the STOP button while another app is open.",
            if (Build.VERSION.SDK_INT >= 33) "Tap Allow, or enable it in App info > Notifications." else "Enable notifications in App info.",
            "ALLOW"
        ) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            refresh++
        }

        PermRow(
            "Contacts & Phone",
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
            "Lets JARVIS find a contact by name and place a call when you ask. Every call asks for your approval first.",
            "Tap Allow, or enable Contacts and Phone in App info > Permissions.", "ALLOW"
        ) { phoneLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)); refresh++ }

        PermRow(
            "Accessibility service", PermissionStatus.accessibility(ctx),
            "Lets JARVIS read the screen and tap/type in other apps when you ask. It never operates banking, payment or password apps.",
            "1) Open App info > ⋮ menu > 'Allow restricted settings' (Android 13+, sideloaded apps).  2) Accessibility > Installed apps > JARVIS > turn on.",
            "OPEN ACCESSIBILITY"
        ) { ctx.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        PermRow(
            "App info (restricted settings)", true,
            "If the accessibility switch is greyed out, open App info, tap ⋮ (top right) and choose 'Allow restricted settings'.",
            null, null, null
        )
        Row { HudButton("OPEN APP INFO", { ctx.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) }

        PermRow(
            "Network", PermissionStatus.network(ctx),
            "Needed to reach the AI provider, web lookup and GitHub. No special permission prompt.",
            "Turn on Wi-Fi or mobile data.", null, null
        )

        PermRow(
            "Speech recognition service", PermissionStatus.speechRecognition(ctx),
            "Android's built-in recognizer is used for Hindi / English / Hinglish.",
            "Install or update the Google app / Speech Services, or use the text box.", null, null
        )

        PermRow(
            "Battery (recommended)", true,
            "On phones with 2-3 GB RAM, set JARVIS to 'Unrestricted' battery use so Android does not kill it mid-task and reminders fire on time.",
            null, null, null
        )
        Row { HudButton("BATTERY SETTINGS", { ctx.open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) }

        if (firstRun) HudButton("CONTINUE", onDone, Modifier.fillMaxWidth(), filled = true)
    }
}
