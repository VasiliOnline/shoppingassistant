package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogDataVersionTest {
    @Test
    fun catalog_fingerprint_includes_stage21_resources() {
        val fingerprintWithStage21 = CatalogDataVersion.catalogFingerprint(includeStage21 = true)
        val fingerprintWithoutStage21 = CatalogDataVersion.catalogFingerprint(includeStage21 = false)

        assertNotEquals(
            "Stage 2.1 resources must contribute to catalog fingerprint.",
            fingerprintWithoutStage21,
            fingerprintWithStage21,
        )

        val currentFingerprint = CatalogDataVersion.current.substringAfter("+")
        assertEquals(fingerprintWithStage21, currentFingerprint)
    }
}
