// Last synced: 2025-11-22 16:51 (updated)
package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.ui.graphics.Color

/**
 * Палитра и формы для экрана логина / регистрации.
 */
object AuthPalette {

    // Иллюстрации (общие для header/фон)
    val IllustrationBackgroundLight: Color = Color(0xFFF4F3FF)
    val IllustrationBackgroundDark: Color = Color(0xFF151320)
    val IllustrationAccent: Color = Color(0xFF6750A4)
    val ScreenBackgroundOverlay: Color = Color(0x1F000000)

    // фон экрана (бирюзовый под профайлом)
    val ScreenBackground: Color = Color(0xFF0B7C7F)

    // базовый фон полей
    val FieldBase: Color = Color(0xFF4A8A87)

    // базовый цвет кнопки "Sign in / Sign up"
    val ButtonContainer: Color = Color(0xFF2E3E3B)

    // основной цвет текста/иконок на тёмном фоне
    val OnPrimary: Color = Color.White

    // фон ошибки (для текста ошибок, а не для контейнера поля)
    val ErrorContainer: Color = Color(0xFFB3261E).copy(alpha = 0.85f)

    // цвет текста ошибок
    val ErrorText: Color = Color(0xFFFFDAD6)

    // контур подсказок (тонкий тёмный бирюзовый)
    val HintBorder: Color = Color(0xFF066C6E)

    // цвет текста подсказок
    val HintText: Color = OnPrimary.copy(alpha = 0.9f)

    // цвет иконки успешной валидации
    val SuccessIcon: Color = Color(0xFF6EE7B7)

    // цвет иконки ошибки валидации
    val ErrorIcon: Color = Color(0xFFFF9A8A)
}
