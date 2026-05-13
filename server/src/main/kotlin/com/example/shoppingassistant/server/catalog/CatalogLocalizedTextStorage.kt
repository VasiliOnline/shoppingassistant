package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.domain.i18n.localizedTextOf

internal fun localizedTextFromStorage(
    localized: LocalizedText?,
    titleRu: String?,
    titleEn: String?,
): LocalizedText =
    localized?.takeUnless { it.isBlank() }
        ?: localizedTextOf("ru" to titleRu, "en" to titleEn)

internal fun LocalizedText.storageRu(fallback: String): String =
    resolve(locale = "ru", fallback = fallback)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

internal fun LocalizedText.storageEn(): String? =
    this["en"]?.trim()?.takeIf { it.isNotEmpty() }
