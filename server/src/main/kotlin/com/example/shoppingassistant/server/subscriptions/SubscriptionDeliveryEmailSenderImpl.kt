package com.example.shoppingassistant.server.subscriptions

import com.example.shoppingassistant.server.config.EmailConfig
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import org.slf4j.LoggerFactory

class SubscriptionDeliveryEmailSenderImpl(
    private val config: EmailConfig,
) {
    private val logger = LoggerFactory.getLogger(SubscriptionDeliveryEmailSenderImpl::class.java)

    fun send(email: String, subject: String, text: String) {
        require(config.enabled) { "SMTP is not enabled" }

        val props = Properties().apply {
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.auth", (config.username != null && config.password != null).toString())
            put("mail.smtp.starttls.enable", config.tlsEnabled.toString())
        }

        val session = if (config.username != null && config.password != null) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(config.username, config.password)
            })
        } else {
            Session.getInstance(props)
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            this.subject = subject
            setText(text)
        }

        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                Transport.send(message)
                logger.info("Subscription notification email sent to {}", email)
                return
            } catch (t: Throwable) {
                lastError = t
                logger.warn("Failed to send subscription email to {} (attempt {}): {}", email, attempt + 1, t.message)
                Thread.sleep(500L * (attempt + 1))
            }
        }

        throw IllegalStateException("Failed to send subscription email", lastError)
    }
}

