package com.example.shoppingassistant.server.push

import kotlinx.serialization.Serializable

@Serializable
data class PushTokenDto(
    val id: Long,
    val userId: Long,
    val platform: String,
    val token: String,
    val deviceId: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val lastSeenAt: Long,
)

interface PushTokensRepository {
    suspend fun upsertToken(
        userId: Long,
        platform: String,
        token: String,
        deviceId: String?,
    ): Long

    suspend fun listActiveTokens(userId: Long, platform: String? = null): List<String>
}

data class PushSendResult(
    val successCount: Int,
    val failureCount: Int,
    val error: String? = null,
)

interface FcmPushSender {
    suspend fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ): PushSendResult
}

