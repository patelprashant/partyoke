package com.partyoke.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val PartyPink = Color(0xFFFF2E92)
val PartyPurple = Color(0xFF7C3AED)
val PartyBg = Color(0xFF140A24)
val PartyCard = Color(0xFF1D1033)
val PartyMuted = Color(0xFFB9A9D6)

private val PartyOkeColors = darkColorScheme(
    primary = PartyPink,
    secondary = PartyPurple,
    background = PartyBg,
    surface = PartyCard,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun PartyOkeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PartyOkeColors, content = content)
}

/**
 * Parses a "#RRGGBB" accent string into a Compose [Color], falling back to
 * [PartyPink] if the value is missing or malformed. Used to apply the
 * admin-selected accent on the TV screens.
 */
fun parseAccent(hex: String?): Color {
    if (hex.isNullOrBlank()) return PartyPink
    return try {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return PartyPink
        Color(0xFF000000 or clean.toLong(16))
    } catch (e: Exception) {
        PartyPink
    }
}
