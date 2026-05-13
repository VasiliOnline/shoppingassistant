package com.example.shoppingassistant.domain.profile

import kotlinx.serialization.Serializable

@Serializable
enum class ProfileViewScope {
    OWNER,
    PUBLIC,
}

@Serializable
enum class ProfileVerificationBadge {
    EMAIL_VERIFIED,
    PHONE_VERIFIED,
    TRUSTED_SELLER,
}

@Serializable
data class ProfileListingPreviewItem(
    val offerId: String,
    val title: String,
    val priceMajor: Double? = null,
    val currency: String = "USD",
    val imageUrl: String? = null,
    val updatedAtMillis: Long? = null,
    val status: String = "ACTIVE",
    val sourceName: String? = null,
)

@Serializable
data class ProfileView(
    val userId: Long,
    val scope: ProfileViewScope = ProfileViewScope.PUBLIC,
    val email: String? = null,
    val phone: String? = null,
    val displayName: String,
    val bio: String? = null,
    val website: String? = null,
    val avatarUrl: String? = null,
    val city: String? = null,
    val joinedAtMillis: Long? = null,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val publicProfileEnabled: Boolean = true,
    val cityVisible: Boolean = true,
    val ratingAverage: Double? = null,
    val ratingCount: Int = 0,
    val verificationBadges: List<ProfileVerificationBadge> = emptyList(),
    val listingsCount: Int = 0,
    val listingsPreview: List<ProfileListingPreviewItem> = emptyList(),
    val sellerDeliveryZones: List<SellerDeliveryZone> = emptyList(),
)

@Serializable
data class ProfilePublicUpdatePayload(
    val displayName: String,
    val bio: String? = null,
    val website: String? = null,
    val avatarUrl: String? = null,
    val city: String? = null,
)

@Serializable
data class ProfilePrivacyUpdatePayload(
    val publicProfileEnabled: Boolean = true,
    val cityVisible: Boolean = true,
)

@Serializable
data class SellerDeliveryZonesUpdatePayload(
    val zones: List<SellerDeliveryZone> = emptyList(),
)

sealed interface ProfileLookupResult {
    data class Found(
        val profile: ProfileView,
    ) : ProfileLookupResult

    data object NotFound : ProfileLookupResult

    data object PrivateUnavailable : ProfileLookupResult
}

interface ProfileContractRepository {
    suspend fun getProfile(targetUserId: Long? = null): ProfileLookupResult
    suspend fun updatePublicProfile(payload: ProfilePublicUpdatePayload): ProfileView
    suspend fun updatePrivacy(payload: ProfilePrivacyUpdatePayload): ProfileView
    suspend fun updateSellerDeliveryZones(payload: SellerDeliveryZonesUpdatePayload): ProfileView
}

interface ProfileViewCacheRepository {
    suspend fun getProfileView(key: String): ProfileView?
    suspend fun saveProfileView(key: String, value: ProfileView)
    suspend fun clearProfileView(key: String)
}

class GetProfileViewTask(
    private val repository: ProfileContractRepository,
) {
    suspend operator fun invoke(targetUserId: Long? = null): ProfileLookupResult =
        repository.getProfile(targetUserId)
}

class UpdatePublicProfileTask(
    private val repository: ProfileContractRepository,
) {
    suspend operator fun invoke(payload: ProfilePublicUpdatePayload): ProfileView =
        repository.updatePublicProfile(payload)
}

class UpdateProfilePrivacyTask(
    private val repository: ProfileContractRepository,
) {
    suspend operator fun invoke(payload: ProfilePrivacyUpdatePayload): ProfileView =
        repository.updatePrivacy(payload)
}

class UpdateSellerDeliveryZonesTask(
    private val repository: ProfileContractRepository,
) {
    suspend operator fun invoke(payload: SellerDeliveryZonesUpdatePayload): ProfileView =
        repository.updateSellerDeliveryZones(payload)
}
