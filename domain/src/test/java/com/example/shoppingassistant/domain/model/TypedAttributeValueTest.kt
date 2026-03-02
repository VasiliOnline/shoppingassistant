package com.example.shoppingassistant.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypedAttributeValueTest {

    @Test
    fun json_roundtrip_preserves_primitive_types() {
        val json = Json { encodeDefaults = true }
        val value = mapOf(
            "title" to TypedAttributeValue.Text("iPhone"),
            "ram_gb" to TypedAttributeValue.Number(8.0),
            "in_stock" to TypedAttributeValue.Bool(true),
        )

        val payload = json.encodeToString(value)
        val restored = json.decodeFromString<Map<String, TypedAttributeValue>>(payload)

        assertEquals(TypedAttributeValue.Text("iPhone"), restored["title"])
        assertEquals(TypedAttributeValue.Number(8.0), restored["ram_gb"])
        assertEquals(TypedAttributeValue.Bool(true), restored["in_stock"])
    }

    @Test
    fun numeric_parsing_extensions_work_for_text_and_number() {
        assertEquals(12.5f, TypedAttributeValue.Number(12.5).asFloatOrNull())
        assertEquals(9.0f, TypedAttributeValue.Text("9").asFloatOrNull())
        assertNull(TypedAttributeValue.Bool(false).asFloatOrNull())
    }
}
