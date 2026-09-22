package com.chronocube.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChronoCubeColors = darkColorScheme(
    primary = Color(0xFF79E8C5),
    onPrimary = Color(0xFF00382B),
    secondary = Color(0xFF9EBBFF),
    onSecondary = Color(0xFF082A61),
    background = Color(0xFF05070B),
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF10141D),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF1A202C),
    onSurfaceVariant = Color(0xFFBFC7D5),
    outline = Color(0xFF657184),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ChronoCubeTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ChronoCubeColors,
        content = content,
    )
}
