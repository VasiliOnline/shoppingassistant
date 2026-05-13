package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.R
import kotlinx.coroutines.launch

private enum class RecoveryChannel {
    EMAIL,
    PHONE,
}

@Composable
fun ProfileForgotPasswordTask(
    props: ProfileForgotPasswordProps,
) {
    var channel by remember { mutableStateOf(RecoveryChannel.EMAIL) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var successToken by remember { mutableStateOf<String?>(null) }
    var successChannel by remember { mutableStateOf<RecoveryChannel?>(null) }

    val emailFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val titleText = stringResource(R.string.profile_forgot_title)
    val emailLabel = stringResource(R.string.profile_email_label)
    val emailPlaceholder = stringResource(R.string.profile_email_placeholder)
    val phoneLabel = stringResource(R.string.profile_phone_label)
    val phonePlaceholder = stringResource(R.string.profile_phone_placeholder)
    val sendLabel = stringResource(
        if (channel == RecoveryChannel.EMAIL) {
            R.string.profile_forgot_send_link
        } else {
            R.string.profile_forgot_send_code
        },
    )
    val backLabel = stringResource(R.string.profile_forgot_back)
    val continueLabel = stringResource(R.string.profile_forgot_continue_to_reset)
    val emailSuccessText = stringResource(R.string.profile_forgot_success)
    val phoneSuccessText = stringResource(R.string.profile_forgot_phone_success)
    val errorTextDefault = stringResource(R.string.profile_forgot_failed)
    val emailRequiredText = stringResource(R.string.profile_auth_email_required)
    val emailInvalidText = stringResource(R.string.profile_auth_email_invalid)
    val phoneRequiredText = stringResource(R.string.profile_auth_phone_required)
    val phoneInvalidText = stringResource(R.string.profile_auth_phone_invalid)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthPalette.ScreenBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )

            RecoveryChannelSwitch(
                selected = channel,
                onSelected = {
                    channel = it
                    fieldError = null
                    errorText = null
                    successChannel = null
                    successToken = null
                },
            )

            if (successChannel != null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (successChannel == RecoveryChannel.EMAIL) emailSuccessText else phoneSuccessText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.profile_forgot_continue_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AuthPrimaryButton(
                                text = continueLabel,
                                onClick = { props.onContinueToReset(successToken) },
                                modifier = Modifier.weight(1f),
                                enabled = true,
                                isLoading = false,
                            )
                            OutlinedButton(
                                onClick = props.onBackToLogin,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(backLabel)
                            }
                        }
                    }
                }
            } else {
                AuthTextField(
                    value = if (channel == RecoveryChannel.EMAIL) email else phone,
                    onValueChange = { newValue ->
                        if (channel == RecoveryChannel.EMAIL) {
                            email = newValue
                        } else {
                            phone = newValue
                        }
                        fieldError = null
                        errorText = null
                    },
                    label = if (channel == RecoveryChannel.EMAIL) emailLabel else phoneLabel,
                    placeholder = if (channel == RecoveryChannel.EMAIL) emailPlaceholder else phonePlaceholder,
                    leadingIcon = null,
                    isError = fieldError != null,
                    errorText = fieldError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = if (channel == RecoveryChannel.EMAIL) KeyboardType.Email else KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    focusRequester = if (channel == RecoveryChannel.EMAIL) emailFocusRequester else phoneFocusRequester,
                    validationState = AuthFieldValidationState.None,
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                AuthPrimaryButton(
                    text = sendLabel,
                    onClick = {
                        val validationError = if (channel == RecoveryChannel.EMAIL) {
                            validateAuthEmail(email, emailRequiredText, emailInvalidText)
                        } else {
                            validateAuthPhone(phone, phoneRequiredText, phoneInvalidText)
                        }
                        fieldError = validationError
                        if (validationError != null) {
                            if (channel == RecoveryChannel.EMAIL) {
                                emailFocusRequester.requestFocus()
                            } else {
                                phoneFocusRequester.requestFocus()
                            }
                            return@AuthPrimaryButton
                        }

                        isLoading = true
                        errorText = null

                        scope.launch {
                            try {
                                successToken = if (channel == RecoveryChannel.EMAIL) {
                                    props.onSubmitEmail(email.trim())
                                } else {
                                    props.onSubmitPhone(phone.trim())
                                }
                                successChannel = channel
                            } catch (t: Throwable) {
                                errorText = t.message ?: errorTextDefault
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
                    onClick = { if (!isLoading) props.onBackToLogin() },
                    enabled = !isLoading,
                ) {
                    Text(
                        text = backLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryChannelSwitch(
    selected: RecoveryChannel,
    onSelected: (RecoveryChannel) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecoveryChannelButton(
                label = stringResource(R.string.profile_email_label),
                selected = selected == RecoveryChannel.EMAIL,
                onClick = { onSelected(RecoveryChannel.EMAIL) },
                modifier = Modifier.weight(1f),
            )
            RecoveryChannelButton(
                label = stringResource(R.string.profile_phone_label),
                selected = selected == RecoveryChannel.PHONE,
                onClick = { onSelected(RecoveryChannel.PHONE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecoveryChannelButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
