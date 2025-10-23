package com.example.shoppingassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Светлая палитра (набор основных цветов приложения в светлой теме)
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),     // основной цвет (кнопки/акценты)
    onPrimary = Color.White,
    secondary = Color(0xFF22C55E),   // дополнительный (поддерживающий)
    onSecondary = Color.White,
    tertiary = Color(0xFFF59E0B),    // ещё один акцент (предупреждения/метки)
    background = Color(0xFFF8FAFC),  // фон экранов
    surface = Color(0xFFFFFFFF),     // фон карточек/панелей
    onBackground = Color(0xFF0F172A),// текст на фоне
    onSurface = Color(0xFF0F172A)    // текст на карточках
)

// Тёмная палитра (те же роли, но под тёмный фон)
private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF001027),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF002114),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111827),
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB)
)

/**
 * AppTheme — «обёртка темы» (единый стиль) для всех экранов.
 * [isSystemInDarkTheme] — автоматически читает настройку темы на устройстве пользователя.
 */
@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(), // автоподстройка под систему
    content: @Composable () -> Unit                // UI-вложение (экраны, карточки и т.п.)
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        // Typography() и Shapes() оставим по умолчанию — потом при желании настроим
        content = content
    )
}
