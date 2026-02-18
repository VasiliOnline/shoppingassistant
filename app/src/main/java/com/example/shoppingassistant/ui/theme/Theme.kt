package com.example.shoppingassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.shoppingassistant.domain.profile.ProfileThemePreference

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E5EFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1E5EFF),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1E5EFF),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F7F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F2F4),
    outline = Color(0xFFE0E3E8),
    outlineVariant = Color(0xFFE6E9EE),
    onBackground = Color(0xFF0B1220),
    onSurface = Color(0xFF0B1220),
    onSurfaceVariant = Color(0xFF4A4F58),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7DBEFF),
    onPrimary = Color(0xFF00111F),
    secondary = Color(0xFF7DBEFF),
    onSecondary = Color(0xFF00111F),
    tertiary = Color(0xFF7DBEFF),
    onTertiary = Color(0xFF00111F),
    background = Color(0xFF14161A),
    surface = Color(0xFF1A1D22),
    surfaceVariant = Color(0xFF232831),
    outline = Color(0xFF2C333D),
    outlineVariant = Color(0xFF333B46),
    onBackground = Color(0xFFE6ECF5),
    onSurface = Color(0xFFE6ECF5),
    onSurfaceVariant = Color(0xFFB5BDC9),
)

@Composable
fun AppTheme(
    themePreference: ProfileThemePreference = ProfileThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themePreference) {
        ProfileThemePreference.LIGHT -> false
        ProfileThemePreference.DARK -> true
        ProfileThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
