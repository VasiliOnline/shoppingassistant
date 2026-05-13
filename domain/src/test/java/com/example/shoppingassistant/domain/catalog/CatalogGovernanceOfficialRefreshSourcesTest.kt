package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceOfficialRefreshSourcesTest {

    @Test
    fun tech_phones_sources_are_loaded_for_all_current_official_brand_waves() {
        val sources = CatalogGovernanceOfficialRefreshSources.resolve("TECH.PHONES")

        assertEquals(9, sources.size)
        assertTrue(sources.any { it.sourceCode == "APPLE_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "SAMSUNG_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "GOOGLE_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "XIAOMI_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "ONEPLUS_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "NOTHING_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "HUAWEI_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "HONOR_OFFICIAL_PHONES" })
        assertTrue(sources.any { it.sourceCode == "REALME_OFFICIAL_PHONES" })
    }

    @Test
    fun apple_source_declares_two_models_from_shared_buy_page() {
        val source = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "APPLE_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )

        requireNotNull(source)
        assertEquals("APPLE", source.brandCode)
        assertEquals(4, source.endpoints.size)
        assertTrue(source.endpoints.any { it.sourceUri == "https://www.apple.com/shop/buy-iphone/iphone-16" })
        assertTrue(source.endpoints.any { it.sourceUri == "https://www.apple.com/shop/buy-iphone/iphone-17-pro" })
        assertTrue(source.endpoints.any { it.modelCode == "IPHONE_16" })
        assertTrue(source.endpoints.any { it.modelCode == "IPHONE_16_PLUS" })
        assertTrue(source.endpoints.any { it.modelCode == "IPHONE_17_PRO" })
        assertTrue(source.endpoints.any { it.modelCode == "IPHONE_17_PRO_MAX" })
    }

    @Test
    fun google_source_declares_pixel_7a_8_and_9_wave_from_shared_specs_page() {
        val source = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "GOOGLE_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )

        requireNotNull(source)
        assertEquals("GOOGLE", source.brandCode)
        assertEquals(7, source.endpoints.size)
        assertTrue(
            source.endpoints.all {
                it.sourceUri == "https://support.google.com/pixelphone/answer/7158570?hl=en"
            },
        )
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_7A" })
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_8" })
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_8_PRO" })
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_8A" })
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_9" })
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_9_PRO" })
        assertTrue(source.endpoints.any { it.modelCode == "PIXEL_9_PRO_XL" })
    }

    @Test
    fun xiaomi_oneplus_and_nothing_sources_cover_current_next_brand_wave_models() {
        val xiaomi = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "XIAOMI_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )
        val oneplus = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "ONEPLUS_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )
        val nothing = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "NOTHING_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )

        requireNotNull(xiaomi)
        requireNotNull(oneplus)
        requireNotNull(nothing)

        assertEquals("XIAOMI", xiaomi.brandCode)
        assertEquals(3, xiaomi.endpoints.size)
        assertTrue(xiaomi.endpoints.any { it.modelCode == "XIAOMI_14" })
        assertTrue(xiaomi.endpoints.any { it.modelCode == "POCO_X7_PRO" })
        assertTrue(xiaomi.endpoints.any { it.modelCode == "REDMI_NOTE_14_PRO_PLUS" })

        assertEquals("ONEPLUS", oneplus.brandCode)
        assertEquals(3, oneplus.endpoints.size)
        assertTrue(oneplus.endpoints.any { it.modelCode == "ONEPLUS_13" })
        assertTrue(oneplus.endpoints.any { it.modelCode == "ONEPLUS_12" })
        assertTrue(oneplus.endpoints.any { it.modelCode == "ONEPLUS_NORD_4" })

        assertEquals("NOTHING", nothing.brandCode)
        assertEquals(3, nothing.endpoints.size)
        assertTrue(nothing.endpoints.any { it.modelCode == "NOTHING_PHONE_2" })
        assertTrue(nothing.endpoints.any { it.modelCode == "NOTHING_PHONE_2A" })
        assertTrue(nothing.endpoints.any { it.modelCode == "NOTHING_PHONE_1" })
    }

    @Test
    fun huawei_honor_and_realme_sources_cover_remaining_curated_phone_brands() {
        val huawei = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "HUAWEI_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )
        val honor = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "HONOR_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )
        val realme = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = "REALME_OFFICIAL_PHONES",
            categoryCode = "TECH.PHONES",
        )

        requireNotNull(huawei)
        requireNotNull(honor)
        requireNotNull(realme)

        assertEquals("HUAWEI", huawei.brandCode)
        assertEquals(4, huawei.endpoints.size)
        assertTrue(huawei.endpoints.any { it.modelCode == "HUAWEI_PURA70" })
        assertTrue(huawei.endpoints.any { it.modelCode == "HUAWEI_PURA70_PRO" })
        assertTrue(huawei.endpoints.any { it.modelCode == "HUAWEI_P60" })
        assertTrue(huawei.endpoints.any { it.modelCode == "HUAWEI_P60_PRO" })
        assertTrue(huawei.endpoints.any { it.metadata["modelLine"] == "P" })
        assertTrue(huawei.endpoints.all { it.parserType == CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE })

        assertEquals("HONOR", honor.brandCode)
        assertEquals(6, honor.endpoints.size)
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_200" })
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_200_PRO" })
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_MAGIC6_PRO" })
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_400" })
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_400_PRO" })
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_MAGIC7_PRO" })
        assertTrue(honor.endpoints.any { it.metadata["modelLine"] == "Magic6" })
        assertTrue(honor.endpoints.any { it.metadata["modelLine"] == "Magic7" })
        assertTrue(honor.endpoints.any { it.modelCode == "HONOR_400" && it.releaseDate == "2025-05-22" })

        assertEquals("REALME", realme.brandCode)
        assertEquals(6, realme.endpoints.size)
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_GT6" })
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_GT7_PRO" })
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_12_PRO_PLUS" })
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_14_PRO_PLUS" })
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_14_PRO" })
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_GT6T" })
        assertTrue(realme.endpoints.any { it.metadata["modelLine"] == "GT" })
        assertTrue(realme.endpoints.any { it.metadata["modelLine"] == "14 Pro+" })
        assertTrue(realme.endpoints.any { it.modelCode == "REALME_GT6T" && it.releaseDate == "2024-06-20" })
    }
}
