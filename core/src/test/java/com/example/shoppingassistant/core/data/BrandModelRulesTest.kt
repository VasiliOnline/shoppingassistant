package com.example.shoppingassistant.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandModelRulesTest {

    @Test
    fun normalizes_cyrillic_iphone_family_alias_to_apple_brand() {
        val normalized = BrandModelRules.fromRaw("айфон 17 про 256гб")

        assertEquals("Apple", normalized.brand)
        assertEquals("iPhone 17 Pro 256гб", normalized.model)
    }

    @Test
    fun normalizes_cyrillic_galaxy_family_alias_to_samsung_brand() {
        val normalized = BrandModelRules.fromRaw("галакси s24 ultra")

        assertEquals("Samsung", normalized.brand)
        assertEquals("Galaxy S24 Ultra", normalized.model)
    }

    @Test
    fun normalizes_brand_plus_family_prefix_to_canonical_brand_model() {
        val normalized = BrandModelRules.fromRaw("apple iphone 16 pro 256gb")

        assertEquals("Apple", normalized.brand)
        assertEquals("iPhone 16 pro 256gb", normalized.model)
    }

    @Test
    fun normalizes_macbook_family_alias_to_apple_brand() {
        val normalized = BrandModelRules.fromRaw("макбук air m3 13")

        assertEquals("Apple", normalized.brand)
        assertEquals("MacBook Air m3 13", normalized.model)
    }

    @Test
    fun normalizes_short_phone_model_alias_to_canonical_brand_and_model() {
        val normalized = BrandModelRules.fromRaw("s24 ultra 256gb")

        assertEquals("Samsung", normalized.brand)
        assertEquals("Galaxy S24 Ultra 256gb", normalized.model)
    }

    @Test
    fun normalizes_compact_short_phone_alias_with_russian_chipset_and_storage_suffix() {
        val normalized = BrandModelRules.fromRaw("s24u 120гц снап 8 ген 3 256гб")

        assertEquals("Samsung", normalized.brand)
        assertEquals("Galaxy S24 Ultra 120гц снап 8 ген 3 256гб", normalized.model)
    }

    @Test
    fun normalizes_compact_phone_family_query_to_apple_brand_without_wrong_category_drift() {
        val normalized = BrandModelRules.fromRaw("iphone17pro256gb")

        assertEquals("Apple", normalized.brand)
        assertEquals("iPhone 17 Pro 256 gb", normalized.model)
    }

    @Test
    fun normalizes_compact_pm_alias_to_canonical_iphone_model() {
        val normalized = BrandModelRules.fromRaw("iphone16pm256gb")

        assertEquals("Apple", normalized.brand)
        assertEquals("iPhone 16 Pro Max 256 gb", normalized.model)
    }

    @Test
    fun normalizes_compact_pixel_7_pro_alias_to_google_brand_and_model() {
        val normalized = BrandModelRules.fromRaw("pixel7pro128gb")

        assertEquals("Google", normalized.brand)
        assertEquals("Pixel 7 Pro 128 gb", normalized.model)
    }

    @Test
    fun normalizes_compact_huawei_p60_pro_alias_to_huawei_brand_and_model() {
        val normalized = BrandModelRules.fromRaw("huaweip60pro256gb")

        assertEquals("Huawei", normalized.brand)
        assertEquals("Huawei P60 Pro 256 gb", normalized.model)
    }

    @Test
    fun normalizes_compact_poco_f6_pro_alias_to_xiaomi_brand_and_model() {
        val normalized = BrandModelRules.fromRaw("pocof6pro512gb")

        assertEquals("Xiaomi", normalized.brand)
        assertEquals("POCO F6 Pro 512 gb", normalized.model)
    }
}
