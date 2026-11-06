package com.jarvis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Jx {
    val bg = Color(0xFF04070D)
    val panel = Color(0xFF0A111C)
    val cyan = Color(0xFF00E5FF)
    val blue = Color(0xFF2979FF)
    val violet = Color(0xFF7C4DFF)
    val green = Color(0xFF1DE9B6)
    val amber = Color(0xFFFFB300)
    val red = Color(0xFFFF1744)
    val text = Color(0xFFD6F6FF)
    val dim = Color(0xFF6B8A99)
}

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Jx.cyan, onPrimary = Color.Black, secondary = Jx.blue,
            background = Jx.bg, onBackground = Jx.text, surface = Jx.panel, onSurface = Jx.text, error = Jx.red
        ),
        content = content
    )
}

/** Panel with thin border and HUD-style corner brackets. */
@Composable
fun HudPanel(modifier: Modifier = Modifier, accent: Color = Jx.cyan, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier
            .background(Jx.panel.copy(alpha = 0.85f), shape)
            .border(1.dp, accent.copy(alpha = 0.25f), shape)
            .drawBehind {
                val l = 14.dp.toPx()
                val w = 2.dp.toPx()
                val c = accent.copy(alpha = 0.9f)
                val x1 = size.width
                val y1 = size.height
                drawLine(c, Offset(0f, 0f), Offset(l, 0f), w); drawLine(c, Offset(0f, 0f), Offset(0f, l), w)
                drawLine(c, Offset(x1, 0f), Offset(x1 - l, 0f), w); drawLine(c, Offset(x1, 0f), Offset(x1, l), w)
                drawLine(c, Offset(0f, y1), Offset(l, y1), w); drawLine(c, Offset(0f, y1), Offset(0f, y1 - l), w)
                drawLine(c, Offset(x1, y1), Offset(x1 - l, y1), w); drawLine(c, Offset(x1, y1), Offset(x1, y1 - l), w)
            }
            .padding(12.dp),
        content = content
    )
}

@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Jx.cyan,
    filled: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) color.copy(alpha = 0.92f) else color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (filled) Color.Black else color.copy(alpha = if (enabled) 1f else 0.4f),
            fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Jx.cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
}

@Composable
fun Mono(text: String, color: Color = Jx.text, size: Int = 13, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = modifier
    )
}

@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row {
        options.forEach { (value, label) ->
            val on = value == selected
            HudButton(
                label, { onSelect(value) },
                Modifier.padding(end = 8.dp), color = if (on) Jx.cyan else Jx.dim, filled = on
            )
        }
    }
}

@Composable
fun hudFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Jx.cyan, unfocusedBorderColor = Jx.cyan.copy(alpha = 0.3f),
    cursorColor = Jx.cyan, focusedTextColor = Jx.text, unfocusedTextColor = Jx.text,
    focusedLabelColor = Jx.cyan, unfocusedLabelColor = Jx.dim
)
