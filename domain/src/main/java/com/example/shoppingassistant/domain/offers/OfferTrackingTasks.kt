package com.example.shoppingassistant.domain.offers

import com.example.shoppingassistant.domain.ingest.SourceType
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Категории для отслеживаемых офферов из внешних ссылок.
 */
@Serializable
enum class OfferCategory {
    FAST_FOOD,
    TECH,
    SUPPLEMENTS,
    ENTERTAINMENT_TOYS,
    OTHER,
}

/**
 * Источник/площадка и ссылка оригинального объявления.
 */
@Serializable
data class TrackedOfferSource(
    val sourceType: SourceType,
    val sourceId: String? = null,
    val url: String,
    val canonicalUrl: String? = null,
    val listingId: String? = null,
    val domainName: String? = null,
    val sourceIconUrl: String? = null,
)

/**
 * Нормализованные данные, из которых создаётся отслеживаемый оффер.
 */
@Serializable
data class TrackedOfferInput(
    val userId: String,
    val title: String,
    val categoryCode: String? = null,
    val categoryConfidence: Double? = null,
    val parserVersion: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val primaryAttribute: String? = null,
    val priceValue: Double,
    val currency: String,
    val imageUrls: List<String> = emptyList(),
    val description: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val source: TrackedOfferSource,
)

/**
 * Результат создания отслеживаемого оффера.
 */
@Serializable
data class CreateTrackedOfferResult(
    val status: CreateTrackedOfferStatus,
    val offerId: String? = null,
    val existingOfferId: String? = null,
    val message: String? = null,
    val reasonCodes: List<String> = emptyList(),
)

@Serializable
enum class CreateTrackedOfferStatus {
    CREATED,
    ALREADY_EXISTS,
    INVALID_INPUT,
}

/**
 * Доменный контракт создания отслеживаемого оффера по нормализованной ссылке.
 */
interface TrackedOfferRepository {
    suspend fun createTrackedOffer(request: TrackedOfferInput): CreateTrackedOfferResult

    /**
     * Обновление цены/атрибутов уже созданного отслеживаемого оффера.
     * Не трогаем title/brand/model пользователя.
     */
    suspend fun refreshTrackedOffer(request: RefreshTrackedOfferInput): RefreshTrackedOfferResult
}

class CreateTrackedOfferTask(
    private val repository: TrackedOfferRepository,
) {
    suspend operator fun invoke(request: TrackedOfferInput): CreateTrackedOfferResult =
        repository.createTrackedOffer(request)
}

/**
 * Параметры обновления отслеживаемого оффера (по его id).
 */
@Serializable
data class RefreshTrackedOfferInput(
    val offerId: String,
    val priceValue: Double,
    val currency: String,
    val attributes: Map<String, String> = emptyMap(),
    val dataSource: String? = null,
)

@Serializable
data class RefreshTrackedOfferResult(
    val status: RefreshTrackedOfferStatus,
    val message: String? = null,
    val reasonCodes: List<String> = emptyList(),
)

@Serializable
enum class RefreshTrackedOfferStatus {
    UPDATED,
    NOT_FOUND,
    INVALID_INPUT,
}

class RefreshTrackedOfferTask(
    private val repository: TrackedOfferRepository,
) {
    suspend operator fun invoke(request: RefreshTrackedOfferInput): RefreshTrackedOfferResult =
        repository.refreshTrackedOffer(request)
}

fun OfferCategory.toFallbackCategoryCode(): String? = when (this) {
    OfferCategory.FAST_FOOD -> "FOOD.READY_MEALS"
    OfferCategory.TECH -> "TECH.PHONES"
    OfferCategory.SUPPLEMENTS -> "BEAUTY.HEALTH"
    OfferCategory.ENTERTAINMENT_TOYS -> "KIDS.TOYS_GAMES"
    OfferCategory.OTHER -> null
}

fun TrackedOfferInput.resolveCategoryCode(): String? =
    categoryCode
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
