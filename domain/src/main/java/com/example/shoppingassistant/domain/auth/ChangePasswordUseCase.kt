package com.example.shoppingassistant.domain.auth

import com.example.shoppingassistant.domain.model.AuthResult

/**
 * Use-case смены пароля (старый -> новый).
 */
class ChangePasswordUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): AuthResult =
        repository.changePassword(oldPassword, newPassword)
}
