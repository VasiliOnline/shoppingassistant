package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceTier
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshParserType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceOfficialPhonesConnectorTest {

    @Test
    fun loadPayload_buildsApplePack_and_reusesExistingScopedCanon() = runBlocking {
        val connector = CatalogGovernanceOfficialPhonesConnector(
            fetcher = FakeCatalogGovernanceOfficialPageFetcher(
                mapOf(
                    "https://www.apple.com/shop/buy-iphone/iphone-16" to APPLE_IPHONE_16_HTML,
                    "https://www.apple.com/shop/buy-iphone/iphone-17-pro" to APPLE_HTML,
                ),
            ),
        )

        val payload = connector.loadPayload(officialEntry("APPLE_OFFICIAL_PHONES"))
        val pack = payload.packs.single()

        assertEquals("APPLE_OFFICIAL_PHONES", pack.sourceCode)
        assertEquals(
            listOf("IPHONE_16", "IPHONE_16_PLUS", "IPHONE_17_PRO", "IPHONE_17_PRO_MAX"),
            pack.models.map { it.code },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "memory_gb" &&
                    value.modelCode == "IPHONE_16" &&
                    value.canonicalCode == "128"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "color" &&
                    value.modelCode == "IPHONE_16_PLUS" &&
                    value.canonicalCode == "PINK"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "chipset_family" &&
                    value.modelCode == "IPHONE_16_PLUS" &&
                    value.canonicalCode == "A18"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "color" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "DEEP_BLUE"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "memory_gb" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "256"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "os_family" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "IOS"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "model_line" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "IPHONE"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "network_type" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "5G"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "release_year" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "2026"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "screen_size_inch" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "6.3"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "wireless_charging" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "esim_support" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            pack.canonicalValues.any { value ->
                value.attributeCode == "ip_rating" &&
                    value.modelCode == "IPHONE_17_PRO" &&
                    value.canonicalCode == "IP68"
            },
        )
    }

    @Test
    fun loadPayload_parsesSamsungAndGoogleOfficialPages() = runBlocking {
        val connector = CatalogGovernanceOfficialPhonesConnector(
            fetcher = FakeCatalogGovernanceOfficialPageFetcher(
                mapOf(
                    "https://www.samsung.com/us/smartphones/galaxy-s24/buy/" to SAMSUNG_S24_HTML,
                    "https://www.samsung.com/us/smartphones/galaxy-s24-ultra/buy/" to SAMSUNG_S24_ULTRA_HTML,
                    "https://support.google.com/pixelphone/answer/7158570?hl=en" to GOOGLE_PIXEL_HTML,
                ),
            ),
        )

        val samsungPack = connector.loadPayload(officialEntry("SAMSUNG_OFFICIAL_PHONES")).packs.single()
        assertTrue(samsungPack.models.any { it.code == "GALAXY_S24" })
        assertTrue(samsungPack.models.any { it.code == "GALAXY_S24_PLUS" })
        assertTrue(samsungPack.models.any { it.code == "GALAXY_S24_ULTRA" })
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "color" &&
                    value.modelCode == "GALAXY_S24" &&
                    value.canonicalCode == "YELLOW"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "memory_gb" &&
                    value.modelCode == "GALAXY_S24_ULTRA" &&
                    value.canonicalCode == "1024"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "screen_size_inch" &&
                    value.modelCode == "GALAXY_S24_PLUS" &&
                    value.canonicalCode == "6.7"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "release_year" &&
                    value.modelCode == "GALAXY_S24" &&
                    value.canonicalCode == "2024"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "battery_mah" &&
                    value.modelCode == "GALAXY_S24" &&
                    value.canonicalCode == "4000"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "wired_charging_w" &&
                    value.modelCode == "GALAXY_S24_PLUS" &&
                    value.canonicalCode == "45"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "wireless_charging" &&
                    value.modelCode == "GALAXY_S24_ULTRA" &&
                    value.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "esim_support" &&
                    value.modelCode == "GALAXY_S24_PLUS" &&
                    value.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            samsungPack.canonicalValues.any { value ->
                value.attributeCode == "ip_rating" &&
                    value.modelCode == "GALAXY_S24" &&
                    value.canonicalCode == "IP68"
            },
        )

        val googlePack = connector.loadPayload(officialEntry("GOOGLE_OFFICIAL_PHONES")).packs.single()
        assertTrue(googlePack.models.any { it.code == "PIXEL_7A" })
        assertTrue(googlePack.models.any { it.code == "PIXEL_8" })
        assertTrue(googlePack.models.any { it.code == "PIXEL_8_PRO" })
        assertTrue(googlePack.models.any { it.code == "PIXEL_8A" })
        assertTrue(googlePack.models.any { it.code == "PIXEL_9" })
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "memory_gb" &&
                    value.modelCode == "PIXEL_8" &&
                    value.canonicalCode == "128"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "chipset_family" &&
                    value.modelCode == "PIXEL_8_PRO" &&
                    value.canonicalCode == "TENSOR_G3"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "ram_gb" &&
                    value.modelCode == "PIXEL_8A" &&
                    value.canonicalCode == "8"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "refresh_rate_hz" &&
                    value.modelCode == "PIXEL_7A" &&
                    value.canonicalCode == "90"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "chipset_family" &&
                    value.modelCode == "PIXEL_9_PRO_XL" &&
                    value.canonicalCode == "TENSOR_G4"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "refresh_rate_hz" &&
                    value.modelCode == "PIXEL_9" &&
                    value.canonicalCode == "120"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "ram_gb" &&
                    value.modelCode == "PIXEL_9_PRO" &&
                    value.canonicalCode == "16"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "screen_size_inch" &&
                    value.modelCode == "PIXEL_9_PRO_XL" &&
                    value.canonicalCode == "6.8"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "release_year" &&
                    value.modelCode == "PIXEL_9" &&
                    value.canonicalCode == "2024"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "battery_mah" &&
                    value.modelCode == "PIXEL_9_PRO_XL" &&
                    value.canonicalCode == "5060"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "wired_charging_w" &&
                    value.modelCode == "PIXEL_9_PRO_XL" &&
                    value.canonicalCode == "37"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "wireless_charging" &&
                    value.modelCode == "PIXEL_8" &&
                    value.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "esim_support" &&
                    value.modelCode == "PIXEL_9_PRO" &&
                    value.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            googlePack.canonicalValues.any { value ->
                value.attributeCode == "ip_rating" &&
                    value.modelCode == "PIXEL_7A" &&
                    value.canonicalCode == "IP67"
            },
        )
        val storageAliases = googlePack.canonicalValues
            .first { value ->
                value.attributeCode == "memory_gb" &&
                    value.modelCode == "PIXEL_8" &&
                    value.canonicalCode == "128"
            }
            .aliases["en"]
            .orEmpty()
        val ramAliases = googlePack.canonicalValues
            .first { value ->
                value.attributeCode == "ram_gb" &&
                    value.modelCode == "PIXEL_8A" &&
                    value.canonicalCode == "8"
            }
            .aliases["en"]
            .orEmpty()
        assertTrue("storage aliases should keep contextual gb hint", "128gb" in storageAliases)
        assertFalse("storage aliases should not keep bare numeric hints", "128" in storageAliases)
        assertTrue("ram aliases should keep contextual ram hint", "8gb ram" in ramAliases)
        assertFalse("ram aliases should not keep bare numeric hints", "8" in ramAliases)
    }

    @Test
    fun loadPayload_parsesXiaomiOnePlusAndNothingOfficialSources() = runBlocking {
        val connector = CatalogGovernanceOfficialPhonesConnector(
            fetcher = FakeCatalogGovernanceOfficialPageFetcher(
                mapOf(
                    "https://www.mi.com/global/product/xiaomi-14/specs/" to XIAOMI_14_HTML,
                    "https://www.mi.com/global/product/poco-x7-pro/specs/" to POCO_X7_PRO_HTML,
                    "https://www.mi.com/global/product/redmi-note-14-pro-plus-5g/specs/" to REDMI_NOTE_14_PRO_PLUS_HTML,
                    "https://www.oneplus.com/us/13/specs" to ONEPLUS_13_HTML,
                    "https://www.oneplus.com/us/12/specs" to ONEPLUS_12_HTML,
                    "https://www.oneplus.com/global/nord-4/specs" to ONEPLUS_NORD_4_HTML,
                    "https://nothing.tech/products/phone-2" to NOTHING_PHONE_2_HTML,
                    "https://checkout.nothing.tech/pages/phone-2a" to NOTHING_PHONE_2A_HTML,
                    "https://intl.nothing.tech/products/phone-1" to NOTHING_PHONE_1_HTML,
                ),
            ),
        )

        val xiaomiPack = connector.loadPayload(officialEntry("XIAOMI_OFFICIAL_PHONES")).packs.single()
        assertTrue("xiaomi pack should include XIAOMI_14", xiaomiPack.models.any { it.code == "XIAOMI_14" })
        assertTrue("xiaomi pack should include POCO_X7_PRO", xiaomiPack.models.any { it.code == "POCO_X7_PRO" })
        assertTrue(
            "xiaomi pack should include REDMI_NOTE_14_PRO_PLUS",
            xiaomiPack.models.any { it.code == "REDMI_NOTE_14_PRO_PLUS" },
        )
        assertTrue(
            "xiaomi pack should expose Jade Green for Xiaomi 14",
            xiaomiPack.canonicalValues.any { it.attributeCode == "color" && it.modelCode == "XIAOMI_14" && it.canonicalCode == "GREEN" },
        )
        assertTrue(
            "xiaomi pack should expose 512GB for POCO X7 Pro",
            xiaomiPack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "POCO_X7_PRO" && it.canonicalCode == "512" },
        )
        assertTrue(
            "xiaomi pack should expose 12GB RAM for Redmi Note 14 Pro+",
            xiaomiPack.canonicalValues.any { it.attributeCode == "ram_gb" && it.modelCode == "REDMI_NOTE_14_PRO_PLUS" && it.canonicalCode == "12" },
        )
        assertTrue(
            "xiaomi pack should expose 4610mAh for Xiaomi 14",
            xiaomiPack.canonicalValues.any { it.attributeCode == "battery_mah" && it.modelCode == "XIAOMI_14" && it.canonicalCode == "4610" },
        )
        assertTrue(
            "xiaomi pack should expose 120W charging for Redmi Note 14 Pro+",
            xiaomiPack.canonicalValues.any { it.attributeCode == "wired_charging_w" && it.modelCode == "REDMI_NOTE_14_PRO_PLUS" && it.canonicalCode == "120" },
        )

        val oneplusPack = connector.loadPayload(officialEntry("ONEPLUS_OFFICIAL_PHONES")).packs.single()
        assertTrue("oneplus pack should include ONEPLUS_13", oneplusPack.models.any { it.code == "ONEPLUS_13" })
        assertTrue("oneplus pack should include ONEPLUS_12", oneplusPack.models.any { it.code == "ONEPLUS_12" })
        assertTrue("oneplus pack should include ONEPLUS_NORD_4", oneplusPack.models.any { it.code == "ONEPLUS_NORD_4" })
        assertTrue(
            "oneplus pack should expose Midnight Ocean for OnePlus 13",
            oneplusPack.canonicalValues.any { it.attributeCode == "color" && it.modelCode == "ONEPLUS_13" && it.canonicalCode == "BLUE" },
        )
        assertTrue(
            "oneplus pack should expose 512GB for OnePlus 12",
            oneplusPack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "ONEPLUS_12" && it.canonicalCode == "512" },
        )
        assertTrue(
            "oneplus pack should expose 16GB RAM for Nord 4",
            oneplusPack.canonicalValues.any { it.attributeCode == "ram_gb" && it.modelCode == "ONEPLUS_NORD_4" && it.canonicalCode == "16" },
        )
        assertTrue(
            "oneplus pack should expose 6000mAh for OnePlus 13",
            oneplusPack.canonicalValues.any { it.attributeCode == "battery_mah" && it.modelCode == "ONEPLUS_13" && it.canonicalCode == "6000" },
        )
        assertTrue(
            "oneplus pack should expose wireless charging for OnePlus 12",
            oneplusPack.canonicalValues.any { it.attributeCode == "wireless_charging" && it.modelCode == "ONEPLUS_12" && it.canonicalCode == "TRUE" },
        )
        assertTrue(
            "oneplus pack should expose Nord model line for Nord 4",
            oneplusPack.canonicalValues.any { it.attributeCode == "model_line" && it.modelCode == "ONEPLUS_NORD_4" && it.canonicalCode == "NORD" },
        )

        val nothingPack = connector.loadPayload(officialEntry("NOTHING_OFFICIAL_PHONES")).packs.single()
        assertTrue("nothing pack should include NOTHING_PHONE_2", nothingPack.models.any { it.code == "NOTHING_PHONE_2" })
        assertTrue("nothing pack should include NOTHING_PHONE_2A", nothingPack.models.any { it.code == "NOTHING_PHONE_2A" })
        assertTrue("nothing pack should include NOTHING_PHONE_1", nothingPack.models.any { it.code == "NOTHING_PHONE_1" })
        assertTrue(
            "nothing pack should expose Dark Grey color for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "color" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode in setOf("GRAY", "GREY", "DARK_GRAY", "DARK_GREY")
            },
        )
        assertTrue(
            "nothing pack should expose 512GB for Phone (2)",
            nothingPack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "NOTHING_PHONE_2" && it.canonicalCode == "512" },
        )
        assertTrue(
            "nothing pack should auto-extract release_date for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "release_date" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "2023-07-17"
            },
        )
        assertTrue(
            "nothing pack should auto-extract chipset for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "chipset_family" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "SNAPDRAGON_8_PLUS_GEN_1"
            },
        )
        assertTrue(
            "nothing pack should auto-extract screen size for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "screen_size_inch" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "6.7"
            },
        )
        assertTrue(
            "nothing pack should auto-extract refresh rate for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "refresh_rate_hz" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "120"
            },
        )
        assertTrue(
            "nothing pack should auto-extract battery for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "battery_mah" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "4700"
            },
        )
        assertTrue(
            "nothing pack should auto-extract wired charging for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "wired_charging_w" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "45"
            },
        )
        assertTrue(
            "nothing pack should auto-extract wireless charging for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "wireless_charging" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            "nothing pack should auto-extract eSIM support for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "esim_support" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            "nothing pack should auto-extract dual SIM support for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "dual_sim" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "TRUE"
            },
        )
        assertTrue(
            "nothing pack should auto-extract IP rating for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "ip_rating" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "IP54"
            },
        )
        assertTrue(
            "nothing pack should auto-extract network type for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "network_type" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "5G"
            },
        )
        assertTrue(
            "nothing pack should auto-extract OS family for Phone (2)",
            nothingPack.canonicalValues.any {
                it.attributeCode == "os_family" &&
                    it.modelCode == "NOTHING_PHONE_2" &&
                    it.canonicalCode == "ANDROID"
            },
        )
        assertTrue(
            "nothing pack should expose 8GB RAM for Phone (1)",
            nothingPack.canonicalValues.any { it.attributeCode == "ram_gb" && it.modelCode == "NOTHING_PHONE_1" && it.canonicalCode == "8" },
        )
        assertTrue(
            "nothing pack should expose 5000mAh for Phone (2a)",
            nothingPack.canonicalValues.any { it.attributeCode == "battery_mah" && it.modelCode == "NOTHING_PHONE_2A" && it.canonicalCode == "5000" },
        )
        assertTrue(
            "nothing pack should expose 120Hz for Phone (1)",
            nothingPack.canonicalValues.any { it.attributeCode == "refresh_rate_hz" && it.modelCode == "NOTHING_PHONE_1" && it.canonicalCode == "120" },
        )
        assertTrue(
            "nothing pack should expose Phone model line",
            nothingPack.canonicalValues.any { it.attributeCode == "model_line" && it.modelCode == "NOTHING_PHONE_2A" && it.canonicalCode == "PHONE" },
        )
    }

    @Test
    fun loadPayload_parsesHuaweiHonorAndRealmeOfficialSources() = runBlocking {
        val connector = CatalogGovernanceOfficialPhonesConnector(
            fetcher = FakeCatalogGovernanceOfficialPageFetcher(
                mapOf(
                    "https://consumer.huawei.com/sk/phones/pura70/specs/" to HUAWEI_PURA70_HTML,
                    "https://consumer.huawei.com/sk/phones/pura70-pro/specs/" to HUAWEI_PURA70_PRO_HTML,
                    "https://consumer.huawei.com/en/phones/p60/specs/" to HUAWEI_P60_HTML,
                    "https://consumer.huawei.com/en/phones/p60-pro/specs/" to HUAWEI_P60_PRO_HTML,
                    "https://www.honor.com/global/phones/honor-200/spec/" to HONOR_200_HTML,
                    "https://www.honor.com/global/phones/honor-200-pro/spec/" to HONOR_200_PRO_HTML,
                    "https://www.honor.com/global/phones/honor-magic6-pro/spec/" to HONOR_MAGIC6_PRO_HTML,
                    "https://www.honor.com/global/phones/honor-400/" to HONOR_400_HTML,
                    "https://www.honor.com/global/phones/honor-400-pro/" to HONOR_400_PRO_HTML,
                    "https://www.honor.com/global/phones/honor-magic7-pro/spec/" to HONOR_MAGIC7_PRO_HTML,
                    "https://www.realme.com/global/realme-gt-6/specs" to REALME_GT6_HTML,
                    "https://www.realme.com/global/realme-gt-7-pro/specs" to REALME_GT7_PRO_HTML,
                    "https://www.realme.com/br/realme-12-pro-plus/specs" to REALME_12_PRO_PLUS_HTML,
                    "https://www.realme.com/global/realme-14-pro-plus-5g/specs" to REALME_14_PRO_PLUS_HTML,
                    "https://www.realme.com/global/realme-14-pro-5g/specs" to REALME_14_PRO_HTML,
                    "https://www.realme.com/global/realme-gt-6t/specs" to REALME_GT6T_HTML,
                ),
            ),
        )

        val huaweiPack = connector.loadPayload(officialEntry("HUAWEI_OFFICIAL_PHONES")).packs.single()
        assertTrue(huaweiPack.models.any { it.code == "HUAWEI_PURA70" })
        assertTrue(huaweiPack.models.any { it.code == "HUAWEI_PURA70_PRO" })
        assertTrue(huaweiPack.models.any { it.code == "HUAWEI_P60" })
        assertTrue(huaweiPack.models.any { it.code == "HUAWEI_P60_PRO" })
        assertTrue(
            huaweiPack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "HUAWEI_PURA70" && it.canonicalCode == "256" },
        )
        assertTrue(
            huaweiPack.canonicalValues.any { it.attributeCode == "ram_gb" && it.modelCode == "HUAWEI_PURA70_PRO" && it.canonicalCode == "12" },
        )
        assertTrue(
            huaweiPack.canonicalValues.any { it.attributeCode == "color" && it.modelCode == "HUAWEI_PURA70" && it.canonicalCode == "BLUE" },
        )
        assertTrue(
            huaweiPack.canonicalValues.any { it.attributeCode == "network_type" && it.modelCode == "HUAWEI_PURA70" && it.canonicalCode == "4G" },
        )
        assertTrue(
            huaweiPack.canonicalValues.any { it.attributeCode == "model_line" && it.modelCode == "HUAWEI_PURA70_PRO" && it.canonicalCode == "PURA" },
        )
        assertTrue(
            huaweiPack.canonicalValues.any { it.attributeCode == "wired_charging_w" && it.modelCode == "HUAWEI_P60_PRO" && it.canonicalCode == "88" },
        )
        assertTrue(
            huaweiPack.canonicalValues.any {
                it.attributeCode == "chipset_family" &&
                    it.modelCode == "HUAWEI_P60" &&
                    it.canonicalCode == "SNAPDRAGON_8_PLUS_GEN_1"
            },
        )

        val honorPack = connector.loadPayload(officialEntry("HONOR_OFFICIAL_PHONES")).packs.single()
        assertTrue(honorPack.models.any { it.code == "HONOR_200" })
        assertTrue(honorPack.models.any { it.code == "HONOR_200_PRO" })
        assertTrue(honorPack.models.any { it.code == "HONOR_MAGIC6_PRO" })
        assertTrue(honorPack.models.any { it.code == "HONOR_400" })
        assertTrue(honorPack.models.any { it.code == "HONOR_400_PRO" })
        assertTrue(honorPack.models.any { it.code == "HONOR_MAGIC7_PRO" })
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "HONOR_200" && it.canonicalCode == "512" },
        )
        assertTrue(
            honorPack.canonicalValues.any {
                it.attributeCode == "color" &&
                    it.modelCode == "HONOR_200_PRO" &&
                    it.canonicalCode in setOf("CYAN", "OCEAN_CYAN")
            },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "wireless_charging" && it.modelCode == "HONOR_200_PRO" && it.canonicalCode == "TRUE" },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "ip_rating" && it.modelCode == "HONOR_MAGIC6_PRO" && it.canonicalCode == "IP68" },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "model_line" && it.modelCode == "HONOR_MAGIC6_PRO" && it.canonicalCode == "MAGIC6" },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "battery_mah" && it.modelCode == "HONOR_400" && it.canonicalCode == "6000" },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "release_date" && it.modelCode == "HONOR_400" && it.canonicalCode == "2025-05-22" },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "wired_charging_w" && it.modelCode == "HONOR_400_PRO" && it.canonicalCode == "100" },
        )
        assertTrue(
            honorPack.canonicalValues.any { it.attributeCode == "model_line" && it.modelCode == "HONOR_MAGIC7_PRO" && it.canonicalCode == "MAGIC7" },
        )

        val realmePack = connector.loadPayload(officialEntry("REALME_OFFICIAL_PHONES")).packs.single()
        assertTrue(realmePack.models.any { it.code == "REALME_GT6" })
        assertTrue(realmePack.models.any { it.code == "REALME_GT7_PRO" })
        assertTrue(realmePack.models.any { it.code == "REALME_12_PRO_PLUS" })
        assertTrue(realmePack.models.any { it.code == "REALME_14_PRO_PLUS" })
        assertTrue(realmePack.models.any { it.code == "REALME_14_PRO" })
        assertTrue(realmePack.models.any { it.code == "REALME_GT6T" })
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "ram_gb" && it.modelCode == "REALME_GT7_PRO" && it.canonicalCode == "16" },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "REALME_GT7_PRO" && it.canonicalCode == "512" },
        )
        assertTrue(
            realmePack.canonicalValues.any {
                it.attributeCode == "color" &&
                    it.modelCode == "REALME_12_PRO_PLUS" &&
                    it.canonicalCode in setOf("RED", "EXPLORER_RED")
            },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "ip_rating" && it.modelCode == "REALME_GT7_PRO" && it.canonicalCode == "IP69" },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "model_line" && it.modelCode == "REALME_GT6" && it.canonicalCode == "GT" },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "battery_mah" && it.modelCode == "REALME_14_PRO_PLUS" && it.canonicalCode == "6000" },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "wired_charging_w" && it.modelCode == "REALME_14_PRO" && it.canonicalCode == "45" },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "memory_gb" && it.modelCode == "REALME_GT6T" && it.canonicalCode == "512" },
        )
        assertTrue(
            realmePack.canonicalValues.any { it.attributeCode == "release_date" && it.modelCode == "REALME_GT6T" && it.canonicalCode == "2024-06-20" },
        )
    }

    @Test
    fun loadPayload_merges_operator_seeded_overlay_endpoints() = runBlocking {
        val connector = CatalogGovernanceOfficialPhonesConnector(
            fetcher = object : CatalogGovernanceOfficialPageFetcher {
                override suspend fun fetchText(uri: String): String = HONOR_500_ULTRA_HTML
            },
            endpointOverlayRepository = object : CatalogGovernanceOfficialPhoneEndpointOverlayRepository {
                override suspend fun listEndpoints(
                    categoryCode: String,
                    sourceCode: String,
                ): List<CatalogGovernanceOfficialPhoneEndpointOverlay> =
                    listOf(
                        CatalogGovernanceOfficialPhoneEndpointOverlay(
                            candidateId = 900L,
                            categoryCode = categoryCode,
                            sourceCode = sourceCode,
                            brandCode = "HONOR",
                            endpointCode = "HONOR_500_ULTRA",
                            parserType = CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE,
                            sourceUri = "https://www.honor.com/global/phones/honor-500-ultra/",
                            familyCode = "HONOR_500",
                            modelCode = "HONOR_500_ULTRA",
                            modelLabel = "Honor 500 Ultra",
                            aliases = mapOf("en" to listOf("500 Ultra", "Honor 500 Ultra")),
                            createdBy = "test",
                            createdAt = 1L,
                            updatedAt = 1L,
                        ),
                    )

                override suspend fun upsertEndpoint(
                    endpoint: CatalogGovernanceOfficialPhoneEndpointOverlay,
                ): CatalogGovernanceOfficialPhoneEndpointOverlay = endpoint
            },
        )

        val honorPack = connector.loadPayload(officialEntry("HONOR_OFFICIAL_PHONES")).packs.single()

        assertTrue(honorPack.models.any { it.code == "HONOR_500_ULTRA" })
        assertTrue(
            honorPack.canonicalValues.any { value ->
                value.attributeCode == "model_line" &&
                    value.modelCode == "HONOR_500_ULTRA" &&
                    value.canonicalCode == "HONOR_500"
            },
        )
        assertTrue(
            honorPack.canonicalValues.any { value ->
                value.attributeCode == "release_date" &&
                    value.modelCode == "HONOR_500_ULTRA" &&
                    value.canonicalCode == "2026-03-18"
            },
        )
        assertTrue(
            honorPack.canonicalValues.any { value ->
                value.attributeCode == "release_year" &&
                    value.modelCode == "HONOR_500_ULTRA" &&
                    value.canonicalCode == "2026"
            },
        )
        assertTrue(
            honorPack.canonicalValues.any { value ->
                value.attributeCode == "color" &&
                    value.modelCode == "HONOR_500_ULTRA" &&
                    value.canonicalCode in setOf("BLACK", "MIDNIGHT_BLACK")
            },
        )
    }

    private fun officialEntry(sourceCode: String): CatalogGovernanceSourceRegistryEntry =
        CatalogGovernanceSourceRegistryEntry(
            registryCode = "TECH.PHONES|OFFICIAL_PHONE_WEB_SOURCE|$sourceCode",
            categoryCode = "TECH.PHONES",
            connectorType = CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE,
            sourceCode = sourceCode,
            externalRef = sourceCode,
            displayName = sourceCode,
            tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
            defaultLocale = "en-US",
            marketCode = "US",
            enabled = true,
            autoPublish = true,
            createdAt = 1L,
            updatedAt = 1L,
        )

    private class FakeCatalogGovernanceOfficialPageFetcher(
        private val htmlByUri: Map<String, String>,
    ) : CatalogGovernanceOfficialPageFetcher {
        override suspend fun fetchText(uri: String): String =
            htmlByUri[uri] ?: error("Missing fake HTML for '$uri'.")
    }

    private companion object {
        private const val APPLE_IPHONE_16_HTML = """
            {"name":"iPhone 16 128GB Ultramarine"}
            {"name":"iPhone 16 256GB Teal"}
            {"name":"iPhone 16 512GB Black"}
            {"name":"iPhone 16 Plus 128GB Pink"}
            {"name":"iPhone 16 Plus 256GB White"}
            {"name":"iPhone 16 Plus 512GB Ultramarine"}
        """

        private const val APPLE_HTML = """
            {"name":"iPhone 17 Pro 256GB Deep Blue"}
            {"name":"iPhone 17 Pro 512GB Cosmic Orange"}
            {"name":"iPhone 17 Pro Max 256GB Silver"}
            {"name":"iPhone 17 Pro Max 1TB Deep Blue"}
        """

        private const val SAMSUNG_S24_HTML = """
            "productTitle":"Galaxy S24 128GB (Unlocked)"
            "productTitle":"Galaxy S24 256GB (Unlocked)"
            "productTitle":"Galaxy S24+ 256GB (Unlocked)"
            "productTitle":"Galaxy S24+ 512GB (Unlocked)"
            What colors do Galaxy S24 and S24+ come in?
            Galaxy S24 and S24+ come in Cobalt Violet, Amber Yellow, Onyx Black and Marble Gray.
        """

        private const val SAMSUNG_S24_ULTRA_HTML = """
            aria-label="256GB"
            aria-label="512GB"
            aria-label="1TB"
            aria-label="Color Variant: Titanium Black"
            aria-label="Color Variant: Titanium Gray"
        """

        private const val GOOGLE_PIXEL_HTML = """
            Pixel 7a</a><div><table>
            <p><strong>Memory</strong></p><ul><li>8 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Charcoal</li><li>Snow</li><li>Sea</li><li>Coral</li></ul></td></tr>
            <p>Google Tensor G2</p>
            <p>60-90Hz</p>
            </table>
            Pixel 8</a><div><table>
            <p><strong>Memory</strong></p><ul><li>8 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB / 256 GB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Hazel</li><li>Rose</li><li>Mint</li></ul></td></tr>
            <p>Google Tensor G3</p>
            <p>60-120Hz</p>
            </table>
            Pixel 8 Pro</a><div><table>
            <p><strong>Memory</strong></p><ul><li>12 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB / 256 GB / 512 GB / 1 TB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Porcelain</li><li>Bay</li><li>Mint</li></ul></td></tr>
            <p>Google Tensor G3</p>
            <p>1-120Hz</p>
            </table>
            Pixel 8a</a><div><table>
            <p><strong>Memory</strong></p><ul><li>8 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB / 256 GB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Porcelain</li><li>Bay</li><li>Aloe</li></ul></td></tr>
            <p>Google Tensor G3</p>
            <p>60-120Hz</p>
            </table>
            Pixel 9</a><div><table>
            <p><strong>Memory</strong></p><ul><li>12 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB / 256 GB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Porcelain</li><li>Wintergreen</li><li>Peony</li></ul></td></tr>
            <p>Google Tensor G4</p>
            <p>1-120Hz</p>
            </table>
            Pixel 9 Pro</a><div><table>
            <p><strong>Memory</strong></p><ul><li>16 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB / 256 GB / 512 GB / 1 TB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Porcelain</li><li>Hazel</li><li>Rose Quartz</li></ul></td></tr>
            <p>Google Tensor G4</p>
            <p>1-120Hz</p>
            </table>
            Pixel 9 Pro XL</a><div><table>
            <p><strong>Memory</strong></p><ul><li>16 GB RAM</li></ul>
            <p><strong>Storage</strong></p><ul><li>128 GB / 256 GB / 512 GB / 1 TB</li></ul>
            <tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Porcelain</li><li>Hazel</li><li>Rose Quartz</li></ul></td></tr>
            <p>Google Tensor G4</p>
            <p>1-120Hz</p>
            </table>
        """

        private const val XIAOMI_14_HTML = """
            Xiaomi 14 Specs
            Black White Jade Green
            12GB+256GB, 12GB+512GB
        """

        private const val POCO_X7_PRO_HTML = """
            POCO X7 Pro Specs
            Black Green Yellow
            12GB+256GB, 12GB+512GB
        """

        private const val REDMI_NOTE_14_PRO_PLUS_HTML = """
            Redmi Note 14 Pro+ 5G Specs
            Black Purple Blue
            12+256GB, 12+512GB
        """

        private const val ONEPLUS_13_HTML = """
            OnePlus 13 Specs
            Midnight Ocean Arctic Dawn Black Eclipse
            12GB+256GB, 16GB+512GB
        """

        private const val ONEPLUS_12_HTML = """
            OnePlus 12 Specs
            12GB+256GB, 16GB+512GB
        """

        private const val ONEPLUS_NORD_4_HTML = """
            OnePlus Nord 4 Specs
            Mercurial Silver Oasis Green Obsidian Midnight
            12GB+256GB, 16GB+512GB
        """

        private const val HUAWEI_PURA70_HTML = """
            list-pink.png list-white.png specs-black.png specs-blue.png
            RAM + ROM
            12 GB RAM + 256 GB ROM
        """

        private const val HUAWEI_PURA70_PRO_HTML = """
            specs-white.png specs-black.png
            RAM + ROM
            12 GB RAM + 512 GB ROM
        """

        private const val HUAWEI_P60_HTML = """
            specs-black.png specs-white.png specs-blue.png
            RAM + ROM
            8 GB RAM + 256 GB ROM
            8 GB RAM + 512 GB ROM
        """

        private const val HUAWEI_P60_PRO_HTML = """
            specs-black.png specs-white.png specs-rococo-pearl.png
            RAM + ROM
            8 GB RAM + 256 GB ROM
            12 GB RAM + 512 GB ROM
        """

        private const val HONOR_200_HTML = """
            data-value="8GB+256GB 12GB+256GB 12GB+512GB"
            color-name-list" value="Moonlight White"
            color-name-list" value="Emerald Green"
            color-name-list" value="Coral Pink"
            color-name-list" value="Black"
        """

        private const val HONOR_200_PRO_HTML = """
            data-value="12GB+512GB"
            color-name-list" value="Ocean Cyan"
            color-name-list" value="Moonlight White"
            color-name-list" value="Black"
        """

        private const val HONOR_MAGIC6_PRO_HTML = """
            data-value="12GB+512GB"
            color-name-list" value="Epi Green"
            color-name-list" value="Black"
            color-name-list" value="Cloud Purple"
        """

        private const val HONOR_400_HTML = """
            data-value="8GB+256GB 12GB+512GB"
            color-name-list" value="Midnight Black"
            color-name-list" value="Desert Gold"
            color-name-list" value="Meteor Silver"
            color-name-list" value="Tidal Blue"
        """

        private const val HONOR_400_PRO_HTML = """
            data-value="12GB+512GB"
            color-name-list" value="Lunar Grey"
            color-name-list" value="Midnight Black"
            color-name-list" value="Tidal Blue"
        """

        private const val HONOR_MAGIC7_PRO_HTML = """
            data-value="12GB+512GB 16GB+1TB"
            color-name-list" value="Lunar Shadow Grey"
            color-name-list" value="Breeze Blue"
            color-name-list" value="Black"
        """

        private const val HONOR_500_ULTRA_HTML = """
            data-value="12GB+512GB"
            color-name-list" value="Midnight Black"
            color-name-list" value="Moonlight White"
            {"launchDate":"2026-03-18"}
            Android 16 with 5G
        """

        private const val REALME_GT6_HTML = """
            realme GT 6 Specs
            Fluid Silver Razor Green
            12GB+256GB, 16GB+512GB
        """

        private const val REALME_GT7_PRO_HTML = """
            realme GT 7 Pro
            Mars Orange Galaxy Grey
            RAM：12GB/16GB
            ROM：256GB/512GB
        """

        private const val REALME_12_PRO_PLUS_HTML = """
            realme 12 Pro+ Specs
            Submarine Blue Navigator Beige Explorer Red
            RAM: 12 GB
            ROM: 512 GB
        """

        private const val REALME_14_PRO_PLUS_HTML = """
            realme 14 Pro+ 5G Specs
            Pearl White Suede Grey Bikaner Purple
            12GB+256GB, 12GB+512GB
        """

        private const val REALME_14_PRO_HTML = """
            realme 14 Pro 5G Specs
            Pearl White Jaipur Pink Suede Grey
            RAM：8GB/12GB
            ROM：256GB/512GB
        """

        private const val REALME_GT6T_HTML = """
            realme GT 6T Specs
            Fluid Silver Razor Green
            8GB+256GB, 12GB+512GB
        """

        private const val NOTHING_PHONE_2_HTML = """
            https://nothing.tech/products/phone-2?Colour=White&amp;Capacity=8%2B128GB
            https://nothing.tech/products/phone-2?Colour=White&amp;Capacity=12%2B256GB
            https://nothing.tech/products/phone-2?Colour=Dark+Grey&amp;Capacity=12%2B512GB
            Nothing OS 2.0 based on Android 13
            5G Dual SIM with eSIM support
            6.7-inch LTPO OLED display 120Hz adaptive refresh rate
            Snapdragon 8+ Gen 1
            4700mAh battery
            45W wired charging 15W wireless charging
            IP54
            Released July 17, 2023
        """

        private const val NOTHING_PHONE_2A_HTML = """
            Black_Glyphs Milk_Glyphs
            8+128GB 12+256GB
        """

        private const val NOTHING_PHONE_1_HTML = """
            https://intl.nothing.tech/products/phone-1?colour=Black&amp;capacity=8%2B128GB
            https://intl.nothing.tech/products/phone-1?colour=White&amp;capacity=8%2B256GB
            https://intl.nothing.tech/products/phone-1?colour=White&amp;capacity=12%2B256GB
        """
    }
}
