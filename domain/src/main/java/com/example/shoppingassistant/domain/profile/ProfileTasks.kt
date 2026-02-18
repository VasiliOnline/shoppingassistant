package com.example.shoppingassistant.domain.profile

import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser

/**
 * Контракты доменной области Profile.
 */
data class ProfileUpdatePayload(
    val displayName: String? = null,
    val city: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
)

interface ProfileRepository {
    suspend fun updateProfile(payload: ProfileUpdatePayload): AuthUser
    suspend fun updatePhotos(photos: List<String>): AuthUser
    suspend fun requestEmailChange(newEmail: String)
    suspend fun confirmEmailChange(token: String): AuthResult
    suspend fun deleteAccount(): Boolean
}
