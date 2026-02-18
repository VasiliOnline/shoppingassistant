package com.example.shoppingassistant.core.data.storage

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.storage.PhotoStorageRepository
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * HTTP-клиент для загрузки фото на backend.
 */
class PhotoStorageRemoteRepository(
    private val backendClient: BackendClient,
) : PhotoStorageRepository {

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String?): String {
        val payload = UploadRequest(
            filename = filename,
            contentType = contentType,
            dataBase64 = Base64.getEncoder().encodeToString(bytes),
        )

        val resp = backendClient.client.post("$baseUrl/api/storage/upload") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val dto: UploadResponse = resp.body()
        return dto.url
    }

    @Serializable
    private data class UploadRequest(
        val filename: String,
        val contentType: String? = null,
        val dataBase64: String,
    )

    @Serializable
    private data class UploadResponse(
        val url: String,
    )
}
