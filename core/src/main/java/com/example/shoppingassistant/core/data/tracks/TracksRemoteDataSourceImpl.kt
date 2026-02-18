package com.example.shoppingassistant.core.data.tracks

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackOffersPage
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackEventsPage
import com.example.shoppingassistant.domain.tracks.TrackTop10
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class TracksRemoteDataSourceImpl(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : TracksRemoteDataSource {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listTracks(): List<Track> {
        val token = authRepository.currentToken() ?: return emptyList()
        val response: HttpResponse = backendClient.client.get("$baseUrl/api/tracks") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return emptyList()
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to load tracks: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun getTrack(trackId: String): Track? {
        val token = authRepository.currentToken() ?: return null
        val response: HttpResponse = backendClient.client.get("$baseUrl/api/tracks/$trackId") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return null
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to load track: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun createTrack(request: TrackCreateRequest): Track {
        val token = authRepository.currentToken() ?: throw IllegalStateException("Unauthorized")
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/tracks") {
            header("Authorization", ensureBearer(token))
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            throw IllegalStateException("Unauthorized")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to create track: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun updateTrack(trackId: String, request: TrackUpdateRequest): Track? {
        val token = authRepository.currentToken() ?: return null
        val response: HttpResponse = backendClient.client.put("$baseUrl/api/tracks/$trackId") {
            header("Authorization", ensureBearer(token))
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return null
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to update track: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun updateTrackTarget(
        trackId: String,
        request: TrackTargetUpdateRequest,
    ): TrackTargetUpdateRemoteResult {
        val token = authRepository.currentToken() ?: return TrackTargetUpdateRemoteResult.InvalidInput("Unauthorized")
        val response: HttpResponse = backendClient.client.put("$baseUrl/api/tracks/$trackId/target") {
            header("Authorization", ensureBearer(token))
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return TrackTargetUpdateRemoteResult.InvalidInput("Unauthorized")
        }
        return when (response.status) {
            HttpStatusCode.OK -> TrackTargetUpdateRemoteResult.Updated(response.body())
            HttpStatusCode.Conflict -> TrackTargetUpdateRemoteResult.AlreadyExists(response.body())
            HttpStatusCode.NotFound -> TrackTargetUpdateRemoteResult.NotFound
            HttpStatusCode.BadRequest -> {
                val reason = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                TrackTargetUpdateRemoteResult.InvalidInput(reason.ifBlank { "Invalid target" })
            }
            else -> throw IllegalStateException("Failed to update target: HTTP ${response.status.value}")
        }
    }

    override suspend fun pauseTrack(trackId: String): Track? {
        val token = authRepository.currentToken() ?: return null
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/tracks/$trackId/pause") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return null
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to pause track: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun resumeTrack(trackId: String): Track? {
        val token = authRepository.currentToken() ?: return null
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/tracks/$trackId/resume") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return null
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to resume track: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun deleteTrack(trackId: String): Boolean {
        val token = authRepository.currentToken() ?: return false
        val response: HttpResponse = backendClient.client.delete("$baseUrl/api/tracks/$trackId") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return false
        }
        if (response.status == HttpStatusCode.NotFound) return false
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to delete track: HTTP ${response.status.value}")
        }
        return true
    }

    override suspend fun getTop10(trackId: String, forceRefresh: Boolean, preferCache: Boolean): TrackTop10 {
        val token = authRepository.currentToken() ?: throw IllegalStateException("Unauthorized")
        val response: HttpResponse = backendClient.client.get("$baseUrl/api/tracks/$trackId/top10") {
            header("Authorization", ensureBearer(token))
            parameter("forceRefresh", forceRefresh)
            parameter("preferCache", preferCache)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            throw IllegalStateException("Unauthorized")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to load top10: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun refreshTop10(trackId: String): TrackTop10 {
        val token = authRepository.currentToken() ?: throw IllegalStateException("Unauthorized")
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/tracks/$trackId/top10/refresh") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            throw IllegalStateException("Unauthorized")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to refresh top10: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun listOffers(
        trackId: String,
        limit: Int,
        offset: Int,
        sort: TrackOfferSort,
    ): TrackOffersPage {
        val token = authRepository.currentToken() ?: return TrackOffersPage(
            items = emptyList(),
            limit = limit,
            offset = offset,
            canLoadMore = false,
            sort = sort,
        )
        val response: HttpResponse = backendClient.client.get("$baseUrl/api/tracks/$trackId/offers") {
            header("Authorization", ensureBearer(token))
            parameter("limit", limit)
            parameter("offset", offset)
            parameter("sort", sort.name)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return TrackOffersPage(
                items = emptyList(),
                limit = limit,
                offset = offset,
                canLoadMore = false,
                sort = sort,
            )
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to load offers: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun listEvents(trackId: String, limit: Int, offset: Int): TrackEventsPage {
        val token = authRepository.currentToken() ?: return TrackEventsPage(
            items = emptyList(),
            limit = limit,
            offset = offset,
            canLoadMore = false,
        )
        val response: HttpResponse = backendClient.client.get("$baseUrl/api/tracks/$trackId/events") {
            header("Authorization", ensureBearer(token))
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return TrackEventsPage(items = emptyList(), limit = limit, offset = offset, canLoadMore = false)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to load track events: HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun markEventRead(eventId: String): Boolean {
        val token = authRepository.currentToken() ?: return false
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/tracks/events/$eventId/read") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return false
        }
        return response.status.isSuccess()
    }

    override suspend fun markAllEventsRead(trackId: String): Int {
        val token = authRepository.currentToken() ?: return 0
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/tracks/$trackId/events/read-all") {
            header("Authorization", ensureBearer(token))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return 0
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to mark all track events: HTTP ${response.status.value}")
        }
        return response.body<MarkAllReadResponse>().updated
    }

    private fun ensureBearer(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"
}

@Serializable
private data class MarkAllReadResponse(val updated: Int)
