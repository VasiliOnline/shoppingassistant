package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button

import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shoppingassistant.feature.pages.profile.ProfileState
import com.example.shoppingassistant.feature.R

/**
 * Dialog for editing personal data. This dialog replaces the bottom sheet used
 * previously for personal data editing. It appears centered on the screen
 * with enough width to hold all fields and controls. When visible, it dims
 * the background to focus the user's attention.
 *
 * @param profile current authorized profile state. Only the email and phone
 * values are used from this object.
 * @param onClose invoked when the dialog should be dismissed.
 * @param onPhoneChanged invoked when the phone number is changed. The new
 * phone value is passed back to the caller. Default is a no‑op.
 * @param onPasswordChanged invoked when the user successfully changes
 * their password. The new password is passed back to the caller. Default
 * is a no‑op.
 * @param onRequestDelete invoked when the user taps the delete account
 * button. Default is a no‑op.
 */
@Composable
fun ProfilePersonalDataDialog(
    profile: ProfileState.Authorized,
    onClose: () -> Unit,
    onPhoneChanged: (String) -> Unit = {},
    onPasswordChanged: (String) -> Unit = {},
    onRequestDelete: () -> Unit = {},
) {
    // Local state for editing flags
    var editingEmail by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf(false) }

    // Current email and phone values from the profile. We copy them into
    // mutable state to allow in‑dialog updates without mutating profile directly.
    var currentEmail by remember(profile.id) { mutableStateOf(profile.email) }
    var phone by remember(profile.id) { mutableStateOf(profile.phone.orEmpty()) }

    // Fields for editing email
    var newEmail by remember { mutableStateOf(currentEmail) }
    var emailError by remember { mutableStateOf<String?>(null) }

    // Fields for editing password
    var currentPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The outer box dims the background. The blur effect is approximated
        // by a semi‑transparent overlay because true blur is not available.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            // Card containing all personal data sections
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Title bar with close button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.profile_personal_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.profile_close),
                            )
                        }
                    }

                    // Email section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_personal_email_label),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Show current email in a pill with light background
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = currentEmail.ifBlank { stringResource(R.string.profile_personal_not_set) },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { editingEmail = !editingEmail },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(stringResource(R.string.profile_personal_change))
                            }
                        }
                        if (editingEmail) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val emailRequiredError = stringResource(R.string.profile_auth_email_required)
                                val emailInvalidError = stringResource(R.string.profile_email_change_invalid)
                                TextField(
                                    value = newEmail,
                                    onValueChange = {
                                        newEmail = it
                                        emailError = null
                                    },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.profile_email_placeholder)) },
                                    isError = emailError != null,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.3f
                                        ),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.25f
                                        ),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        errorIndicatorColor = Color.Transparent,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (emailError != null) {
                                    Text(
                                        text = emailError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            emailError = validateAuthEmail(
                                                newEmail,
                                                emailRequiredError,
                                                emailInvalidError,
                                            )
                                            if (emailError == null) {
                                                currentEmail = newEmail.trim()
                                                editingEmail = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.profile_email_change_save))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingEmail = false
                                            newEmail = currentEmail
                                            emailError = null
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.profile_links_cancel_action))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    // Password section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_personal_password_label),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Disabled password field showing stars
                            TextField(
                                value = "********",
                                onValueChange = {},
                                enabled = false,
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    ),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.25f
                                    ),
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.25f
                                    ),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { editingPassword = !editingPassword },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.secondary,
                                ),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(stringResource(R.string.profile_password_change_title))
                            }
                        }
                        if (editingPassword) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextField(
                                    value = currentPasswordInput,
                                    onValueChange = {
                                        currentPasswordInput = it
                                    },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.profile_password_current_placeholder)) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.3f
                                        ),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.25f
                                        ),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextField(
                                    value = newPasswordInput,
                                    onValueChange = {
                                        newPasswordInput = it
                                        passwordError = null
                                    },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.profile_password_new_placeholder)) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    isError = passwordError != null,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.3f
                                        ),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.25f
                                        ),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        errorIndicatorColor = Color.Transparent,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (passwordError != null) {
                                    Text(
                                        text = passwordError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val passwordMinErrorText = stringResource(R.string.profile_password_new_min)
                                    val passwordRequirementsErrorText = stringResource(R.string.profile_password_requirements)

                                    Button(
                                        onClick = {
                                            val pwd = newPasswordInput
                                            val hasLetters = pwd.any { it.isLetter() }
                                            val hasDigits = pwd.any { it.isDigit() }

                                            passwordError = when {
                                                pwd.length < 8 -> passwordMinErrorText
                                                !hasLetters || !hasDigits -> passwordRequirementsErrorText
                                                else -> null
                                            }

                                            if (passwordError == null) {
                                                onPasswordChanged(pwd)
                                                editingPassword = false
                                                currentPasswordInput = ""
                                                newPasswordInput = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.profile_password_save))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingPassword = false
                                            currentPasswordInput = ""
                                            newPasswordInput = ""
                                            passwordError = null
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.profile_password_cancel))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    // Phone section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_personal_phone_label),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = phone.ifBlank { stringResource(R.string.profile_personal_not_set) },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = {
                                    onPhoneChanged(phone)
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.profile_personal_change_phone))
                            }
                        }
                    }

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    // Delete account button with soft red background
                    Button(
                        onClick = { onRequestDelete() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_personal_delete_account))
                    }
                }
            }
        }
    }
}
