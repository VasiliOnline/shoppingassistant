package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.vision.VisionConsumeRequest
import com.example.shoppingassistant.domain.vision.VisionUsage
import com.example.shoppingassistant.domain.vision.VisionUsageRepository
import com.example.shoppingassistant.domain.vision.VisionUsageResult
import com.example.shoppingassistant.domain.vision.VisionUsageStatus
import com.example.shoppingassistant.server.config.VisionConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class VisionUsageRepositoryImpl(
    private val config: VisionConfig,
) : VisionUsageRepository {
    override suspend fun getUsage(userKey: String?): VisionUsageResult = DatabaseFactory.dbQuery {
        val key = normalizeUserKey(userKey)
        val now = System.currentTimeMillis()
        val row = VisionUsageTable.selectAll().where { VisionUsageTable.userKey eq key }.singleOrNull()
        val usage = row?.toUsage()?.let { applyResetIfNeeded(key, it, now) }
            ?: createDefault(key, now)
        VisionUsageResult(status = VisionUsageStatus.OK, usage = usage)
    }

    override suspend fun consume(request: VisionConsumeRequest): VisionUsageResult = DatabaseFactory.dbQuery {
        val key = normalizeUserKey(request.userKey)
        val units = request.units.coerceAtLeast(0)
        if (units == 0) {
            val current = ensureUsage(key, System.currentTimeMillis())
            return@dbQuery VisionUsageResult(
                status = VisionUsageStatus.ERROR,
                usage = current,
                message = "Некорректная стоимость",
            )
        }

        val now = System.currentTimeMillis()
        val current = ensureUsage(key, now)
        if (current.remainingToday < units || current.remainingTotal < units) {
            return@dbQuery VisionUsageResult(
                status = VisionUsageStatus.LIMIT_EXCEEDED,
                usage = current,
                message = "Лимит распознавания исчерпан",
            )
        }

        val updated = current.copy(
            remainingToday = (current.remainingToday - units).coerceAtLeast(0),
            remainingTotal = (current.remainingTotal - units).coerceAtLeast(0),
            updatedAtMillis = now,
        )
        VisionUsageTable.update({ VisionUsageTable.userKey eq key }) { stmt ->
            stmt[remainingToday] = updated.remainingToday
            stmt[remainingTotal] = updated.remainingTotal
            stmt[resetAtMillis] = updated.resetAtMillis ?: nextResetAt(now)
            stmt[updatedAtMillis] = updated.updatedAtMillis
        }
        VisionUsageResult(status = VisionUsageStatus.OK, usage = updated)
    }

    private fun ensureUsage(userKey: String, now: Long): VisionUsage {
        val row = VisionUsageTable.selectAll().where { VisionUsageTable.userKey eq userKey }.singleOrNull()
        val usage = row?.toUsage()
        return usage?.let { applyResetIfNeeded(userKey, it, now) } ?: createDefault(userKey, now)
    }

    private fun applyResetIfNeeded(userKey: String, usage: VisionUsage, now: Long): VisionUsage {
        val resetAt = usage.resetAtMillis ?: nextResetAt(now)
        if (now < resetAt) return usage

        val refreshed = usage.copy(
            remainingToday = config.dailyLimit,
            resetAtMillis = nextResetAt(now),
            updatedAtMillis = now,
        )
        VisionUsageTable.update({ VisionUsageTable.userKey eq userKey }) { stmt ->
            stmt[remainingToday] = refreshed.remainingToday
            stmt[resetAtMillis] = refreshed.resetAtMillis ?: nextResetAt(now)
            stmt[updatedAtMillis] = refreshed.updatedAtMillis
        }
        return refreshed
    }

    private fun createDefault(userKey: String, now: Long): VisionUsage {
        val usage = VisionUsage(
            remainingToday = config.dailyLimit,
            remainingTotal = config.totalLimit,
            resetAtMillis = nextResetAt(now),
            updatedAtMillis = now,
        )
        VisionUsageTable.insert { stmt ->
            stmt[this.userKey] = userKey
            stmt[remainingToday] = usage.remainingToday
            stmt[remainingTotal] = usage.remainingTotal
            stmt[resetAtMillis] = usage.resetAtMillis ?: nextResetAt(now)
            stmt[updatedAtMillis] = usage.updatedAtMillis
        }
        return usage
    }

    private fun normalizeUserKey(userKey: String?): String =
        userKey?.trim()?.takeIf { it.isNotBlank() } ?: "anonymous"

    private fun nextResetAt(now: Long): Long =
        now + config.resetIntervalHours * 60L * 60L * 1000L

    private fun ResultRow.toUsage(): VisionUsage = VisionUsage(
        remainingToday = this[VisionUsageTable.remainingToday],
        remainingTotal = this[VisionUsageTable.remainingTotal],
        resetAtMillis = this[VisionUsageTable.resetAtMillis],
        updatedAtMillis = this[VisionUsageTable.updatedAtMillis],
    )
}
