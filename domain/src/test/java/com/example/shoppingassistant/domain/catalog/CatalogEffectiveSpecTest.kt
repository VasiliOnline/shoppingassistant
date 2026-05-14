package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEffectiveSpecTest {
    private val repository = SeedBackedCatalogRepository()

    @Test
    fun effective_spec_exposes_stage4_semantics_and_required_if_rules() = runBlocking {
        val spec = repository.getCategoryEffectiveSpec("TECH.PHONES")

        assertNotNull(spec)
        spec ?: return@runBlocking

        assertEquals(CatalogCategoryReadiness.READY, spec.readiness)
        assertTrue(spec.meta.identityAttributeCodes.contains("brand"))
        assertTrue(spec.meta.identityAttributeCodes.contains("model"))
        assertTrue(spec.meta.identityAttributeCodes.contains("model_line"))
        assertTrue(spec.attributes.any { it.code == "model_line" && it.facetEnabled })
        assertTrue(spec.attributes.any { it.code == "release_date" && !it.facetEnabled })
        assertTrue(
            "TECH.PHONES should expose requiredIf rules in the effective spec.",
            spec.requiredIfRules.any { it.requiredAttributeCode == "battery_health_percent" },
        )

        val condition = spec.systemAttributes.firstOrNull { it.code == "condition" }
        assertNotNull(condition)
        condition ?: return@runBlocking
        assertEquals(Stage22ValueSetType.CLOSED, condition.valueSetType)
        assertTrue(condition.dictionaryRequired)
        assertTrue(condition.requiredForOffer)
        assertTrue(condition.requiredForExpress)
        assertTrue(
            "Dictionary-backed condition should be rendered via a selection widget.",
            condition.widgetHint == CatalogAttributeWidgetHint.CHIPS ||
                condition.widgetHint == CatalogAttributeWidgetHint.DROPDOWN,
        )
    }

    @Test
    fun effective_spec_carries_scoped_constraints_for_brand_model_context() = runBlocking {
        val spec = repository.getCategoryEffectiveSpec(
            categoryCode = "APPL.SMALL",
            brand = "DeLonghi",
            model = "Magnifica S",
        )

        assertNotNull(spec)
        spec ?: return@runBlocking

        assertTrue(spec.constraints.any { it.scope == ConstraintScope.CATEGORY })
        assertTrue(spec.constraints.any { it.scope == ConstraintScope.MODEL })
    }

    @Test
    fun effective_spec_reuses_canonical_category_titles_from_seed() = runBlocking {
        val spec = repository.getCategoryEffectiveSpec("TECH.PHONES")

        assertNotNull(spec)
        spec ?: return@runBlocking

        assertEquals("Телефоны", spec.category.title["ru"])
        assertEquals("Phones", spec.category.title["en"])
    }

    @Test
    fun effective_spec_exposes_phone_refresh_rate_and_chipset_with_curated_options() = runBlocking {
        val spec = repository.getCategoryEffectiveSpec("TECH.PHONES")

        assertNotNull(spec)
        spec ?: return@runBlocking

        val refreshRate = spec.attributes.firstOrNull { it.code == "refresh_rate_hz" }
        val chipset = spec.attributes.firstOrNull { it.code == "chipset_family" }
        val modelLine = spec.attributes.firstOrNull { it.code == "model_line" }
        val releaseDate = spec.attributes.firstOrNull { it.code == "release_date" }
        val networkType = spec.attributes.firstOrNull { it.code == "network_type" }

        assertNotNull(refreshRate)
        assertNotNull(chipset)
        assertNotNull(modelLine)
        assertNotNull(releaseDate)
        assertNotNull(networkType)
        refreshRate ?: return@runBlocking
        chipset ?: return@runBlocking
        modelLine ?: return@runBlocking
        releaseDate ?: return@runBlocking
        networkType ?: return@runBlocking

        assertEquals(AttributeDataType.INT, refreshRate.dataType)
        assertEquals(Stage22ValueType.NUMBER, refreshRate.valueType)
        assertEquals(Stage22ValueSetType.OPEN, refreshRate.valueSetType)
        assertTrue(refreshRate.facetEnabled)
        assertEquals("hz", refreshRate.unit)
        assertTrue(refreshRate.options.any { it.valueCode == "120" })

        assertEquals(AttributeDataType.STRING, chipset.dataType)
        assertEquals(Stage22ValueType.STRING, chipset.valueType)
        assertEquals(Stage22ValueSetType.OPEN, chipset.valueSetType)
        assertTrue(chipset.facetEnabled)
        assertTrue(chipset.options.any { it.valueCode == "SNAPDRAGON_8_GEN_3" })
        assertTrue(chipset.options.any { option -> option.aliases.any { it.contains("тензор g4") } })

        assertEquals(AttributeDataType.STRING, modelLine.dataType)
        assertTrue(modelLine.facetEnabled)
        assertTrue(modelLine.options.any { it.valueCode == "IPHONE_16" })
        assertTrue(modelLine.options.any { option -> "iphone16" in option.aliases })

        assertEquals(AttributeDataType.STRING, releaseDate.dataType)
        assertFalse(releaseDate.facetEnabled)

        assertEquals(AttributeDataType.ENUM, networkType.dataType)
        assertTrue(networkType.facetEnabled)
        assertTrue(networkType.options.any { it.valueCode == "5G" })
        assertTrue(networkType.options.any { option -> "5 джи" in option.aliases })
    }

    @Test
    fun effective_spec_projects_curated_phone_brand_and_model_constraints() = runBlocking {
        val spec = repository.getCategoryEffectiveSpec(
            categoryCode = "TECH.PHONES",
            brand = "Nothing",
            model = "Nothing Phone (2a)",
        )

        assertNotNull(spec)
        spec ?: return@runBlocking

        assertEquals(
            listOf(ConstraintScope.CATEGORY, ConstraintScope.BRAND, ConstraintScope.MODEL),
            spec.constraints.map { it.scope },
        )

        val brandConstraint = spec.constraints.firstOrNull { it.scope == ConstraintScope.BRAND }
        assertNotNull(brandConstraint)
        assertEquals("Nothing", brandConstraint?.brand)
        assertEquals(
            listOf("ANDROID"),
            brandConstraint?.attributeConstraints?.firstOrNull { it.attributeCode == "os_family" }?.allowedValues,
        )
        assertTrue(
            brandConstraint?.attributeConstraints
                ?.firstOrNull { it.attributeCode == "model_line" }
                ?.allowedValues
                ?.contains("Nothing Phone 2a") == true,
        )

        val modelConstraint = spec.constraints.firstOrNull { it.scope == ConstraintScope.MODEL }
        assertNotNull(modelConstraint)
        assertEquals("Nothing", modelConstraint?.brand)
        assertEquals("Nothing Phone (2a)", modelConstraint?.model)
        val modelRules = modelConstraint?.attributeConstraints.orEmpty().associateBy { it.attributeCode }

        assertEquals(listOf("2024"), modelRules["release_year"]?.allowedValues)
        assertEquals(listOf("Nothing Phone 2a"), modelRules["model_line"]?.allowedValues)
        assertEquals(listOf("128", "256"), modelRules["memory_gb"]?.allowedValues)
        assertEquals(listOf("8", "12"), modelRules["ram_gb"]?.allowedValues)
        assertEquals(listOf("BLACK", "WHITE"), modelRules["color"]?.allowedValues)
        assertEquals(listOf("Dimensity 7200 Pro"), modelRules["chipset_family"]?.allowedValues)
    }

    @Test
    fun effective_spec_includes_system_offer_attributes_without_ui_fallbacks() = runBlocking {
        val spec = repository.getCategoryEffectiveSpec("TECH.PHONES")

        assertNotNull(spec)
        spec ?: return@runBlocking

        val price = spec.systemAttributes.firstOrNull { it.code == "price" }
        val currency = spec.systemAttributes.firstOrNull { it.code == "currency" }
        val condition = spec.systemAttributes.firstOrNull { it.code == "condition" }

        assertNotNull(price)
        assertNotNull(currency)
        assertNotNull(condition)
        assertTrue(spec.attributes.none { attribute -> attribute.code in setOf("price", "currency", "condition") })
        assertTrue(price?.requiredForOffer == true)
        assertTrue(price?.requiredForExpress == true)
        assertTrue(currency?.requiredForOffer == true)
        assertTrue(currency?.requiredForExpress == true)
        assertTrue(condition?.requiredForOffer == true)
        assertTrue(condition?.requiredForExpress == true)
        assertEquals(Stage22ValueSetType.CLOSED, currency?.valueSetType)
        assertTrue(currency?.options?.map { it.valueCode } == listOf("RUB", "USD", "EUR"))
    }

    @Test
    fun active_leaf_categories_must_pass_effective_spec_completeness_gate() = runBlocking {
        val leafCodes = leafCategoryCodes(CatalogSeed.categories)
        val failures = leafCodes
            .mapNotNull { code ->
                val spec = repository.getCategoryEffectiveSpec(code)
                when {
                    spec == null -> "$code:missing_spec"
                    spec.readiness != CatalogCategoryReadiness.READY ->
                        "$code:${spec.meta.readinessBlockingIssues.joinToString("|")}"
                    !spec.meta.completenessGatePassed ->
                        "$code:gate_flag_false"
                    else -> null
                }
            }

        assertTrue(
            "Active leaf categories must be release-ready in effective spec: ${failures.take(10)}",
            failures.isEmpty(),
        )
    }
}

private class SeedBackedCatalogRepository : CatalogReadRepository {
    private val specsByCode = CatalogSeed.categoryWriteSpecs.associateBy { it.category.code }

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val spec = specsByCode[categoryCode.trim().uppercase()] ?: return null
        val constraints = resolveConstraints(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
        return spec.toCategoryEffectiveSpec(constraints = constraints)
    }

    private fun resolveConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        val normalizedCategoryCode = categoryCode.trim()
        if (normalizedCategoryCode.isBlank()) return emptyList()
        return CatalogSeed.constraints.filter { constraint ->
            when (constraint.scope) {
                ConstraintScope.GLOBAL -> true
                ConstraintScope.CATEGORY ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true
                ConstraintScope.BRAND ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand.orEmpty(), ignoreCase = true) == true
                ConstraintScope.MODEL ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand.orEmpty(), ignoreCase = true) == true &&
                        constraint.model?.equals(model.orEmpty(), ignoreCase = true) == true
            }
        }
    }
}

private fun leafCategoryCodes(categories: List<Category>): List<String> {
    val parentCodes = categories
        .mapNotNull { category -> category.parentCode?.trim()?.takeIf(String::isNotBlank) }
        .toSet()
    return categories
        .asSequence()
        .filter { category ->
            category.status == CategoryStatus.ACTIVE &&
                category.code.trim().isNotEmpty() &&
                category.code !in parentCodes
        }
        .map { category -> category.code }
        .sorted()
        .toList()
}

