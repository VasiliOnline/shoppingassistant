package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogCanonicalProductFamilyRegistryTest {

    @Test
    fun matches_cyrillic_iphone_product_query_to_phone_family() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("айфон 16 про серый 256гб")

        requireNotNull(match)
        assertEquals("TECH.PHONES", match.defaultCategoryCode)
        assertEquals("Apple", match.brandCanonical)
        assertEquals("iPhone 16 про серый 256гб", match.modelText)
    }

    @Test
    fun matches_brand_plus_family_prefix_for_english_query() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("apple iphone 16 pro 256gb")

        requireNotNull(match)
        assertEquals("TECH.PHONES", match.defaultCategoryCode)
        assertEquals("Apple", match.brandCanonical)
        assertEquals("iPhone 16 pro 256gb", match.modelText)
    }

    @Test
    fun matches_compact_family_query_without_curated_model() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("iphone17pro256gb")

        requireNotNull(match)
        assertEquals("TECH.PHONES", match.defaultCategoryCode)
        assertEquals("Apple", match.brandCanonical)
        assertEquals("iPhone 17 pro 256 gb", match.modelText)
    }

    @Test
    fun matches_real_phone_family_alias_from_internal_signals() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("pixel phone")

        requireNotNull(match)
        assertEquals("GOOGLE_PIXEL", match.familyCode)
        assertEquals("TECH.PHONES", match.defaultCategoryCode)
        assertEquals("Google", match.brandCanonical)
        assertEquals("Pixel", match.modelText)
    }

    @Test
    fun prefers_more_specific_redmi_note_family_over_generic_redmi_family() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("redmi note 13 pro plus")

        requireNotNull(match)
        assertEquals("XIAOMI_REDMI_NOTE", match.familyCode)
        assertEquals("TECH.PHONES", match.defaultCategoryCode)
        assertEquals("Xiaomi", match.brandCanonical)
        assertEquals("Redmi Note 13 pro plus", match.modelText)
    }

    @Test
    fun matches_curated_cyrillic_redmi_phone_alias_from_internal_queries() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("xiaomi redmi смартфон")

        requireNotNull(match)
        assertEquals("XIAOMI_REDMI", match.familyCode)
        assertEquals("TECH.PHONES", match.defaultCategoryCode)
        assertEquals("Xiaomi", match.brandCanonical)
        assertEquals("Redmi", match.modelText)
    }

    @Test
    fun ignores_accessory_queries_even_if_family_is_present() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("iphone case 16")

        assertNull(match)
    }

    @Test
    fun ignores_compact_accessory_queries_even_if_family_is_present() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("iphone16procase")

        assertNull(match)
    }

    @Test
    fun matches_macbook_air_query_to_laptop_family() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("макбук air m3 13")

        requireNotNull(match)
        assertEquals("TECH.COMPUTERS", match.defaultCategoryCode)
        assertEquals("Apple", match.brandCanonical)
        assertEquals("MacBook Air m3 13", match.modelText)
    }

    @Test
    fun matches_kindle_query_to_ereader_family() {
        val match = CatalogCanonicalProductFamilyRegistry.matchQuery("kindle paperwhite 2024")

        requireNotNull(match)
        assertEquals("TECH.TABLETS_E_READERS", match.defaultCategoryCode)
        assertEquals("Amazon", match.brandCanonical)
        assertEquals("Kindle paperwhite 2024", match.modelText)
    }
}
