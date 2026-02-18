package com.example.shoppingassistant.core.data.ingest.util

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URI

object HtmlExtractors {

    fun extractMeta(html: String, property: String? = null, name: String? = null): String? {
        if (property == null && name == null) return null
        val attr = property?.let { "property=['\"]$it['\"]" } ?: "name=['\"]$name['\"]"
        val regex = Regex(
            """<meta[^>]+$attr[^>]+content=['"]([^'"]+)['"]""",
            setOf(RegexOption.IGNORE_CASE),
        )
        return regex.find(html)?.groupValues?.getOrNull(1)?.let(::cleanText)
    }

    fun extractJsonLd(html: String): String? {
        val scriptRegex = Regex(
            """<script[^>]+application/ld\+json[^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return scriptRegex.find(html)?.groupValues?.getOrNull(1)?.trim()
    }

    fun parseJsonObject(json: Json, raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()

    fun extractCanonicalUrl(html: String): String? {
        val canonical = Regex(
            """<link\s+rel=['"]canonical['"]\s+href=['"]([^'"]+)['"]""",
            setOf(RegexOption.IGNORE_CASE),
        ).find(html)?.groupValues?.getOrNull(1)

        val ogUrl = extractMeta(html, property = "og:url")

        return cleanText(canonical ?: ogUrl)
    }

    fun extractIconUrl(html: String, baseUrl: String): String? {
        val linkRegex = Regex("<link[^>]+>", RegexOption.IGNORE_CASE)
        val relRegex = Regex("""rel=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        val hrefRegex = Regex("""href=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        val candidates = mutableListOf<String>()

        linkRegex.findAll(html).forEach { match ->
            val tag = match.value
            val rel = relRegex.find(tag)?.groupValues?.getOrNull(1)?.lowercase()?.trim()
            if (rel == null) return@forEach
            if (!rel.contains("icon")) return@forEach
            val href = hrefRegex.find(tag)?.groupValues?.getOrNull(1)
            if (!href.isNullOrBlank()) {
                candidates.add(href)
            }
        }

        val raw = candidates.firstOrNull()?.let(::cleanText)
            ?: "/favicon.ico"
        return resolveUrl(baseUrl, raw)
    }

    fun stripHtml(raw: String?): String? {
        if (raw == null) return null
        return raw.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
    }

    fun cleanText(raw: String?): String? =
        raw?.replace(Regex("\\s+"), " ")?.trim()?.ifEmpty { null }

    fun decodeJsonString(json: Json, raw: String?): String? {
        if (raw == null) return null
        val wrapped = "\"${raw.replace("\"", "\\\"")}\""
        return runCatching { json.decodeFromString<String>(wrapped) }.getOrElse { raw }
    }

    fun resolveUrl(baseUrl: String, rawUrl: String?): String? {
        val cleaned = rawUrl?.trim().orEmpty()
        if (cleaned.isBlank()) return null
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) return cleaned
        if (cleaned.startsWith("//")) {
            val scheme = runCatching { URI(baseUrl).scheme }.getOrNull() ?: "https"
            return "$scheme:$cleaned"
        }
        return runCatching { URI(baseUrl).resolve(cleaned).toString() }.getOrNull() ?: cleaned
    }
}
