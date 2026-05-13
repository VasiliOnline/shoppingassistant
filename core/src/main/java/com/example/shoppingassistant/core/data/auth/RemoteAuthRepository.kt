// Last synced: 2025-11-24 16:44
package com.example.shoppingassistant.core.data.auth

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.profile.DeleteAccountReceipt
import com.example.shoppingassistant.domain.profile.ProfileUpdatePayload
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * Клиентский репозиторий авторизации поверх HTTP backend-а.
 *
 * Используется на Android:
 *  - register/login — создают сессию и сохраняют bearer-токен;
 *  - getCurrentUser/getUserById — читают профиль из /api/profile/me;
 *  - logout — инвалидирует сессию на backend-е.
 *
 * В этом же классе держим вспомогательный метод resetPassword для reset-flow.
 */
class RemoteAuthRepository(
    private val backendClient: BackendClient,
    private val tokenStorage: AuthTokenStorage,
) : AuthRepository {

    @Volatile
    private var authToken: String? = null
    @Volatile
    private var tokenLoaded: Boolean = false

    private suspend fun ensureTokenLoaded() {
        if (tokenLoaded) return
        authToken = tokenStorage.get()
        tokenLoaded = true
    }

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): AuthResult {
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                AuthRegisterRequestDto(
                    email = email,
                    password = password,
                    displayName = displayName,
                ),
            )
        }

        return when (response.status) {
            HttpStatusCode.Created,
            HttpStatusCode.OK -> {
                val dto: AuthUserResponseDto = response.body()
                authToken = dto.token
                tokenLoaded = true
                dto.token?.let { tokenStorage.save(it) }
                AuthResult.Success(dto.toDomain())
            }

            HttpStatusCode.Conflict -> {
                AuthResult.Error(AuthError.EMAIL_ALREADY_EXISTS)
            }

            else -> {
                AuthResult.Error(AuthError.UNKNOWN)
            }
        }
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AuthResult {
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                AuthLoginRequestDto(
                    email = email,
                    password = password,
                ),
            )
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: AuthUserResponseDto = response.body()
                authToken = dto.token
                tokenLoaded = true
                dto.token?.let { tokenStorage.save(it) }
                AuthResult.Success(dto.toDomain())
            }

            HttpStatusCode.Unauthorized -> {
                AuthResult.Error(AuthError.INVALID_CREDENTIALS)
            }

            HttpStatusCode.BadRequest,
            HttpStatusCode.Conflict -> {
                AuthResult.Error(AuthError.UNKNOWN)
            }

            else -> {
                AuthResult.Error(AuthError.UNKNOWN)
            }
        }
    }

    override suspend fun getCurrentUser(): AuthUser? {
        ensureTokenLoaded()
        val token = authToken ?: return null
        val client = backendClient.client

        val response: HttpResponse = client.get("$baseUrl/api/profile/me") {
            header("Authorization", "Bearer $token")
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: ProfileSummaryResponseDto = response.body()
                dto.toDomain()
            }

            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                null
            }

            else -> null
        }
    }

    override suspend fun getUserById(id: Long): AuthUser? {
        val current = getCurrentUser()
        return if (current?.id == id) current else null
    }

    override suspend fun currentToken(): String? {
        ensureTokenLoaded()
        return authToken
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult {
        ensureTokenLoaded()
        val token = authToken ?: return AuthResult.Error(AuthError.INVALID_CREDENTIALS)
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/change-password") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                ChangePasswordRequestDto(
                    oldPassword = oldPassword,
                    newPassword = newPassword,
                ),
            )
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: AuthUserResponseDto = response.body()
                // backend может выдать новый токен после смены пароля — сохраняем его,
                // иначе оставляем старый, чтобы не выбрасывать из сессии.
                dto.token?.let {
                    authToken = it
                    tokenLoaded = true
                    tokenStorage.save(it)
                }
                AuthResult.Success(dto.toDomain())
            }

            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                AuthResult.Error(AuthError.INVALID_CREDENTIALS)
            }

            HttpStatusCode.BadRequest -> AuthResult.Error(AuthError.INVALID_CREDENTIALS)

            else -> AuthResult.Error(AuthError.UNKNOWN)
        }
    }

    /**
     * Обновление основных полей профиля (имя, город, телефон, аватар).
     */
    suspend fun updateProfile(payload: ProfileUpdatePayload): AuthUser {
        ensureTokenLoaded()
        val token = authToken ?: throw IllegalStateException("Требуется авторизация")
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/profile/update") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                ProfileUpdateRequestDto(
                    displayName = payload.displayName?.trim()?.takeIf { it.isNotEmpty() },
                    city = payload.city?.trim()?.takeIf { it.isNotEmpty() },
                    phone = payload.phone?.trim()?.takeIf { it.isNotEmpty() },
                    avatarUrl = payload.avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
                    bio = payload.bio?.trim()?.takeIf { it.isNotEmpty() },
                    website = payload.website?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: ProfileSummaryResponseDto = response.body()
                dto.toDomain()
            }

            HttpStatusCode.BadRequest -> {
                throw IllegalStateException(parseProfileErrorMessage(response, "Некорректные данные профиля"))
            }

            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                throw IllegalStateException("Требуется авторизация")
            }

            else -> throw IllegalStateException(
                "Не удалось обновить профиль (код ${response.status.value}).",
            )
        }
    }

    /**
     * Сохранение списка URL фотографий пользователя.
     */
    suspend fun updatePhotos(photos: List<String>): AuthUser {
        ensureTokenLoaded()
        val token = authToken ?: throw IllegalStateException("Требуется авторизация")
        val client = backendClient.client

        val sanitized = photos.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)

        val response: HttpResponse = client.post("$baseUrl/api/profile/photos") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ProfilePhotosUpdateRequestDto(photos = sanitized))
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: ProfileSummaryResponseDto = response.body()
                dto.toDomain()
            }

            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                throw IllegalStateException("Требуется авторизация")
            }

            else -> throw IllegalStateException(
                "Не удалось сохранить фото (код ${response.status.value}).",
            )
        }
    }

    override suspend fun logout() {
        ensureTokenLoaded()
        val token = authToken
        try {
            if (!token.isNullOrBlank()) {
                val client = backendClient.client
                client.post("$baseUrl/api/auth/logout") {
                    header("Authorization", "Bearer $token")
                }
            }
        } finally {
            authToken = null
            tokenLoaded = true
            tokenStorage.clear()
        }
    }

    /**
     * Старт восстановления пароля по email.
     *
     * Возвращает resetToken (если backend его отдаёт в dev), иначе null.
     * На проде можно игнорировать значение и просто показать "проверьте почту".
     */
    suspend fun startPasswordReset(email: String): String? {
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(
                PasswordResetStartRequestDto(email = email),
            )
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: PasswordResetStartResponseDto = response.body()
                dto.resetToken
            }

            else -> {
                val message = runCatching {
                    response.body<ResetPasswordErrorResponseDto>().message
                }.getOrNull()
                throw IllegalStateException(
                    message ?: "Не удалось инициировать восстановление (код ${response.status.value}).",
                )
            }
        }
    }

    /**
     * Старт восстановления пароля по телефону (SMS/звонок).
     *
     * Не раскрывает, существует ли номер. Возвращает dev resetToken, если backend его отдал.
     */
    suspend fun startPasswordResetByPhone(phone: String): String? {
        val client = backendClient.client
        val normalized = phone.filter { it.isDigit() }

        val response: HttpResponse = client.post("$baseUrl/api/auth/forgot-password/phone") {
            contentType(ContentType.Application.Json)
            setBody(
                PasswordResetStartByPhoneRequestDto(phone = normalized),
            )
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: PasswordResetStartResponseDto = response.body()
                dto.resetToken
            }

            else -> {
                val message = runCatching {
                    response.body<ResetPasswordErrorResponseDto>().message
                }.getOrNull()
                throw IllegalStateException(
                    message ?: "Не удалось инициировать восстановление по телефону (код ${response.status.value}).",
                )
            }
        }
    }

    /**
     * Смена пароля по reset-токену.
     *
     * Предполагается, что токен пользователь получил по почте/другому каналу,
     * а здесь он лишь вводит token + новый пароль.
     *
     * Успех → функция завершится без исключения.
     * Ошибка → кидаем IllegalStateException с человекочитаемым текстом.
     */
    suspend fun resetPassword(
        resetToken: String,
        newPassword: String,
    ) {
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(
                ResetPasswordRequestDto(
                    token = resetToken,
                    newPassword = newPassword,
                ),
            )
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                // успех: ничего не делаем, просто выходим
            }

            HttpStatusCode.BadRequest,
            HttpStatusCode.NotFound,
            HttpStatusCode.Conflict -> {
                val errorDto = try {
                    response.body<ResetPasswordErrorResponseDto>()
                } catch (_: Throwable) {
                    null
                }
                val message = errorDto?.message
                    ?: "Не удалось сменить пароль (код ${response.status.value})."

                throw IllegalStateException(message)
            }

            else -> {
                throw IllegalStateException(
                    "Не удалось сменить пароль (код ${response.status.value}).",
                )
            }
        }

    }

    suspend fun startEmailVerification(): VerificationDeliveryStatus {
        ensureTokenLoaded()
        val token = authToken ?: throw IllegalStateException("Требуется авторизация")
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/verify-email/start") {
            header("Authorization", "Bearer $token")
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: SimpleStatusDto = response.body()
                if (dto.status == "ALREADY_VERIFIED") {
                    VerificationDeliveryStatus.ALREADY_VERIFIED
                } else {
                    VerificationDeliveryStatus.SENT
                }
            }

            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                throw IllegalStateException("Требуется авторизация")
            }

            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось отправить письмо с подтверждением email"),
            )
        }
    }

    suspend fun confirmEmailVerification(token: String) {
        val client = backendClient.client
        val response: HttpResponse = client.post("$baseUrl/api/auth/verify-email/confirm") {
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailConfirmRequestDto(token = token))
        }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось подтвердить email"),
            )
        }
    }

    suspend fun startPhoneVerification(): VerificationDeliveryStatus {
        ensureTokenLoaded()
        val token = authToken ?: throw IllegalStateException("Требуется авторизация")
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/verify-phone/start") {
            header("Authorization", "Bearer $token")
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: SimpleStatusDto = response.body()
                if (dto.status == "ALREADY_VERIFIED") {
                    VerificationDeliveryStatus.ALREADY_VERIFIED
                } else {
                    VerificationDeliveryStatus.SENT
                }
            }

            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                throw IllegalStateException("Требуется авторизация")
            }

            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось отправить код подтверждения"),
            )
        }
    }

    suspend fun confirmPhoneVerification(token: String) {
        val client = backendClient.client
        val response: HttpResponse = client.post("$baseUrl/api/auth/verify-phone/confirm") {
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailConfirmRequestDto(token = token))
        }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось подтвердить телефон"),
            )
        }
    }

    suspend fun startPhoneChange(newPhone: String, currentPassword: String) {
        ensureTokenLoaded()
        val token = authToken ?: throw IllegalStateException("Требуется авторизация")
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/change-phone/start") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                ChangePhoneStartRequestDto(
                    newPhone = newPhone,
                    currentPassword = currentPassword,
                ),
            )
        }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                throw IllegalStateException("Требуется авторизация")
            }

            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось начать смену телефона"),
            )
        }
    }

    suspend fun confirmPhoneChange(token: String) {
        val client = backendClient.client
        val response: HttpResponse = client.post("$baseUrl/api/auth/change-phone/confirm") {
            contentType(ContentType.Application.Json)
            setBody(ChangePhoneConfirmRequestDto(token = token))
        }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось подтвердить новый телефон"),
            )
        }
    }

    // === DTO / маппинги ===

    @Serializable
    private data class AuthRegisterRequestDto(
        val email: String,
        val password: String,
        val displayName: String? = null,
    )

    @Serializable
    private data class AuthLoginRequestDto(
        val email: String,
        val password: String,
    )

    @Serializable
    private data class AuthUserResponseDto(
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
        val createdAt: Long? = null,
        val photos: List<String> = emptyList(),
    )

    @Serializable
    private data class ProfileSummaryResponseDto(
        val id: Long,
        val email: String,
        val displayName: String,
        val phone: String? = null,
        val pendingPhone: String? = null,
        val pendingPhoneRequestedAt: Long? = null,
        val avatarUrl: String? = null,
        val city: String? = null,
        val emailVerified: Boolean = false,
        val emailVerifiedAt: Long? = null,
        val phoneVerifiedAt: Long? = null,
        val createdAt: Long? = null,
        val photos: List<String> = emptyList(),
    )

    @Serializable
    private data class ProfileUpdateRequestDto(
        val displayName: String? = null,
        val city: String? = null,
        val phone: String? = null,
        val avatarUrl: String? = null,
        val bio: String? = null,
        val website: String? = null,
    )

    @Serializable
    private data class ProfilePhotosUpdateRequestDto(
        val photos: List<String> = emptyList(),
    )

    @Serializable
    private data class ResetPasswordRequestDto(
        val token: String,
        val newPassword: String,
    )

    @Serializable
    private data class ResetPasswordErrorResponseDto(
        val error: String,
        val message: String,
        val field: String? = null,
    )

    @Serializable
    private data class PasswordResetStartRequestDto(
        val email: String,
    )
    @Serializable
    private data class PasswordResetStartByPhoneRequestDto(
        val phone: String,
    )

    @Serializable
    private data class PasswordResetStartResponseDto(
        val status: String,
        val resetToken: String? = null,
    )

    @Serializable
    private data class ChangePasswordRequestDto(
        val oldPassword: String,
        val newPassword: String,
    )

    @Serializable
    private data class ChangeEmailStartRequestDto(
        val newEmail: String,
        val currentPassword: String,
    )

    @Serializable
    private data class ChangeEmailConfirmRequestDto(
        val token: String,
    )

    @Serializable
    private data class ChangePhoneStartRequestDto(
        val newPhone: String,
        val currentPassword: String,
    )

    @Serializable
    private data class ChangePhoneConfirmRequestDto(
        val token: String,
    )

    @Serializable
    private data class SimpleStatusDto(
        val status: String,
    )

    @Serializable
    private data class DeleteAccountResponseDto(
        val status: String,
        val deleteAfter: Long? = null,
        val restoreToken: String? = null,
    )

    @Serializable
    private data class DeleteAccountRequestDto(
        val currentPassword: String,
    )

    @Serializable
    private data class ProfileErrorDto(
        val code: String,
        val message: String,
    )

    private fun AuthUserResponseDto.toDomain(): AuthUser =
        AuthUser(
            id = id,
            email = email,
            displayName = displayName,
            phone = phone,
            pendingPhone = pendingPhone,
            pendingPhoneRequestedAt = pendingPhoneRequestedAt,
            avatarUrl = avatarUrl,
            city = city,
            emailVerified = emailVerified,
            emailVerifiedAt = emailVerifiedAt,
            phoneVerifiedAt = phoneVerifiedAt,
            createdAt = createdAt,
            photos = photos,
        )

    private fun ProfileSummaryResponseDto.toDomain(): AuthUser =
        AuthUser(
            id = id,
            email = email,
            displayName = displayName,
            phone = phone,
            pendingPhone = pendingPhone,
            pendingPhoneRequestedAt = pendingPhoneRequestedAt,
            avatarUrl = avatarUrl,
            city = city,
            emailVerified = emailVerified,
            emailVerifiedAt = emailVerifiedAt,
            phoneVerifiedAt = phoneVerifiedAt,
            createdAt = createdAt,
            photos = photos,
        )

    private suspend fun parseProfileErrorMessage(response: HttpResponse, fallback: String): String =
        runCatching { response.body<ProfileErrorDto>().message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback

    private suspend fun parseAuthErrorMessage(response: HttpResponse, fallback: String): String =
        runCatching { response.body<ResetPasswordErrorResponseDto>().message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback

    /**
     * Инициировать смену email. Требует авторизации.
     */
    suspend fun startEmailChange(newEmail: String, currentPassword: String) {
        ensureTokenLoaded()
        val token = authToken ?: throw IllegalStateException("Требуется авторизация")
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/change-email/start") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                ChangeEmailStartRequestDto(
                    newEmail = newEmail,
                    currentPassword = currentPassword,
                ),
            )
        }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.Conflict -> throw IllegalStateException(parseAuthErrorMessage(response, "Email уже используется"))
            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                throw IllegalStateException("Требуется авторизация")
            }
            else -> throw IllegalStateException(parseAuthErrorMessage(response, "Не удалось инициировать смену email"))
        }
    }

    /**
     * Подтвердить смену email по токену из письма. Возвращает нового пользователя.
     */
    suspend fun confirmEmailChange(token: String): AuthResult {
        val client = backendClient.client
        val response: HttpResponse = client.post("$baseUrl/api/auth/change-email/confirm") {
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailConfirmRequestDto(token = token))
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: AuthUserResponseDto = response.body()
                authToken = dto.token
                tokenLoaded = true
                dto.token?.let { tokenStorage.save(it) }
                AuthResult.Success(dto.toDomain())
            }
            HttpStatusCode.BadRequest -> AuthResult.Error(AuthError.INVALID_CREDENTIALS)
            HttpStatusCode.Conflict -> AuthResult.Error(AuthError.EMAIL_ALREADY_EXISTS)
            else -> AuthResult.Error(AuthError.UNKNOWN)
        }
    }

    /**
     * Soft-delete аккаунта. Требует авторизации.
     */
    suspend fun deleteAccount(currentPassword: String): DeleteAccountReceipt? {
        ensureTokenLoaded()
        val token = authToken ?: return null
        val client = backendClient.client

        val response: HttpResponse = client.post("$baseUrl/api/auth/delete-account") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequestDto(currentPassword = currentPassword))
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: DeleteAccountResponseDto = response.body()
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                DeleteAccountReceipt(
                    deleteAfter = dto.deleteAfter,
                    restoreToken = dto.restoreToken,
                )
            }
            HttpStatusCode.Unauthorized -> {
                authToken = null
                tokenLoaded = true
                tokenStorage.clear()
                null
            }
            else -> throw IllegalStateException(parseAuthErrorMessage(response, "Не удалось запланировать удаление аккаунта"))
        }
    }

    suspend fun restoreDeletedAccount(token: String): AuthUser {
        val client = backendClient.client
        val response: HttpResponse = client.post("$baseUrl/api/auth/delete-account/restore") {
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailConfirmRequestDto(token = token))
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val dto: AuthUserResponseDto = response.body()
                authToken = dto.token
                tokenLoaded = true
                dto.token?.let { tokenStorage.save(it) }
                dto.toDomain()
            }

            else -> throw IllegalStateException(
                parseAuthErrorMessage(response, "Не удалось восстановить аккаунт по токену"),
            )
        }
    }
}

enum class VerificationDeliveryStatus {
    SENT,
    ALREADY_VERIFIED,
}
