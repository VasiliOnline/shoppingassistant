// Last synced: 2025-11-18 17:28
package com.example.shoppingassistant.core.data

import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistry
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery

object BrandModelRules {

    fun fromRaw(raw: String, attrs: Map<String, String> = emptyMap()): NormalizedQuery {
        val s = raw.trim().replace("\\s+".toRegex(), " ")
        val knownFamily = fromKnownFamily(raw = s, attrs = attrs)
        if (knownFamily != null) return knownFamily

        val (brand, model) = splitByFirstSpace(s)

        return NormalizedQuery(
            brand = brand.trim(),
            model = model.trim(),
            attributes = Normalization.normalizeTypedAttrs(attrs),
        )
    }

    fun fromKnownFamily(raw: String, attrs: Map<String, String> = emptyMap()): NormalizedQuery? {
        val s = raw.trim().replace("\\s+".toRegex(), " ")
        val knownModel = CatalogCanonicalModelRegistry.matchQuery(s)
        if (knownModel != null) {
            return NormalizedQuery(
                brand = knownModel.brandCanonical,
                model = knownModel.modelText,
                attributes = Normalization.normalizeTypedAttrs(attrs),
            )
        }
        val family = CatalogCanonicalProductFamilyRegistry.matchQuery(s) ?: return null
        return NormalizedQuery(
            brand = family.brandCanonical,
            model = family.modelText,
            attributes = Normalization.normalizeTypedAttrs(attrs),
        )
    }

    private fun splitByFirstSpace(s: String): Pair<String, String> {
        val i = s.indexOf(' ')
        return if (i > 0) s.substring(0, i) to s.substring(i + 1) else s to ""
    }
}
