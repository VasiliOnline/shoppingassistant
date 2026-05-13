package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.profile.ProfileEntryMode
import com.example.shoppingassistant.domain.profile.ProfileVerificationBadge
import com.example.shoppingassistant.domain.profile.ProfileView
import com.example.shoppingassistant.domain.profile.ProfileViewScope

enum class AccountAttentionItem {
    VERIFY_EMAIL,
    ADD_PHONE,
    VERIFY_PHONE,
    CONFIRM_PENDING_PHONE,
}

enum class AccountHealthAction {
    VERIFY_EMAIL,
    ADD_PHONE,
    VERIFY_PHONE,
    CONFIRM_PENDING_PHONE,
    ENABLE_PUBLIC_PROFILE,
    ADD_AVATAR,
    ADD_BIO,
    ADD_CITY,
    PUBLISH_FIRST_LISTING,
}

enum class AccountSecurityLevel {
    BASIC,
    PROTECTED,
    STRONG,
}

enum class PublicTrustStage {
    NEW_PROFILE,
    ACTIVE_SELLER,
    VERIFIED_PROFILE,
    TRUSTED_SELLER,
}

data class AccountCompletionState(
    val percent: Int = 0,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val securityLevel: AccountSecurityLevel = AccountSecurityLevel.BASIC,
    val nextActions: List<AccountHealthAction> = emptyList(),
)

data class OwnerAccountModeState(
    val needsAttention: List<AccountAttentionItem> = emptyList(),
    val isEmptyModules: Boolean = false,
    val completion: AccountCompletionState = AccountCompletionState(),
)

internal fun resolveProfileEntryMode(
    targetUserId: Long?,
    profileScope: ProfileViewScope?,
    savedMode: ProfileEntryMode,
): ProfileEntryMode = when {
    targetUserId != null -> ProfileEntryMode.PUBLIC
    profileScope != ProfileViewScope.OWNER -> ProfileEntryMode.PUBLIC
    else -> savedMode
}

internal fun buildOwnerAccountModeState(
    authUser: AuthUser,
    profile: ProfileView,
    activityHub: OwnerActivityHub,
): OwnerAccountModeState {
    val needsAttention = buildList {
        if (!authUser.emailVerified) add(AccountAttentionItem.VERIFY_EMAIL)
        when {
            !authUser.pendingPhone.isNullOrBlank() -> add(AccountAttentionItem.CONFIRM_PENDING_PHONE)
            authUser.phone.isNullOrBlank() -> add(AccountAttentionItem.ADD_PHONE)
            authUser.phoneVerifiedAt == null -> add(AccountAttentionItem.VERIFY_PHONE)
        }
    }
    val completion = buildAccountCompletionState(
        authUser = authUser,
        profile = profile,
    )

    val isEmptyModules =
        activityHub.activeListingsCount == 0 &&
            activityHub.draftCount == 0 &&
            activityHub.trackedCount == 0 &&
            activityHub.notificationsUnread == 0 &&
            activityHub.notificationsPreview.isEmpty()

    return OwnerAccountModeState(
        needsAttention = needsAttention,
        isEmptyModules = isEmptyModules,
        completion = completion,
    )
}

internal fun buildAccountCompletionState(
    authUser: AuthUser,
    profile: ProfileView,
): AccountCompletionState {
    val checklist = listOf(
        profile.publicProfileEnabled,
        authUser.emailVerified,
        authUser.phoneVerifiedAt != null && authUser.pendingPhone.isNullOrBlank(),
        !profile.avatarUrl.isNullOrBlank(),
        !profile.bio.isNullOrBlank(),
        !profile.city.isNullOrBlank(),
        profile.listingsCount > 0,
    )
    val completedCount = checklist.count { it }
    val totalCount = checklist.size
    val percent = if (totalCount == 0) 0 else ((completedCount * 100f) / totalCount).toInt()

    val nextActions = buildList {
        if (!authUser.emailVerified) add(AccountHealthAction.VERIFY_EMAIL)
        when {
            !authUser.pendingPhone.isNullOrBlank() -> add(AccountHealthAction.CONFIRM_PENDING_PHONE)
            authUser.phone.isNullOrBlank() -> add(AccountHealthAction.ADD_PHONE)
            authUser.phoneVerifiedAt == null -> add(AccountHealthAction.VERIFY_PHONE)
        }
        if (!profile.publicProfileEnabled) add(AccountHealthAction.ENABLE_PUBLIC_PROFILE)
        if (profile.avatarUrl.isNullOrBlank()) add(AccountHealthAction.ADD_AVATAR)
        if (profile.bio.isNullOrBlank()) add(AccountHealthAction.ADD_BIO)
        if (profile.city.isNullOrBlank()) add(AccountHealthAction.ADD_CITY)
        if (profile.listingsCount == 0) add(AccountHealthAction.PUBLISH_FIRST_LISTING)
    }

    return AccountCompletionState(
        percent = percent,
        completedCount = completedCount,
        totalCount = totalCount,
        securityLevel = resolveAccountSecurityLevel(authUser),
        nextActions = nextActions,
    )
}

internal fun resolveAccountSecurityLevel(
    authUser: AuthUser,
): AccountSecurityLevel = when {
    authUser.emailVerified && authUser.phoneVerifiedAt != null && authUser.pendingPhone.isNullOrBlank() ->
        AccountSecurityLevel.STRONG

    authUser.emailVerified || authUser.phoneVerifiedAt != null || !authUser.pendingPhone.isNullOrBlank() ->
        AccountSecurityLevel.PROTECTED

    else -> AccountSecurityLevel.BASIC
}

internal fun resolvePublicTrustStage(
    profile: ProfileView,
): PublicTrustStage = when {
    profile.ratingCount > 0 || profile.verificationBadges.contains(ProfileVerificationBadge.TRUSTED_SELLER) ->
        PublicTrustStage.TRUSTED_SELLER

    profile.listingsCount > 0 &&
        (profile.emailVerified || profile.phoneVerified || profile.verificationBadges.isNotEmpty()) ->
        PublicTrustStage.VERIFIED_PROFILE

    profile.listingsCount > 0 ->
        PublicTrustStage.ACTIVE_SELLER

    else ->
        PublicTrustStage.NEW_PROFILE
}

internal fun shouldShowCompactStickyIdentity(
    profile: ProfileView,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    if (profile.scope != ProfileViewScope.PUBLIC && profile.scope != ProfileViewScope.OWNER) return false
    return firstVisibleItemIndex > 1 || (firstVisibleItemIndex == 1 && firstVisibleItemScrollOffset > 24)
}
