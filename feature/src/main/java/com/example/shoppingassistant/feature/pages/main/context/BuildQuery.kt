// Last synced: 2025-12-18 18:11:21
// FILE: feature/.../feature/mainpage/context/BuildQuery.kt
// Last synced: create
package com.example.shoppingassistant.feature.pages.main.context

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.toTypedAttributesGuess
import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.domain.template.TemplateAnchorType
import com.example.shoppingassistant.feature.pages.main.state.UiTemplate
import com.example.shoppingassistant.feature.pages.model.Product

/**
 * Собрать NormalizedQuery из выбранного якоря (обязателен) и атрибутов шаблона.
 * Возвращает null, если якорь не выбран или шаблон невалиден.
 */
fun buildQuery(
    template: UiTemplate,
    product: Product?,
): NormalizedQuery? {
    if (!template.isLocked) return null
    if (template.anchorType == null) return null
    if (template.errorKeys.isNotEmpty()) return null

    val selectedAttrs = template.asSelectedFilters()
        .filterKeys { key ->
            key != "brand" &&
                key != "model" &&
                !key.startsWith("category_level_") &&
                key != "category"
        }
    val normAttrs = Normalization.normalizeAttrs(selectedAttrs).toTypedAttributesGuess()

    val (brand, model) = when (template.anchorType) {
        TemplateAnchorType.PRODUCT -> {
            val prod = product
            if (prod != null) prod.brand to prod.model
            else {
                val heading = template.lockedTitle ?: template.inputText
                val q = BrandModelRules.fromRaw(heading)
                q.brand to q.model
            }
        }
        TemplateAnchorType.CATEGORY -> {
            val b = template.attributes["brand"]?.canonicalValue.orEmpty()
            val m = template.attributes["model"]?.canonicalValue.orEmpty()
            b to m
        }
    }

    if (brand.isBlank() && model.isBlank() && normAttrs.isEmpty()) return null

    return NormalizedQuery(
        brand = brand.trim(),
        model = model.trim(),
        attributes = normAttrs,
    )
}
