package com.example.shoppingassistant.core.data.profile

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.profile.ProfileContractRepository
import com.example.shoppingassistant.domain.profile.ProfileLookupResult
import com.example.shoppingassistant.domain.profile.ProfilePrivacyUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfilePublicUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfileView
import com.example.shoppingassistant.domain.profile.SellerDeliveryZonesUpdatePayload
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

class ProfileContractRepositoryImpl(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : ProfileContractRepository {

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun getProfile(targetUserId: Long?): ProfileLookupResult {
        val token = authRepository.currentToken()
        val initial = requestProfile(
            targetUserId = targetUserId,
            token = token,
        )
        if (initial.status == HttpStatusCode.Unauthorized && targetUserId != null && !token.isNullOrBlank()) {
            authRepository.logout()
            return parseLookupResult(
                targetUserId = targetUserId,
                response = requestProfile(targetUserId = targetUserId, token = null),
            )
        }
        return parseLookupResult(targetUserId = targetUserId, response = initial)
    }

    override suspend fun updatePublicProfile(payload: ProfilePublicUpdatePayload): ProfileView {
        val token = requireToken()
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/profile/public") {
            header("Authorization", ensureBearer(token))
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        return parseProfileWriteResponse(response)
    }

    override suspend fun updatePrivacy(payload: ProfilePrivacyUpdatePayload): ProfileView {
        val token = requireToken()
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/profile/privacy") {
            header("Authorization", ensureBearer(token))
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        return parseProfileWriteResponse(response)
    }

    override suspend fun updateSellerDeliveryZones(payload: SellerDeliveryZonesUpdatePayload): ProfileView {
        val token = requireToken()
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/profile/delivery-zones") {
            header("Authorization", ensureBearer(token))
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        return parseProfileWriteResponse(response)
    }

    private suspend fun requestProfile(
        targetUserId: Long?,
        token: String?,
    ): HttpResponse = if (targetUserId == null) {
        backendClient.client.get("$baseUrl/api/profile/me/view") {
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
        }
    } else {
        backendClient.client.get("$baseUrl/api/profile/$targetUserId") {
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
        }
    }

    private suspend fun parseLookupResult(
        targetUserId: Long?,
        response: HttpResponse,
    ): ProfileLookupResult = when (response.status) {
        HttpStatusCode.OK -> ProfileLookupResult.Found(response.body())
        HttpStatusCode.NotFound -> ProfileLookupResult.NotFound
        HttpStatusCode.Forbidden -> ProfileLookupResult.PrivateUnavailable
        HttpStatusCode.Unauthorized -> {
            authRepository.logout()
            if (targetUserId == null) {
                throw IllegalStateException("Unauthorized")
            }
            ProfileLookupResult.NotFound
        }
        else -> throw IllegalStateException(
            "Failed to load profile view (${response.status.value}).",
        )
    }

    private suspend fun parseProfileWriteResponse(response: HttpResponse): ProfileView = when (response.status) {
        HttpStatusCode.OK -> response.body()
        HttpStatusCode.Unauthorized -> {
            authRepository.logout()
            throw IllegalStateException("Unauthorized")
        }
        HttpStatusCode.BadRequest -> {
            throw IllegalStateException(parseProfileErrorMessage(response, "Некорректные данные профиля"))
        }
        else -> throw IllegalStateException(
            "Failed to update profile (${response.status.value}).",
        )
    }

    private suspend fun requireToken(): String =
        authRepository.currentToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Unauthorized")

    private fun ensureBearer(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"

    private suspend fun parseProfileErrorMessage(
        response: HttpResponse,
        fallback: String,
    ): String = runCatching {
        response.body<ProfileErrorDto>().message
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback

    @Serializable
    private data class ProfileErrorDto(
        val code: String,
        val message: String,
    )
}
