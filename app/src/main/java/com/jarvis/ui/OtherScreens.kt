package com.jarvis.ui

import androidx.compose.runtime.DisposableEffect
import android.speech.tts.TextToSpeech
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.jarvis.ai.LocalModels
import com.jarvis.ai.LocalEngine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.graph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val g = LocalContext.current.graph
    val entries by g.actionLog.entries.collectAsState()
    val f = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("ACTION HISTORY")
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                HudButton("CLEAR", { g.actionLog.clear() }, color = Jx.red)
            }
        }
        Mono("Everything JARVIS attempted, newest first. Stored only on this phone.", Jx.dim, 11, modifier = Modifier.padding(vertical = 6.dp))
        if (entries.isEmpty()) Mono("Nothing yet.", Jx.dim, 13)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries.reversed()) { e ->
                val col = when {
                    e.status.startsWith("OK") -> Jx.green
                    e.status.startsWith("FAILED") || e.status.startsWith("BLOCKED") || e.status.startsWith("CANCELLED") -> Jx.amber
                    else -> Jx.red
                }
                HudPanel(Modifier.fillMaxWidth(), accent = col) {
                    Mono("${f.format(Date(e.time))}  ·  ${e.tool}", Jx.dim, 11)
                    Mono(e.status, col, 12, true)
                    if (e.detail.isNotBlank()) Mono(e.detail, Jx.text, 11)
                }
            }
        }
    }
}

@Composable
fun MemoryScreen() {
    val g = LocalContext.current.graph
    val mems by g.memory.memories.collectAsState()
    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("LONG-TERM MEMORY")
        Mono("Stored encrypted on this phone. You can also say: \"Remember that…\", \"Forget that\", \"What do you remember about…\".", Jx.dim, 11)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newText, { newText = it }, Modifier.weight(1f),
                placeholder = { Mono("Add a memory…", Jx.dim, 13) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp), colors = hudFieldColors()
            )
            HudButton("ADD", { if (newText.isNotBlank()) { g.memory.add(newText); newText = "" } }, Modifier.padding(start = 6.dp), filled = true)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(mems.sortedByDescending { it.ts }) { m ->
                HudPanel(Modifier.fillMaxWidth()) {
                    if (editing == m.id) {
                        OutlinedTextField(
                            editText, { editText = it }, Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp), colors = hudFieldColors()
                        )
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("SAVE", { g.memory.update(m.id, editText); editing = null }, filled = true)
                            HudButton("CANCEL", { editing = null }, color = Jx.dim)
                        }
                    } else {
                        Mono(m.text, Jx.text, 13)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton("EDIT", { editing = m.id; editText = m.text })
                            HudButton("DELETE", { g.memory.delete(m.id) }, color = Jx.red)
                        }
                    }
                }
            }
        }
        if (mems.isNotEmpty()) HudButton("CLEAR ALL MEMORY", { confirmClear = true }, Modifier.fillMaxWidth(), color = Jx.red)
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all memories?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton({ g.memory.clearAll(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = hudFieldColors()
    )
}

@Composable
private fun PhoneVoicePicker(current: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val ttsRef = remember { arrayOfNulls<TextToSpeech>(1) }
    var label by remember { mutableStateOf(current) }
    DisposableEffect(Unit) {
        ttsRef[0] = TextToSpeech(ctx.applicationContext) { }
        onDispose { ttsRef[0]?.shutdown(); ttsRef[0] = null }
    }
    Mono("Phone voice: " + label.ifBlank { "system default" }, Jx.text, 12)
    HudButton("TRY NEXT PHONE VOICE", {
        val t = ttsRef[0]
        val list = (t?.voices ?: emptySet()).filter { it.locale.language == "en" || it.locale.language == "hi" }.sortedBy { it.name }
        if (t == null || list.isEmpty()) {
            label = "none found (install a voice engine below)"
        } else {
            val idx = list.indexOfFirst { it.name == label }
            val v = list[(idx + 1).mod(list.size)]
            t.voice = v
            label = v.name
            onPick(v.name)
            t.speak("Hello, I am JARVIS. How can I help you?", TextToSpeech.QUEUE_FLUSH, null, "try-voice")
        }
    }, Modifier.fillMaxWidth())
}

@Composable
fun SettingsScreen(onSaved: () -> Unit) {
    val g = LocalContext.current.graph
    val s = g.settings
    var provider by remember { mutableStateOf(s.provider) }
    var gKey by remember { mutableStateOf(s.geminiKey) }
    var gModel by remember { mutableStateOf(s.geminiModel) }
    var oKey by remember { mutableStateOf(s.openAiKey) }
    var oBase by remember { mutableStateOf(s.openAiBase) }
    var oModel by remember { mutableStateOf(s.openAiModel) }
    var ghToken by remember { mutableStateOf(s.githubToken) }
    var ghRepo by remember { mutableStateOf(s.githubRepo) }
    var stt by remember { mutableStateOf(s.sttLang) }
    var speak by remember { mutableStateOf(s.speakReplies) }
    var perf by remember { mutableStateOf(s.perfMode) }
    var saved by remember { mutableStateOf(false) }
    var orbStyle by remember { mutableStateOf(s.orbStyle) }
    var modelPath by remember { mutableStateOf(s.localModelPath) }
    var template by remember { mutableStateOf(s.localTemplate) }
    var ctxTokens by remember { mutableStateOf(s.localMaxTokens.toString()) }
    var importMsg by remember { mutableStateOf("") }
    var elOn by remember { mutableStateOf(s.elevenEnabled) }
    var elKey by remember { mutableStateOf(s.elevenKey) }
    var elVoice by remember { mutableStateOf(s.elevenVoiceId) }
    var elModel by remember { mutableStateOf(s.elevenModel) }
    var ctOn by remember { mutableStateOf(s.customTtsOn) }
    var ctBase by remember { mutableStateOf(s.customTtsBase) }
    var ctKey by remember { mutableStateOf(s.customTtsKey) }
    var ctModel by remember { mutableStateOf(s.customTtsModel) }
    var ctVoice by remember { mutableStateOf(s.customTtsVoice) }
    var phoneVoice by remember { mutableStateOf(s.phoneVoice) }
    var phoneRate by remember { mutableStateOf(s.phoneRate) }
    var phonePitch by remember { mutableStateOf(s.phonePitch) }
    var ambient by remember { mutableStateOf(s.ambientMode) }
    var hands by remember { mutableStateOf(s.handsFree) }
    var wakePath by remember { mutableStateOf(s.wakeModelPath) }
    var wakeMsg by remember { mutableStateOf("") }
    val appCtx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importMsg = "Copying model into JARVIS storage... (1-3 GB, please wait)"
            importMsg = try {
                LocalEngine.close()
                modelPath = LocalModels.import(appCtx, uri)
                s.localModelPath = modelPath
                "Model imported."
            } catch (e: Exception) { "Import failed: ${e.message}" }
        }
    }

    val wakePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            wakeMsg = "Unpacking wake-word model..."
            wakeMsg = try {
                wakePath = com.jarvis.voice.WakeModels.import(appCtx, uri)
                s.wakeModelPath = wakePath
                "Wake-word model ready."
            } catch (e: Exception) { "Import failed: ${e.message}" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("AI PROVIDER")
        ChoiceRow(listOf("local" to "ON-DEVICE", "gemini" to "GEMINI", "openai" to "OPENAI-COMPATIBLE"), provider) { provider = it }
        if (provider == "local") {
            Mono(if (modelPath.isBlank()) "No model imported yet." else "Model: " + modelPath.substringAfterLast('/'), Jx.text, 12)
            HudButton("IMPORT MODEL FILE (.task / .bin)", { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
            if (importMsg.isNotBlank()) Mono(importMsg, Jx.dim, 11)
            Mono("Prompt format", Jx.dim, 12)
            ChoiceRow(listOf("auto" to "AUTO", "gemma" to "GEMMA", "llama3" to "LLAMA 3", "chatml" to "QWEN/CHATML"), template) { template = it }
            Field("Context tokens (1024-8192)", ctxTokens) { ctxTokens = it.filter(Char::isDigit) }
            Mono("Runs 100% offline. Use a 1B-3B instruct model (e.g. Qwen2.5-1.5B, Llama-3.2-1B/3B, Gemma-3-1B). Bigger models need more RAM and may crash the app. See docs/LOCAL_MODEL.md.", Jx.dim, 11)
        } else if (provider == "gemini") {
            Field("Gemini API key", gKey, true) { gKey = it }
            Field("Model", gModel) { gModel = it }
        } else {
            Field("API key", oKey, true) { oKey = it }
            Field("Base URL (https)", oBase) { oBase = it }
            Field("Model", oModel) { oModel = it }
        }
        Mono("Keys are stored encrypted on this phone and are never written to logs. Check your provider's current free-tier terms: JARVIS does not assume any plan is free.", Jx.dim, 11)

        SectionTitle("CORE STYLE")
        ChoiceRow(listOf("plasma" to "PLASMA", "hud" to "CLASSIC HUD"), orbStyle) { orbStyle = it }

        SectionTitle("HANDS-FREE")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono("Always listen for \"Jarvis\" while ON", Jx.text, 13, modifier = Modifier.weight(1f))
            Switch(hands, { hands = it }, colors = SwitchDefaults.colors(checkedTrackColor = Jx.cyan))
        }
        Mono(if (wakePath.isBlank()) "No wake-word model yet." else "Model: " + wakePath.substringAfterLast('/'), Jx.dim, 12)
        HudButton("IMPORT WAKE-WORD MODEL (.zip)", { wakePicker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
        if (wakeMsg.isNotBlank()) Mono(wakeMsg, Jx.dim, 11)
        Mono("Download vosk-model-small-en-us-0.15.zip (about 40 MB) from alphacephei.com/vosk/models and import it. Detection runs fully on the phone; no audio leaves it. Say \"Jarvis\", wait for the beep, then say the command.", Jx.dim, 11)

        SectionTitle("JARVIS VOICE (ELEVENLABS)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono("Use ElevenLabs voice", Jx.text, 13, modifier = Modifier.weight(1f))
            Switch(elOn, { elOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = Jx.cyan))
        }
        Field("ElevenLabs API key", elKey, true) { elKey = it }
        Field("Voice ID", elVoice) { elVoice = it }
        Field("Model", elModel) { elModel = it }
        Mono("Needs internet and uses your ElevenLabs character quota. If it fails or you are offline, JARVIS falls back to the phone's own voice. Model eleven_flash_v2_5 is fast and speaks Hindi + English.", Jx.dim, 11)

        SectionTitle("CUSTOM VOICE (ANY TTS SERVER)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono("Use custom voice server", Jx.text, 13, modifier = Modifier.weight(1f))
            Switch(ctOn, { ctOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = Jx.cyan))
        }
        Field("Base URL (https://... or http://127.0.0.1:PORT/v1)", ctBase) { ctBase = it }
        Field("API key (optional)", ctKey, true) { ctKey = it }
        Field("Model", ctModel) { ctModel = it }
        Field("Voice name / ID", ctVoice) { ctVoice = it }
        Mono("Works with any OpenAI-compatible /audio/speech server: OpenAI, Groq, Kokoro, Piper (openedai-speech) on your PC or in Termux, and others. Priority: custom voice, then ElevenLabs, then the phone voice. If a voice fails, JARVIS falls back automatically.", Jx.dim, 11)

        SectionTitle("PHONE VOICE (ANY INSTALLED ENGINE)")
        PhoneVoicePicker(phoneVoice) { phoneVoice = it }
        Mono("Speed", Jx.dim, 12)
        ChoiceRow(listOf("0.85" to "SLOW", "1.0" to "NORMAL", "1.2" to "FAST"), phoneRate) { phoneRate = it }
        Mono("Pitch (DEEP sounds more like JARVIS)", Jx.dim, 12)
        ChoiceRow(listOf("0.8" to "DEEP", "1.0" to "NORMAL", "1.2" to "HIGH"), phonePitch) { phonePitch = it }
        HudButton("INSTALL / CHOOSE VOICE ENGINE", {
            runCatching { appCtx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth())
        Mono("Install any text-to-speech app (Google, Samsung, RHVoice, Piper, Sherpa...) from the Play Store or F-Droid, pick it in the phone's TTS settings, then step through its voices above.", Jx.dim, 11)

        SectionTitle("AMBIENT MODE")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono("Full-screen black landscape core while ON", Jx.text, 13, modifier = Modifier.weight(1f))
            Switch(ambient, { ambient = it }, colors = SwitchDefaults.colors(checkedTrackColor = Jx.cyan))
        }
        Mono("Only the STOP button is visible. Tap the core to speak. Press Back to show the controls; tap AMBIENT in the top bar to return.", Jx.dim, 11)

        SectionTitle("VOICE")
        Mono("Listening language", Jx.dim, 12)
        ChoiceRow(listOf("en-IN" to "ENGLISH / HINGLISH", "hi-IN" to "HINDI"), stt) { stt = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono("Speak replies aloud", Jx.text, 13, modifier = Modifier.weight(1f))
            Switch(speak, { speak = it }, colors = SwitchDefaults.colors(checkedTrackColor = Jx.cyan))
        }

        SectionTitle("SAFETY")
        Mono("Controlled mode is enforced: medium/high-risk actions require approval. Sending a message always shows the exact recipient, app and message before sending. Banking, payment and password apps remain blocked.", Jx.dim, 11)

        SectionTitle("PERFORMANCE")
        ChoiceRow(listOf("auto" to "AUTO", "lite" to "LITE", "balanced" to "BALANCED", "high" to "HIGH"), perf) { perf = it }
        Mono("Auto picks by phone RAM. Lite turns animations off for 2-3 GB phones. Refresh rate follows your screen (60/90/120 Hz).", Jx.dim, 11)

        SectionTitle("GITHUB (READ-ONLY)")
        Field("Personal access token (optional)", ghToken, true) { ghToken = it }
        Field("Default repo (owner/name)", ghRepo) { ghRepo = it }

        HudButton(if (saved) "SAVED ✓" else "SAVE", {
            s.elevenEnabled = elOn; s.elevenKey = elKey; s.elevenVoiceId = elVoice; s.elevenModel = elModel
            s.customTtsOn = ctOn; s.customTtsBase = ctBase; s.customTtsKey = ctKey; s.customTtsModel = ctModel; s.customTtsVoice = ctVoice
            s.phoneVoice = phoneVoice; s.phoneRate = phoneRate; s.phonePitch = phonePitch; s.ambientMode = ambient
            s.handsFree = hands; s.orbStyle = orbStyle; s.localTemplate = template; s.localMaxTokens = ctxTokens.toIntOrNull() ?: 4096
            s.provider = provider; s.geminiKey = gKey; s.geminiModel = gModel
            s.openAiKey = oKey; s.openAiBase = oBase; s.openAiModel = oModel
            s.githubToken = ghToken; s.githubRepo = ghRepo
            s.sttLang = stt; s.speakReplies = speak; s.perfMode = perf
            saved = true
            com.jarvis.app.Power.ensureService(appCtx, mic = true) // apply hands-free change right away
            onSaved()
        }, Modifier.fillMaxWidth(), filled = true)

        HudButton("CLEAR CONVERSATION CONTEXT", { g.conversation.clear() }, Modifier.fillMaxWidth(), color = Jx.dim)
    }
}
