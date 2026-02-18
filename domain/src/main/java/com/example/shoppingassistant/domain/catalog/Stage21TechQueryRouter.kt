package com.example.shoppingassistant.domain.catalog

import kotlin.math.roundToInt

class Stage21TechQueryRouter(
    aliases: List<AliasEntry> = Stage21TechPackageLoader.aliasEntries,
    browseNodes: List<BrowseNode> = Stage21TechPackageLoader.browseNodes,
    routingRulesYaml: String = Stage21TechPackageLoader.routingRulesYaml,
) : QueryRouter {

    private val rules: RoutingRules = RoutingRulesParser.parse(routingRulesYaml)
    internal val debugTriggerCount: Int = rules.triggers.values.sumOf { it.size }
    internal val debugConflictCount: Int = rules.conflicts.size
    private val techBrowseByCode: Map<String, BrowseNode> = browseNodes
        .asSequence()
        .filter { it.browseCode.startsWith(BROWSE_TECH_PREFIX) }
        .associateBy { it.browseCode }
    private val techLeafCodes: Set<String> = run {
        val techCodes = CatalogSeed.categories
            .asSequence()
            .map { it.code }
            .filter { it.startsWith(TECH_CATEGORY_PREFIX) }
            .toList()
        val parents = CatalogSeed.categories
            .asSequence()
            .mapNotNull { it.parentCode?.takeIf(String::isNotBlank) }
            .toSet()
        techCodes.filterNot { it in parents }.toSet()
    }
    private val techAliases: List<AliasRuntimeEntry> = aliases
        .asSequence()
        .filterNot { it.isBlocked }
        .filter {
            val target = it.targetCode.trim()
            target.startsWith(TECH_CATEGORY_PREFIX) || target.startsWith(BROWSE_TECH_PREFIX)
        }
        .map { entry ->
            AliasRuntimeEntry(
                locale = entry.locale.trim(),
                term = entry.term.trim(),
                normalized = normalize(entry.normalizedTerm),
                targetCode = entry.targetCode.trim(),
                weight = entry.weight.coerceIn(0, 100),
            )
        }
        .filter { it.normalized.isNotBlank() && it.targetCode.isNotBlank() }
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
                result = fallbackResult(normalizedQuery, confidence = 0.0),
                topCandidates = emptyList(),
                wasAmbiguous = false,
            )
        }

        val scored = scoreCandidates(
            normalizedQuery = normalizedQuery,
            locale = locale,
        )
        if (scored.isEmpty()) {
            return QueryRoutingDebugResult(
                result = fallbackResult(normalizedQuery, confidence = 0.0),
                topCandidates = emptyList(),
                wasAmbiguous = false,
            )
        }

        val top1 = scored.first()
        val top2 = scored.getOrNull(1)
        val gap = if (top2 == null) Int.MAX_VALUE else top1.score - top2.score
        val hasMeaningfulAmbiguity = top2 != null &&
            gap < rules.top1Top2Gap &&
            !areEquivalentIntents(top1, top2)

        val result = when {
            top1.score >= rules.acceptThreshold && !hasMeaningfulAmbiguity -> {
                top1.toResult(normalizedQuery)
            }

            top1.score >= rules.acceptThreshold && rules.ambiguousToGroup -> {
                val groupTarget = resolveAmbiguousGroupTarget(top1)
                if (groupTarget != null) {
                    QueryRoutingResult(
                        routeType = QueryRouteType.OPEN_BROWSE,
                        primaryTargetCode = groupTarget,
                        extractedTokens = tokenize(normalizedQuery),
                        confidence = scoreToConfidence(top1.score),
                    )
                } else {
                    fallbackResult(normalizedQuery, confidence = scoreToConfidence(top1.score))
                }
            }

            else -> fallbackResult(normalizedQuery, confidence = scoreToConfidence(top1.score))
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
            wasAmbiguous = hasMeaningfulAmbiguity,
        )
    }

    private fun areEquivalentIntents(
        first: ScoredCandidate,
        second: ScoredCandidate,
    ): Boolean {
        if (first.routeType == second.routeType && first.targetCode == second.targetCode) {
            return true
        }

        val firstGroup = toGroupCode(first)
        val secondGroup = toGroupCode(second)
        return firstGroup != null && secondGroup != null && firstGroup == secondGroup
    }

    private fun toGroupCode(candidate: ScoredCandidate): String? {
        return when (candidate.routeType) {
            QueryRouteType.OPEN_CATEGORY -> rules.groupMap[candidate.targetCode]
            QueryRouteType.OPEN_BROWSE -> candidate.targetCode.takeIf { code ->
                techBrowseByCode[code]?.nodeKind == BrowseNodeKind.GROUP || code == rules.defaultRoot
            } ?: techBrowseByCode[candidate.targetCode]?.targetCategoryCode?.let { rules.groupMap[it] }

            QueryRouteType.RUN_SEARCH -> null
        }
    }

    private fun resolveAmbiguousGroupTarget(candidate: ScoredCandidate): String? {
        return when (candidate.routeType) {
            QueryRouteType.OPEN_CATEGORY -> rules.groupMap[candidate.targetCode]
            QueryRouteType.OPEN_BROWSE -> {
                val leafFromBrowse = techBrowseByCode[candidate.targetCode]?.targetCategoryCode
                if (leafFromBrowse != null) {
                    rules.groupMap[leafFromBrowse] ?: candidate.targetCode
                } else {
                    candidate.targetCode
                }
            }

            QueryRouteType.RUN_SEARCH -> null
        }
    }

    private fun scoreCandidates(
        normalizedQuery: String,
        locale: String,
    ): List<ScoredCandidate> {
        val byTarget = LinkedHashMap<String, ScoredCandidate>()

        techAliases.forEach { alias ->
            val matchKind = detectMatchKind(
                query = normalizedQuery,
                alias = alias.normalized,
            ) ?: return@forEach

            val routeType = resolveRouteType(alias.targetCode) ?: return@forEach
            val leafCode = resolveLeafCode(alias.targetCode)
            val score = computeScore(
                alias = alias,
                matchKind = matchKind,
                normalizedQuery = normalizedQuery,
                leafCode = leafCode,
                locale = locale,
            )
            val candidate = ScoredCandidate(
                targetCode = alias.targetCode,
                routeType = routeType,
                score = score,
                matchedAlias = alias.term,
                matchKind = matchKind,
            )

            val current = byTarget[alias.targetCode]
            if (current == null || candidate.isBetterThan(current)) {
                byTarget[alias.targetCode] = candidate
            }
        }

        return byTarget.values
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.score }
                    .thenBy { it.matchKind.ordinal }
                    .thenBy { it.targetCode },
            )
    }

    private fun computeScore(
        alias: AliasRuntimeEntry,
        matchKind: AliasMatchKind,
        normalizedQuery: String,
        leafCode: String?,
        locale: String,
    ): Int {
        val baseScore = (alias.weight * rules.aliasPriorityWeight).roundToInt()
        val matchPenalty = when (matchKind) {
            AliasMatchKind.EXACT -> 0
            AliasMatchKind.PREFIX -> 2
            AliasMatchKind.TOKEN -> 5
            AliasMatchKind.FUZZY -> 10
        }
        val localePenalty = if (alias.locale.equals(locale, ignoreCase = true)) 0 else 2
        val triggerBonus = if (leafCode == null) {
            0
        } else {
            rules.triggers[leafCode].orEmpty()
                .asSequence()
                .filter { it.pattern.containsMatchIn(normalizedQuery) }
                .sumOf { it.add }
                .coerceAtMost(rules.triggerBonusMax)
        }
        val conflictPenalty = if (leafCode == null) {
            0
        } else {
            rules.conflicts
                .asSequence()
                .filter { it.pattern.containsMatchIn(normalizedQuery) }
                .sumOf { conflict -> if (leafCode == conflict.prefer) 0 else conflict.penaltyOther }
                .coerceAtMost(rules.conflictPenaltyMax)
        }

        return (baseScore + triggerBonus - conflictPenalty - matchPenalty - localePenalty).coerceAtLeast(0)
    }

    private fun resolveRouteType(targetCode: String): QueryRouteType? {
        return when {
            targetCode in techLeafCodes -> QueryRouteType.OPEN_CATEGORY
            targetCode in techBrowseByCode -> QueryRouteType.OPEN_BROWSE
            else -> null
        }
    }

    private fun resolveLeafCode(targetCode: String): String? {
        return when {
            targetCode in techLeafCodes -> targetCode
            targetCode in techBrowseByCode -> techBrowseByCode[targetCode]?.targetCategoryCode?.takeIf { it in techLeafCodes }
            else -> null
        }
    }

    private fun detectMatchKind(
        query: String,
        alias: String,
    ): AliasMatchKind? {
        if (query == alias) return AliasMatchKind.EXACT
        if (query.startsWith(alias) && query.length > alias.length) return AliasMatchKind.PREFIX
        if (tokenContains(query, alias)) return AliasMatchKind.TOKEN
        if (rules.fuzzyEnabled && fuzzyContains(query, alias)) return AliasMatchKind.FUZZY
        return null
    }

    private fun tokenContains(query: String, alias: String): Boolean {
        val queryTokens = tokenize(query).toSet()
        val aliasTokens = tokenize(alias)
        if (aliasTokens.isEmpty()) return false
        return aliasTokens.all { it in queryTokens }
    }

    private fun fuzzyContains(query: String, alias: String): Boolean {
        val queryTokens = tokenize(query)
        val aliasTokens = tokenize(alias)
        if (queryTokens.isEmpty() || aliasTokens.isEmpty()) return false

        var usedFuzzy = false
        for (aliasToken in aliasTokens) {
            if (aliasToken in queryTokens) continue
            if (aliasToken.length < rules.fuzzyMinTokenLen) return false

            val matchedFuzzy = queryTokens.any { queryToken ->
                queryToken.length >= rules.fuzzyMinTokenLen &&
                    levenshteinDistance(aliasToken, queryToken) <= rules.fuzzyMaxLevenshtein
            }
            if (!matchedFuzzy) return false
            usedFuzzy = true
        }
        return usedFuzzy
    }

    private fun fallbackResult(normalizedQuery: String, confidence: Double): QueryRoutingResult {
        val fallbackTarget = rules.defaultRoot.takeIf { it in techBrowseByCode }
        return if (fallbackTarget != null) {
            QueryRoutingResult(
                routeType = QueryRouteType.OPEN_BROWSE,
                primaryTargetCode = fallbackTarget,
                extractedTokens = tokenize(normalizedQuery),
                confidence = confidence.coerceIn(0.0, 1.0),
            )
        } else {
            QueryRoutingResult(
                routeType = QueryRouteType.RUN_SEARCH,
                primaryTargetCode = null,
                extractedTokens = tokenize(normalizedQuery),
                confidence = confidence.coerceIn(0.0, 1.0),
            )
        }
    }

    private fun ScoredCandidate.toResult(normalizedQuery: String): QueryRoutingResult = QueryRoutingResult(
        routeType = routeType,
        primaryTargetCode = targetCode,
        extractedTokens = tokenize(normalizedQuery),
        confidence = scoreToConfidence(score),
    )

    private fun ScoredCandidate.isBetterThan(other: ScoredCandidate): Boolean {
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
        private const val TECH_CATEGORY_PREFIX = "TECH."
        private const val BROWSE_TECH_PREFIX = "B.TECH"
    }
}

data class QueryRoutingCandidate(
    val targetCode: String,
    val routeType: QueryRouteType,
    val score: Int,
    val matchedAlias: String,
    val matchKind: AliasMatchKind,
)

data class QueryRoutingDebugResult(
    val result: QueryRoutingResult,
    val topCandidates: List<QueryRoutingCandidate>,
    val wasAmbiguous: Boolean,
)

private data class AliasRuntimeEntry(
    val locale: String,
    val term: String,
    val normalized: String,
    val targetCode: String,
    val weight: Int,
)

private data class ScoredCandidate(
    val targetCode: String,
    val routeType: QueryRouteType,
    val score: Int,
    val matchedAlias: String,
    val matchKind: AliasMatchKind,
)

private data class RoutingRules(
    val acceptThreshold: Int,
    val top1Top2Gap: Int,
    val aliasPriorityWeight: Double,
    val triggerBonusMax: Int,
    val conflictPenaltyMax: Int,
    val fuzzyEnabled: Boolean,
    val fuzzyMaxLevenshtein: Int,
    val fuzzyMinTokenLen: Int,
    val triggers: Map<String, List<TriggerRule>>,
    val conflicts: List<ConflictRule>,
    val ambiguousToGroup: Boolean,
    val defaultRoot: String,
    val groupMap: Map<String, String>,
)

private data class TriggerRule(
    val pattern: Regex,
    val add: Int,
)

private data class ConflictRule(
    val pattern: Regex,
    val prefer: String,
    val penaltyOther: Int,
)

private object RoutingRulesParser {
    private val keyValueRegex = Regex("""^\s*([A-Za-z0-9_.-]+):\s*(.+?)\s*$""")
    private val triggerLeafRegex = Regex("""^\s{2}([A-Z0-9_.]+):\s*$""")
    private val triggerPatternRegex = Regex("""^\s{4}-\s*pattern:\s*(.+?)\s*$""")
    private val triggerAddRegex = Regex("""^\s{6}add:\s*(\d+)\s*$""")
    private val conflictPatternRegex = Regex("""^\s*-\s*pattern:\s*(.+?)\s*$""")
    private val conflictPreferRegex = Regex("""^\s{2}prefer:\s*([A-Z0-9_.]+)\s*$""")
    private val conflictPenaltyRegex = Regex("""^\s{2}penalty_other:\s*(\d+)\s*$""")
    private val groupMapRegex = Regex("""^\s{4}([A-Z0-9_.]+):\s*([A-Z0-9_.]+)\s*$""")

    fun parse(yaml: String): RoutingRules {
        val lines = yaml
            .lineSequence()
            .map { it.trimEnd('\r') }
            .toList()

        val scoringLines = extractSection(
            lines = lines,
            sectionHeader = "scoring:",
            stopHeaders = setOf("priority_buckets:", "triggers:", "conflicts:", "fallbacks:"),
        )
        val triggersLines = extractSection(
            lines = lines,
            sectionHeader = "triggers:",
            stopHeaders = setOf("conflicts:", "fallbacks:"),
        )
        val conflictsLines = extractSection(
            lines = lines,
            sectionHeader = "conflicts:",
            stopHeaders = setOf("fallbacks:"),
        )
        val fallbacksLines = extractSection(
            lines = lines,
            sectionHeader = "fallbacks:",
            stopHeaders = emptySet(),
        )

        val scoringMap = parseKeyValueMap(scoringLines)
        val fuzzyLines = extractSubSection(scoringLines, "fuzzy:")
        val fuzzyMap = parseKeyValueMap(fuzzyLines)

        val triggers = parseTriggers(triggersLines)
        val conflicts = parseConflicts(conflictsLines)
        val fallbacksMap = parseKeyValueMap(fallbacksLines)
        val groupMap = parseGroupMap(fallbacksLines)

        return RoutingRules(
            acceptThreshold = scoringMap["accept_threshold"]?.toIntOrNull() ?: 70,
            top1Top2Gap = scoringMap["top1_top2_gap"]?.toIntOrNull() ?: 10,
            aliasPriorityWeight = scoringMap["alias_priority_weight"]?.toDoubleOrNull() ?: 1.0,
            triggerBonusMax = scoringMap["trigger_bonus_max"]?.toIntOrNull() ?: 25,
            conflictPenaltyMax = scoringMap["conflict_penalty_max"]?.toIntOrNull() ?: 25,
            fuzzyEnabled = fuzzyMap["enabled"]?.toBooleanStrictOrNullCompat() ?: true,
            fuzzyMaxLevenshtein = fuzzyMap["max_levenshtein"]?.toIntOrNull() ?: 1,
            fuzzyMinTokenLen = fuzzyMap["min_token_len"]?.toIntOrNull() ?: 5,
            triggers = triggers,
            conflicts = conflicts,
            ambiguousToGroup = fallbacksMap["ambiguous_to_group"]?.toBooleanStrictOrNullCompat() ?: true,
            defaultRoot = fallbacksMap["default_root"] ?: "B.TECH",
            groupMap = groupMap,
        )
    }

    private fun parseTriggers(lines: List<String>): Map<String, List<TriggerRule>> {
        if (lines.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, MutableList<TriggerRule>>()
        var currentLeaf: String? = null

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val leafMatch = triggerLeafRegex.matchEntire(line)
            if (leafMatch != null) {
                currentLeaf = leafMatch.groupValues[1]
                out.getOrPut(currentLeaf) { mutableListOf() }
                i++
                continue
            }

            val patternMatch = triggerPatternRegex.matchEntire(line)
            if (patternMatch != null && currentLeaf != null) {
                val rawPattern = patternMatch.groupValues[1].trim()
                val add = triggerAddRegex.matchEntire(lines.getOrElse(i + 1) { "" })
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                out.getOrPut(currentLeaf) { mutableListOf() }.add(
                    TriggerRule(
                        pattern = compilePattern(rawPattern),
                        add = add,
                    ),
                )
            }
            i++
        }

        return out
    }

    private fun parseConflicts(lines: List<String>): List<ConflictRule> {
        if (lines.isEmpty()) return emptyList()
        val conflicts = mutableListOf<ConflictRule>()
        var i = 0
        while (i < lines.size) {
            val patternMatch = conflictPatternRegex.matchEntire(lines[i])
            if (patternMatch == null) {
                i++
                continue
            }

            val rawPattern = patternMatch.groupValues[1].trim()
            var prefer = ""
            var penalty = 0
            var j = i + 1
            while (j < lines.size && conflictPatternRegex.matchEntire(lines[j]) == null) {
                conflictPreferRegex.matchEntire(lines[j])?.let { prefer = it.groupValues[1].trim() }
                conflictPenaltyRegex.matchEntire(lines[j])?.let { penalty = it.groupValues[1].toIntOrNull() ?: 0 }
                j++
            }

            if (prefer.isNotBlank()) {
                conflicts += ConflictRule(
                    pattern = compilePattern(rawPattern),
                    prefer = prefer,
                    penaltyOther = penalty,
                )
            }
            i = j
        }
        return conflicts
    }

    private fun parseGroupMap(lines: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        lines.forEach { line ->
            groupMapRegex.matchEntire(line)?.let { match ->
                out[match.groupValues[1]] = match.groupValues[2]
            }
        }
        return out
    }

    private fun parseKeyValueMap(lines: List<String>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        lines.forEach { line ->
            val match = keyValueRegex.matchEntire(line) ?: return@forEach
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            map[key] = value
        }
        return map
    }

    private fun extractSubSection(
        lines: List<String>,
        sectionHeader: String,
    ): List<String> {
        val start = lines.indexOfFirst { it.trim() == sectionHeader }
        if (start < 0) return emptyList()
        val out = mutableListOf<String>()
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.startsWith("  ") && !line.startsWith("    ")) break
            out += line
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

    private fun String.toBooleanStrictOrNullCompat(): Boolean? = when (trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }

    private fun compilePattern(raw: String): Regex {
        // (?U) makes \b and \w Unicode-aware for Cyrillic tokens in rules.
        val source = "(?U)$raw"
        return Regex(source, RegexOption.IGNORE_CASE)
    }
}
