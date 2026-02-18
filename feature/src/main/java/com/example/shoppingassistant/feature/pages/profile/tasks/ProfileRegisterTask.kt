// Last synced: 2025-11-19 21:16
// FILE: feature/src/main/java/com/example/shoppingassistant/feature/pages/profile/tasks/ProfileRegisterTask.kt
// Last synced: 2025-12-06 XX:XX (updated by GPT)
// FILE: feature/src/main/java/com/example/shoppingassistant/feature/pages/profile/tasks/ProfileRegisterTask.kt
package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.feature.R
import kotlinx.coroutines.launch

@Composable
fun ProfileRegisterTask(
    onRegister: suspend (String, String, String?) -> AuthResult,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
    onLoginClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }

    val registerTitle = stringResource(R.string.profile_register_title)
    val registerSubtitle = stringResource(R.string.profile_register_subtitle)
    val nameLabel = stringResource(R.string.profile_name_optional_label)
    val namePlaceholder = stringResource(R.string.profile_name_placeholder)
    val emailLabel = stringResource(R.string.profile_email_label)
    val emailPlaceholder = stringResource(R.string.profile_email_placeholder)
    val passwordLabel = stringResource(R.string.profile_password_label)
    val passwordPlaceholder = stringResource(R.string.profile_password_placeholder)
    val registerButtonLabel = stringResource(R.string.profile_register_button)
    val loginLabel = stringResource(R.string.profile_has_account_login)
    val emailExistsText = stringResource(R.string.profile_register_email_exists)
    val registerFailedText = stringResource(R.string.profile_register_failed)
    val registerNetworkFailedText = stringResource(R.string.profile_register_network_failed)
    val emailRequiredText = stringResource(R.string.profile_auth_email_required)
    val emailInvalidText = stringResource(R.string.profile_auth_email_invalid)
    val passwordShortText = stringResource(R.string.profile_auth_password_short)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthIllustrationHeader(
                title = registerTitle,
                subtitle = registerSubtitle,
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AuthTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            errorText = null
                        },
                        label = nameLabel,
                        placeholder = namePlaceholder,
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                            )
                        },
                        isError = false,
                        errorText = null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        focusRequester = nameFocusRequester,
                    )

                    AuthTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = null
                            errorText = null
                        },
                        label = emailLabel,
                        placeholder = emailPlaceholder,
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = null,
                            )
                        },
                        isError = emailError != null,
                        errorText = emailError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        focusRequester = emailFocusRequester,
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                            errorText = null
                        },
                        label = passwordLabel,
                        placeholder = passwordPlaceholder,
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                            )
                        },
                        isError = passwordError != null,
                        errorText = passwordError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        focusRequester = passwordFocusRequester,
                        isPassword = true,
                        isPasswordVisible = passwordVisible,
                        onPasswordVisibilityChange = {
                            passwordVisible = !passwordVisible
                        },
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
                        text = registerButtonLabel,
                        onClick = {
                            val emailValidationError = validateAuthEmail(
                                email,
                                emailRequiredText,
                                emailInvalidText,
                            )
                            val passwordValidationError = validateAuthPassword(
                                password,
                                passwordShortText,
                            )

                            emailError = emailValidationError
                            passwordError = passwordValidationError

                            if (emailValidationError != null || passwordValidationError != null) {
                                when {
                                    emailValidationError != null ->
                                        emailFocusRequester.requestFocus()

                                    else ->
                                        passwordFocusRequester.requestFocus()
                                }
                                return@AuthPrimaryButton
                            }

                            isLoading = true
                            errorText = null

                            scope.launch {
                                val result = runCatching {
                                    val name = displayName.trim().ifEmpty { null }
                                    onRegister(email.trim(), password, name)
                                }.getOrNull()

                                isLoading = false

                                when (result) {
                                    is AuthResult.Success -> {
                                        onSuccess()
                                        onClose()
                                    }

                                    is AuthResult.Error -> {
                                        errorText = when (result.error) {
                                            AuthError.EMAIL_ALREADY_EXISTS ->
                                                emailExistsText

                                            else ->
                                                registerFailedText
                                        }
                                    }

                                    null -> {
                                        errorText =
                                            registerNetworkFailedText
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        isLoading = isLoading,
                    )

                    TextButton(
                        onClick = {
                            if (!isLoading) onLoginClick()
                        },
                        enabled = !isLoading,
                    ) {
                        Text(
                            text = loginLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
