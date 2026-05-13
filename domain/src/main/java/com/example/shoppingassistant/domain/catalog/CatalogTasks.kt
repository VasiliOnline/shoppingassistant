package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.SellerType
import com.example.shoppingassistant.domain.i18n.LocalizedText
import kotlinx.serialization.Serializable

@Serializable
enum class CategorySegment {
    TECH,
    APPL,
    HOME,
    FASH,
    BEAUTY,
    KIDS,
    FOOD,
    PETS,
    SPORT,
    AUTO,
    OTHER,
}

@Serializable
enum class CategoryStatus {
    ACTIVE,
    DEPRECATED,
    HIDDEN,
}

@Serializable
data class Category(
    val code: String,               // "TECH.PHONES"
    val segment: CategorySegment,   // TECH / FOOD / ...
    val title: LocalizedText = LocalizedText.Empty,
    val parentCode: String? = null, // Иерархия при необходимости
    val description: String? = null,
    val status: CategoryStatus = CategoryStatus.ACTIVE,
    val replacementCode: String? = null, // Каноническая замена для DEPRECATED-ветки
)

@Serializable
enum class AttributeDataType {
    STRING,
    INT,
    DECIMAL,
    BOOL,
    ENUM,
    STRING_LIST,
}

@Serializable
data class AttributeDef(
    val code: String,
    val title: String,
    val dataType: AttributeDataType,
    val requiredForSearch: Boolean = false,
    val requiredForOffer: Boolean = false,
    val requiredForExpress: Boolean = false,
    val requiredBy: String? = null, // ISO date (yyyy-MM-dd) for policy rollout deadline
    val facetEnabled: Boolean = false,
    val multiValued: Boolean = false,
    val valueDictCode: String? = null,
)

@Serializable
data class CategoryAttribute(
    val categoryCode: String,
    val attributeCode: String,
    val uiOrder: Int = 0,
    val isRequiredForCategory: Boolean = false,
)

@Serializable
data class AttributeValueDictEntry(
    val canonicalCode: String,
    val canonicalValue: String,
    val synonyms: List<String> = emptyList(),
    val rank: Int = 0,
)

@Serializable
data class AttributeValueDict(
    val attributeCode: String,
    val entries: List<AttributeValueDictEntry>,
    val code: String? = null,
)

/**
 * Условная обязательность атрибутов: requiredIf(...)
 * Пример: required "model_number" if brand=Apple AND family=iPhone.
 */
@Serializable
enum class AttributeConditionOp {
    EQUALS_ANY,
    STARTS_WITH_ANY,
}

@Serializable
data class AttributeCondition(
    val attributeCode: String,
    val op: AttributeConditionOp = AttributeConditionOp.EQUALS_ANY,
    val values: List<String>,
)

@Serializable
data class RequiredIfRule(
    val requiredAttributeCode: String,
    val whenAll: List<AttributeCondition>,
)

@Serializable
data class CatalogCategoryWriteSpec(
    val category: Category,
    val attributes: List<AttributeDef>,
    val categoryAttributes: List<CategoryAttribute>,
    val valueDictionaries: List<AttributeValueDict> = emptyList(),
    val requiredIfRules: List<RequiredIfRule> = emptyList(),
)

@Serializable
data class AttributeValue(
    val raw: String,
    val dictCode: String? = null,   // код справочника/enum
    val valueCode: String? = null,  // canonicalCode из AttributeValueDict
)

@Serializable
data class ProductTemplate(
    val id: String? = null,
    val categoryCode: String,
    val title: String,
    val brand: String? = null,
    val model: String? = null,
    val attributes: Map<String, AttributeValue> = emptyMap(),
)

@Serializable
data class CatalogOffer(
    val id: String? = null,
    val productId: String? = null,
    val categoryCode: String,
    val price: Money,
    val currency: String,
    val location: String? = null,
    val origin: OfferOrigin = OfferOrigin.LINK,
    val sellerType: SellerType? = null,
    val externalUrl: String? = null,
    val attributes: Map<String, AttributeValue> = emptyMap(),
)

@Serializable
enum class OfferOrigin {
    LINK,
    EXPRESS,
    SCRAPED,
}

/**
 * Product-read контракт каталога.
 * Product/UI потребители читают категорийную runtime-семантику только через effective spec.
 */
interface CatalogReadRepository {
    suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String? = null,
        model: String? = null,
    ): CatalogCategoryEffectiveSpec?
}

/**
 * Taxonomy-read контракт каталога.
 * Отдельно от product effective spec отдаёт навигационное дерево и redirect resolution.
 */
interface CatalogTaxonomyRepository {
    suspend fun listCategories(): List<Category>

    suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int = 32,
    ): CategoryRedirectResolution?
}

interface CatalogWriteRepository {
    suspend fun upsertCategorySpec(spec: CatalogCategoryWriteSpec)
}
