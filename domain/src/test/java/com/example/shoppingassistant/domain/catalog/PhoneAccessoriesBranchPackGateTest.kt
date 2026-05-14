package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAccessoriesBranchPackGateTest {

    @Test
    fun stage22_profile_exposes_product_runtime_shape() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val spec = Stage22EffectiveSpecEngine.fromSeed(registry = registry)
            .getEffectiveSpec(CATEGORY)

        assertFalse("TECH.PHONE_ACCESSORIES must use its own Stage22 profile.", spec.meta.isFallback)
        assertTrue(spec.attributes.any { it.attributeCode == "accessory_type" && it.required })
        assertTrue("accessory_type" in spec.identityAttributes)
        assertTrue("compatible_phone_brand" in spec.identityAttributes)
        assertTrue("compatible_phone_family" in spec.identityAttributes)
        assertTrue("compatible_phone_model" in spec.identityAttributes)
        assertTrue("accessory_type" in spec.facetAttributes)

        assertTrue("compatibility_mode" in spec.facetAttributes)
        assertTrue("compatibility_confidence" in spec.facetAttributes)
        assertFalse("compatible_model_text is an initial compatibility field, not a runtime facet.", "compatible_model_text" in spec.facetAttributes)
    }

    @Test
    fun dictionaries_and_external_phone_refs_are_wired_to_the_right_standard() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val accessoryTypes = registry.dictionaries.getValue("accessory_type").entries.map { it.valueCode }.toSet()

        assertTrue(accessoryTypes.size >= 43)
        assertTrue(setOf("CASE", "CABLE", "SCREEN_PROTECTOR", "PHONE_REPAIR_PART").all { it in accessoryTypes })

        assertEquals(Stage22ValueType.ENUM, registry.attributes.getValue("compatible_phone_brand").valueType)
        assertEquals(Stage22ValueSetType.CLOSED, registry.attributes.getValue("compatible_phone_brand").valueSetType)

        val familyRef = registry.attributes.getValue("compatible_phone_family")
        val modelRef = registry.attributes.getValue("compatible_phone_model")
        assertEquals(Stage22ValueType.STRING, familyRef.valueType)
        assertEquals(Stage22ValueSetType.OPEN, familyRef.valueSetType)
        assertEquals(Stage22ValueType.STRING, modelRef.valueType)
        assertEquals(Stage22ValueSetType.OPEN, modelRef.valueSetType)
        assertFalse("Family refs are external TECH.PHONES refs, not closed dictionaries.", registry.dictionaries.containsKey("compatible_phone_family"))
        assertFalse("Model refs are external TECH.PHONES refs, not closed dictionaries.", registry.dictionaries.containsKey("compatible_phone_model"))
    }

    @Test
    fun stage4_profile_keeps_phone_accessory_surface_compact() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        requireNotNull(profile)
        assertTrue("accessory_type" in profile.mainTypedFacetKeys)
        assertTrue("compatible_phone_model" in profile.requiresBrandContextTypedFacetKeys)
        assertTrue("compatibility_mode" in profile.mainTypedFacetKeys)
        assertTrue("compatibility_confidence" in profile.mainTypedFacetKeys)
        assertTrue("compatible_model_text" in profile.noticePriorityTypedFacetKeys)
        assertTrue("evidence_policy" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun route_aliases_do_not_steal_plain_phone_model_queries() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals("TECH.PHONES", router.route("iphone 13", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("чехол iphone 13", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("кабель type c", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("зарядка iphone", "ru-RU").primaryTargetCode)
    }

    private companion object {
        private const val CATEGORY = "TECH.PHONE_ACCESSORIES"
    }
}
