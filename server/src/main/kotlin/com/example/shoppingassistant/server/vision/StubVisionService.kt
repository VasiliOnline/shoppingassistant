package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult
import com.example.shoppingassistant.domain.vision.VisionSource

/**
 * Простейший заглушечный сервис: если base64 непустой — возвращает фиктивные бренд/модель.
 */
class StubVisionService : VisionService {
    override suspend fun normalizeImage(base64: String): NormalizedQuery? {
        if (base64.isBlank()) return null
        return NormalizedQuery(
            brand = "CameraBrand",
            model = "Model-X",
            attributes = emptyMap(),
        )
    }

    override suspend fun normalizePhotos(request: VisionNormalizeRequest): VisionNormalizeResult? {
        val first = request.photos.firstOrNull { it.base64.isNotBlank() } ?: return null
        val normalized = normalizeImage(first.base64) ?: return null
        val title = listOfNotNull(normalized.brand, normalized.model).joinToString(" ").ifBlank { null }
        return VisionNormalizeResult(
            normalizedQuery = normalized,
            title = title,
            confidence = 0.2f,
            usedSources = setOf(VisionSource.VISUAL),
        )
    }
}
