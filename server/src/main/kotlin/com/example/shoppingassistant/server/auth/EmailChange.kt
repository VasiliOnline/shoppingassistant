package com.example.shoppingassistant.server.auth

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import io.lettuce.core.api.sync.RedisCommands

data class ChangeEmailPayload(val userId: Long, val newEmail: String)

interface ChangeEmailTokenStore {
    fun save(token: String, payload: ChangeEmailPayload)
    fun consume(token: String): ChangeEmailPayload?
}

class InMemoryChangeEmailTokenStore(
    ttlSeconds: Long = TimeUnit.HOURS.toSeconds(24),
) : ChangeEmailTokenStore {

    private data class Entry(val payload: ChangeEmailPayload, val expiresAt: Long)
    private val ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds)
    private val store = ConcurrentHashMap<String, Entry>()

    override fun save(token: String, payload: ChangeEmailPayload) {
        val expiresAt = System.currentTimeMillis() + ttlMillis
        store[token] = Entry(payload, expiresAt)
    }

    override fun consume(token: String): ChangeEmailPayload? {
        val entry = store[token] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            store.remove(token)
            return null
        }
        store.remove(token)
        return entry.payload
    }
}

class LettuceChangeEmailTokenStore(
    private val commands: RedisCommands<String, String>,
    private val ttlSeconds: Long = TimeUnit.HOURS.toSeconds(24),
    private val keyPrefix: String = "change_email:",
) : ChangeEmailTokenStore {
    override fun save(token: String, payload: ChangeEmailPayload) {
        commands.setex(keyPrefix + token, ttlSeconds, "${payload.userId}:${payload.newEmail}")
    }

    override fun consume(token: String): ChangeEmailPayload? {
        val raw = commands.get(keyPrefix + token) ?: return null
        commands.del(keyPrefix + token)
        val delimiter = raw.indexOf(':')
        if (delimiter <= 0 || delimiter >= raw.length - 1) return null
        val userId = raw.substring(0, delimiter).toLongOrNull() ?: return null
        val email = raw.substring(delimiter + 1)
        return ChangeEmailPayload(userId = userId, newEmail = email)
    }
}

private fun generateEmailChangeToken(random: SecureRandom): String {
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Сервис смены email по двухфакторной схеме: старт -> токен -> подтверждение.
 */
class ChangeEmailService(
    private val store: ChangeEmailTokenStore,
    private val notificationSender: VerificationNotificationSender,
    private val random: SecureRandom = SecureRandom(),
) {
    fun start(userId: Long, newEmail: String): String {
        val token = generateEmailChangeToken(random)
        store.save(token, ChangeEmailPayload(userId = userId, newEmail = newEmail))
        notificationSender.sendVerificationLink(email = newEmail, token = token)
        return token
    }

    fun confirm(token: String): ChangeEmailPayload? = store.consume(token)
}
