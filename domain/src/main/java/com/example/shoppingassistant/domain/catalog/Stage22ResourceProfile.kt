package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.i18n.localizedTextOf
import kotlinx.serialization.Serializable

@Serializable
internal data class Stage22SeedCategoryRef(
    val code: String,
    val segment: CategorySegment,
    val title: String? = null,
    val titleRu: String? = null,
    val titleEn: String? = null,
    val parentCode: String? = null,
    val description: String? = null,
    val status: CategoryStatus = CategoryStatus.ACTIVE,
    val replacementCode: String? = null,
) {
    fun toCategory(): Category = Category(
        code = code,
        segment = segment,
        title = localizedTextOf(
            "ru" to (titleRu?.trim()?.takeIf { it.isNotEmpty() }
                ?: title?.trim()?.takeIf { it.isNotEmpty() }),
            "en" to titleEn?.trim()?.takeIf { it.isNotEmpty() },
        ),
        parentCode = parentCode?.trim()?.takeIf { it.isNotEmpty() },
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        status = status,
        replacementCode = replacementCode?.trim()?.takeIf { it.isNotEmpty() },
    )
}

@Serializable
internal data class Stage22ResourceProfile(
    val category: Stage22SeedCategoryRef,
    val attributes: List<AttributeDef> = emptyList(),
    val categoryAttributes: List<CategoryAttribute> = emptyList(),
    val valueDictionaries: List<AttributeValueDict> = emptyList(),
    val requiredIfRules: List<RequiredIfRule> = emptyList(),
    val extendsProfiles: List<String> = emptyList(),
) {
    fun toCategoryWriteSpec(): CatalogCategoryWriteSpec = CatalogCategoryWriteSpec(
        category = category.toCategory(),
        attributes = attributes,
        categoryAttributes = categoryAttributes,
        valueDictionaries = valueDictionaries,
        requiredIfRules = requiredIfRules,
    )
}

@Serializable
internal data class Stage22SharedResourceProfile(
    val profileCode: String,
    val attributes: List<AttributeDef> = emptyList(),
    val categoryAttributes: List<CategoryAttribute> = emptyList(),
    val valueDictionaries: List<AttributeValueDict> = emptyList(),
    val requiredIfRules: List<RequiredIfRule> = emptyList(),
)

internal fun CatalogCategoryWriteSpec.toStage22ResourceProfile(): Stage22ResourceProfile =
    Stage22ResourceProfile(
        category = Stage22SeedCategoryRef(
            code = category.code,
            segment = category.segment,
            title = category.title.resolve(locale = "ru"),
            titleRu = category.title["ru"],
            titleEn = category.title["en"],
            parentCode = category.parentCode,
            description = category.description,
            status = category.status,
            replacementCode = category.replacementCode,
        ),
        attributes = attributes,
        categoryAttributes = categoryAttributes,
        valueDictionaries = valueDictionaries,
        requiredIfRules = requiredIfRules,
        extendsProfiles = emptyList(),
    )
