// Modified version of ProfilePage with interactive bottom sheet
// Last updated: 2025-12-03
package com.example.shoppingassistant.feature.pages.profile


import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.R
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileAuthorizedBlock
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileBanner
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileErrorKind
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileForgotPasswordProps
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileForgotPasswordTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileLoginTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileRegisterTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileResetPasswordTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileStateTaskUiState
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.state.StateHost
import com.example.shoppingassistant.feature.ui.state.model.LoadingPhase
import com.example.shoppingassistant.feature.ui.state.model.OfflineMode
import com.example.shoppingassistant.feature.ui.state.model.ScreenState
import com.example.shoppingassistant.feature.ui.state.model.StateAction
import com.example.shoppingassistant.feature.ui.state.model.StateActionType
import com.example.shoppingassistant.feature.BuildConfig
import org.koin.androidx.compose.koinViewModel

/**
 * Корневой экран профиля.
 *
 * Переведён на ModalBottomSheet для того, чтобы профиль отображался
 * в виде одного интерактивного нижнего листа без отдельной статической страницы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    onLoginClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onMyItemsClick: () -> Unit = {},
    onTrackedItemsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onClose: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onPolicyClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
) {
    var showLogin by remember { mutableStateOf(false) }
    var showRegister by remember { mutableStateOf(false) }
    var showResetPassword by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    var prefilledResetToken by remember { mutableStateOf<String?>(null) }

    val viewModel: ProfileViewModel = koinViewModel()
    val ui: ProfileStateTaskUiState by viewModel.uiState.collectAsState()
    val state = ui.state
    val isLoading = ui.isLoading
    val error = ui.error

    // Локальные обработчики: открывают модальные формы и дергают внешние коллбеки
    val handleLoginClick: () -> Unit = {
        showLogin = true
        onLoginClick()
    }
    val handleRegisterClick: () -> Unit = {
        showRegister = true
        onRegisterClick()
    }
    val handleLogoutClick: () -> Unit = {
        viewModel.logout()
        onLogoutClick()
    }

    val closeSheet: () -> Unit = {
        showLogin = false
        showRegister = false
        showResetPassword = false
        showForgotPassword = false
        onClose()
    }

    val errorTitle = error?.let { issue ->
        when (issue.kind) {
            ProfileErrorKind.NETWORK -> stringResource(R.string.profile_error_network_title)
            ProfileErrorKind.UNKNOWN -> stringResource(R.string.profile_error_unknown_title)
        }
    }
    val errorMessage = error?.let { stringResource(it.messageResId) }
    val isAuthorized = state is ProfileState.Authorized
    val isOffline = isAuthorized && ui.banner == ProfileBanner.OFFLINE
    val contentPadding = PaddingValues(bottom = LayoutDefaults.ContentBottomSpacing)

    val screenState = when {
        isLoading -> ScreenState.Loading(LoadingPhase.INITIAL)
        error != null && errorTitle != null && errorMessage != null -> ScreenState.Error(
            title = errorTitle,
            message = errorMessage,
            primaryAction = StateAction(
                label = stringResource(R.string.state_error_retry),
                onAction = { viewModel.reload() },
                type = StateActionType.RETRY,
            ),
            icon = Icons.Outlined.Person,
        )
        isOffline -> ScreenState.Offline(
            mode = OfflineMode.WITH_CACHE,
            primaryAction = StateAction(
                label = stringResource(R.string.state_offline_banner_action),
                onAction = { viewModel.reload() },
                type = StateActionType.RETRY,
            ),
        )
        else -> ScreenState.Content(isStale = ui.isStale)
    }

    // Состояние для bottom sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = closeSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = closeSheet) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.profile_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                StateHost(
                    state = screenState,
                    contentPadding = contentPadding,
                    screenName = "profile",
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        val authorized = state as? ProfileState.Authorized
                        if (authorized != null) {
                            ProfileAuthorizedBlock(
                                profile = authorized,
                                onLogoutClick = handleLogoutClick,
                                onMyItemsClick = onMyItemsClick,
                                onTrackedItemsClick = onTrackedItemsClick,
                                onProfileChanged = { viewModel.reload() },
                                viewModel = viewModel,
                            )
                        } else {
                            ProfileGuestBlock(
                                onLoginClick = handleLoginClick,
                                onHelpClick = onHelpClick,
                                onPolicyClick = onPolicyClick,
                                onAboutClick = onAboutClick,
                                onDebugLoginClick = if (BuildConfig.DEBUG) viewModel::debugLogin else null,
                            )
                        }
                    }
                }

                // Анимация формы логина — форма приезжает снизу и уезжает вверх.
                // Используем FQN для AnimatedVisibility, чтобы избежать коллизии с ColumnScope.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLogin,
                    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(260)),
                    exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(animationSpec = tween(200)),
                ) {
                    ProfileLoginTask(
                        onLogin = { email, password -> viewModel.login(email, password) },
                        onSuccess = { viewModel.reload() },
                        onClose = { showLogin = false },
                        onForgotPasswordClick = {
                            showLogin = false
                            showForgotPassword = true
                            showResetPassword = false
                            prefilledResetToken = null
                        },
                        onSignUpClick = {
                            showLogin = false
                            handleRegisterClick()
                        },
                    )
                }

                // Модальное окно для запроса reset-токена
                androidx.compose.animation.AnimatedVisibility(
                    visible = showForgotPassword,
                    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(260)),
                    exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(animationSpec = tween(200)),
                ) {
                    ProfileForgotPasswordTask(
                        props = ProfileForgotPasswordProps(
                            onSubmit = { email -> viewModel.startPasswordReset(email) },
                            onClose = {
                                showForgotPassword = false
                                if (!showResetPassword) showLogin = true
                            },
                            onResetTokenReceived = { token ->
                                prefilledResetToken = token
                                showForgotPassword = false
                                if (token != null) {
                                    showResetPassword = true
                                } else {
                                    showLogin = true
                                }
                            },
                        ),
                    )
                }

                // Модальное окно для ввода нового пароля по reset-token
                androidx.compose.animation.AnimatedVisibility(
                    visible = showResetPassword,
                    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(260)),
                    exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(animationSpec = tween(200)),
                ) {
                    ProfileResetPasswordTask(
                        onResetPassword = { token, newPassword ->
                            viewModel.resetPassword(resetToken = token, newPassword = newPassword)
                        },
                        onSuccess = {
                            prefilledResetToken = null
                            showResetPassword = false
                            showForgotPassword = false
                            showRegister = false
                            showLogin = true
                        },
                        onClose = {
                            prefilledResetToken = null
                            showResetPassword = false
                        },
                        initialToken = prefilledResetToken,
                    )
                }

                // Анимация формы регистрации
                androidx.compose.animation.AnimatedVisibility(
                    visible = showRegister,
                    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(260)),
                    exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(animationSpec = tween(200)),
                ) {
                    ProfileRegisterTask(
                        onRegister = { email, password, name -> viewModel.register(email, password, name) },
                        onSuccess = { viewModel.reload() },
                        onClose = { showRegister = false },
                        onLoginClick = {
                            showRegister = false
                            handleLoginClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileGuestBlock(
    onLoginClick: () -> Unit,
    onHelpClick: () -> Unit,
    onPolicyClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDebugLoginClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.profile_guest_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuestValueRow(icon = Icons.Outlined.StarOutline, text = stringResource(R.string.profile_guest_value_subscriptions))
                GuestValueRow(icon = Icons.Outlined.History, text = stringResource(R.string.profile_guest_value_history))
                GuestValueRow(icon = Icons.Outlined.Shield, text = stringResource(R.string.profile_guest_value_trust))
                GuestValueRow(icon = Icons.Outlined.Sync, text = stringResource(R.string.profile_guest_value_sync))
            }
        }

        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.profile_unauth_login))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onHelpClick) { Text(stringResource(R.string.profile_guest_help)) }
            TextButton(onClick = onPolicyClick) { Text(stringResource(R.string.profile_guest_policy)) }
            TextButton(onClick = onAboutClick) { Text(stringResource(R.string.profile_guest_about)) }
        }

        if (onDebugLoginClick != null) {
            Spacer(modifier = Modifier.heightIn(min = 4.dp))
            TextButton(onClick = onDebugLoginClick) {
                Text(stringResource(R.string.profile_guest_debug_login))
            }
        }
    }
}

@Composable
private fun GuestValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
