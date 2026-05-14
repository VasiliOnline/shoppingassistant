package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacetDefinitionFashSeedMergeTest {

    @Test
    fun seed_fash_definition_overrides_stale_remote_fash_binding() {
        val remoteDefinitions = listOf(
            definition(
                facetKey = "material",
                appliesTo = listOf("FASH.MEN", "HOME.FURNITURE"),
            ),
            definition(
                facetKey = "brand",
                appliesTo = listOf("FASH.MEN"),
            ),
        )
        val seedDefinitions = listOf(
            definition(
                facetKey = "material",
                appliesTo = listOf("HOME.FURNITURE"),
            ),
            definition(
                facetKey = "apparel_type",
                appliesTo = listOf("FASH.MEN", "FASH.WOMEN", "FASH.KIDS"),
            ),
            definition(
                facetKey = "size_label",
                appliesTo = listOf("FASH.MEN", "FASH.WOMEN", "FASH.KIDS"),
            ),
        )

        val merged = mergeFacetDefinitionsWithFashSeed(
            remoteDefinitions = remoteDefinitions,
            seedDefinitions = seedDefinitions,
        )
        val material = merged.first { it.facetKey == "material" }

        assertFalse(material.appliesToCategoryCodes.any { it == "FASH.MEN" })
        assertTrue(merged.any { it.facetKey == "apparel_type" && "FASH.MEN" in it.appliesToCategoryCodes })
        assertTrue(merged.any { it.facetKey == "size_label" && "FASH.MEN" in it.appliesToCategoryCodes })
    }

    private fun definition(
        facetKey: String,
        appliesTo: List<String>,
    ): FacetDefinition = FacetDefinition(
        facetKey = facetKey,
        title = localizedTextOf("ru" to facetKey),
        valueType = FacetDataType.ENUM,
        appliesToCategoryCodes = appliesTo,
        attributeCode = facetKey,
    )
}
