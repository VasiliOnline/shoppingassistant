package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.core.push.NotificationPriority
import com.example.shoppingassistant.core.push.TrackingNotificationsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileContractTextTest {

    @Test
    fun not_found_message_does_not_leak_debug_user_id() {
        val message = profileNotFoundMessage(42)

        assertEquals(
            "Публичный профиль больше недоступен или ссылка на него устарела.",
            message,
        )
        assertFalse(message.contains("42"))
    }

    @Test
    fun account_contact_statuses_follow_owner_contract() {
        assertEquals(
            "user@test.com • подтверждён",
            formatAccountEmailStatus(email = "user@test.com", verified = true),
        )
        assertEquals(
            "user@test.com • нужно подтвердить",
            formatAccountEmailStatus(email = "user@test.com", verified = false),
        )
        assertEquals("Не указан", formatAccountPhoneStatus(phone = null, verified = false))
        assertEquals(
            "+7 999 000 00 00 • нужно подтвердить",
            formatAccountPhoneStatus(phone = "+7 999 000 00 00", verified = false),
        )
        assertEquals(
            "+7 999 000 00 00 • подтверждён",
            formatAccountPhoneStatus(phone = "+7 999 000 00 00", verified = true),
        )
        assertEquals(
            "+7 999 000 00 00 • новый номер +7 999 111 22 33 ожидает подтверждения",
            formatAccountPhoneStatus(
                phone = "+7 999 000 00 00",
                verified = true,
                pendingPhone = "+7 999 111 22 33",
            ),
        )
    }

    @Test
    fun notification_priority_labels_are_product_facing() {
        assertEquals("Низкий", formatNotificationPriorityLabel(NotificationPriority.LOW))
        assertEquals("Обычный", formatNotificationPriorityLabel(NotificationPriority.DEFAULT))
        assertEquals("Высокий", formatNotificationPriorityLabel(NotificationPriority.HIGH))
        assertTrue(
            formatNotificationPriorityDescription(NotificationPriority.HIGH)
                .contains("быстрее"),
        )
        assertEquals(
            "22:00 - 08:00",
            formatQuietHoursRange(
                TrackingNotificationsSettings(
                    quietHoursStart = 22,
                    quietHoursEnd = 8,
                ),
            ),
        )
    }

    @Test
    fun privacy_summary_reflects_visibility_contract() {
        assertEquals(
            "Публичный профиль открыт, а город виден другим пользователям.",
            formatPrivacySummary(publicProfileEnabled = true, cityVisible = true),
        )
        assertEquals(
            "Публичный профиль открыт, но город скрыт.",
            formatPrivacySummary(publicProfileEnabled = true, cityVisible = false),
        )
        assertEquals(
            "Публичный профиль скрыт. Другие пользователи не увидят вашу публичную страницу.",
            formatPrivacySummary(publicProfileEnabled = false, cityVisible = true),
        )
    }

    @Test
    fun delete_account_copy_mentions_restore_token_even_without_deadline() {
        val message = formatDeleteAccountScheduledMessage(deleteAfterMillis = null)
        val hint = formatDeleteAccountTokenHint(hasRestoreToken = true)

        assertTrue(message.contains("токену восстановления"))
        assertTrue(hint.contains("Сохраните токен восстановления"))
    }

    @Test
    fun account_attention_and_empty_texts_are_product_facing() {
        assertEquals("Есть важный шаг", formatAccountNeedsAttentionTitle(1))
        assertTrue(
            formatAccountNeedsAttentionMessage(listOf(AccountAttentionItem.VERIFY_EMAIL))
                .contains("Подтвердите email"),
        )
        assertTrue(
            formatAccountNeedsAttentionMessage(listOf(AccountAttentionItem.CONFIRM_PENDING_PHONE))
                .contains("Подтвердите новый номер"),
        )
        assertEquals("Аккаунт пока пуст", accountEmptyModulesTitle())
        assertTrue(accountEmptyModulesMessage().contains("быстрые переходы"))
        assertEquals("Готовность аккаунта 64%", formatAccountCompletionTitle(64))
        assertTrue(
            formatAccountCompletionSummary(
                AccountCompletionState(
                    percent = 40,
                    completedCount = 3,
                    totalCount = 7,
                ),
            ).contains("доверять"),
        )
        assertEquals("Усиленная защита", formatAccountSecurityLevel(AccountSecurityLevel.STRONG))
        assertTrue(
            formatAccountHealthActionMessage(AccountHealthAction.PUBLISH_FIRST_LISTING)
                .contains("витрину"),
        )
        assertTrue(
            formatRecoveryMethodsSummary(
                authUserEmailVerified = true,
                authUserPhoneVerified = false,
            ).contains("email"),
        )
    }

    @Test
    fun public_trust_copy_stays_marketplace_facing() {
        assertEquals("Новый профиль", formatPublicTrustTitle(PublicTrustStage.NEW_PROFILE))
        assertTrue(
            formatPublicTrustSummary(PublicTrustStage.VERIFIED_PROFILE)
                .contains("подтверждённые контакты"),
        )
    }
}
