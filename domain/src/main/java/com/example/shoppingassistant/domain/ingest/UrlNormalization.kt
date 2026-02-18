package com.example.shoppingassistant.domain.ingest

import java.net.URI

data class NormalizedUrl(
    val raw: String,
    val normalized: String,
    val host: String?,
    val scheme: String,
    val path: String,
    val query: String?,
)

data class UrlNormalizationConfig(
    val defaultScheme: String = "https",
    val dropWww: Boolean = true,
    val ignoredQueryKeys: Set<String> = setOf(
        "gclid",
        "fbclid",
        "yclid",
        "igshid",
    ),
    val ignoredQueryPrefixes: Set<String> = setOf(
        "utm_",
    ),
)

interface UrlNormalizer {
    fun normalize(url: String): NormalizedUrl
}

class DefaultUrlNormalizer(
    private val config: UrlNormalizationConfig = UrlNormalizationConfig(),
) : UrlNormalizer {

    override fun normalize(url: String): NormalizedUrl {
        val raw = url.trim()
        if (raw.isEmpty()) {
            return NormalizedUrl(
                raw = raw,
                normalized = raw,
                host = null,
                scheme = config.defaultScheme,
                path = "",
                query = null,
            )
        }

        val prepared = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "${config.defaultScheme}:$raw"
            else -> "${config.defaultScheme}://$raw"
        }

        val uri = runCatching { URI(prepared) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: config.defaultScheme
        var host = uri?.host?.lowercase()
        if (host != null && config.dropWww && host.startsWith("www.")) {
            host = host.removePrefix("www.")
        }

        val port = uri?.port?.takeIf { it != -1 && it != defaultPort(scheme) }
        val rawPath = uri?.rawPath.orEmpty().ifEmpty { "/" }
        val path = if (rawPath.length > 1 && rawPath.endsWith("/")) {
            rawPath.dropLast(1)
        } else {
            rawPath
        }
        val query = normalizeQuery(uri?.rawQuery)

        val normalized = buildString {
            append(scheme)
            append("://")
            if (host != null) append(host) else append("")
            if (port != null) append(":").append(port)
            append(path)
            if (!query.isNullOrBlank()) append("?").append(query)
        }

        return NormalizedUrl(
            raw = raw,
            normalized = normalized,
            host = host,
            scheme = scheme,
            path = path,
            query = query,
        )
    }

    private fun normalizeQuery(query: String?): String? {
        if (query.isNullOrBlank()) return null
        val items = query.split("&")
            .mapNotNull { item ->
                val trimmed = item.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split("=", limit = 2)
                val key = parts[0].trim()
                if (key.isEmpty()) return@mapNotNull null
                if (shouldIgnoreKey(key)) return@mapNotNull null
                val value = if (parts.size > 1) parts[1].trim() else ""
                key to value
            }
            .sortedWith(compareBy({ it.first.lowercase() }, { it.second.lowercase() }))

        if (items.isEmpty()) return null
        return items.joinToString("&") { (key, value) ->
            if (value.isEmpty()) key else "$key=$value"
        }
    }

    private fun shouldIgnoreKey(key: String): Boolean {
        val lower = key.lowercase()
        if (lower in config.ignoredQueryKeys) return true
        return config.ignoredQueryPrefixes.any { prefix -> lower.startsWith(prefix) }
    }

    private fun defaultPort(scheme: String): Int = when (scheme.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
}
