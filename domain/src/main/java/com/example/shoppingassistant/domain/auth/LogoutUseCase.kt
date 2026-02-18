package com.example.shoppingassistant.domain.auth

/**
 * Use-case выхода из профиля.
 */
class LogoutUseCase(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke() {
        repository.logout()
    }
}
