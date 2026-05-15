package com.example.shoppingassistant.server.shortlisting

import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldKind
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldValue
import com.example.shoppingassistant.domain.shortlisting.ShortListingValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortListingTechPhonesIdentitySignatureBuilderTest {
    @Test
    fun iphone_and_cyrillic_iphone_aliases_build_compatible_product_identity_signature() {
        val latin = ShortListingTechPhonesIdentitySignatureBuilder.tryBuild(
            categoryCode = CATEGORY,
            mergedFields = fields(model = "iPhone 13 128"),
        )
        val cyrillic = ShortListingTechPhonesIdentitySignatureBuilder.tryBuild(
            categoryCode = CATEGORY,
            mergedFields = fields(model = "айфон 13 128"),
        )

        assertNotNull(latin)
        assertNotNull(cyrillic)
        assertEquals(latin.signatureSource, cyrillic.signatureSource)
        assertEquals(
            latin.identityAttributes.getValue("product_identity_match_key").normalizedValue,
            cyrillic.identityAttributes.getValue("product_identity_match_key").normalizedValue,
        )
        assertTrue(latin.signatureSource.contains("storage_capacity_gb=128"))
    }

    @Test
    fun different_storage_builds_different_product_identity_signature() {
        val storage128 = ShortListingTechPhonesIdentitySignatureBuilder.tryBuild(
            categoryCode = CATEGORY,
            mergedFields = fields(model = "iPhone 13 128"),
        )
        val storage256 = ShortListingTechPhonesIdentitySignatureBuilder.tryBuild(
            categoryCode = CATEGORY,
            mergedFields = fields(model = "iPhone 13 256"),
        )

        assertNotNull(storage128)
        assertNotNull(storage256)
        assertNotEquals(storage128.signatureSource, storage256.signatureSource)
        assertTrue(storage128.signatureSource.contains("storage_capacity_gb=128"))
        assertTrue(storage256.signatureSource.contains("storage_capacity_gb=256"))
    }

    @Test
    fun ambiguous_short_alias_falls_back_to_ad_hoc_signature_path() {
        val ambiguous = ShortListingTechPhonesIdentitySignatureBuilder.tryBuild(
            categoryCode = CATEGORY,
            mergedFields = fields(model = "13 pro"),
        )

        assertNull(ambiguous)
    }

    @Test
    fun non_tech_phones_category_is_ignored() {
        val result = ShortListingTechPhonesIdentitySignatureBuilder.tryBuild(
            categoryCode = "TECH.TABLETS_E_READERS",
            mergedFields = fields(model = "iPhone 13 128"),
        )

        assertNull(result)
    }

    private fun fields(
        brand: String? = null,
        model: String? = null,
        attrs: Map<String, String> = emptyMap(),
    ): Map<String, ShortListingFieldValue> =
        buildMap {
            brand?.let { put("brand", field(it)) }
            model?.let { put("model", field(it)) }
            attrs.forEach { (key, value) -> put(key, field(value)) }
        }

    private fun field(value: String): ShortListingFieldValue =
        ShortListingFieldValue(
            kind = ShortListingFieldKind.SCALAR,
            valueType = ShortListingValueType.STRING,
            displayValue = value,
            normalizedValue = value,
        )

    private companion object {
        private const val CATEGORY = "TECH.PHONES"
    }
}
