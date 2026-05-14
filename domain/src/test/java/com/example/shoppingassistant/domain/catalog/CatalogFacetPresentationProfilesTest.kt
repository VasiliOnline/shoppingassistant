package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFacetPresentationProfilesTest {

    @Test
    fun global_profile_merges_with_branch_profile() {
        val profile = CatalogFacetPresentationProfiles.resolve("FOOD.GROCERIES")

        requireNotNull(profile)
        assertTrue("model" in profile.requiresBrandContextTypedFacetKeys)
        assertTrue("condition" in profile.hiddenSystemFacetKeys)
    }

    @Test
    fun tech_phones_gets_global_rules_without_food_hides() {
        val profile = CatalogFacetPresentationProfiles.resolve("TECH.PHONES")

        requireNotNull(profile)
        assertTrue("model" in profile.requiresBrandContextTypedFacetKeys)
        assertFalse("condition" in profile.hiddenSystemFacetKeys)
    }

    @Test
    fun tech_phones_profile_exposes_professional_additional_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve("TECH.PHONES")

        requireNotNull(profile)
        assertTrue("model" in profile.mainTypedFacetKeys)
        assertTrue("memory_gb" in profile.mainTypedFacetKeys)
        assertTrue("color" in profile.additionalTypedFacetKeys)
        assertTrue("release_year" in profile.additionalTypedFacetKeys)
        assertFalse("release_date" in profile.additionalTypedFacetKeys)
        assertTrue("camera_main_mp" in profile.additionalTypedFacetKeys)
        assertTrue("sim_configuration" in profile.additionalTypedFacetKeys)
        assertTrue("model_number" in profile.additionalTypedFacetKeys)
        assertTrue("memory_card_type" in profile.additionalTypedFacetKeys)
        assertTrue("os_family" in profile.additionalTypedFacetKeys)
        assertTrue("connectivity" in profile.additionalTypedFacetKeys)
        assertFalse("os_family" in profile.hiddenTypedFacetKeys)
        assertTrue("screen_size_inch" in profile.additionalTypedFacetKeys)
        assertTrue("wireless_charging" in profile.additionalTypedFacetKeys)
        assertTrue("esim_support" in profile.additionalTypedFacetKeys)
        assertTrue(profile.orderedTypedFacetKeys.first() == "model")
        assertTrue("network_type" in profile.liveOnlyTypedFacetKeys)
        assertTrue(profile.noticePriorityTypedFacetKeys.first() == "model")
        assertTrue("os_family" in profile.suppressedAutoAppliedTypedFacetKeys)
    }

    @Test
    fun beauty_skincare_hides_condition_but_keeps_brand() {
        val profile = CatalogFacetPresentationProfiles.resolve("BEAUTY.SKINCARE")

        requireNotNull(profile)
        assertTrue("condition" in profile.hiddenSystemFacetKeys)
        assertFalse("brand" in profile.hiddenSystemFacetKeys)
    }

    @Test
    fun beauty_health_hides_condition_and_brand() {
        val profile = CatalogFacetPresentationProfiles.resolve("BEAUTY.HEALTH")

        requireNotNull(profile)
        assertTrue("condition" in profile.hiddenSystemFacetKeys)
        assertTrue("brand" in profile.hiddenSystemFacetKeys)
    }

    @Test
    fun beauty_devices_keep_condition_visible() {
        val profile = CatalogFacetPresentationProfiles.resolve("BEAUTY.DEVICES")

        requireNotNull(profile)
        assertFalse("condition" in profile.hiddenSystemFacetKeys)
    }

    @Test
    fun food_ready_meals_hide_brand_and_condition() {
        val profile = CatalogFacetPresentationProfiles.resolve("FOOD.READY_MEALS")

        requireNotNull(profile)
        assertTrue("brand" in profile.hiddenSystemFacetKeys)
        assertTrue("condition" in profile.hiddenSystemFacetKeys)
        assertTrue("cuisine_type" in profile.mainTypedFacetKeys)
        assertTrue("cuisine_type" in profile.liveOnlyTypedFacetKeys)
        assertTrue(profile.noticePriorityTypedFacetKeys.first() == "cuisine_type")
    }

    @Test
    fun auto_branch_keeps_brand_and_condition_visible() {
        val profile = CatalogFacetPresentationProfiles.resolve("AUTO.PARTS")

        requireNotNull(profile)
        assertFalse("brand" in profile.hiddenSystemFacetKeys)
        assertFalse("condition" in profile.hiddenSystemFacetKeys)
        assertTrue("model" in profile.mainTypedFacetKeys)
        assertTrue("material" in profile.liveOnlyTypedFacetKeys)
        assertTrue(profile.noticePriorityTypedFacetKeys.first() == "model")
    }

    @Test
    fun dormant_books_profile_is_ready_for_future_branch() {
        val profile = CatalogFacetPresentationProfiles.resolve("BOOKS.FICTION")

        requireNotNull(profile)
        assertTrue("brand" in profile.hiddenSystemFacetKeys)
        assertTrue("condition" in profile.hiddenSystemFacetKeys)
    }
}
