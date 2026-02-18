// Last synced: 2025-11-20 21:13
package com.example.shoppingassistant.core.data.auth

import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser

/**
 * Простая in-memory реализация AuthRepository для клиента.
 *
 * Удобна для оффлайна/тестов без backend-а.
 */
class InMemoryAuthRepository : AuthRepository {

    private val usersByEmail = mutableMapOf<String, AuthUser>()
    private var autoIncrementId: Long = 1L
    private var currentUser: AuthUser? = null

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): AuthResult {
        if (usersByEmail.containsKey(email)) {
            return AuthResult.Error(AuthError.EMAIL_ALREADY_EXISTS)
        }

        val user = AuthUser(
            id = autoIncrementId++,
            email = email,
            displayName = displayName ?: email,
            phone = null,
            avatarUrl = null,
            city = null,
            emailVerified = false,
            createdAt = System.currentTimeMillis(),
            photos = emptyList(),
        )

        usersByEmail[email] = user
        currentUser = user

        return AuthResult.Success(user)
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthResult {
        val user = usersByEmail[email]
            ?: return AuthResult.Error(AuthError.INVALID_CREDENTIALS)

        currentUser = user
        return AuthResult.Success(user)
    }

    override suspend fun getCurrentUser(): AuthUser? = currentUser

    override suspend fun getUserById(id: Long): AuthUser? =
        usersByEmail.values.firstOrNull { it.id == id }

    override suspend fun logout() {
        currentUser = null
    }

    override suspend fun currentToken(): String? = null

    override suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult {
        return AuthResult.Success(currentUser ?: return AuthResult.Error(AuthError.INVALID_CREDENTIALS))
    }
}
