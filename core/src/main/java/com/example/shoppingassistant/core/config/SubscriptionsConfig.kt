package com.example.shoppingassistant.core.config

object SubscriptionsConfig {
    const val MAX_SUBSCRIPTIONS: Int = 10
    // Ограничение истории уведомлений (чтобы локальная БД не росла бесконечно)
    const val MAX_NOTIFICATIONS: Int = 200

    // Дефолтный минимальный интервал между алертами (минуты)
    const val DEFAULT_MIN_ALERT_INTERVAL_MIN: Int = 120

    // Production-границы для интервала алертов (минуты)
    const val MIN_ALERT_INTERVAL_MIN: Int = 10
    const val MAX_ALERT_INTERVAL_MIN: Int = 60 * 24 * 7 // 7 дней

    const val DEFAULT_CURRENCY: String = "EUR"
    val SUPPORTED_CURRENCIES: List<String> = listOf("EUR", "USD", "RUB")

    // Дефолтная лестница (проценты)
    val DEFAULT_LADDER_STEPS: List<Double> = listOf(5.0, 10.0, 15.0)

    // Production-валидация условий
    const val MIN_DROP_PERCENT: Double = 1.0
    const val MAX_DROP_PERCENT: Double = 50.0
    val ALLOWED_LADDER_STEPS: List<Double> = listOf(5.0, 10.0, 15.0, 20.0)
}
