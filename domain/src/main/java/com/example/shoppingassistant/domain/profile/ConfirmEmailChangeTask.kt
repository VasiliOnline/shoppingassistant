package com.example.shoppingassistant.domain.profile

import com.example.shoppingassistant.domain.model.AuthResult

class ConfirmEmailChangeTask(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(token: String): AuthResult =
        repository.confirmEmailChange(token)
}
