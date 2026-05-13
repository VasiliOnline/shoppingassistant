package com.example.shoppingassistant.domain.facet

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class FacetRuntimeFiltersApplierTest {
    @Test
    fun range_preset_rules_become_typed_attribute_filters_when_definition_is_available() {
        val preset = CatalogSeed.facetPresets.first { it.presetCode == "FP.TECH.PHONES.PREMIUM" }

        val applied = FacetRuntimeFiltersApplier.apply(
            base = FacetRuntimeFilters(),
            collection = null,
            preset = preset,
            definitions = CatalogSeed.facetDefinitions,
        )

        val memoryFilter = applied.attributeFilters["memory_gb"]
        val ramFilter = applied.attributeFilters["ram_gb"]

        assertNotNull("Expected typed filter for memory_gb", memoryFilter)
        assertNotNull("Expected typed filter for ram_gb", ramFilter)
        assertEquals(TypedAttributeOperator.GTE, memoryFilter?.op)
        assertEquals(TypedAttributeValue.Number(256.0), memoryFilter?.value)
        assertEquals(TypedAttributeOperator.GTE, ramFilter?.op)
        assertEquals(TypedAttributeValue.Number(8.0), ramFilter?.value)
        assertFalse("Range filters should not fall back to legacy raw attributes", applied.attributes.containsKey("memory_gb"))
        assertFalse("Range filters should not fall back to legacy raw attributes", applied.attributes.containsKey("ram_gb"))
    }

    @Test
    fun enum_preset_rules_become_typed_attribute_filters_when_definition_is_available() {
        val collection = CatalogSeed.facetCollections.first { it.collectionCode == "B.FOOD.READY.05" }
        val preset = CatalogSeed.facetPresets.first { it.presetCode == collection.presetCode }

        val applied = FacetRuntimeFiltersApplier.apply(
            base = FacetRuntimeFilters(),
            collection = collection,
            preset = preset,
            definitions = CatalogSeed.facetDefinitions,
        )

        val cuisineFilter = applied.attributeFilters["cuisine"]
        assertNotNull("Expected typed filter for cuisine", cuisineFilter)
        assertEquals(TypedAttributeOperator.EQ, cuisineFilter?.op)
        assertEquals(TypedAttributeValue.Text("пицца"), cuisineFilter?.value)
        assertFalse("Definition-backed enum facets should use typed filters instead of raw preset attributes", applied.attributes.containsKey("cuisine"))
    }
}
