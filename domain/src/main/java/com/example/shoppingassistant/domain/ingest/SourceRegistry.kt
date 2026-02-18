package com.example.shoppingassistant.domain.ingest

/**
 * Возможности источника (ингест, трекинг цен и т.д.).
 */
data class SourceCapabilities(
    val canIngest: Boolean = true,
    val canTrackPrice: Boolean = true,
    val needsJs: Boolean = false,
    val needsGeo: Boolean = false,
    val requiresBrowser: Boolean = false,
)

/**
 * Метаданные источника (единая точка правды).
 */
data class SourceRegistryEntry(
    val id: String,
    val sourceType: SourceType,
    val displayName: String,
    val hosts: Set<String> = emptySet(),
    val hostPatterns: List<String> = emptyList(),
    val urlPatterns: List<String> = emptyList(),
    val iconUrl: String? = null,
    val capabilities: SourceCapabilities = SourceCapabilities(),
    val rolloutEnabled: Boolean = true,
)

interface SourceRegistry {
    fun all(): List<SourceRegistryEntry>
    fun findById(id: String): SourceRegistryEntry?
    fun findBySourceType(type: SourceType): SourceRegistryEntry?
}

enum class SourceResolveStatus {
    RESOLVED,
    UNSUPPORTED,
    DISABLED,
    INVALID_URL,
}

data class SourceResolveResult(
    val status: SourceResolveStatus,
    val source: SourceRegistryEntry? = null,
    val normalizedUrl: NormalizedUrl? = null,
) {
    val sourceType: SourceType = source?.sourceType ?: SourceType.UNKNOWN
    val sourceId: String? = source?.id
}

interface SourceResolver {
    fun resolve(url: String): SourceResolveResult
}

class DefaultSourceResolver(
    private val registry: SourceRegistry,
    private val urlNormalizer: UrlNormalizer,
) : SourceResolver {

    override fun resolve(url: String): SourceResolveResult {
        val normalized = urlNormalizer.normalize(url)
        val host = normalized.host?.lowercase() ?: return SourceResolveResult(
            status = SourceResolveStatus.INVALID_URL,
            normalizedUrl = normalized,
        )

        val entry = registry.all().firstOrNull { candidate ->
            hostMatches(candidate, host) || urlMatches(candidate, normalized.normalized)
        }
        if (entry == null) {
            return SourceResolveResult(
                status = SourceResolveStatus.UNSUPPORTED,
                normalizedUrl = normalized,
            )
        }
        if (!entry.rolloutEnabled || !entry.capabilities.canIngest || entry.capabilities.requiresBrowser) {
            return SourceResolveResult(
                status = SourceResolveStatus.DISABLED,
                source = entry,
                normalizedUrl = normalized,
            )
        }
        return SourceResolveResult(
            status = SourceResolveStatus.RESOLVED,
            source = entry,
            normalizedUrl = normalized,
        )
    }

    private fun hostMatches(entry: SourceRegistryEntry, host: String): Boolean {
        val direct = entry.hosts.any { it.equals(host, ignoreCase = true) }
        if (direct) return true
        return entry.hostPatterns.any { pattern -> matchHostPattern(host, pattern) }
    }

    private fun urlMatches(entry: SourceRegistryEntry, url: String): Boolean {
        if (entry.urlPatterns.isEmpty()) return false
        return entry.urlPatterns.any { pattern ->
            runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(url) }
                .getOrDefault(false)
        }
    }

    private fun matchHostPattern(host: String, pattern: String): Boolean {
        val cleaned = pattern.trim().lowercase()
        if (cleaned.startsWith("*.")) {
            val suffix = cleaned.removePrefix("*.")
            return host != suffix && host.endsWith(".$suffix")
        }
        return host == cleaned
    }
}

/**
 * Базовый каталог источников (для bootstrap).
 */
object DefaultSourceCatalog {
    val entries: List<SourceRegistryEntry> = listOf(
        SourceRegistryEntry(
            id = "avito",
            sourceType = SourceType.AVITO,
            displayName = "Avito",
            hosts = setOf("avito.ru", "m.avito.ru"),
            hostPatterns = listOf("*.avito.ru"),
            iconUrl = "https://www.avito.ru/favicon.ico",
            capabilities = SourceCapabilities(
                canIngest = true,
                canTrackPrice = true,
                needsJs = false,
                needsGeo = false,
                requiresBrowser = false,
            ),
            rolloutEnabled = true,
        ),
    )
}
