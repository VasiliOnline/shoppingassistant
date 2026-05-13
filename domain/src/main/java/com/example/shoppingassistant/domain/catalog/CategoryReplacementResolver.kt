package com.example.shoppingassistant.domain.catalog

import java.util.Locale

data class CategoryRedirectResolution(
    val requestedCode: String,
    val resolvedCode: String,
    val redirectChain: List<String>,
    val wasRedirected: Boolean,
    val cycleDetected: Boolean,
    val unresolvedTarget: String? = null,
)

object CategoryReplacementResolver {

    fun resolve(
        requestedCode: String,
        categories: List<Category>,
        maxHops: Int = 32,
    ): CategoryRedirectResolution? = resolve(
        requestedCode = requestedCode,
        categoriesByCode = categories.associateBy { category -> normalizeCode(category.code) },
        maxHops = maxHops,
    )

    fun resolve(
        requestedCode: String,
        categoriesByCode: Map<String, Category>,
        maxHops: Int = 32,
    ): CategoryRedirectResolution? {
        val normalizedRequestedCode = normalizeCode(requestedCode)
        if (normalizedRequestedCode.isEmpty()) return null

        val normalizedByCode = categoriesByCode.entries.associate { (rawCode, category) ->
            normalizeCode(rawCode) to category
        }
        val requested = normalizedByCode[normalizedRequestedCode] ?: return null

        var currentCode = normalizeCode(requested.code)
        val chain = mutableListOf(currentCode)
        val seen = linkedSetOf(currentCode)
        var unresolvedTarget: String? = null
        var cycleDetected = false
        var hops = 0

        while (hops < maxHops) {
            hops += 1
            val current = normalizedByCode[currentCode] ?: break
            val replacementCode = current.replacementCode?.let(::normalizeCode).orEmpty()
            if (replacementCode.isEmpty()) break
            if (!seen.add(replacementCode)) {
                cycleDetected = true
                chain += replacementCode
                break
            }
            val replacementCategory = normalizedByCode[replacementCode]
            if (replacementCategory == null) {
                unresolvedTarget = replacementCode
                chain += replacementCode
                break
            }
            currentCode = normalizeCode(replacementCategory.code)
            chain += currentCode
        }

        if (hops >= maxHops) {
            cycleDetected = true
        }

        return CategoryRedirectResolution(
            requestedCode = normalizedRequestedCode,
            resolvedCode = currentCode,
            redirectChain = chain.toList(),
            wasRedirected = currentCode != normalizedRequestedCode,
            cycleDetected = cycleDetected,
            unresolvedTarget = unresolvedTarget,
        )
    }

    private fun normalizeCode(code: String?): String =
        code?.trim()?.uppercase(Locale.ROOT).orEmpty()
}

