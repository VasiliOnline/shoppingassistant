package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogGovernanceCuratedSeedTest {

    @Test
    fun projects_value_dictionary_aliases_for_top_tech_wave() {
        val colorDictionary = CatalogGovernanceCuratedSeed.projectedValueDictionaries()
            .firstOrNull { it.attributeCode == "color" }

        requireNotNull(colorDictionary)
        val grayEntry = colorDictionary.entries.firstOrNull { it.canonicalCode == "GRAY" }
        requireNotNull(grayEntry)
        assertTrue("graphite" in grayEntry.synonyms)
        assertTrue("космический серый" in grayEntry.synonyms)
    }

    @Test
    fun projects_phone_models_for_top_tech_wave() {
        val models = CatalogGovernanceCuratedSeed.projectedModels()
        val families = CatalogGovernanceCuratedSeed.projectedProductFamilies()

        val iphone = models.firstOrNull { it.modelCode == "IPHONE_16_PRO" }
        requireNotNull(iphone)
        assertEquals("TECH.PHONES", iphone.defaultCategoryCode)
        assertEquals("Apple", iphone.brandCanonical)
        assertEquals("iPhone 16 Pro", iphone.canonicalModel)
        assertTrue("айфон 16 про" in iphone.modelAliases)

        val samsung = models.firstOrNull { it.modelCode == "GALAXY_S24_ULTRA" }
        requireNotNull(samsung)
        assertTrue("s24 ultra" in samsung.modelAliases)

        val samsungPlus = models.firstOrNull { it.modelCode == "GALAXY_S24_PLUS" }
        requireNotNull(samsungPlus)
        assertTrue("s24 plus" in samsungPlus.modelAliases)

        val iphonePm = models.firstOrNull { it.modelCode == "IPHONE_16_PRO_MAX" }
        requireNotNull(iphonePm)
        assertTrue("iphone 16 pm" in iphonePm.modelAliases)
        assertTrue("iphone16promax" in iphonePm.modelAliases)

        val iphone16 = models.firstOrNull { it.modelCode == "IPHONE_16" }
        requireNotNull(iphone16)
        assertTrue("iphone16" in iphone16.modelAliases)

        val iphone16Plus = models.firstOrNull { it.modelCode == "IPHONE_16_PLUS" }
        requireNotNull(iphone16Plus)
        assertTrue("iphone16plus" in iphone16Plus.modelAliases)

        val iphone16Pro = models.firstOrNull { it.modelCode == "IPHONE_16_PRO" }
        requireNotNull(iphone16Pro)
        assertTrue("iphone16pro" in iphone16Pro.modelAliases)

        val iphone17Pro = models.firstOrNull { it.modelCode == "IPHONE_17_PRO" }
        requireNotNull(iphone17Pro)
        assertTrue("iphone17pro" in iphone17Pro.modelAliases)

        val iphone17ProMax = models.firstOrNull { it.modelCode == "IPHONE_17_PRO_MAX" }
        requireNotNull(iphone17ProMax)
        assertTrue("iphone 17 pm" in iphone17ProMax.modelAliases)

        val huawei = models.firstOrNull { it.modelCode == "HUAWEI_P60" }
        requireNotNull(huawei)
        assertTrue("huawei p60" in huawei.modelAliases)

        val xiaomi = models.firstOrNull { it.modelCode == "XIAOMI_13T" }
        requireNotNull(xiaomi)
        assertTrue("xiaomi 13t" in xiaomi.modelAliases)

        val poco = models.firstOrNull { it.modelCode == "POCO_F6_PRO" }
        requireNotNull(poco)
        assertTrue("poco f6 pro" in poco.modelAliases)
        assertTrue("pocof6pro" in poco.modelAliases)

        val redmiNote13ProPlus = models.firstOrNull { it.modelCode == "REDMI_NOTE_13_PRO_PLUS" }
        requireNotNull(redmiNote13ProPlus)
        assertTrue("redminote13proplus" in redmiNote13ProPlus.modelAliases)

        val honor200Pro = models.firstOrNull { it.modelCode == "HONOR_200_PRO" }
        requireNotNull(honor200Pro)
        assertTrue("honor200pro" in honor200Pro.modelAliases)

        val pocoX6Pro = models.firstOrNull { it.modelCode == "POCO_X6_PRO" }
        requireNotNull(pocoX6Pro)
        assertTrue("pocox6pro" in pocoX6Pro.modelAliases)

        val pocoF6 = models.firstOrNull { it.modelCode == "POCO_F6" }
        requireNotNull(pocoF6)
        assertTrue("pocof6" in pocoF6.modelAliases)

        val honorMagic6Pro = models.firstOrNull { it.modelCode == "HONOR_MAGIC6_PRO" }
        requireNotNull(honorMagic6Pro)
        assertTrue("honormagic6pro" in honorMagic6Pro.modelAliases)

        val honor200 = models.firstOrNull { it.modelCode == "HONOR_200" }
        requireNotNull(honor200)
        assertTrue("honor200" in honor200.modelAliases)

        val huaweiPro = models.firstOrNull { it.modelCode == "HUAWEI_P60_PRO" }
        requireNotNull(huaweiPro)
        assertTrue("p60 pro" in huaweiPro.modelAliases)

        val pixel9ProXl = models.firstOrNull { it.modelCode == "PIXEL_9_PRO_XL" }
        requireNotNull(pixel9ProXl)
        assertTrue("pixel9proxl" in pixel9ProXl.modelAliases)

        val oneplus13 = models.firstOrNull { it.modelCode == "ONEPLUS_13" }
        requireNotNull(oneplus13)
        assertTrue("oneplus13" in oneplus13.modelAliases)

        val oneplus12 = models.firstOrNull { it.modelCode == "ONEPLUS_12" }
        requireNotNull(oneplus12)
        assertTrue("oneplus12" in oneplus12.modelAliases)

        val nothing2a = models.firstOrNull { it.modelCode == "NOTHING_PHONE_2A" }
        requireNotNull(nothing2a)
        assertTrue("nothing2a" in nothing2a.modelAliases)

        val nothing2 = models.firstOrNull { it.modelCode == "NOTHING_PHONE_2" }
        requireNotNull(nothing2)
        assertTrue("nothing2" in nothing2.modelAliases)

        val nothing = models.firstOrNull { it.modelCode == "NOTHING_PHONE_1" }
        requireNotNull(nothing)
        assertTrue("nothingphone1" in nothing.modelAliases)

        val nord4 = models.firstOrNull { it.modelCode == "ONEPLUS_NORD_4" }
        requireNotNull(nord4)
        assertTrue("oneplusnord4" in nord4.modelAliases)

        val xiaomi14 = models.firstOrNull { it.modelCode == "XIAOMI_14" }
        requireNotNull(xiaomi14)
        assertTrue("xiaomi14" in xiaomi14.modelAliases)

        val xiaomi13Pro = models.firstOrNull { it.modelCode == "XIAOMI_13_PRO" }
        requireNotNull(xiaomi13Pro)
        assertTrue("xiaomi13pro" in xiaomi13Pro.modelAliases)

        val xiaomi13T = models.firstOrNull { it.modelCode == "XIAOMI_13T" }
        requireNotNull(xiaomi13T)
        assertTrue("xiaomi13t" in xiaomi13T.modelAliases)

        val xiaomi14Ultra = models.firstOrNull { it.modelCode == "XIAOMI_14_ULTRA" }
        requireNotNull(xiaomi14Ultra)
        assertTrue("xiaomi14ultra" in xiaomi14Ultra.modelAliases)

        val pocoX6 = models.firstOrNull { it.modelCode == "POCO_X6" }
        requireNotNull(pocoX6)
        assertTrue("pocox6" in pocoX6.modelAliases)

        val realmeGt6 = models.firstOrNull { it.modelCode == "REALME_GT6" }
        requireNotNull(realmeGt6)
        assertTrue("realmegt6" in realmeGt6.modelAliases)

        val realme12ProPlus = models.firstOrNull { it.modelCode == "REALME_12_PRO_PLUS" }
        requireNotNull(realme12ProPlus)
        assertTrue("realme12proplus" in realme12ProPlus.modelAliases)

        val pixelFamily = families.firstOrNull { it.familyCode == "GOOGLE_PIXEL" }
        requireNotNull(pixelFamily)
        assertTrue("pixel phone" in pixelFamily.familyAliases)

        val redmiFamily = families.firstOrNull { it.familyCode == "XIAOMI_REDMI" }
        requireNotNull(redmiFamily)
        assertTrue("xiaomi redmi смартфон" in redmiFamily.familyAliases)
    }

    @Test
    fun projects_phone_value_aliases_without_losing_new_canon_entries() {
        val dictionaries = CatalogGovernanceCuratedSeed.projectedValueDictionaries()

        val colorDictionary = dictionaries.firstOrNull { it.attributeCode == "color" }
        requireNotNull(colorDictionary)
        val desertTitanium = colorDictionary.entries.firstOrNull { it.canonicalCode == "DESERT_TITANIUM" }
        requireNotNull(desertTitanium)
        assertTrue("desert titan" in desertTitanium.synonyms)
        assertTrue("песочный титан" in desertTitanium.synonyms)
        val teal = colorDictionary.entries.firstOrNull { it.canonicalCode == "TEAL" }
        requireNotNull(teal)
        assertTrue("teal" in teal.synonyms)
        assertTrue("бирюзовый" in teal.synonyms)
        val ultramarine = colorDictionary.entries.firstOrNull { it.canonicalCode == "ULTRAMARINE" }
        requireNotNull(ultramarine)
        assertTrue("ultramarine" in ultramarine.synonyms)
        assertTrue("ультрамарин" in ultramarine.synonyms)

        val networkDictionary = dictionaries.firstOrNull { it.attributeCode == "network_type" }
        requireNotNull(networkDictionary)
        val fiveG = networkDictionary.entries.firstOrNull { it.canonicalCode == "5G" }
        requireNotNull(fiveG)
        assertTrue("nr" in fiveG.synonyms)
        assertTrue("5 джи" in fiveG.synonyms)
        assertTrue("5g nr" in fiveG.synonyms)

        val memoryDictionary = dictionaries.firstOrNull { it.attributeCode == "memory_gb" }
        requireNotNull(memoryDictionary)
        val memory256 = memoryDictionary.entries.firstOrNull { it.canonicalCode == "256" }
        requireNotNull(memory256)
        assertTrue("256gb" in memory256.synonyms)
        assertTrue("256 гб" in memory256.synonyms)
        assertTrue("rom 256" in memory256.synonyms)

        val ramDictionary = dictionaries.firstOrNull { it.attributeCode == "ram_gb" }
        requireNotNull(ramDictionary)
        val ram12 = ramDictionary.entries.firstOrNull { it.canonicalCode == "12" }
        requireNotNull(ram12)
        assertTrue("12gb ram" in ram12.synonyms)
        assertTrue("12 гб озу" in ram12.synonyms)
        assertTrue("12ram" in ram12.synonyms)
        assertTrue("ram 12" in ram12.synonyms)

        val modelLineDictionary = dictionaries.firstOrNull { it.attributeCode == "model_line" }
        requireNotNull(modelLineDictionary)
        val iphone16Line = modelLineDictionary.entries.firstOrNull { it.canonicalCode == "IPHONE_16" }
        requireNotNull(iphone16Line)
        assertEquals("iPhone 16", iphone16Line.canonicalValue)
        assertTrue("iphone16" in iphone16Line.synonyms)
        val galaxyS24Line = modelLineDictionary.entries.firstOrNull { it.canonicalCode == "GALAXY_S24" }
        requireNotNull(galaxyS24Line)
        assertTrue("galaxys24" in galaxyS24Line.synonyms)
        val honorMagic6Line = modelLineDictionary.entries.firstOrNull { it.canonicalCode == "HONOR_MAGIC6" }
        requireNotNull(honorMagic6Line)
        assertTrue("magic6" in honorMagic6Line.synonyms)

        val dualSimDictionary = dictionaries.firstOrNull { it.attributeCode == "dual_sim" }
        requireNotNull(dualSimDictionary)
        val dualSimTrue = dualSimDictionary.entries.firstOrNull { it.canonicalCode == "TRUE" }
        requireNotNull(dualSimTrue)
        assertTrue("dual sim" in dualSimTrue.synonyms)
        assertTrue("двухсимочный" in dualSimTrue.synonyms)

        val refreshRateDictionary = dictionaries.firstOrNull { it.attributeCode == "refresh_rate_hz" }
        requireNotNull(refreshRateDictionary)
        val refresh120 = refreshRateDictionary.entries.firstOrNull { it.canonicalCode == "120" }
        requireNotNull(refresh120)
        assertTrue("120hz" in refresh120.synonyms)
        assertTrue("120 гц" in refresh120.synonyms)

        val chipsetDictionary = dictionaries.firstOrNull { it.attributeCode == "chipset_family" }
        requireNotNull(chipsetDictionary)
        val snapdragon8Gen3 = chipsetDictionary.entries.firstOrNull { it.canonicalCode == "SNAPDRAGON_8_GEN_3" }
        requireNotNull(snapdragon8Gen3)
        assertTrue("снап 8 ген 3" in snapdragon8Gen3.synonyms)
        assertTrue("sd8g3" in snapdragon8Gen3.synonyms)
        val snapdragon8sGen3 = chipsetDictionary.entries.firstOrNull { it.canonicalCode == "SNAPDRAGON_8S_GEN_3" }
        requireNotNull(snapdragon8sGen3)
        assertTrue("sd8sgen3" in snapdragon8sGen3.synonyms)
        val tensorG4 = chipsetDictionary.entries.firstOrNull { it.canonicalCode == "TENSOR_G4" }
        requireNotNull(tensorG4)
        assertTrue("tensorg4" in tensorG4.synonyms)
        assertTrue("тенсор g4" in tensorG4.synonyms)
        val hazel = colorDictionary.entries.firstOrNull { it.canonicalCode == "HAZEL" }
        requireNotNull(hazel)
        assertTrue("hazel" in hazel.synonyms)
        assertTrue("хейзел" in hazel.synonyms)
        val beige = colorDictionary.entries.firstOrNull { it.canonicalCode == "BEIGE" }
        requireNotNull(beige)
        assertTrue("navigator beige" in beige.synonyms)
        assertTrue("бежевый" in beige.synonyms)
        val kirin9000s = chipsetDictionary.entries.firstOrNull { it.canonicalCode == "KIRIN_9000S" }
        requireNotNull(kirin9000s)
        assertTrue("кирин 9000 с" in kirin9000s.synonyms)
        val dimensity8200Ultra = chipsetDictionary.entries.firstOrNull { it.canonicalCode == "DIMENSITY_8200_ULTRA" }
        requireNotNull(dimensity8200Ultra)
        assertTrue("dimensity8200ultra" in dimensity8200Ultra.synonyms)
        assertTrue("дименсити 8200 ультра" in dimensity8200Ultra.synonyms)
        val helioG99Ultra = chipsetDictionary.entries.firstOrNull { it.canonicalCode == "HELIO_G99_ULTRA" }
        requireNotNull(helioG99Ultra)
        assertTrue("heliog99ultra" in helioG99Ultra.synonyms)
        assertTrue("хелио g99 ультра" in helioG99Ultra.synonyms)
    }

    @Test
    fun resolves_brand_scoped_phone_os_values() {
        val appleOsValues = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "os_family",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "APPLE",
            ),
            locale = "en",
        )
        assertEquals(listOf("iOS"), appleOsValues.map { it.displayValue })

        val samsungOsValues = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "os_family",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "SAMSUNG",
            ),
            locale = "en",
        )
        assertEquals(listOf("Android"), samsungOsValues.map { it.displayValue })
    }

    @Test
    fun resolves_brand_scoped_phone_chipset_values() {
        val appleChipsets = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "chipset_family",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "APPLE",
            ),
            locale = "en",
        )
        assertEquals(
            listOf("A15 Bionic", "A16 Bionic", "A17 Pro", "A18", "A18 Pro", "A19 Pro"),
            appleChipsets.map { it.displayValue },
        )

        val googleChipsets = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "chipset_family",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "GOOGLE",
            ),
            locale = "en",
        )
        assertEquals(
            listOf("Tensor G2", "Tensor G3", "Tensor G4"),
            googleChipsets.map { it.displayValue },
        )
    }

    @Test
    fun resolves_brand_scoped_phone_network_and_ram_values() {
        val appleNetwork = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "network_type",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "APPLE",
            ),
            locale = "en",
        )
        assertEquals(listOf("5G"), appleNetwork.map { it.displayValue })

        val samsungRam = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "ram_gb",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "SAMSUNG",
            ),
            locale = "en",
        )
        assertEquals(listOf("6 GB", "8 GB", "12 GB"), samsungRam.map { it.displayValue })
    }

    @Test
    fun resolves_phone_memory_values_in_product_order() {
        val appleMemory = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "memory_gb",
            scope = CatalogGovernanceScope(
                categoryCode = "TECH.PHONES",
                brandCode = "APPLE",
            ),
            locale = "en",
        )
        assertEquals(
            listOf("64 GB", "128 GB", "256 GB", "512 GB", "1 TB"),
            appleMemory.map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_iphone_17_pro() {
        val scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "APPLE",
            modelCode = "IPHONE_17_PRO",
        )

        assertEquals(
            listOf("Silver", "Deep Blue", "Cosmic Orange"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("A19 Pro"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("120 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_galaxy_s24_ultra() {
        val scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "SAMSUNG",
            modelCode = "GALAXY_S24_ULTRA",
        )

        assertEquals(
            listOf("Black", "Gray", "Purple", "Yellow"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("5G"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "network_type",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("120 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_galaxy_s24_and_s24_plus() {
        val s24Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "SAMSUNG",
            modelCode = "GALAXY_S24",
        )

        assertEquals(
            listOf("Black", "Gray", "Purple", "Yellow"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = s24Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = s24Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = s24Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val s24PlusScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "SAMSUNG",
            modelCode = "GALAXY_S24_PLUS",
        )

        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = s24PlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = s24PlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_pixel_9_pro() {
        val scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "GOOGLE",
            modelCode = "PIXEL_9_PRO",
        )

        assertEquals(
            listOf("Black", "White", "Pink", "Hazel"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Tensor G4"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_pixel_9_and_pixel_9_pro_xl() {
        val pixel9Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "GOOGLE",
            modelCode = "PIXEL_9",
        )

        assertEquals(
            listOf("Black", "White", "Green", "Pink"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pixel9Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pixel9Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pixel9Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Tensor G4"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = pixel9Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val pixel9ProXlScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "GOOGLE",
            modelCode = "PIXEL_9_PRO_XL",
        )

        assertEquals(
            listOf("Black", "White", "Pink", "Hazel"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pixel9ProXlScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pixel9ProXlScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pixel9ProXlScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_huawei_p60_pro() {
        val scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "HUAWEI",
            modelCode = "HUAWEI_P60_PRO",
        )

        assertEquals(
            listOf("Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("4G"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "network_type",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8+ Gen 1"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = scope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_oneplus_13_and_nothing_phone_2a() {
        val oneplusScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "ONEPLUS",
            modelCode = "ONEPLUS_13",
        )

        assertEquals(
            listOf("White", "Black", "Blue"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = oneplusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = oneplusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB", "16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = oneplusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Elite"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = oneplusScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val nothing2aScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "NOTHING",
            modelCode = "NOTHING_PHONE_2A",
        )

        assertEquals(
            listOf("Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = nothing2aScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = nothing2aScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = nothing2aScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Dimensity 7200 Pro"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = nothing2aScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_oneplus_12_and_nothing_phone_2() {
        val oneplus12Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "ONEPLUS",
            modelCode = "ONEPLUS_12",
        )

        assertEquals(
            listOf("Green", "Black"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = oneplus12Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = oneplus12Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB", "16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = oneplus12Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = oneplus12Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val nothing2Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "NOTHING",
            modelCode = "NOTHING_PHONE_2",
        )

        assertEquals(
            listOf("Gray", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = nothing2Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = nothing2Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = nothing2Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8+ Gen 1"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = nothing2Scope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_nord4_xiaomi14_and_nothing_phone_1() {
        val nord4Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "ONEPLUS",
            modelCode = "ONEPLUS_NORD_4",
        )

        assertEquals(
            listOf("Silver", "Black", "Green"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = nord4Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = nord4Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB", "16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = nord4Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 7+ Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = nord4Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val xiaomi14Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "XIAOMI_14",
        )

        assertEquals(
            listOf("Green", "Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = xiaomi14Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = xiaomi14Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = xiaomi14Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = xiaomi14Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val nothing1Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "NOTHING",
            modelCode = "NOTHING_PHONE_1",
        )

        assertEquals(
            listOf("Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = nothing1Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = nothing1Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = nothing1Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 778G+"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = nothing1Scope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_redmi_note_13_pro_plus_poco_f6_pro_and_honor_200_pro() {
        val redmiScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "REDMI_NOTE_13_PRO_PLUS",
        )

        assertEquals(
            listOf("Black", "White", "Purple", "Silver"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = redmiScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = redmiScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = redmiScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("5G"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "network_type",
                scope = redmiScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("120 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = redmiScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Dimensity 7200 Ultra"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = redmiScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val pocoScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "POCO_F6_PRO",
        )

        assertEquals(
            listOf("Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pocoScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pocoScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB", "16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pocoScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("5G"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "network_type",
                scope = pocoScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("120 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = pocoScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 2"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = pocoScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val honorScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "HONOR",
            modelCode = "HONOR_200_PRO",
        )

        assertEquals(
            listOf("Cyan", "White", "Black"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = honorScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = honorScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = honorScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("5G"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "network_type",
                scope = honorScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("120 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = honorScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8s Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = honorScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_poco_x6_pro_poco_f6_honor_200_and_honor_magic6_pro() {
        val pocoX6ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "POCO_X6_PRO",
        )

        assertEquals(
            listOf("Yellow", "Gray", "Black"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pocoX6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pocoX6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pocoX6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Dimensity 8300 Ultra"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = pocoX6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val pocoF6Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "POCO_F6",
        )

        assertEquals(
            listOf("Black", "Green", "Titanium"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pocoF6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pocoF6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pocoF6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8s Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = pocoF6Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val honor200Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "HONOR",
            modelCode = "HONOR_200",
        )

        assertEquals(
            listOf("White", "Green", "Pink", "Black"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = honor200Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = honor200Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = honor200Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 7 Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = honor200Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val honorMagic6ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "HONOR",
            modelCode = "HONOR_MAGIC6_PRO",
        )

        assertEquals(
            listOf("Green", "Black", "Purple"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = honorMagic6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = honorMagic6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = honorMagic6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = honorMagic6ProScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val pocoX7ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "POCO_X7_PRO",
        )

        assertEquals(
            listOf("Yellow", "Green", "Black"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pocoX7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pocoX7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pocoX7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Dimensity 8400 Ultra"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = pocoX7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val redmiNote14ProPlusScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "REDMI_NOTE_14_PRO_PLUS",
        )

        assertEquals(
            listOf("Black", "Purple", "Blue", "Gold"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = redmiNote14ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = redmiNote14ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = redmiNote14ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 7s Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = redmiNote14ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val realmeGt7ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "REALME",
            modelCode = "REALME_GT7_PRO",
        )

        assertEquals(
            listOf("Orange", "Gray"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = realmeGt7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = realmeGt7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB", "16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = realmeGt7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Elite"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = realmeGt7ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_xiaomi_and_realme_verified_wave() {
        val xiaomi13ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "XIAOMI_13_PRO",
        )

        assertEquals(
            listOf("Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = xiaomi13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = xiaomi13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = xiaomi13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 2"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = xiaomi13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val xiaomi13TScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "XIAOMI_13T",
        )

        assertEquals(
            listOf("Blue", "Green", "Black"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = xiaomi13TScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = xiaomi13TScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = xiaomi13TScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("144 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = xiaomi13TScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Dimensity 8200 Ultra"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = xiaomi13TScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val xiaomi14UltraScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "XIAOMI_14_ULTRA",
        )

        assertEquals(
            listOf("Black", "White"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = xiaomi14UltraScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = xiaomi14UltraScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = xiaomi14UltraScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8 Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = xiaomi14UltraScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val redmiNote13ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "REDMI_NOTE_13_PRO",
        )

        assertEquals(
            listOf("Black", "Purple", "Cyan", "Green"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = redmiNote13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = redmiNote13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = redmiNote13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("4G"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "network_type",
                scope = redmiNote13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Helio G99 Ultra"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = redmiNote13ProScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val pocoX6Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "XIAOMI",
            modelCode = "POCO_X6",
        )

        assertEquals(
            listOf("Black", "White", "Blue"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = pocoX6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = pocoX6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = pocoX6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 7s Gen 2"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = pocoX6Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val realmeGt6Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "REALME",
            modelCode = "REALME_GT6",
        )

        assertEquals(
            listOf("Silver", "Green"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = realmeGt6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = realmeGt6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB", "16 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = realmeGt6Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 8s Gen 3"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = realmeGt6Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val realme12ProPlusScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "REALME",
            modelCode = "REALME_12_PRO_PLUS",
        )

        assertEquals(
            listOf("Blue", "Beige", "Red"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = realme12ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = realme12ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("8 GB", "12 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "ram_gb",
                scope = realme12ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("Snapdragon 7s Gen 2"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = realme12ProPlusScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }

    @Test
    fun resolves_model_scoped_values_for_iphone_16_family_verified_wave() {
        val iphone16Scope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "APPLE",
            modelCode = "IPHONE_16",
        )

        assertEquals(
            listOf("Black", "White", "Pink", "Teal", "Ultramarine"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = iphone16Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB", "512 GB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = iphone16Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("60 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = iphone16Scope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("A18"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = iphone16Scope,
                locale = "en",
            ).map { it.displayValue },
        )

        val iphone16ProScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "APPLE",
            modelCode = "IPHONE_16_PRO",
        )

        assertEquals(
            listOf("Black", "White", "Natural Titanium", "Desert Titanium"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "color",
                scope = iphone16ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("128 GB", "256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = iphone16ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("120 Hz"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "refresh_rate_hz",
                scope = iphone16ProScope,
                locale = "en",
            ).map { it.displayValue },
        )
        assertEquals(
            listOf("A18 Pro"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "chipset_family",
                scope = iphone16ProScope,
                locale = "en",
            ).map { it.displayValue },
        )

        val iphone16ProMaxScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = "APPLE",
            modelCode = "IPHONE_16_PRO_MAX",
        )

        assertEquals(
            listOf("256 GB", "512 GB", "1 TB"),
            CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
                attributeCode = "memory_gb",
                scope = iphone16ProMaxScope,
                locale = "en",
            ).map { it.displayValue },
        )
    }
}
