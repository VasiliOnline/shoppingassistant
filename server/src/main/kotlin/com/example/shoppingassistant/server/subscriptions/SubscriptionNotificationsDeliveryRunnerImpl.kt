package com.example.shoppingassistant.server.subscriptions

import com.example.shoppingassistant.server.config.EmailConfig
import com.example.shoppingassistant.server.config.SubscriptionsDeliveryConfig
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.innerJoin

class SubscriptionNotificationsDeliveryRunnerImpl(
    private val config: SubscriptionsDeliveryConfig,
    private val emailConfig: EmailConfig,
) : SubscriptionNotificationsDeliveryRunner {

    override suspend fun runOnce(): SubscriptionDeliveryRunResult {
        if (!config.enabled) {
            return SubscriptionDeliveryRunResult(picked = 0, sent = 0, failed = 0, skipped = 0)
        }
        if (!emailConfig.enabled) {
            return SubscriptionDeliveryRunResult(picked = 0, sent = 0, failed = 0, skipped = 0)
        }

        val now = System.currentTimeMillis()
        val stuckBefore = now - config.stuckSendingTimeoutMillis

        val work = DatabaseFactory.dbQuery {
            val join = SubscriptionNotificationsTable.innerJoin(AuthUsersTable, { userId }, { AuthUsersTable.id })

            val q = join.selectAll()
            q.andWhere { SubscriptionNotificationsTable.deliveryChannel eq "EMAIL" }
            q.andWhere { AuthUsersTable.isDeleted eq false }
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
                DeliveryWorkItem(
                    notificationId = row[SubscriptionNotificationsTable.id],
                    userId = row[SubscriptionNotificationsTable.userId],
                    email = row[AuthUsersTable.email],
                    emailVerified = row[AuthUsersTable.emailVerified],
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

        val sender = SubscriptionDeliveryEmailSenderImpl(emailConfig)

        val results = work.map { item ->
            val subject = "Уведомление по подписке"
            val body = buildString {
                appendLine(item.message)
                appendLine()
                appendLine("Время (ms): ${item.createdAt}")
            }

            if (!item.emailVerified) {
                skipped += 1
                return@map DeliveryResult(
                    notificationId = item.notificationId,
                    status = "FAILED",
                    error = "Email is not verified",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    sender.send(item.email, subject, body)
                }
            }.fold(
                onSuccess = {
                    sent += 1
                    DeliveryResult(item.notificationId, status = "SENT", error = null)
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

    private data class DeliveryWorkItem(
        val notificationId: Long,
        val userId: Long,
        val email: String,
        val emailVerified: Boolean,
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
