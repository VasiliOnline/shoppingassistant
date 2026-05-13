package com.example.shoppingassistant.server.profile

import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.profile.ProfileLookupResult
import com.example.shoppingassistant.domain.profile.ProfilePrivacyUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfilePublicUpdatePayload
import com.example.shoppingassistant.domain.profile.SellerDeliveryZonesUpdatePayload
import com.example.shoppingassistant.server.auth.SessionManager
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.UserProfilesTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.koin.java.KoinJavaComponent

fun Route.profileRoutes() {
    route("/api/profile") {
        get("/me") {
            val userId = call.requireAuthorizedUserId() ?: return@get
            val authRepository: AuthRepository =
                KoinJavaComponent.get(AuthRepository::class.java)
            val user = authRepository.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            call.respond(user.toSummaryResponse())
        }

        get("/me/view") {
            val userId = call.requireAuthorizedUserId() ?: return@get
            val service: ProfileContractService =
                KoinJavaComponent.get(ProfileContractService::class.java)
            when (val result = service.getProfileView(targetUserId = userId, viewerUserId = userId)) {
                is ProfileLookupResult.Found -> call.respond(result.profile)
                ProfileLookupResult.NotFound -> call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ProfileErrorResponse(
                        code = "NOT_FOUND",
                        message = "Профиль не найден",
                    ),
                )
                ProfileLookupResult.PrivateUnavailable -> call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = ProfileErrorResponse(
                        code = "PRIVATE_PROFILE_UNAVAILABLE",
                        message = "Публичный профиль недоступен",
                    ),
                )
            }
        }

        post("/update") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val request = runCatching { call.receive<ProfileUpdateRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

            if (request.phone != null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ProfileErrorResponse(
                        code = "PHONE_UPDATE_REQUIRES_CONFIRMATION",
                        message = "Смена телефона выполняется отдельно через подтверждение по SMS.",
                    ),
                )
                return@post
            }

            val normalizedWebsite = runCatching { normalizeWebsite(request.website) }
                .getOrElse { code ->
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = ProfileErrorResponse(
                            code = code.message ?: "WEBSITE_INVALID",
                            message = "Некорректная ссылка профиля",
                        ),
                    )
                    return@post
                }

            val updated = DatabaseFactory.dbQuery {
                ensureProfileRow(userId)

                val authUpdated = AuthUsersTable.update(
                    where = {
                        (AuthUsersTable.id eq userId) and (AuthUsersTable.isDeleted eq false)
                    },
                ) { row ->
                    row[displayName] = request.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    row[city] = request.city?.trim()?.takeIf { it.isNotEmpty() }
                    row[avatarUrl] = request.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
                } > 0

                UserProfilesTable.update(
                    where = { UserProfilesTable.userId eq userId },
                ) { row ->
                    row[displayName] = request.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    row[city] = request.city?.trim()?.takeIf { it.isNotEmpty() }
                    row[avatarUrl] = request.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
                    row[bio] = request.bio?.trim()?.takeIf { it.isNotEmpty() }
                    row[website] = normalizedWebsite
                }

                authUpdated
            }

            if (!updated) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            val authRepository: AuthRepository =
                KoinJavaComponent.get(AuthRepository::class.java)
            val user = authRepository.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.respond(user.toSummaryResponse())
        }

        post("/public") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val payload = runCatching { call.receive<ProfilePublicUpdatePayload>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

            val service: ProfileContractService =
                KoinJavaComponent.get(ProfileContractService::class.java)
            val profile = runCatching { service.updatePublicProfile(userId = userId, payload = payload) }
                .getOrElse { throwable ->
                    if (throwable is IllegalArgumentException) {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = ProfileErrorResponse(
                                code = throwable.message ?: "INVALID_PUBLIC_PROFILE",
                                message = "Некорректные данные публичного профиля",
                            ),
                        )
                    } else {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                    return@post
                }
            call.respond(profile)
        }

        post("/privacy") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val payload = runCatching { call.receive<ProfilePrivacyUpdatePayload>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

            val service: ProfileContractService =
                KoinJavaComponent.get(ProfileContractService::class.java)
            val profile = runCatching { service.updatePrivacy(userId = userId, payload = payload) }
                .getOrElse {
                    call.respond(HttpStatusCode.InternalServerError)
                    return@post
                }
            call.respond(profile)
        }

        post("/delivery-zones") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val payload = runCatching { call.receive<SellerDeliveryZonesUpdatePayload>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

            val service: ProfileContractService =
                KoinJavaComponent.get(ProfileContractService::class.java)
            val profile = runCatching { service.updateSellerDeliveryZones(userId = userId, payload = payload) }
                .getOrElse { throwable ->
                    if (throwable is IllegalArgumentException) {
                        call.respond(
                            status = HttpStatusCode.BadRequest,
                            message = ProfileErrorResponse(
                                code = throwable.message ?: "INVALID_DELIVERY_ZONES",
                                message = "Некорректные зоны доставки",
                            ),
                        )
                    } else {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                    return@post
                }
            call.respond(profile)
        }

        post("/photos") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val request = runCatching { call.receive<ProfilePhotosUpdateRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

            val sanitized = request.photos
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)

            val updated = DatabaseFactory.dbQuery {
                AuthUsersTable.update(
                    where = { AuthUsersTable.id eq userId },
                ) { row ->
                    row[photoUrls] = sanitized
                } > 0
            }

            if (!updated) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            val authRepository: AuthRepository =
                KoinJavaComponent.get(AuthRepository::class.java)
            val user = authRepository.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.respond(user.toSummaryResponse())
        }

        get("/{id}") {
            val targetUserId = call.parameters["id"]?.toLongOrNull()
            if (targetUserId == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ProfileErrorResponse(
                        code = "INVALID_PROFILE_ID",
                        message = "Некорректный идентификатор профиля",
                    ),
                )
                return@get
            }

            val viewerUserId = call.resolveAuthorizedUserIdOrNull()
            val service: ProfileContractService =
                KoinJavaComponent.get(ProfileContractService::class.java)
            when (val result = service.getProfileView(targetUserId = targetUserId, viewerUserId = viewerUserId)) {
                is ProfileLookupResult.Found -> call.respond(result.profile)
                ProfileLookupResult.NotFound -> call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ProfileErrorResponse(
                        code = "NOT_FOUND",
                        message = "Профиль не найден",
                    ),
                )
                ProfileLookupResult.PrivateUnavailable -> call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = ProfileErrorResponse(
                        code = "PRIVATE_PROFILE_UNAVAILABLE",
                        message = "Публичный профиль недоступен",
                    ),
                )
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireAuthorizedUserId(): Long? {
    val userId = resolveAuthorizedUserIdOrNull()
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return userId
}

private fun io.ktor.server.application.ApplicationCall.resolveAuthorizedUserIdOrNull(): Long? {
    val token = extractBearerToken(request.headers["Authorization"]) ?: return null
    val sessionManager: SessionManager =
        KoinJavaComponent.get(SessionManager::class.java)
    return sessionManager.getUserId(token)
}

private suspend fun ensureProfileRow(userId: Long) {
    val exists = UserProfilesTable
        .selectAll()
        .where { UserProfilesTable.userId eq userId }
        .singleOrNull() != null
    if (exists) return

    val authRow = AuthUsersTable
        .selectAll()
        .where { AuthUsersTable.id eq userId }
        .singleOrNull()
        ?: return

    UserProfilesTable.insert { row ->
        row[UserProfilesTable.userId] = userId
        row[displayName] = authRow[AuthUsersTable.displayName]
        row[avatarUrl] = authRow[AuthUsersTable.avatarUrl]
        row[city] = authRow[AuthUsersTable.city]
        row[bio] = null
        row[website] = null
        row[publicProfileEnabled] = true
        row[cityVisible] = true
    }
}

private fun normalizeWebsite(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    require(normalized.length <= 512) { "WEBSITE_TOO_LONG" }
    val host = runCatching { java.net.URI(normalized).host }.getOrNull()
    require(host?.contains('.') == true) { "WEBSITE_INVALID" }
    return normalized
}

private fun extractBearerToken(header: String?): String? {
    if (header == null || !header.startsWith("Bearer ")) return null
    val token = header.removePrefix("Bearer ").trim()
    return token.takeIf { it.isNotEmpty() }
}

private fun com.example.shoppingassistant.domain.model.AuthUser.toSummaryResponse(): ProfileSummaryResponse =
    ProfileSummaryResponse(
        id = id,
        email = email,
        displayName = displayName ?: email,
        phone = phone,
        pendingPhone = pendingPhone,
        pendingPhoneRequestedAt = pendingPhoneRequestedAt,
        avatarUrl = avatarUrl,
        city = city,
        emailVerified = emailVerified,
        emailVerifiedAt = emailVerifiedAt,
        phoneVerifiedAt = phoneVerifiedAt,
        phoneVerified = phoneVerifiedAt != null,
        createdAt = createdAt,
        photos = photos,
    )
