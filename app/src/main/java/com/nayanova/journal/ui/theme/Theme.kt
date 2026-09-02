package com.nayanova.journal.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue80 = Color(0xFFBBDEFB)
private val Blue40 = Color(0xFF1565C0)
private val BlueGrey80 = Color(0xFFB0BEC5)
private val BlueGrey40 = Color(0xFF546E7A)
private val Teal80 = Color(0xFFB2DFDB)
private val Teal40 = Color(0xFF00897B)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Teal40,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
