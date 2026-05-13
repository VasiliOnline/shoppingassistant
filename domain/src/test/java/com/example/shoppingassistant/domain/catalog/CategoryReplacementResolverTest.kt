package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryReplacementResolverTest {

    @Test
    fun resolvesDeprecatedChain_toActiveCategory() {
        val categories = listOf(
            Category(
                code = "TECH.OLD",
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.MID",
            ),
            Category(
                code = "TECH.MID",
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.NEW",
            ),
            Category(
                code = "TECH.NEW",
                segment = CategorySegment.TECH,
                status = CategoryStatus.ACTIVE,
            ),
        )

        val resolution = CategoryReplacementResolver.resolve("TECH.OLD", categories)
        requireNotNull(resolution)
        assertEquals("TECH.OLD", resolution.requestedCode)
        assertEquals("TECH.NEW", resolution.resolvedCode)
        assertEquals(listOf("TECH.OLD", "TECH.MID", "TECH.NEW"), resolution.redirectChain)
        assertTrue(resolution.wasRedirected)
        assertFalse(resolution.cycleDetected)
        assertNull(resolution.unresolvedTarget)
    }

    @Test
    fun detectsReplacementCycle() {
        val categories = listOf(
            Category(
                code = "TECH.A",
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.B",
            ),
            Category(
                code = "TECH.B",
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.A",
            ),
        )

        val resolution = CategoryReplacementResolver.resolve("TECH.A", categories)
        requireNotNull(resolution)
        assertTrue(resolution.cycleDetected)
        assertTrue(resolution.redirectChain.size >= 3)
    }

    @Test
    fun returnsNull_forUnknownCategory() {
        val resolution = CategoryReplacementResolver.resolve(
            requestedCode = "UNKNOWN.CODE",
            categories = emptyList(),
        )
        assertNull(resolution)
    }
}

