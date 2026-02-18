package com.example.shoppingassistant.core.data.profile

import com.example.shoppingassistant.core.data.auth.RemoteAuthRepository
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.profile.ProfileRepository
import com.example.shoppingassistant.domain.profile.ProfileUpdatePayload

class ProfileRepositoryImpl(
    private val remoteAuthRepository: RemoteAuthRepository,
) : ProfileRepository {

    override suspend fun updateProfile(payload: ProfileUpdatePayload): AuthUser =
        remoteAuthRepository.updateProfile(payload)

    override suspend fun updatePhotos(photos: List<String>): AuthUser =
        remoteAuthRepository.updatePhotos(photos)

    override suspend fun requestEmailChange(newEmail: String) {
        remoteAuthRepository.startEmailChange(newEmail)
    }

    override suspend fun confirmEmailChange(token: String): AuthResult =
        remoteAuthRepository.confirmEmailChange(token)

    override suspend fun deleteAccount(): Boolean =
        remoteAuthRepository.deleteAccount()
}
