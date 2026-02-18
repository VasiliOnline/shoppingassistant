// Last synced: 2025-11-23 18:38
package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import io.lettuce.core.api.sync.RedisCommands
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Контракт менеджера токенов восстановления пароля "resetToken -> userId".
 */
interface PasswordResetTokenManager {

    /**
     * Создаёт новый reset-токен для пользователя и сохраняет его в хранилище.
     *
     * @return строковый токен, который можно передать пользователю
     * (позже — по e-mail/SMS и т.п.).
     */
    fun createResetToken(userId: Long): String

    /**
     * Пытается потребить токен: возвращает userId и удаляет запись из хранилища.
     * Если токен не найден или протух — вернёт null.
     */
    fun consumeResetToken(token: String): Long?
}

/**
 * Абстрактное хранилище reset-токенов.
 */
interface PasswordResetTokenStore {
    fun save(token: String, userId: Long)
    fun findUserId(token: String): Long?
    fun delete(token: String)
}

private fun generateResetToken(random: SecureRandom): String {
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)
}

/**
 * Базовая реализация менеджера reset-токенов поверх абстрактного хранилища.
 */
open class BasePasswordResetTokenManager(
    private val store: PasswordResetTokenStore,
    private val random: SecureRandom = SecureRandom(),
) : PasswordResetTokenManager {

    override fun createResetToken(userId: Long): String {
        val token = generateResetToken(random)
        store.save(token, userId)
        return token
    }

    override fun consumeResetToken(token: String): Long? {
        val userId = store.findUserId(token) ?: return null
        store.delete(token)
        return userId
    }
}

/**
 * In-memory реализация, полезна для тестов и dev-сборки без Redis.
 */
class InMemoryPasswordResetTokenStore(
    ttlSeconds: Long = TimeUnit.MINUTES.toSeconds(15),
) : PasswordResetTokenStore {

    private data class Entry(val userId: Long, val expiresAtMillis: Long)

    private val tokens = ConcurrentHashMap<String, Entry>()
    private val ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds)

    override fun save(token: String, userId: Long) {
        val expiresAt = System.currentTimeMillis() + ttlMillis
        tokens[token] = Entry(userId, expiresAt)
    }

    override fun findUserId(token: String): Long? {
        val entry = tokens[token] ?: return null
        return if (entry.expiresAtMillis < System.currentTimeMillis()) {
            tokens.remove(token)
            null
        } else {
            entry.userId
        }
    }

    override fun delete(token: String) {
        tokens.remove(token)
    }
}

/**
 * Redis-ориентированное хранилище reset-токенов.
 *
 * Ключи хранятся с префиксом, чтобы не пересекаться с сессиями.
 */
class LettucePasswordResetTokenStore(
    private val commands: RedisCommands<String, String>,
    private val ttlSeconds: Long,
    private val keyPrefix: String = "password_reset:",
) : PasswordResetTokenStore {

    override fun save(token: String, userId: Long) {
        val key = keyPrefix + token
        commands.setex(key, ttlSeconds, userId.toString())
    }

    override fun findUserId(token: String): Long? {
        val key = keyPrefix + token
        return commands.get(key)?.toLongOrNull()
    }

    override fun delete(token: String) {
        val key = keyPrefix + token
        commands.del(key)
    }
}

/**
 * Реализация менеджера reset-токенов поверх Redis/in-memory хранилища.
 */
class RedisPasswordResetTokenManager(
    store: PasswordResetTokenStore,
    random: SecureRandom = SecureRandom(),
) : BasePasswordResetTokenManager(store, random)

/**
 * Сервис восстановления пароля.
 *
 * Работает:
 * - с Postgres (AuthUsersTable через DatabaseFactory.dbQuery);
 * - с PasswordHasher (тот же формат, что и при регистрации);
 * - с PasswordResetTokenManager (Redis/in-memory).
 */
class PasswordResetService(
    private val passwordHasher: PasswordHasher,
    private val tokenManager: PasswordResetTokenManager,
    private val resetNotificationSender: ResetNotificationSender,
    private val smsResetNotificationSender: SmsResetNotificationSender,
) {

    /**
     * Стартует процесс восстановления пароля.
     *
     * В целях безопасности HTTP-слой всегда отвечает 200 OK и не раскрывает,
     * существует ли email. reset-токен отправляется через ResetNotificationSender.
     */
    suspend fun startPasswordReset(email: String) {
        val normalizedEmail = normalizeEmail(email)

        DatabaseFactory.dbQuery {
            val row = AuthUsersTable
                .selectAll()
                .where { (AuthUsersTable.email eq normalizedEmail) and (AuthUsersTable.isDeleted eq false) }
                .singleOrNull()
                ?: return@dbQuery Unit

            val userId = row[AuthUsersTable.id]
            val token = tokenManager.createResetToken(userId)
            resetNotificationSender.sendResetLink(
                email = normalizedEmail,
                resetToken = token,
            )
        }
    }

    /**
     * Старт восстановления по телефону. Не раскрывает, существует ли номер.
     */
    suspend fun startPasswordResetByPhone(phone: String) {
        if (!isPhoneValid(phone)) return
        val normalizedPhone = normalizePhone(phone)

        DatabaseFactory.dbQuery {
            val row = AuthUsersTable
                .selectAll()
                .where { (AuthUsersTable.phone eq normalizedPhone) and (AuthUsersTable.isDeleted eq false) }
                .singleOrNull()
                ?: return@dbQuery Unit
            if (row[AuthUsersTable.phoneVerifiedAt] == null) {
                return@dbQuery Unit
            }

            val userId = row[AuthUsersTable.id]
            val token = tokenManager.createResetToken(userId)
            smsResetNotificationSender.sendResetCode(
                phone = normalizedPhone,
                resetToken = token,
            )
        }
    }

    /**
     * Меняет пароль пользователя по валидному reset-токену.
     *
     * @return true, если пароль был успешно изменён;
     *         false, если токен не найден/просрочен.
     */
    suspend fun resetPassword(resetToken: String, newPassword: String): Boolean {
        val userId = tokenManager.consumeResetToken(resetToken) ?: return false
        val newHash = passwordHasher.hash(newPassword)

        return DatabaseFactory.dbQuery {
            val updatedRows = AuthUsersTable.update(
                where = { AuthUsersTable.id eq userId },
            ) { row ->
                row[password] = newHash
            }
            updatedRows > 0
        }
    }

    private fun normalizeEmail(email: String): String =
        email.trim().lowercase(Locale.ROOT)
}

/**
 * Вспомогательный менеджер для self-reset (смена пароля по старому).
 * Не хранит токены, кодирует userId в строку.
 */
class SelfResetTokenManager : PasswordResetTokenManager {
    override fun createResetToken(userId: Long): String = "SELF_RESET_$userId"
    override fun consumeResetToken(token: String): Long? =
        token.removePrefix("SELF_RESET_").toLongOrNull()
}
