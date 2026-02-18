package com.example.shoppingassistant.server.push

import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PushTokensRepositoryImpl : PushTokensRepository {

    override suspend fun upsertToken(
        userId: Long,
        platform: String,
        token: String,
        deviceId: String?,
    ): Long =
        DatabaseFactory.dbQuery {
            val now = System.currentTimeMillis()
            val normalizedPlatform = platform.trim().uppercase()
            val normalizedToken = token.trim()

            val q = PushTokensTable.selectAll()
            q.andWhere { PushTokensTable.userId eq userId }
            q.andWhere { PushTokensTable.platform eq normalizedPlatform }
            q.andWhere { PushTokensTable.token eq normalizedToken }
            val existing = q.limit(1).singleOrNull()
            if (existing != null) {
                val id = existing[PushTokensTable.id]
                PushTokensTable.update({ PushTokensTable.id eq id }) { stmt ->
                    stmt[this.deviceId] = deviceId
                    stmt[this.isActive] = true
                    stmt[this.lastSeenAt] = now
                }
                return@dbQuery id
            }

            PushTokensTable.insert { stmt ->
                stmt[this.userId] = userId
                stmt[this.platform] = normalizedPlatform
                stmt[this.token] = normalizedToken
                stmt[this.deviceId] = deviceId
                stmt[this.isActive] = true
                stmt[this.createdAt] = now
                stmt[this.lastSeenAt] = now
            }[PushTokensTable.id]
        }

    override suspend fun listActiveTokens(userId: Long, platform: String?): List<String> =
        DatabaseFactory.dbQuery {
            val q = PushTokensTable.selectAll()
            q.andWhere { PushTokensTable.userId eq userId }
            q.andWhere { PushTokensTable.isActive eq true }
            if (platform != null) {
                q.andWhere { PushTokensTable.platform eq platform.trim().uppercase() }
            }
            q.orderBy(PushTokensTable.lastSeenAt, org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(50)
                .map { it[PushTokensTable.token] }
        }
}

