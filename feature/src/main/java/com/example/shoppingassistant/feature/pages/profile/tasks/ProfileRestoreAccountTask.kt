package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ProfileRestoreAccountTask(
    onRestore: suspend (String) -> Unit,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tokenFocusRequester = remember { FocusRequester() }

    var token by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthIllustrationHeader(
                title = "Восстановление аккаунта",
                subtitle = "Введите токен восстановления, который вы сохранили при удалении аккаунта.",
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
                        value = token,
                        onValueChange = {
                            token = it
                            fieldError = null
                            errorText = null
                        },
                        label = "Токен восстановления",
                        placeholder = "Вставьте токен из сохранённого экрана удаления",
                        isError = fieldError != null,
                        errorText = fieldError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        focusRequester = tokenFocusRequester,
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    AuthPrimaryButton(
                        text = "Восстановить аккаунт",
                        onClick = {
                            val trimmedToken = token.trim()
                            if (trimmedToken.isBlank()) {
                                fieldError = "Введите токен восстановления"
                                tokenFocusRequester.requestFocus()
                                return@AuthPrimaryButton
                            }

                            isLoading = true
                            errorText = null
                            scope.launch {
                                runCatching { onRestore(trimmedToken) }
                                    .onSuccess {
                                        onSuccess()
                                        onClose()
                                    }
                                    .onFailure { throwable ->
                                        errorText = throwable.message ?: "Не удалось восстановить аккаунт"
                                    }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        isLoading = isLoading,
                    )

                    TextButton(onClick = onBackToLogin, enabled = !isLoading) {
                        Text("Вернуться ко входу")
                    }
                    TextButton(onClick = onClose, enabled = !isLoading) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}
