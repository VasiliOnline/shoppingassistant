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
    val bio: String? = null,
    val website: String? = null,
)

data class DeleteAccountReceipt(
    val deleteAfter: Long? = null,
    val restoreToken: String? = null,
)

interface ProfileRepository {
    suspend fun updateProfile(payload: ProfileUpdatePayload): AuthUser
    suspend fun updatePhotos(photos: List<String>): AuthUser
    suspend fun requestEmailChange(newEmail: String, currentPassword: String)
    suspend fun confirmEmailChange(token: String): AuthResult
    suspend fun deleteAccount(currentPassword: String): DeleteAccountReceipt?
}
