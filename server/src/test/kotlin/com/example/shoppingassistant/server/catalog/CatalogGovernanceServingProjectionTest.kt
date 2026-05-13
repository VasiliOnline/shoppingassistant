package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceServingProjectionTest {

    @Test
    fun projectableAttributeHintAlias_skips_numericOnlyHints() {
        val bareNumberAlias = CatalogAliasCanon(
            locale = "en",
            aliasText = "8",
            normalizedAlias = "8",
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            targetCode = "8",
            attributeCode = "ram_gb",
            confidence = 0.9,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val decimalLikeAlias = bareNumberAlias.copy(
            aliasText = "6.7",
            normalizedAlias = "6 7",
            targetCode = "6_7",
            attributeCode = "screen_size_inch",
        )
        val contextualAlias = bareNumberAlias.copy(
            aliasText = "8gb ram",
            normalizedAlias = "8gb ram",
        )

        assertFalse(isProjectableAttributeHintAlias(bareNumberAlias))
        assertFalse(isProjectableAttributeHintAlias(decimalLikeAlias))
        assertTrue(isProjectableAttributeHintAlias(contextualAlias))
    }
}
