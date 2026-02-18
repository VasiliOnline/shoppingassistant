package com.example.shoppingassistant.domain.auth

import com.example.shoppingassistant.domain.model.AuthResult

/**
 * Use-case регистрации пользователя.
 */
class RegisterUserUseCase(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(
        email: String,
        password: String,
        displayName: String?,
    ): AuthResult {
        return repository.register(email, password, displayName)
    }
}
