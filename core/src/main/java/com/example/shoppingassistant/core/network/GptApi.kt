package com.example.shoppingassistant.core.network

import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery
import kotlinx.serialization.Serializable

/**
 * GPT используется ТОЛЬКО как фолбэк нормализации.
 * На MVP — безопасный локальный фолбэк без сети.
 */
object GptApi {

    /**
     * На MVP: локально пытаемся распарсить бренд/модель, а атрибуты нормализуем.
     * Позже здесь появится реальный вызов HTTP.
     */
    suspend fun normalizeQuery(input: String, attrs: Map<String,String> = emptyMap()): NormalizedQuery {
        return try {
            BrandModelRules.fromRaw(input, attrs)
        } catch (_: Throwable) {
            NormalizedQuery(
                brand = input.trim(),
                model = "",
                attributes = Normalization.normalizeAttrs(attrs)
            )
        }
    }
}

@Serializable private data class NormalizeReq(val input: String, val attrs: Map<String,String>)
@Serializable private data class NormalizeResp(
    val brand: String? = null,
    val model: String? = null,
    val attributes: Map<String,String>? = null
)
