package com.jarvis.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.jarvis.app.graph
import com.jarvis.settings.PerfProfile

@Composable
fun HomeScreen(vm: JarvisViewModel, profile: PerfProfile, onMic: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) list.animateScrollToItem(vm.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val orbStyle = LocalContext.current.graph.settings.orbStyle
        val tap = { if (!vm.powered) vm.powerOn() else onMic() }
        if (orbStyle == "hud") {
            Orb(status = vm.status, level = vm.level, profile = profile, modifier = Modifier.fillMaxWidth().height(230.dp).clickable(onClick = tap))
        } else {
            PlasmaOrb(status = vm.status, level = vm.level, on = vm.powered, profile = profile, modifier = Modifier.fillMaxWidth().height(260.dp).clickable(onClick = tap))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Mono(
                when {
                    !vm.powered -> "OFFLINE  ·  tap the core to power on"
                    vm.partial.isNotBlank() -> "› ${vm.partial}"
                    else -> vm.statusLine + if (vm.status == Status.IDLE) "  ·  tap the core to speak" else ""
                },
                color = if (vm.powered) (if (orbStyle == "hud") vm.status.color() else vm.status.plasma()) else Jx.dim,
                size = 12, modifier = Modifier.weight(1f)
            )
            if (vm.powered) HudButton("⏻ OFF", { vm.shutdown() }, color = Jx.red)
        }

        vm.banner?.let { b ->
            HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), accent = Jx.amber) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono(b, Jx.amber, 12, modifier = Modifier.weight(1f))
                    HudButton("OK", { vm.banner = null }, color = Jx.amber)
                }
            }
        }

        vm.pendingConfirm?.let { p ->
            HudPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp), accent = Jx.green) {
                Mono("CONFIRM", Jx.green, 11, true)
                Mono(p, Jx.text, 13, modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudButton("SEND", { vm.answerConfirm(true) }, color = Jx.green, filled = true)
                    HudButton("CANCEL", { vm.answerConfirm(false) }, color = Jx.red)
                }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.messages) { m ->
                val (tag, col) = when (m.role) {
                    "user" -> "YOU" to Jx.dim
                    "system" -> "SYS" to Jx.amber
                    else -> "JARVIS" to Jx.cyan
                }
                HudPanel(Modifier.fillMaxWidth(), accent = col) {
                    Mono(tag, col, 10, true)
                    Mono(m.text, Jx.text, 13)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Mono("Type a command…", Jx.dim, 13) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                colors = hudFieldColors(), maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.submitText(input); input = "" })
            )
            Spacer(Modifier.padding(3.dp))
            HudButton("SEND", { vm.submitText(input); input = "" }, filled = true)
        }
    }
}
