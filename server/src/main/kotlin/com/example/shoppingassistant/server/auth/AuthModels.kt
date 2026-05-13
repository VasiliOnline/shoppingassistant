// Last synced: 2025-11-23 18:38
package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.domain.model.AuthUser
import kotlinx.serialization.Serializable

/**
 * DTO для запроса регистрации на backend-е.
 * Это тонкая HTTP-модель, не доменная.
 */
@Serializable
data class AuthRegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null,
)

/**
 * DTO для запроса логина.
 */
@Serializable
data class AuthLoginRequest(
    val email: String,
    val password: String,
)

/**
 * Единый формат ошибки для /api/auth.
 */
@Serializable
data class AuthErrorResponse(
    val error: String,
    val message: String,
    val field: String? = null,
)

/**
 * Ответ с данными авторизованного пользователя + bearer-токен сессии.
 * Это HTTP-обёртка над доменной моделью AuthUser.
 */
@Serializable
data class AuthUserResponse(
    val id: Long,
    val email: String,
    val displayName: String,
    val phone: String? = null,
    val pendingPhone: String? = null,
    val pendingPhoneRequestedAt: Long? = null,
    val avatarUrl: String? = null,
    val city: String? = null,
    val token: String? = null,
    val emailVerified: Boolean = false,
    val emailVerifiedAt: Long? = null,
    val phoneVerifiedAt: Long? = null,
    val phoneVerified: Boolean = false,
    val createdAt: Long? = null,
    val photos: List<String> = emptyList(),
)

/**
 * DTO для старта восстановления пароля.
 */
@Serializable
data class PasswordResetStartRequest(
    val email: String,
)

/**
 * Ответ на старт восстановления пароля.
 *
 * resetToken возвращаем только для dev/debug.
 * В проде его можно не отдавать и отправлять по e-mail.
 */
@Serializable
data class PasswordResetStartResponse(
    val status: String,
    val resetToken: String? = null,
)

/**
 * DTO для старта восстановления по телефону.
 */
@Serializable
data class PasswordResetStartByPhoneRequest(
    val phone: String,
)

/**
 * DTO для завершения восстановления пароля.
 */
@Serializable
data class PasswordResetCompleteRequest(
    val token: String,
    val newPassword: String,
)

/**
 * Ответ на завершение восстановления пароля.
 */
@Serializable
data class PasswordResetCompleteResponse(
    val status: String,
)

/**
 * DTO для старта верификации email (ответ простой OK).
 */
@Serializable
data class EmailVerificationStartResponse(
    val status: String,
)

/**
 * DTO для подтверждения email по токену.
 */
@Serializable
data class EmailVerificationConfirmRequest(
    val token: String,
)

@Serializable
data class PhoneVerificationConfirmRequest(
    val token: String,
)
/**
 * DTO для смены пароля по старому.
 */
@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

@Serializable
data class ChangeEmailStartRequest(
    val newEmail: String,
    val currentPassword: String,
)

@Serializable
data class ChangeEmailConfirmRequest(
    val token: String,
)

@Serializable
data class ChangePhoneStartRequest(
    val newPhone: String,
    val currentPassword: String,
)

@Serializable
data class ChangePhoneConfirmRequest(
    val token: String,
)

@Serializable
data class DeleteAccountRequest(
    val currentPassword: String,
)

@Serializable
data class DeleteAccountResponse(
    val status: String,
    val deleteAfter: Long? = null,
    val restoreToken: String? = null,
)

@Serializable
data class RestoreAccountRequest(
    val token: String,
)

/**
 * Маппинг доменной модели в HTTP-ответ.
 */
fun AuthUser.toResponse(token: String? = null): AuthUserResponse {
    return AuthUserResponse(
        id = id,
        email = email,
        displayName = displayName ?: email,
        phone = phone,
        pendingPhone = pendingPhone,
        pendingPhoneRequestedAt = pendingPhoneRequestedAt,
        avatarUrl = avatarUrl,
        city = city,
        token = token,
        emailVerified = emailVerified,
        emailVerifiedAt = emailVerifiedAt,
        phoneVerifiedAt = phoneVerifiedAt,
        phoneVerified = phoneVerifiedAt != null,
        createdAt = createdAt,
        photos = photos,
    )
}
