package com.example.shoppingassistant.domain.profile

class RequestEmailChangeTask(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(newEmail: String, currentPassword: String) {
        repository.requestEmailChange(newEmail, currentPassword)
    }
}
