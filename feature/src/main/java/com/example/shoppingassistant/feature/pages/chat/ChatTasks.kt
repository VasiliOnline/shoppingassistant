package com.example.shoppingassistant.feature.pages.chat

import java.net.URI

/**
 * Контракты UI-экрана чата с продавцом.
 */
data class ChatProps(
    val offerId: String? = null,
    val sellerName: String,
    val sellerStatus: String? = null,
    val sellerAvatarUrl: String? = null,
    val offerTitle: String,
    val offerPrice: Double? = null,
    val externalUrl: String? = null,
    val redirectUrl: String? = null,
    val deeplinkUrl: String? = null,
    val sourceName: String? = null,
    val searchSessionId: String? = null,
    val offerPosition: Int? = null,
    val onBack: () -> Unit,
)

enum class OfferOpenFailureReason(val code: String) {
    URL_MISSING("invalid_url"),
    INVALID_URL("invalid_url"),
    NO_HANDLER("no_handler"),
    BLOCKED_POLICY("blocked_policy"),
    OFFLINE("offline"),
    TIMEOUT("timeout"),
    UNKNOWN("unknown"),
}

enum class OfferOpenFlowSource(val code: String) {
    PRIMARY("primary"),
    RETRY("retry"),
    BROWSER("browser"),
}

enum class OfferOpenChannel(val code: String) {
    NATIVE_APP_LINK("native_app"),
    EXTERNAL_BROWSER("external_browser"),
}

enum class OfferOpenUrlType(val code: String) {
    REDIRECT("redirect"),
    DEEPLINK("deeplink"),
}

fun normalizeExternalUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (host == "localhost") return uri.normalize().toString()
    if (!host.contains('.')) return null
    return uri.normalize().toString()
}

fun extractExternalHost(normalizedUrl: String?): String? {
    val url = normalizedUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()
}

data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
)
