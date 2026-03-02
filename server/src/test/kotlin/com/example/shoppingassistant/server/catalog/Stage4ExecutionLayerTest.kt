package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.model.TypedAttributeValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Stage4ExecutionLayerTest {

    private val executionLayer = Stage4ExecutionLayerImpl()

    @Test
    fun ingest_maps_dictionary_alias_to_value_code() {
        val normalized = executionLayer.normalizeAttributesForIngest(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("color" to "розовый"),
        )

        assertEquals("PINK", normalized["color"])
    }

    @Test
    fun ingest_drops_unknown_value_for_closed_set_attribute() {
        val normalized = executionLayer.normalizeAttributesForIngest(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("condition" to "абсолютно новый из коробки"),
        )

        assertFalse(normalized.containsKey("condition"))
    }

    @Test
    fun search_keeps_unknown_value_for_closed_set_attribute_as_token() {
        val normalized = executionLayer.normalizeAttributesForSearch(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("condition" to "absolutely_new"),
        )

        assertEquals("ABSOLUTELY_NEW", normalized["condition"])
    }

    @Test
    fun dedup_uses_stage4_template_for_category_profile_attribute() {
        val normalized = executionLayer.normalizeAttributesForIngest(
            categoryCode = "TECH.PHONES",
            attributes = linkedMapOf(
                " Color " to "розовый",
                "color" to "pink",
            ),
        )

        assertEquals(1, normalized.size)
        assertEquals("PINK", normalized["color"])
    }

    @Test
    fun ingest_normalizes_open_text_attributes() {
        val normalized = executionLayer.normalizeAttributesForIngest(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("brand" to "  Apple   Inc  "),
        )

        assertTrue(normalized.containsKey("brand"))
        assertEquals("apple inc", normalized["brand"])
    }

    @Test
    fun ingest_strict_drops_unknown_attribute_and_reports_reason() {
        val outcome = executionLayer.normalizeAttributesForIngestStrict(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("unknown_dimension" to "42"),
        )

        assertTrue(outcome.normalizedAttributes.isEmpty())
        assertEquals(1, outcome.droppedCount)
        assertEquals(1, outcome.unknownAttributeCount)
        assertTrue(outcome.reasonCodes.contains("UNKNOWN_ATTRIBUTE:unknown_dimension"))
    }

    @Test
    fun ingest_strict_drops_incompatible_number_value() {
        val outcome = executionLayer.normalizeAttributesForIngestStrict(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("memory_gb" to "not_a_number"),
        )

        assertTrue(outcome.normalizedAttributes.isEmpty())
        assertEquals(1, outcome.droppedCount)
        assertTrue(outcome.reasonCodes.contains("INCOMPATIBLE_VALUE:memory_gb"))
    }

    @Test
    fun ingest_strict_drops_out_of_range_value() {
        val outcome = executionLayer.normalizeAttributesForIngestStrict(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("battery_health_percent" to "150"),
        )

        assertTrue(outcome.normalizedAttributes.isEmpty())
        assertTrue(outcome.reasonCodes.contains("OUT_OF_RANGE:battery_health_percent"))
    }

    @Test
    fun ingest_strict_drops_unit_mismatch() {
        val outcome = executionLayer.normalizeAttributesForIngestStrict(
            categoryCode = "FOOD.READY_MEALS",
            attributes = mapOf("shelf_life_days" to "30 kg"),
        )

        assertTrue(outcome.normalizedAttributes.isEmpty())
        assertTrue(outcome.reasonCodes.contains("UNIT_MISMATCH:shelf_life_days"))
    }

    @Test
    fun ingest_strict_drops_pattern_mismatch() {
        val outcome = executionLayer.normalizeAttributesForIngestStrict(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("region_code" to "@@bad"),
        )

        assertTrue(outcome.normalizedAttributes.isEmpty())
        assertTrue(outcome.reasonCodes.contains("PATTERN_MISMATCH:region_code"))
    }

    @Test
    fun ingest_strict_reports_required_if_missing() {
        val outcome = executionLayer.normalizeAttributesForIngestStrict(
            categoryCode = "TECH.PHONES",
            attributes = mapOf("condition" to "used"),
        )

        assertEquals("USED", outcome.normalizedAttributes["condition"])
        assertTrue(outcome.reasonCodes.contains("REQUIRED_IF_MISSING:battery_health_percent"))
    }

    @Test
    fun typed_conversion_uses_stage4_value_types() {
        val typed = executionLayer.toTypedAttributes(
            mapOf(
                "memory_gb" to "256",
                "dual_sim" to "true",
                "brand" to "Apple",
            ),
        )

        assertIs<TypedAttributeValue.Number>(typed["memory_gb"])
        assertIs<TypedAttributeValue.Bool>(typed["dual_sim"])
        assertIs<TypedAttributeValue.Text>(typed["brand"])

        val raw = executionLayer.toRawStringAttributes(typed)
        assertEquals("256", raw["memory_gb"])
        assertEquals("true", raw["dual_sim"])
        assertEquals("Apple", raw["brand"])
    }

    @Test
    fun managed_env_fails_fast_when_stage4_db_snapshot_is_incomplete() {
        val strictExecutionLayer = Stage4ExecutionLayerImpl(
            appEnvResolver = { "staging" },
        )

        val error = assertFailsWith<IllegalStateException> {
            strictExecutionLayer.normalizeAttributesForIngest(
                categoryCode = "TECH.PHONES",
                attributes = mapOf("brand" to "Apple"),
            )
        }

        assertTrue(error.message.orEmpty().contains("partial") || error.message.orEmpty().contains("empty"))
    }
}
