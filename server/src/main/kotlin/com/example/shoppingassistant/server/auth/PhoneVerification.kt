package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import io.lettuce.core.api.sync.RedisCommands
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class PhoneVerificationPayload(
    val userId: Long,
    val phone: String,
    val purpose: PhoneVerificationPurpose = PhoneVerificationPurpose.PRIMARY_PHONE_VERIFICATION,
)

enum class PhoneVerificationPurpose {
    PRIMARY_PHONE_VERIFICATION,
    PENDING_PHONE_CHANGE,
}

sealed class PhoneVerificationStartResult {
    data object Sent : PhoneVerificationStartResult()
    data object RateLimited : PhoneVerificationStartResult()
    data object MissingPhone : PhoneVerificationStartResult()
    data object AlreadyVerified : PhoneVerificationStartResult()
}

interface PhoneVerificationTokenStore {
    fun save(token: String, payload: PhoneVerificationPayload)
    fun consume(token: String): PhoneVerificationPayload?
}

class InMemoryPhoneVerificationTokenStore(
    ttlSeconds: Long,
) : PhoneVerificationTokenStore {
    private data class Entry(val payload: PhoneVerificationPayload, val expiresAt: Long)
    private val ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds)
    private val storage = ConcurrentHashMap<String, Entry>()

    override fun save(token: String, payload: PhoneVerificationPayload) {
        storage[token] = Entry(payload, System.currentTimeMillis() + ttlMillis)
    }

    override fun consume(token: String): PhoneVerificationPayload? {
        val entry = storage[token] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            storage.remove(token)
            return null
        }
        storage.remove(token)
        return entry.payload
    }
}

class LettucePhoneVerificationTokenStore(
    private val commands: RedisCommands<String, String>,
    private val ttlSeconds: Long,
    private val keyPrefix: String = "phone_verify:",
) : PhoneVerificationTokenStore {
    override fun save(token: String, payload: PhoneVerificationPayload) {
        val raw = "${payload.userId}:${payload.purpose.name}:${payload.phone}"
        commands.setex(keyPrefix + token, ttlSeconds, raw)
    }

    override fun consume(token: String): PhoneVerificationPayload? {
        val raw = commands.get(keyPrefix + token) ?: return null
        commands.del(keyPrefix + token)
        val parts = raw.split(":")
        if (parts.size < 3) return null
        val userId = parts.first().toLongOrNull() ?: return null
        val purpose = runCatching { PhoneVerificationPurpose.valueOf(parts[1]) }
            .getOrDefault(PhoneVerificationPurpose.PRIMARY_PHONE_VERIFICATION)
        val phone = parts.drop(2).joinToString(":")
        return PhoneVerificationPayload(userId = userId, phone = phone, purpose = purpose)
    }
}

interface PhoneVerificationNotificationSender {
    fun sendVerificationCode(phone: String, token: String)
}

class LoggingPhoneVerificationNotificationSender : PhoneVerificationNotificationSender {
    private val logger = org.slf4j.LoggerFactory.getLogger(LoggingPhoneVerificationNotificationSender::class.java)
    override fun sendVerificationCode(phone: String, token: String) {
        logger.info("Phone verification requested for {} token={}", maskPhone(phone), maskToken(token))
    }
}

class PhoneVerificationService(
    private val tokenStore: PhoneVerificationTokenStore,
    private val notificationSender: PhoneVerificationNotificationSender,
    private val cooldownTracker: ResendCooldownTracker,
    private val random: SecureRandom = SecureRandom(),
) {
    private fun generateCode(): String =
        (100000 + random.nextInt(900000)).toString()

    suspend fun start(userId: Long): PhoneVerificationStartResult {
        val row = DatabaseFactory.dbQuery {
            AuthUsersTable
                .selectAll()
                .where { AuthUsersTable.id eq userId }
                .singleOrNull()
        } ?: return PhoneVerificationStartResult.MissingPhone

        if (row[AuthUsersTable.isDeleted]) return PhoneVerificationStartResult.MissingPhone

        val phone = row[AuthUsersTable.phone] ?: return PhoneVerificationStartResult.MissingPhone
        if (row[AuthUsersTable.phoneVerifiedAt] != null) {
            return PhoneVerificationStartResult.AlreadyVerified
        }

        if (!isPhoneValid(phone)) return PhoneVerificationStartResult.MissingPhone
        if (!cooldownTracker.tryAcquire("verify-phone:${normalizePhone(phone)}:$userId")) {
            return PhoneVerificationStartResult.RateLimited
        }

        val token = generateCode()
        val payload = PhoneVerificationPayload(
            userId = userId,
            phone = normalizePhone(phone),
            purpose = PhoneVerificationPurpose.PRIMARY_PHONE_VERIFICATION,
        )
        tokenStore.save(token, payload)
        notificationSender.sendVerificationCode(payload.phone, token)
        return PhoneVerificationStartResult.Sent
    }

    suspend fun startPendingChange(userId: Long): PhoneVerificationStartResult {
        val row = DatabaseFactory.dbQuery {
            AuthUsersTable
                .selectAll()
                .where { AuthUsersTable.id eq userId }
                .singleOrNull()
        } ?: return PhoneVerificationStartResult.MissingPhone

        if (row[AuthUsersTable.isDeleted]) return PhoneVerificationStartResult.MissingPhone

        val pendingPhone = row[AuthUsersTable.pendingPhone] ?: return PhoneVerificationStartResult.MissingPhone
        if (!isPhoneValid(pendingPhone)) return PhoneVerificationStartResult.MissingPhone
        if (!cooldownTracker.tryAcquire("verify-phone-change:${normalizePhone(pendingPhone)}:$userId")) {
            return PhoneVerificationStartResult.RateLimited
        }

        val token = generateCode()
        val payload = PhoneVerificationPayload(
            userId = userId,
            phone = normalizePhone(pendingPhone),
            purpose = PhoneVerificationPurpose.PENDING_PHONE_CHANGE,
        )
        tokenStore.save(token, payload)
        notificationSender.sendVerificationCode(payload.phone, token)
        return PhoneVerificationStartResult.Sent
    }

    suspend fun confirm(token: String): PhoneVerificationPayload? {
        val payload = tokenStore.consume(token) ?: return null
        val now = System.currentTimeMillis()
        val confirmed = DatabaseFactory.dbQuery {
            val updated = AuthUsersTable.update(
                where = {
                    when (payload.purpose) {
                        PhoneVerificationPurpose.PRIMARY_PHONE_VERIFICATION ->
                            (AuthUsersTable.id eq payload.userId) and
                                (AuthUsersTable.phone eq payload.phone) and
                                (AuthUsersTable.isDeleted eq false)

                        PhoneVerificationPurpose.PENDING_PHONE_CHANGE ->
                            (AuthUsersTable.id eq payload.userId) and
                                (AuthUsersTable.pendingPhone eq payload.phone) and
                                (AuthUsersTable.isDeleted eq false)
                    }
                },
            ) {
                when (payload.purpose) {
                    PhoneVerificationPurpose.PRIMARY_PHONE_VERIFICATION -> {
                        it[phoneVerifiedAt] = now
                    }

                    PhoneVerificationPurpose.PENDING_PHONE_CHANGE -> {
                        it[phone] = payload.phone
                        it[pendingPhone] = null
                        it[pendingPhoneRequestedAt] = null
                        it[phoneVerifiedAt] = now
                    }
                }
            }
            updated > 0
        }
        return if (confirmed) payload else null
    }
}
