package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.model.OfferFacetType
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetUiConfig
import org.junit.Assert.assertEquals
import org.junit.Test

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
                titleRu = "Бренд",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "condition",
                titleRu = "Состояние",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "color",
                titleRu = "Цвет",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "battery_health_percent",
                titleRu = "Состояние аккумулятора",
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
                titleRu = "Бренд",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "delivery_channel",
                titleRu = "Канал доставки",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "seller_trust",
                titleRu = "Доверие продавца",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "Color",
                titleRu = "Цвет",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "COLOR",
                titleRu = "Цвет (дубликат)",
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH_PHONES"),
            ),
            FacetDefinition(
                facetKey = "private_note",
                titleRu = "Служебное",
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
}
