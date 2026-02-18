package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.server.config.EmailConfig
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Authenticator
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import java.util.Properties
import com.example.shoppingassistant.server.config.SmsConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Logging fallback: пишет письмо с токеном в лог (используем, если SMTP не настроен).
 */
class LoggingResetNotificationSender : ResetNotificationSender {
    private val logger = LoggerFactory.getLogger(LoggingResetNotificationSender::class.java)

    override fun sendResetLink(email: String, resetToken: String) {
        logger.info(
            "Password reset requested for {} token={}",
            maskEmail(email),
            maskToken(resetToken),
        )
    }
}

/**
 * Logging fallback для SMS-кодов.
 */
class LoggingSmsResetNotificationSender : SmsResetNotificationSender {
    private val logger = LoggerFactory.getLogger(LoggingSmsResetNotificationSender::class.java)

    override fun sendResetCode(phone: String, resetToken: String) {
        logger.info(
            "Password reset (phone) requested for ${maskPhone(phone)}, token=${maskToken(resetToken)}",
        )
    }
}

/**
 * SMTP-отправка писем с reset-ссылкой.
 */
class SmtpResetNotificationSender(
    private val config: EmailConfig,
) : ResetNotificationSender {

    private val logger = LoggerFactory.getLogger(SmtpResetNotificationSender::class.java)

    override fun sendResetLink(email: String, resetToken: String) {
        require(config.enabled) { "SMTP is not enabled" }

        val props = Properties().apply {
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.auth", (config.username != null && config.password != null).toString())
            put("mail.smtp.starttls.enable", config.tlsEnabled.toString())
        }

        val session = if (config.username != null && config.password != null) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })
        } else {
            Session.getInstance(props)
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            subject = "Восстановление пароля"
            val link = "${config.resetBaseUrl}/reset-password?token=$resetToken"
            setText(
                """
                Вы запросили восстановление пароля.
                Перейдите по ссылке, чтобы задать новый пароль:
                $link

                Если вы не запрашивали восстановление, проигнорируйте письмо.
                """.trimIndent()
            )
        }

        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                Transport.send(message)
                logger.info("Password reset email sent to $email via SMTP ${config.host}:${config.port}")
                return
            } catch (t: Throwable) {
                lastError = t
                logger.warn("Failed to send reset email to $email (attempt ${attempt + 1}): ${t.message}")
                Thread.sleep(500L * (attempt + 1))
            }
        }
        throw IllegalStateException("Не удалось отправить письмо восстановления", lastError)
    }
}

/**
 * Клиент для отправки SMS через HTTP API провайдера.
 */
class SmsProviderClient(
    private val config: SmsConfig,
    private val client: HttpClient = HttpClient.newBuilder().build(),
) {
    private val logger = LoggerFactory.getLogger(SmsProviderClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun sendSms(phone: String, message: String) {
        require(config.enabled) { "SMS is not enabled" }

        val payload = mapOf(
            "phone" to phone,
            "message" to message,
            "sender" to (config.sender ?: "ShoppingAssistant"),
        )
        val body = json.encodeToString(payload)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.apiUrl!!))
            .timeout(Duration.ofMillis(config.timeoutMillis))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${config.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            logger.warn(
                "SMS send failed status={} phone={} resp={}",
                response.statusCode(),
                maskPhone(phone),
                response.body().take(256),
            )
            throw IllegalStateException("SMS provider responded with ${response.statusCode()}")
        }

        logger.info("SMS dispatched to {}", maskPhone(phone))
    }
}

/**
 * Отправка SMS для восстановления пароля.
 */
class HttpSmsResetNotificationSender(
    private val smsClient: SmsProviderClient,
) : SmsResetNotificationSender {

    override fun sendResetCode(phone: String, resetToken: String) {
        smsClient.sendSms(
            phone = phone,
            message = "Reset code: $resetToken",
        )
    }
}

/**
 * Отправка SMS-кода для подтверждения телефона.
 */
class SmsVerificationNotificationSender(
    private val smsClient: SmsProviderClient,
) : PhoneVerificationNotificationSender {
    override fun sendVerificationCode(phone: String, token: String) {
        smsClient.sendSms(
            phone = phone,
            message = "Verify code: $token",
        )
    }
}
