// Last synced: 2025-11-20 21:13
package com.example.shoppingassistant.domain.auth

import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser

/**
 * Доменный контракт авторизации.
 *
 * Не знает о том, как именно реализованы сессии (cookie, bearer, JWT и т.д.).
 */
interface AuthRepository {

    /**
     * Регистрация нового пользователя.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): AuthResult

    /**
     * Логин по email+паролю.
     */
    suspend fun login(
        email: String,
        password: String,
    ): AuthResult

    /**
     * "Текущий пользователь" для конкретной реализации:
     * на клиенте — тот, для кого хранится токен;
     * на backend-е сейчас не используется (вернёт null).
     */
    suspend fun getCurrentUser(): AuthUser?

    /**
     * Получение пользователя по идентификатору.
     * Нужен для backend-а, чтобы "раскрыть" userId из сессии.
     */
    suspend fun getUserById(id: Long): AuthUser?

    /**
     * Logout для текущей реализации.
     */
    suspend fun logout()

    /**
     * Текущий bearer-токен, сохранённый реализацией авторизации.
     *
     * Нужен для запросов к backend-у, которые требуют авторизации, но не знают
     * о формате хранения сессии.
     */
    suspend fun currentToken(): String?

    /**
     * Смена пароля по старому паролю.
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult
}
