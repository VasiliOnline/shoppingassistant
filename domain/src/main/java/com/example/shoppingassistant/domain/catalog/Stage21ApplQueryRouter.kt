package com.example.shoppingassistant.domain.catalog

class Stage21ApplQueryRouter(
    aliases: List<ApplAliasSeedRow> = Stage21ApplPackageLoader.aliasSeedRows,
    browseNodes: List<BrowseNode> = Stage21ApplPackageLoader.browseNodes,
    routingRulesYaml: String = Stage21ApplPackageLoader.routingRulesYaml,
) : QueryRouter {

    private val rules: ApplRoutingRules = ApplRoutingRulesParser.parse(routingRulesYaml)
    private val applNodeCanonical: Map<String, String> = browseNodes
        .asSequence()
        .filter { it.browseCode.startsWith(BROWSE_APPL_PREFIX) }
        .mapNotNull { node ->
            val canonical = node.targetCategoryCode?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            node.browseCode to canonical
        }
        .toMap()

    private val applAliases: List<ApplAliasRuntimeEntry> = aliases
        .asSequence()
        .map { row ->
            ApplAliasRuntimeEntry(
                locale = row.locale,
                query = row.query,
                normalizedQuery = normalize(row.normalizedQuery),
                tokens = tokenize(normalize(row.normalizedQuery)).filter { it.length >= 3 },
                targetType = row.targetType,
                targetId = row.targetId,
                weight = row.weight,
                matchKind = row.matchKind,
                negativeTokens = row.negativeTokens.map(::normalize).filter { it.isNotBlank() },
                isBlocked = row.isBlocked,
            )
        }
        .filter { it.normalizedQuery.isNotBlank() }
        .filter { runtimeEntry ->
            when (runtimeEntry.targetType) {
                ApplAliasTargetType.NODE -> runtimeEntry.targetId.startsWith(BROWSE_APPL_PREFIX)
                ApplAliasTargetType.CANONICAL -> runtimeEntry.targetId.isNotBlank()
            }
        }
        .toList()

    override suspend fun route(query: String, locale: String): QueryRoutingResult =
        routeWithCandidates(query = query, locale = locale).result

    fun routeWithCandidates(
        query: String,
        locale: String = "ru-RU",
        topN: Int = 3,
    ): QueryRoutingDebugResult {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) {
            return QueryRoutingDebugResult(
                result = fallbackNoMatch(normalizedQuery),
                topCandidates = emptyList(),
                wasAmbiguous = false,
            )
        }

        val disambiguated = resolveDisambiguation(normalizedQuery)
        if (disambiguated != null) {
            return QueryRoutingDebugResult(
                result = disambiguated,
                topCandidates = listOf(
                    QueryRoutingCandidate(
                        targetCode = disambiguated.primaryTargetCode.orEmpty(),
                        routeType = disambiguated.routeType,
                        score = 100,
                        matchedAlias = "disambiguation",
                        matchKind = AliasMatchKind.EXACT,
                    ),
                ),
                wasAmbiguous = false,
            )
        }

        val scored = scoreCandidates(
            normalizedQuery = normalizedQuery,
            locale = locale,
        )

        if (scored.isEmpty()) {
            return QueryRoutingDebugResult(
                result = fallbackNoMatch(normalizedQuery),
                topCandidates = emptyList(),
                wasAmbiguous = false,
            )
        }

        val top1 = scored.first()
        val top2 = scored.getOrNull(1)
        val margin = if (top2 == null) Int.MAX_VALUE else top1.score - top2.score

        val result = when {
            top1.targetType == ApplAliasTargetType.CANONICAL && !top1.targetCode.startsWith(APPL_CANONICAL_PREFIX) -> {
                top1.toResult(normalizedQuery)
            }

            margin < rules.minMargin -> {
                fallbackLowConfidence(top1, normalizedQuery)
            }

            else -> top1.toResult(normalizedQuery)
        }

        val topCandidates = scored
            .take(topN.coerceAtLeast(1))
            .map {
                QueryRoutingCandidate(
                    targetCode = it.targetCode,
                    routeType = it.routeType,
                    score = it.score,
                    matchedAlias = it.matchedAlias,
                    matchKind = it.matchKind,
                )
            }

        return QueryRoutingDebugResult(
            result = result,
            topCandidates = topCandidates,
            wasAmbiguous = margin < rules.minMargin,
        )
    }

    private fun resolveDisambiguation(normalizedQuery: String): QueryRoutingResult? {
        val queryTokens = tokenize(normalizedQuery)
        val matched = rules.disambiguation.firstOrNull { rule ->
            rule.anyGroups.all { group ->
                group.any { token -> matchesRuleToken(queryTokens, normalizedQuery, token) }
            }
        } ?: return null

        return when (matched.routeType) {
            ApplRouteTargetType.CANONICAL -> QueryRoutingResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = matched.routeId,
                extractedTokens = queryTokens,
                confidence = 0.99,
            )

            ApplRouteTargetType.NODE -> QueryRoutingResult(
                routeType = QueryRouteType.OPEN_BROWSE,
                primaryTargetCode = matched.routeId,
                extractedTokens = queryTokens,
                confidence = 0.99,
            )
        }
    }

    private fun scoreCandidates(
        normalizedQuery: String,
        locale: String,
    ): List<ApplScoredCandidate> {
        val queryTokens = tokenize(normalizedQuery)
        val byTarget = LinkedHashMap<String, ApplScoredCandidate>()

        applAliases.forEach { alias ->
            val phraseMatched = containsPhrase(normalizedQuery, alias.normalizedQuery)
            val exactMatched = normalizedQuery == alias.normalizedQuery
            val exactTokenMatches = alias.tokens.count { aliasToken ->
                matchesRuleToken(queryTokens, normalizedQuery, aliasToken)
            }
            val tokenMatched = alias.tokens.isNotEmpty() && exactTokenMatches == alias.tokens.size
            val fuzzyMatched = alias.tokens.isNotEmpty() && alias.tokens.all { aliasToken ->
                matchesRuleToken(queryTokens, normalizedQuery, aliasToken) ||
                    fuzzyTokenMatch(aliasToken, queryTokens)
            }
            val negativeTokenMatched = alias.negativeTokens.any { negativeToken ->
                matchesRuleToken(queryTokens, normalizedQuery, negativeToken)
            }

            val semanticMatched = when (alias.matchKind) {
                AliasMatchKind.EXACT -> exactMatched
                AliasMatchKind.PREFIX -> phraseMatched
                AliasMatchKind.TOKEN -> tokenMatched
                AliasMatchKind.FUZZY -> fuzzyMatched
            }

            if (!semanticMatched || alias.isBlocked) {
                return@forEach
            }

            val specificityBonus = rules.tieBreakers
                .asSequence()
                .filter { it.nodeId == alias.targetId }
                .filter { matchesRuleToken(queryTokens, normalizedQuery, it.token) }
                .map { rules.specificityBonusMax }
                .firstOrNull()
                ?: 0

            val matchScore = when (alias.matchKind) {
                AliasMatchKind.EXACT ->
                    rules.phraseExact + (if (tokenMatched) alias.tokens.size * rules.tokenMatch else 0)
                AliasMatchKind.PREFIX ->
                    rules.phraseExact
                AliasMatchKind.TOKEN ->
                    (alias.tokens.size * rules.tokenMatch) + if (exactMatched) rules.phraseExact else 0
                AliasMatchKind.FUZZY ->
                    rules.editDistanceClose + if (exactMatched) rules.phraseExact else 0
            }
            val localePenalty = if (alias.locale.equals(locale, ignoreCase = true)) 0 else 2
            val negativeTokenPenalty = if (negativeTokenMatched) rules.negativeToken else 0

            val score = (
                alias.weight +
                    matchScore +
                    specificityBonus -
                    localePenalty +
                    negativeTokenPenalty
                ).coerceAtLeast(0)

            val routeType = when (alias.targetType) {
                ApplAliasTargetType.NODE -> QueryRouteType.OPEN_BROWSE
                ApplAliasTargetType.CANONICAL -> QueryRouteType.OPEN_CATEGORY
            }

            val candidate = ApplScoredCandidate(
                targetCode = alias.targetId,
                targetType = alias.targetType,
                routeType = routeType,
                score = score,
                matchedAlias = alias.query,
                matchKind = alias.matchKind,
            )

            val current = byTarget[alias.targetId]
            if (current == null || candidate.isBetterThan(current)) {
                byTarget[alias.targetId] = candidate
            }
        }

        return byTarget.values
            .sortedWith(
                compareByDescending<ApplScoredCandidate> { it.score }
                    .thenBy { it.matchKind.ordinal }
                    .thenBy { it.targetCode },
            )
    }

    private fun fallbackNoMatch(normalizedQuery: String): QueryRoutingResult = QueryRoutingResult(
        routeType = QueryRouteType.OPEN_CATEGORY,
        primaryTargetCode = APPL_CANONICAL_ROOT,
        extractedTokens = tokenize(normalizedQuery),
        confidence = 0.0,
    )

    private fun fallbackLowConfidence(
        top1: ApplScoredCandidate,
        normalizedQuery: String,
    ): QueryRoutingResult {
        val fallbackCanonical = top1.toCanonicalFallback(applNodeCanonical)
            ?.takeIf { it.startsWith(APPL_CANONICAL_PREFIX) }
            ?: APPL_CANONICAL_ROOT

        return QueryRoutingResult(
            routeType = QueryRouteType.OPEN_CATEGORY,
            primaryTargetCode = fallbackCanonical,
            extractedTokens = tokenize(normalizedQuery),
            confidence = scoreToConfidence(top1.score),
        )
    }

    private fun ApplScoredCandidate.toResult(normalizedQuery: String): QueryRoutingResult = QueryRoutingResult(
        routeType = routeType,
        primaryTargetCode = targetCode,
        extractedTokens = tokenize(normalizedQuery),
        confidence = scoreToConfidence(score),
    )

    private fun ApplScoredCandidate.toCanonicalFallback(nodeCanonical: Map<String, String>): String? = when (targetType) {
        ApplAliasTargetType.CANONICAL -> targetCode
        ApplAliasTargetType.NODE -> nodeCanonical[targetCode]
    }

    private fun ApplScoredCandidate.isBetterThan(other: ApplScoredCandidate): Boolean {
        if (score != other.score) return score > other.score
        if (matchKind != other.matchKind) return matchKind.ordinal < other.matchKind.ordinal
        return targetCode < other.targetCode
    }

    private fun scoreToConfidence(score: Int): Double = (score / 100.0).coerceIn(0.0, 1.0)

    private fun normalize(input: String): String = input
        .trim()
        .lowercase()
        .replace('ё', 'е')
        .replace("[-‐‑‒–—]+".toRegex(), " ")
        .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    private fun tokenize(text: String): List<String> =
        text.split(" ").map { it.trim() }.filter { it.isNotEmpty() }

    private fun containsPhrase(query: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        if (query == phrase) return true
        val escaped = Regex.escape(phrase)
        return Regex("(^|\\s)$escaped(\\s|$)").containsMatchIn(query)
    }

    private fun matchesRuleToken(
        queryTokens: List<String>,
        normalizedQuery: String,
        rawToken: String,
    ): Boolean {
        val token = normalize(rawToken)
        if (token.isBlank()) return false
        if (' ' in token) {
            return normalizedQuery.contains(token)
        }
        if (token.length < 3) {
            return queryTokens.any { it == token }
        }
        return queryTokens.any { it == token || it.startsWith(token) }
    }

    private fun fuzzyTokenMatch(aliasToken: String, queryTokens: List<String>): Boolean {
        if (aliasToken.length < 4) return false
        return queryTokens.any { token ->
            token.length >= 4 && levenshteinDistance(aliasToken, token) <= 1
        }
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val prev = IntArray(right.length + 1) { it }
        val curr = IntArray(right.length + 1)

        for (i in 1..left.length) {
            curr[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost,
                )
            }
            for (j in prev.indices) {
                prev[j] = curr[j]
            }
        }
        return prev[right.length]
    }

    private companion object {
        private const val APPL_CANONICAL_ROOT = "APPL"
        private const val APPL_CANONICAL_PREFIX = "APPL."
        private const val BROWSE_APPL_PREFIX = "B.APPL"
    }
}

private data class ApplAliasRuntimeEntry(
    val locale: String,
    val query: String,
    val normalizedQuery: String,
    val tokens: List<String>,
    val targetType: ApplAliasTargetType,
    val targetId: String,
    val weight: Int,
    val matchKind: AliasMatchKind,
    val negativeTokens: List<String>,
    val isBlocked: Boolean,
)

private data class ApplScoredCandidate(
    val targetCode: String,
    val targetType: ApplAliasTargetType,
    val routeType: QueryRouteType,
    val score: Int,
    val matchedAlias: String,
    val matchKind: AliasMatchKind,
)

private data class ApplRoutingRules(
    val phraseExact: Int,
    val tokenMatch: Int,
    val editDistanceClose: Int,
    val negativeToken: Int,
    val specificityBonusMin: Int,
    val specificityBonusMax: Int,
    val minMargin: Int,
    val disambiguation: List<ApplDisambiguationRule>,
    val tieBreakers: List<ApplTieBreakerRule>,
)

private data class ApplDisambiguationRule(
    val anyGroups: List<List<String>>,
    val routeType: ApplRouteTargetType,
    val routeId: String,
)

private enum class ApplRouteTargetType {
    NODE,
    CANONICAL,
}

private data class ApplTieBreakerRule(
    val token: String,
    val nodeId: String,
)

private object ApplRoutingRulesParser {
    private val keyValueRegex = Regex("""^\s*([A-Za-z0-9_.-]+):\s*(.+?)\s*$""")
    private val disambiguationItemRegex = Regex("""^\s{2}-\s*$""")
    private val disambiguationGroupRegex = Regex("""^\s{6}-\s*$""")
    private val disambiguationTokenRegex = Regex("""^\s{8}-\s*(.+?)\s*$""")
    private val disambiguationRouteTypeRegex = Regex("""^\s{6}type:\s*(NODE|CANONICAL)\s*$""")
    private val disambiguationRouteIdRegex = Regex("""^\s{6}id:\s*(.+?)\s*$""")
    private val tieBreakerItemRegex = Regex("""^\s{2}-\s*$""")
    private val tieBreakerTokenRegex = Regex("""^\s{6}token:\s*(.+?)\s*$""")
    private val tieBreakerNodeRegex = Regex("""^\s{6}node_id:\s*(.+?)\s*$""")

    fun parse(yaml: String): ApplRoutingRules {
        val lines = yaml
            .lineSequence()
            .map { it.trimEnd('\r') }
            .toList()

        val scoringLines = extractSection(
            lines = lines,
            sectionHeader = "scoring:",
            stopHeaders = setOf("disambiguation:", "tie_breakers:", "fallbacks:"),
        )
        val disambiguationLines = extractSection(
            lines = lines,
            sectionHeader = "disambiguation:",
            stopHeaders = setOf("tie_breakers:", "fallbacks:"),
        )
        val tieBreakersLines = extractSection(
            lines = lines,
            sectionHeader = "tie_breakers:",
            stopHeaders = setOf("fallbacks:"),
        )

        val scoring = parseKeyValueMap(scoringLines)

        return ApplRoutingRules(
            phraseExact = scoring["phrase_exact"]?.toIntOrNull() ?: 6,
            tokenMatch = scoring["token_match"]?.toIntOrNull() ?: 2,
            editDistanceClose = scoring["edit_distance_close"]?.toIntOrNull() ?: 1,
            negativeToken = scoring["negative_token"]?.toIntOrNull() ?: -8,
            specificityBonusMin = scoring["specificity_bonus_min"]?.toIntOrNull() ?: 1,
            specificityBonusMax = scoring["specificity_bonus_max"]?.toIntOrNull() ?: 3,
            minMargin = scoring["min_margin"]?.toIntOrNull() ?: 2,
            disambiguation = parseDisambiguation(disambiguationLines),
            tieBreakers = parseTieBreakers(tieBreakersLines),
        )
    }

    private fun parseDisambiguation(lines: List<String>): List<ApplDisambiguationRule> {
        if (lines.isEmpty()) return emptyList()

        val out = mutableListOf<ApplDisambiguationRule>()
        val itemStarts = lines
            .mapIndexedNotNull { index, line -> index.takeIf { disambiguationItemRegex.matches(line) } }

        itemStarts.forEachIndexed { itemIndex, startIndex ->
            val endIndex = itemStarts.getOrNull(itemIndex + 1) ?: lines.size
            val block = lines.subList(startIndex + 1, endIndex)

            val groups = mutableListOf<MutableList<String>>()
            var currentGroup: MutableList<String>? = null
            var routeType: ApplRouteTargetType? = null
            var routeId: String? = null

            block.forEach { line ->
                if (disambiguationGroupRegex.matches(line)) {
                    currentGroup = mutableListOf()
                    groups += currentGroup!!
                    return@forEach
                }

                disambiguationTokenRegex.matchEntire(line)?.let { match ->
                    val token = unwrapYamlScalar(match.groupValues[1])
                    if (token.isNotBlank()) {
                        if (currentGroup == null) {
                            currentGroup = mutableListOf()
                            groups += currentGroup!!
                        }
                        currentGroup?.add(token)
                    }
                    return@forEach
                }

                disambiguationRouteTypeRegex.matchEntire(line)?.let { match ->
                    routeType = ApplRouteTargetType.valueOf(match.groupValues[1])
                    return@forEach
                }

                disambiguationRouteIdRegex.matchEntire(line)?.let { match ->
                    routeId = unwrapYamlScalar(match.groupValues[1])
                }
            }

            if (groups.isNotEmpty() && routeType != null && !routeId.isNullOrBlank()) {
                val nonNullRouteType = routeType ?: ApplRouteTargetType.CANONICAL
                val resolvedRouteId = when (nonNullRouteType) {
                    ApplRouteTargetType.NODE -> normalizeBrowseCode(routeId.orEmpty())
                    ApplRouteTargetType.CANONICAL -> routeId.orEmpty()
                }
                out += ApplDisambiguationRule(
                    anyGroups = groups.map { it.toList() },
                    routeType = nonNullRouteType,
                    routeId = resolvedRouteId,
                )
            }
        }

        return out
    }

    private fun parseTieBreakers(lines: List<String>): List<ApplTieBreakerRule> {
        if (lines.isEmpty()) return emptyList()

        val out = mutableListOf<ApplTieBreakerRule>()
        val itemStarts = lines
            .mapIndexedNotNull { index, line -> index.takeIf { tieBreakerItemRegex.matches(line) } }

        itemStarts.forEachIndexed { itemIndex, startIndex ->
            val endIndex = itemStarts.getOrNull(itemIndex + 1) ?: lines.size
            val block = lines.subList(startIndex + 1, endIndex)

            var token: String? = null
            var nodeId: String? = null
            block.forEach { line ->
                tieBreakerTokenRegex.matchEntire(line)?.let { token = unwrapYamlScalar(it.groupValues[1]) }
                tieBreakerNodeRegex.matchEntire(line)?.let { nodeId = normalizeBrowseCode(unwrapYamlScalar(it.groupValues[1])) }
            }

            if (!token.isNullOrBlank() && !nodeId.isNullOrBlank()) {
                out += ApplTieBreakerRule(token = token.orEmpty(), nodeId = nodeId.orEmpty())
            }
        }

        return out
    }

    private fun parseKeyValueMap(lines: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        lines.forEach { line ->
            val match = keyValueRegex.matchEntire(line) ?: return@forEach
            out[match.groupValues[1]] = match.groupValues[2]
        }
        return out
    }

    private fun extractSection(
        lines: List<String>,
        sectionHeader: String,
        stopHeaders: Set<String>,
    ): List<String> {
        val start = lines.indexOfFirst { it.trim() == sectionHeader }
        if (start < 0) return emptyList()

        val out = mutableListOf<String>()
        for (i in start + 1 until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed in stopHeaders) break
            out += lines[i]
        }
        return out
    }

    private fun unwrapYamlScalar(raw: String): String {
        val trimmed = raw.trim()
        return trimmed.removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'")
    }

    private fun normalizeBrowseCode(rawNodeId: String): String {
        val normalized = rawNodeId.trim()
        val lower = normalized.lowercase()
        if (!lower.startsWith("bn_appl_")) return normalized
        val suffix = lower.removePrefix("bn_appl_")
        if (suffix == "root") return "B.APPL"

        val segments = suffix
            .split('_')
            .filter { it.isNotBlank() }
            .map { it.uppercase() }
        return (listOf("B", "APPL") + segments).joinToString(".")
    }
}
