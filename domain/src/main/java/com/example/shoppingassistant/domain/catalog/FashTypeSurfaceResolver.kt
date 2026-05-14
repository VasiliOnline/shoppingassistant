package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
internal data class FashTypedAttributeCoverageMatrix(
    val schemaVersion: String,
    val matrixCode: String,
    val categories: List<FashTypeSurfaceCategory>,
    val goldenCases: List<FashTypeSurfaceGoldenCase> = emptyList(),
)

@Serializable
internal data class FashTypeSurfaceCategory(
    val categoryCode: String,
    val typeAttribute: String,
    val initialFieldsLimit: Int = 8,
    val primaryFacetsLimit: Int = 7,
    val commonHiddenAttributes: List<String> = emptyList(),
    val commonRequiredAttributes: List<String> = emptyList(),
    val commonValidatorAttributes: List<String> = emptyList(),
    val commonNoGuessAttributes: List<String> = emptyList(),
    val nonFacetExplanations: Map<String, String> = emptyMap(),
    val groups: List<FashTypeSurfaceGroup>,
)

@Serializable
internal data class FashTypeSurfaceGroup(
    val groupCode: String,
    val typeValues: List<String>,
    val initialAttributes: List<String> = emptyList(),
    val primaryAttributes: List<String>,
    val secondaryAttributes: List<String> = emptyList(),
    val hiddenAttributes: List<String> = emptyList(),
    val requiredAttributes: List<String> = emptyList(),
    val validatorAttributes: List<String> = emptyList(),
    val noGuessAttributes: List<String> = emptyList(),
)

@Serializable
internal data class FashTypeSurfaceGoldenCase(
    val caseId: String,
    val categoryCode: String,
    val typeValue: String,
    val expectedGroupCode: String,
    val expectedPrimaryAttributes: List<String>,
)

internal data class FashResolvedTypeSurface(
    val categoryCode: String,
    val typeAttribute: String,
    val typeValue: String,
    val groupCode: String,
    val initialAttributes: List<String>,
    val primaryAttributes: List<String>,
    val secondaryAttributes: List<String>,
    val hiddenAttributes: List<String>,
    val requiredAttributes: List<String>,
    val validatorAttributes: List<String>,
    val noGuessAttributes: List<String>,
)

internal object FashTypeSurfaceResolver {
    private const val MATRIX_PATH =
        "taxonomy/stage2/2.2/FASH/fash_typed_attribute_coverage_matrix.v1_0.json"

    val matrix: FashTypedAttributeCoverageMatrix by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = MATRIX_PATH,
            deserializer = FashTypedAttributeCoverageMatrix.serializer(),
        )
    }

    private val categoriesByCode: Map<String, FashTypeSurfaceCategory> by lazy {
        matrix.categories.associateBy { category -> category.categoryCode.normalizeCategoryCode() }
    }

    private val groupsByCategoryAndType: Map<Pair<String, String>, Pair<FashTypeSurfaceCategory, FashTypeSurfaceGroup>> by lazy {
        buildMap {
            matrix.categories.forEach { category ->
                val normalizedCategory = category.categoryCode.normalizeCategoryCode()
                category.groups.forEach { group ->
                    group.typeValues.forEach { typeValue ->
                        val normalizedType = typeValue.normalizeTypeValue()
                        require(put(normalizedCategory to normalizedType, category to group) == null) {
                            "Duplicate FASH type surface for $normalizedCategory/$normalizedType."
                        }
                    }
                }
            }
        }
    }

    fun category(categoryCode: String): FashTypeSurfaceCategory? =
        categoriesByCode[categoryCode.normalizeCategoryCode()]

    fun resolve(
        categoryCode: String,
        typeValue: String,
    ): FashResolvedTypeSurface? {
        val normalizedCategory = categoryCode.normalizeCategoryCode()
        val normalizedType = typeValue.normalizeTypeValue()
        val (category, group) = groupsByCategoryAndType[normalizedCategory to normalizedType] ?: return null

        val initialAttributes = group.initialAttributes.ifEmpty { group.primaryAttributes }

        return FashResolvedTypeSurface(
            categoryCode = category.categoryCode,
            typeAttribute = category.typeAttribute,
            typeValue = normalizedType,
            groupCode = group.groupCode,
            initialAttributes = initialAttributes.distinctNormalized(),
            primaryAttributes = group.primaryAttributes.distinctNormalized(),
            secondaryAttributes = group.secondaryAttributes.distinctNormalized(),
            hiddenAttributes = (category.commonHiddenAttributes + group.hiddenAttributes).distinctNormalized(),
            requiredAttributes = (category.commonRequiredAttributes + group.requiredAttributes).distinctNormalized(),
            validatorAttributes = (category.commonValidatorAttributes + group.validatorAttributes).distinctNormalized(),
            noGuessAttributes = (category.commonNoGuessAttributes + group.noGuessAttributes).distinctNormalized(),
        )
    }

    fun allResolvedSurfaces(): List<FashResolvedTypeSurface> =
        matrix.categories.flatMap { category ->
            category.groups.flatMap { group ->
                group.typeValues.mapNotNull { typeValue -> resolve(category.categoryCode, typeValue) }
            }
        }

    private fun String.normalizeCategoryCode(): String =
        trim().uppercase(Locale.ROOT)

    private fun String.normalizeTypeValue(): String =
        trim().uppercase(Locale.ROOT)

    private fun List<String>.distinctNormalized(): List<String> =
        mapNotNull { value ->
            value.trim()
                .lowercase(Locale.ROOT)
                .takeIf { it.isNotEmpty() }
        }.distinct()
}
