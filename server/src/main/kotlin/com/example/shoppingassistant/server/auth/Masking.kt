package com.example.shoppingassistant.server.auth

/**
 * Утилиты маскировки чувствительных данных в логах/аудите.
 */
fun maskPhone(phone: String?): String {
    if (phone.isNullOrBlank()) return "unknown"
    val digits = phone.filter { it.isDigit() }
    if (digits.length <= 4) return "***${digits.takeLast(2)}"
    val visible = digits.takeLast(4)
    return "***$visible"
}

fun maskToken(token: String?, keepStart: Int = 4, keepEnd: Int = 2): String {
    if (token.isNullOrBlank()) return "null"
    if (token.length <= keepStart + keepEnd) return "***"
    val start = token.take(keepStart)
    val end = token.takeLast(keepEnd)
    return "$start***$end"
}

fun maskEmail(email: String?): String {
    if (email.isNullOrBlank()) return "unknown"
    val parts = email.split("@")
    if (parts.size != 2) return "***"
    val name = parts[0]
    val domain = parts[1]
    val maskedName = if (name.length <= 2) "***" else "${name.first()}***${name.last()}"
    return "$maskedName@$domain"
}
