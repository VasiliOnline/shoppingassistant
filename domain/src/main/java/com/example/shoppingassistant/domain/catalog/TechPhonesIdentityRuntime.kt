package com.example.shoppingassistant.domain.catalog

import java.util.Locale

private const val TECH_PHONES_CATEGORY = "TECH.PHONES"

data class TechPhoneModelHead(
    val modelCode: String,
    val brand: String,
    val family: String,
    val line: String,
    val model: String,
    val phoneType: String,
    val releaseYear: Int?,
    val storageValuesGb: List<String> = emptyList(),
    val ramValuesGb: List<String> = emptyList(),
    val sourcePolicy: String? = null,
)

data class TechPhoneModelAlias(
    val alias: String,
    val brand: String,
    val family: String,
    val line: String,
    val model: String,
    val modelCode: String,
    val priority: Int,
    val sourcePolicy: String? = null,
)

data class TechPhonesDedupPolicy(
    val policyId: String?,
    val masterProductKeyPriority: List<String>,
    val offerDedupKey: List<String>,
    val variantAxes: List<String>,
    val notSameMasterWhen: List<String>,
    val sameMasterButDifferentOfferWhen: List<String>,
)

data class TechPhonesIdentityResolutionPolicy(
    val policyId: String?,
    val resolutionPriority: List<String>,
    val ambiguousShortAliases: Set<String>,
    val rawPolicyIds: List<String>,
)

data class TechPhonesIdentityRuntime(
    val models: List<TechPhoneModelHead>,
    val aliases: List<TechPhoneModelAlias>,
    val dedupPolicy: TechPhonesDedupPolicy,
    val identityPolicy: TechPhonesIdentityResolutionPolicy,
)

enum class ProductIdentityStatus {
    RESOLVED,
    CANDIDATE,
    UNRESOLVED,
}

data class ProductIdentity(
    val categoryCode: String,
    val brandCanonical: String?,
    val modelCanonical: String?,
    val modelCode: String?,
    val familyCode: String?,
    val variantAttributes: Map<String, String>,
    val matchKey: String,
    val confidence: Double,
    val status: ProductIdentityStatus,
    val reasonCodes: List<String>,
)

data class OfferIdentity(
    val productIdentity: ProductIdentity,
    val sourceId: String?,
    val externalId: String?,
    val sourceUrl: String?,
    val sellerId: String?,
    val matchKey: String,
)

object TechPhonesIdentityRuntimeLoader {
    private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_phones/v1_0"
    private const val OVERLAY_PATH =
        "taxonomy/stage2/2.2/TECH/category_packs/tech_phones/data_overlays/model_data_hardening/v1_1"

    fun load(): TechPhonesIdentityRuntime {
        val baseModels = readBaseModelHead("$BASE_PATH/model_head_seed.tech_phones.v1_0.tsv")
        val overlayModels = readOverlayModelHead("$OVERLAY_PATH/model_head_seed_delta.tech_phones.v1_1.tsv")
        val variantHints = readVariantHints("$OVERLAY_PATH/model_variant_minimal_matrix.tech_phones.v1_1.tsv")
        val models = mergeModels(
            baseModels = baseModels,
            overlayModels = overlayModels,
            variantHints = variantHints,
        )
        val aliases = mergeAliases(
            baseAliases = readBaseAliases("$BASE_PATH/phone_model_aliases.tech_phones.ru.v1_0.tsv"),
            overlayAliases = readOverlayAliases("$OVERLAY_PATH/model_aliases_delta.ru.tech_phones.v1_1.tsv"),
            models = models,
        )
        return TechPhonesIdentityRuntime(
            models = models,
            aliases = aliases,
            dedupPolicy = readDedupPolicy("$BASE_PATH/phone_dedup_policy.tech_phones.v1_0.yaml"),
            identityPolicy = readIdentityPolicy(
                basePath = "$BASE_PATH/phone_identity_resolution_rules.tech_phones.v1_0.yaml",
                overlayPath = "$OVERLAY_PATH/identity_resolution_min_policy.tech_phones.v1_1.yaml",
            ),
        )
    }

    private fun readBaseModelHead(path: String): List<TechPhoneModelHead> =
        readTsv(path).map { row ->
            val brand = row.getValue("brand").normalizedCode()
            val model = row.getValue("model").trim()
            TechPhoneModelHead(
                modelCode = modelCodeOf(brand = brand, model = model),
                brand = brand,
                family = row.getValue("family").normalizedCode(),
                line = row.getValue("line").normalizedCode(),
                model = model,
                phoneType = row.getValue("phone_type").normalizedCode(),
                releaseYear = row.getValue("release_year").trim().toIntOrNull(),
                sourcePolicy = row["source_policy"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    private fun readOverlayModelHead(path: String): List<TechPhoneModelHead> =
        readTsv(path).map { row ->
            TechPhoneModelHead(
                modelCode = row.getValue("model_code").normalizedCode(),
                brand = row.getValue("brand").normalizedCode(),
                family = row.getValue("family").normalizedCode(),
                line = row.getValue("line").normalizedCode(),
                model = row.getValue("model").trim(),
                phoneType = row.getValue("phone_type").normalizedCode(),
                releaseYear = row.getValue("release_year").trim().toIntOrNull(),
                storageValuesGb = row.getValue("storage_values").splitValues(),
                ramValuesGb = row.getValue("ram_values").splitValues(),
                sourcePolicy = row["source_policy"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    private fun readVariantHints(path: String): Map<String, Pair<List<String>, List<String>>> =
        readTsv(path).associate { row ->
            row.getValue("model_code").normalizedCode() to (
                row.getValue("storage_values_soft").splitValues() to row.getValue("ram_values_soft").splitValues()
                )
        }

    private fun mergeModels(
        baseModels: List<TechPhoneModelHead>,
        overlayModels: List<TechPhoneModelHead>,
        variantHints: Map<String, Pair<List<String>, List<String>>>,
    ): List<TechPhoneModelHead> {
        val byIdentity = LinkedHashMap<String, TechPhoneModelHead>()
        baseModels.forEach { model ->
            byIdentity[model.identityKey()] = model
        }
        overlayModels.forEach { model ->
            val hinted = variantHints[model.modelCode]
            val existing = byIdentity[model.identityKey()]
            byIdentity[model.identityKey()] = model.copy(
                storageValuesGb = (model.storageValuesGb + hinted?.first.orEmpty() + existing?.storageValuesGb.orEmpty())
                    .distinctNumericStrings(),
                ramValuesGb = (model.ramValuesGb + hinted?.second.orEmpty() + existing?.ramValuesGb.orEmpty())
                    .distinctNumericStrings(),
            )
        }
        return byIdentity.values.sortedWith(
            compareBy<TechPhoneModelHead> { it.brand }
                .thenBy { it.model },
        )
    }

    private fun readBaseAliases(path: String): List<TechPhoneModelAlias> =
        readTsv(path).map { row ->
            val brand = row.getValue("brand").normalizedCode()
            val model = row.getValue("model").trim()
            TechPhoneModelAlias(
                alias = row.getValue("alias").trim(),
                brand = brand,
                family = row.getValue("family").normalizedCode(),
                line = "",
                model = model,
                modelCode = modelCodeOf(brand = brand, model = model),
                priority = row.getValue("priority").trim().toIntOrNull() ?: 0,
                sourcePolicy = row["source_policy"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    private fun readOverlayAliases(path: String): List<TechPhoneModelAlias> =
        readTsv(path).map { row ->
            TechPhoneModelAlias(
                alias = row.getValue("alias").trim(),
                brand = row.getValue("brand").normalizedCode(),
                family = row.getValue("family").normalizedCode(),
                line = row.getValue("line").normalizedCode(),
                model = row.getValue("model").trim(),
                modelCode = row.getValue("model_code").normalizedCode(),
                priority = row.getValue("priority").trim().toIntOrNull() ?: 0,
                sourcePolicy = row["source_policy"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    private fun mergeAliases(
        baseAliases: List<TechPhoneModelAlias>,
        overlayAliases: List<TechPhoneModelAlias>,
        models: List<TechPhoneModelHead>,
    ): List<TechPhoneModelAlias> {
        val modelAliases = models.flatMap { model ->
            listOf(
                TechPhoneModelAlias(
                    alias = model.model,
                    brand = model.brand,
                    family = model.family,
                    line = model.line,
                    model = model.model,
                    modelCode = model.modelCode,
                    priority = 110,
                    sourcePolicy = model.sourcePolicy,
                ),
                TechPhoneModelAlias(
                    alias = "${model.brand} ${model.model}",
                    brand = model.brand,
                    family = model.family,
                    line = model.line,
                    model = model.model,
                    modelCode = model.modelCode,
                    priority = 105,
                    sourcePolicy = model.sourcePolicy,
                ),
            )
        }
        val byAliasAndModel = LinkedHashMap<String, TechPhoneModelAlias>()
        (baseAliases + overlayAliases + modelAliases)
            .filter { it.alias.isNotBlank() && it.modelCode.isNotBlank() }
            .forEach { alias ->
                val key = "${normalizeText(alias.alias)}|${alias.modelCode}"
                val existing = byAliasAndModel[key]
                if (existing == null || alias.priority > existing.priority) {
                    byAliasAndModel[key] = alias
                }
            }
        return byAliasAndModel.values.sortedWith(
            compareByDescending<TechPhoneModelAlias> { normalizeText(it.alias).length }
                .thenByDescending { it.priority }
                .thenBy { it.modelCode },
        )
    }

    private fun readDedupPolicy(path: String): TechPhonesDedupPolicy {
        val yaml = CatalogSeedResourceReader.readText(path)
        return TechPhonesDedupPolicy(
            policyId = parseScalar(yaml, "policy_id"),
            masterProductKeyPriority = parseTopLevelList(yaml, "master_product_key_priority"),
            offerDedupKey = parseTopLevelList(yaml, "offer_dedup_key"),
            variantAxes = parseTopLevelList(yaml, "variant_axes"),
            notSameMasterWhen = parseTopLevelList(yaml, "not_same_master_when"),
            sameMasterButDifferentOfferWhen = parseTopLevelList(yaml, "same_master_but_different_offer_when"),
        )
    }

    private fun readIdentityPolicy(
        basePath: String,
        overlayPath: String,
    ): TechPhonesIdentityResolutionPolicy {
        val baseYaml = CatalogSeedResourceReader.readText(basePath)
        val overlayYaml = CatalogSeedResourceReader.readText(overlayPath)
        return TechPhonesIdentityResolutionPolicy(
            policyId = parseScalar(baseYaml, "policy_id"),
            resolutionPriority = parseTopLevelList(baseYaml, "resolution_priority"),
            ambiguousShortAliases = parseInlineExamples(overlayYaml, "examples")
                .map(::normalizeText)
                .filter { it.isNotEmpty() }
                .toSet(),
            rawPolicyIds = listOfNotNull(
                parseScalar(baseYaml, "policy_id"),
                parseScalar(overlayYaml, "pack"),
            ),
        )
    }

    private fun readTsv(path: String): List<Map<String, String>> {
        val lines = CatalogSeedResourceReader.readText(path)
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split('\t')
        return lines.drop(1).map { line ->
            val cells = line.split('\t')
            header.indices.associate { index ->
                header[index] to cells.getOrElse(index) { "" }
            }
        }
    }

    private fun parseScalar(
        yaml: String,
        key: String,
    ): String? =
        Regex("""(?m)^${Regex.escape(key)}:\s*(.+?)\s*$""")
            .find(yaml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun parseTopLevelList(
        yaml: String,
        key: String,
    ): List<String> {
        val lines = yaml.lines()
        val start = lines.indexOfFirst { it.trim() == "$key:" }
        if (start < 0) return emptyList()
        val values = mutableListOf<String>()
        for (index in start + 1 until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (!line.startsWith(" ") && trimmed.endsWith(":")) break
            if (trimmed.startsWith("- ")) {
                values += trimmed.removePrefix("- ").trim()
            }
        }
        return values
    }

    private fun parseInlineExamples(
        yaml: String,
        key: String,
    ): List<String> {
        val match = Regex("""(?m)^\s*${Regex.escape(key)}:\s*\[(.+)]\s*$""").find(yaml) ?: return emptyList()
        return match.groupValues[1]
            .split(',')
            .map { value -> value.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }
    }
}

class TechPhonesIdentityResolver(
    private val runtime: TechPhonesIdentityRuntime = TechPhonesIdentityRuntimeLoader.load(),
) {
    private val modelsByCode = runtime.models.associateBy { it.modelCode }
    private val brandCodes = runtime.models.map { it.brand }.toSet()
    private val aliasesByNormalized = runtime.aliases.groupBy { normalizeText(it.alias) }
    private val sortedAliasKeys = aliasesByNormalized.keys
        .filter { it.isNotBlank() }
        .sortedByDescending { it.length }

    fun resolveProductIdentity(
        categoryCode: String,
        brand: String? = null,
        model: String? = null,
        titleOrQuery: String? = null,
        attrs: Map<String, String> = emptyMap(),
    ): ProductIdentity {
        val normalizedCategory = normalizeCategoryCode(categoryCode)
        if (normalizedCategory != TECH_PHONES_CATEGORY) {
            return unresolved(
                categoryCode = normalizedCategory.ifBlank { categoryCode.trim() },
                brand = normalizeBrandInput(brand),
                titleOrQuery = titleOrQuery,
                reasonCodes = listOf("UNSUPPORTED_CATEGORY"),
            )
        }

        val queryText = listOfNotNull(model, titleOrQuery)
            .joinToString(" ")
            .trim()
        val normalizedQuery = normalizeText(queryText)
        val brandCanonical = normalizeBrandInput(brand)
            ?: inferBrandFromText(normalizedQuery)
        val match = selectModelMatch(
            normalizedModelText = normalizeText(model.orEmpty()),
            normalizedQuery = normalizedQuery,
            brandCanonical = brandCanonical,
        )

        if (match == null) {
            return unresolved(
                categoryCode = normalizedCategory,
                brand = brandCanonical,
                titleOrQuery = titleOrQuery ?: model,
                reasonCodes = listOf("MODEL_NOT_RESOLVED"),
            )
        }

        val matchedAlias = match.aliasKey
        val hasBrandContext = brandCanonical != null ||
            textHasBrandContext(normalizedQuery = normalizedQuery, candidate = match.alias)
        if (matchedAlias in runtime.identityPolicy.ambiguousShortAliases && !hasBrandContext) {
            return ProductIdentity(
                categoryCode = normalizedCategory,
                brandCanonical = match.alias.brand,
                modelCanonical = null,
                modelCode = null,
                familyCode = match.alias.family.ifBlank { null },
                variantAttributes = emptyMap(),
                matchKey = unresolvedMatchKey(
                    categoryCode = normalizedCategory,
                    brand = match.alias.brand,
                    rawText = titleOrQuery ?: model ?: matchedAlias,
                ),
                confidence = 0.3,
                status = ProductIdentityStatus.UNRESOLVED,
                reasonCodes = listOf("AMBIGUOUS_SHORT_ALIAS", "MODEL_NEEDS_BRAND_OR_CONTEXT"),
            )
        }

        val modelHead = modelsByCode[match.alias.modelCode]
        val variants = resolveVariantAttributes(
            model = modelHead,
            attrs = attrs,
            normalizedQuery = normalizedQuery,
        )
        val confidence = when {
            match.alias.priority >= 100 && hasBrandContext -> 0.98
            match.alias.priority >= 100 -> 0.94
            hasBrandContext -> 0.9
            else -> 0.82
        }
        val status = if (confidence >= 0.9) ProductIdentityStatus.RESOLVED else ProductIdentityStatus.CANDIDATE
        val identity = ProductIdentity(
            categoryCode = normalizedCategory,
            brandCanonical = match.alias.brand,
            modelCanonical = match.alias.model,
            modelCode = match.alias.modelCode,
            familyCode = match.alias.family.ifBlank { null },
            variantAttributes = variants,
            matchKey = productMatchKey(
                categoryCode = normalizedCategory,
                brand = match.alias.brand,
                modelCode = match.alias.modelCode,
                variants = variants,
            ),
            confidence = confidence,
            status = status,
            reasonCodes = buildList {
                add("MODEL_ALIAS_MATCH")
                if (hasBrandContext) add("BRAND_CONTEXT")
                if (variants.isNotEmpty()) add("VARIANT_AXES:${variants.keys.joinToString(",")}")
            },
        )
        return identity
    }

    fun resolveOfferIdentity(
        productIdentity: ProductIdentity,
        sourceId: String? = null,
        externalId: String? = null,
        sourceUrl: String? = null,
        sellerId: String? = null,
    ): OfferIdentity {
        val components = linkedMapOf(
            "product" to productIdentity.matchKey,
            "source" to sourceId.normalizedNullable(),
            "external" to externalId.normalizedNullable(),
            "url" to sourceUrl.normalizedNullable(),
            "seller" to sellerId.normalizedNullable(),
        ).filterValues { it != null }

        return OfferIdentity(
            productIdentity = productIdentity,
            sourceId = sourceId?.trim()?.takeIf { it.isNotEmpty() },
            externalId = externalId?.trim()?.takeIf { it.isNotEmpty() },
            sourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
            sellerId = sellerId?.trim()?.takeIf { it.isNotEmpty() },
            matchKey = components.entries.joinToString("|") { (key, value) -> "$key=$value" },
        )
    }

    private fun selectModelMatch(
        normalizedModelText: String,
        normalizedQuery: String,
        brandCanonical: String?,
    ): CandidateMatch? {
        val searchTexts = listOf(normalizedModelText, normalizedQuery)
            .filter { it.isNotBlank() }
            .distinct()
        val candidates = searchTexts.flatMap { text -> aliasMatches(text) }
        if (candidates.isEmpty()) return null

        return candidates
            .filter { candidate -> brandCanonical == null || candidate.alias.brand == brandCanonical }
            .ifEmpty { candidates }
            .sortedWith(
                compareByDescending<CandidateMatch> { it.score }
                    .thenByDescending { it.alias.priority }
                    .thenByDescending { it.aliasKey.length }
                    .thenBy { it.alias.modelCode },
            )
            .firstOrNull()
    }

    private fun aliasMatches(text: String): List<CandidateMatch> {
        if (text.isBlank()) return emptyList()
        val compactText = compact(text)
        return sortedAliasKeys.flatMap { aliasKey ->
            val compactAlias = compact(aliasKey)
            val matches = containsNormalizedPhrase(text = text, phrase = aliasKey) ||
                (compactAlias.isNotBlank() && compactText.contains(compactAlias))
            if (!matches) return@flatMap emptyList()
            aliasesByNormalized.getValue(aliasKey).map { alias ->
                CandidateMatch(
                    alias = alias,
                    aliasKey = aliasKey,
                    score = aliasKey.length * 10 + alias.priority,
                )
            }
        }
    }

    private fun inferBrandFromText(normalizedText: String): String? {
        if (normalizedText.isBlank()) return null
        return brandCodes.firstOrNull { brand ->
            containsNormalizedPhrase(normalizedText, normalizeText(brand))
        } ?: when {
            containsNormalizedPhrase(normalizedText, "iphone") -> "APPLE"
            containsNormalizedPhrase(normalizedText, "айфон") -> "APPLE"
            containsNormalizedPhrase(normalizedText, "эппл") -> "APPLE"
            containsNormalizedPhrase(normalizedText, "аппл") -> "APPLE"
            containsNormalizedPhrase(normalizedText, "самсунг") -> "SAMSUNG"
            else -> null
        }
    }

    private fun textHasBrandContext(
        normalizedQuery: String,
        candidate: TechPhoneModelAlias,
    ): Boolean {
        if (normalizedQuery.isBlank()) return false
        if (containsNormalizedPhrase(normalizedQuery, normalizeText(candidate.brand))) return true
        return when (candidate.brand) {
            "APPLE" -> containsNormalizedPhrase(normalizedQuery, "iphone") ||
                containsNormalizedPhrase(normalizedQuery, "айфон") ||
                containsNormalizedPhrase(normalizedQuery, "эппл") ||
                containsNormalizedPhrase(normalizedQuery, "аппл")
            "SAMSUNG" -> containsNormalizedPhrase(normalizedQuery, "samsung") ||
                containsNormalizedPhrase(normalizedQuery, "самсунг") ||
                containsNormalizedPhrase(normalizedQuery, "galaxy")
            "GOOGLE" -> containsNormalizedPhrase(normalizedQuery, "google") ||
                containsNormalizedPhrase(normalizedQuery, "pixel")
            else -> false
        }
    }

    private fun resolveVariantAttributes(
        model: TechPhoneModelHead?,
        attrs: Map<String, String>,
        normalizedQuery: String,
    ): Map<String, String> {
        val normalizedAttrs = attrs.entries.associate { (key, value) ->
            normalizeAttributeKey(key) to value.trim()
        }
        val variants = LinkedHashMap<String, String>()

        pickFirst(normalizedAttrs, "storage_capacity_gb", "storage", "memory", "builtin_memory")
            ?.let(::normalizeCapacityGb)
            ?.let { variants["storage_capacity_gb"] = it }
        pickFirst(normalizedAttrs, "ram_gb", "ram", "memory_ram")
            ?.let(::normalizeCapacityGb)
            ?.let { variants["ram_gb"] = it }
        pickFirst(normalizedAttrs, "color_family", "color", "colour")
            ?.let(::normalizeLooseValue)
            ?.let { variants["color_family"] = it }
        pickFirst(normalizedAttrs, "region_variant", "region", "market_region")
            ?.let(::normalizeLooseValue)
            ?.let { variants["region_variant"] = it }
        pickFirst(normalizedAttrs, "variant")
            ?.let(::normalizeLooseValue)
            ?.let { variants["variant"] = it }

        if (model != null && normalizedQuery.isNotBlank()) {
            if ("storage_capacity_gb" !in variants) {
                detectKnownNumericVariant(normalizedQuery, model.storageValuesGb)
                    ?.let { variants["storage_capacity_gb"] = it }
            }
            if ("ram_gb" !in variants) {
                detectKnownNumericVariant(normalizedQuery, model.ramValuesGb)
                    ?.let { variants["ram_gb"] = it }
            }
        }

        return variants
            .filterKeys { key -> key in runtime.dedupPolicy.variantAxes || key == "variant" }
            .toSortedMap()
    }

    private fun unresolved(
        categoryCode: String,
        brand: String?,
        titleOrQuery: String?,
        reasonCodes: List<String>,
    ): ProductIdentity =
        ProductIdentity(
            categoryCode = categoryCode,
            brandCanonical = brand,
            modelCanonical = null,
            modelCode = null,
            familyCode = null,
            variantAttributes = emptyMap(),
            matchKey = unresolvedMatchKey(
                categoryCode = categoryCode,
                brand = brand,
                rawText = titleOrQuery.orEmpty(),
            ),
            confidence = 0.0,
            status = ProductIdentityStatus.UNRESOLVED,
            reasonCodes = reasonCodes,
        )

    private data class CandidateMatch(
        val alias: TechPhoneModelAlias,
        val aliasKey: String,
        val score: Int,
    )
}

private fun productMatchKey(
    categoryCode: String,
    brand: String,
    modelCode: String,
    variants: Map<String, String>,
): String {
    val base = linkedMapOf(
        "category" to categoryCode.normalizedKey(),
        "brand" to brand.normalizedKey(),
        "model" to modelCode.normalizedKey(),
    )
    val all = base + variants.mapValues { (_, value) -> value.normalizedKey() }
    return all.entries.joinToString("|") { (key, value) -> "$key=$value" }
}

private fun unresolvedMatchKey(
    categoryCode: String,
    brand: String?,
    rawText: String,
): String {
    val values = linkedMapOf(
        "category" to categoryCode.normalizedKey(),
        "brand" to brand?.normalizedKey(),
        "unresolved" to normalizeText(rawText).ifBlank { "unknown" }.normalizedKey(),
    ).filterValues { it != null }
    return values.entries.joinToString("|") { (key, value) -> "$key=$value" }
}

private fun containsNormalizedPhrase(
    text: String,
    phrase: String,
): Boolean {
    if (phrase.isBlank()) return false
    return text == phrase ||
        text.startsWith("$phrase ") ||
        text.endsWith(" $phrase") ||
        text.contains(" $phrase ")
}

private fun detectKnownNumericVariant(
    normalizedText: String,
    allowedValues: List<String>,
): String? {
    if (allowedValues.isEmpty()) return null
    val tokens = Stage21QueryTextNormalizer.tokenize(normalizedText)
    return allowedValues.firstOrNull { allowed ->
        allowed in tokens ||
            "$allowed gb" in normalizedText ||
            "$allowed гб" in normalizedText ||
            "${allowed}gb" in compact(normalizedText)
    }
}

private fun normalizeBrandInput(value: String?): String? {
    val normalized = normalizeText(value.orEmpty())
    if (normalized.isBlank()) return null
    return when (normalized) {
        "apple", "эппл", "аппл" -> "APPLE"
        "samsung", "самсунг" -> "SAMSUNG"
        "google", "гугл" -> "GOOGLE"
        else -> normalized.normalizedCode()
    }
}

private fun normalizeCapacityGb(value: String): String? {
    val normalized = normalizeText(value)
    if (normalized.isBlank()) return null
    val number = Regex("""\b(\d{1,4})\s*(tb|тб|gb|гб)?\b""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    val unit = Regex("""\b\d{1,4}\s*(tb|тб|gb|гб)\b""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
    return when (unit) {
        "tb", "тб" -> (number.toIntOrNull()?.times(1024))?.toString()
        else -> number
    }
}

private fun normalizeLooseValue(value: String): String? =
    normalizeText(value).takeIf { it.isNotBlank() }

private fun normalizeAttributeKey(value: String): String =
    value.trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("""[\s\-]+"""), "_")
        .replace(Regex("""[^\p{L}\p{N}_]+"""), "")
        .replace(Regex("""_+"""), "_")
        .trim('_')

private fun normalizeCategoryCode(value: String): String =
    value.trim().uppercase(Locale.ROOT)

private fun normalizeText(value: String): String =
    Stage21QueryTextNormalizer.normalize(value)

private fun compact(value: String): String =
    normalizeText(value).replace(" ", "")

private fun String.normalizedCode(): String =
    trim()
        .uppercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}]+"""), "_")
        .replace(Regex("""_+"""), "_")
        .trim('_')

private fun String.normalizedKey(): String =
    normalizeText(this)
        .replace(Regex("""[^\p{L}\p{N}]+"""), "-")
        .replace(Regex("""-+"""), "-")
        .trim('-')

private fun String?.normalizedNullable(): String? =
    this?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.normalizedKey()

private fun String.splitValues(): List<String> =
    split('|')
        .mapNotNull { normalizeCapacityGb(it) }
        .distinctNumericStrings()

private fun List<String>.distinctNumericStrings(): List<String> =
    map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

private fun TechPhoneModelHead.identityKey(): String =
    "${brand.normalizedCode()}|${normalizeText(model)}"

private fun modelCodeOf(
    brand: String,
    model: String,
): String =
    "${brand.normalizedCode()}_${model.normalizedCode()}"

private fun <K, V> Map<K, V>.filterValues(predicate: (V) -> Boolean): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    forEach { (key, value) ->
        if (predicate(value)) result[key] = value
    }
    return result
}

private fun pickFirst(
    values: Map<String, String>,
    vararg keys: String,
): String? =
    keys.firstNotNullOfOrNull { key -> values[key]?.takeIf { it.isNotBlank() } }
