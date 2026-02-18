// Last synced: 2025-12-14 15:57:37
package com.example.shoppingassistant.domain.subscriptions

import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionScope { QUERY, OFFER }

@Serializable
enum class SubscriptionConditionType {
    MAX_PRICE, // price ≤ X
    DROP_PERCENT, // падение ≥ %
    ANALOG_APPEARED, // появился аналог (флаг)
    LADDER_STEP_PERCENT // шаги-лестницы (например 5/10/15)
}

@Serializable
data class SubscriptionCondition(
    val type: SubscriptionConditionType,
    val numberValue: Double? = null, // проценты / шаги
    val moneyMinor: Long? = null, // цена в minor units (как Money.minor)
    val currency: String? = null
)

@Serializable
data class Subscription(
    val id: Long,
    val scope: SubscriptionScope,
    val input: String, // запрос или url
    val title: String,
    val conditions: List<SubscriptionCondition> = emptyList(),
    val minAlertIntervalMinutes: Int = 120,
    val isActive: Boolean = true,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Serializable
data class SubscriptionNotification(
    val id: Long,
    val subscriptionId: Long? = null,
    val text: String,
    val createdAtMillis: Long,
    val isRead: Boolean = false
)

@Serializable
data class AddSubscriptionRequest(val rawInput: String)

@Serializable
sealed class AddSubscriptionResult {
    @Serializable data class Created(val id: Long) : AddSubscriptionResult()
    @Serializable data class AlreadyExists(val id: Long) : AddSubscriptionResult()
    @Serializable data class LimitReached(val max: Int) : AddSubscriptionResult()
    @Serializable data class InvalidInput(val reason: String) : AddSubscriptionResult()
}

@Serializable
data class UpdateSubscriptionRequest(
    val id: Long,
    val maxPriceMinor: Long?,
    val currency: String?,
    val dropPercent: Double?,
    val analogAppeared: Boolean,
    val ladderSteps: List<Double>,
    val minAlertIntervalMinutes: Int,
    val isActive: Boolean
)

@Serializable
data class SubscriptionNotificationsPage(
    val items: List<SubscriptionNotification>,
    val total: Int,
    val offset: Int,
    val limit: Int,
)

interface SubscriptionsRepository {
    suspend fun listSubscriptions(): List<Subscription>
    suspend fun listNotifications(): List<SubscriptionNotification>
    suspend fun listNotificationsPage(limit: Int, offset: Int): SubscriptionNotificationsPage
    suspend fun addSubscription(request: AddSubscriptionRequest): AddSubscriptionResult
    suspend fun updateSubscription(request: UpdateSubscriptionRequest): Boolean
    suspend fun deleteSubscription(id: Long)
    suspend fun markNotificationRead(id: Long)
    suspend fun markAllNotificationsRead()
}
