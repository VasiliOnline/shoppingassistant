package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogConstraintsSelectionTest {

    @Test
    fun select_returnsGlobalAndCategory_whenNoBrandModel() {
        val selected = CatalogConstraintsSelection.select(
            constraints = sampleConstraints(),
            categoryCode = "TECH.PHONES",
            brand = null,
            model = null,
        )

        assertEquals(
            listOf(ConstraintScope.GLOBAL, ConstraintScope.CATEGORY),
            selected.map { it.scope },
        )
    }

    @Test
    fun select_returnsBrand_whenBrandProvided() {
        val selected = CatalogConstraintsSelection.select(
            constraints = sampleConstraints(),
            categoryCode = "TECH.PHONES",
            brand = "apple",
            model = null,
        )

        assertEquals(
            listOf(ConstraintScope.GLOBAL, ConstraintScope.CATEGORY, ConstraintScope.BRAND),
            selected.map { it.scope },
        )
    }

    @Test
    fun select_returnsModel_whenBrandAndModelProvided() {
        val selected = CatalogConstraintsSelection.select(
            constraints = sampleConstraints(),
            categoryCode = "TECH.PHONES",
            brand = "Apple",
            model = "iPhone 16 Pro",
        )

        assertEquals(
            listOf(ConstraintScope.GLOBAL, ConstraintScope.CATEGORY, ConstraintScope.BRAND, ConstraintScope.MODEL),
            selected.map { it.scope },
        )
    }

    @Test
    fun select_excludesBrandAndModel_whenBrandDoesNotMatch() {
        val selected = CatalogConstraintsSelection.select(
            constraints = sampleConstraints(),
            categoryCode = "TECH.PHONES",
            brand = "Samsung",
            model = "Galaxy S24",
        )

        assertEquals(
            listOf(ConstraintScope.GLOBAL, ConstraintScope.CATEGORY),
            selected.map { it.scope },
        )
    }

    @Test
    fun select_excludes_constraints_outside_effective_window() {
        val selected = CatalogConstraintsSelection.select(
            constraints = listOf(
                CatalogConstraints(
                    scope = ConstraintScope.GLOBAL,
                    effectiveFrom = "2099-01-01",
                ),
                CatalogConstraints(
                    scope = ConstraintScope.CATEGORY,
                    categoryCode = "TECH.PHONES",
                    effectiveTo = "2020-01-01",
                ),
                CatalogConstraints(
                    scope = ConstraintScope.CATEGORY,
                    categoryCode = "TECH.PHONES",
                    effectiveFrom = "2020-01-01",
                    effectiveTo = "2099-01-01",
                ),
            ),
            categoryCode = "TECH.PHONES",
            brand = null,
            model = null,
            onDate = LocalDate.parse("2026-02-19"),
        )

        assertEquals(1, selected.size)
        assertEquals(ConstraintScope.CATEGORY, selected.single().scope)
    }

    private fun sampleConstraints(): List<CatalogConstraints> = listOf(
        CatalogConstraints(
            scope = ConstraintScope.GLOBAL,
            attributeConstraints = listOf(
                AttributeValueConstraint(
                    attributeCode = "condition",
                    allowedValues = listOf("NEW", "USED"),
                ),
            ),
        ),
        CatalogConstraints(
            scope = ConstraintScope.CATEGORY,
            categoryCode = "TECH.PHONES",
            compatibilityRules = listOf(
                CompatibilityRule(
                    whenAll = listOf(
                        AttributeCondition(
                            attributeCode = "brand",
                            op = AttributeConditionOp.EQUALS_ANY,
                            values = listOf("Apple"),
                        ),
                    ),
                    apply = listOf(
                        AttributeValueConstraint(
                            attributeCode = "os_family",
                            allowedValues = listOf("IOS"),
                        ),
                    ),
                ),
            ),
        ),
        CatalogConstraints(
            scope = ConstraintScope.BRAND,
            categoryCode = "TECH.PHONES",
            brand = "Apple",
            attributeConstraints = listOf(
                AttributeValueConstraint(
                    attributeCode = "repairability",
                    forbiddenValues = listOf("LOW"),
                ),
            ),
        ),
        CatalogConstraints(
            scope = ConstraintScope.MODEL,
            categoryCode = "TECH.PHONES",
            brand = "Apple",
            model = "iPhone 16 Pro",
            attributeConstraints = listOf(
                AttributeValueConstraint(
                    attributeCode = "memory_gb",
                    allowedValues = listOf("256", "512", "1024"),
                ),
            ),
        ),
    )
}
