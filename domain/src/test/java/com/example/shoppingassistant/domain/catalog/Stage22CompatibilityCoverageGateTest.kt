package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage22CompatibilityCoverageGateTest {
    @Test
    fun every_leaf_category_must_have_category_level_compatibility_rule() {
        val packages = GenericStage22PackageLoader.loadAll()
        val coverage = packages
            .asSequence()
            .flatMap { packageData ->
                packageData.constraints
                    .asSequence()
                    .filter { constraint -> constraint.scope == ConstraintScope.CATEGORY }
                    .filter { constraint -> constraint.compatibilityRules.isNotEmpty() }
                    .mapNotNull { constraint -> constraint.categoryCode?.trim()?.takeIf { code -> code.isNotEmpty() } }
            }
            .toSet()

        val leafCategories = leafCategoryCodes(CatalogSeed.categories)
        val missingCoverage = (leafCategories - coverage).sorted()

        assertTrue(
            "Each leaf category must have at least one CATEGORY compatibility rule. Missing: " +
                missingCoverage.joinToString(),
            missingCoverage.isEmpty(),
        )
    }

    private fun leafCategoryCodes(categories: List<Category>): Set<String> {
        val categoryCodes = categories
            .asSequence()
            .map { category -> category.code.trim() }
            .filter { code -> code.isNotEmpty() }
            .toSet()
        val parentCodes = categories
            .asSequence()
            .mapNotNull { category -> category.parentCode?.trim()?.takeIf { parent -> parent.isNotEmpty() } }
            .toSet()
        return categoryCodes - parentCodes
    }
}
