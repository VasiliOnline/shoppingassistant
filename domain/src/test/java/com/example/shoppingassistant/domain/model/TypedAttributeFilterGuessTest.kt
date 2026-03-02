package com.example.shoppingassistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TypedAttributeFilterGuessTest {

    @Test
    fun parses_operators_and_ranges() {
        val filters = mapOf(
            "ram" to ">=8",
            "screen" to "6.1..6,9",
            "weight" to "<=1.5",
        ).toTypedAttributeFiltersGuess()

        val ram = filters["ram"]
        assertNotNull(ram)
        assertEquals(TypedAttributeOperator.GTE, ram?.op)
        assertEquals(8.0, (ram?.value as TypedAttributeValue.Number).value, 0.0)

        val screen = filters["screen"]
        assertNotNull(screen)
        assertEquals(TypedAttributeOperator.BETWEEN, screen?.op)
        assertEquals(6.1, (screen?.from as TypedAttributeValue.Number).value, 0.0)
        assertEquals(6.9, (screen?.to as TypedAttributeValue.Number).value, 0.0)

        val weight = filters["weight"]
        assertNotNull(weight)
        assertEquals(TypedAttributeOperator.LTE, weight?.op)
        assertEquals(1.5, (weight?.value as TypedAttributeValue.Number).value, 0.0)
    }

    @Test
    fun parses_in_contains_and_exists_flags() {
        val filters = mapOf(
            "color" to "black, white",
            "title" to "*pro max*",
            "nfc" to "exists",
            "warranty" to "!exists",
        ).toTypedAttributeFiltersGuess()

        val colors = filters["color"]
        assertNotNull(colors)
        assertEquals(TypedAttributeOperator.IN, colors?.op)
        assertEquals(2, colors?.values?.size)

        val title = filters["title"]
        assertNotNull(title)
        assertEquals(TypedAttributeOperator.CONTAINS, title?.op)
        assertEquals("pro max", (title?.value as TypedAttributeValue.Text).value)

        assertEquals(TypedAttributeOperator.EXISTS, filters["nfc"]?.op)
        assertEquals(TypedAttributeOperator.NOT_EXISTS, filters["warranty"]?.op)
    }
}
