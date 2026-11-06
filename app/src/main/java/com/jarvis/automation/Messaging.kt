package com.jarvis.automation

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.accessibility.UiDriver
import com.jarvis.agent.Risk
import com.jarvis.agent.Tool
import com.jarvis.agent.ToolResult
import com.jarvis.agent.fail
import com.jarvis.agent.ok
import com.jarvis.app.Graph
import com.jarvis.app.JarvisApp
import com.jarvis.security.KillSwitch
import com.jarvis.tools.AppResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/** One adapter per messaging app. Flow: openChat -> typeMessage -> (user confirms) -> send. */
interface MessagingAdapter {
    val displayName: String
    val aliases: List<String>
    val packages: List<String>
    suspend fun openChat(contact: String): ToolResult
    suspend fun typeMessage(message: String): ToolResult
    suspend fun clearInput()
    suspend fun send(): ToolResult
}

/**
 * Generic accessibility-based adapter. The per-app subclasses only provide hints (labels / ids).
 * Third-party UIs change often and some apps block automation: every step verifies itself and
 * reports a clear failure instead of pretending to succeed.
 */
open class GenericAccessibilityMessagingAdapter(
    override val displayName: String,
    override val aliases: List<String>,
    override val packages: List<String>,
    private val entryHints: List<String> = emptyList(),
    private val searchHints: List<String> = listOf("search"),
    private val inputHints: List<String> = listOf("message"),
    private val sendHints: List<String> = listOf("send")
) : MessagingAdapter {

    private val ctx: Context get() = JarvisApp.instance

    override suspend fun openChat(contact: String): ToolResult {
        val pm = ctx.packageManager
        val pkg = packages.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
            ?: return fail("$displayName is not installed.")
        val intent = pm.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        if (!UiDriver.waitFor(8000) { UiDriver.rawPackage() == pkg }) return fail("$displayName did not come to the foreground.")
        UiDriver.blockedReason()?.let { return fail(it) }
        delay(1200)

        for (h in entryHints) {
            KillSwitch.check()
            val n = UiDriver.findByText(h).firstOrNull() ?: continue
            UiDriver.click(n)
            delay(900)
            break
        }

        val search = searchHints.firstNotNullOfOrNull { UiDriver.findByText(it).firstOrNull { n -> !n.isEditable } }
        if (search != null) {
            UiDriver.click(search)
            delay(800)
        }
        val field = UiDriver.searchEditable()
        if (field != null) {
            if (!UiDriver.setText(field, contact)) return fail("Could not type into the search box of $displayName.")
            delay(1400)
        } else if (search == null) {
            return fail("Could not find a search box in $displayName (its screen may differ from what I expect).")
        }

        KillSwitch.check()
        val hit = UiDriver.findByText(contact).firstOrNull { !it.isEditable }
            ?: return fail("Could not find '$contact' in $displayName's results. Check the exact name as saved in the app.")
        UiDriver.click(hit)
        delay(1500)

        if (!UiDriver.waitFor(4000) { UiDriver.bottomEditable(inputHints) != null }) {
            return fail("Clicked '$contact' but no chat input appeared (the app may have opened a profile instead).")
        }
        return ok("Opened chat with $contact in $displayName.")
    }

    override suspend fun typeMessage(message: String): ToolResult {
        val input = UiDriver.bottomEditable(inputHints) ?: return fail("No message box found on screen.")
        if (!UiDriver.setText(input, message)) return fail("The app refused text input in the message box.")
        delay(400)
        input.refresh()
        val now = input.text?.toString().orEmpty().replace("\r", "")
        if (now.isBlank()) return fail("The message did not appear in the box (the app may block automated typing).")
        return ok("Message typed (not sent yet).")
    }

    override suspend fun clearInput() {
        UiDriver.bottomEditable(inputHints)?.let { UiDriver.setText(it, "") }
    }

    override suspend fun send(): ToolResult {
        val before = UiDriver.bottomEditable(inputHints)
        val btn = sendHints.firstNotNullOfOrNull { h ->
            UiDriver.findByText(h, exact = true).firstOrNull() ?: UiDriver.findByText(h).firstOrNull { !it.isEditable }
        }
        var tapped = false
        if (btn != null) {
            tapped = UiDriver.click(btn)
        } else if (before != null && android.os.Build.VERSION.SDK_INT >= 30) {
            tapped = before.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        if (!tapped) return fail("Could not find a Send button or trigger send.")
        delay(900)
        val after = UiDriver.bottomEditable(inputHints)
        val cleared = after == null || after.apply { refresh() }.text.isNullOrBlank() ||
            after.text.toString() == (after.hintText?.toString() ?: "")
        return if (cleared) ok("Message sent (the input box was cleared).")
        else ok("I tapped Send, but could not verify delivery. Please check the chat.")
    }
}

class WhatsAppAdapter : GenericAccessibilityMessagingAdapter(
    "WhatsApp", listOf("whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप"),
    listOf("com.whatsapp", "com.whatsapp.w4b"),
    inputHints = listOf("entry", "message")
)

class TelegramAdapter : GenericAccessibilityMessagingAdapter(
    "Telegram", listOf("telegram", "टेलीग्राम"),
    listOf("org.telegram.messenger", "org.telegram.messenger.web")
)

class InstagramAdapter : GenericAccessibilityMessagingAdapter(
    "Instagram", listOf("instagram", "insta", "इंस्टाग्राम"),
    listOf("com.instagram.android"),
    entryHints = listOf("messenger", "direct", "messages")
)

class SnapchatAdapter : GenericAccessibilityMessagingAdapter(
    "Snapchat", listOf("snapchat", "snap", "स्नैपचैट"),
    listOf("com.snapchat.android"),
    entryHints = listOf("chat"),
    inputHints = listOf("send a chat", "chat")
)

object MessagingRegistry {
    private val adapters: List<MessagingAdapter> = listOf(WhatsAppAdapter(), TelegramAdapter(), InstagramAdapter(), SnapchatAdapter())

    fun forApp(name: String): MessagingAdapter? {
        val n = name.lowercase().trim()
        return adapters.firstOrNull { a ->
            a.displayName.lowercase() == n || a.aliases.any { it == n || (it.length > 3 && n.contains(it)) }
        } ?: AppResolver.resolve(JarvisApp.instance, name)?.let { app ->
            GenericAccessibilityMessagingAdapter(app.label, listOf(n), listOf(app.pkg))
        }
    }
}

class SendMessageTool(private val g: Graph) : Tool {
    override val name = "send_message"
    override val description =
        "Open a messaging app, find a contact/chat, type the message, ask the user to confirm, then send. " +
            "args: {\"app\":\"WhatsApp|Telegram|Instagram|Snapchat|any installed chat app\",\"contact\":\"name as saved in the app\",\"message\":\"exact final text incl. emojis\"}"
    override val risk = Risk.HIGH
    // The messaging adapter already previews the exact message and asks before sending.
    override val handlesConfirmationInternally = true
    override val requiresAccessibility = true
    override val timeoutMs = 240_000L

    override suspend fun execute(args: JSONObject): ToolResult {
        val app = args.optString("app").trim()
        val contact = args.optString("contact").trim()
        val message = args.optString("message")
        if (app.isEmpty() || contact.isEmpty() || message.isBlank()) return fail("send_message needs app, contact and message.")
        val adapter = MessagingRegistry.forApp(app) ?: return fail("I don't know an installed app called '$app'.")

        adapter.openChat(contact).let { if (!it.ok) return it }
        adapter.typeMessage(message).let { if (!it.ok) return it }

        KillSwitch.check()
        val approved = try {
            g.confirmations.ask("Message ready for $contact on ${adapter.displayName}:\n\"$message\"\n\nSend it?")
        } catch (e: CancellationException) {
            // Emergency stop while waiting: leave the chat clean, never send.
            withContext(NonCancellable) { adapter.clearInput() }
            throw e
        }
        if (!approved) {
            adapter.clearInput()
            return fail("The user declined, so the message was NOT sent.")
        }
        KillSwitch.check()
        return adapter.send()
    }
}
