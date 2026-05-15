package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPhonesIdentityRuntimeTest {
    private val runtime = TechPhonesIdentityRuntimeLoader.load()
    private val resolver = TechPhonesIdentityResolver(runtime)

    @Test
    fun loader_reads_real_tech_phones_identity_and_dedup_artifacts() {
        assertTrue(runtime.models.any { it.modelCode == "APPLE_IPHONE_13" })
        assertTrue(runtime.aliases.any { it.alias == "айфон 13" && it.modelCode == "APPLE_IPHONE_13" })
        assertEquals("phone_dedup_policy.tech_phones.v1_0", runtime.dedupPolicy.policyId)
        assertTrue("storage_capacity_gb" in runtime.dedupPolicy.variantAxes)
        assertTrue("ram_gb" in runtime.dedupPolicy.variantAxes)
        assertTrue("13 pro" in runtime.identityPolicy.ambiguousShortAliases)
    }

    @Test
    fun resolver_canonicalizes_brand_and_model_alias() {
        val identity = resolver.resolveProductIdentity(
            categoryCode = CATEGORY,
            brand = "Samsung",
            model = "s24 ultra",
        )

        assertEquals(ProductIdentityStatus.RESOLVED, identity.status)
        assertEquals("SAMSUNG", identity.brandCanonical)
        assertEquals("Galaxy S24 Ultra", identity.modelCanonical)
        assertEquals("SAMSUNG_GALAXY_S24_ULTRA", identity.modelCode)
        assertTrue(identity.matchKey.contains("category=tech-phones"))
        assertTrue(identity.matchKey.contains("brand=samsung"))
        assertTrue(identity.matchKey.contains("model=samsung-galaxy-s24-ultra"))
    }

    @Test
    fun resolver_supports_iphone_and_cyrillic_aliases() {
        val identity = resolver.resolveProductIdentity(
            categoryCode = CATEGORY,
            titleOrQuery = "айфон 13 128",
        )

        assertEquals(ProductIdentityStatus.RESOLVED, identity.status)
        assertEquals("APPLE", identity.brandCanonical)
        assertEquals("iPhone 13", identity.modelCanonical)
        assertEquals("APPLE_IPHONE_13", identity.modelCode)
        assertEquals("128", identity.variantAttributes["storage_capacity_gb"])
        assertTrue(identity.matchKey.contains("storage_capacity_gb=128"))
    }

    @Test
    fun storage_and_ram_variant_axes_are_part_of_product_match_key() {
        val identity = resolver.resolveProductIdentity(
            categoryCode = CATEGORY,
            titleOrQuery = "pixel 10 pro 512",
            attrs = mapOf(
                "ram" to "16 GB",
                "color" to "Black",
                "region" to "EU",
            ),
        )

        assertEquals(ProductIdentityStatus.RESOLVED, identity.status)
        assertEquals("GOOGLE_PIXEL_10_PRO", identity.modelCode)
        assertEquals("512", identity.variantAttributes["storage_capacity_gb"])
        assertEquals("16", identity.variantAttributes["ram_gb"])
        assertEquals("black", identity.variantAttributes["color_family"])
        assertEquals("eu", identity.variantAttributes["region_variant"])
        assertTrue(identity.matchKey.contains("storage_capacity_gb=512"))
        assertTrue(identity.matchKey.contains("ram_gb=16"))
    }

    @Test
    fun ambiguous_short_alias_stays_unresolved_without_brand_context() {
        val identity = resolver.resolveProductIdentity(
            categoryCode = CATEGORY,
            titleOrQuery = "13 pro 256",
        )

        assertEquals(ProductIdentityStatus.UNRESOLVED, identity.status)
        assertNull(identity.modelCode)
        assertTrue("AMBIGUOUS_SHORT_ALIAS" in identity.reasonCodes)
        assertTrue(identity.matchKey.contains("unresolved=13-pro-256"))
    }

    @Test
    fun product_identity_is_independent_from_offer_identity_inputs() {
        val productIdentity = resolver.resolveProductIdentity(
            categoryCode = CATEGORY,
            titleOrQuery = "iphone 13 128",
        )
        val firstOffer = resolver.resolveOfferIdentity(
            productIdentity = productIdentity,
            sourceId = "market",
            externalId = "A-1",
            sellerId = "seller-1",
        )
        val secondOffer = resolver.resolveOfferIdentity(
            productIdentity = productIdentity,
            sourceId = "market",
            externalId = "A-2",
            sellerId = "seller-2",
        )

        assertNotNull(productIdentity.modelCode)
        assertEquals(productIdentity.matchKey, firstOffer.productIdentity.matchKey)
        assertEquals(productIdentity.matchKey, secondOffer.productIdentity.matchKey)
        assertNotEquals(firstOffer.matchKey, secondOffer.matchKey)
    }

    private companion object {
        private const val CATEGORY = "TECH.PHONES"
    }
}
