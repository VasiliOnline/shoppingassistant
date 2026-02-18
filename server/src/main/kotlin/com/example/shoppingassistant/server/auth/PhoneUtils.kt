package com.example.shoppingassistant.server.auth

import java.util.Locale

/**
 * Приводит телефон к цифровому виду без пробелов/скобок/плюсов.
 */
fun normalizePhone(raw: String): String =
    raw.filter { it.isDigit() }

/**
 * Простая валидация телефона: проверяем минимальную длину после нормализации.
 */
fun isPhoneValid(raw: String, minDigits: Int = 10): Boolean =
    normalizePhone(raw).length >= minDigits
