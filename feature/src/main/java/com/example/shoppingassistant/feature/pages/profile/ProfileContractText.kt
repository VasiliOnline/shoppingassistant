package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.core.push.NotificationPriority
import com.example.shoppingassistant.core.push.TrackingNotificationsSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun profileNotFoundMessage(targetUserId: Long?): String =
    if (targetUserId != null && targetUserId > 0) {
        "Публичный профиль больше недоступен или ссылка на него устарела."
    } else {
        "Публичный профиль недоступен, был удалён или ссылка на него устарела."
    }

internal fun privateProfileUnavailableMessage(): String =
    "Владелец отключил публичный профиль. Попробуйте открыть его позже или воспользуйтесь другим способом связи, если он доступен."

internal fun staleProfileBannerMessage(): String =
    "Сеть недоступна или сервер не ответил. На экране показаны последние сохранённые данные."

internal fun unavailableSectionMessage(section: String): String =
    "Не удалось открыть $section. Обновите экран или попробуйте снова позже."

internal fun formatAccountEmailStatus(email: String, verified: Boolean): String =
    if (verified) "$email • подтверждён" else "$email • нужно подтвердить"

internal fun formatAccountPhoneStatus(
    phone: String?,
    verified: Boolean,
    pendingPhone: String? = null,
): String =
    when {
        !pendingPhone.isNullOrBlank() && phone.isNullOrBlank() ->
            "Новый номер $pendingPhone ожидает подтверждения"
        !pendingPhone.isNullOrBlank() ->
            "$phone • новый номер $pendingPhone ожидает подтверждения"
        phone.isNullOrBlank() -> "Не указан"
        verified -> "$phone • подтверждён"
        else -> "$phone • нужно подтвердить"
    }

internal fun formatNotificationPriorityLabel(priority: NotificationPriority): String = when (priority) {
    NotificationPriority.LOW -> "Низкий"
    NotificationPriority.DEFAULT -> "Обычный"
    NotificationPriority.HIGH -> "Высокий"
}

internal fun formatNotificationPriorityDescription(priority: NotificationPriority): String = when (priority) {
    NotificationPriority.LOW -> "Ненавязчивый режим: уведомления приходят тихо и не перебивают другие сценарии."
    NotificationPriority.DEFAULT -> "Сбалансированный режим для большинства событий: сообщения о важных изменениях приходят вовремя."
    NotificationPriority.HIGH -> "Приоритетный режим для случаев, когда важно быстрее заметить новые события."
}

internal fun formatQuietHoursRange(settings: TrackingNotificationsSettings): String =
    "%02d:00 - %02d:00".format(settings.quietHoursStart, settings.quietHoursEnd)

internal fun formatPrivacySummary(
    publicProfileEnabled: Boolean,
    cityVisible: Boolean,
): String = when {
    publicProfileEnabled && cityVisible ->
        "Публичный профиль открыт, а город виден другим пользователям."

    publicProfileEnabled ->
        "Публичный профиль открыт, но город скрыт."

    else ->
        "Публичный профиль скрыт. Другие пользователи не увидят вашу публичную страницу."
}

internal fun formatAccountNeedsAttentionTitle(count: Int): String =
    if (count > 1) "Есть несколько важных шагов" else "Есть важный шаг"

internal fun formatAccountNeedsAttentionMessage(items: List<AccountAttentionItem>): String =
    items.distinct().joinToString(separator = " ") { item ->
        when (item) {
            AccountAttentionItem.VERIFY_EMAIL ->
                "Подтвердите email, чтобы проще восстанавливать доступ и получать важные письма."

            AccountAttentionItem.ADD_PHONE ->
                "Добавьте телефон, чтобы у вас был резервный способ вернуть доступ."

            AccountAttentionItem.VERIFY_PHONE ->
                "Подтвердите телефон, чтобы усилить защиту аккаунта."

            AccountAttentionItem.CONFIRM_PENDING_PHONE ->
                "Подтвердите новый номер из SMS, чтобы сделать его основным и сохранить безопасный способ восстановления."
        }
    }

internal fun accountEmptyModulesTitle(): String = "Аккаунт пока пуст"

internal fun accountEmptyModulesMessage(): String =
    "Когда появятся объявления, черновики, отслеживания или уведомления, здесь будут быстрые переходы к ним."

internal fun formatAccountCompletionTitle(percent: Int): String =
    "Готовность аккаунта $percent%"

internal fun formatAccountCompletionSummary(completion: AccountCompletionState): String = when {
    completion.percent >= 85 ->
        "Основа уже настроена: публичный профиль выглядит уверенно, а доступ к аккаунту защищён."

    completion.percent >= 60 ->
        "Базовые шаги уже закрыты. Осталось ещё немного, чтобы профиль выглядел надёжнее и доступ было проще восстановить."

    else ->
        "Закройте ключевые шаги, чтобы покупателям было проще доверять профилю, а вам — безопасно управлять доступом."
}

internal fun formatAccountSecurityLevel(level: AccountSecurityLevel): String = when (level) {
    AccountSecurityLevel.BASIC -> "Базовая защита"
    AccountSecurityLevel.PROTECTED -> "Защита включена"
    AccountSecurityLevel.STRONG -> "Усиленная защита"
}

internal fun formatAccountHealthActionTitle(action: AccountHealthAction): String = when (action) {
    AccountHealthAction.VERIFY_EMAIL -> "Подтвердить email"
    AccountHealthAction.ADD_PHONE -> "Добавить телефон"
    AccountHealthAction.VERIFY_PHONE -> "Подтвердить телефон"
    AccountHealthAction.CONFIRM_PENDING_PHONE -> "Подтвердить новый телефон"
    AccountHealthAction.ENABLE_PUBLIC_PROFILE -> "Открыть публичный профиль"
    AccountHealthAction.ADD_AVATAR -> "Добавить фото"
    AccountHealthAction.ADD_BIO -> "Добавить описание"
    AccountHealthAction.ADD_CITY -> "Добавить город"
    AccountHealthAction.PUBLISH_FIRST_LISTING -> "Опубликовать первое объявление"
}

internal fun formatAccountHealthActionMessage(action: AccountHealthAction): String = when (action) {
    AccountHealthAction.VERIFY_EMAIL ->
        "Письма о восстановлении доступа и важных изменениях будут приходить на подтверждённый адрес."

    AccountHealthAction.ADD_PHONE ->
        "Телефон даст резервный способ вернуть доступ, если письмо недоступно."

    AccountHealthAction.VERIFY_PHONE ->
        "Подтверждённый номер снижает риск потери аккаунта и усиливает защиту входа."

    AccountHealthAction.CONFIRM_PENDING_PHONE ->
        "Пока код из SMS не подтверждён, новый номер не станет основным способом восстановления."

    AccountHealthAction.ENABLE_PUBLIC_PROFILE ->
        "Без публичной страницы покупатели не увидят ваш профиль продавца и сигналы доверия."

    AccountHealthAction.ADD_AVATAR ->
        "Фото делает публичный профиль узнаваемым и повышает доверие с первого взгляда."

    AccountHealthAction.ADD_BIO ->
        "Короткое описание помогает быстрее понять, кто вы и чем занимаетесь."

    AccountHealthAction.ADD_CITY ->
        "Город помогает покупателям оценить регион, встречу и доставку."

    AccountHealthAction.PUBLISH_FIRST_LISTING ->
        "После первой публикации профиль превратится в рабочую витрину продавца."
}

internal fun formatRecoveryMethodsSummary(authUserEmailVerified: Boolean, authUserPhoneVerified: Boolean): String = when {
    authUserEmailVerified && authUserPhoneVerified ->
        "Доступ можно восстанавливать через подтверждённый email и телефон."

    authUserEmailVerified ->
        "Основной способ восстановления уже настроен через подтверждённый email."

    authUserPhoneVerified ->
        "Восстановление через телефон уже доступно, но email ещё стоит подтвердить."

    else ->
        "Надёжный путь восстановления ещё не собран. Сначала подтвердите контакты."
}

internal fun formatPublicTrustTitle(stage: PublicTrustStage): String = when (stage) {
    PublicTrustStage.NEW_PROFILE -> "Новый профиль"
    PublicTrustStage.ACTIVE_SELLER -> "Есть активные объявления"
    PublicTrustStage.VERIFIED_PROFILE -> "Контакты подтверждены"
    PublicTrustStage.TRUSTED_SELLER -> "Есть история доверия"
}

internal fun formatPublicTrustSummary(stage: PublicTrustStage): String = when (stage) {
    PublicTrustStage.NEW_PROFILE ->
        "Профиль только начинает собирать репутацию. Доверие будут усиливать подтверждённые контакты, активные объявления и отзывы."

    PublicTrustStage.ACTIVE_SELLER ->
        "У продавца уже есть активные объявления. Следующий шаг для доверия — подтверждённые контакты и история отзывов."

    PublicTrustStage.VERIFIED_PROFILE ->
        "У профиля есть активные объявления и подтверждённые контакты. Это делает продавца заметно надёжнее для покупателей."

    PublicTrustStage.TRUSTED_SELLER ->
        "У профиля уже есть отзывы или накопленные сигналы доверия. Это самый сильный слой репутации на странице продавца."
}

internal fun formatDeleteAccountDeadline(deleteAfterMillis: Long?): String? =
    deleteAfterMillis?.let {
        SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(it))
    }

internal fun formatDeleteAccountScheduledMessage(deleteAfterMillis: Long?): String =
    formatDeleteAccountDeadline(deleteAfterMillis)?.let { deadline ->
        "Удаление уже запланировано. До $deadline аккаунт можно восстановить по токену восстановления."
    } ?: "Удаление уже запланировано. Аккаунт можно восстановить по токену восстановления, пока не истёк срок удаления."

internal fun formatDeleteAccountTokenHint(hasRestoreToken: Boolean): String =
    if (hasRestoreToken) {
        "Сохраните токен восстановления в безопасном месте. Без него вернуть доступ без поддержки не получится."
    } else {
        "Если токен восстановления не отобразился, не закрывайте экран и повторите запрос позже."
    }
