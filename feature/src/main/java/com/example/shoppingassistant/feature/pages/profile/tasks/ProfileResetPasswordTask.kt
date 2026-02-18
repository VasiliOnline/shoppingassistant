// Last synced: 2025-11-26 14:15:35
package com.example.shoppingassistant.feature.pages.profile.tasks

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.R
import com.example.shoppingassistant.feature.ui.animations.rememberSuccessAnimationTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Модальная форма смены пароля по reset-токену.
 *
 * Пользователь вводит:
 * - reset-токен;
 * - новый пароль;
 * - подтверждение пароля.
 *
 * onResetPassword — suspend-коллбек, который реально бьёт в backend.
 * onSuccess — вызывается только при успешной смене.
 * onClose — закрывает модалку без изменений.
 */
//
@SuppressLint("RememberInComposition")
@Composable
fun ProfileResetPasswordTask(
    onResetPassword: suspend (token: String, newPassword: String) -> Unit,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
    initialToken: String? = null,
) {
    val scope = rememberCoroutineScope()
    val successAnimation = rememberSuccessAnimationTask()
    val titleText = stringResource(R.string.profile_reset_title)
    val successTitle = stringResource(R.string.profile_reset_success_title)
    val successSubtitle = stringResource(R.string.profile_reset_success_subtitle)
    val goToLogin = stringResource(R.string.profile_reset_go_to_login)
    val tokenLabel = stringResource(R.string.profile_reset_token_label)
    val tokenPlaceholder = stringResource(R.string.profile_reset_token_placeholder)
    val newPasswordLabel = stringResource(R.string.profile_reset_new_password_label)
    val newPasswordPlaceholder = stringResource(R.string.profile_password_placeholder)
    val confirmLabel = stringResource(R.string.profile_reset_confirm_password_label)
    val confirmPlaceholder = stringResource(R.string.profile_reset_confirm_placeholder)
    val changePasswordLabel = stringResource(R.string.profile_reset_action)
    val cancelLabel = stringResource(R.string.profile_reset_cancel)
    val tokenRequiredText = stringResource(R.string.profile_reset_token_required)
    val passwordMismatchText = stringResource(R.string.profile_reset_password_mismatch)
    val resetFailedText = stringResource(R.string.profile_reset_failed)
    val passwordShortText = stringResource(R.string.profile_auth_password_short)

    var resetSuccess by remember { mutableStateOf(false) }
    var successNotified by remember { mutableStateOf(false) }

    var resetToken by remember { mutableStateOf(initialToken ?: "") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var tokenError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(initialToken) {
        resetSuccess = false
        successNotified = false
        isLoading = false
        errorText = null
    }

    LaunchedEffect(resetSuccess) {
        if (resetSuccess && !successNotified) {
            delay(1800)
            successNotified = true
            onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
            )

            if (resetSuccess) {
                successAnimation.Render(modifier = Modifier.size(120.dp))
                Text(
                    text = successTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = successSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuthPrimaryButton(
                    text = goToLogin,
                    onClick = {
                        if (!successNotified) {
                            successNotified = true
                            onSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isLoading = false,
                )
            } else {
                // Поле для reset-токена
                AuthTextField(
                    value = resetToken,
                    onValueChange = {
                        resetToken = it
                        tokenError = null
                        errorText = null
                    },
                    label = tokenLabel,
                    placeholder = tokenPlaceholder,
                    leadingIcon = null,
                    isError = tokenError != null,
                    errorText = tokenError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    focusRequester = FocusRequester(),
                )

                // Новый пароль
                AuthTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        passwordError = null
                        errorText = null
                    },
                    label = newPasswordLabel,
                    placeholder = newPasswordPlaceholder,
                    leadingIcon = null,
                    isError = passwordError != null,
                    errorText = passwordError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    focusRequester = FocusRequester(),
                    isPassword = true,
                    isPasswordVisible = false,
                    onPasswordVisibilityChange = null,
                )

                // Подтверждение пароля
                AuthTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmError = null
                        errorText = null
                    },
                    label = confirmLabel,
                    placeholder = confirmPlaceholder,
                    leadingIcon = null,
                    isError = confirmError != null,
                    errorText = confirmError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    focusRequester = FocusRequester(),
                    isPassword = true,
                    isPasswordVisible = false,
                    onPasswordVisibilityChange = null,
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                AuthPrimaryButton(
                    text = changePasswordLabel,
                    onClick = {
                        val token = resetToken.trim()
                        if (token.isEmpty()) {
                            tokenError = tokenRequiredText
                        }

                        val passwordValidation = validateAuthPassword(
                            newPassword,
                            passwordShortText,
                        )
                        passwordError = passwordValidation

                        if (confirmPassword != newPassword) {
                            confirmError = passwordMismatchText
                        }

                        if (
                            tokenError != null ||
                            passwordError != null ||
                            confirmError != null
                        ) {
                            return@AuthPrimaryButton
                        }

                        isLoading = true
                        errorText = null

                        scope.launch {
                            try {
                                onResetPassword(token, newPassword)
                                resetSuccess = true
                            } catch (t: Throwable) {
                                errorText = t.message
                                    ?: resetFailedText
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isLoading = isLoading,
                )

                TextButton(
                    onClick = {
                        if (!isLoading) onClose()
                    },
                    enabled = !isLoading,
                ) {
                    Text(
                        text = cancelLabel,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
