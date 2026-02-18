package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.server.db.AuthAuditTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.insert

/**
 * Сервис для записи аудита auth-событий в таблицу auth_audit.
 */
class AuthAuditService {
    suspend fun log(
        event: String,
        userId: Long? = null,
        email: String? = null,
        ip: String? = null,
        userAgent: String? = null,
    ) {
        val ts = System.currentTimeMillis()
        DatabaseFactory.dbQuery {
            AuthAuditTable.insert {
                it[AuthAuditTable.event] = event
                it[AuthAuditTable.userId] = userId
                it[AuthAuditTable.email] = email
                it[AuthAuditTable.ip] = ip
                it[AuthAuditTable.userAgent] = userAgent
                it[AuthAuditTable.createdAt] = ts
            }
        }
    }
}
