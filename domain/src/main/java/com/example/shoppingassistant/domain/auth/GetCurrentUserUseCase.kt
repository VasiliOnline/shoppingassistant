package com.example.shoppingassistant.domain.auth

import com.example.shoppingassistant.domain.model.AuthUser

/**
 * Use-case получения текущего авторизованного пользователя.
 */
class GetCurrentUserUseCase(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(): AuthUser? {
        return repository.getCurrentUser()
    }
}
