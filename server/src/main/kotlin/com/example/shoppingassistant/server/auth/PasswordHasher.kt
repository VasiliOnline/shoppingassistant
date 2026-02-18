// Last synced: 2025-11-20 21:13
package com.example.shoppingassistant.server.auth

import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Отдельный компонент для хеширования паролей.
 * Держим в server, домен от деталей не зависит.
 */
interface PasswordHasher {

    /**
     * Возвращает строку-хеш, пригодную для хранения в БД.
     */
    fun hash(password: String): String

    /**
     * Проверяет, соответствует ли [password] сохранённому [hash].
     */
    fun verify(password: String, hash: String): Boolean
}

/**
 * Прод-реализация: по умолчанию хеширует через BCrypt.
 * Для обратной совместимости умеет проверять старый формат "<saltBase64>:<hashBase64>" (salted SHA-256).
 */
class DefaultPasswordHasher(
    private val random: SecureRandom = SecureRandom(),
    private val bcryptRounds: Int = 12,
) : PasswordHasher {

    override fun hash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(bcryptRounds))
    }

    override fun verify(password: String, hash: String): Boolean {
        return when {
            hash.startsWith("\$2") -> BCrypt.checkpw(password, hash)
            else -> verifyLegacySha(password, hash)
        }
    }

    /**
     * Поддержка старых хешей "<saltBase64>:<hashBase64>".
     */
    private fun verifyLegacySha(password: String, legacyHash: String): Boolean {
        val parts = legacyHash.split(":")
        if (parts.size != 2) return false

        val salt = try {
            Base64.getDecoder().decode(parts[0])
        } catch (_: IllegalArgumentException) {
            return false
        }

        val expectedHash = try {
            Base64.getDecoder().decode(parts[1])
        } catch (_: IllegalArgumentException) {
            return false
        }

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val actualHash = digest.digest(password.toByteArray(Charsets.UTF_8))

        return MessageDigest.isEqual(expectedHash, actualHash)
    }
}
