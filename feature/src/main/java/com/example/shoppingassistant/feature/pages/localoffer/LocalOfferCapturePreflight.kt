package com.example.shoppingassistant.feature.pages.localoffer

import android.content.Context
import android.graphics.BitmapFactory
import com.example.shoppingassistant.domain.localoffer.LocalOfferPhotoRole
import com.example.shoppingassistant.domain.ugc.draft.DraftMedia
import com.example.shoppingassistant.feature.pages.common.CommercePhotoCaptureInsights
import com.example.shoppingassistant.feature.pages.common.analyzeCommercePhotoCapture
import java.io.File
import java.net.URI
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LocalOfferCapturePreflightSeverity {
    WARNING,
    BLOCKING,
}

data class LocalOfferCapturePreflightIssue(
    val code: String,
    val severity: LocalOfferCapturePreflightSeverity,
    val message: String,
)

data class LocalOfferCaptureSignals(
    val width: Int?,
    val height: Int?,
    val byteSize: Long?,
    val detailScore: Double? = null,
) {
    val shortSidePx: Int?
        get() = listOfNotNull(width, height).minOrNull()

    val longSidePx: Int?
        get() = listOfNotNull(width, height).maxOrNull()

    val aspectRatio: Double?
        get() {
            val w = width ?: return null
            val h = height ?: return null
            if (w <= 0 || h <= 0) return null
            return w.toDouble() / h.toDouble()
        }
}

data class LocalOfferCapturePreflightReport(
    val issues: List<LocalOfferCapturePreflightIssue> = emptyList(),
    val findings: List<String> = emptyList(),
) {
    val hasBlockingIssues: Boolean
        get() = issues.any { issue -> issue.severity == LocalOfferCapturePreflightSeverity.BLOCKING }
}

suspend fun analyzeLocalOfferCapturePreflight(
    context: Context,
    media: DraftMedia,
    role: LocalOfferPhotoRole,
): LocalOfferCapturePreflightReport = withContext(Dispatchers.Default) {
    val insights = analyzeCommercePhotoCapture(context, media)
    LocalOfferCapturePreflightEvaluator.evaluate(
        signals = LocalOfferCaptureSignals(
            width = media.width,
            height = media.height,
            byteSize = media.byteSize,
            detailScore = media.localUri?.let(::estimateDetailScore),
        ),
        role = role,
        insights = insights,
    )
}

object LocalOfferCapturePreflightEvaluator {
    fun evaluate(
        signals: LocalOfferCaptureSignals,
        role: LocalOfferPhotoRole,
        insights: CommercePhotoCaptureInsights? = null,
    ): LocalOfferCapturePreflightReport {
        val issues = buildList {
            val shortSide = signals.shortSidePx
            val byteSize = signals.byteSize
            val aspectRatio = signals.aspectRatio
            val detailScore = signals.detailScore
            val isTechRole = role == LocalOfferPhotoRole.TECH_1 || role == LocalOfferPhotoRole.TECH_2
            val minBlockingShortSide = if (isTechRole) 900 else 560
            val minWarningShortSide = if (isTechRole) 1200 else 720

            if (shortSide == null || shortSide < minBlockingShortSide) {
                add(
                    LocalOfferCapturePreflightIssue(
                        code = "image_too_small",
                        severity = LocalOfferCapturePreflightSeverity.BLOCKING,
                        message = if (isTechRole) {
                            "Технический кадр слишком маленький. Нужен более крупный и читаемый снимок."
                        } else {
                            "Фото слишком маленькое. Переснимите предмет ближе и в лучшем качестве."
                        },
                    ),
                )
            } else if (shortSide < minWarningShortSide) {
                add(
                    LocalOfferCapturePreflightIssue(
                        code = "image_low_resolution",
                        severity = LocalOfferCapturePreflightSeverity.WARNING,
                        message = if (isTechRole) {
                            "Технический кадр лучше снять ближе, чтобы текст и маркировка читались без зума."
                        } else {
                            "Кадр может быть мягким для быстрой нормализации. Лучше снять чуть ближе."
                        },
                    ),
                )
            }

            if (!isTechRole && insights != null) {
                val subjectCoverage = insights.subjectCoverage
                val subjectCenteredness = insights.subjectCenteredness
                if (subjectCoverage != null && subjectCoverage < 0.08f) {
                    add(
                        LocalOfferCapturePreflightIssue(
                            code = "object_too_far",
                            severity = LocalOfferCapturePreflightSeverity.BLOCKING,
                            message = "Товар почти не занимает кадр. Поднесите его ближе и снимите крупнее.",
                        ),
                    )
                } else if (subjectCoverage != null && subjectCoverage < 0.16f) {
                    add(
                        LocalOfferCapturePreflightIssue(
                            code = "object_far",
                            severity = LocalOfferCapturePreflightSeverity.WARNING,
                            message = "Товар ещё далековато. Лучше заполнить кадр предметом сильнее.",
                        ),
                    )
                }
                if (
                    subjectCoverage != null &&
                    subjectCoverage < 0.16f &&
                    subjectCenteredness != null &&
                    subjectCenteredness < 0.42f
                ) {
                    add(
                        LocalOfferCapturePreflightIssue(
                            code = "object_off_center",
                            severity = LocalOfferCapturePreflightSeverity.WARNING,
                            message = "Сместите товар ближе к центру кадра, чтобы мы быстрее его распознали.",
                        ),
                    )
                }
            }

            if (byteSize != null && byteSize < MIN_REASONABLE_IMAGE_SIZE_BYTES) {
                add(
                    LocalOfferCapturePreflightIssue(
                        code = "image_underweight",
                        severity = LocalOfferCapturePreflightSeverity.BLOCKING,
                        message = "Файл выглядит слишком лёгким и, скорее всего, пережат. Сделайте новый снимок.",
                    ),
                )
            }

            if (aspectRatio != null && (aspectRatio > MAX_REASONABLE_ASPECT_RATIO || aspectRatio < MIN_REASONABLE_ASPECT_RATIO)) {
                add(
                    LocalOfferCapturePreflightIssue(
                        code = "image_extreme_crop",
                        severity = LocalOfferCapturePreflightSeverity.WARNING,
                        message = "Кадр выглядит сильно обрезанным. Покажите предмет целиком и без лишнего zoom.",
                    ),
                )
            }

            if (detailScore != null) {
                if (detailScore < if (isTechRole) 8.5 else 7.0) {
                    add(
                        LocalOfferCapturePreflightIssue(
                            code = "image_blurred",
                            severity = if (isTechRole) {
                                LocalOfferCapturePreflightSeverity.BLOCKING
                            } else {
                                LocalOfferCapturePreflightSeverity.WARNING
                            },
                            message = if (isTechRole) {
                                "Техническое фото смазано. Нужен чёткий кадр с читаемым текстом."
                            } else {
                                "Фото может быть смазанным. Переснимите при лучшем свете или удерживайте камеру стабильнее."
                            },
                        ),
                    )
                }
            }

            if (isTechRole && insights != null && !insights.hasBarcodeOrReadableText) {
                add(
                    LocalOfferCapturePreflightIssue(
                        code = "tech_label_not_detected",
                        severity = LocalOfferCapturePreflightSeverity.WARNING,
                        message = "На этом снимке пока не видно маркировку или штрихкод. Если они есть на товаре, снимите ближе и без бликов.",
                    ),
                )
            }
        }
        val findings = buildList {
            insights?.barcodeValue?.takeIf { it.isNotBlank() }?.let { add("Найден штрихкод") }
            if (!insights?.recognizedText.isNullOrBlank() || insights?.textHints?.isNotEmpty() == true) {
                add("Найдён читаемый текст или маркировка")
            }
            insights?.objectLabel?.takeIf { it.isNotBlank() }?.let { label ->
                add("Похоже на: $label")
            }
            insights?.subjectCoverage?.let { coverage ->
                if (coverage >= 0.2f) {
                    add("Товар занимает заметную часть кадра")
                }
            }
        }.distinct()
        return LocalOfferCapturePreflightReport(
            issues = issues.distinctBy { issue -> issue.code },
            findings = findings,
        )
    }

    private const val MIN_REASONABLE_IMAGE_SIZE_BYTES = 48_000L
    private const val MAX_REASONABLE_ASPECT_RATIO = 2.6
    private const val MIN_REASONABLE_ASPECT_RATIO = 0.38
}

private fun estimateDetailScore(localUri: String): Double? {
    val file = runCatching { File(URI(localUri)) }.getOrNull()
        ?.takeIf { it.exists() }
        ?: return null
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, options)
    val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        },
    ) ?: return null

    var edgeSum = 0.0
    var edgeCount = 0
    for (y in 0 until bitmap.height - 1) {
        for (x in 0 until bitmap.width - 1) {
            val current = bitmap.getPixel(x, y).toLuma()
            edgeSum += abs(current - bitmap.getPixel(x + 1, y).toLuma())
            edgeSum += abs(current - bitmap.getPixel(x, y + 1).toLuma())
            edgeCount += 2
        }
    }
    if (!bitmap.isRecycled) {
        bitmap.recycle()
    }
    if (edgeCount == 0) return null
    return edgeSum / edgeCount
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width.coerceAtLeast(1)
    var currentHeight = height.coerceAtLeast(1)
    while (currentWidth > MAX_ANALYSIS_SIDE_PX || currentHeight > MAX_ANALYSIS_SIDE_PX) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun Int.toLuma(): Double {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return 0.299 * r + 0.587 * g + 0.114 * b
}

private const val MAX_ANALYSIS_SIDE_PX = 128
