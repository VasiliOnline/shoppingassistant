package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Базовый профиль пользователя (продавца/покупателя).
 * Без привязки к конкретным слоям хранения.
 */
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val countryCode: String?,
    val city: String?,
    val rating: UserRating? = null,
    val preferences: UserPreferences = UserPreferences(),
)

@Serializable
data class UserRating(
    val value: Double,
    val count: Int,
)

/**
 * Предпочтения/бейджи пользователя — могут использоваться в карточках офферов.
 */
@Serializable
data class UserPreferences(
    val badges: List<UserBadge> = emptyList(),
    val shippingCountries: List<String> = emptyList(),
)

@Serializable
enum class UserBadge {
    SHIPS_WORLDWIDE,
    SHIPS_COUNTRY,
    OFFERS_DISCOUNT,
    FAST_RESPONSE,
    FAST_SHIPPING,
    VERIFIED,
}
