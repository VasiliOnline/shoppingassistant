package com.example.shoppingassistant.feature.pages.offers

import java.util.Locale

fun isDeliverableToUser(
    shippingCountries: List<String>,
    userCountryCode: String?,
): Boolean {
    if (userCountryCode.isNullOrBlank()) return true
    if (shippingCountries.isEmpty()) return true

    val userCode = userCountryCode.trim().uppercase(Locale.ROOT)
    val userName = countryName(userCode)

    return shippingCountries.any { raw ->
        val candidate = raw.trim()
        if (candidate.isBlank()) return@any false
        val lowered = candidate.lowercase(Locale.ROOT)
        if (lowered in setOf("world", "worldwide", "global", "international", "intl", "all")) {
            return@any true
        }
        if (candidate.equals(userCode, ignoreCase = true)) return@any true
        val candidateName = countryName(candidate)
        candidateName != null && candidateName.equals(userName, ignoreCase = true)
    }
}

private fun countryName(countryCodeOrName: String?): String? {
    if (countryCodeOrName.isNullOrBlank()) return null
    val trimmed = countryCodeOrName.trim()
    if (trimmed.length > 3) return trimmed
    return try {
        Locale("", trimmed.uppercase(Locale.ROOT)).getDisplayCountry(Locale("ru", "RU"))
            .takeIf { it.isNotBlank() } ?: trimmed
    } catch (_: Exception) {
        trimmed
    }
}
