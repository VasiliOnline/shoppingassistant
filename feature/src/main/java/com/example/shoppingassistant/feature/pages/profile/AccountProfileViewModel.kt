package com.example.shoppingassistant.feature.pages.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.core.data.auth.RemoteAuthRepository
import com.example.shoppingassistant.core.data.auth.VerificationDeliveryStatus
import com.example.shoppingassistant.core.data.profile.ProfileSettingsStore
import com.example.shoppingassistant.core.push.TrackingNotificationsSettings
import com.example.shoppingassistant.core.push.TrackingNotificationsSettingsStorage
import com.example.shoppingassistant.domain.auth.ChangePasswordUseCase
import com.example.shoppingassistant.domain.auth.GetCurrentUserUseCase
import com.example.shoppingassistant.domain.auth.LoginUserUseCase
import com.example.shoppingassistant.domain.auth.LogoutUseCase
import com.example.shoppingassistant.domain.auth.RegisterUserUseCase
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.profile.DeleteAccountReceipt
import com.example.shoppingassistant.domain.profile.ConfirmEmailChangeTask
import com.example.shoppingassistant.domain.profile.DeleteAccountTask
import com.example.shoppingassistant.domain.profile.GetProfileSettingsTask
import com.example.shoppingassistant.domain.profile.GetProfileViewTask
import com.example.shoppingassistant.domain.profile.ProfileEntryMode
import com.example.shoppingassistant.domain.profile.ProfileLookupResult
import com.example.shoppingassistant.domain.profile.ProfilePrivacyUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfileSettings
import com.example.shoppingassistant.domain.profile.ProfileView
import com.example.shoppingassistant.domain.profile.ProfileViewCacheRepository
import com.example.shoppingassistant.domain.profile.RequestEmailChangeTask
import com.example.shoppingassistant.domain.profile.UpdateProfilePrivacyTask
import com.example.shoppingassistant.domain.profile.UpdatePublicProfileTask
import com.example.shoppingassistant.domain.profile.SellerDeliveryZone
import com.example.shoppingassistant.domain.profile.SellerDeliveryZonesUpdatePayload
import com.example.shoppingassistant.domain.profile.UpdateSellerDeliveryZonesTask
import com.example.shoppingassistant.domain.storage.UploadPhotoUseCase
import com.example.shoppingassistant.domain.subscriptions.ListNotificationsPageTask
import com.example.shoppingassistant.domain.subscriptions.MarkAllNotificationsReadTask
import com.example.shoppingassistant.domain.subscriptions.MarkNotificationReadTask
import com.example.shoppingassistant.domain.subscriptions.SubscriptionNotification
import com.example.shoppingassistant.domain.subscriptions.SubscriptionNotificationsPage
import com.example.shoppingassistant.domain.tracks.GetTrackedItemsListTask
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.ugc.draft.ListDraftOffersTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AccountProfileViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getProfileView: GetProfileViewTask,
    private val updatePublicProfile: UpdatePublicProfileTask,
    private val updateProfilePrivacy: UpdateProfilePrivacyTask,
    private val updateSellerDeliveryZonesTask: UpdateSellerDeliveryZonesTask,
    private val requestEmailChangeTask: RequestEmailChangeTask,
    private val confirmEmailChangeTask: ConfirmEmailChangeTask,
    private val deleteAccountTask: DeleteAccountTask,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val uploadPhotoUseCase: UploadPhotoUseCase,
    private val loginUserUseCase: LoginUserUseCase,
    private val registerUserUseCase: RegisterUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val authRepository: RemoteAuthRepository,
    private val getProfileSettings: GetProfileSettingsTask,
    private val profileSettingsStore: ProfileSettingsStore,
    private val profileViewCacheRepository: ProfileViewCacheRepository,
    private val listDraftOffers: ListDraftOffersTask,
    private val getTrackedItems: GetTrackedItemsListTask,
    private val listNotificationsPage: ListNotificationsPageTask,
    private val markNotificationRead: MarkNotificationReadTask,
    private val markAllNotificationsRead: MarkAllNotificationsReadTask,
    private val notificationsSettingsStorage: TrackingNotificationsSettingsStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountProfileUiState())
    val uiState: StateFlow<AccountProfileUiState> = _uiState.asStateFlow()

    private var requestedUserId: Long? = null
    private var requestedRouteUserId: Long? = null
    private var requestedInvalidPublicTarget: Boolean = false

    fun load(targetUserId: Long? = requestedRouteUserId) {
        requestedRouteUserId = targetUserId
        requestedUserId = sanitizeTargetUserId(targetUserId)
        requestedInvalidPublicTarget = targetUserId != null && requestedUserId == null
        viewModelScope.launch {
            loadInternal(
                targetUserId = requestedUserId,
                invalidPublicTarget = requestedInvalidPublicTarget,
            )
        }
    }

    fun reload() {
        viewModelScope.launch {
            loadInternal(
                targetUserId = requestedUserId,
                invalidPublicTarget = requestedInvalidPublicTarget,
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { logoutUseCase() }
            load(requestedRouteUserId)
        }
    }

    suspend fun login(email: String, password: String): AuthResult =
        loginUserUseCase(email.trim(), password)

    suspend fun register(email: String, password: String, displayName: String?): AuthResult =
        registerUserUseCase(email.trim(), password, displayName?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun startPasswordReset(email: String): String? =
        authRepository.startPasswordReset(email.trim())

    suspend fun startPasswordResetByPhone(phone: String): String? =
        authRepository.startPasswordResetByPhone(phone.trim())

    suspend fun resetPassword(resetToken: String, newPassword: String) {
        authRepository.resetPassword(resetToken = resetToken.trim(), newPassword = newPassword)
    }

    suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String?): String =
        uploadPhotoUseCase(bytes, filename, contentType)

    suspend fun savePublicProfile(
        displayName: String,
        bio: String?,
        website: String?,
        city: String?,
        avatarUrl: String?,
    ): ProfileView {
        val profile = updatePublicProfile(
            com.example.shoppingassistant.domain.profile.ProfilePublicUpdatePayload(
                displayName = displayName,
                bio = bio,
                website = website,
                city = city,
                avatarUrl = avatarUrl,
            ),
        )
        applyProfile(profile)
        return profile
    }

    suspend fun updatePrivacy(
        publicProfileEnabled: Boolean,
        cityVisible: Boolean,
    ): ProfileView {
        val profile = updateProfilePrivacy(
            ProfilePrivacyUpdatePayload(
                publicProfileEnabled = publicProfileEnabled,
                cityVisible = cityVisible,
            ),
        )
        applyProfile(profile)
        return profile
    }

    suspend fun updateSellerDeliveryZones(
        zones: List<SellerDeliveryZone>,
    ): ProfileView {
        val profile = updateSellerDeliveryZonesTask(
            SellerDeliveryZonesUpdatePayload(zones = zones),
        )
        applyProfile(profile)
        return profile
    }

    suspend fun startPhoneChange(newPhone: String, currentPassword: String) {
        authRepository.startPhoneChange(
            newPhone = newPhone.trim(),
            currentPassword = currentPassword,
        )
        load(requestedUserId)
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult =
        changePasswordUseCase(oldPassword, newPassword).also { result ->
            if (result is AuthResult.Success) {
                load(requestedUserId)
            }
        }

    suspend fun requestEmailChange(email: String, currentPassword: String) {
        requestEmailChangeTask(email.trim(), currentPassword)
    }

    suspend fun startEmailVerification(): VerificationDeliveryStatus =
        authRepository.startEmailVerification()

    suspend fun confirmEmailVerification(token: String) {
        authRepository.confirmEmailVerification(token.trim())
        load(requestedUserId)
    }

    suspend fun confirmEmailChange(token: String): AuthResult =
        confirmEmailChangeTask(token.trim()).also { result ->
            if (result is AuthResult.Success) {
                load(requestedUserId)
            }
        }

    suspend fun deleteAccount(currentPassword: String): DeleteAccountReceipt? =
        deleteAccountTask(currentPassword)

    suspend fun startPhoneVerification(): VerificationDeliveryStatus =
        authRepository.startPhoneVerification()

    suspend fun confirmPhoneVerification(token: String) {
        authRepository.confirmPhoneVerification(token.trim())
        load(requestedUserId)
    }

    suspend fun confirmPhoneChange(token: String) {
        authRepository.confirmPhoneChange(token.trim())
        load(requestedUserId)
    }

    suspend fun restoreDeletedAccount(token: String) {
        authRepository.restoreDeletedAccount(token.trim())
        requestedRouteUserId = null
        requestedUserId = null
        requestedInvalidPublicTarget = false
        load(null)
    }

    fun updateAppSettings(settings: ProfileSettings) {
        viewModelScope.launch {
            val current = uiState.value.content as? ProfileRouteContent.Ready ?: return@launch
            val owner = current.ownerContext ?: return@launch
            profileSettingsStore.update(owner.authUser.id.toString(), settings)
            _uiState.value = _uiState.value.copy(
                content = current.copy(ownerContext = owner.copy(appSettings = settings)),
            )
        }
    }

    fun rememberProfileEntryMode(mode: ProfileEntryMode) {
        viewModelScope.launch {
            val current = uiState.value.content as? ProfileRouteContent.Ready ?: return@launch
            val owner = current.ownerContext ?: return@launch
            val updated = owner.appSettings.copy(profileEntryMode = mode)
            if (updated == owner.appSettings) return@launch
            profileSettingsStore.update(owner.authUser.id.toString(), updated)
            _uiState.value = _uiState.value.copy(
                content = current.copy(ownerContext = owner.copy(appSettings = updated)),
            )
        }
    }

    fun updatePushSettings(settings: TrackingNotificationsSettings) {
        notificationsSettingsStorage.set(settings)
        val current = uiState.value.content as? ProfileRouteContent.Ready ?: return
        val owner = current.ownerContext ?: return
        _uiState.value = _uiState.value.copy(
            content = current.copy(ownerContext = owner.copy(pushSettings = settings)),
        )
    }

    fun markNotificationRead(notificationId: Long) {
        viewModelScope.launch {
            runCatching { markNotificationRead.invoke(notificationId) }
            refreshOwnerOnlyDecorations()
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            runCatching { markAllNotificationsRead.invoke() }
            refreshOwnerOnlyDecorations()
        }
    }

    private suspend fun loadInternal(
        targetUserId: Long?,
        invalidPublicTarget: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            isStale = false,
            guestNotice = null,
        )

        if (invalidPublicTarget) {
            _uiState.value = AccountProfileUiState(
                isLoading = false,
                content = ProfileRouteContent.NotFound(null),
            )
            return
        }

        val hasPersistedAuthToken = !runCatching { authRepository.currentToken() }
            .getOrNull()
            .isNullOrBlank()
        val currentUserResult = runCatching { getCurrentUser() }
        val currentUser = currentUserResult.getOrNull()
        val cacheKey = resolveCacheKey(targetUserId = targetUserId, currentUser = currentUser)
        val cachedProfile = cacheKey
            ?.let { key -> runCatching { profileViewCacheRepository.getProfileView(key) }.getOrNull() }

        if (currentUser == null && targetUserId == null && currentUserResult.isSuccess && !hasPersistedAuthToken) {
            _uiState.value = AccountProfileUiState(
                isLoading = false,
                content = ProfileRouteContent.Guest,
            )
            return
        }

        val lookupTarget = when {
            currentUser != null && (targetUserId == null || targetUserId == currentUser.id) -> null
            else -> targetUserId
        }

        val loaded = if (lookupTarget == null && currentUser == null && !hasPersistedAuthToken) {
            Result.failure(currentUserResult.exceptionOrNull() ?: IllegalStateException("Unauthorized"))
        } else {
            runCatching { getProfileView(lookupTarget) }
        }

        loaded.onSuccess { result ->
            when (result) {
                is ProfileLookupResult.Found -> {
                    cacheKey?.let { key ->
                        runCatching { profileViewCacheRepository.saveProfileView(key, result.profile) }
                    }
                    val content = buildReadyContent(
                        profile = result.profile,
                        currentUser = currentUser ?: result.profile.toCachedAuthUser(),
                    )
                    _uiState.value = AccountProfileUiState(
                        isLoading = false,
                        content = content,
                    )
                }
                ProfileLookupResult.NotFound -> {
                    if (targetUserId == null) {
                        resetBrokenOwnerSession(
                            message = "Текущая сессия больше недействительна или аккаунт был удалён. Войдите снова.",
                            cacheKey = cacheKey,
                        )
                    } else {
                        _uiState.value = AccountProfileUiState(
                            isLoading = false,
                            content = ProfileRouteContent.NotFound(targetUserId),
                        )
                    }
                }
                ProfileLookupResult.PrivateUnavailable -> {
                    if (targetUserId == null) {
                        resetBrokenOwnerSession(
                            message = "Сессия сброшена. Публичный режим не может скрыть owner-профиль, поэтому нужен новый вход.",
                            cacheKey = cacheKey,
                        )
                    } else {
                        _uiState.value = AccountProfileUiState(
                            isLoading = false,
                            content = ProfileRouteContent.PrivateUnavailable(targetUserId),
                        )
                    }
                }
            }
        }.onFailure { throwable ->
            val ownerSessionFailure = targetUserId == null && isSessionFailure(throwable)
            if (ownerSessionFailure) {
                resetBrokenOwnerSession(
                    message = "Не удалось подтвердить текущую авторизацию. Войдите заново.",
                    cacheKey = cacheKey,
                )
            } else if (cachedProfile != null) {
                val cachedContent = buildReadyContent(
                    profile = cachedProfile,
                    currentUser = currentUser ?: cachedProfile.toCachedAuthUser(),
                )
                _uiState.value = AccountProfileUiState(
                    isLoading = false,
                    isStale = true,
                    content = cachedContent,
                )
            } else {
                _uiState.value = AccountProfileUiState(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Не удалось загрузить профиль",
                    content = if (currentUser == null && targetUserId == null && !hasPersistedAuthToken) {
                        ProfileRouteContent.Guest
                    } else {
                        ProfileRouteContent.Error
                    },
                )
            }
        }
    }

    private suspend fun resetBrokenOwnerSession(
        message: String,
        cacheKey: String?,
    ) {
        cacheKey?.let { key ->
            runCatching { profileViewCacheRepository.clearProfileView(key) }
        }
        runCatching { logoutUseCase() }
        _uiState.value = AccountProfileUiState(
            isLoading = false,
            guestNotice = message,
            content = ProfileRouteContent.Guest,
        )
    }

    private fun isSessionFailure(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty()
        return message.equals("Unauthorized", ignoreCase = true) ||
            message.contains("401") ||
            message.contains("Unauthorized", ignoreCase = true)
    }

    private suspend fun refreshOwnerOnlyDecorations() {
        val current = _uiState.value.content as? ProfileRouteContent.Ready ?: return
        val owner = current.ownerContext ?: return
        val refreshed = buildOwnerContext(owner.authUser, current.profile)
        _uiState.value = _uiState.value.copy(
            content = current.copy(ownerContext = refreshed),
        )
    }

    private suspend fun buildReadyContent(
        profile: ProfileView,
        currentUser: AuthUser?,
    ): ProfileRouteContent.Ready {
        val ownerContext = if (profile.scope == com.example.shoppingassistant.domain.profile.ProfileViewScope.OWNER) {
            buildOwnerContext(
                authUser = currentUser ?: profile.toCachedAuthUser()
                ?: AuthUser(
                    id = profile.userId,
                    email = profile.email ?: "",
                    displayName = profile.displayName,
                    phone = profile.phone,
                    avatarUrl = profile.avatarUrl,
                    city = profile.city,
                    emailVerified = profile.emailVerified,
                    createdAt = profile.joinedAtMillis,
                    photos = emptyList(),
                ),
                profile = profile,
            )
        } else {
            null
        }

        return ProfileRouteContent.Ready(
            profile = profile,
            ownerContext = ownerContext,
        )
    }

    private suspend fun buildOwnerContext(
        authUser: AuthUser,
        profile: ProfileView,
    ): OwnerProfileContext {
        val drafts = runCatching { listDraftOffers().first() }.getOrElse { emptyList() }
        val tracks = runCatching { getTrackedItems() }.getOrElse { emptyList() }
        val notificationsPage = runCatching { listNotificationsPage(limit = 50, offset = 0) }
            .getOrElse {
                SubscriptionNotificationsPage(
                    items = emptyList(),
                    total = 0,
                    offset = 0,
                    limit = 50,
                )
            }
        val unreadNotifications = notificationsPage.items.count { !it.isRead }
        val appSettings = runCatching { getProfileSettings(authUser.id.toString()) }
            .getOrElse { ProfileSettings() }
        val activityHub = OwnerActivityHub(
            activeListingsCount = profile.listingsCount,
            draftCount = drafts.size,
            trackedCount = tracks.size,
            trackedUnreadCount = tracks.sumOf { track -> track.stats.newEventsCount },
            notificationsUnread = unreadNotifications,
            notificationsPreview = notificationsPage.items
                .sortedByDescending { it.createdAtMillis }
                .take(3),
        )

        return OwnerProfileContext(
            authUser = authUser,
            appSettings = appSettings,
            pushSettings = notificationsSettingsStorage.get(),
            activityHub = activityHub,
            notificationsPage = notificationsPage,
            tracks = tracks,
            accountState = buildOwnerAccountModeState(
                authUser = authUser,
                profile = profile,
                activityHub = activityHub,
            ),
        )
    }

    private suspend fun applyProfile(profile: ProfileView) {
        val current = uiState.value.content as? ProfileRouteContent.Ready
        val ownerContext = current?.ownerContext?.authUser
            ?.mergePublicProfile(profile)
            ?.let { buildOwnerContext(it, profile) }
            ?: if (profile.scope == com.example.shoppingassistant.domain.profile.ProfileViewScope.OWNER) {
                profile.toCachedAuthUser()
                    ?.mergePublicProfile(profile)
                    ?.let { buildOwnerContext(it, profile) }
            } else {
                null
            }

        resolveCacheKey(targetUserId = requestedUserId, currentUser = ownerContext?.authUser)
            ?.let { key -> runCatching { profileViewCacheRepository.saveProfileView(key, profile) } }

        _uiState.value = AccountProfileUiState(
            isLoading = false,
            content = ProfileRouteContent.Ready(
                profile = profile,
                ownerContext = ownerContext,
            ),
        )
    }

    private fun resolveCacheKey(
        targetUserId: Long?,
        currentUser: AuthUser?,
    ): String? = when {
        targetUserId == null && currentUser == null -> null
        currentUser != null && (targetUserId == null || targetUserId == currentUser.id) -> "owner:${currentUser.id}"
        targetUserId != null -> "public:$targetUserId"
        else -> null
    }

    private fun sanitizeTargetUserId(targetUserId: Long?): Long? =
        targetUserId?.takeIf { it > 0L }
}

data class AccountProfileUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isStale: Boolean = false,
    val guestNotice: String? = null,
    val content: ProfileRouteContent = ProfileRouteContent.Guest,
)

sealed interface ProfileRouteContent {
    data object Guest : ProfileRouteContent
    data object Error : ProfileRouteContent

    data class Ready(
        val profile: ProfileView,
        val ownerContext: OwnerProfileContext? = null,
    ) : ProfileRouteContent

    data class NotFound(
        val targetUserId: Long?,
    ) : ProfileRouteContent

    data class PrivateUnavailable(
        val targetUserId: Long,
    ) : ProfileRouteContent
}

data class OwnerProfileContext(
    val authUser: AuthUser,
    val appSettings: ProfileSettings,
    val pushSettings: TrackingNotificationsSettings,
    val activityHub: OwnerActivityHub,
    val notificationsPage: SubscriptionNotificationsPage,
    val tracks: List<Track>,
    val accountState: OwnerAccountModeState,
)

data class OwnerActivityHub(
    val activeListingsCount: Int,
    val draftCount: Int,
    val trackedCount: Int,
    val trackedUnreadCount: Int,
    val notificationsUnread: Int,
    val notificationsPreview: List<SubscriptionNotification>,
)

internal fun ProfileView.toCachedAuthUser(): AuthUser? {
    if (scope != com.example.shoppingassistant.domain.profile.ProfileViewScope.OWNER) return null
    val safeEmail = email?.takeIf { it.isNotBlank() } ?: return null
    return AuthUser(
        id = userId,
        email = safeEmail,
        displayName = displayName,
        phone = phone,
        avatarUrl = avatarUrl,
        city = city,
        emailVerified = emailVerified,
        createdAt = joinedAtMillis,
        photos = emptyList(),
    )
}

private fun AuthUser.mergePublicProfile(profile: ProfileView): AuthUser =
    copy(
        displayName = profile.displayName,
        avatarUrl = profile.avatarUrl,
        city = profile.city,
    )
