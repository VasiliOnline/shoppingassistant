package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
data class Stage40GeneratedFrom(
    val stage22DataVersion: String,
    val stage22SchemaVersion: String,
    val stage22GeneratedAt: String,
    val stage3Version: String = "3.0",
)

@Serializable
data class Stage40ImmutableAttribute(
    val attributeCode: String,
    val valueType: Stage22ValueType,
    val valueSetType: Stage22ValueSetType,
    val unit: String? = null,
    val isIdentity: Boolean,
    val isFacet: Boolean,
    val normalization: String? = null,
    val dictionaryRequired: Boolean,
    val immutableFingerprint: String,
)

@Serializable
data class Stage40ImmutableSchemaDocument(
    val stage: String = "4.0",
    val schemaVersion: String = "1.0.0",
    val generatedFrom: Stage40GeneratedFrom,
    val attributes: List<Stage40ImmutableAttribute>,
)

@Serializable
enum class Stage40DedupTokenMode {
    VALUE_CODE,
    NORMALIZED_TEXT,
}

@Serializable
data class Stage40NormalizationRule(
    val attributeCode: String,
    val normalization: String,
    val valueSetType: Stage22ValueSetType,
    val dictionaryBacked: Boolean,
    val acceptsFreeText: Boolean,
    val canonicalSource: String,
    val dedupTokenMode: Stage40DedupTokenMode,
)

@Serializable
data class Stage40NormalizationContractDocument(
    val stage: String = "4.0",
    val schemaVersion: String = "1.0.0",
    val rules: List<Stage40NormalizationRule>,
)

@Serializable
enum class Stage40DedupEntity {
    ATTRIBUTE_SCHEMA,
    DICTIONARY_VALUE,
    CATEGORY_PROFILE_ATTRIBUTE,
    FACET_DEFINITION,
    FACET_PRESET,
    FACET_COLLECTION,
    CATEGORY_IDENTITY_SIGNATURE,
}

@Serializable
data class Stage40DedupTemplate(
    val entity: Stage40DedupEntity,
    val template: String,
    val fields: List<String>,
    val description: String,
)

@Serializable
data class Stage40DedupKeysDocument(
    val stage: String = "4.0",
    val schemaVersion: String = "1.0.0",
    val templates: List<Stage40DedupTemplate>,
)

@Serializable
data class Stage40RequiredIfCondition(
    val attributeCode: String,
    val op: AttributeConditionOp = AttributeConditionOp.EQUALS_ANY,
    val values: List<String> = emptyList(),
)

@Serializable
data class Stage40RequiredIfRule(
    val categoryCode: String,
    val whenAll: List<Stage40RequiredIfCondition> = emptyList(),
)

@Serializable
data class Stage40TypedConstraint(
    val attributeCode: String,
    val valueType: Stage22ValueType,
    val enumOnly: Boolean = false,
    val unit: String? = null,
    val regex: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val requiredIf: List<Stage40RequiredIfRule> = emptyList(),
)

@Serializable
data class Stage40TypedConstraintsDocument(
    val stage: String = "4.0",
    val schemaVersion: String = "1.0.0",
    val constraints: List<Stage40TypedConstraint>,
)

internal object Stage40ContractLoader {
    private const val STAGE40_BASE = "taxonomy/stage4/4.0"

    private val immutableSchemaCache: Stage40ImmutableSchemaDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "$STAGE40_BASE/immutable_attribute_schema.json",
            deserializer = Stage40ImmutableSchemaDocument.serializer(),
        )
    }

    private val normalizationContractCache: Stage40NormalizationContractDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "$STAGE40_BASE/normalization_contract.json",
            deserializer = Stage40NormalizationContractDocument.serializer(),
        )
    }

    private val dedupKeysCache: Stage40DedupKeysDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "$STAGE40_BASE/dedup_keys.json",
            deserializer = Stage40DedupKeysDocument.serializer(),
        )
    }

    private val typedConstraintsCache: Stage40TypedConstraintsDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "$STAGE40_BASE/typed_constraints.json",
            deserializer = Stage40TypedConstraintsDocument.serializer(),
        )
    }

    fun loadImmutableSchema(): Stage40ImmutableSchemaDocument = immutableSchemaCache

    fun loadNormalizationContract(): Stage40NormalizationContractDocument = normalizationContractCache

    fun loadDedupKeys(): Stage40DedupKeysDocument = dedupKeysCache

    fun loadTypedConstraints(): Stage40TypedConstraintsDocument = typedConstraintsCache
}
