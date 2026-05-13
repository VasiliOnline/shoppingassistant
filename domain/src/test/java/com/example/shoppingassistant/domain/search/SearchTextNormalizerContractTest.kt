package com.example.shoppingassistant.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextNormalizerContractTest {

    @Test
    fun normalize_collapses_spaces_and_trims() {
        assertEquals("iphone 16 pro", SearchTextNormalizer.normalize("  iphone   16   pro  "))
    }

    @Test
    fun normalize_for_editing_keeps_single_trailing_space() {
        assertEquals("iphone 16 ", SearchTextNormalizer.normalizeForEditing("  iphone   16   "))
    }

    @Test
    fun normalize_token_compacts_mixed_language_symbols() {
        assertEquals("iphone16про", SearchTextNormalizer.normalizeToken(" iPhone-16/ПРО "))
    }
}

