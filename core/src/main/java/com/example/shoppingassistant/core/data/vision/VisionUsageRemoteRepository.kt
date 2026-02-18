// Last synced: 2025-12-21 14:27:53
package com.example.shoppingassistant.core.data.vision

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.vision.VisionConsumeRequest
import com.example.shoppingassistant.domain.vision.VisionUsage
import com.example.shoppingassistant.domain.vision.VisionUsageRepository
import com.example.shoppingassistant.domain.vision.VisionUsageResult
import com.example.shoppingassistant.domain.vision.VisionUsageStatus
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class VisionUsageRemoteRepository(
    private val backendClient: BackendClient,
    private val storage: VisionUsageStorage,
) : VisionUsageRepository {

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun getUsage(userKey: String?): VisionUsageResult {
        val response = backendClient.client.post("$baseUrl/api/vision/usage") {
            contentType(ContentType.Application.Json)
            setBody(VisionUsageRequestDto(userKey = userKey))
        }

        val cached = storage.getLast(userKey)

        if (response.status != HttpStatusCode.OK) {
            return VisionUsageResult(
                status = VisionUsageStatus.ERROR,
                usage = cached,
                message = "Не удалось загрузить лимит",
            )
        }

        val dto: VisionUsageResponseDto = response.body()
        val usage = dto.usage ?: cached
        if (dto.status == VisionUsageStatus.OK && usage != null) {
            storage.save(userKey, usage)
        }
        return dto.toResult(usage)
    }

    override suspend fun consume(request: VisionConsumeRequest): VisionUsageResult {
        val response = backendClient.client.post("$baseUrl/api/vision/consume") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val cached = storage.getLast(request.userKey)

        if (response.status != HttpStatusCode.OK) {
            return VisionUsageResult(
                status = VisionUsageStatus.ERROR,
                usage = cached,
                message = "Не удалось списать лимит",
            )
        }

        val dto: VisionUsageResponseDto = response.body()
        val usage = dto.usage ?: cached
        if (dto.status == VisionUsageStatus.OK && usage != null) {
            storage.save(request.userKey, usage)
        }
        return dto.toResult(usage)
    }

    @Serializable
    private data class VisionUsageRequestDto(
        val userKey: String? = null,
    )

    @Serializable
    private data class VisionUsageResponseDto(
        val status: VisionUsageStatus,
        val usage: VisionUsage? = null,
        val message: String? = null,
    ) {
        fun toResult(usageOverride: VisionUsage?): VisionUsageResult =
            VisionUsageResult(
                status = status,
                usage = usageOverride,
                message = message,
            )
    }
}
