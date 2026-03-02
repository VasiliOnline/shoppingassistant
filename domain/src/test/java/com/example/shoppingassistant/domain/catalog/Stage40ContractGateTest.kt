package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.facet.FacetSchemaValidator
import com.example.shoppingassistant.domain.facet.FacetValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class Stage40ContractGateTest {
    @Test
    fun stage4_immutable_schema_must_match_stage22_registry() {
        val expected = expectedImmutableSchemaDocument()
        assertEquals(expected, CatalogSeed.stage40ImmutableSchema)
    }

    @Test
    fun stage4_normalization_contract_must_match_stage22_registry() {
        val expected = expectedNormalizationContractDocument()
        assertEquals(expected, CatalogSeed.stage40NormalizationContract)
    }

    @Test
    fun stage4_typed_constraints_must_match_stage22_registry_and_profiles() {
        val expected = expectedTypedConstraintsDocument()
        assertEquals(expected, CatalogSeed.stage40TypedConstraints)
    }

    @Test
    fun stage4_dedup_templates_must_cover_product_ready_model_without_collisions() {
        val expectedDedup = expectedDedupKeysDocument()
        assertEquals(expectedDedup, CatalogSeed.stage40DedupKeys)

        val stage22Report = Stage22SeedValidator().validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        assertTrue(stage22Report.summary(), stage22Report.isValid)

        val stage3Report = FacetSchemaValidator().validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = CatalogSeed.facetPresets,
            collections = CatalogSeed.facetCollections,
        )
        assertTrue(stage3Report.summary(), stage3Report.isValid)

        val stubbedPresets = CatalogSeed.facetPresets
            .asSequence()
            .filter { preset ->
                preset.notes?.trim().orEmpty().equals(STAGE3_STUB_NOTE, ignoreCase = true)
            }
            .map { preset -> preset.presetCode.trim() }
            .sorted()
            .toList()
        assertTrue(
            "Stage 4 gate requires product-ready Stage 3 data without stub presets: ${stubbedPresets.joinToString()}",
            stubbedPresets.isEmpty(),
        )

        val registry = Stage22RegistryLoader.loadSnapshot()
        assertUnique(
            entity = Stage40DedupEntity.ATTRIBUTE_SCHEMA.name,
            keys = registry.attributes.keys.map { it.trim() },
        )

        val dictionaryKeys = registry.dictionaries.values.flatMap { dictionary ->
            val attributeCode = dictionary.attributeCode.trim()
            dictionary.entries.map { entry ->
                "$attributeCode|${entry.valueCode.trim()}"
            }
        }
        assertUnique(
            entity = Stage40DedupEntity.DICTIONARY_VALUE.name,
            keys = dictionaryKeys,
        )

        val profileKeys = CatalogSeed.profiles.flatMap { profile ->
            val categoryCode = profile.category.code.trim()
            profile.attributes.map { attribute ->
                "$categoryCode|${attribute.code.trim()}"
            }
        }
        assertUnique(
            entity = Stage40DedupEntity.CATEGORY_PROFILE_ATTRIBUTE.name,
            keys = profileKeys,
        )

        assertUnique(
            entity = Stage40DedupEntity.FACET_DEFINITION.name,
            keys = CatalogSeed.facetDefinitions.map { definition -> definition.facetKey.trim() },
        )
        assertUnique(
            entity = Stage40DedupEntity.FACET_PRESET.name,
            keys = CatalogSeed.facetPresets.map { preset -> preset.presetCode.trim() },
        )
        assertUnique(
            entity = Stage40DedupEntity.FACET_COLLECTION.name,
            keys = CatalogSeed.facetCollections.map { collection -> collection.collectionCode.trim() },
        )

        val stage40AttributeCodes = CatalogSeed.stage40ImmutableSchema.attributes
            .map { it.attributeCode.trim() }
            .toSet()
        val missingFacetAttributes = CatalogSeed.facetDefinitions
            .asSequence()
            .filter { definition -> definition.source != FacetValueSource.DERIVED }
            .map { definition -> definition.facetKey.trim() }
            .filterNot { key -> key in stage40AttributeCodes || key in OFFER_FACET_ALLOWLIST }
            .distinct()
            .sorted()
            .toList()
        assertTrue(
            "Stage 4 immutable schema must cover all Stage 3 facet keys. Missing: ${missingFacetAttributes.joinToString()}",
            missingFacetAttributes.isEmpty(),
        )

        val effectiveSpecEngine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        val fallbackLeafCategories = mutableListOf<String>()
        val identitySignatureKeys = leafCategoryCodes(CatalogSeed.categories)
            .sorted()
            .map { categoryCode ->
                val spec = effectiveSpecEngine.getEffectiveSpec(categoryCode)
                if (spec.meta.isFallback) {
                    fallbackLeafCategories += categoryCode
                }
                val identityAttributes = spec.identityAttributes
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .sorted()
                assertTrue(
                    "Leaf category '$categoryCode' must have identity attributes for Stage 4 dedup.",
                    identityAttributes.isNotEmpty(),
                )
                val signatureHash = sha256Hex(identityAttributes.joinToString("|"))
                "$categoryCode|$signatureHash"
            }

        assertTrue(
            "Leaf categories must be product-ready (no fallback effective spec) before Stage 4 validation: " +
                fallbackLeafCategories.joinToString(),
            fallbackLeafCategories.isEmpty(),
        )
        assertUnique(
            entity = Stage40DedupEntity.CATEGORY_IDENTITY_SIGNATURE.name,
            keys = identitySignatureKeys,
        )
    }

    private fun expectedImmutableSchemaDocument(): Stage40ImmutableSchemaDocument {
        val registry = Stage22RegistryLoader.loadSnapshot()
        return Stage40ImmutableSchemaDocument(
            stage = "4.0",
            schemaVersion = "1.0.0",
            generatedFrom = Stage40GeneratedFrom(
                stage22DataVersion = registry.meta.dataVersion.trim(),
                stage22SchemaVersion = registry.meta.schemaVersion.trim(),
                stage22GeneratedAt = registry.meta.generatedAt.trim(),
                stage3Version = "3.0",
            ),
            attributes = registry.attributes.values
                .asSequence()
                .sortedBy { it.attributeCode }
                .map { attribute ->
                    val attributeCode = attribute.attributeCode.trim()
                    val valueType = attribute.valueType
                    val valueSetType = attribute.valueSetType
                    val unit = attribute.unit?.trim()?.takeIf { it.isNotEmpty() }
                    val normalization = attribute.normalization?.trim()?.takeIf { it.isNotEmpty() }
                    val fingerprintSource = buildString {
                        append(attributeCode)
                        append("|")
                        append(valueType.name)
                        append("|")
                        append(valueSetType.name)
                        append("|")
                        append(unit.orEmpty())
                        append("|")
                        append(attribute.isIdentity)
                        append("|")
                        append(attribute.isFacet)
                        append("|")
                        append(normalization.orEmpty())
                    }
                    Stage40ImmutableAttribute(
                        attributeCode = attributeCode,
                        valueType = valueType,
                        valueSetType = valueSetType,
                        unit = unit,
                        isIdentity = attribute.isIdentity,
                        isFacet = attribute.isFacet,
                        normalization = normalization,
                        dictionaryRequired = valueSetType != Stage22ValueSetType.OPEN,
                        immutableFingerprint = "sha256:${sha256Hex(fingerprintSource)}",
                    )
                }
                .toList(),
        )
    }

    private fun expectedNormalizationContractDocument(): Stage40NormalizationContractDocument {
        val registry = Stage22RegistryLoader.loadSnapshot()
        return Stage40NormalizationContractDocument(
            stage = "4.0",
            schemaVersion = "1.0.0",
            rules = registry.attributes.values
                .asSequence()
                .sortedBy { it.attributeCode }
                .map { attribute ->
                    val dictionaryBacked = attribute.valueSetType != Stage22ValueSetType.OPEN
                    Stage40NormalizationRule(
                        attributeCode = attribute.attributeCode.trim(),
                        normalization = attribute.normalization?.trim().orEmpty().ifBlank { DEFAULT_NORMALIZATION },
                        valueSetType = attribute.valueSetType,
                        dictionaryBacked = dictionaryBacked,
                        acceptsFreeText = !dictionaryBacked,
                        canonicalSource = if (dictionaryBacked) DICTIONARY_SOURCE else INLINE_SOURCE,
                        dedupTokenMode = if (dictionaryBacked) Stage40DedupTokenMode.VALUE_CODE else Stage40DedupTokenMode.NORMALIZED_TEXT,
                    )
                }
                .toList(),
        )
    }

    private fun expectedDedupKeysDocument(): Stage40DedupKeysDocument = Stage40DedupKeysDocument(
        stage = "4.0",
        schemaVersion = "1.0.0",
        templates = listOf(
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.ATTRIBUTE_SCHEMA,
                template = "{attributeCode}",
                fields = listOf("attributeCode"),
                description = "Stable key for immutable attribute schema entries.",
            ),
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.DICTIONARY_VALUE,
                template = "{attributeCode}|{valueCode}",
                fields = listOf("attributeCode", "valueCode"),
                description = "Stable key for dictionary value normalization entries.",
            ),
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.CATEGORY_PROFILE_ATTRIBUTE,
                template = "{categoryCode}|{attributeCode}",
                fields = listOf("categoryCode", "attributeCode"),
                description = "Stable key for category profile attribute bindings.",
            ),
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.FACET_DEFINITION,
                template = "{facetKey}",
                fields = listOf("facetKey"),
                description = "Stable key for Stage 3 facet definitions.",
            ),
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.FACET_PRESET,
                template = "{presetCode}",
                fields = listOf("presetCode"),
                description = "Stable key for Stage 3 facet presets.",
            ),
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.FACET_COLLECTION,
                template = "{collectionCode}",
                fields = listOf("collectionCode"),
                description = "Stable key for Stage 3 facet collections.",
            ),
            Stage40DedupTemplate(
                entity = Stage40DedupEntity.CATEGORY_IDENTITY_SIGNATURE,
                template = "{categoryCode}|{identityAttributesHash}",
                fields = listOf("categoryCode", "identityAttributesHash"),
                description = "Stable key for per-category identity attribute signatures used in dedup.",
            ),
        ),
    )

    private fun expectedTypedConstraintsDocument(): Stage40TypedConstraintsDocument {
        val requiredIfByAttribute = LinkedHashMap<String, MutableList<Stage40RequiredIfRule>>()
        CatalogSeed.profiles.forEach { profile ->
            val categoryCode = profile.category.code.trim().uppercase()
            if (categoryCode.isBlank()) return@forEach
            profile.requiredIfRules.forEach { rule ->
                val requiredAttributeCode = rule.requiredAttributeCode.trim()
                if (requiredAttributeCode.isBlank()) return@forEach
                val whenAll = rule.whenAll
                    .mapNotNull { condition ->
                        val attributeCode = condition.attributeCode.trim()
                        val values = condition.values
                            .map { value -> value.trim() }
                            .filter { value -> value.isNotEmpty() }
                        if (attributeCode.isBlank() || values.isEmpty()) {
                            null
                        } else {
                            Stage40RequiredIfCondition(
                                attributeCode = attributeCode,
                                op = condition.op,
                                values = values,
                            )
                        }
                    }
                    .sortedWith(
                        compareBy<Stage40RequiredIfCondition> { it.attributeCode }
                            .thenBy { it.op.name }
                            .thenBy { it.values.joinToString("|") },
                    )
                if (whenAll.isEmpty()) return@forEach
                requiredIfByAttribute
                    .getOrPut(requiredAttributeCode) { mutableListOf() }
                    .add(
                        Stage40RequiredIfRule(
                            categoryCode = categoryCode,
                            whenAll = whenAll,
                        ),
                    )
            }
        }

        val constraints = CatalogSeed.stage40ImmutableSchema.attributes
            .asSequence()
            .sortedBy { it.attributeCode }
            .map { attribute ->
                val attributeCode = attribute.attributeCode.trim()
                val requiredIf = requiredIfByAttribute[attributeCode]
                    .orEmpty()
                    .distinct()
                    .sortedWith(
                        compareBy<Stage40RequiredIfRule> { it.categoryCode }
                            .thenBy { it.whenAll.joinToString("|") { c -> "${c.attributeCode}:${c.op}:${c.values.joinToString(",")}" } },
                    )
                val range = RANGE_CONSTRAINTS[attributeCode]
                Stage40TypedConstraint(
                    attributeCode = attributeCode,
                    valueType = attribute.valueType,
                    enumOnly = attribute.valueType == Stage22ValueType.ENUM,
                    unit = attribute.unit?.trim()?.takeIf { it.isNotEmpty() },
                    regex = PATTERN_CONSTRAINTS[attributeCode],
                    minValue = range?.first,
                    maxValue = range?.second,
                    requiredIf = requiredIf,
                )
            }
            .toList()

        return Stage40TypedConstraintsDocument(
            stage = "4.0",
            schemaVersion = "1.0.0",
            constraints = constraints,
        )
    }

    private fun assertUnique(
        entity: String,
        keys: List<String>,
    ) {
        val normalized = keys
            .map { key -> key.trim() }
            .filter { key -> key.isNotEmpty() }
        val blankCount = keys.size - normalized.size
        assertTrue("$entity dedup keys must be non-blank.", blankCount == 0)

        val duplicates = normalized
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        assertTrue(
            "$entity dedup keys must be unique. Duplicates: ${duplicates.take(10)}",
            duplicates.isEmpty(),
        )
    }

    private fun leafCategoryCodes(categories: List<Category>): Set<String> {
        val categoryCodes = categories
            .asSequence()
            .map { it.code.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val parentCodes = categories
            .asSequence()
            .mapNotNull { it.parentCode?.trim()?.takeIf(String::isNotBlank) }
            .toSet()
        return categoryCodes - parentCodes
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        private const val DEFAULT_NORMALIZATION = "stage22.query_normalizer_v1"
        private const val INLINE_SOURCE = "inline"
        private const val DICTIONARY_SOURCE = "taxonomy/stage2/2.2/_registry/value_dictionaries.json"
        private const val STAGE3_STUB_NOTE = "Stage 3.0 baseline preset"
        private val OFFER_FACET_ALLOWLIST = setOf("price")
        private val RANGE_CONSTRAINTS = mapOf(
            "age_from_months" to (0.0 to 480.0),
            "age_to_months" to (0.0 to 480.0),
            "battery_health_percent" to (0.0 to 100.0),
            "release_year" to (1900.0 to 2100.0),
            "shelf_life_days" to (0.0 to 3650.0),
        )
        private val PATTERN_CONSTRAINTS = mapOf(
            "region_code" to "^[a-z]{2}(?:-[a-z0-9]{1,8})?$",
        )
    }
}
