package com.example.shoppingassistant.server.ai.yandex

data class YandexAiExecutionTarget(
    val key: String,
    val modelUri: String,
    val promptId: String? = null,
) {
    val usesSavedAgent: Boolean
        get() = !promptId.isNullOrBlank()
}
