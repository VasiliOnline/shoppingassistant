package com.example.shoppingassistant.server.subscriptions

import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class SubscriptionNotificationsRepositoryImpl : SubscriptionNotificationsRepository {
    override suspend fun listNotificationsPage(
        userId: Long,
        limit: Int,
        offset: Int,
    ): SubscriptionNotificationsPage =
        DatabaseFactory.dbQuery {
            val safeLimit = limit.coerceIn(1, 200)
            val safeOffset = offset.coerceAtLeast(0)

            val rows = SubscriptionNotificationsTable
                .selectAll()
                .where { SubscriptionNotificationsTable.userId eq userId }
                .orderBy(SubscriptionNotificationsTable.createdAt, SortOrder.DESC)
                .limit(safeLimit + 1)
                .offset(safeOffset.toLong())
                .toList()

            val canLoadMore = rows.size > safeLimit
            val items = rows.take(safeLimit).map { row ->
                SubscriptionNotificationDto(
                    id = row[SubscriptionNotificationsTable.id],
                    alertId = row[SubscriptionNotificationsTable.alertId],
                    offerId = row[SubscriptionNotificationsTable.offerId],
                    priceHistoryId = row[SubscriptionNotificationsTable.priceHistoryId],
                    createdAt = row[SubscriptionNotificationsTable.createdAt],
                    priceCollectedAt = row[SubscriptionNotificationsTable.priceCollectedAt],
                    isRead = row[SubscriptionNotificationsTable.isRead],
                    message = row[SubscriptionNotificationsTable.message],
                    dropPercent = row[SubscriptionNotificationsTable.dropPercent],
                    baselinePriceMinor = row[SubscriptionNotificationsTable.baselinePriceMinor],
                    currentPriceMinor = row[SubscriptionNotificationsTable.currentPriceMinor],
                    currency = row[SubscriptionNotificationsTable.currency],
                )
            }

            SubscriptionNotificationsPage(
                items = items,
                limit = safeLimit,
                offset = safeOffset,
                canLoadMore = canLoadMore,
            )
        }

    override suspend fun markRead(userId: Long, notificationId: Long): Boolean =
        DatabaseFactory.dbQuery {
            val updated = SubscriptionNotificationsTable.update(
                where = {
                    (SubscriptionNotificationsTable.userId eq userId) and
                        (SubscriptionNotificationsTable.id eq notificationId) and
                        (SubscriptionNotificationsTable.isRead eq false)
                },
            ) { stmt ->
                stmt[isRead] = true
            }
            updated > 0
        }

    override suspend fun markAllRead(userId: Long): Int =
        DatabaseFactory.dbQuery {
            SubscriptionNotificationsTable.update(
                where = {
                    (SubscriptionNotificationsTable.userId eq userId) and
                        (SubscriptionNotificationsTable.isRead eq false)
                },
            ) { stmt ->
                stmt[isRead] = true
            }
        }
}
