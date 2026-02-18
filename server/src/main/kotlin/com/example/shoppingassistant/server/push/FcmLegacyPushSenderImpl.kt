package com.example.shoppingassistant.server.push

import com.example.shoppingassistant.server.config.FcmConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FcmLegacyPushSenderImpl(
    private val config: FcmConfig,
) : FcmPushSender {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override suspend fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): PushSendResult {
        if (!config.enabled) return PushSendResult(0, 0, error = "FCM disabled")
        val key = config.serverKey ?: return PushSendResult(0, 0, error = "FCM server key is missing")
        val ids = tokens.mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }.distinct()
        if (ids.isEmpty()) return PushSendResult(0, 0, error = "No tokens")

        val payload = FcmLegacySendRequest(
            registrationIds = ids,
            notification = FcmNotification(title = title, body = body),
            data = if (data.isEmpty()) null else data,
            dryRun = if (config.dryRun) true else null,
        )

        val bodyJson = json.encodeToString(payload)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "key=$key")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            return PushSendResult(
                successCount = 0,
                failureCount = ids.size,
                error = "HTTP ${response.statusCode()}",
            )
        }

        val parsed = runCatching { json.decodeFromString(FcmLegacySendResponse.serializer(), response.body()) }
            .getOrNull()

        return PushSendResult(
            successCount = parsed?.success ?: 0,
            failureCount = parsed?.failure ?: 0,
            error = null,
        )
    }
}

@Serializable
private data class FcmLegacySendRequest(
    @SerialName("registration_ids") val registrationIds: List<String>,
    val notification: FcmNotification? = null,
    val data: Map<String, String>? = null,
    @SerialName("dry_run") val dryRun: Boolean? = null,
)

@Serializable
private data class FcmNotification(
    val title: String,
    val body: String,
)

@Serializable
private data class FcmLegacySendResponse(
    val success: Int = 0,
    val failure: Int = 0,
)

