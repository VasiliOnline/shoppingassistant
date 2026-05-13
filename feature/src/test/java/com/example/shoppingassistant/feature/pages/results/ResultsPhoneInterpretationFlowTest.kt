package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.search.InterpretedSearchIntent
import com.example.shoppingassistant.domain.search.SearchInterpretationProvenance
import com.example.shoppingassistant.domain.search.SearchIntentMode
import com.example.shoppingassistant.domain.search.SearchRequestSource
import com.example.shoppingassistant.feature.pages.model.AttributeDef
import com.example.shoppingassistant.feature.pages.model.matchFreeQueryAttributeValue
import com.example.shoppingassistant.feature.pages.model.parseFreeQueryAttributes
import com.example.shoppingassistant.feature.pages.model.toFeatureParseableAttributeDefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsPhoneInterpretationFlowTest {

    private val repository = SeedBackedCatalogRepository()

    @Test
    fun parses_refresh_rate_and_chipset_from_s24_ultra_query() = runBlocking {
        val interpreted = interpretPhoneQuery("s24 ultra 120гц snapdragon 8 gen 3 256gb")
        val query = interpreted.query

        assertEquals("Samsung", query.brand)
        assertTrue("Expected Galaxy S24 Ultra model, got '${query.model}'", query.model.startsWith("Galaxy S24 Ultra"))
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("SNAPDRAGON_8_GEN_3", query.attributes["chipset_family"]?.asRawString())
        assertEquals("256", query.attributes["memory_gb"]?.asRawString())
    }

    @Test
    fun parses_compact_s24u_query_with_russian_snapdragon_alias() = runBlocking {
        val interpreted = interpretPhoneQuery("s24u 120гц снап 8 ген 3 256гб")
        val query = interpreted.query

        assertEquals("Samsung", query.brand)
        assertTrue(query.model.startsWith("Galaxy S24 Ultra"))
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("SNAPDRAGON_8_GEN_3", query.attributes["chipset_family"]?.asRawString())
        assertEquals("256", query.attributes["memory_gb"]?.asRawString())
    }

    @Test
    fun parses_tensor_g4_and_refresh_rate_from_pixel_query() = runBlocking {
        val interpreted = interpretPhoneQuery("pixel 9 120гц tensor g4 128gb")
        val query = interpreted.query

        assertEquals("Google", query.brand)
        assertTrue(query.model.startsWith("Pixel 9"))
        assertEquals("128", interpreted.parsedAttrs["memory_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("TENSOR_G4", interpreted.parsedAttrs["chipset_family"])
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("TENSOR_G4", query.attributes["chipset_family"]?.asRawString())
        assertEquals("128", query.attributes["memory_gb"]?.asRawString())
    }

    @Test
    fun parses_pixel_9_wintergreen_query_with_model_scoped_color() = runBlocking {
        val interpreted = interpretPhoneQuery("pixel9 wintergreen 256gb tensorg4")
        val query = interpreted.query

        assertEquals("Google", query.brand)
        assertTrue(query.model.startsWith("Pixel 9"))
        assertEquals("GREEN", interpreted.parsedAttrs["color"])
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("TENSOR_G4", interpreted.parsedAttrs["chipset_family"])
        assertEquals("GREEN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_russian_pixel_query_with_tensor_alias() = runBlocking {
        val interpreted = interpretPhoneQuery("пиксель 9 120гц тенсор g4 128гб")
        val query = interpreted.query

        assertEquals("Google", query.brand)
        assertTrue(query.model.startsWith("Pixel 9"))
        assertEquals("128", interpreted.parsedAttrs["memory_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("TENSOR_G4", interpreted.parsedAttrs["chipset_family"])
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("TENSOR_G4", query.attributes["chipset_family"]?.asRawString())
        assertEquals("128", query.attributes["memory_gb"]?.asRawString())
    }

    @Test
    fun parses_iphone_16_query_with_verified_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("iphone16 teal 256gb a18 5g")
        val query = interpreted.query

        assertEquals("Apple", query.brand)
        assertTrue(query.model.startsWith("iPhone 16"))
        assertTrue(!query.model.startsWith("iPhone 16 Pro"))
        assertEquals("TEAL", interpreted.parsedAttrs["color"])
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("A18", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("TEAL", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_iphone_16_pro_query_with_verified_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("iphone16pro desert titanium 1tb 120hz a18pro 5g")
        val query = interpreted.query

        assertEquals("Apple", query.brand)
        assertTrue(query.model.startsWith("iPhone 16 Pro"))
        assertTrue(!query.model.startsWith("iPhone 16 Pro Max"))
        assertEquals("DESERT_TITANIUM", interpreted.parsedAttrs["color"])
        assertEquals("1024", interpreted.parsedAttrs["memory_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("A18_PRO", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("DESERT_TITANIUM", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_s24_plus_query_with_official_color_alias() = runBlocking {
        val interpreted = interpretPhoneQuery("s24+ amber yellow 512gb 12ram 120hz")
        val query = interpreted.query

        assertEquals("Samsung", query.brand)
        assertTrue(query.model.startsWith("Galaxy S24 Plus"))
        assertEquals("YELLOW", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("YELLOW", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_kirin_9000s_from_compact_huawei_query() = runBlocking {
        val interpreted = interpretPhoneQuery("huaweip60pro256gb кирин 9000 с")
        val query = interpreted.query

        assertEquals("Huawei", query.brand)
        assertTrue(query.model.startsWith("Huawei P60 Pro"))
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("KIRIN_9000S", interpreted.parsedAttrs["chipset_family"])
        assertEquals("KIRIN_9000S", query.attributes["chipset_family"]?.asRawString())
        assertEquals("256", query.attributes["memory_gb"]?.asRawString())
    }

    @Test
    fun parses_verified_huawei_p60_pro_query_with_snapdragon_and_4g() = runBlocking {
        val interpreted = interpretPhoneQuery("huaweip60pro512gb 4g snapdragon 8 plus gen 1")
        val query = interpreted.query

        assertEquals("Huawei", query.brand)
        assertTrue(query.model.startsWith("Huawei P60 Pro"))
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("4G", interpreted.parsedAttrs["network_type"])
        assertEquals("SNAPDRAGON_8_PLUS_GEN_1", interpreted.parsedAttrs["chipset_family"])
        assertEquals("512", query.attributes["memory_gb"]?.asRawString())
        assertEquals("4G", query.attributes["network_type"]?.asRawString())
        assertEquals("SNAPDRAGON_8_PLUS_GEN_1", query.attributes["chipset_family"]?.asRawString())
    }

    @Test
    fun matches_russian_5_dji_against_network_type_def() = runBlocking {
        val spec = requireNotNull(repository.getCategoryEffectiveSpec("TECH.PHONES"))
        val defs = spec.toFeatureParseableAttributeDefs()
        val networkTypeDef = defs.firstOrNull { it.key == "network_type" }

        requireNotNull(networkTypeDef)
        assertEquals("5G", matchFreeQueryAttributeValue(networkTypeDef, "5 джи"))
    }

    @Test
    fun matches_curated_sd7gen3_against_phone_chipset_def() = runBlocking {
        val spec = requireNotNull(repository.getCategoryEffectiveSpec("TECH.PHONES"))
        val defs = spec.toFeatureParseableAttributeDefs()
        val chipsetDef = defs.firstOrNull { it.key == "chipset_family" }

        requireNotNull(chipsetDef)
        assertTrue(
            "Expected SNAPDRAGON_7_GEN_3 in parseable chipset defs, got ${
                chipsetDef.allowedValues.map { it.code }
            }",
            chipsetDef.allowedValues.any { it.code == "SNAPDRAGON_7_GEN_3" },
        )
        assertTrue(
            "Expected sd7gen3 alias in parseable chipset defs, got ${
                chipsetDef.allowedValues.firstOrNull { it.code == "SNAPDRAGON_7_GEN_3" }?.synonyms
            }",
            chipsetDef.allowedValues
                .firstOrNull { it.code == "SNAPDRAGON_7_GEN_3" }
                ?.synonyms
                ?.any { it.equals("sd7gen3", ignoreCase = true) } == true,
        )
        assertEquals("SNAPDRAGON_7_GEN_3", matchFreeQueryAttributeValue(chipsetDef, "sd7gen3"))
    }

    @Test
    fun parses_long_tail_poco_query_with_ram_network_and_chipset() = runBlocking {
        val interpreted = interpretPhoneQuery("pocof6pro512gb 12гб озу 120гц снап 8 ген 2 5 джи")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("POCO F6 Pro"))
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_2", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("512", query.attributes["memory_gb"]?.asRawString())
        assertEquals("12", query.attributes["ram_gb"]?.asRawString())
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("SNAPDRAGON_8_GEN_2", query.attributes["chipset_family"]?.asRawString())
        assertEquals("5G", query.attributes["network_type"]?.asRawString())
    }

    @Test
    fun parses_compact_nothing_query_with_ram_network_and_refresh_rate() = runBlocking {
        val interpreted = interpretPhoneQuery("nothingphone1128gb 8гб озу 120гц 5 джи")
        val query = interpreted.query

        assertEquals("Nothing", query.brand)
        assertTrue(query.model.startsWith("Nothing Phone (1)"))
        assertEquals("128", interpreted.parsedAttrs["memory_gb"])
        assertEquals("8", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("128", query.attributes["memory_gb"]?.asRawString())
        assertEquals("8", query.attributes["ram_gb"]?.asRawString())
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("5G", query.attributes["network_type"]?.asRawString())
    }

    @Test
    fun parses_compact_poco_query_with_sd8gen2_and_ram_suffix() = runBlocking {
        val interpreted = interpretPhoneQuery("pocof6pro512gb 12ram 120hz sd8gen2 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("POCO F6 Pro"))
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_2", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("512", query.attributes["memory_gb"]?.asRawString())
        assertEquals("12", query.attributes["ram_gb"]?.asRawString())
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("SNAPDRAGON_8_GEN_2", query.attributes["chipset_family"]?.asRawString())
        assertEquals("5G", query.attributes["network_type"]?.asRawString())
    }

    @Test
    fun parses_redmi_note_13_pro_plus_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("redminote13proplus aurora purple 512gb 12ram 120hz dimensity7200ultra 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("Redmi Note 13 Pro Plus"))
        assertEquals("PURPLE", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("DIMENSITY_7200_ULTRA", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("PURPLE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_honor_200_pro_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("honor200pro ocean cyan 512gb 12ram 120hz sd8sgen3 5g")
        val query = interpreted.query

        assertEquals("Honor", query.brand)
        assertTrue(query.model.startsWith("Honor 200 Pro"))
        assertEquals("CYAN", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8S_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("CYAN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_compact_pixel_query_with_tensor_alias_and_ram_suffix() = runBlocking {
        val interpreted = interpretPhoneQuery("pixel9pro256gb 16ram 120hz tensorg4 5g")
        val query = interpreted.query

        assertEquals("Google", query.brand)
        assertTrue(query.model.startsWith("Pixel 9 Pro"))
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("TENSOR_G4", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("256", query.attributes["memory_gb"]?.asRawString())
        assertEquals("16", query.attributes["ram_gb"]?.asRawString())
        assertEquals("120", query.attributes["refresh_rate_hz"]?.asRawString())
        assertEquals("TENSOR_G4", query.attributes["chipset_family"]?.asRawString())
        assertEquals("5G", query.attributes["network_type"]?.asRawString())
    }

    @Test
    fun parses_pixel_9_pro_hazel_query_with_model_scoped_color() = runBlocking {
        val interpreted = interpretPhoneQuery("pixel9pro256gb hazel 16ram 120hz tensorg4 5g")
        val query = interpreted.query

        assertEquals("Google", query.brand)
        assertTrue(query.model.startsWith("Pixel 9 Pro"))
        assertEquals("HAZEL", interpreted.parsedAttrs["color"])
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("TENSOR_G4", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("HAZEL", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_pixel_9_pro_xl_rose_quartz_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("pixel9proxl rose quartz 512gb 16ram tensorg4")
        val query = interpreted.query

        assertEquals("Google", query.brand)
        assertTrue(query.model.startsWith("Pixel 9 Pro XL"))
        assertEquals("PINK", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("TENSOR_G4", interpreted.parsedAttrs["chipset_family"])
        assertEquals("PINK", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_s24_ultra_titanium_gray_query_with_model_scoped_color() = runBlocking {
        val interpreted = interpretPhoneQuery("s24 ultra titanium gray 512gb 120hz sd8gen3")
        val query = interpreted.query

        assertEquals("Samsung", query.brand)
        assertTrue(query.model.startsWith("Galaxy S24 Ultra"))
        assertEquals("GRAY", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("GRAY", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_iphone_17_pro_query_with_model_scoped_memory_and_color() = runBlocking {
        val interpreted = interpretPhoneQuery("iphone17pro white 256gb")
        val query = interpreted.query

        assertEquals("Apple", query.brand)
        assertTrue(query.model.startsWith("iPhone 17 Pro"))
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("WHITE", interpreted.parsedAttrs["color"])
        assertEquals("256", query.attributes["memory_gb"]?.asRawString())
        assertEquals("WHITE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_spaced_iphone_17_pro_query_with_brand_color_and_memory() = runBlocking {
        val interpreted = interpretPhoneQuery("Apple iPhone17 pro orange 512gb")
        val query = interpreted.query

        assertEquals("Apple", query.brand)
        assertTrue(
            "Expected iPhone 17 Pro model, got '${query.model}', parsed=${interpreted.parsedAttrs}, queryAttrs=${query.attributes.mapValues { it.value.asRawString() }}",
            query.model.startsWith("iPhone 17 Pro"),
        )
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("512", query.attributes["memory_gb"]?.asRawString())
        assertEquals("ORANGE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_oneplus_13_midnight_ocean_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("oneplus13 midnight ocean 512gb 16ram 120hz sd8elite")
        val query = interpreted.query

        assertEquals("OnePlus", query.brand)
        assertTrue(query.model.startsWith("OnePlus 13"))
        assertEquals("BLUE", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_ELITE", interpreted.parsedAttrs["chipset_family"])
        assertEquals("BLUE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_nothing_phone_2a_milk_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("nothing2a milk 256gb 12ram dimensity7200pro 5g")
        val query = interpreted.query

        assertEquals("Nothing", query.brand)
        assertTrue(query.model.startsWith("Nothing Phone"))
        assertEquals("WHITE", interpreted.parsedAttrs["color"])
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("DIMENSITY_7200_PRO", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("WHITE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_oneplus_12_flowy_emerald_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("oneplus12 flowy emerald 512gb 16ram 120hz sd8gen3")
        val query = interpreted.query

        assertEquals("OnePlus", query.brand)
        assertTrue(query.model.startsWith("OnePlus 12"))
        assertEquals("GREEN", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("GREEN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_nothing_phone_2_dark_gray_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("nothing2 dark gray 512gb 12ram snapdragon 8 plus gen 1 5g")
        val query = interpreted.query

        assertEquals("Nothing", query.brand)
        assertTrue(query.model.startsWith("Nothing Phone"))
        assertEquals("GRAY", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("SNAPDRAGON_8_PLUS_GEN_1", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("GRAY", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_oneplus_nord4_mercurial_silver_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("oneplusnord4 mercurial silver 512gb 16ram 120hz snapdragon 7 plus gen 3 5g")
        val query = interpreted.query

        assertEquals("OnePlus", query.brand)
        assertTrue(query.model.startsWith("OnePlus Nord 4"))
        assertEquals("SILVER", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_7_PLUS_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("SILVER", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_xiaomi_14_jade_green_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("xiaomi14 jade green 512gb 12ram 120hz sd8gen3 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("Xiaomi 14"))
        assertEquals("GREEN", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("GREEN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_nothing_phone_1_white_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("nothingphone1 white 256gb 12ram 120hz snapdragon 778g plus 5g")
        val query = interpreted.query

        assertEquals("Nothing", query.brand)
        assertTrue(query.model.startsWith("Nothing Phone (1)"))
        assertEquals("WHITE", interpreted.parsedAttrs["color"])
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_778G_PLUS", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("WHITE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_poco_x6_pro_yellow_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("pocox6pro poco yellow 512gb 12ram 120hz dimensity8300ultra 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("POCO X6 Pro"))
        assertEquals("YELLOW", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("DIMENSITY_8300_ULTRA", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("YELLOW", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_poco_f6_titanium_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("pocof6 titanium 512gb 12ram 120hz sd8sgen3 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("POCO F6"))
        assertEquals("TITANIUM", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8S_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("TITANIUM", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_honor_200_emerald_green_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("honor200 emerald green 512gb 12ram 120hz sd7gen3 5g")
        val query = interpreted.query

        assertEquals("Honor", query.brand)
        assertTrue(query.model.startsWith("Honor 200"))
        assertEquals("GREEN", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_7_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("GREEN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_honor_magic6_pro_epi_green_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("honormagic6pro epi green 512gb 12ram 120hz sd8gen3 5g")
        val query = interpreted.query

        assertEquals("Honor", query.brand)
        assertTrue(query.model.startsWith("Honor Magic6 Pro"))
        assertEquals("GREEN", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("GREEN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_poco_x7_pro_yellow_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("pocox7pro yellow 512gb 12ram 120hz dimensity8400ultra 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("POCO X7 Pro"))
        assertEquals("YELLOW", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("DIMENSITY_8400_ULTRA", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("YELLOW", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_redmi_note_14_pro_plus_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("redminote14proplus lavender purple 512gb 12ram 120hz sd7sgen3 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("Redmi Note 14 Pro Plus"))
        assertEquals("PURPLE", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_7S_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("PURPLE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_realme_gt7_pro_query_with_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("realmegt7pro mars orange 512gb 16ram 120hz sd8elite 5g")
        val query = interpreted.query

        assertEquals("realme", query.brand)
        assertTrue(query.model.startsWith("realme GT 7 Pro"))
        assertEquals("ORANGE", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_ELITE", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("ORANGE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_xiaomi_13t_query_with_verified_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("xiaomi13t alpine blue 256gb 12ram 144hz dimensity8200ultra 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("Xiaomi 13T"))
        assertEquals("BLUE", interpreted.parsedAttrs["color"])
        assertEquals("256", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("144", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("DIMENSITY_8200_ULTRA", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("BLUE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_xiaomi_14_ultra_query_with_verified_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("xiaomi14ultra white 512gb 16ram 120hz sd8gen3 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("Xiaomi 14 Ultra"))
        assertEquals("WHITE", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("16", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8_GEN_3", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
        assertEquals("WHITE", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_redmi_note_13_pro_query_with_verified_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("redminote13pro ocean teal 512gb 12ram 120hz heliog99ultra 4g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("Redmi Note 13 Pro"))
        assertEquals("CYAN", interpreted.parsedAttrs["color"])
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("HELIO_G99_ULTRA", interpreted.parsedAttrs["chipset_family"])
        assertEquals("4G", interpreted.parsedAttrs["network_type"])
        assertEquals("CYAN", query.attributes["color"]?.asRawString())
    }

    @Test
    fun parses_poco_x6_query_with_verified_model_scoped_values() = runBlocking {
        val interpreted = interpretPhoneQuery("pocox6 blue 512gb 12ram 120hz sd7sgen2 5g")
        val query = interpreted.query

        assertEquals("Xiaomi", query.brand)
        assertTrue(query.model.startsWith("POCO X6"))
        assertTrue(!query.model.startsWith("POCO X6 Pro"))
        assertEquals("512", interpreted.parsedAttrs["memory_gb"])
        assertEquals("12", interpreted.parsedAttrs["ram_gb"])
        assertEquals("120", interpreted.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_7S_GEN_2", interpreted.parsedAttrs["chipset_family"])
        assertEquals("5G", interpreted.parsedAttrs["network_type"])
    }

    @Test
    fun parses_realme_verified_wave_queries_with_model_scoped_values() = runBlocking {
        val gt6 = interpretPhoneQuery("realmegt6 razor green 512gb 16ram 120hz sd8sgen3 5g")
        assertEquals("realme", gt6.query.brand)
        assertTrue(gt6.query.model.startsWith("realme GT 6"))
        assertEquals("GREEN", gt6.parsedAttrs["color"])
        assertEquals("512", gt6.parsedAttrs["memory_gb"])
        assertEquals("16", gt6.parsedAttrs["ram_gb"])
        assertEquals("120", gt6.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_8S_GEN_3", gt6.parsedAttrs["chipset_family"])
        assertEquals("5G", gt6.parsedAttrs["network_type"])

        val realme12ProPlus = interpretPhoneQuery("realme12proplus submarine blue 512gb 12ram 120hz sd7sgen2 5g")
        assertEquals("realme", realme12ProPlus.query.brand)
        assertTrue(realme12ProPlus.query.model.startsWith("realme 12 Pro Plus"))
        assertEquals("BLUE", realme12ProPlus.parsedAttrs["color"])
        assertEquals("512", realme12ProPlus.parsedAttrs["memory_gb"])
        assertEquals("12", realme12ProPlus.parsedAttrs["ram_gb"])
        assertEquals("120", realme12ProPlus.parsedAttrs["refresh_rate_hz"])
        assertEquals("SNAPDRAGON_7S_GEN_2", realme12ProPlus.parsedAttrs["chipset_family"])
        assertEquals("5G", realme12ProPlus.parsedAttrs["network_type"])
    }

    private suspend fun interpretPhoneQuery(raw: String): ParsedPhoneQuery {
        val spec = requireNotNull(repository.getCategoryEffectiveSpec("TECH.PHONES"))
        val defs = spec.toFeatureParseableAttributeDefs()
        assertPhoneParseableDefs(defs)
        val attrs = parseFreeQueryAttributes(
            queryText = raw,
            attributeDefs = defs,
        )
        val intent = InterpretedSearchIntent(
            queryText = raw,
            categoryCode = "TECH.PHONES",
            attrs = attrs,
            mode = SearchIntentMode.QUERY,
            provenance = SearchInterpretationProvenance(
                source = SearchRequestSource.RAW_TEXT,
                usedParsedAttributes = attrs.isNotEmpty(),
            ),
        )
        return ParsedPhoneQuery(
            query = requireNotNull(intent.toResultsQuery()),
            parsedAttrs = attrs,
        )
    }

    private fun assertPhoneParseableDefs(defs: List<AttributeDef>) {
        val keys = defs.map { it.key }
        assertEquals("Expected exactly one network_type def, got $keys", 1, keys.count { it == "network_type" })
        assertTrue("Expected color in parseable defs, got $keys", "color" in keys)
        assertTrue("Expected memory_gb in parseable defs, got $keys", "memory_gb" in keys)
        assertTrue("Expected ram_gb in parseable defs, got $keys", "ram_gb" in keys)
        assertTrue("Expected network_type in parseable defs, got $keys", "network_type" in keys)
        assertTrue("Expected refresh_rate_hz in parseable defs, got $keys", "refresh_rate_hz" in keys)
        assertTrue("Expected chipset_family in parseable defs, got $keys", "chipset_family" in keys)
    }
}

private data class ParsedPhoneQuery(
    val query: NormalizedQuery,
    val parsedAttrs: Map<String, String>,
)

private class SeedBackedCatalogRepository : CatalogReadRepository {
    private val specsByCode = CatalogSeed.categoryWriteSpecs.associateBy { it.category.code }

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val spec = specsByCode[categoryCode.trim().uppercase()] ?: return null
        val constraints = resolveConstraints(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
        return spec.toCategoryEffectiveSpec(constraints = constraints)
    }

    private fun resolveConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        val normalizedCategoryCode = categoryCode.trim()
        if (normalizedCategoryCode.isBlank()) return emptyList()
        return CatalogSeed.constraints.filter { constraint ->
            when (constraint.scope) {
                ConstraintScope.GLOBAL -> true
                ConstraintScope.CATEGORY ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true
                ConstraintScope.BRAND ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand.orEmpty(), ignoreCase = true) == true
                ConstraintScope.MODEL ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand.orEmpty(), ignoreCase = true) == true &&
                        constraint.model?.equals(model.orEmpty(), ignoreCase = true) == true
            }
        }
    }
}
