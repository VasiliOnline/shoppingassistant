package com.example.shoppingassistant.feature.pages.common

import android.content.Context
import android.net.Uri
import com.example.shoppingassistant.domain.ugc.draft.DraftMedia
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.feature.pages.main.visualsearch.VisualSearchCaptureAnalyzer

data class CommercePhotoCaptureInsights(
    val barcodeValue: String? = null,
    val recognizedText: String? = null,
    val textHints: List<String> = emptyList(),
    val imageLabels: List<String> = emptyList(),
    val objectLabel: String? = null,
    val objectConfidence: Float? = null,
    val subjectCoverage: Float? = null,
    val subjectCenteredness: Float? = null,
) {
    val hasBarcodeOrReadableText: Boolean
        get() = !barcodeValue.isNullOrBlank() || textHints.isNotEmpty() || !recognizedText.isNullOrBlank()
}

suspend fun analyzeCommercePhotoCapture(
    context: Context,
    media: DraftMedia,
): CommercePhotoCaptureInsights? {
    val localUri = media.localUri?.takeIf { it.isNotBlank() } ?: return null
    val uri = Uri.parse(localUri)
    return runCatching {
        VisualSearchCaptureAnalyzer(context).use { analyzer ->
            val analysis = analyzer.analyzeUri(uri, VisualSearchCaptureMode.IMAGE) ?: return null
            val region = analysis.suggestedRegion
            val subjectCoverage = region?.let { candidate ->
                (candidate.width * candidate.height).coerceIn(0f, 1f)
            }
            val subjectCenteredness = region?.let { candidate ->
                val centerX = candidate.left + candidate.width / 2f
                val centerY = candidate.top + candidate.height / 2f
                val dx = centerX - 0.5f
                val dy = centerY - 0.5f
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                (1f - distance / 0.70710677f).coerceIn(0f, 1f)
            }
            CommercePhotoCaptureInsights(
                barcodeValue = analysis.barcodeValue,
                recognizedText = analysis.recognizedText,
                textHints = analysis.textHints,
                imageLabels = analysis.imageLabels,
                objectLabel = analysis.objectLabel,
                objectConfidence = analysis.objectConfidence,
                subjectCoverage = subjectCoverage,
                subjectCenteredness = subjectCenteredness,
            )
        }
    }.getOrNull()
}
