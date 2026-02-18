package com.example.shoppingassistant.domain.auth

import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.AuthResult

/**
 * Use-case входа по email + password.
 */
class LoginUserUseCase(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(
        email: String,
        password: String,
    ): AuthResult {
        return repository.login(email, password)
    }
}
