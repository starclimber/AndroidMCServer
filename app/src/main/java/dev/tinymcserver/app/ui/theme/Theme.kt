package dev.tinymcserver.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF7CB342)
private val GreenDark = Color(0xFF33691E)
private val Bg = Color(0xFF0F1419)
private val Surface = Color(0xFF1A2129)
private val SurfaceVar = Color(0xFF232C36)

private val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    primaryContainer = GreenDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF80CBC4),
    background = Bg,
    onBackground = Color(0xFFE6E9EC),
    surface = Surface,
    onSurface = Color(0xFFE6E9EC),
    surfaceVariant = SurfaceVar,
    onSurfaceVariant = Color(0xFFB6BEC7),
    error = Color(0xFFEF5350),
)

private val LightScheme = lightColorScheme(
    primary = GreenDark,
    secondary = Color(0xFF00695C),
    background = Color(0xFFF6F8F5),
    surface = Color.White,
)

@Composable
fun TinyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
