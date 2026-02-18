// Last synced: 2025-11-20 21:13
package com.example.shoppingassistant.server.profile

import com.example.shoppingassistant.domain.model.AuthUser
import kotlinx.serialization.Serializable

/**
 * Минимальное представление профиля для /api/profile/me.
 */
@Serializable
data class ProfileSummaryResponse(
    val id: Long,
    val email: String,
    val displayName: String,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val city: String? = null,
    val emailVerified: Boolean = false,
    val emailVerifiedAt: Long? = null,
    val phoneVerifiedAt: Long? = null,
    val phoneVerified: Boolean = false,
    val createdAt: Long? = null,
    val photos: List<String> = emptyList(),
)

/**
 * Запрос на обновление основных полей профиля.
 */
@Serializable
data class ProfileUpdateRequest(
    val displayName: String? = null,
    val city: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
)

/**
 * Обновление набора фото пользователя.
 */
@Serializable
data class ProfilePhotosUpdateRequest(
    val photos: List<String> = emptyList(),
)

/**
 * Маппинг доменного пользователя в HTTP-ответ профиля.
 */
fun AuthUser.toProfileSummaryResponse(): ProfileSummaryResponse =
    ProfileSummaryResponse(
        id = id,
        email = email,
        displayName = displayName ?: email,
        phone = phone,
        avatarUrl = avatarUrl,
        city = city,
        emailVerified = emailVerified,
        emailVerifiedAt = emailVerifiedAt,
        phoneVerifiedAt = phoneVerifiedAt,
        phoneVerified = phoneVerifiedAt != null,
        createdAt = createdAt,
        photos = photos,
    )
