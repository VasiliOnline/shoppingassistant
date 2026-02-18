package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update

object OfferAlertsRepository {
    private const val DEFAULT_DELIVERY_CHANNEL: String = "EMAIL"
    private const val MAX_MIN_INTERVAL_MINUTES: Int = 60 * 24 * 7

    data class UpsertResult(
        val id: Long,
        val created: Boolean,
    )

    suspend fun createAlert(
        userId: Long,
        scope: String,
        productId: Long?,
        offerId: Long?,
        alertType: String,
        thresholdValue: Double,
        currency: String?,
        deliveryChannel: String?,
        minIntervalMinutes: Int = 120,
    ) {
        val normalizedDeliveryChannel = deliveryChannel
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_DELIVERY_CHANNEL

        DatabaseFactory.dbQuery {
            AlertsTable.insert {
                it[AlertsTable.userId] = userId
                it[AlertsTable.scope] = scope
                it[AlertsTable.productId] = productId
                it[AlertsTable.offerId] = offerId
                it[AlertsTable.alertType] = alertType
                it[AlertsTable.thresholdValue] = thresholdValue
                it[AlertsTable.currency] = currency
                it[AlertsTable.deliveryChannel] = normalizedDeliveryChannel
                it[AlertsTable.minIntervalMinutes] = minIntervalMinutes.coerceIn(0, MAX_MIN_INTERVAL_MINUTES)
                it[AlertsTable.isActive] = true
                it[AlertsTable.lastTriggeredAt] = null
                it[AlertsTable.createdAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun upsertOfferPercentDropAlert(
        userId: Long,
        offerId: Long,
        percentDrop: Int,
        deliveryChannel: String?,
        minIntervalMinutes: Int = 120,
    ): UpsertResult =
        DatabaseFactory.dbQuery {
            val normalizedDeliveryChannel = deliveryChannel
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_DELIVERY_CHANNEL

            val q = AlertsTable.selectAll()
            q.andWhere { AlertsTable.userId eq userId }
            q.andWhere { AlertsTable.scope eq "OFFER" }
            q.andWhere { AlertsTable.offerId eq offerId }
            q.andWhere { AlertsTable.alertType eq "PERCENT_DROP" }
            val existing = q.limit(1).singleOrNull()
            if (existing != null) {
                val id = existing[AlertsTable.id]
                val wasActive = existing[AlertsTable.isActive]
                AlertsTable.update({ AlertsTable.id eq id }) { stmt ->
                    stmt[thresholdValue] = percentDrop.toDouble()
                    stmt[this.deliveryChannel] = normalizedDeliveryChannel
                    stmt[this.minIntervalMinutes] = minIntervalMinutes.coerceIn(0, MAX_MIN_INTERVAL_MINUTES)
                    stmt[isActive] = true
                    if (!wasActive) {
                        stmt[lastTriggeredAt] = null
                    }
                }
                UpsertResult(id = id, created = false)
            } else {
                val insert = AlertsTable.insert { stmt ->
                    stmt[this.userId] = userId
                    stmt[scope] = "OFFER"
                    stmt[productId] = null
                    stmt[this.offerId] = offerId
                    stmt[alertType] = "PERCENT_DROP"
                    stmt[thresholdValue] = percentDrop.toDouble()
                    stmt[currency] = null
                    stmt[this.deliveryChannel] = normalizedDeliveryChannel
                    stmt[this.minIntervalMinutes] = minIntervalMinutes.coerceIn(0, MAX_MIN_INTERVAL_MINUTES)
                    stmt[isActive] = true
                    stmt[lastTriggeredAt] = null
                    stmt[createdAt] = System.currentTimeMillis()
                }
                val id = insert[AlertsTable.id]
                UpsertResult(id = id, created = true)
            }
        }

    suspend fun deactivateAlert(id: Long, userId: Long) {
        DatabaseFactory.dbQuery {
            AlertsTable.update({ (AlertsTable.id eq id) and (AlertsTable.userId eq userId) }) {
                it[isActive] = false
            }
        }
    }

    suspend fun listAlerts(userId: Long): List<AlertDto> =
        DatabaseFactory.dbQuery {
            val query = AlertsTable.selectAll()
            query.andWhere { AlertsTable.userId eq userId }
            query.andWhere { AlertsTable.isActive eq true }
            query.map { row ->
                AlertDto(
                    id = row[AlertsTable.id],
                    scope = row[AlertsTable.scope],
                    productId = row[AlertsTable.productId],
                    offerId = row[AlertsTable.offerId],
                    alertType = row[AlertsTable.alertType],
                    thresholdValue = row[AlertsTable.thresholdValue],
                    currency = row[AlertsTable.currency],
                    deliveryChannel = row[AlertsTable.deliveryChannel],
                    minIntervalMinutes = row[AlertsTable.minIntervalMinutes],
                    lastTriggeredAt = row[AlertsTable.lastTriggeredAt],
                    createdAt = row[AlertsTable.createdAt],
                )
            }
        }
}

@Serializable
data class AlertDto(
    val id: Long,
    val scope: String,
    val productId: Long?,
    val offerId: Long?,
    val alertType: String,
    val thresholdValue: Double,
    val currency: String?,
    val deliveryChannel: String?,
    val minIntervalMinutes: Int,
    val lastTriggeredAt: Long?,
    val createdAt: Long,
)
