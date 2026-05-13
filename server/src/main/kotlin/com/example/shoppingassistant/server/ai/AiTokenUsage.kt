package com.example.shoppingassistant.server.ai

data class AiTokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
) {
    val isEmpty: Boolean
        get() = inputTokens == null && outputTokens == null && totalTokens == null
}
