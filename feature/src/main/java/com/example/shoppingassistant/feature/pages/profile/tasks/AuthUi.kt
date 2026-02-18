// Last synced: 2025-11-19 15:14
// FILE: feature/src/main/java/com/example/shoppingassistant/feature/pages/profile/tasks/AuthUi.kt
// Restored and cleaned by GPT on 2025-12-06
package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.R

/**
 * Дополнительное состояние валидации поля.
 */
enum class AuthFieldValidationState {
    None,
    Success,
    Error,
}

/**
 * Общий заголовок-иллюстрация для экранов логина/регистрации/OTP.
 * Стили близки к макетам: крупный блок сверху + текст под ним.
 */
@Composable
fun AuthIllustrationHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val illustrationBackground = if (isLight) {
            AuthPalette.IllustrationBackgroundLight
        } else {
            AuthPalette.IllustrationBackgroundDark
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 8.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    illustrationBackground,
                                    illustrationBackground.copy(alpha = 0.8f),
                                ),
                            ),
                            shape = RoundedCornerShape(32.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Общий текстовый инпут для auth-экранов.
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean,
    errorText: String?,
    keyboardOptions: KeyboardOptions,
    focusRequester: FocusRequester,
    validationState: AuthFieldValidationState = AuthFieldValidationState.None,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
) {
    val visualTransformation: VisualTransformation =
        if (isPassword && !isPasswordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }

    val labelColor = when {
        isError -> MaterialTheme.colorScheme.error
        validationState == AuthFieldValidationState.Success ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = {
                Text(
                    text = label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = leadingIcon,
            trailingIcon = if (isPassword && onPasswordVisibilityChange != null) {
                {
                    IconButton(onClick = onPasswordVisibilityChange) {
                        Icon(
                            imageVector = if (isPasswordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (isPasswordVisible) {
                                stringResource(R.string.profile_auth_hide_password)
                            } else {
                                stringResource(R.string.profile_auth_show_password)
                            },
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            isError = isError,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = containerColor,
                focusedContainerColor = containerColor,
                disabledContainerColor = containerColor.copy(alpha = 0.6f),
                errorContainerColor = containerColor,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )

        if (isError && errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Общая основная кнопка для auth-экранов.
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseContainer = MaterialTheme.colorScheme.primary
    val baseContent = MaterialTheme.colorScheme.onPrimary
    val pressedContainer = MaterialTheme.colorScheme.primaryContainer
    val pressedContent = MaterialTheme.colorScheme.onPrimaryContainer

    val containerColor = when {
        !enabled || isLoading -> baseContainer.copy(alpha = 0.7f)
        isPressed -> pressedContainer
        else -> baseContainer
    }

    val contentColor = when {
        !enabled || isLoading -> baseContent.copy(alpha = 0.9f)
        isPressed -> pressedContent
        else -> baseContent
    }

    Button(
        onClick = {
            if (enabled && !isLoading) {
                onClick()
            }
        },
        modifier = modifier.height(50.dp),
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(18.dp)
                    .padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Простая валидация email/пароля для auth-экранов.
 */
fun validateAuthEmail(
    email: String,
    emptyError: String,
    invalidError: String,
): String? {
    val value = email.trim()
    if (value.isEmpty()) {
        return emptyError
    }
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    if (!emailRegex.matches(value)) {
        return invalidError
    }
    return null
}

fun validateAuthPassword(
    password: String,
    shortError: String,
): String? {
    if (password.length < 6) {
        return shortError
    }
    return null
}
