package com.example.shoppingassistant.domain.useroffers

import kotlinx.serialization.Serializable

@Serializable
enum class UserOfferStatus {
    ACTIVE,
    PAUSED,
    DRAFT,
    FINISHED,
    ARCHIVED,
}

@Serializable
enum class UserOfferPublicationStatus {
    PUBLISHED,
    ON_MODERATION,
    ERROR,
}

@Serializable
enum class UserOfferSort {
    PUBLISHED_AT,
    UPDATED_AT,
    PRICE,
    VIEWS,
    EXPIRES_AT,
    CATEGORY,
}

@Serializable
data class UserOffersQuery(
    val limit: Int = 20,
    val cursor: String? = null,
    val statuses: List<UserOfferStatus> = emptyList(),
    val searchQuery: String? = null,
    val updatedSinceMillis: Long? = null,
    val sort: UserOfferSort = UserOfferSort.PUBLISHED_AT,
)

@Serializable
data class UserOfferSummary(
    val id: String,
    val title: String,
    val category: String? = null,
    val priceMajor: Double? = null,
    val currency: String = "USD",
    val status: UserOfferStatus = UserOfferStatus.ACTIVE,
    val publicationStatus: UserOfferPublicationStatus = UserOfferPublicationStatus.PUBLISHED,
    val coverUrl: String? = null,
    val publishedAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val sourceUpdatedAtMillis: Long? = null,
    val sourceName: String? = null,
    val sourceIconUrl: String? = null,
    val expiresAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val lastRenewedAtMillis: Long? = null,
    val viewsCount: Int? = null,
    val favoritesCount: Int? = null,
    val messagesCount: Int? = null,
    val todayViews: Int? = null,
    val todayContacts: Int? = null,
    val isPromoted: Boolean = false,
)

@Serializable
data class UserOffersPage(
    val items: List<UserOfferSummary>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

interface UserOffersRepository {
    suspend fun list(query: UserOffersQuery): UserOffersPage
}

@Serializable
enum class UserOfferActionType {
    PAUSE,
    ACTIVATE,
    MARK_FINISHED,
    ARCHIVE,
    DELETE,
    RENEW,
    DUPLICATE,
}

@Serializable
enum class UserOfferActionStatus {
    SUCCESS,
    NOT_FOUND,
    INVALID_INPUT,
    FORBIDDEN,
    FAILED,
}

@Serializable
data class UserOfferActionRequest(
    val offerId: String,
    val action: UserOfferActionType,
)

@Serializable
data class UserOfferActionResult(
    val status: UserOfferActionStatus,
    val offer: UserOfferSummary? = null,
    val newOffer: UserOfferSummary? = null,
    val message: String? = null,
)

interface UserOffersActionsRepository {
    suspend fun performAction(request: UserOfferActionRequest): UserOfferActionResult
}

class PerformUserOfferActionTask(
    private val repository: UserOffersActionsRepository,
) {
    suspend operator fun invoke(request: UserOfferActionRequest): UserOfferActionResult =
        repository.performAction(request)
}

@Serializable
data class UserOfferPriceUpdateRequest(
    val offerId: String,
    val priceMajor: Double,
    val currency: String? = null,
)

@Serializable
data class UserOfferPriceUpdateResult(
    val offerId: String,
    val status: UserOfferActionStatus,
    val offer: UserOfferSummary? = null,
    val message: String? = null,
)

@Serializable
data class UserOfferPriceBulkRequest(
    val items: List<UserOfferPriceUpdateRequest>,
)

@Serializable
data class UserOfferPriceBulkResult(
    val results: List<UserOfferPriceUpdateResult>,
    val succeeded: Int,
    val failed: Int,
)

interface UserOffersPriceRepository {
    suspend fun updatePrice(request: UserOfferPriceUpdateRequest): UserOfferPriceUpdateResult
    suspend fun updatePrices(request: UserOfferPriceBulkRequest): UserOfferPriceBulkResult
}

class UpdateUserOfferPriceTask(
    private val repository: UserOffersPriceRepository,
) {
    suspend operator fun invoke(request: UserOfferPriceUpdateRequest): UserOfferPriceUpdateResult =
        repository.updatePrice(request)
}

class UpdateUserOfferPricesTask(
    private val repository: UserOffersPriceRepository,
) {
    suspend operator fun invoke(request: UserOfferPriceBulkRequest): UserOfferPriceBulkResult =
        repository.updatePrices(request)
}
