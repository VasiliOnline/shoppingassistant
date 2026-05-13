package com.example.shoppingassistant.feature.pages.profile

import android.content.ClipData
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.shoppingassistant.core.data.auth.VerificationDeliveryStatus
import com.example.shoppingassistant.core.push.NotificationPriority
import com.example.shoppingassistant.core.push.TrackingNotificationsSettings
import com.example.shoppingassistant.core.push.TrackingPushNotifier
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.profile.DeleteAccountReceipt
import com.example.shoppingassistant.domain.profile.ProfileEntryMode
import com.example.shoppingassistant.domain.profile.ProfileListingPreviewItem
import com.example.shoppingassistant.domain.profile.ProfileVerificationBadge
import com.example.shoppingassistant.domain.profile.ProfileView
import com.example.shoppingassistant.domain.profile.ProfileViewScope
import com.example.shoppingassistant.domain.profile.activeDeliveryAddress
import com.example.shoppingassistant.domain.subscriptions.SubscriptionNotification
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileForgotPasswordProps
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileForgotPasswordTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileCatalogGovernanceScreen
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileLoginTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileRegisterTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileRestoreAccountTask
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileResetPasswordTask
import com.example.shoppingassistant.feature.pages.profile.tasks.ProfileSettingsPage
import com.example.shoppingassistant.feature.pages.profile.tasks.validateAuthEmail
import com.example.shoppingassistant.feature.pages.profile.tasks.validateAuthPassword
import com.example.shoppingassistant.feature.pages.profile.tasks.validateAuthPhone
import com.example.shoppingassistant.feature.pages.useroffers.UserOffersTab
import com.example.shoppingassistant.feature.ui.layout.AppTopBar
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.layout.ScreenRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class ProfileMode {
    PUBLIC,
    ACCOUNT,
}

private enum class ProfileSurface {
    ROOT,
    EDIT_PUBLIC,
    ACCOUNT_DATA,
    APP_SETTINGS,
    DELIVERY_ADDRESSES,
    SELLER_DELIVERY_ZONES,
    NOTIFICATIONS_CENTER,
    NOTIFICATION_SETTINGS,
    CATALOG_GOVERNANCE,
    PRIVACY,
    ABOUT,
}

private enum class AuthOverlay {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
    RESTORE_ACCOUNT,
}

private enum class AccountDialog {
    EMAIL,
    PHONE,
    PASSWORD,
}

private fun ProfileMode.toEntryMode(): ProfileEntryMode = when (this) {
    ProfileMode.PUBLIC -> ProfileEntryMode.PUBLIC
    ProfileMode.ACCOUNT -> ProfileEntryMode.ACCOUNT
}

private fun ProfileEntryMode.toProfileMode(): ProfileMode = when (this) {
    ProfileEntryMode.PUBLIC -> ProfileMode.PUBLIC
    ProfileEntryMode.ACCOUNT -> ProfileMode.ACCOUNT
}

private val ProfileInk = Color(0xFF121722)
private val ProfileMutedInk = Color(0xFF667085)
private val ProfileStroke = Color(0xFFE5E7EB)
private val ProfileCanvas = Color(0xFFF7F8FA)
private val ProfileBlue = Color(0xFF2563EB)
private val ProfileBlueCanvas = Color(0xFFEFF4FF)
private val ProfileDanger = Color(0xFFDC2626)
private val ProfileDangerCanvas = Color(0xFFFEF2F2)

@Composable
private fun profileOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ProfileInk,
    unfocusedTextColor = ProfileInk,
    focusedLabelColor = ProfileBlue,
    unfocusedLabelColor = ProfileMutedInk,
    cursorColor = ProfileBlue,
    focusedBorderColor = ProfileBlue.copy(alpha = 0.36f),
    unfocusedBorderColor = ProfileStroke,
    disabledBorderColor = ProfileStroke,
    focusedSupportingTextColor = ProfileMutedInk,
    unfocusedSupportingTextColor = ProfileMutedInk,
    errorBorderColor = ProfileDanger,
    errorLabelColor = ProfileDanger,
    errorSupportingTextColor = ProfileDanger,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    errorContainerColor = Color.White,
)

@Composable
private fun ProfileSectionCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    danger: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = when {
        danger -> ProfileDanger.copy(alpha = 0.22f)
        emphasized -> ProfileBlue.copy(alpha = 0.14f)
        else -> ProfileStroke
    }
    val containerColor = when {
        danger -> ProfileDangerCanvas
        else -> Color.White
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (emphasized) 24.dp else 22.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (emphasized) 3.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun ProfileStatusPill(
    text: String,
    highlighted: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) {
            ProfileBlueCanvas
        } else {
            ProfileCanvas
        },
        border = BorderStroke(
            1.dp,
            if (highlighted) {
                ProfileBlue.copy(alpha = 0.18f)
            } else {
                ProfileStroke
            },
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) ProfileBlue else ProfileMutedInk,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProfileInsetGroup(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

@Composable
fun ProfilePage(
    targetUserId: Long? = null,
    onBack: () -> Unit = {},
    onOpenMyItems: (UserOffersTab) -> Unit = {},
    onOpenTrackedItems: () -> Unit = {},
    onOpenSellerListings: (sellerId: Long, sellerName: String?) -> Unit = { _, _ -> },
    onOpenOffer: (offerId: String) -> Unit = {},
) {
    val viewModel: AccountProfileViewModel = koinViewModel()
    val ui by viewModel.uiState.collectAsState()
    var mode by rememberSaveable(targetUserId) { mutableStateOf(ProfileMode.PUBLIC) }
    var surface by rememberSaveable(targetUserId) { mutableStateOf(ProfileSurface.ROOT) }
    var authOverlay by rememberSaveable { mutableStateOf<AuthOverlay?>(null) }
    var resetToken by rememberSaveable { mutableStateOf<String?>(null) }
    var modeRestored by rememberSaveable(targetUserId) { mutableStateOf(false) }
    val rootListState = rememberSaveable(targetUserId, saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(targetUserId) {
        mode = ProfileMode.PUBLIC
        surface = ProfileSurface.ROOT
        authOverlay = null
        resetToken = null
        modeRestored = false
        viewModel.load(targetUserId)
    }

    val content = ui.content
    val ready = content as? ProfileRouteContent.Ready
    val ownerContext = ready?.ownerContext
    val isOwnerView = ownerContext != null && ready.profile.scope == ProfileViewScope.OWNER
    val savedEntryMode = ownerContext?.appSettings?.profileEntryMode ?: ProfileEntryMode.PUBLIC

    LaunchedEffect(isOwnerView) {
        if (!isOwnerView) {
            mode = ProfileMode.PUBLIC
            if (surface != ProfileSurface.ROOT) {
                surface = ProfileSurface.ROOT
            }
        }
    }

    LaunchedEffect(targetUserId, ready?.profile?.scope, savedEntryMode) {
        if (!modeRestored && (targetUserId != null || ready != null)) {
            mode = resolveProfileEntryMode(
                targetUserId = targetUserId,
                profileScope = ready?.profile?.scope,
                savedMode = savedEntryMode,
            ).toProfileMode()
            modeRestored = true
        }
    }

    BackHandler(enabled = authOverlay != null || surface != ProfileSurface.ROOT) {
        when {
            authOverlay != null -> {
                authOverlay = null
                resetToken = null
            }
            surface != ProfileSurface.ROOT -> surface = profileBackSurface(surface)
        }
    }

    ScreenRoot(
        topBar = {
            AppTopBar(
                title = profileTopBarTitle(surface, ready, mode),
                onBack = if (surface == ProfileSurface.ROOT) onBack else { { surface = profileBackSurface(surface) } },
                trailingContent = {
                    if (surface == ProfileSurface.ROOT) {
                        if (ui.isStale) {
                            AssistChip(
                                onClick = { viewModel.reload() },
                                label = { Text("Офлайн-кэш") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                        if (ready != null) {
                            IconButton(onClick = { viewModel.reload() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Обновить профиль",
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        when (surface) {
            ProfileSurface.ROOT -> ProfileRootContent(
                padding = contentPadding,
                ui = ui,
                mode = mode,
                listState = rootListState,
                onModeChange = {
                    mode = it
                    if (targetUserId == null && isOwnerView) {
                        viewModel.rememberProfileEntryMode(it.toEntryMode())
                    }
                },
                onOpenLogin = { authOverlay = AuthOverlay.LOGIN },
                onOpenRegister = { authOverlay = AuthOverlay.REGISTER },
                onOpenRestoreAccount = { authOverlay = AuthOverlay.RESTORE_ACCOUNT },
                onOpenEditPublic = { surface = ProfileSurface.EDIT_PUBLIC },
                onOpenSellerListings = onOpenSellerListings,
                onOpenOffer = onOpenOffer,
                onOpenActiveListings = { onOpenMyItems(UserOffersTab.ACTIVE) },
                onOpenDrafts = { onOpenMyItems(UserOffersTab.DRAFTS) },
                onOpenTrackedItems = onOpenTrackedItems,
                onOpenNotificationsCenter = { surface = ProfileSurface.NOTIFICATIONS_CENTER },
                onOpenNotificationSettings = { surface = ProfileSurface.NOTIFICATION_SETTINGS },
                onOpenAccountData = { surface = ProfileSurface.ACCOUNT_DATA },
                onOpenAppSettings = { surface = ProfileSurface.APP_SETTINGS },
                onOpenPrivacy = { surface = ProfileSurface.PRIVACY },
                onOpenAbout = { surface = ProfileSurface.ABOUT },
                onLogout = viewModel::logout,
                onReload = viewModel::reload,
            )
            ProfileSurface.EDIT_PUBLIC -> ProfileEditPublicSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.ACCOUNT_DATA -> ProfileAccountDataSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.APP_SETTINGS -> ProfileAppSettingsSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.DELIVERY_ADDRESSES -> ProfileBuyerDeliveryAddressesSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.SELLER_DELIVERY_ZONES -> ProfileSellerDeliveryZonesSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.NOTIFICATIONS_CENTER -> ProfileNotificationsSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.NOTIFICATION_SETTINGS -> ProfileNotificationSettingsSlot(ready, contentPadding, viewModel) { surface = it }
            ProfileSurface.CATALOG_GOVERNANCE -> ProfileCatalogGovernanceScreen(
                contentPadding = contentPadding,
                onBack = { surface = ProfileSurface.APP_SETTINGS },
            )
            ProfileSurface.PRIVACY -> ProfilePrivacySlot(ready, contentPadding, viewModel, onBack) { surface = it }
            ProfileSurface.ABOUT -> AboutProfileScreen(
                contentPadding = contentPadding,
                onBack = { surface = ProfileSurface.ROOT },
            )
        }
    }

    when (authOverlay) {
        AuthOverlay.LOGIN -> ProfileLoginTask(
            onLogin = { email, password -> viewModel.login(email, password) },
            onSuccess = { viewModel.reload() },
            onClose = { authOverlay = null },
            onForgotPasswordClick = { authOverlay = AuthOverlay.FORGOT_PASSWORD },
            onSignUpClick = { authOverlay = AuthOverlay.REGISTER },
        )
        AuthOverlay.REGISTER -> ProfileRegisterTask(
            onRegister = { email, password, name -> viewModel.register(email, password, name) },
            onSuccess = { viewModel.reload() },
            onClose = { authOverlay = null },
            onLoginClick = { authOverlay = AuthOverlay.LOGIN },
        )
        AuthOverlay.FORGOT_PASSWORD -> ProfileForgotPasswordTask(
            props = ProfileForgotPasswordProps(
                onSubmitEmail = { email -> viewModel.startPasswordReset(email) },
                onSubmitPhone = { phone -> viewModel.startPasswordResetByPhone(phone) },
                onClose = { authOverlay = null },
                onContinueToReset = { token ->
                    resetToken = token
                    authOverlay = AuthOverlay.RESET_PASSWORD
                },
                onBackToLogin = { authOverlay = AuthOverlay.LOGIN },
            ),
        )
        AuthOverlay.RESET_PASSWORD -> ProfileResetPasswordTask(
            onResetPassword = { token, newPassword ->
                viewModel.resetPassword(resetToken = token, newPassword = newPassword)
            },
            onSuccess = {
                resetToken = null
                authOverlay = AuthOverlay.LOGIN
            },
            onClose = {
                resetToken = null
                authOverlay = null
            },
            initialToken = resetToken,
        )
        AuthOverlay.RESTORE_ACCOUNT -> ProfileRestoreAccountTask(
            onRestore = viewModel::restoreDeletedAccount,
            onSuccess = { viewModel.reload() },
            onClose = { authOverlay = null },
            onBackToLogin = { authOverlay = AuthOverlay.LOGIN },
        )
        null -> Unit
    }
}

@Composable
private fun ProfileEditPublicSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Редактирование недоступно",
            message = "Редактирование доступно только владельцу профиля после входа в аккаунт.",
            onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        )
        return
    }
    EditPublicProfileScreen(
        profile = ready.profile,
        contentPadding = contentPadding,
        onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        onSave = { displayName, bio, website, city, avatarUrl ->
            viewModel.savePublicProfile(displayName, bio, website, city, avatarUrl)
        },
        onUploadPhoto = viewModel::uploadPhoto,
    )
}

@Composable
private fun ProfileAccountDataSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Данные аккаунта недоступны",
            message = unavailableSectionMessage("данные аккаунта"),
            onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        )
        return
    }
    AccountDataScreen(
        authUser = ready.ownerContext.authUser,
        contentPadding = contentPadding,
        onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        onRequestEmailChange = viewModel::requestEmailChange,
        onConfirmEmailChange = viewModel::confirmEmailChange,
        onStartEmailVerification = viewModel::startEmailVerification,
        onConfirmEmailVerification = viewModel::confirmEmailVerification,
        onStartPhoneChange = viewModel::startPhoneChange,
        onConfirmPhoneChange = viewModel::confirmPhoneChange,
        onStartPhoneVerification = viewModel::startPhoneVerification,
        onConfirmPhoneVerification = viewModel::confirmPhoneVerification,
        onChangePassword = viewModel::changePassword,
    )
}

@Composable
private fun ProfileAppSettingsSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Настройки недоступны",
            message = unavailableSectionMessage("настройки аккаунта"),
            onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        )
        return
    }
    ProfileSettingsPage(
        settings = ready.ownerContext.appSettings,
        onSettingsChange = viewModel::updateAppSettings,
        onBackClick = { onSurfaceChange(ProfileSurface.ROOT) },
        showEmbeddedHeader = false,
        onOpenDeliveryAddresses = {
            onSurfaceChange(ProfileSurface.DELIVERY_ADDRESSES)
        },
        onOpenSellerDeliveryZones = {
            onSurfaceChange(ProfileSurface.SELLER_DELIVERY_ZONES)
        },
        activeDeliveryAddressSummary = ready.ownerContext.appSettings.activeDeliveryAddress()
            ?.label
            ?: "Активный адрес пока не выбран",
        sellerDeliveryZonesCount = ready.profile.sellerDeliveryZones.size,
        onOpenCatalogGovernance = {
            onSurfaceChange(ProfileSurface.CATALOG_GOVERNANCE)
        },
    )
}

@Composable
private fun ProfileBuyerDeliveryAddressesSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Адреса доставки недоступны",
            message = unavailableSectionMessage("адреса доставки"),
            onBack = { onSurfaceChange(ProfileSurface.APP_SETTINGS) },
        )
        return
    }
    BuyerDeliveryAddressesScreen(
        settings = ready.ownerContext.appSettings,
        contentPadding = contentPadding,
        onSettingsChange = viewModel::updateAppSettings,
    )
}

@Composable
private fun ProfileSellerDeliveryZonesSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Зоны доставки недоступны",
            message = unavailableSectionMessage("зоны доставки"),
            onBack = { onSurfaceChange(ProfileSurface.APP_SETTINGS) },
        )
        return
    }
    SellerDeliveryZonesScreen(
        zones = ready.profile.sellerDeliveryZones,
        contentPadding = contentPadding,
        onSaveZones = viewModel::updateSellerDeliveryZones,
    )
}

@Composable
private fun ProfileNotificationsSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Уведомления недоступны",
            message = unavailableSectionMessage("уведомления"),
            onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        )
        return
    }
    NotificationsCenterScreen(
        notifications = ready.ownerContext.notificationsPage.items,
        totalCount = ready.ownerContext.notificationsPage.total,
        unreadCount = ready.ownerContext.activityHub.notificationsUnread,
        contentPadding = contentPadding,
        onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        onMarkRead = viewModel::markNotificationRead,
        onMarkAllRead = viewModel::markAllNotificationsRead,
    )
}

@Composable
private fun ProfileNotificationSettingsSlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Настройки уведомлений недоступны",
            message = unavailableSectionMessage("настройки уведомлений"),
            onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        )
        return
    }
    NotificationSettingsScreen(
        settings = ready.ownerContext.pushSettings,
        contentPadding = contentPadding,
        onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        onSave = viewModel::updatePushSettings,
    )
}

@Composable
private fun ProfilePrivacySlot(
    ready: ProfileRouteContent.Ready?,
    contentPadding: PaddingValues,
    viewModel: AccountProfileViewModel,
    onRootBack: () -> Unit,
    onSurfaceChange: (ProfileSurface) -> Unit,
) {
    if (ready == null || ready.ownerContext == null) {
        ProfileUnavailableSubscreen(
            contentPadding = contentPadding,
            title = "Приватность недоступна",
            message = unavailableSectionMessage("настройки приватности"),
            onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        )
        return
    }
    PrivacySafetyScreen(
        profile = ready.profile,
        contentPadding = contentPadding,
        onBack = { onSurfaceChange(ProfileSurface.ROOT) },
        onSavePrivacy = viewModel::updatePrivacy,
        onDeleteAccount = {
            viewModel.deleteAccount(it)
        },
        onDeleteCompleted = {
            viewModel.logout()
            onRootBack()
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileRootContent(
    padding: PaddingValues,
    ui: AccountProfileUiState,
    mode: ProfileMode,
    listState: LazyListState,
    onModeChange: (ProfileMode) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenRestoreAccount: () -> Unit,
    onOpenEditPublic: () -> Unit,
    onOpenSellerListings: (sellerId: Long, sellerName: String?) -> Unit,
    onOpenOffer: (offerId: String) -> Unit,
    onOpenActiveListings: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenTrackedItems: () -> Unit,
    onOpenNotificationsCenter: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccountData: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
    onReload: () -> Unit,
) {
    val ready = ui.content as? ProfileRouteContent.Ready
    if (ui.isLoading && ready == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    when (val content = ui.content) {
        ProfileRouteContent.Guest -> GuestProfileScreen(
            contentPadding = padding,
            noticeMessage = ui.guestNotice,
            onOpenLogin = onOpenLogin,
            onOpenRegister = onOpenRegister,
            onOpenRestoreAccount = onOpenRestoreAccount,
        )
        ProfileRouteContent.Error -> ProfileStatusScreen(
            contentPadding = padding,
            icon = Icons.Outlined.ErrorOutline,
            title = "Профиль не загрузился",
            message = ui.errorMessage ?: "Не удалось загрузить данные профиля. Можно повторить загрузку.",
            tone = SystemNoticeTone.Error,
            primaryLabel = "Повторить",
            onPrimary = onReload,
        )
        is ProfileRouteContent.NotFound -> ProfileStatusScreen(
            contentPadding = padding,
            icon = Icons.Outlined.Person,
            title = "Профиль не найден",
            message = profileNotFoundMessage(content.targetUserId),
            tone = SystemNoticeTone.Info,
            primaryLabel = "Обновить",
            onPrimary = onReload,
        )
        is ProfileRouteContent.PrivateUnavailable -> ProfileStatusScreen(
            contentPadding = padding,
            icon = Icons.Outlined.Shield,
            title = "Публичный профиль скрыт",
            message = privateProfileUnavailableMessage(),
            tone = SystemNoticeTone.Warning,
            primaryLabel = "Обновить",
            onPrimary = onReload,
        )
        is ProfileRouteContent.Ready -> {
            val owner = content.ownerContext
            val showAccountTab = owner != null && content.profile.scope == ProfileViewScope.OWNER
            LaunchedEffect(mode) {
                listState.scrollToItem(0)
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = LayoutDefaults.HorizontalPadding,
                    end = LayoutDefaults.HorizontalPadding,
                    top = LayoutDefaults.SectionSpacing,
                    bottom = padding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (ui.isStale) {
                    item("stale-banner") {
                        StatusBanner(
                            title = "Показываем сохранённую версию",
                            message = staleProfileBannerMessage(),
                            tone = SystemNoticeTone.Warning,
                        )
                    }
                }

                item("mode-content") {
                    AnimatedContent(
                        targetState = mode,
                        label = "profile-mode-content",
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { it / 16 }) togetherWith
                                (fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(160)) { -it / 20 })
                        },
                    ) { currentMode ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when {
                                currentMode == ProfileMode.ACCOUNT && owner != null -> {
                                    OwnerIdentityHeroCard(
                                        profile = content.profile,
                                        owner = owner,
                                        selectedMode = currentMode,
                                        onModeChange = onModeChange,
                                        onOpenEdit = onOpenEditPublic,
                                        onOpenListings = onOpenActiveListings,
                                        onOpenAccountData = onOpenAccountData,
                                    )
                                    AccountHealthCard(
                                        state = owner.accountState,
                                        onOpenAccountData = onOpenAccountData,
                                        onOpenEditPublic = onOpenEditPublic,
                                        onOpenPrivacy = onOpenPrivacy,
                                        onOpenListings = onOpenActiveListings,
                                    )
                                    if (owner.accountState.isEmptyModules) {
                                        EmptyAccountModulesCard()
                                    }
                                    ActivityHubCard(
                                        owner = owner,
                                        onOpenActiveListings = onOpenActiveListings,
                                        onOpenDrafts = onOpenDrafts,
                                        onOpenTrackedItems = onOpenTrackedItems,
                                        onOpenNotifications = onOpenNotificationsCenter,
                                    )
                                    AccountSettingsCard(
                                        profile = content.profile,
                                        owner = owner,
                                        onOpenEditPublic = onOpenEditPublic,
                                        onOpenAccountData = onOpenAccountData,
                                        onOpenAppSettings = onOpenAppSettings,
                                        onOpenNotificationSettings = onOpenNotificationSettings,
                                        onOpenPrivacy = onOpenPrivacy,
                                        onOpenAbout = onOpenAbout,
                                        onLogout = onLogout,
                                    )
                                }

                                else -> {
                                    PublicIdentityCard(
                                        profile = content.profile,
                                        isOwner = showAccountTab,
                                        selectedMode = currentMode,
                                        onModeChange = onModeChange,
                                        onOpenEdit = onOpenEditPublic,
                                        onOpenListings = {
                                            onOpenSellerListings(content.profile.userId, content.profile.displayName)
                                        },
                                    )
                                    TrustCard(profile = content.profile)
                                    ListingsPreviewCard(
                                        profile = content.profile,
                                        isOwner = showAccountTab,
                                        onOpenListings = {
                                            onOpenSellerListings(content.profile.userId, content.profile.displayName)
                                        },
                                        onOpenOffer = onOpenOffer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestProfileScreen(
    contentPadding: PaddingValues,
    noticeMessage: String?,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenRestoreAccount: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = LayoutDefaults.HorizontalPadding,
            end = LayoutDefaults.HorizontalPadding,
            top = LayoutDefaults.SectionSpacing,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!noticeMessage.isNullOrBlank()) {
            item("guest-notice") {
                StatusBanner(
                    title = "Авторизация сброшена",
                    message = noticeMessage,
                    tone = SystemNoticeTone.Warning,
                )
            }
        }
        item {
            ProfileSectionCard(emphasized = true) {
                Surface(
                    shape = CircleShape,
                    color = ProfileCanvas,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Text(
                    text = "Аккаунт и профиль",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "После входа вы сможете управлять объявлениями, черновиками, настройками приложения и безопасностью аккаунта.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ProfileMutedInk,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuestValueRow(Icons.Outlined.Inventory2, "Управление своими объявлениями и черновиками")
                    GuestValueRow(Icons.Outlined.TrackChanges, "Отслеживание цен и важных изменений")
                    GuestValueRow(Icons.Outlined.Notifications, "Центр уведомлений и push-настройки")
                    GuestValueRow(Icons.Outlined.Security, "Приватность, безопасность и удаление аккаунта")
                }
                Button(
                    onClick = onOpenLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Войти")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenRegister) {
                        Text("Создать аккаунт")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onOpenRestoreAccount) {
                        Text("Восстановить доступ")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileModeSwitch(
    selected: ProfileMode,
    onSelected: (ProfileMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ModeButton(
                label = "Публичный",
                selected = selected == ProfileMode.PUBLIC,
                onClick = { onSelected(ProfileMode.PUBLIC) },
                modifier = Modifier.weight(1f),
            )
            ModeButton(
                label = "Аккаунт",
                selected = selected == ProfileMode.ACCOUNT,
                onClick = { onSelected(ProfileMode.ACCOUNT) },
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ProfileStroke),
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val underlineWidth by animateDpAsState(
        targetValue = if (selected) 52.dp else 24.dp,
        animationSpec = tween(durationMillis = 200),
        label = "mode_underline_width",
    )
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) ProfileInk else ProfileMutedInk,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) ProfileBlue else Color.Transparent),
        )
    }
}

@Composable
private fun CompactStickyIdentityHeader(
    profile: ProfileView,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                name = profile.displayName,
                avatarUrl = profile.avatarUrl,
                modifier = Modifier.size(42.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = ProfileInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = profile.city?.takeIf { it.isNotBlank() }
                    ?: formatJoinedAt(profile.joinedAtMillis)
                    ?: if (profile.listingsCount > 0) "${profile.listingsCount} активных объявлений" else null
                secondary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMutedInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = ProfileStroke)
    }
}

@Composable
private fun OwnerIdentityHeroCard(
    profile: ProfileView,
    owner: OwnerProfileContext,
    selectedMode: ProfileMode,
    onModeChange: (ProfileMode) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenListings: () -> Unit,
    onOpenAccountData: () -> Unit,
) {
    val profileVisibility = if (profile.publicProfileEnabled) "Публичный профиль включён" else "Публичный профиль скрыт"
    val contactsState = when {
        owner.authUser.emailVerified && owner.authUser.phoneVerifiedAt != null -> "Контакты подтверждены"
        owner.authUser.emailVerified || owner.authUser.phoneVerifiedAt != null -> "Частично подтверждён"
        else -> "Контакты ждут подтверждения"
    }
    val trustStage = resolvePublicTrustStage(profile)
    val metaLine = buildList {
        profile.city?.takeIf { it.isNotBlank() }?.let(::add)
        profile.ratingAverage?.let { rating ->
            val ratingLabel = if (profile.ratingCount > 0) {
                "${"%.1f".format(Locale.getDefault(), rating)} · ${profile.ratingCount} отзывов"
            } else {
                "%.1f".format(Locale.getDefault(), rating)
            }
            add(ratingLabel)
        }
        if (profile.ratingAverage == null && profile.ratingCount > 0) {
            add("${profile.ratingCount} отзывов")
        }
        if (profile.listingsCount > 0) add("${profile.listingsCount} объявлений")
    }.joinToString(" • ")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                name = profile.displayName,
                avatarUrl = profile.avatarUrl,
                modifier = Modifier.size(72.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = ProfileInk,
                )
                val subline = listOfNotNull(owner.authUser.email.takeIf { it.isNotBlank() }).joinToString(" • ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProfileMutedInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subline.isNotBlank()) {
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMutedInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStatusPill(text = formatPublicTrustTitle(trustStage), highlighted = trustStage != PublicTrustStage.NEW_PROFILE)
                    ProfileStatusPill(text = profileVisibility, highlighted = profile.publicProfileEnabled)
                }
            }
        }
        ProfileModeSwitch(selected = selectedMode, onSelected = onModeChange)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = contactsState,
                    style = MaterialTheme.typography.titleSmall,
                    color = ProfileInk,
                )
                Text(
                    text = "Редактируйте профиль, объявления и защиту аккаунта из одной точки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileMutedInk,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onOpenEdit) {
                Text("Редактировать")
            }
        }

        ProfileInsetGroup(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            SettingsRow(
                icon = Icons.Outlined.Inventory2,
                title = "Мои объявления",
                subtitle = "${owner.activityHub.activeListingsCount} активных, ${owner.activityHub.draftCount} черновиков",
                onClick = onOpenListings,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.Outlined.VerifiedUser,
                title = "Данные аккаунта",
                subtitle = contactsState,
                onClick = onOpenAccountData,
            )
        }
    }
}

@Composable
private fun AccountHealthCard(
    state: OwnerAccountModeState,
    onOpenAccountData: () -> Unit,
    onOpenEditPublic: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenListings: () -> Unit,
) {
    val completion = state.completion
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Готовность аккаунта",
            style = MaterialTheme.typography.titleLarge,
            color = ProfileInk,
        )
        ProfileInsetGroup {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${completion.percent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ProfileInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatAccountCompletionSummary(completion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProfileMutedInk,
                    )
                }
                ProfileStatusPill(
                    text = formatAccountSecurityLevel(completion.securityLevel),
                    highlighted = true,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { completion.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = ProfileBlue,
                trackColor = ProfileCanvas,
            )
            val nextActions = completion.nextActions.take(3)
            if (nextActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                nextActions.forEachIndexed { index, action ->
                    HealthActionRow(
                        title = formatAccountHealthActionTitle(action),
                        message = formatAccountHealthActionMessage(action),
                        onClick = {
                            when (action) {
                                AccountHealthAction.VERIFY_EMAIL,
                                AccountHealthAction.ADD_PHONE,
                                AccountHealthAction.VERIFY_PHONE,
                                AccountHealthAction.CONFIRM_PENDING_PHONE -> onOpenAccountData()

                                AccountHealthAction.ENABLE_PUBLIC_PROFILE -> onOpenPrivacy()

                                AccountHealthAction.ADD_AVATAR,
                                AccountHealthAction.ADD_BIO,
                                AccountHealthAction.ADD_CITY -> onOpenEditPublic()

                                AccountHealthAction.PUBLISH_FIRST_LISTING -> onOpenListings()
                            }
                        },
                    )
                    if (index != nextActions.lastIndex) {
                        HorizontalDivider(color = ProfileStroke)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAccountModulesCard() {
    ProfileSectionCard {
        Text(
            text = accountEmptyModulesTitle(),
            style = MaterialTheme.typography.titleLarge,
            color = ProfileInk,
        )
        Text(
            text = accountEmptyModulesMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileMutedInk,
        )
    }
}

@Composable
private fun HealthFactChip(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ProfileCanvas,
        border = BorderStroke(1.dp, ProfileStroke),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = ProfileInk,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ProfileMutedInk,
            )
        }
    }
}

@Composable
private fun HealthActionRow(
    title: String,
    message: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = ProfileCanvas,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = ProfileMutedInk,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = ProfileInk)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileMutedInk,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = ProfileMutedInk,
            )
        }
    }
}

@Composable
private fun PublicIdentityCard(
    profile: ProfileView,
    isOwner: Boolean,
    selectedMode: ProfileMode,
    onModeChange: (ProfileMode) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenListings: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val trustStage = resolvePublicTrustStage(profile)
    val topFacts = buildList {
        add(formatPublicTrustTitle(trustStage))
        if (profile.emailVerified || profile.phoneVerified) add("Контакты подтверждены")
    }
    val secondaryMeta = listOfNotNull(
        profile.city?.takeIf { it.isNotBlank() },
        formatJoinedAt(profile.joinedAtMillis),
    ).joinToString(" • ")
    val reviewMeta = buildList {
        profile.ratingAverage?.let { rating ->
            val ratingLabel = if (profile.ratingCount > 0) {
                "${"%.1f".format(Locale.getDefault(), rating)} · ${profile.ratingCount} отзывов"
            } else {
                "%.1f".format(Locale.getDefault(), rating)
            }
            add(ratingLabel)
        }
        if (profile.ratingAverage == null && profile.ratingCount > 0) {
            add("${profile.ratingCount} отзывов")
        }
        if (profile.listingsCount > 0) add("${profile.listingsCount} объявлений")
    }.joinToString(" • ")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                name = profile.displayName,
                avatarUrl = profile.avatarUrl,
                modifier = Modifier.size(84.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = ProfileInk,
                )
                if (topFacts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        topFacts.take(2).forEachIndexed { index, fact ->
                            ProfileStatusPill(text = fact, highlighted = index == 0)
                        }
                    }
                }
                if (secondaryMeta.isNotBlank()) {
                    Text(
                        text = secondaryMeta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProfileMutedInk,
                    )
                }
                if (reviewMeta.isNotBlank()) {
                    Text(
                        text = reviewMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMutedInk,
                    )
                }
            }
        }
        if (isOwner) {
            ProfileModeSwitch(selected = selectedMode, onSelected = onModeChange)
        }

        profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyLarge,
                color = ProfileInk,
            )
        }

        profile.website?.takeIf { it.isNotBlank() }?.let { website ->
            Text(
                text = website,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileBlue,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(website) } },
            )
        }

        ProfileInsetGroup(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            SettingsRow(
                icon = Icons.Outlined.Inventory2,
                title = if (isOwner) "Мои объявления" else "Все объявления",
                subtitle = when {
                    profile.listingsCount > 0 && profile.ratingCount > 0 -> "${profile.listingsCount} объявлений, ${profile.ratingCount} отзывов"
                    profile.listingsCount > 0 -> "${profile.listingsCount} объявлений"
                    profile.ratingCount > 0 -> "${profile.ratingCount} отзывов"
                    else -> "Когда появятся объявления и отзывы, они будут видны здесь"
                },
                onClick = onOpenListings,
            )
            if (isOwner) {
                HorizontalDivider(color = ProfileStroke)
                SettingsRow(
                    icon = Icons.Outlined.Edit,
                    title = "Редактировать профиль",
                    subtitle = "Имя, описание, фото, сайт и город",
                    onClick = onOpenEdit,
                )
            }
        }
    }
}

@Composable
private fun TrustCard(profile: ProfileView) {
    val trustStage = resolvePublicTrustStage(profile)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Надёжность и репутация",
            style = MaterialTheme.typography.titleLarge,
            color = ProfileInk,
        )
        val trustSignals = buildList {
            formatJoinedAt(profile.joinedAtMillis)?.let(::add)
            if (profile.listingsCount > 0) add("${profile.listingsCount} активных объявлений")
            profile.ratingAverage?.let { rating ->
                add("Рейтинг ${"%.1f".format(Locale.getDefault(), rating)} из 5")
            }
            if (profile.ratingCount > 0) add("${profile.ratingCount} отзывов")
            if (profile.emailVerified) add("Email подтверждён")
            if (profile.phoneVerified) add("Телефон подтверждён")
            addAll(profile.verificationBadges.map(::formatBadge))
        }

        ProfileInsetGroup {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPublicTrustTitle(trustStage),
                    style = MaterialTheme.typography.titleMedium,
                    color = ProfileInk,
                )
                ProfileStatusPill(text = formatPublicTrustTitle(trustStage), highlighted = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatPublicTrustSummary(trustStage),
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileMutedInk,
            )
            if (trustSignals.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Пока у профиля нет накопленных сигналов. Когда появятся подтверждения, объявления и отзывы, они будут собраны здесь.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                trustSignals.forEachIndexed { index, signal ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = ProfileBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = signal, style = MaterialTheme.typography.bodyMedium, color = ProfileInk)
                    }
                    if (index != trustSignals.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingsPreviewCard(
    profile: ProfileView,
    isOwner: Boolean,
    onOpenListings: () -> Unit,
    onOpenOffer: (offerId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Превью объявлений", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (profile.listingsCount > 0) "${profile.listingsCount} в продаже" else "Активных объявлений пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
            }
            TextButton(onClick = onOpenListings) {
                Text(if (isOwner) "Мои объявления" else "Все объявления")
            }
        }

        ProfileInsetGroup {
            if (profile.listingsPreview.isEmpty()) {
                Text(
                    text = if (isOwner) {
                        "Когда появятся активные объявления, здесь будет ваша витрина с быстрым переходом к карточкам."
                    } else {
                        "Сейчас у продавца нет активных объявлений. Когда они появятся, здесь будет короткая витрина."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(profile.listingsPreview, key = { it.offerId }) { item ->
                        ListingPreviewItemCard(item = item, onOpenOffer = { onOpenOffer(item.offerId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingPreviewItemCard(
    item: ProfileListingPreviewItem,
    onOpenOffer: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onOpenOffer),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileStroke),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp)
                        .background(ProfileCanvas),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.priceMajor?.let { price ->
                    Text(
                        text = formatPrice(price, item.currency),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ProfileBlue,
                    )
                }
                val meta = buildList {
                    item.sourceName?.takeIf { it.isNotBlank() }?.let(::add)
                    item.updatedAtMillis?.let { add(formatShortDate(it)) }
                }.joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMutedInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityHubCard(
    owner: OwnerProfileContext,
    onOpenActiveListings: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenTrackedItems: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Что происходит сейчас",
            style = MaterialTheme.typography.titleLarge,
            color = ProfileInk,
        )
        ProfileInsetGroup(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            HubMetricRow(
                icon = Icons.Outlined.Inventory2,
                title = "Активные объявления",
                value = owner.activityHub.activeListingsCount.toString(),
                subtitle = if (owner.activityHub.activeListingsCount > 0) {
                    "Откройте и проверьте опубликованные объявления"
                } else {
                    "Пока публикаций нет. Здесь будет быстрый доступ к витрине продавца"
                },
                onClick = onOpenActiveListings,
            )
            HorizontalDivider(color = ProfileStroke)
            HubMetricRow(
                icon = Icons.Outlined.Drafts,
                title = "Черновики",
                value = owner.activityHub.draftCount.toString(),
                subtitle = "Продолжите публикацию незавершённых объявлений",
                onClick = onOpenDrafts,
            )
            HorizontalDivider(color = ProfileStroke)
            HubMetricRow(
                icon = Icons.Outlined.TrackChanges,
                title = "Трекинг",
                value = owner.activityHub.trackedCount.toString(),
                subtitle = if (owner.activityHub.trackedUnreadCount > 0) {
                    "${owner.activityHub.trackedUnreadCount} новых событий"
                } else {
                    "Новых событий нет"
                },
                onClick = onOpenTrackedItems,
            )
            HorizontalDivider(color = ProfileStroke)
            HubMetricRow(
                icon = Icons.Outlined.Notifications,
                title = "Уведомления",
                value = owner.activityHub.notificationsUnread.toString(),
                subtitle = if (owner.activityHub.notificationsUnread > 0) "Есть непрочитанные важные события" else "Все уведомления прочитаны",
                onClick = onOpenNotifications,
            )
        }
    }
}

@Composable
private fun HubMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = ProfileCanvas,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = ProfileMutedInk,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = ProfileInk)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileMutedInk,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = ProfileBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AccountSettingsCard(
    profile: ProfileView,
    owner: OwnerProfileContext,
    onOpenEditPublic: () -> Unit,
    onOpenAccountData: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Настройки и аккаунт",
            style = MaterialTheme.typography.titleLarge,
            color = ProfileInk,
        )
        ProfileInsetGroup(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            SettingsRow(
                icon = Icons.Outlined.Public,
                title = "Публичный профиль",
                subtitle = if (profile.publicProfileEnabled) "Публичный режим включён" else "Публичный режим скрыт",
                onClick = onOpenEditPublic,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.Outlined.AlternateEmail,
                title = "Данные аккаунта",
                subtitle = owner.authUser.email,
                onClick = onOpenAccountData,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.Outlined.Settings,
                title = "Настройки приложения",
                subtitle = "Язык, оформление, фильтры и быстрые действия",
                onClick = onOpenAppSettings,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Push и уведомления",
                subtitle = if (owner.pushSettings.pushEnabled) "Настроено для этого устройства" else "Push на устройстве выключены",
                onClick = onOpenNotificationSettings,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.Outlined.Security,
                title = "Приватность и безопасность",
                subtitle = "Видимость профиля, город и удаление аккаунта",
                onClick = onOpenPrivacy,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Article,
                title = "О продукте и поддержке",
                subtitle = "Справка по профилю, аккаунту и восстановлению доступа",
                onClick = onOpenAbout,
            )
            HorizontalDivider(color = ProfileStroke)
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                title = "Выйти",
                subtitle = "Завершить текущую сессию",
                tint = ProfileDanger,
                onClick = onLogout,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color = ProfileInk,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = ProfileCanvas,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (tint == ProfileInk) ProfileMutedInk else tint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tint == ProfileInk) ProfileInk else tint,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileMutedInk,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = ProfileMutedInk,
            )
        }
    }
}

@Composable
private fun EditPublicProfileScreen(
    profile: ProfileView,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: suspend (
        displayName: String,
        bio: String?,
        website: String?,
        city: String?,
        avatarUrl: String?,
    ) -> ProfileView,
    onUploadPhoto: suspend (bytes: ByteArray, fileName: String, contentType: String?) -> String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val initialDisplayName = remember(profile.userId, profile.displayName) { profile.displayName.trim() }
    val initialBio = remember(profile.userId, profile.bio) { profile.bio.orEmpty().trim() }
    val initialWebsite = remember(profile.userId, profile.website) { profile.website.orEmpty().trim() }
    val initialCity = remember(profile.userId, profile.city) { profile.city.orEmpty().trim() }
    val initialAvatarUrl = remember(profile.userId, profile.avatarUrl) { profile.avatarUrl.orEmpty().trim() }

    var displayName by rememberSaveable(profile.userId) { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable(profile.userId) { mutableStateOf(profile.bio.orEmpty()) }
    var website by rememberSaveable(profile.userId) { mutableStateOf(profile.website.orEmpty()) }
    var city by rememberSaveable(profile.userId) { mutableStateOf(profile.city.orEmpty()) }
    var avatarUrl by rememberSaveable(profile.userId) { mutableStateOf(profile.avatarUrl.orEmpty()) }
    var errorMessage by rememberSaveable(profile.userId) { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var isUploading by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var showDiscardChanges by rememberSaveable(profile.userId) { mutableStateOf(false) }

    val hasUnsavedChanges = remember(
        displayName,
        bio,
        website,
        city,
        avatarUrl,
        initialDisplayName,
        initialBio,
        initialWebsite,
        initialCity,
        initialAvatarUrl,
    ) {
        displayName.trim() != initialDisplayName ||
            bio.trim() != initialBio ||
            website.trim() != initialWebsite ||
            city.trim() != initialCity ||
            avatarUrl.trim() != initialAvatarUrl
    }
    val displayNameError = remember(displayName) {
        when {
            displayName.isBlank() -> "Укажите имя профиля"
            displayName.trim().length > 80 -> "Имя должно быть короче 80 символов"
            else -> null
        }
    }
    val bioError = remember(bio) {
        if (bio.trim().length > 280) "Описание должно быть короче 280 символов" else null
    }
    val websiteError = remember(website) {
        val normalized = website.trim()
        when {
            normalized.isBlank() -> null
            normalized.length > 512 -> "Ссылка слишком длинная"
            !normalized.contains('.') -> "Добавьте ссылку в формате site.com или https://site.com"
            else -> null
        }
    }
    val cityError = remember(city) {
        if (city.trim().length > 120) "Город должен быть короче 120 символов" else null
    }

    fun handleBackAction() {
        if (isSaving || isUploading) return
        if (hasUnsavedChanges) {
            showDiscardChanges = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !isSaving && !isUploading) {
        handleBackAction()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isUploading = true
            errorMessage = null
            runCatching {
                val upload = prepareProfilePhotoUpload(context, uri)
                val uploaded = onUploadPhoto(
                    upload.bytes,
                    "profile-${profile.userId}.jpg",
                    upload.contentType,
                )
                avatarUrl = uploaded
            }.onFailure { throwable ->
                errorMessage = throwable.message ?: "Не удалось загрузить фото"
            }
            isUploading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = LayoutDefaults.HorizontalPadding,
                end = LayoutDefaults.HorizontalPadding,
                top = LayoutDefaults.SectionSpacing,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileSectionCard(emphasized = true) {
            Text(
                text = "Публичная идентичность",
                style = MaterialTheme.typography.titleLarge,
                color = ProfileInk,
            )
            Text(
                text = "Обновите имя, описание, сайт, город и фотографию. Эти данные видят другие пользователи, если публичный профиль включён.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileMutedInk,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    name = displayName.ifBlank { profile.displayName },
                    avatarUrl = avatarUrl.takeIf { it.isNotBlank() },
                    modifier = Modifier.size(84.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch("image/*") }, enabled = !isUploading) {
                        Text(if (isUploading) "Загрузка..." else "Загрузить фото")
                    }
                    TextButton(
                        onClick = { avatarUrl = "" },
                        enabled = !isUploading && avatarUrl.isNotBlank(),
                    ) {
                        Text("Удалить фото")
                    }
                }
            }

            HorizontalDivider(color = ProfileStroke)

            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя профиля") },
                isError = displayNameError != null,
                supportingText = {
                    Text(displayNameError ?: "${displayName.trim().length}/80")
                },
                colors = profileOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = bio,
                onValueChange = {
                    bio = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("О себе") },
                isError = bioError != null,
                supportingText = {
                    Text(bioError ?: "${bio.trim().length}/280")
                },
                colors = profileOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = website,
                onValueChange = {
                    website = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Сайт") },
                isError = websiteError != null,
                supportingText = websiteError?.let { { Text(it) } },
                colors = profileOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = city,
                onValueChange = {
                    city = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Город") },
                isError = cityError != null,
                supportingText = cityError?.let { { Text(it) } },
                colors = profileOutlinedTextFieldColors(),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileDanger,
                )
            }
            Button(
                onClick = {
                    if (displayNameError != null || bioError != null || websiteError != null || cityError != null) {
                        errorMessage = displayNameError ?: bioError ?: websiteError ?: cityError
                        return@Button
                    }
                    scope.launch {
                        isSaving = true
                        errorMessage = null
                        runCatching {
                            onSave(
                                displayName.trim(),
                                bio.trim().ifEmpty { null },
                                website.trim().ifEmpty { null },
                                city.trim().ifEmpty { null },
                                avatarUrl.trim().ifEmpty { null },
                            )
                        }.onSuccess { onBack() }
                            .onFailure { throwable ->
                                errorMessage = throwable.message ?: "Не удалось сохранить профиль"
                            }
                        isSaving = false
                    }
                },
                enabled = !isSaving && displayNameError == null && bioError == null && websiteError == null && cityError == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSaving) "Сохраняем..." else "Сохранить")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = ::handleBackAction,
                    enabled = !isSaving,
                ) {
                    Text("Отменить")
                }
            }
        }
    }

    if (showDiscardChanges) {
        ProfileDialogScaffold(
            icon = Icons.Outlined.Edit,
            title = "Отменить изменения?",
            onDismiss = { showDiscardChanges = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChanges = false
                        onBack()
                    },
                ) {
                    Text("Не сохранять")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChanges = false }) {
                    Text("Продолжить")
                }
            },
        ) {
            Text(
                text = "Несохранённые изменения будут потеряны. Если хотите вернуться позже, сначала сохраните профиль.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileMutedInk,
            )
        }
    }
}

@Composable
private fun AccountDataScreen(
    authUser: AuthUser,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRequestEmailChange: suspend (String, String) -> Unit,
    onConfirmEmailChange: suspend (String) -> AuthResult,
    onStartEmailVerification: suspend () -> VerificationDeliveryStatus,
    onConfirmEmailVerification: suspend (String) -> Unit,
    onStartPhoneChange: suspend (String, String) -> Unit,
    onConfirmPhoneChange: suspend (String) -> Unit,
    onStartPhoneVerification: suspend () -> VerificationDeliveryStatus,
    onConfirmPhoneVerification: suspend (String) -> Unit,
    onChangePassword: suspend (String, String) -> AuthResult,
) {
    var dialog by rememberSaveable { mutableStateOf<AccountDialog?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = LayoutDefaults.HorizontalPadding,
            end = LayoutDefaults.HorizontalPadding,
            top = LayoutDefaults.SectionSpacing,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("summary") {
            AccountProtectionOverviewCard(authUser = authUser)
        }
        item("account-card") {
            ProfileSectionCard {
                Text(
                    text = "Контакты и вход",
                    style = MaterialTheme.typography.titleLarge,
                    color = ProfileInk,
                )
                authUser.createdAt?.let { createdAt ->
                    Text(
                        text = "Аккаунт создан ${formatShortDate(createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMutedInk,
                    )
                }
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    title = "Email",
                    subtitle = formatAccountEmailStatus(authUser.email, authUser.emailVerified),
                    onClick = { dialog = AccountDialog.EMAIL },
                )
                HorizontalDivider(color = ProfileStroke)
                SettingsRow(
                    icon = Icons.Outlined.Phone,
                    title = "Телефон",
                    subtitle = formatAccountPhoneStatus(
                        phone = authUser.phone,
                        verified = authUser.phoneVerifiedAt != null,
                        pendingPhone = authUser.pendingPhone,
                    ),
                    onClick = { dialog = AccountDialog.PHONE },
                )
                HorizontalDivider(color = ProfileStroke)
                SettingsRow(
                    icon = Icons.Outlined.Lock,
                    title = "Пароль",
                    subtitle = "Обновите пароль, если им пользовались на другом устройстве или заметили подозрительную активность.",
                    onClick = { dialog = AccountDialog.PASSWORD },
                )
            }
        }
    }

    when (dialog) {
        AccountDialog.EMAIL -> EmailChangeDialog(
            currentEmail = authUser.email,
            currentEmailVerified = authUser.emailVerified,
            onDismiss = { dialog = null },
            onRequestEmailChange = onRequestEmailChange,
            onConfirmEmailChange = onConfirmEmailChange,
            onStartEmailVerification = onStartEmailVerification,
            onConfirmEmailVerification = onConfirmEmailVerification,
        )
        AccountDialog.PHONE -> PhoneChangeDialog(
            currentPhone = authUser.phone,
            currentPhoneVerified = authUser.phoneVerifiedAt != null,
            pendingPhone = authUser.pendingPhone,
            onDismiss = { dialog = null },
            onStartPhoneChange = onStartPhoneChange,
            onConfirmPhoneChange = onConfirmPhoneChange,
            onStartPhoneVerification = onStartPhoneVerification,
            onConfirmPhoneVerification = onConfirmPhoneVerification,
        )
        AccountDialog.PASSWORD -> PasswordChangeDialog(
            onDismiss = { dialog = null },
            onSave = onChangePassword,
        )
        null -> Unit
    }
}

@Composable
private fun AccountProtectionOverviewCard(
    authUser: AuthUser,
) {
    val securityLevel = resolveAccountSecurityLevel(authUser)
    ProfileSectionCard(emphasized = true) {
        Text(
            text = "Данные аккаунта",
            style = MaterialTheme.typography.titleLarge,
            color = ProfileInk,
        )
        Text(
            text = "Контакты в этом разделе защищают вход и помогают восстанавливать доступ без поддержки. Здесь же можно обновить пароль и проверить, всё ли готово для безопасного входа.",
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileMutedInk,
        )
        ProfileStatusPill(text = formatAccountSecurityLevel(securityLevel), highlighted = true)
        Text(
            text = formatRecoveryMethodsSummary(
                authUserEmailVerified = authUser.emailVerified,
                authUserPhoneVerified = authUser.phoneVerifiedAt != null,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileMutedInk,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthFactChip(
                title = if (authUser.emailVerified) "Email подтверждён" else "Email ждёт подтверждения",
                subtitle = authUser.email,
                modifier = Modifier.weight(1f),
            )
            HealthFactChip(
                title = when {
                    !authUser.pendingPhone.isNullOrBlank() -> "Новый телефон ждёт кода"
                    authUser.phoneVerifiedAt != null -> "Телефон подтверждён"
                    authUser.phone.isNullOrBlank() -> "Телефон не добавлен"
                    else -> "Телефон ждёт подтверждения"
                },
                subtitle = authUser.pendingPhone ?: authUser.phone ?: "Добавьте резервный контакт",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotificationsCenterScreen(
    notifications: List<SubscriptionNotification>,
    totalCount: Int,
    unreadCount: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = LayoutDefaults.HorizontalPadding,
            end = LayoutDefaults.HorizontalPadding,
            top = LayoutDefaults.SectionSpacing,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("summary") {
            ProfileSectionCard(emphasized = true) {
                Text(text = "Центр уведомлений", style = MaterialTheme.typography.titleLarge)
            Text(
                text = when {
                    totalCount > notifications.size ->
                        "Показаны последние ${notifications.size} из $totalCount уведомлений"
                        unreadCount > 0 ->
                            "$unreadCount непрочитанных уведомлений"
                        else ->
                            "Все уведомления прочитаны"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
                if (notifications.any { !it.isRead }) {
                    Button(
                        onClick = onMarkAllRead,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Прочитать всё")
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item("empty") {
                ProfileStatusCard(
                    icon = Icons.Outlined.Notifications,
                    title = "Пока пусто",
                    message = "Новые уведомления появятся здесь. Пока у вас нет непрочитанных событий.",
                )
            }
        } else {
            items(notifications, key = { it.id }) { notification ->
                ProfileSectionCard(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileStatusPill(
                            text = if (notification.isRead) "Прочитано" else "Новое",
                            highlighted = !notification.isRead,
                        )
                        Text(
                            text = formatShortDate(notification.createdAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = ProfileMutedInk,
                        )
                    }
                    Text(text = notification.text, style = MaterialTheme.typography.bodyLarge, color = ProfileInk)
                    if (!notification.isRead) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onMarkRead(notification.id) }) {
                                Text("Пометить как прочитанное")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsScreen(
    settings: TrackingNotificationsSettings,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (TrackingNotificationsSettings) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings) }
    val systemPushAllowed = remember(context, draft.pushEnabled) {
        TrackingPushNotifier(context).canPostNotifications()
    }
    val quietHoursPresets = remember {
        listOf(
            22 to 8,
            23 to 8,
            0 to 7,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = LayoutDefaults.HorizontalPadding,
                end = LayoutDefaults.HorizontalPadding,
                top = LayoutDefaults.SectionSpacing,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileSectionCard {
            Text(
                text = "Push и уведомления",
                style = MaterialTheme.typography.titleLarge,
                color = ProfileInk,
            )
            Text(
                text = "Здесь настраивается поведение уведомлений на текущем устройстве: трекинг, публикации, системные события и важные сообщения о безопасности.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileMutedInk,
            )
            if (!systemPushAllowed) {
                StatusBanner(
                    title = "Системное разрешение выключено",
                    message = "Сначала разрешите уведомления в настройках Android, затем вернитесь и настройте их поведение внутри приложения.",
                    tone = SystemNoticeTone.Warning,
                )
                OutlinedButton(onClick = { openAppNotificationSettings(context) }) {
                    Text("Открыть системные настройки")
                }
            }
            SwitchRow(
                title = "Push-уведомления",
                subtitle = if (systemPushAllowed) {
                    "Главный переключатель уведомлений на этом устройстве."
                } else {
                    "Этот переключатель вступит в силу после включения уведомлений в системных настройках."
                },
                checked = draft.pushEnabled,
                onCheckedChange = { draft = draft.copy(pushEnabled = it) },
            )
            HorizontalDivider(color = ProfileStroke)
            SwitchRow(
                title = "Тихие часы",
                subtitle = if (draft.quietHoursEnabled) {
                    "Сейчас включены с ${formatQuietHoursRange(draft)}. Уведомления останутся в центре, но не будут отвлекать."
                } else {
                    "Помогают отключать лишний шум ночью, но не терять сами события."
                },
                checked = draft.quietHoursEnabled,
                onCheckedChange = { draft = draft.copy(quietHoursEnabled = it) },
            )
            if (draft.quietHoursEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Готовые интервалы",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quietHoursPresets.forEach { (start, end) ->
                            FilterChip(
                                selected = draft.quietHoursStart == start && draft.quietHoursEnd == end,
                                onClick = {
                                    draft = draft.copy(
                                        quietHoursStart = start,
                                        quietHoursEnd = end,
                                    )
                                },
                                label = {
                                    Text("%02d:00 - %02d:00".format(start, end))
                                },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = ProfileStroke)
            SwitchRow(
                title = "Группировать уведомления",
                subtitle = "Похожие события будут собираться в одну карточку.",
                checked = draft.groupNotifications,
                onCheckedChange = { draft = draft.copy(groupNotifications = it) },
            )
            Text(text = "Приоритет", style = MaterialTheme.typography.titleSmall)
            Text(
                text = formatNotificationPriorityDescription(draft.priority),
                style = MaterialTheme.typography.bodySmall,
                color = ProfileMutedInk,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = draft.priority == priority,
                        onClick = { draft = draft.copy(priority = priority) },
                        label = { Text(formatNotificationPriorityLabel(priority)) },
                    )
                }
            }
            Button(
                onClick = {
                    onSave(draft)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onBack) {
                    Text("Отменить")
                }
            }
        }
    }
}

@Composable
private fun PrivacySafetyScreen(
    profile: ProfileView,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSavePrivacy: suspend (publicProfileEnabled: Boolean, cityVisible: Boolean) -> ProfileView,
    onDeleteAccount: suspend (String) -> DeleteAccountReceipt?,
    onDeleteCompleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    var publicProfileEnabled by rememberSaveable(profile.userId) { mutableStateOf(profile.publicProfileEnabled) }
    var cityVisible by rememberSaveable(profile.userId) { mutableStateOf(profile.cityVisible) }
    var errorMessage by rememberSaveable(profile.userId) { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var isDeleting by rememberSaveable { mutableStateOf(false) }
    var deletePassword by rememberSaveable { mutableStateOf("") }
    var deleteErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteReceipt by remember { mutableStateOf<DeleteAccountReceipt?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = LayoutDefaults.HorizontalPadding,
                end = LayoutDefaults.HorizontalPadding,
                top = LayoutDefaults.SectionSpacing,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileSectionCard {
            Text(
                text = "Приватность и безопасность",
                style = MaterialTheme.typography.titleLarge,
                color = ProfileInk,
            )
            Text(
                text = formatPrivacySummary(
                    publicProfileEnabled = publicProfileEnabled,
                    cityVisible = cityVisible,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileMutedInk,
            )
            SwitchRow(
                title = "Публичный профиль",
                subtitle = "Если выключить, другие пользователи перестанут видеть ваш публичный профиль.",
                checked = publicProfileEnabled,
                onCheckedChange = {
                    publicProfileEnabled = it
                    errorMessage = null
                },
            )
            HorizontalDivider(color = ProfileStroke)
            SwitchRow(
                title = "Показывать город",
                subtitle = "Город будет отображаться в публичном профиле и карточке доверия.",
                checked = cityVisible,
                onCheckedChange = {
                    cityVisible = it
                    errorMessage = null
                },
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileDanger,
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorMessage = null
                        runCatching {
                            onSavePrivacy(publicProfileEnabled, cityVisible)
                        }.onSuccess { onBack() }
                            .onFailure { throwable ->
                                errorMessage = throwable.message ?: "Не удалось сохранить настройки приватности"
                            }
                        isSaving = false
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSaving) "Сохраняем..." else "Сохранить")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = !isSaving,
                ) {
                    Text("Назад")
                }
            }
        }

        ProfileSectionCard(danger = true) {
            Text(
                text = "Удаление аккаунта",
                style = MaterialTheme.typography.titleLarge,
                color = ProfileDanger,
            )
            Text(
                text = "Удаление запланирует закрытие аккаунта и завершит все активные сессии. Пока не истёк срок удаления, доступ можно восстановить по токену восстановления.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileMutedInk,
            )
            OutlinedButton(
                onClick = {
                    deleteErrorMessage = null
                    deletePassword = ""
                    showDeleteConfirm = true
                },
                enabled = !isDeleting,
                border = BorderStroke(1.dp, ProfileDanger),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = ProfileDanger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDeleting) "Удаляем..." else "Удалить аккаунт",
                    color = ProfileDanger,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        ProfileDialogScaffold(
            icon = Icons.Outlined.DeleteOutline,
            title = "Удалить аккаунт?",
            onDismiss = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deletePassword.isBlank()) {
                            deleteErrorMessage = "Введите текущий пароль"
                            return@TextButton
                        }
                        scope.launch {
                            isDeleting = true
                            val receipt = runCatching { onDeleteAccount(deletePassword) }
                                .onFailure { throwable ->
                                    deleteErrorMessage = throwable.message ?: "Не удалось запланировать удаление аккаунта"
                                }
                                .getOrNull()
                            isDeleting = false
                            if (receipt != null) {
                                showDeleteConfirm = false
                                deletePassword = ""
                                deleteErrorMessage = null
                                deleteReceipt = receipt
                            }
                        }
                    },
                ) {
                    Text("Удалить", color = ProfileDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            },
        ) {
            Text(
                text = "После подтверждения текущая сессия завершится, а профиль будет скрыт для других пользователей. Если срок удаления ещё не истёк, аккаунт можно восстановить по токену.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = deletePassword,
                onValueChange = {
                    deletePassword = it
                    deleteErrorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текущий пароль") },
                supportingText = { Text("Подтвердите пароль, чтобы никто не смог удалить аккаунт из открытой сессии.") },
                visualTransformation = PasswordVisualTransformation(),
                colors = profileOutlinedTextFieldColors(),
            )
            if (deleteErrorMessage != null) {
                Text(
                    text = deleteErrorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileDanger,
                )
            }
        }
    }

    deleteReceipt?.let { receipt ->
        ProfileDialogScaffold(
            icon = Icons.Outlined.VerifiedUser,
            title = "Аккаунт помечен на удаление",
            onDismiss = {},
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteReceipt = null
                        onDeleteCompleted()
                    },
                ) {
                    Text("Понятно")
                }
            },
            dismissButton = receipt.restoreToken?.takeIf { it.isNotBlank() }?.let { token ->
                {
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("restore_token", token)))
                            }
                            Toast.makeText(context, "Токен восстановления скопирован.", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("Скопировать токен")
                    }
                }
            },
        ) {
            Text(
                text = formatDeleteAccountScheduledMessage(receipt.deleteAfter),
                style = MaterialTheme.typography.bodyMedium,
            )
            receipt.restoreToken?.takeIf { it.isNotBlank() }?.let { token ->
                OutlinedTextField(
                    value = token,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    label = { Text("Токен восстановления") },
                    supportingText = {
                        Text(formatDeleteAccountTokenHint(hasRestoreToken = true))
                    },
                )
            } ?: Text(
                text = formatDeleteAccountTokenHint(hasRestoreToken = false),
                style = MaterialTheme.typography.bodySmall,
                color = ProfileMutedInk,
            )
        }
    }
}

@Composable
private fun AboutProfileScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = LayoutDefaults.HorizontalPadding,
            end = LayoutDefaults.HorizontalPadding,
            top = LayoutDefaults.SectionSpacing,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("about") {
            ProfileSectionCard {
                Text(text = "О профиле и аккаунте", style = MaterialTheme.typography.titleLarge, color = ProfileInk)
                Text(
                    text = "В этом разделе собраны публичный профиль, данные аккаунта, безопасность, настройки приложения и восстановление доступа.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ProfileInk,
                )
                Text(
                    text = "Если какой-то раздел временно недоступен, обновите экран или повторите попытку позже. Часть действий с аккаунтом может требовать подтверждения через ваши контакты.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
            }
        }
        item("self-service") {
            ProfileSectionCard {
                Text(text = "Что можно сделать самостоятельно", style = MaterialTheme.typography.titleLarge, color = ProfileInk)
                Text("• обновить публичный профиль и управлять его видимостью", style = MaterialTheme.typography.bodyMedium, color = ProfileInk)
                Text("• сменить email, телефон и пароль", style = MaterialTheme.typography.bodyMedium, color = ProfileInk)
                Text("• проверить, какие контакты подтверждены и используются для защиты входа", style = MaterialTheme.typography.bodyMedium, color = ProfileInk)
                Text("• запланировать удаление аккаунта и сохранить токен восстановления", style = MaterialTheme.typography.bodyMedium, color = ProfileInk)
            }
        }
        item("support") {
            ProfileSectionCard {
                Text(text = "Если подтверждение не приходит", style = MaterialTheme.typography.titleLarge, color = ProfileInk)
                Text(
                    text = "Проверьте правильность email или телефона, подождите несколько минут перед повторной отправкой и убедитесь, что доступ к выбранному контакту у вас под рукой.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileInk,
                )
                Text(
                    text = "Если контакт устарел или вы потеряли к нему доступ, сначала обновите его в разделе данных аккаунта, а затем повторите подтверждение.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatusScreen(
    contentPadding: PaddingValues,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    tone: SystemNoticeTone = SystemNoticeTone.Info,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = LayoutDefaults.HorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        ProfileStatusCard(
            icon = icon,
            title = title,
            message = message,
            tone = tone,
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
        )
    }
}

@Composable
private fun ProfileUnavailableSubscreen(
    contentPadding: PaddingValues,
    title: String,
    message: String,
    onBack: () -> Unit,
) {
    ProfileStatusScreen(
        contentPadding = contentPadding,
        icon = Icons.AutoMirrored.Outlined.ArrowBack,
        title = title,
        message = message,
        tone = SystemNoticeTone.Warning,
        primaryLabel = "Назад",
        onPrimary = onBack,
    )
}

@Composable
private fun ProfileStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    tone: SystemNoticeTone = SystemNoticeTone.Info,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    SystemNoticeCard(
        title = title,
        body = message,
        tone = tone,
        iconOverride = icon,
        modifier = Modifier.fillMaxWidth(),
        bottomContent = if (primaryLabel == null || onPrimary == null) {
            null
        } else {
            {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(primaryLabel)
                }
            }
        },
    )
}

@Composable
private fun StatusBanner(
    title: String,
    message: String,
    tone: SystemNoticeTone = SystemNoticeTone.Info,
) {
    SystemNoticeCard(
        title = title,
        body = message,
        tone = tone,
        compact = true,
    )
}

@Composable
private fun GuestValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = ProfileCanvas,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProfileMutedInk,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = ProfileInk)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = ProfileInk)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ProfileMutedInk,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProfileAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = ProfileCanvas,
        border = BorderStroke(1.dp, ProfileStroke),
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.trim().take(1).ifBlank { "P" }.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ProfileInk,
                )
            }
        }
    }
}

@Composable
private fun ProfileDialogScaffold(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable (() -> Unit),
    dismissButton: (@Composable (() -> Unit))? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ProfileStroke),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ProfileBlueCanvas,
                    border = BorderStroke(1.dp, ProfileBlue.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.CenterHorizontally),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ProfileBlue,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = ProfileInk,
                )
                content()
                HorizontalDivider(color = ProfileStroke)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun EmailChangeDialog(
    currentEmail: String,
    currentEmailVerified: Boolean,
    onDismiss: () -> Unit,
    onRequestEmailChange: suspend (String, String) -> Unit,
    onConfirmEmailChange: suspend (String) -> AuthResult,
    onStartEmailVerification: suspend () -> VerificationDeliveryStatus,
    onConfirmEmailVerification: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var nextEmail by rememberSaveable { mutableStateOf(currentEmail) }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var changeToken by rememberSaveable { mutableStateOf("") }
    var verificationToken by rememberSaveable { mutableStateOf("") }
    var changeRequested by rememberSaveable { mutableStateOf(false) }
    var verificationRequested by rememberSaveable { mutableStateOf(false) }
    var emailVerified by rememberSaveable(currentEmail, currentEmailVerified) {
        mutableStateOf(currentEmailVerified)
    }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var emailVerificationCooldown by rememberSaveable(currentEmail) { mutableStateOf(0) }
    var emailChangeCooldown by rememberSaveable(currentEmail) { mutableStateOf(0) }
    val emailRequiredMessage = "Укажите email"
    val emailInvalidMessage = "Введите корректный email"
    val passwordRequiredMessage = "Введите текущий пароль"

    LaunchedEffect(emailVerificationCooldown) {
        if (emailVerificationCooldown > 0) {
            delay(1000)
            emailVerificationCooldown -= 1
        }
    }

    LaunchedEffect(emailChangeCooldown) {
        if (emailChangeCooldown > 0) {
            delay(1000)
            emailChangeCooldown -= 1
        }
    }

    ProfileDialogScaffold(
        icon = Icons.Outlined.Email,
        title = "Email и подтверждение",
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Готово")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (emailVerified) {
                        "Текущий email подтверждён: $currentEmail"
                    } else {
                        "Текущий email ещё не подтверждён: $currentEmail"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
                if (!emailVerified) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                runCatching { onStartEmailVerification() }
                                    .onSuccess { status ->
                                        when (status) {
                                            VerificationDeliveryStatus.ALREADY_VERIFIED -> {
                                                emailVerified = true
                                                verificationRequested = false
                                                verificationToken = ""
                                                infoMessage = "Email уже подтверждён."
                                            }

                                            VerificationDeliveryStatus.SENT -> {
                                                verificationRequested = true
                                                emailVerificationCooldown = VERIFICATION_RESEND_COOLDOWN_SECONDS
                                                infoMessage = "Мы отправили письмо с ссылкой и кодом подтверждения на текущий email."
                                            }
                                        }
                                    }
                                    .onFailure { throwable ->
                                        errorMessage = throwable.message ?: "Не удалось отправить письмо для подтверждения email"
                                    }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && emailVerificationCooldown == 0,
                    ) {
                        Text(
                            resendActionLabel(
                                defaultLabel = "Отправить письмо для подтверждения",
                                remainingSeconds = emailVerificationCooldown,
                            ),
                        )
                    }
                    if (verificationRequested || verificationToken.isNotBlank()) {
                        OutlinedTextField(
                            value = verificationToken,
                            onValueChange = {
                                verificationToken = it
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Код или токен подтверждения") },
                            supportingText = { Text("Код из письма.") },
                            colors = profileOutlinedTextFieldColors(),
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    infoMessage = null
                                    runCatching { onConfirmEmailVerification(verificationToken.trim()) }
                                        .onSuccess {
                                            emailVerified = true
                                            verificationRequested = false
                                            verificationToken = ""
                                            infoMessage = "Email подтверждён."
                                        }
                                        .onFailure { throwable ->
                                            errorMessage = throwable.message ?: "Не удалось подтвердить email"
                                        }
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && verificationToken.isNotBlank(),
                        ) {
                            Text("Подтвердить текущий email")
                        }
                    }
                    HorizontalDivider(color = ProfileStroke)
                }
                Text(
                    text = "Новый email",
                    style = MaterialTheme.typography.titleSmall,
                    color = ProfileInk,
                )
                OutlinedTextField(
                    value = nextEmail,
                    onValueChange = {
                        nextEmail = it
                        changeRequested = false
                        changeToken = ""
                        infoMessage = null
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Новый email") },
                    supportingText = {
                        Text("Подтверждение придёт на этот адрес.")
                    },
                    colors = profileOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текущий пароль") },
                    supportingText = { Text("Подтвердите текущий пароль.") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = profileOutlinedTextFieldColors(),
                )
                if (changeRequested) {
                    OutlinedTextField(
                        value = changeToken,
                        onValueChange = {
                            changeToken = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Код или токен из письма") },
                        supportingText = { Text("Код из письма.") },
                        colors = profileOutlinedTextFieldColors(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val validationError = validateAuthEmail(
                                nextEmail,
                                emailRequiredMessage,
                                emailInvalidMessage,
                            )
                            errorMessage = when {
                                validationError != null -> validationError
                                currentPassword.isBlank() -> passwordRequiredMessage
                                else -> null
                            }
                            if (errorMessage != null) {
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                runCatching { onRequestEmailChange(nextEmail.trim(), currentPassword) }
                                    .onSuccess {
                                        changeRequested = true
                                        emailChangeCooldown = VERIFICATION_RESEND_COOLDOWN_SECONDS
                                        infoMessage = "Мы отправили письмо на новый email. Подтвердите смену по ссылке или введите код вручную."
                                    }
                                    .onFailure { throwable ->
                                        errorMessage = throwable.message ?: "Не удалось запросить смену email"
                                    }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && nextEmail.isNotBlank() && emailChangeCooldown == 0,
                    ) {
                        Text(
                            resendActionLabel(
                                defaultLabel = if (changeRequested) "Отправить письмо ещё раз" else "Сменить email",
                                remainingSeconds = emailChangeCooldown,
                            ),
                        )
                    }
                    if (changeRequested) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    infoMessage = null
                                    when (val result = onConfirmEmailChange(changeToken.trim())) {
                                        is AuthResult.Success -> onDismiss()
                                        is AuthResult.Error -> {
                                            errorMessage = when (result.error) {
                                                AuthError.INVALID_CREDENTIALS -> "Код подтверждения недействителен или уже истёк."
                                                AuthError.EMAIL_ALREADY_EXISTS -> "Этот email уже используется другим аккаунтом."
                                                AuthError.UNKNOWN -> "Не удалось подтвердить новый email"
                                            }
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && changeToken.isNotBlank(),
                        ) {
                            Text("Подтвердить новый email")
                        }
                    }
                }
                if (infoMessage != null) {
                    Text(
                        text = infoMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileBlue,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileDanger,
                    )
                }
        }
    }
}

@Composable
private fun PhoneChangeDialog(
    currentPhone: String?,
    currentPhoneVerified: Boolean,
    pendingPhone: String?,
    onDismiss: () -> Unit,
    onStartPhoneChange: suspend (String, String) -> Unit,
    onConfirmPhoneChange: suspend (String) -> Unit,
    onStartPhoneVerification: suspend () -> VerificationDeliveryStatus,
    onConfirmPhoneVerification: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newPhone by rememberSaveable(currentPhone, pendingPhone) {
        mutableStateOf(pendingPhone ?: currentPhone.orEmpty())
    }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var currentVerificationToken by rememberSaveable { mutableStateOf("") }
    var pendingVerificationToken by rememberSaveable { mutableStateOf("") }
    var currentVerificationRequested by rememberSaveable(currentPhone) { mutableStateOf(false) }
    var pendingVerificationRequested by rememberSaveable(pendingPhone) {
        mutableStateOf(!pendingPhone.isNullOrBlank())
    }
    var phoneVerified by rememberSaveable(currentPhone, currentPhoneVerified) {
        mutableStateOf(currentPhoneVerified)
    }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var currentPhoneCooldown by rememberSaveable(currentPhone) { mutableStateOf(0) }
    var pendingPhoneCooldown by rememberSaveable(pendingPhone) { mutableStateOf(0) }
    val phoneRequiredMessage = "Укажите телефон"
    val phoneInvalidMessage = "Введите корректный номер телефона"
    val passwordRequiredMessage = "Введите текущий пароль"

    LaunchedEffect(currentPhoneCooldown) {
        if (currentPhoneCooldown > 0) {
            delay(1000)
            currentPhoneCooldown -= 1
        }
    }

    LaunchedEffect(pendingPhoneCooldown) {
        if (pendingPhoneCooldown > 0) {
            delay(1000)
            pendingPhoneCooldown -= 1
        }
    }

    ProfileDialogScaffold(
        icon = Icons.Outlined.Phone,
        title = "Телефон и подтверждение",
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Готово")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (phoneVerified && currentPhone.orEmpty().isNotBlank()) {
                        "Текущий номер подтверждён и используется для восстановления доступа."
                    } else {
                        "Добавьте или подтвердите телефон, чтобы защитить аккаунт и восстанавливать доступ по SMS."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMutedInk,
                )
                if (!currentPhone.isNullOrBlank()) {
                    Text(
                        text = formatAccountPhoneStatus(
                            phone = currentPhone,
                            verified = phoneVerified,
                            pendingPhone = pendingPhone,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMutedInk,
                    )
                }
                if (!phoneVerified && !currentPhone.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                runCatching { onStartPhoneVerification() }
                                    .onSuccess { status ->
                                        when (status) {
                                            VerificationDeliveryStatus.ALREADY_VERIFIED -> {
                                                phoneVerified = true
                                                currentVerificationRequested = false
                                                currentVerificationToken = ""
                                                infoMessage = "Текущий телефон уже подтверждён."
                                            }

                                            VerificationDeliveryStatus.SENT -> {
                                                currentVerificationRequested = true
                                                currentPhoneCooldown = VERIFICATION_RESEND_COOLDOWN_SECONDS
                                                infoMessage = "Мы отправили код подтверждения на текущий номер."
                                            }
                                        }
                                    }
                                    .onFailure { throwable ->
                                        errorMessage = throwable.message ?: "Не удалось отправить код подтверждения"
                                    }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && currentPhoneCooldown == 0,
                    ) {
                        Text(
                            resendActionLabel(
                                defaultLabel = "Подтвердить текущий номер",
                                remainingSeconds = currentPhoneCooldown,
                            ),
                        )
                    }
                }
                if ((currentVerificationRequested || currentVerificationToken.isNotBlank()) && !phoneVerified) {
                    OutlinedTextField(
                        value = currentVerificationToken,
                        onValueChange = {
                            currentVerificationToken = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Код для текущего номера") },
                        colors = profileOutlinedTextFieldColors(),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                runCatching { onConfirmPhoneVerification(currentVerificationToken.trim()) }
                                    .onSuccess {
                                        phoneVerified = true
                                        currentVerificationRequested = false
                                        currentVerificationToken = ""
                                        infoMessage = "Текущий телефон подтверждён."
                                    }
                                    .onFailure { throwable ->
                                        errorMessage = throwable.message ?: "Не удалось подтвердить телефон"
                                    }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && currentVerificationToken.isNotBlank(),
                    ) {
                        Text("Подтвердить текущий номер")
                    }
                }
                HorizontalDivider(color = ProfileStroke)
                Text(
                    text = "Новый номер",
                    style = MaterialTheme.typography.titleSmall,
                    color = ProfileInk,
                )
                OutlinedTextField(
                    value = newPhone,
                    onValueChange = {
                        newPhone = it
                        pendingVerificationRequested = false
                        pendingVerificationToken = ""
                        infoMessage = null
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Новый телефон") },
                    supportingText = { Text("Номер станет основным после подтверждения.") },
                    colors = profileOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текущий пароль") },
                    supportingText = { Text("Подтвердите текущий пароль.") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = profileOutlinedTextFieldColors(),
                )
                Button(
                    onClick = {
                        val validationError = validateAuthPhone(
                            newPhone,
                            phoneRequiredMessage,
                            phoneInvalidMessage,
                        )
                        errorMessage = when {
                            validationError != null -> validationError
                            currentPassword.isBlank() -> passwordRequiredMessage
                            else -> null
                        }
                        if (errorMessage != null) {
                            return@Button
                        }
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            infoMessage = null
                            runCatching { onStartPhoneChange(newPhone.trim(), currentPassword) }
                                .onSuccess {
                                    pendingVerificationRequested = true
                                    pendingPhoneCooldown = VERIFICATION_RESEND_COOLDOWN_SECONDS
                                    pendingVerificationToken = ""
                                    infoMessage = "Мы отправили код на новый номер. Он станет основным только после подтверждения."
                                }
                                .onFailure { throwable ->
                                    errorMessage = throwable.message ?: "Не удалось начать смену телефона"
                                }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading && newPhone.isNotBlank() && pendingPhoneCooldown == 0,
                ) {
                    Text(
                        resendActionLabel(
                            defaultLabel = if (!pendingPhone.isNullOrBlank()) "Отправить код ещё раз" else "Отправить код на новый номер",
                            remainingSeconds = pendingPhoneCooldown,
                        ),
                    )
                }
                if ((pendingVerificationRequested || pendingVerificationToken.isNotBlank() || !pendingPhone.isNullOrBlank()) && newPhone.isNotBlank()) {
                    OutlinedTextField(
                        value = pendingVerificationToken,
                        onValueChange = {
                            pendingVerificationToken = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Код для нового номера") },
                        colors = profileOutlinedTextFieldColors(),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                runCatching { onConfirmPhoneChange(pendingVerificationToken.trim()) }
                                    .onSuccess {
                                        infoMessage = "Новый номер подтверждён и стал основным."
                                        onDismiss()
                                    }
                                    .onFailure { throwable ->
                                        errorMessage = throwable.message ?: "Не удалось подтвердить новый телефон"
                                    }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && pendingVerificationToken.isNotBlank(),
                    ) {
                        Text("Подтвердить новый номер")
                    }
                }
                if (infoMessage != null) {
                    Text(
                        text = infoMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileBlue,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileDanger,
                    )
                }
        }
    }
}

@Composable
private fun PasswordChangeDialog(
    onDismiss: () -> Unit,
    onSave: suspend (oldPassword: String, newPassword: String) -> AuthResult,
) {
    val scope = rememberCoroutineScope()
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showCurrentPassword by rememberSaveable { mutableStateOf(false) }
    var showNewPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirmPassword by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val passwordShortMessage = "Пароль должен содержать минимум 8 символов"
    val passwordStrength = remember(newPassword) { describePasswordStrength(newPassword) }

    ProfileDialogScaffold(
        icon = Icons.Outlined.Lock,
        title = "Сменить пароль",
        onDismiss = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val passwordValidationError = validateAuthPassword(
                        newPassword,
                        passwordShortMessage,
                    )
                    errorMessage = when {
                        currentPassword.isBlank() -> "Введите текущий пароль"
                        passwordValidationError != null -> passwordValidationError
                        newPassword == currentPassword -> "Новый пароль должен отличаться от текущего"
                        confirmPassword != newPassword -> "Повторный пароль не совпадает"
                        else -> null
                    }
                    if (errorMessage != null) {
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        when (val result = onSave(currentPassword, newPassword)) {
                            is AuthResult.Success -> onDismiss()
                            is AuthResult.Error -> {
                                errorMessage = when (result.error) {
                                    AuthError.INVALID_CREDENTIALS -> "Текущий пароль неверен"
                                    AuthError.EMAIL_ALREADY_EXISTS -> "Неожиданная ошибка смены пароля"
                                    AuthError.UNKNOWN -> "Не удалось сменить пароль"
                                }
                            }
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading,
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Отмена")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текущий пароль") },
                    visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                            Icon(
                                imageVector = if (showCurrentPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showCurrentPassword) "Скрыть пароль" else "Показать пароль",
                            )
                        }
                    },
                    colors = profileOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Новый пароль") },
                    supportingText = {
                        Text("Используйте не менее 8 символов и не повторяйте текущий пароль. ${passwordStrength.label}")
                    },
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showNewPassword) "Скрыть пароль" else "Показать пароль",
                            )
                        }
                    },
                    colors = profileOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Повторите новый пароль") },
                    supportingText = {
                        when {
                            confirmPassword.isBlank() -> Text("Повторите новый пароль, чтобы исключить ошибку при вводе.")
                            confirmPassword == newPassword -> Text("Пароли совпадают.")
                            else -> Text("Повторный пароль пока не совпадает.")
                        }
                    },
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showConfirmPassword) "Скрыть пароль" else "Показать пароль",
                            )
                        }
                    },
                    colors = profileOutlinedTextFieldColors(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileDanger,
                    )
                }
        }
    }
}

private fun profileTopBarTitle(
    surface: ProfileSurface,
    ready: ProfileRouteContent.Ready?,
    mode: ProfileMode,
): String = when (surface) {
    ProfileSurface.ROOT -> when {
        ready == null -> "Профиль"
        mode == ProfileMode.ACCOUNT && ready.ownerContext != null -> "Аккаунт"
        else -> "Профиль"
    }
    ProfileSurface.EDIT_PUBLIC -> "Редактирование профиля"
    ProfileSurface.ACCOUNT_DATA -> "Данные аккаунта"
    ProfileSurface.APP_SETTINGS -> "Настройки"
    ProfileSurface.DELIVERY_ADDRESSES -> "Адреса доставки"
    ProfileSurface.SELLER_DELIVERY_ZONES -> "Зоны доставки"
    ProfileSurface.NOTIFICATIONS_CENTER -> "Уведомления"
    ProfileSurface.NOTIFICATION_SETTINGS -> "Push и уведомления"
    ProfileSurface.CATALOG_GOVERNANCE -> "Catalog governance"
    ProfileSurface.PRIVACY -> "Приватность и безопасность"
    ProfileSurface.ABOUT -> "О продукте"
}

private fun profileBackSurface(surface: ProfileSurface): ProfileSurface = when (surface) {
    ProfileSurface.DELIVERY_ADDRESSES,
    ProfileSurface.SELLER_DELIVERY_ZONES,
    ProfileSurface.CATALOG_GOVERNANCE,
    -> ProfileSurface.APP_SETTINGS

    else -> ProfileSurface.ROOT
}

private data class PasswordStrengthDescriptor(
    val label: String,
)

private fun describePasswordStrength(password: String): PasswordStrengthDescriptor {
    val score = buildList {
        if (password.length >= 8) add(1)
        if (password.any { it.isDigit() }) add(1)
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) add(1)
        if (password.any { !it.isLetterOrDigit() }) add(1)
    }.sum()

    val label = when {
        password.isBlank() -> "Придумайте новый пароль."
        score <= 1 -> "Пароль пока слабый."
        score == 2 -> "Пароль средней силы."
        score == 3 -> "Хороший пароль."
        else -> "Сильный пароль."
    }
    return PasswordStrengthDescriptor(label = label)
}

private fun formatBadge(badge: ProfileVerificationBadge): String = when (badge) {
    ProfileVerificationBadge.EMAIL_VERIFIED -> "Email подтверждён"
    ProfileVerificationBadge.PHONE_VERIFIED -> "Телефон подтверждён"
    ProfileVerificationBadge.TRUSTED_SELLER -> "Надёжный продавец"
}

private fun formatJoinedAt(value: Long?): String? {
    if (value == null) return null
    return "На платформе с ${formatShortDate(value)}"
}

private fun formatShortDate(value: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(value))

private fun formatPrice(value: Double, currency: String): String {
    val normalized = if (value.roundToInt().toDouble() == value) {
        value.roundToInt().toString()
    } else {
        "%.2f".format(Locale.US, value)
    }
    return "$normalized $currency"
}

private fun resendActionLabel(defaultLabel: String, remainingSeconds: Int): String =
    if (remainingSeconds > 0) "$defaultLabel через $remainingSeconds c" else defaultLabel

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
}

private suspend fun prepareProfilePhotoUpload(context: Context, uri: Uri): PreparedProfilePhotoUpload =
    withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: throw IllegalStateException("Не удалось прочитать выбранный файл")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Выберите изображение в поддерживаемом формате")
        }

        val sampleSize = calculateProfilePhotoSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxSidePx = PROFILE_PHOTO_MAX_SIDE_PX,
        )
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalStateException("Не удалось декодировать выбранное изображение")

        val resized = resizeProfilePhoto(bitmap, PROFILE_PHOTO_MAX_SIDE_PX)
        if (resized !== bitmap) {
            bitmap.recycle()
        }

        try {
            PreparedProfilePhotoUpload(
                bytes = compressProfilePhoto(resized),
                contentType = "image/jpeg",
            )
        } finally {
            resized.recycle()
        }
    }

private fun calculateProfilePhotoSampleSize(
    width: Int,
    height: Int,
    maxSidePx: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSidePx * 2 || currentHeight > maxSidePx * 2) {
        sampleSize *= 2
        currentWidth /= 2
        currentHeight /= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun resizeProfilePhoto(bitmap: Bitmap, maxSidePx: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxSidePx) return bitmap
    val scale = maxSidePx.toFloat() / maxSide.toFloat()
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun compressProfilePhoto(bitmap: Bitmap): ByteArray {
    var quality = PROFILE_PHOTO_JPEG_QUALITY
    while (quality >= PROFILE_PHOTO_MIN_JPEG_QUALITY) {
        val bytes = ByteArrayOutputStream().use { output ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            if (!compressed) {
                throw IllegalStateException("Не удалось подготовить изображение для загрузки")
            }
            output.toByteArray()
        }
        if (bytes.size <= PROFILE_PHOTO_MAX_UPLOAD_BYTES) {
            return bytes
        }
        quality -= 8
    }
    throw IllegalStateException("Фото слишком большое. Выберите изображение меньшего размера.")
}

private data class PreparedProfilePhotoUpload(
    val bytes: ByteArray,
    val contentType: String,
)

private const val PROFILE_PHOTO_MAX_SIDE_PX = 1600
private const val PROFILE_PHOTO_MAX_UPLOAD_BYTES = 3 * 1024 * 1024
private const val PROFILE_PHOTO_JPEG_QUALITY = 90
private const val PROFILE_PHOTO_MIN_JPEG_QUALITY = 58
private const val VERIFICATION_RESEND_COOLDOWN_SECONDS = 60
