package com.example.shoppingassistant.domain.profile

class DeleteAccountTask(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(): Boolean = repository.deleteAccount()
}
