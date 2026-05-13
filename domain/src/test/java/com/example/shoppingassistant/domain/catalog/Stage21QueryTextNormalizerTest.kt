package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class Stage21QueryTextNormalizerTest {

    @Test
    fun preserves_plus_semantics_for_phone_model_labels() {
        assertEquals(
            "galaxy s24 plus",
            Stage21QueryTextNormalizer.normalize("Galaxy S24+"),
        )
    }

    @Test
    fun preserves_plus_semantics_for_chipset_labels() {
        assertEquals(
            "snapdragon 7 plus gen 3",
            Stage21QueryTextNormalizer.normalize("Snapdragon 7+ Gen 3"),
        )
    }
}
