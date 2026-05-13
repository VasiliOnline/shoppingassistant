package com.example.shoppingassistant.feature.pages.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsTypedFacetScopedUniverseTest {

    @Test
    fun buildResultsTypedFacetLookupScopes_preserves_model_scopes_for_multi_brand_selection() {
        val scopes = buildResultsTypedFacetLookupScopes(
            selectedBrands = setOf("Apple", "Samsung"),
            query = null,
            typedAttributeFilters = mapOf(
                "model" to TypedAttributeFilterDraft(
                    op = com.example.shoppingassistant.domain.model.TypedAttributeOperator.IN,
                    valuesCsv = "iPhone 17 Pro, Galaxy S24 Ultra",
                ),
            ),
            categoryCode = "TECH.PHONES",
        )

        assertEquals(
            listOf(
                ResultsTypedFacetLookupScope(brand = "Apple", model = "iPhone 17 Pro"),
                ResultsTypedFacetLookupScope(brand = "Samsung", model = "Galaxy S24 Ultra"),
            ),
            scopes,
        )
    }

    @Test
    fun buildResultsTypedFacetLookupScopes_keeps_brand_only_scope_for_unmapped_selected_brand() {
        val scopes = buildResultsTypedFacetLookupScopes(
            selectedBrands = setOf("Apple", "Samsung"),
            query = null,
            typedAttributeFilters = mapOf(
                "model" to TypedAttributeFilterDraft(
                    op = com.example.shoppingassistant.domain.model.TypedAttributeOperator.IN,
                    valuesCsv = "iPhone 17 Pro",
                ),
            ),
            categoryCode = "TECH.PHONES",
        )

        assertEquals(
            listOf(
                ResultsTypedFacetLookupScope(brand = "Apple", model = "iPhone 17 Pro"),
                ResultsTypedFacetLookupScope(brand = "Samsung"),
            ),
            scopes,
        )
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_runtime_governance_values_over_static_seed() {
        val values = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            runtimeKnownValuesByAttributeCode = mapOf(
                "color" to listOf("Deep Blue", "Cosmic Orange", "Silver"),
            ),
            localeTag = "en",
        )

        assertEquals(listOf("Deep Blue", "Cosmic Orange", "Silver"), values)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_runtime_numeric_phone_values_for_richer_facts() {
        val releaseYears = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "release_year",
            attributeCode = "release_year",
            runtimeKnownValuesByAttributeCode = mapOf(
                "release_year" to listOf("2024", "2026"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("2024", "2026"), releaseYears)

        val screenSizes = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro",
            runtimeKey = "screen_size_inch",
            attributeCode = "screen_size_inch",
            runtimeKnownValuesByAttributeCode = mapOf(
                "screen_size_inch" to listOf("6.1", "6.3", "6.8"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("6.1", "6.3", "6.8"), screenSizes)

        val batteryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = "Galaxy S24+",
            runtimeKey = "battery_mah",
            attributeCode = "battery_mah",
            runtimeKnownValuesByAttributeCode = mapOf(
                "battery_mah" to listOf("4000", "4700", "5000"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("4000", "4700", "5000"), batteryValues)

        val wiredChargingValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro XL",
            runtimeKey = "wired_charging_w",
            attributeCode = "wired_charging_w",
            runtimeKnownValuesByAttributeCode = mapOf(
                "wired_charging_w" to listOf("25", "27", "45"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("25", "27", "45"), wiredChargingValues)

        val wirelessValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "wireless_charging",
            attributeCode = "wireless_charging",
            runtimeKnownValuesByAttributeCode = mapOf(
                "wireless_charging" to listOf("true"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("true"), wirelessValues)

        val esimValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "esim_support",
            attributeCode = "esim_support",
            runtimeKnownValuesByAttributeCode = mapOf(
                "esim_support" to listOf("true"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("true"), esimValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_runtime_richer_values_for_next_wave_phone_models() {
        val xiaomiReleaseYears = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Xiaomi 14",
            runtimeKey = "release_year",
            attributeCode = "release_year",
            runtimeKnownValuesByAttributeCode = mapOf(
                "release_year" to listOf("2024"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("2024"), xiaomiReleaseYears)

        val oneplusBattery = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus 13",
            runtimeKey = "battery_mah",
            attributeCode = "battery_mah",
            runtimeKnownValuesByAttributeCode = mapOf(
                "battery_mah" to listOf("5400", "6000"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("5400", "6000"), oneplusBattery)

        val oneplusWireless = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus 13",
            runtimeKey = "wireless_charging",
            attributeCode = "wireless_charging",
            runtimeKnownValuesByAttributeCode = mapOf(
                "wireless_charging" to listOf("true"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("true"), oneplusWireless)

        val nothingIpValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (2a)",
            runtimeKey = "ip_rating",
            attributeCode = "ip_rating",
            runtimeKnownValuesByAttributeCode = mapOf(
                "ip_rating" to listOf("IP54"),
            ),
            localeTag = "en",
        )
        assertEquals(listOf("IP54"), nothingIpValues)
    }

    @Test
    fun loadKnownTypedFacetValueAliases_prefers_runtime_governance_aliases_over_static_seed() {
        val aliases = loadKnownTypedFacetValueAliases(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            runtimeKnownValuesByAttributeCode = mapOf(
                "color" to listOf("Deep Blue"),
            ),
            runtimeKnownValueAliasesByAttributeCode = mapOf(
                "color" to mapOf(
                    "Deep Blue" to listOf("deep blue", "глубокий синий"),
                ),
            ),
            localeTag = "en",
        )

        assertEquals(listOf("Deep Blue"), aliases.keys.toList())
        assertTrue(aliases["Deep Blue"].orEmpty().contains("deep blue"))
        assertTrue(aliases["Deep Blue"].orEmpty().contains("глубокий синий"))
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_brand_scoped_os_for_apple_phones() {
        val values = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro",
            runtimeKey = "os_family",
            attributeCode = "os_family",
        )

        assertEquals(listOf("iOS"), values)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_brand_scoped_chipsets_for_google_phones_when_model_scope_is_absent() {
        val values = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 8 Pro",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )

        assertEquals(listOf("Tensor G2", "Tensor G3", "Tensor G4"), values)
    }

    @Test
    fun loadKnownTypedFacetValueAliases_filters_out_broad_os_values_when_brand_scope_exists() {
        val aliases = loadKnownTypedFacetValueAliases(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro",
            runtimeKey = "os_family",
            attributeCode = "os_family",
            localeTag = "en",
        )

        assertEquals(listOf("iOS"), aliases.keys.toList())
        assertTrue(aliases["iOS"].orEmpty().any { alias -> alias.equals("iphone os", ignoreCase = true) })
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_brand_scoped_network_and_ram_for_major_phone_brands() {
        val appleNetworkValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro",
            runtimeKey = "network_type",
            attributeCode = "network_type",
            localeTag = "en",
        )
        assertEquals(listOf("5G"), appleNetworkValues)

        val samsungRamValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = null,
            runtimeKey = "ram_gb",
            attributeCode = "ram_gb",
            localeTag = "en",
        )
        assertEquals(listOf("6 GB", "8 GB", "12 GB"), samsungRamValues)
    }

    @Test
    fun loadKnownTypedFacetValues_aggregates_brand_scoped_values_for_multi_brand_phone_search() {
        val values = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = null,
            lookupModel = null,
            lookupScopes = listOf(
                ResultsTypedFacetLookupScope(brand = "Apple"),
                ResultsTypedFacetLookupScope(brand = "Nothing"),
            ),
            runtimeKey = "network_type",
            attributeCode = "network_type",
            localeTag = "en",
        )

        assertEquals(listOf("5G", "3G", "4G"), values)
    }

    @Test
    fun loadKnownTypedFacetValues_aggregates_brand_scoped_model_values_for_multi_brand_phone_search() {
        val values = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = null,
            lookupModel = null,
            lookupScopes = listOf(
                ResultsTypedFacetLookupScope(brand = "Apple"),
                ResultsTypedFacetLookupScope(brand = "Samsung"),
            ),
            runtimeKey = "model",
            attributeCode = "model",
            localeTag = "en",
        )

        assertTrue(values.any { value -> value.equals("iPhone 16 Pro", ignoreCase = true) })
        assertTrue(values.any { value -> value.equals("Galaxy S24 Ultra", ignoreCase = true) })
    }

    @Test
    fun loadKnownTypedFacetValues_orders_phone_memory_values_naturally() {
        val values = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )

        assertEquals(listOf("128 GB", "256 GB", "512 GB", "1 TB"), values)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_iphone_17_pro() {
        val memoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB", "1 TB"), memoryValues)

        val colorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 17 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Silver", "Deep Blue", "Cosmic Orange"), colorValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_iphone_16_family() {
        val iphone16Colors = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "White", "Pink", "Teal", "Ultramarine"), iphone16Colors)

        val iphone16Memory = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB", "512 GB"), iphone16Memory)

        val iphone16ProColors = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "White", "Natural Titanium", "Desert Titanium"), iphone16ProColors)

        val iphone16ProMemory = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB", "512 GB", "1 TB"), iphone16ProMemory)

        val iphone16ProMaxMemory = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Apple",
            lookupModel = "iPhone 16 Pro Max",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB", "1 TB"), iphone16ProMaxMemory)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_galaxy_s24_ultra() {
        val memoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = "Galaxy S24 Ultra",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB", "1 TB"), memoryValues)

        val ramValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = "Galaxy S24 Ultra",
            runtimeKey = "ram_gb",
            attributeCode = "ram_gb",
            localeTag = "en",
        )
        assertEquals(listOf("12 GB"), ramValues)

        val colorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = "Galaxy S24 Ultra",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "Gray", "Purple", "Yellow"), colorValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_galaxy_s24_and_s24_plus() {
        val s24MemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = "Galaxy S24",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB", "512 GB"), s24MemoryValues)

        val s24PlusRamValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Samsung",
            lookupModel = "Galaxy S24 Plus",
            runtimeKey = "ram_gb",
            attributeCode = "ram_gb",
            localeTag = "en",
        )
        assertEquals(listOf("12 GB"), s24PlusRamValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_pixel_9_pro() {
        val memoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB", "512 GB", "1 TB"), memoryValues)

        val ramValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro",
            runtimeKey = "ram_gb",
            attributeCode = "ram_gb",
            localeTag = "en",
        )
        assertEquals(listOf("16 GB"), ramValues)

        val colorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "White", "Pink", "Hazel"), colorValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_pixel_9_and_pixel_9_pro_xl() {
        val pixel9ColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "White", "Green", "Pink"), pixel9ColorValues)

        val pixel9ProXlMemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro XL",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB", "512 GB", "1 TB"), pixel9ProXlMemoryValues)

        val pixel9ProXlChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Google",
            lookupModel = "Pixel 9 Pro XL",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Tensor G4"), pixel9ProXlChipsetValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_huawei_p60_pro() {
        val memoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Huawei",
            lookupModel = "Huawei P60 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB"), memoryValues)

        val networkValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Huawei",
            lookupModel = "Huawei P60 Pro",
            runtimeKey = "network_type",
            attributeCode = "network_type",
            localeTag = "en",
        )
        assertEquals(listOf("4G"), networkValues)

        val chipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Huawei",
            lookupModel = "Huawei P60 Pro",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 8+ Gen 1"), chipsetValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_oneplus_13_and_nothing_phone_2a() {
        val oneplusMemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus 13",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB"), oneplusMemoryValues)

        val oneplusChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus 13",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 8 Elite"), oneplusChipsetValues)

        val nothingColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (2a)",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "White"), nothingColorValues)

        val nothingRamValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (2a)",
            runtimeKey = "ram_gb",
            attributeCode = "ram_gb",
            localeTag = "en",
        )
        assertEquals(listOf("8 GB", "12 GB"), nothingRamValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_oneplus_12_and_nothing_phone_2() {
        val oneplus12ColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus 12",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Green", "Black"), oneplus12ColorValues)

        val oneplus12ChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus 12",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 8 Gen 3"), oneplus12ChipsetValues)

        val nothing2MemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (2)",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB", "512 GB"), nothing2MemoryValues)

        val nothing2ColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (2)",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Gray", "White"), nothing2ColorValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_nord4_xiaomi14_and_nothing_phone_1() {
        val nord4ColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus Nord 4",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Silver", "Black", "Green"), nord4ColorValues)

        val nord4ChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "OnePlus",
            lookupModel = "OnePlus Nord 4",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 7+ Gen 3"), nord4ChipsetValues)

        val xiaomi14MemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Xiaomi 14",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB"), xiaomi14MemoryValues)

        val xiaomi14ColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Xiaomi 14",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Green", "Black", "White"), xiaomi14ColorValues)

        val nothing1MemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (1)",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("128 GB", "256 GB"), nothing1MemoryValues)

        val nothing1ChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Nothing",
            lookupModel = "Nothing Phone (1)",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 778G+"), nothing1ChipsetValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_redmi_poco_and_honor_wave() {
        val redmiColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Redmi Note 13 Pro Plus",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "White", "Purple", "Silver"), redmiColorValues)

        val redmiChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Redmi Note 13 Pro Plus",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Dimensity 7200 Ultra"), redmiChipsetValues)

        val pocoMemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO F6 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB", "1 TB"), pocoMemoryValues)

        val pocoChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO F6 Pro",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 8 Gen 2"), pocoChipsetValues)

        val honorColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Honor",
            lookupModel = "Honor 200 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Cyan", "White", "Black"), honorColorValues)

        val honorChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Honor",
            lookupModel = "Honor 200 Pro",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 8s Gen 3"), honorChipsetValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_poco_and_honor_next_wave() {
        val pocoX6ProColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO X6 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Yellow", "Gray", "Black"), pocoX6ProColorValues)

        val pocoF6MemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO F6",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB"), pocoF6MemoryValues)

        val pocoF6ChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO F6",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 8s Gen 3"), pocoF6ChipsetValues)

        val honor200ColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Honor",
            lookupModel = "Honor 200",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("White", "Green", "Pink", "Black"), honor200ColorValues)

        val honor200ChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Honor",
            lookupModel = "Honor 200",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 7 Gen 3"), honor200ChipsetValues)

        val honorMagic6ProMemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Honor",
            lookupModel = "Honor Magic6 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("512 GB"), honorMagic6ProMemoryValues)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_poco_x7_pro_redmi_note_14_pro_plus_and_realme_gt7_pro() {
        val pocoX7ProChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO X7 Pro",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Dimensity 8400 Ultra"), pocoX7ProChipsetValues)

        val pocoX7ProMemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO X7 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB"), pocoX7ProMemoryValues)

        val redmi14ProPlusColors = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Redmi Note 14 Pro Plus",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "Purple", "Blue", "Gold"), redmi14ProPlusColors)

        val redmi14ProPlusChipset = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Redmi Note 14 Pro Plus",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 7s Gen 3"), redmi14ProPlusChipset)

        val realmeGt7ProColors = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "realme",
            lookupModel = "realme GT 7 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Orange", "Gray"), realmeGt7ProColors)

        val realmeGt7ProMemory = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "realme",
            lookupModel = "realme GT 7 Pro",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("256 GB", "512 GB"), realmeGt7ProMemory)
    }

    @Test
    fun loadKnownTypedFacetValues_prefers_model_scoped_values_for_verified_xiaomi_and_realme_wave() {
        val xiaomi13TColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Xiaomi 13T",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Blue", "Green", "Black"), xiaomi13TColorValues)

        val xiaomi13TChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Xiaomi 13T",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Dimensity 8200 Ultra"), xiaomi13TChipsetValues)

        val xiaomi14UltraMemoryValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Xiaomi 14 Ultra",
            runtimeKey = "memory_gb",
            attributeCode = "memory_gb",
            localeTag = "en",
        )
        assertEquals(listOf("512 GB"), xiaomi14UltraMemoryValues)

        val redmiNote13ProColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Redmi Note 13 Pro",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Black", "Purple", "Cyan", "Green"), redmiNote13ProColorValues)

        val redmiNote13ProNetworkValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "Redmi Note 13 Pro",
            runtimeKey = "network_type",
            attributeCode = "network_type",
            localeTag = "en",
        )
        assertEquals(listOf("4G"), redmiNote13ProNetworkValues)

        val pocoX6ChipsetValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "Xiaomi",
            lookupModel = "POCO X6",
            runtimeKey = "chipset_family",
            attributeCode = "chipset_family",
            localeTag = "en",
        )
        assertEquals(listOf("Snapdragon 7s Gen 2"), pocoX6ChipsetValues)

        val realmeGt6RamValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "realme",
            lookupModel = "realme GT 6",
            runtimeKey = "ram_gb",
            attributeCode = "ram_gb",
            localeTag = "en",
        )
        assertEquals(listOf("8 GB", "12 GB", "16 GB"), realmeGt6RamValues)

        val realme12ProPlusColorValues = loadKnownTypedFacetValues(
            spec = null,
            categoryCode = "TECH.PHONES",
            lookupBrand = "realme",
            lookupModel = "realme 12 Pro Plus",
            runtimeKey = "color",
            attributeCode = "color",
            localeTag = "en",
        )
        assertEquals(listOf("Blue", "Beige", "Red"), realme12ProPlusColorValues)
    }
}
