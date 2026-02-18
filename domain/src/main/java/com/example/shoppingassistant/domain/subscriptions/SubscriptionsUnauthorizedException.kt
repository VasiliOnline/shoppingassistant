package com.example.shoppingassistant.domain.subscriptions

/**
 * Бросается, когда операции с подписками вызываются без авторизации (без токена).
 */
class SubscriptionsUnauthorizedException :
    IllegalStateException("Требуется авторизация для работы с подписками")

