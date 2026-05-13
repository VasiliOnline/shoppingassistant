package com.example.shoppingassistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizationTest {

    @Test
    fun normalize_attrs_preserves_canonical_underscore_keys() {
        val normalized = Normalization.normalizeAttrs(
            mapOf(
                "memory_gb" to "256",
                "refresh_rate_hz" to "120",
                "chipset_family" to "SNAPDRAGON_8_GEN_3",
            ),
        )

        assertEquals(
            mapOf(
                "memory_gb" to "256",
                "refresh_rate_hz" to "120",
                "chipset_family" to "SNAPDRAGON_8_GEN_3",
            ),
            normalized,
        )
    }

    @Test
    fun normalize_attrs_maps_legacy_separators_to_canonical_underscore_keys() {
        val normalized = Normalization.normalizeAttrs(
            mapOf(
                "memory-gb" to "256",
                "refresh rate hz" to "120",
                "chipset-family" to "Tensor G4",
            ),
        )

        assertEquals(
            mapOf(
                "memory_gb" to "256",
                "refresh_rate_hz" to "120",
                "chipset_family" to "Tensor G4",
            ),
            normalized,
        )
    }
}
