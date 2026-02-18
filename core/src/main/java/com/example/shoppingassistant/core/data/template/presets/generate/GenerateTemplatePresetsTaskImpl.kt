package com.example.shoppingassistant.core.data.template.presets.generate

import com.example.shoppingassistant.core.data.db.AttributeValueStat
import com.example.shoppingassistant.core.data.db.ProductDao
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintCheckResult
import com.example.shoppingassistant.domain.template.TemplateAnchorType
import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.TemplateSnapshotAttr
import com.example.shoppingassistant.domain.template.TemplateSnapshotData
import com.example.shoppingassistant.domain.template.TemplateSnapshotMode
import com.example.shoppingassistant.domain.template.presets.TemplatePreset
import com.example.shoppingassistant.domain.template.presets.TemplatePresetSource
import com.example.shoppingassistant.domain.template.presets.TemplatePresetsRepository
import com.example.shoppingassistant.domain.template.presets.generate.GenerateTemplatePresetsRequest
import com.example.shoppingassistant.domain.template.presets.generate.GenerateTemplatePresetsTask
import com.example.shoppingassistant.domain.template.presets.generate.PresetAnchorSource

class GenerateTemplatePresetsTaskImpl(
    private val anchorSource: PresetAnchorSource,
    private val dao: ProductDao,
    private val catalogRepository: CatalogRepository,
    private val constraintsResolver: CatalogConstraintsResolver,
    private val idTask: TemplateIdTask,
    private val presetsRepository: TemplatePresetsRepository,
) : GenerateTemplatePresetsTask {

    override suspend fun generate(request: GenerateTemplatePresetsRequest): List<TemplatePreset> {
        val categoryCode = request.categoryCode.trim()
        if (categoryCode.isBlank()) return emptyList()

        val profile = catalogRepository.getCategoryProfile(categoryCode) ?: return emptyList()
        val anchors = anchorSource.listTopAnchors(request.limit)
            .filter { it.samples >= request.minSamples }

        val presets = anchors.mapNotNull { anchor ->
            val constraints = catalogRepository.listConstraints(categoryCode, anchor.brand, anchor.model)
            val attrs = buildAttributes(
                profile = profile,
                constraints = constraints,
                brand = anchor.brand,
                model = anchor.model,
                maxAttributes = request.maxAttributes,
            )
            if (attrs.isEmpty()) return@mapNotNull null

            val snapshot = TemplateSnapshotData(
                anchorType = TemplateAnchorType.PRODUCT,
                anchorId = "${anchor.brand} ${anchor.model}",
                categoryCode = categoryCode,
                attrs = attrs.map { (k, v) -> TemplateSnapshotAttr(key = k, value = v) }
                    .sortedBy { it.key.lowercase() },
                mode = TemplateSnapshotMode.SEARCH,
            )
            TemplatePreset(
                presetId = idTask.computeId(snapshot),
                source = TemplatePresetSource.GENERATED,
                snapshot = snapshot,
                title = "${anchor.brand} ${anchor.model}",
                rank = anchor.samples,
            )
        }

        if (presets.isNotEmpty()) {
            presetsRepository.upsertGenerated(presets)
        }
        return presets
    }

    private suspend fun buildAttributes(
        profile: CategoryProfile,
        constraints: List<CatalogConstraints>,
        brand: String,
        model: String,
        maxAttributes: Int,
    ): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        attrs["brand"] = brand
        attrs["model"] = model

        val profileKeys = profile.attributes.map { it.code }.toSet()
        val statsByKey = dao.attributeValueStats(brand, model)
            .mapNotNull { stat ->
                val mapped = mapDbKeyToProfileKey(stat.key, profileKeys) ?: return@mapNotNull null
                mapped to stat
            }
            .groupBy({ it.first }, { it.second })

        val orderedKeys = profile.categoryAttributes
            .sortedBy { it.uiOrder }
            .map { it.attributeCode }
            .filterNot { it == "brand" || it == "model" }

        val dictByAttr = profile.valueDictionaries.associateBy { it.attributeCode }
        var picked = 0
        for (key in orderedKeys) {
            if (picked >= maxAttributes) break
            val stats = statsByKey[key].orEmpty()
            if (stats.isEmpty()) continue

            val constraintResult = constraintsResolver.evaluate(constraints, attrs)
            val value = pickValue(
                stats = stats,
                constraintResult = constraintResult,
                attributeCode = key,
                dictEntries = dictByAttr[key]?.entries.orEmpty(),
            ) ?: continue

            attrs[key] = value
            picked++
        }

        val violations = constraintsResolver.evaluate(constraints, attrs).violations
        if (violations.isNotEmpty()) {
            val cleaned = attrs.toMutableMap()
            violations.keys.forEach { key ->
                if (key != "brand" && key != "model") cleaned.remove(key)
            }
            val finalViolations = constraintsResolver.evaluate(constraints, cleaned).violations
            if (finalViolations.isNotEmpty()) return emptyMap()
            return cleaned
        }

        return attrs
    }

    private fun pickValue(
        stats: List<AttributeValueStat>,
        constraintResult: ConstraintCheckResult,
        attributeCode: String,
        dictEntries: List<com.example.shoppingassistant.domain.catalog.AttributeValueDictEntry>,
    ): String? {
        val allowed = constraintResult.allowedValuesByAttribute[attributeCode].orEmpty()
        val forbidden = constraintResult.forbiddenValuesByAttribute[attributeCode].orEmpty()

        val ordered = stats.sortedWith(
            compareByDescending<AttributeValueStat> { it.count }
                .thenBy { it.value.lowercase() }
        )
        for (stat in ordered) {
            val raw = stat.value.trim()
            if (raw.isBlank()) continue
            val canonical = resolveCanonical(raw, dictEntries) ?: if (dictEntries.isEmpty()) raw else null
            if (canonical == null) continue
            if (allowed.isNotEmpty() && allowed.none { it.equals(canonical, ignoreCase = true) }) continue
            if (forbidden.any { it.equals(canonical, ignoreCase = true) }) continue
            return canonical
        }
        return null
    }

    private fun resolveCanonical(
        raw: String,
        dictEntries: List<com.example.shoppingassistant.domain.catalog.AttributeValueDictEntry>,
    ): String? {
        if (dictEntries.isEmpty()) return null
        val trimmed = raw.trim()
        dictEntries.firstOrNull { it.canonicalValue.equals(trimmed, ignoreCase = true) }
            ?.let { return it.canonicalValue }
        return dictEntries.firstOrNull { entry ->
            entry.synonyms.any { syn -> syn.equals(trimmed, ignoreCase = true) }
        }?.canonicalValue
    }

    private fun mapDbKeyToProfileKey(dbKey: String, profileKeys: Set<String>): String? {
        val canonical = canonicalKey(dbKey)
        if (profileKeys.contains(canonical)) return canonical
        if (profileKeys.contains(dbKey)) return dbKey
        keyAliases(dbKey).forEach { alias ->
            if (profileKeys.contains(alias)) return alias
        }
        return null
    }

    private fun canonicalKey(key: String): String = when (key.lowercase()) {
        "memory_gb" -> "memory"
        "ram_gb" -> "ram"
        else -> key
    }

    private fun keyAliases(key: String): List<String> = when (key.lowercase()) {
        "storage" -> listOf("memory", "memory_gb")
        "memory" -> listOf("storage", "memory_gb")
        "memory_gb" -> listOf("memory", "storage")
        "ram_gb" -> listOf("ram")
        "ram" -> listOf("ram_gb")
        "state" -> listOf("condition")
        "condition" -> listOf("state")
        else -> emptyList()
    }
}
