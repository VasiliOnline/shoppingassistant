package com.example.shoppingassistant.domain.profile

import kotlinx.serialization.Serializable

@Serializable
enum class ProfileThemePreference {
    LIGHT,
    DARK,
    SYSTEM,
}

@Serializable
enum class BottomBarStyle {
    SOLID,
    TRANSPARENT,
    BLUR,
    PRIMARY,
}

@Serializable
data class ProfileSettings(
    val languageCode: String = "",
    val countryCode: String = "",
    val theme: ProfileThemePreference = ProfileThemePreference.SYSTEM,
    val hideUndeliverable: Boolean = false,
    val photoPeekEnabled: Boolean = false,
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.SOLID,
)

@Serializable
data class ExternalLink(
    val title: String,
    val url: String,
)

@Serializable
data class ProfileSnapshot(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val city: String?,
    val phone: String?,
    val registeredAt: Long?,
    val emailVerified: Boolean = false,
    val emailVerifiedAt: Long? = null,
    val phoneVerifiedAt: Long? = null,
    val trustScore: Int? = null,
    val dealsCount: Int? = null,
    val favoritesCount: Int? = null,
    val alertsCount: Int? = null,
    val photos: List<String> = emptyList(),
    val settings: ProfileSettings = ProfileSettings(),
    val externalLinks: List<ExternalLink> = emptyList(),
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
