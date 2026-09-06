package com.watchrelay.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val Ink = Color(0xFF0B1117)
val Panel = Color(0xFF151C24)
val PanelSoft = Color(0xFF1C2530)
val Mint = Color(0xFF3DDC97)
val Sky = Color(0xFF7EB6FF)
val Amber = Color(0xFFFFB86B)
val TextMain = Color(0xFFE8EEF4)
val TextDim = Color(0xFF9AA8B6)

private val colors = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = Sky,
    tertiary = Amber,
    background = Ink,
    surface = Panel,
    onBackground = TextMain,
    onSurface = TextMain,
    onSurfaceVariant = TextDim,
    outline = Color(0xFF2A3542)
)

private val type = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        color = TextMain
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = TextMain
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        color = TextMain
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = TextMain),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = TextDim),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
