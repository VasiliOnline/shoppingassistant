package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogCanonicalModelRegistryTest {

    @Test
    fun matches_short_samsung_model_alias_to_phone_category() {
        val match = CatalogCanonicalModelRegistry.matchQuery("s24 ultra 256gb")

        assertNotNull(match)
        assertEquals("GALAXY_S24_ULTRA", match?.modelCode)
        assertEquals("TECH.PHONES", match?.defaultCategoryCode)
        assertEquals("Samsung", match?.brandCanonical)
        assertEquals("Galaxy S24 Ultra 256gb", match?.modelText)
    }

    @Test
    fun matches_compact_short_samsung_alias_with_russian_suffixes_to_ultra_model() {
        val match = CatalogCanonicalModelRegistry.matchQuery("s24u 120гц снап 8 ген 3 256гб")

        assertNotNull(match)
        assertEquals("GALAXY_S24_ULTRA", match?.modelCode)
        assertEquals("Samsung", match?.brandCanonical)
        assertEquals("Galaxy S24 Ultra 120гц снап 8 ген 3 256гб", match?.modelText)
    }

    @Test
    fun matches_compact_iphone_pm_alias_to_phone_category() {
        val match = CatalogCanonicalModelRegistry.matchQuery("iphone16pm256gb")

        assertNotNull(match)
        assertEquals("IPHONE_16_PRO_MAX", match?.modelCode)
        assertEquals("TECH.PHONES", match?.defaultCategoryCode)
        assertEquals("Apple", match?.brandCanonical)
        assertEquals("iPhone 16 Pro Max 256 gb", match?.modelText)
    }

    @Test
    fun matches_compact_iphone_17_pro_alias_to_phone_category() {
        val match = CatalogCanonicalModelRegistry.matchQuery("iphone17pro256gb")

        assertNotNull(match)
        assertEquals("IPHONE_17_PRO", match?.modelCode)
        assertEquals("TECH.PHONES", match?.defaultCategoryCode)
        assertEquals("Apple", match?.brandCanonical)
        assertEquals("iPhone 17 Pro 256 gb", match?.modelText)
    }

    @Test
    fun matches_short_samsung_ultra_alias_without_space() {
        val match = CatalogCanonicalModelRegistry.matchQuery("s24u256gb")

        assertNotNull(match)
        assertEquals("GALAXY_S24_ULTRA", match?.modelCode)
        assertEquals("Galaxy S24 Ultra 256 gb", match?.modelText)
    }

    @Test
    fun matches_compact_pixel_7_pro_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("pixel7pro128gb")

        assertNotNull(match)
        assertEquals("PIXEL_7_PRO", match?.modelCode)
        assertEquals("Google", match?.brandCanonical)
        assertEquals("Pixel 7 Pro 128 gb", match?.modelText)
    }

    @Test
    fun matches_compact_redmi_note_pro_plus_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("redminote13proplus256gb")

        assertNotNull(match)
        assertEquals("REDMI_NOTE_13_PRO_PLUS", match?.modelCode)
        assertEquals("Xiaomi", match?.brandCanonical)
        assertEquals("Redmi Note 13 Pro Plus 256 gb", match?.modelText)
    }

    @Test
    fun matches_compact_poco_f6_pro_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("pocof6pro512gb")

        assertNotNull(match)
        assertEquals("POCO_F6_PRO", match?.modelCode)
        assertEquals("Xiaomi", match?.brandCanonical)
        assertEquals("POCO F6 Pro 512 gb", match?.modelText)
    }

    @Test
    fun matches_compact_huawei_p60_pro_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("huaweip60pro256gb")

        assertNotNull(match)
        assertEquals("HUAWEI_P60_PRO", match?.modelCode)
        assertEquals("Huawei", match?.brandCanonical)
        assertEquals("Huawei P60 Pro 256 gb", match?.modelText)
    }

    @Test
    fun matches_compact_nothing_phone_1_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("nothingphone1128gb")

        assertNotNull(match)
        assertEquals("NOTHING_PHONE_1", match?.modelCode)
        assertEquals("Nothing", match?.brandCanonical)
        assertEquals("Nothing Phone (1) 128 gb", match?.modelText)
    }

    @Test
    fun ignores_accessory_query_for_model_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("iphone 16 pro case")

        assertNull(match)
    }

    @Test
    fun ignores_compact_accessory_query_for_model_alias() {
        val match = CatalogCanonicalModelRegistry.matchQuery("iphone16procase")

        assertNull(match)
    }
}
