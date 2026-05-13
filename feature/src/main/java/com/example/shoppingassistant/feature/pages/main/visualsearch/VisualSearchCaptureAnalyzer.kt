package com.example.shoppingassistant.feature.pages.main.visualsearch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchRegionUi
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class VisualSearchCaptureAnalysis(
    val captureMode: VisualSearchCaptureMode,
    val barcodeValue: String? = null,
    val recognizedText: String? = null,
    val textHints: List<String> = emptyList(),
    val imageLabels: List<String> = emptyList(),
    val objectLabel: String? = null,
    val objectConfidence: Float? = null,
    val suggestedRegion: VisualSearchRegionUi? = null,
    val queryHints: List<String> = emptyList(),
)

class VisualSearchCaptureAnalyzer(
    private val context: Context,
) : Closeable {

    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler: ImageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val objectDetector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build(),
    )

    suspend fun analyzeUri(
        uri: Uri,
        captureMode: VisualSearchCaptureMode,
    ): VisualSearchCaptureAnalysis? = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri) ?: return@withContext null
        analyzeBitmap(bitmap, captureMode)
    }

    suspend fun analyzeBitmap(
        bitmap: Bitmap,
        captureMode: VisualSearchCaptureMode,
    ): VisualSearchCaptureAnalysis {
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeValue = runCatching { detectBarcode(image) }.getOrNull()
        val recognizedText = runCatching { detectText(image) }.getOrNull()
        val textHints = buildTextHints(recognizedText)
        val imageLabels = runCatching { detectImageLabels(image) }.getOrDefault(emptyList())
        val objectInfo = runCatching { detectObject(image, bitmap.width, bitmap.height) }.getOrNull()
        val queryHints = buildQueryHints(
            captureMode = captureMode,
            barcodeValue = barcodeValue,
            textHints = textHints,
            imageLabels = imageLabels,
            objectLabel = objectInfo?.label,
        )
        return VisualSearchCaptureAnalysis(
            captureMode = captureMode,
            barcodeValue = barcodeValue,
            recognizedText = recognizedText,
            textHints = textHints,
            imageLabels = imageLabels,
            objectLabel = objectInfo?.label,
            objectConfidence = objectInfo?.confidence,
            suggestedRegion = objectInfo?.region,
            queryHints = queryHints,
        )
    }

    override fun close() {
        barcodeScanner.close()
        textRecognizer.close()
        imageLabeler.close()
        objectDetector.close()
    }

    private suspend fun detectBarcode(image: InputImage): String? {
        val barcode = barcodeScanner.process(image).awaitResult()
            .firstOrNull { item -> !item.rawValue.isNullOrBlank() }
        return barcode?.rawValue?.trim()?.takeIf { value -> value.isNotEmpty() }
    }

    private suspend fun detectText(image: InputImage): String? {
        val raw = textRecognizer.process(image).awaitResult().text.trim()
        if (raw.isBlank()) return null
        val cleanedLines = raw.split(textLineBreakRegex)
            .map { line -> normalizeOcrLine(line) }
            .filter(::isMeaningfulOcrLine)
            .take(3)
        return cleanedLines.joinToString(" ").takeIf { value -> value.isNotBlank() }
    }

    private suspend fun detectImageLabels(image: InputImage): List<String> =
        imageLabeler.process(image).awaitResult()
            .sortedByDescending { label -> label.confidence }
            .flatMap { label ->
                if (label.confidence < minimumImageLabelConfidence) {
                    emptyList()
                } else {
                    normalizeVisualHint(label.text)
                }
            }
            .distinct()
            .take(maxImageHints)

    private suspend fun detectObject(
        image: InputImage,
        width: Int,
        height: Int,
    ): DetectedObjectInfo? {
        val detected = objectDetector.process(image).awaitResult()
            .mapNotNull { candidate ->
                val box = candidate.boundingBox
                if (box.width() <= 0 || box.height() <= 0 || width <= 0 || height <= 0) return@mapNotNull null
                val label = candidate.labels
                    .filter { item -> item.text.trim().isNotEmpty() }
                    .maxByOrNull { item -> item.confidence }
                val areaRatio = (box.width().toFloat() * box.height().toFloat()) /
                    (width.toFloat() * height.toFloat())
                val displayLabel = normalizeVisualHint(label?.text).firstOrNull()
                DetectedObjectInfo(
                    label = displayLabel,
                    confidence = label?.confidence,
                    region = VisualSearchRegionUi(
                        label = displayLabel ?: "Объект",
                        left = (box.left.toFloat() / width.toFloat()).coerceIn(0f, 1f),
                        top = (box.top.toFloat() / height.toFloat()).coerceIn(0f, 1f),
                        width = (box.width().toFloat() / width.toFloat()).coerceIn(0.05f, 1f),
                        height = (box.height().toFloat() / height.toFloat()).coerceIn(0.05f, 1f),
                    ),
                    score = areaRatio + (label?.confidence ?: 0f) * 0.75f + if (displayLabel != null) commerceHintBoost else 0f,
                )
            }
            .maxByOrNull { candidate -> candidate.score }
            ?: return null
        return DetectedObjectInfo(
            label = detected.label,
            confidence = detected.confidence,
            region = detected.region,
            score = detected.score,
        )
    }

    private fun buildTextHints(recognizedText: String?): List<String> {
        val raw = recognizedText?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(textLineBreakRegex)
            .map { line -> line.trim() }
            .filter(::isMeaningfulOcrLine)
            .flatMap { line -> normalizeVisualHint(line) }
            .distinct()
            .take(maxTextHints)
    }

    private fun buildQueryHints(
        captureMode: VisualSearchCaptureMode,
        barcodeValue: String?,
        textHints: List<String>,
        imageLabels: List<String>,
        objectLabel: String?,
    ): List<String> = buildList {
        when (captureMode) {
            VisualSearchCaptureMode.BARCODE -> {
                barcodeValue?.let { add(it) }
                addAll(textHints)
                addAll(imageLabels)
            }
            VisualSearchCaptureMode.OCR -> {
                addAll(textHints)
                objectLabel?.let { addAll(expandVisualHint(it)) }
                addAll(imageLabels)
            }
            VisualSearchCaptureMode.IMAGE -> {
                addAll(imageLabels)
                objectLabel?.let { addAll(expandVisualHint(it)) }
                addAll(textHints)
                barcodeValue?.let { add(it) }
            }
        }
    }.map { hint -> hint.trim() }
        .filter { hint -> hint.length >= 3 }
        .distinct()
        .take(maxQueryHints)

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val stream = when (uri.scheme?.lowercase()) {
            "file" -> FileInputStream(uri.path ?: return null)
            else -> context.contentResolver.openInputStream(uri)
        } ?: return null
        return stream.use { input -> BitmapFactory.decodeStream(input) }
    }

    private fun normalizeOcrLine(raw: String): String =
        raw
            .replace(ocrNoiseRegex, " ")
            .replace(textSpacingRegex, " ")
            .trim()

    private fun isMeaningfulOcrLine(raw: String): Boolean {
        val normalized = normalizeOcrLine(raw)
        if (normalized.length < 3) return false
        val compact = SearchTextNormalizer.normalizeToken(normalized)
        if (compact.length < 3) return false
        if (BrandModelRules.fromKnownFamily(normalized) != null) return true

        val tokens = SearchTextNormalizer.tokens(normalized)
        if (tokens.isEmpty() || tokens.size > 3) return false
        val lowercase = normalized.lowercase()
        if (ocrWeakTerms.any { term -> lowercase.contains(term) }) return false
        if (ocrStrongTerms.any { term -> lowercase.contains(term) }) return true
        if (tokens.count { token -> token.length == 1 } > 0) return false

        val hasMixedAlphaNumericToken = tokens.any { token ->
            token.any { ch -> ch.isLetter() } &&
                token.any { ch -> ch.isDigit() } &&
                token.length in 4..18
        }
        return hasMixedAlphaNumericToken && compact.length in 5..24
    }

    private fun expandVisualHint(raw: String): List<String> {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return emptyList()
        val lowercase = normalized.lowercase()
        if (lowercase in ignoredVisualHints) return emptyList()
        val aliases = visualHintAliases[lowercase].orEmpty()
        return buildList {
            addAll(aliases)
            if (normalized !in aliases) add(normalized)
        }.distinct()
    }

    private fun normalizeVisualHint(raw: String?): List<String> {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return emptyList()
        return expandVisualHint(normalized)
            .map { hint -> hint.trim() }
            .filter { hint ->
                hint.length >= 3 &&
                    hint.lowercase() !in ignoredVisualHints
            }
            .distinct()
    }

    private data class DetectedObjectInfo(
        val label: String?,
        val confidence: Float?,
        val region: VisualSearchRegionUi,
        val score: Float,
    )

    private companion object {
        val textSpacingRegex = Regex("\\s+")
        val textLineBreakRegex = Regex("[\\r\\n]+")
        val ocrNoiseRegex = Regex("[^\\p{L}\\p{N}\\s\\-_/]+")
        const val minimumImageLabelConfidence = 0.58f
        const val maxImageHints = 4
        const val maxTextHints = 4
        const val maxQueryHints = 8
        const val commerceHintBoost = 0.42f
        val ocrStrongTerms = setOf(
            "мыш",
            "mouse",
            "keyboard",
            "клавиат",
            "iphone",
            "samsung",
            "xiaomi",
            "logitech",
            "monitor",
            "tv",
            "sony",
            "apple",
            "canon",
            "nikon",
        )
        val ocrWeakTerms = setOf(
            "tableware",
            "interior",
            "screen",
            "display",
            "wall",
            "room",
            "floor",
            "home",
        )

        val visualHintAliases = mapOf(
            "wireless mouse" to listOf("беспроводная мышь", "компьютерная мышь"),
            "computer mouse" to listOf("мышь", "компьютерная мышь"),
            "mouse" to listOf("мышь", "компьютерная мышь"),
            "computer peripheral" to listOf("компьютерная мышь"),
            "input device" to listOf("компьютерная мышь"),
            "keyboard" to listOf("клавиатура"),
            "laptop computer" to listOf("ноутбук"),
            "laptop" to listOf("ноутбук"),
            "smartphone" to listOf("смартфон"),
            "cell phone" to listOf("смартфон"),
            "television" to listOf("телевизор"),
            "tv" to listOf("телевизор"),
            "monitor" to listOf("монитор"),
            "computer monitor" to listOf("монитор"),
            "display device" to listOf("монитор"),
            "screen" to listOf("экран"),
            "headphones" to listOf("наушники"),
            "earphones" to listOf("наушники"),
            "remote control" to listOf("пульт"),
            "watch" to listOf("часы"),
            "bag" to listOf("сумка"),
            "shoe" to listOf("обувь"),
        )

        val ignoredVisualHints = setOf(
            "wall",
            "floor",
            "ceiling",
            "room",
            "living room",
            "interior design",
            "property",
            "real estate",
            "home",
            "house",
            "window",
            "tableware",
            "cutlery",
            "dishware",
            "serveware",
            "kitchenware",
            "flatware",
            "silverware",
            "utensil",
            "utensils",
            "hardwood",
            "wood",
            "wood flooring",
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { error ->
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
    addOnCanceledListener {
        if (continuation.isActive) {
            continuation.cancel()
        }
    }
}
