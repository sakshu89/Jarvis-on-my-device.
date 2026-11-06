package com.jarvis.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.jarvis.voice.WakeWord
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.agent.IntentProcessor
import com.jarvis.ai.AIException
import com.jarvis.app.Power
import com.jarvis.app.graph
import com.jarvis.automation.Notifier
import com.jarvis.security.KillSwitch
import com.jarvis.voice.VoiceInput
import com.jarvis.voice.VoiceOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class UiMsg(val role: String, val text: String, val ts: Long = System.currentTimeMillis())

class JarvisViewModel(private val app: Application) : AndroidViewModel(app) {
    private val g = app.graph
    private val voiceIn = VoiceInput(app)
    private val voiceOut = VoiceOutput(app)

    var status by mutableStateOf(Status.IDLE); private set
    var statusLine by mutableStateOf("STANDBY"); private set
    var partial by mutableStateOf(""); private set
    var level by mutableFloatStateOf(0f); private set
    var pendingConfirm by mutableStateOf<String?>(null); private set
    var banner by mutableStateOf<String?>(null)
    var powered by mutableStateOf(Power.isOn); private set
    val messages = mutableStateListOf<UiMsg>()

    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())

    private val onKill: () -> Unit = {
        main.post {
            job?.cancel()
            voiceIn.cancel()
            voiceOut.stop()
            Notifier.clearAll(app)
            partial = ""
            status = Status.IDLE
            statusLine = "STOPPED"
            if (Power.isOn) messages.add(UiMsg("system", "Emergency stop: all pending automation was cancelled."))
            g.actionLog.add("emergency_stop", "", "Stop triggered by user")
        }
    }

    init {
        KillSwitch.addListener(onKill)
        if (!Power.isOn) statusLine = "OFFLINE"
        viewModelScope.launch { voiceOut.notice.collect { n -> if (n != null) { banner = n; voiceOut.notice.value = null } } }
        viewModelScope.launch {
            snapshotFlow { status != Status.IDLE || pendingConfirm != null }.collect { WakeWord.busy.value = it }
        }
        viewModelScope.launch {
            Power.on.collect { on ->
                powered = on
                if (on) { statusLine = "STANDBY" }
                else {
                    job?.cancel(); voiceIn.cancel(); voiceOut.stop()
                    partial = ""; level = 0f; status = Status.IDLE; statusLine = "OFFLINE"
                }
            }
        }
        viewModelScope.launch { voiceIn.level.collect { level = it } }
        viewModelScope.launch {
            voiceOut.speaking.collect { s ->
                if (!s && status == Status.SPEAKING) { status = Status.IDLE; statusLine = "STANDBY" }
            }
        }
        viewModelScope.launch {
            g.confirmations.pending.collect { p ->
                pendingConfirm = p
                if (p != null) voiceOut.speak("JARVIS needs your approval. Please review the request.", "en")
            }
        }
        if (!g.settings.hasAiKey()) banner = "Choose an AI in Settings (import an on-device model, or add an API key) to start."
    }

    fun micGranted() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Tap the orb / mic: start listening, or finish listening if already open. */
    fun onMic() {
        if (!powered) { powerOn(); return }
        if (status == Status.LISTENING) { voiceIn.stop(); return }
        if (!micGranted()) { banner = "Microphone permission is needed for voice. Open the SETUP tab."; return }
        if (!voiceIn.available) { banner = "No speech recognition service on this phone. Type instead, or install the Google app / voice typing."; return }
        voiceOut.stop()
        banner = null
        status = Status.LISTENING
        statusLine = "LISTENING"
        voiceIn.start(
            g.settings.sttLang,
            onPartialText = { partial = it },
            onFinalText = { partial = ""; handle(it) },
            onFail = { msg ->
                partial = ""
                status = Status.IDLE
                statusLine = "STANDBY"
                if (msg.isNotBlank()) banner = msg
            }
        )
    }

    /** Called by the service when the wake word "Jarvis" was heard. */
    fun onWake() {
        if (!powered || status != Status.IDLE || !micGranted()) return
        runCatching {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 70)
                .startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120)
        }
        onMic()
    }

    fun powerOn() {
        Power.powerOn(app)
        messages.add(UiMsg("system", "JARVIS online. It stays on until you say \"shutdown\"."))
    }

    fun shutdown() {
        messages.add(UiMsg("system", "JARVIS shut down. Tap the core to power on."))
        g.confirmations.takeIf { it.hasPending() }?.answer(false)
        Power.shutdown(app)
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        if (!powered) { messages.add(UiMsg("system", "JARVIS is powered off. Tap the core to power on.")); return }
        banner = null
        handle(text.trim())
    }

    fun emergencyStop() = KillSwitch.trigger()

    fun answerConfirm(yes: Boolean) {
        g.confirmations.answer(yes)
    }

    private fun handle(raw: String) {
        if (raw.isBlank()) { status = Status.IDLE; statusLine = "STANDBY"; return }
        messages.add(UiMsg("user", raw))
        val p = IntentProcessor.process(raw)
        if (p.shutdown) { shutdown(); return }
        if (p.stop) {
            // STOP while something is running = cancel that task (stay ON). STOP while idle = full shutdown.
            val busy = job?.isActive == true || status == Status.SPEAKING || pendingConfirm != null
            if (busy) KillSwitch.trigger() else shutdown()
            return
        }
        if (g.confirmations.hasPending()) {
            when {
                p.yes -> g.confirmations.answer(true)
                p.no -> g.confirmations.answer(false)
                else -> speakNow("Please say yes to send, or no to cancel.", "en")
            }
            return
        }
        if (p.clean.isBlank()) { status = Status.IDLE; statusLine = "STANDBY"; return }
        runAgent(p.clean)
    }

    private fun runAgent(text: String) {
        KillSwitch.reset()
        job?.cancel()
        status = Status.THINKING
        statusLine = "THINKING"
        Notifier.showWorking(app, text.take(80))
        job = viewModelScope.launch {
            try {
                val reply = g.agent.run(text) { statusLine = it.uppercase() }
                messages.add(UiMsg("assistant", reply.text))
                speakNow(reply.text, reply.lang)
            } catch (e: CancellationException) {
                // emergency stop: UI already updated by onKill
            } catch (e: AIException) {
                fail(e.message ?: "AI error")
            } catch (e: Exception) {
                fail("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                Notifier.clear(app, Notifier.NID_WORK)
            }
        }
    }

    private fun fail(msg: String) {
        messages.add(UiMsg("assistant", msg))
        status = Status.ERROR
        statusLine = "ERROR"
    }

    private fun speakNow(text: String, lang: String) {
        if (g.settings.speakReplies) {
            status = Status.SPEAKING
            statusLine = "SPEAKING"
            voiceOut.speak(text, lang)
        } else {
            status = Status.IDLE
            statusLine = "STANDBY"
        }
    }

    override fun onCleared() {
        KillSwitch.removeListener(onKill)
        voiceIn.cancel()
        voiceOut.shutdown()
        super.onCleared()
    }
}
