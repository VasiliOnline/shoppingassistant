package com.example.shoppingassistant.server.subscriptions

import com.example.shoppingassistant.server.config.FcmConfig
import com.example.shoppingassistant.server.config.SubscriptionsDeliveryConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.push.FcmPushSender
import com.example.shoppingassistant.server.push.PushTokensRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class SubscriptionNotificationsPushDeliveryRunnerImpl(
    private val config: SubscriptionsDeliveryConfig,
    private val fcmConfig: FcmConfig,
    private val tokensRepo: PushTokensRepository,
    private val sender: FcmPushSender,
) : SubscriptionNotificationsDeliveryRunner {

    override suspend fun runOnce(): SubscriptionDeliveryRunResult {
        if (!config.enabled) return SubscriptionDeliveryRunResult(picked = 0, sent = 0, failed = 0, skipped = 0)
        if (!fcmConfig.enabled) return SubscriptionDeliveryRunResult(picked = 0, sent = 0, failed = 0, skipped = 0)

        val now = System.currentTimeMillis()
        val stuckBefore = now - config.stuckSendingTimeoutMillis

        val work = DatabaseFactory.dbQuery {
            val q = SubscriptionNotificationsTable.selectAll()
            q.andWhere { SubscriptionNotificationsTable.deliveryChannel eq "PUSH" }
            q.andWhere {
                (SubscriptionNotificationsTable.deliveryStatus eq "PENDING") or
                    ((SubscriptionNotificationsTable.deliveryStatus eq "SENDING") and
                        (SubscriptionNotificationsTable.lastDeliveryAttemptAt less stuckBefore))
            }
            q.andWhere {
                SubscriptionNotificationsTable.lastDeliveryAttemptAt.isNull() or
                    (SubscriptionNotificationsTable.lastDeliveryAttemptAt less stuckBefore)
            }
            q.andWhere { SubscriptionNotificationsTable.deliveryAttempts less config.maxAttempts }

            val rows = q
                .orderBy(SubscriptionNotificationsTable.createdAt, SortOrder.ASC)
                .limit(config.batchSize)
                .toList()

            val items = rows.map { row ->
                PushWorkItem(
                    notificationId = row[SubscriptionNotificationsTable.id],
                    userId = row[SubscriptionNotificationsTable.userId],
                    message = row[SubscriptionNotificationsTable.message],
                    createdAt = row[SubscriptionNotificationsTable.createdAt],
                    attempts = row[SubscriptionNotificationsTable.deliveryAttempts],
                )
            }

            items.forEach { item ->
                SubscriptionNotificationsTable.update(
                    where = {
                        (SubscriptionNotificationsTable.id eq item.notificationId) and
                            (SubscriptionNotificationsTable.deliveryStatus neq "SENT")
                    },
                ) { stmt ->
                    stmt[deliveryStatus] = "SENDING"
                    stmt[deliveryAttempts] = item.attempts + 1
                    stmt[lastDeliveryAttemptAt] = now
                    stmt[lastDeliveryError] = null
                }
            }

            items
        }

        var sent = 0
        var failed = 0
        var skipped = 0

        val results = work.map { item ->
            val tokens = tokensRepo.listActiveTokens(userId = item.userId, platform = "ANDROID")
            if (tokens.isEmpty()) {
                skipped += 1
                return@map DeliveryResult(item.notificationId, status = "FAILED", error = "No push tokens")
            }

            val title = "Сработала подписка"
            val body = item.message

            runCatching {
                withContext(Dispatchers.IO) {
                    sender.sendToTokens(
                        tokens = tokens,
                        title = title,
                        body = body,
                        data = mapOf(
                            "notificationId" to item.notificationId.toString(),
                            "createdAt" to item.createdAt.toString(),
                        ),
                    )
                }
            }.fold(
                onSuccess = { sendRes ->
                    if (sendRes.successCount > 0) {
                        sent += 1
                        DeliveryResult(item.notificationId, status = "SENT", error = null)
                    } else {
                        failed += 1
                        val attemptNumber = item.attempts + 1
                        val status = if (attemptNumber >= config.maxAttempts) "FAILED" else "PENDING"
                        DeliveryResult(item.notificationId, status = status, error = sendRes.error ?: "FCM failed")
                    }
                },
                onFailure = { t ->
                    failed += 1
                    val attemptNumber = item.attempts + 1
                    val status = if (attemptNumber >= config.maxAttempts) "FAILED" else "PENDING"
                    DeliveryResult(item.notificationId, status = status, error = t.message?.take(500))
                },
            )
        }

        DatabaseFactory.dbQuery {
            results.forEach { res ->
                SubscriptionNotificationsTable.update(
                    where = { SubscriptionNotificationsTable.id eq res.notificationId },
                ) { stmt ->
                    stmt[deliveryStatus] = res.status
                    if (res.status == "SENT") {
                        stmt[deliveredAt] = now
                        stmt[lastDeliveryError] = null
                    } else {
                        stmt[lastDeliveryError] = res.error
                    }
                }
            }
        }

        return SubscriptionDeliveryRunResult(
            picked = work.size,
            sent = sent,
            failed = failed,
            skipped = skipped,
        )
    }

    private data class PushWorkItem(
        val notificationId: Long,
        val userId: Long,
        val message: String,
        val createdAt: Long,
        val attempts: Int,
    )

    private data class DeliveryResult(
        val notificationId: Long,
        val status: String,
        val error: String?,
    )
}

