package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.model.OfferFacetType
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetUiConfig
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.model.NormalizedQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ResultsSearchContractTest {

    @Test
    fun builds_faceted_request_with_runtime_facets_enabled() {
        val criteria = OfferSearchCriteria(
            brand = null,
            model = null,
        )

        val request = criteria.toResultsFacetedRequest(includeRuntimeFacets = true)

        assertEquals(
            setOf(
                OfferFacetType.BRAND,
                OfferFacetType.CONDITION,
                OfferFacetType.DELIVERY_CHANNEL,
            ),
            request.facets,
        )
        assertEquals(request.facets, request.excludeFacetFilters)
        assertEquals(criteria, request.criteria)
        assertEquals(emptySet<String>(), request.attributeFacetKeys)
    }

    @Test
    fun builds_request_without_runtime_facets_when_disabled() {
        val criteria = OfferSearchCriteria(
            brand = "Apple",
            model = "iPhone",
        )

        val request = criteria.toResultsFacetedRequest(includeRuntimeFacets = false)

        assertEquals(emptySet<OfferFacetType>(), request.facets)
        assertEquals(emptySet<OfferFacetType>(), request.excludeFacetFilters)
        assertEquals(criteria, request.criteria)
        assertEquals(emptySet<String>(), request.attributeFacetKeys)
    }

    @Test
    fun builds_attribute_facet_keys_from_non_system_definitions() {
        val criteria = OfferSearchCriteria(
            brand = null,
            model = null,
        )
        val definitions = listOf(
            FacetDefinition(
                facetKey = "brand",
                title = localizedTextOf("ru" to "Бренд"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "condition",
                title = localizedTextOf("ru" to "Состояние"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "color",
                title = localizedTextOf("ru" to "Цвет"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "battery_health_percent",
                title = localizedTextOf("ru" to "Состояние аккумулятора"),
                valueType = FacetDataType.RANGE,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
        )

        val request = criteria.toResultsFacetedRequest(
            includeRuntimeFacets = true,
            facetDefinitions = definitions,
        )

        assertEquals(
            setOf("color", "battery_health_percent"),
            request.attributeFacetKeys,
        )
    }

    @Test
    fun excludes_hidden_and_system_facet_keys_and_normalizes_case() {
        val criteria = OfferSearchCriteria(brand = null, model = null)
        val definitions = listOf(
            FacetDefinition(
                facetKey = " BRAND ",
                title = localizedTextOf("ru" to "Бренд"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "delivery_channel",
                title = localizedTextOf("ru" to "Канал доставки"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "seller_trust",
                title = localizedTextOf("ru" to "Доверие продавца"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "Color",
                title = localizedTextOf("ru" to "Цвет"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "COLOR",
                title = localizedTextOf("ru" to "Цвет (дубликат)"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "private_note",
                title = localizedTextOf("ru" to "Служебное"),
                valueType = FacetDataType.TEXT,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
                ui = FacetUiConfig(hidden = true),
            ),
        )

        val request = criteria.toResultsFacetedRequest(
            includeRuntimeFacets = true,
            facetDefinitions = definitions,
        )

        assertEquals(setOf("color"), request.attributeFacetKeys)
    }

    @Test
    fun excludes_inactive_facet_definitions_from_attribute_facet_keys() {
        val criteria = OfferSearchCriteria(brand = null, model = null)
        val definitions = listOf(
            FacetDefinition(
                facetKey = "color",
                title = localizedTextOf("ru" to "Цвет"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "battery_health_percent",
                title = localizedTextOf("ru" to "Состояние аккумулятора"),
                valueType = FacetDataType.RANGE,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
                effectiveFrom = "2999-01-01",
            ),
        )

        val request = criteria.toResultsFacetedRequest(
            includeRuntimeFacets = true,
            facetDefinitions = definitions,
        )

        assertEquals(setOf("color"), request.attributeFacetKeys)
    }

    @Test
    fun uses_runtime_filter_key_for_attribute_facets() {
        val criteria = OfferSearchCriteria(brand = null, model = null)
        val definitions = listOf(
            FacetDefinition(
                facetKey = "cuisine",
                title = localizedTextOf("ru" to "Кухня"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("FOOD.READY_MEALS"),
                attributeCode = "cuisine_type",
            ),
        )

        val request = criteria.toResultsFacetedRequest(
            includeRuntimeFacets = true,
            facetDefinitions = definitions,
        )

        assertEquals(setOf("cuisine_type"), request.attributeFacetKeys)
    }

    @Test
    fun prefers_profile_country_for_delivery_matching() {
        val resolved = resolveResultsUserCountryCode(
            profileCountryCode = "ru",
            locale = Locale.forLanguageTag("en-US"),
        )

        assertEquals("RU", resolved)
    }

    @Test
    fun falls_back_to_locale_country_when_profile_country_is_missing() {
        val resolved = resolveResultsUserCountryCode(
            profileCountryCode = "  ",
            locale = Locale.forLanguageTag("en-US"),
        )

        assertEquals("US", resolved)
    }

    @Test
    fun prefers_active_delivery_address_country_over_profile_country() {
        val resolved = resolveResultsUserCountryCode(
            profileCountryCode = "US",
            activeDeliveryAddressCountryCode = "ru",
            locale = Locale.forLanguageTag("en-GB"),
        )

        assertEquals("RU", resolved)
    }

    @Test
    fun hide_undeliverable_profile_setting_enables_delivery_filtering() {
        assertTrue(
            resolveResultsDeliverableOnly(
                explicitDeliverableOnly = false,
                hideUndeliverable = true,
            ),
        )
        assertFalse(
            resolveResultsDeliverableOnly(
                explicitDeliverableOnly = false,
                hideUndeliverable = false,
            ),
        )
    }

    @Test
    fun suppresses_query_model_when_model_is_managed_by_facet_profile() {
        val resolved = resolveCriteriaQueryModel(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 17 Pro",
            ),
            facetDefinitions = listOf(
                FacetDefinition(
                    facetKey = "model",
                    title = localizedTextOf("ru" to "Модель"),
                    valueType = FacetDataType.ENUM,
                    appliesToCategoryCodes = listOf("TECH.PHONES"),
                    attributeCode = "model",
                ),
            ),
        )

        assertNull(resolved)
    }

    @Test
    fun keeps_query_model_when_model_is_not_managed_by_facet_profile() {
        val resolved = resolveCriteriaQueryModel(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 17 Pro",
            ),
            facetDefinitions = listOf(
                FacetDefinition(
                    facetKey = "color",
                    title = localizedTextOf("ru" to "Цвет"),
                    valueType = FacetDataType.ENUM,
                    appliesToCategoryCodes = listOf("TECH.PHONES"),
                    attributeCode = "color",
                ),
            ),
        )

        assertEquals("iPhone 17 Pro", resolved)
    }
}
