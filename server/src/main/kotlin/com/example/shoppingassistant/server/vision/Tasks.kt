package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult

/**
 * Контракты для vision-нормализации (камера -> GPT/нормализация).
 */
interface VisionService {
    suspend fun normalizeImage(base64: String): NormalizedQuery?
    suspend fun normalizePhotos(request: VisionNormalizeRequest): VisionNormalizeResult?
}
