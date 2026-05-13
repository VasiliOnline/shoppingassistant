package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSystemAttributeRegistryLoaderTest {
    @Test
    fun system_attribute_registry_must_expose_data_owned_defaults() {
        val registry = CatalogSystemAttributeRegistryLoader.load()

        val currency = registry.attributesByCode["currency"]
        val condition = registry.attributesByCode["condition"]

        assertEquals(setOf("price", "currency", "condition"), registry.attributesByCode.keys)
        assertTrue("price should be ignored in offer-signal readiness gate", "price" in registry.ignoredForOfferSignalCodes)
        assertTrue("currency should be ignored in offer-signal readiness gate", "currency" in registry.ignoredForOfferSignalCodes)
        assertEquals(Stage22ValueSetType.CLOSED, currency?.valueSetType)
        assertEquals(listOf("RUB", "USD", "EUR"), currency?.options?.map { it.valueCode })
        assertTrue(condition?.isFacet == true)
        assertTrue(condition?.dictionaryRequired == true)
    }
}
