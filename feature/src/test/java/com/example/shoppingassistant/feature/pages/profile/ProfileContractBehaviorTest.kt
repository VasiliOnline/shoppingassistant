package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.profile.ProfileEntryMode
import com.example.shoppingassistant.domain.profile.ProfileView
import com.example.shoppingassistant.domain.profile.ProfileViewScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileContractBehaviorTest {

    @Test
    fun remembered_account_mode_applies_to_owner_root_only() {
        assertEquals(
            ProfileEntryMode.ACCOUNT,
            resolveProfileEntryMode(
                targetUserId = null,
                profileScope = ProfileViewScope.OWNER,
                savedMode = ProfileEntryMode.ACCOUNT,
            ),
        )
        assertEquals(
            ProfileEntryMode.PUBLIC,
            resolveProfileEntryMode(
                targetUserId = 42L,
                profileScope = ProfileViewScope.PUBLIC,
                savedMode = ProfileEntryMode.ACCOUNT,
            ),
        )
        assertEquals(
            ProfileEntryMode.PUBLIC,
            resolveProfileEntryMode(
                targetUserId = null,
                profileScope = ProfileViewScope.PUBLIC,
                savedMode = ProfileEntryMode.ACCOUNT,
            ),
        )
    }

    @Test
    fun account_state_marks_unverified_contacts_as_attention_items() {
        val state = buildOwnerAccountModeState(
            authUser = AuthUser(
                id = 7,
                email = "user@test.com",
                displayName = "User",
                phone = "+79990000000",
                avatarUrl = null,
                city = null,
                emailVerified = false,
                phoneVerifiedAt = null,
            ),
            profile = ProfileView(
                userId = 7,
                scope = ProfileViewScope.OWNER,
                displayName = "User",
            ),
            activityHub = OwnerActivityHub(
                activeListingsCount = 1,
                draftCount = 0,
                trackedCount = 0,
                trackedUnreadCount = 0,
                notificationsUnread = 0,
                notificationsPreview = emptyList(),
            ),
        )

        assertTrue(state.needsAttention.contains(AccountAttentionItem.VERIFY_EMAIL))
        assertTrue(state.needsAttention.contains(AccountAttentionItem.VERIFY_PHONE))
        assertFalse(state.isEmptyModules)
    }

    @Test
    fun account_state_detects_empty_modules() {
        val state = buildOwnerAccountModeState(
            authUser = AuthUser(
                id = 7,
                email = "user@test.com",
                displayName = "User",
                phone = null,
                avatarUrl = null,
                city = null,
                emailVerified = true,
            ),
            profile = ProfileView(
                userId = 7,
                scope = ProfileViewScope.OWNER,
                displayName = "User",
            ),
            activityHub = OwnerActivityHub(
                activeListingsCount = 0,
                draftCount = 0,
                trackedCount = 0,
                trackedUnreadCount = 0,
                notificationsUnread = 0,
                notificationsPreview = emptyList(),
            ),
        )

        assertTrue(state.isEmptyModules)
        assertTrue(state.needsAttention.contains(AccountAttentionItem.ADD_PHONE))
    }

    @Test
    fun account_state_prioritizes_pending_phone_confirmation() {
        val state = buildOwnerAccountModeState(
            authUser = AuthUser(
                id = 8,
                email = "user@test.com",
                displayName = "User",
                phone = "+79990000000",
                pendingPhone = "+79991112233",
                avatarUrl = null,
                city = null,
                emailVerified = true,
                phoneVerifiedAt = 1L,
            ),
            profile = ProfileView(
                userId = 8,
                scope = ProfileViewScope.OWNER,
                displayName = "User",
            ),
            activityHub = OwnerActivityHub(
                activeListingsCount = 0,
                draftCount = 1,
                trackedCount = 0,
                trackedUnreadCount = 0,
                notificationsUnread = 0,
                notificationsPreview = emptyList(),
            ),
        )

        assertTrue(state.needsAttention.contains(AccountAttentionItem.CONFIRM_PENDING_PHONE))
        assertFalse(state.needsAttention.contains(AccountAttentionItem.ADD_PHONE))
    }

    @Test
    fun account_completion_tracks_profile_readiness_and_security() {
        val completion = buildAccountCompletionState(
            authUser = AuthUser(
                id = 11,
                email = "user@test.com",
                displayName = "User",
                phone = "+79990000000",
                avatarUrl = null,
                city = "Москва",
                emailVerified = true,
                phoneVerifiedAt = 1L,
            ),
            profile = ProfileView(
                userId = 11,
                scope = ProfileViewScope.OWNER,
                displayName = "User",
                avatarUrl = "https://cdn/avatar.jpg",
                bio = "Продаю электронику",
                city = "Москва",
                publicProfileEnabled = true,
                listingsCount = 3,
            ),
        )

        assertEquals(100, completion.percent)
        assertEquals(AccountSecurityLevel.STRONG, completion.securityLevel)
        assertTrue(completion.nextActions.isEmpty())
    }

    @Test
    fun public_trust_stage_respects_real_signals_only() {
        assertEquals(
            PublicTrustStage.NEW_PROFILE,
            resolvePublicTrustStage(
                ProfileView(
                    userId = 1,
                    scope = ProfileViewScope.PUBLIC,
                    displayName = "New",
                ),
            ),
        )
        assertEquals(
            PublicTrustStage.ACTIVE_SELLER,
            resolvePublicTrustStage(
                ProfileView(
                    userId = 2,
                    scope = ProfileViewScope.PUBLIC,
                    displayName = "Seller",
                    listingsCount = 2,
                ),
            ),
        )
        assertEquals(
            PublicTrustStage.TRUSTED_SELLER,
            resolvePublicTrustStage(
                ProfileView(
                    userId = 3,
                    scope = ProfileViewScope.PUBLIC,
                    displayName = "Rated",
                    ratingCount = 4,
                ),
            ),
        )
    }

    @Test
    fun compact_sticky_identity_appears_after_scrolling_past_header() {
        val profile = ProfileView(
            userId = 1,
            scope = ProfileViewScope.PUBLIC,
            displayName = "Vasya",
        )

        assertFalse(
            shouldShowCompactStickyIdentity(
                profile = profile,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
        assertTrue(
            shouldShowCompactStickyIdentity(
                profile = profile,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }
}
