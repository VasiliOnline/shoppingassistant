package com.example.shoppingassistant.server.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogPhoneModelEnrichmentPromotionHintsTest {

    @Test
    fun samsung_ready_candidate_gets_high_confidence_buy_page_suggestion() {
        val hints = CatalogPhoneModelEnrichmentPromotionHintsResolver.resolve(
            CatalogPhoneModelEnrichmentCandidate(
                id = 1L,
                candidateKey = "TECH.PHONES|MODEL|GALAXY_S25_EDGE",
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                brandRaw = "Samsung",
                brandNormalized = "samsung",
                brandCode = "SAMSUNG",
                familyRaw = "Galaxy S25",
                familyCode = "SAMSUNG_GALAXY",
                canonicalModelCode = null,
                modelRaw = "Galaxy S25 Edge",
                modelNormalized = "galaxy s25 edge",
                officialSourceCode = "SAMSUNG_OFFICIAL_PHONES",
                officialEndpointCode = null,
                status = CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT,
                observedCount = 3,
                distinctSellerCount = 2,
                sellerRefs = listOf("seller-1", "seller-2"),
                sampleOfferRefs = listOf("offer-1", "offer-2", "offer-3"),
                maxConfidence = 0.95,
                reasonCodes = emptyList(),
                metadata = emptyMap(),
                firstSeenAt = 1L,
                lastSeenAt = 2L,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )

        assertEquals("HIGH", hints.sourceUriSuggestionConfidence)
        assertEquals("SAMSUNG_DEVICE_BUY_PAGE", hints.suggestedParserType?.name)
        assertEquals(
            "https://www.samsung.com/us/smartphones/galaxy-s25-edge/buy/",
            hints.suggestedSourceUri,
        )
    }

    @Test
    fun google_candidate_reuses_shared_support_specs_page() {
        val hints = CatalogPhoneModelEnrichmentPromotionHintsResolver.resolve(
            CatalogPhoneModelEnrichmentCandidate(
                id = 2L,
                candidateKey = "TECH.PHONES|MODEL|PIXEL_10",
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                brandRaw = "Google",
                brandNormalized = "google",
                brandCode = "GOOGLE",
                familyRaw = "Pixel",
                familyCode = "GOOGLE_PIXEL",
                canonicalModelCode = null,
                modelRaw = "Pixel 10",
                modelNormalized = "pixel 10",
                officialSourceCode = "GOOGLE_OFFICIAL_PHONES",
                officialEndpointCode = null,
                status = CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT,
                observedCount = 3,
                distinctSellerCount = 2,
                sellerRefs = listOf("seller-1", "seller-2"),
                sampleOfferRefs = listOf("offer-1", "offer-2", "offer-3"),
                maxConfidence = 0.95,
                reasonCodes = emptyList(),
                metadata = emptyMap(),
                firstSeenAt = 1L,
                lastSeenAt = 2L,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )

        assertEquals("HIGH", hints.sourceUriSuggestionConfidence)
        assertEquals("GOOGLE_PIXEL_SUPPORT_SPECS", hints.suggestedParserType?.name)
        assertTrue(hints.suggestedSourceUri?.contains("support.google.com/pixelphone/answer/7158570") == true)
    }
}
