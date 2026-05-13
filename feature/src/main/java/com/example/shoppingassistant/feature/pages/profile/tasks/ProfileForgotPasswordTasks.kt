package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.runtime.Composable

/**
 * Контракт UI-задачи "Забыл пароль".
 *
 * Props описывает обязательные зависимости и коллбеки, сама реализация в ProfileForgotPasswordTask.kt.
 */
data class ProfileForgotPasswordProps(
    val onSubmitEmail: suspend (email: String) -> String?,
    val onSubmitPhone: suspend (phone: String) -> String?,
    val onClose: () -> Unit,
    val onContinueToReset: (String?) -> Unit,
    val onBackToLogin: () -> Unit,
)

/**
 * Тип UI-задачи: принимает props, отрисовывает форму.
 */
typealias ProfileForgotPasswordTask = @Composable (ProfileForgotPasswordProps) -> Unit
