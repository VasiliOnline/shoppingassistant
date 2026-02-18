package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope

internal object CatalogConstraintsSelection {
    fun select(
        constraints: List<CatalogConstraints>,
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        val normalizedCategory = categoryCode.trim().uppercase()
        if (normalizedCategory.isBlank()) return emptyList()

        val normalizedBrand = brand?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedModel = model?.trim()?.takeIf { it.isNotEmpty() }

        return constraints
            .asSequence()
            .filter { constraint ->
                isApplicable(
                    constraint = constraint,
                    categoryCode = normalizedCategory,
                    brand = normalizedBrand,
                    model = normalizedModel,
                )
            }
            .sortedWith(
                compareBy<CatalogConstraints> { scopePriority(it.scope) }
                    .thenBy { it.categoryCode.orEmpty() }
                    .thenBy { it.brand.orEmpty() }
                    .thenBy { it.model.orEmpty() },
            )
            .toList()
    }

    private fun isApplicable(
        constraint: CatalogConstraints,
        categoryCode: String,
        brand: String?,
        model: String?,
    ): Boolean {
        val constraintCategory = constraint.categoryCode?.trim()?.uppercase()
        val constraintBrand = constraint.brand?.trim()
        val constraintModel = constraint.model?.trim()

        return when (constraint.scope) {
            ConstraintScope.GLOBAL -> true
            ConstraintScope.CATEGORY -> constraintCategory == categoryCode
            ConstraintScope.BRAND -> {
                constraintCategory == categoryCode &&
                    !brand.isNullOrBlank() &&
                    !constraintBrand.isNullOrBlank() &&
                    constraintBrand.equals(brand, ignoreCase = true)
            }

            ConstraintScope.MODEL -> {
                constraintCategory == categoryCode &&
                    !brand.isNullOrBlank() &&
                    !model.isNullOrBlank() &&
                    !constraintBrand.isNullOrBlank() &&
                    !constraintModel.isNullOrBlank() &&
                    constraintBrand.equals(brand, ignoreCase = true) &&
                    constraintModel.equals(model, ignoreCase = true)
            }
        }
    }

    private fun scopePriority(scope: ConstraintScope): Int = when (scope) {
        ConstraintScope.GLOBAL -> 0
        ConstraintScope.CATEGORY -> 1
        ConstraintScope.BRAND -> 2
        ConstraintScope.MODEL -> 3
    }
}
