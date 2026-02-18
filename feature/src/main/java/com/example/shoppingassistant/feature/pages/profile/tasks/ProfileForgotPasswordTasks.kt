package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.runtime.Composable

/**
 * Контракт UI-задачи "Забыл пароль".
 *
 * Props описывает обязательные зависимости и коллбеки, сама реализация в ProfileForgotPasswordTask.kt.
 */
data class ProfileForgotPasswordProps(
    val onSubmit: suspend (email: String) -> String?,
    val onClose: () -> Unit,
    val onResetTokenReceived: (String?) -> Unit,
)

/**
 * Тип UI-задачи: принимает props, отрисовывает форму.
 */
typealias ProfileForgotPasswordTask = @Composable (ProfileForgotPasswordProps) -> Unit
