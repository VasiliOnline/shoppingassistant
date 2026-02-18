package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.server.config.EmailConfig
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import java.util.Properties

class SmtpVerificationNotificationSender(
    private val config: EmailConfig,
) : VerificationNotificationSender {
    private val logger = LoggerFactory.getLogger(SmtpVerificationNotificationSender::class.java)

    override fun sendVerificationLink(email: String, token: String) {
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
            subject = "Подтверждение email"
            val link = "${config.resetBaseUrl}/verify-email?token=$token"
            setText(
                """
                Подтвердите свой email, перейдя по ссылке:
                $link
                Если вы не регистрировались, игнорируйте письмо.
                """.trimIndent()
            )
        }

        Transport.send(message)
        logger.info("Verification email sent to $email via SMTP ${config.host}:${config.port}")
    }
}
