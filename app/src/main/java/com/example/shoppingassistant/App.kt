// Last synced: 2025-11-24 21:10:17
package com.example.shoppingassistant

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =============================
// "БРЕНДОВАЯ" (ТЕКУЩАЯ) ПАЛИТРА
// =============================

// Светлая палитра, которую мы использовали раньше
private val BrandLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),      // холодный синий акцент
    onPrimary = Color.White,
    secondary = Color(0xFF22C55E),    // зелёный акцент (успех / выгодные бейджи)
    onSecondary = Color.White,
    tertiary = Color(0xFFFFC857),     // тёплый акцент (gold-вайб)
    background = Color(0xFFF7FAFF),   // мягкий почти белый фон
    surface = Color(0xFFFFFFFF),      // карточки / панели
    onBackground = Color(0xFF020617),
    onSurface = Color(0xFF020617),
)

// Тёмная палитра под "ледяной" скрин
private val BrandDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xE67EC5FF),      // светящаяся голубая рамка и иконки
    onPrimary = Color(0xFF00111F),
    secondary = Color(0xFF323232),    // мятный/серый акцент
    onSecondary = Color(0xFF00150D),
    tertiary = Color(0xFFFFC857),     // золотистый бейдж / "лучшие"
    background = Color(0xFF282828),   // тёмный фон страницы
    surface = Color(0xFF282828),      // фон карточек / панелей (из-за этого они сливаются)
    onBackground = Color(0xFFE5F0FF),
    onSurface = Color(0xFFE5F0FF),
)

// =============================
// НОВАЯ КЛАССИЧЕСКАЯ СВЕТЛАЯ/ТЁМНАЯ ТЕМА
// =============================

/**
 * Application theme that adapts to the system dark theme setting.  This
 * simplified theme defines a classic light and dark palette that uses pure
 * white and pure black as primary backgrounds, soft greys for cards and
 * surfaces, and gentle accent colours for highlighted elements.  It does not
 * expose configuration flags for brand vs classic colours; instead it always
 * uses the updated classic palette described in the design brief.
 *
 * @param useDarkTheme whether to use the dark colour palette (defaults to
 *                     system setting)
 * @param content the composable children to which this theme will be applied
 */
@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (useDarkTheme) {
        ClassicDarkColors
    } else {
        ClassicLightColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

// -----------------------------------------------------------------------------
//  Colour palettes
//
// Light theme: pure whites for the main background and cards, soft greys for
// secondary backgrounds and outlines, and gentle accent colours.  On-colours
// are chosen to maximise contrast against their backgrounds.  The primary,
// secondary and tertiary colours mirror the brand's palette but remain muted
// to blend with the light design.
private val ClassicLightColors: ColorScheme = lightColorScheme(
    // A softer blue used across the light theme. This pastel tone blends
    // better with the white background and avoids overly saturated accents.
    primary = Color(0xFF4F7DF9),
    onPrimary = Color.White,
    // A muted green for secondary actions such as delivery badges. It is
    // still vibrant enough to draw attention without appearing neon.
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color.White,
    // A warm golden hue for tertiary elements like ranking badges. The
    // lightness works well on white cards and surfaces.
    tertiary = Color(0xFFFFD56F),
    // Use pure white for the main background and card surfaces to
    // maximise contrast with content.
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF3F3F3),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    // Secondary surfaces are very light grey, providing subtle separation
    // between cards and lists while maintaining a clean feel.
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF444444),
    // Light outlines for dividing elements, slightly darker than the
    // variant background to remain visible but unobtrusive.
    outline = Color(0xFFE0E0E0),
    // Tint interactive elements (e.g. toggles) with the primary blue.
    surfaceTint = Color(0xFF4F7DF9),
)

// Dark theme: pure black for the main background, dark greys for cards and
// surfaces, and light grey tones for text.  Accent colours remain the same
// as in the light theme but are applied on dark backgrounds.  Additional
// surface variants and outlines are chosen to create subtle separation
// between elements without overwhelming the user.
private val ClassicDarkColors: ColorScheme = darkColorScheme(
    // Pastel blue primary colour that is easier on the eyes at night.
    primary = Color(0xFF7EA7FF),
    onPrimary = Color(0xFF000000),
    // Soft green for secondary highlights such as delivery labels.
    secondary = Color(0xFFB1E0B3),
    onSecondary = Color(0xFF000000),
    // Gentle golden tone for tertiary elements like ranking chips.
    tertiary = Color(0xFFFFE08F),
    // Use pure black to maximise contrast for dark backgrounds.
    background = Color(0xFF000000),
    // Dark cards and panels are slightly lighter than the background to
    // distinguish surfaces without appearing greyed out.
    surface = Color(0xFF1A1A1A),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    // Secondary surfaces have a subtle elevation above the base background.
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFB3B3B3),
    // Outlines are slightly lighter than surfaces to create soft separators.
    outline = Color(0xFF333333),
    // Tint interactive elements with the primary pastel blue.
    surfaceTint = Color(0xFF7EA7FF),
)