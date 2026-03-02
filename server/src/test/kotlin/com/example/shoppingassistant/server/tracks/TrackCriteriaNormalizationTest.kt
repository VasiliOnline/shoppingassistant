package com.example.shoppingassistant.server.tracks

import com.example.shoppingassistant.domain.tracks.TrackAttributeRange
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrackCriteriaNormalizationTest {

    @Test
    fun dedupKeyIsDeterministicForEquivalentExtendedTargetAttributes() {
        val targetA = TrackDedupKeyFactory.normalizeTarget(
            type = TrackType.PRODUCT,
            matchKeyRaw = "bm:Apple|iPhone 15 Pro",
            categoryCodeRaw = "tech.phones",
            attributesRaw = mapOf("color" to "black"),
            attributesMultiRaw = mapOf("storage" to listOf("256GB", "128GB")),
            attributesRangeRaw = mapOf(
                "price" to TrackAttributeRange(min = "100", max = "500", unit = "USD"),
            ),
            queryTextRaw = "iPhone 15 Pro",
        ) ?: error("expected normalized target A")

        val targetB = TrackDedupKeyFactory.normalizeTarget(
            type = TrackType.PRODUCT,
            matchKeyRaw = "bm:apple|iphone 15 pro",
            categoryCodeRaw = "TECH.PHONES",
            attributesRaw = mapOf("color" to "black"),
            attributesMultiRaw = mapOf("storage" to listOf("128gb", "256gb")),
            attributesRangeRaw = mapOf(
                "price" to TrackAttributeRange(min = "100", max = "500", unit = "usd"),
            ),
            queryTextRaw = "  iphone   15 pro  ",
        ) ?: error("expected normalized target B")

        val keyA = TrackDedupKeyFactory.buildDedupKey(targetA, TrackFilters())
        val keyB = TrackDedupKeyFactory.buildDedupKey(targetB, TrackFilters())

        assertEquals(keyA, keyB)
        assertTrue(keyA.startsWith("v2:"))
    }

    @Test
    fun dedupKeyChangesWhenQueryTextChanges() {
        val base = TrackDedupKeyFactory.normalizeTarget(
            type = TrackType.PRODUCT,
            matchKeyRaw = "bm:Apple|iPhone 15 Pro",
            categoryCodeRaw = "TECH.PHONES",
            attributesRaw = mapOf("color" to "black"),
            queryTextRaw = "iphone 15 pro",
        ) ?: error("expected normalized base target")

        val variant = TrackDedupKeyFactory.normalizeTarget(
            type = TrackType.PRODUCT,
            matchKeyRaw = "bm:Apple|iPhone 15 Pro",
            categoryCodeRaw = "TECH.PHONES",
            attributesRaw = mapOf("color" to "black"),
            queryTextRaw = "iphone 15 plus",
        ) ?: error("expected normalized variant target")

        val keyA = TrackDedupKeyFactory.buildDedupKey(base, TrackFilters())
        val keyB = TrackDedupKeyFactory.buildDedupKey(variant, TrackFilters())

        assertNotEquals(keyA, keyB)
    }
}
