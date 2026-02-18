package com.example.shoppingassistant.domain.profile

import com.example.shoppingassistant.domain.model.AuthUser

class UpdateProfileTask(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(payload: ProfileUpdatePayload): AuthUser =
        repository.updateProfile(payload)
}
