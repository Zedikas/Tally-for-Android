package com.samua.tally.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.samua.tally.model.TallyTheme
import com.samua.tally.model.presetColors
import com.samua.tally.model.rawColorHex

val PinkTally = Color(0xFFFF7EFF)
val PurpleTally = Color(0xFF9000FF)
val BlueTally = Color(0xFF0A84FF)

fun colorFromRaw(raw: String, fallback: Color = BlueTally): Color {
    presetColors[raw]?.let { return Color(it) }
    val hex = rawColorHex(raw) ?: return fallback
    return runCatching { Color(0xFF000000L or hex.toLong(16)) }.getOrDefault(fallback)
}

@Composable
fun TallyTheme(
    theme: TallyTheme,
    accentRaw: String,
    customAccentHex: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (theme) {
        TallyTheme.SYSTEM -> isSystemInDarkTheme()
        TallyTheme.LIGHT -> false
        TallyTheme.DARK, TallyTheme.OLED -> true
    }
    val accent = if (accentRaw == "custom") colorFromRaw("custom:$customAccentHex") else colorFromRaw(accentRaw)
    val scheme = when {
        theme == TallyTheme.OLED -> darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = PinkTally,
            background = Color.Black,
            surface = Color(0xFF090909),
            surfaceVariant = Color(0xFF141414),
            onBackground = Color.White,
            onSurface = Color.White
        )
        dark -> darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = PinkTally,
            background = Color(0xFF121319),
            surface = Color(0xFF1B1C23),
            surfaceVariant = Color(0xFF272832)
        )
        else -> lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = PurpleTally,
            background = Color(0xFFF7F7FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEDEEF5)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = androidx.compose.material3.Typography(), content = content)
}
