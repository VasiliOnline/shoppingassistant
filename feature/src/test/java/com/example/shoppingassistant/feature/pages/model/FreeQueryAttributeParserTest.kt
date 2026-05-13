package com.example.shoppingassistant.feature.pages.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeQueryAttributeParserTest {

    @Test
    fun exact_single_token_value_beats_multi_word_prefix_alias() {
        val colorDef = AttributeDef(
            key = "color",
            title = "Color",
            allowedValues = listOf(
                ValueDef(
                    code = "BLACK",
                    label = "Black",
                    synonyms = listOf("titanium black", "black"),
                ),
                ValueDef(
                    code = "TITANIUM",
                    label = "Titanium",
                    synonyms = listOf("titanium color"),
                ),
            ),
        )

        assertEquals("TITANIUM", matchFreeQueryAttributeValue(colorDef, "titanium"))
    }

    @Test
    fun plus_sign_is_preserved_in_compact_normalization() {
        assertEquals("sd7plusgen3", normalizeFreeQueryAttributeToken("sd 7+ gen 3"))
        assertEquals("snapdragon7plusgen3", normalizeFreeQueryAttributeToken("Snapdragon 7+ Gen 3"))
    }

    @Test
    fun exact_match_distinguishes_7_gen3_from_7_plus_gen3() {
        val chipsetDef = AttributeDef(
            key = "chipset_family",
            title = "Chipset",
            allowedValues = listOf(
                ValueDef(
                    code = "SNAPDRAGON_7_GEN_3",
                    label = "Snapdragon 7 Gen 3",
                    synonyms = listOf("sd 7 gen 3", "sd7gen3"),
                ),
                ValueDef(
                    code = "SNAPDRAGON_7_PLUS_GEN_3",
                    label = "Snapdragon 7+ Gen 3",
                    synonyms = listOf("sd 7+ gen 3", "sd7plusgen3"),
                ),
            ),
        )

        assertEquals("SNAPDRAGON_7_GEN_3", matchFreeQueryAttributeValue(chipsetDef, "sd7gen3"))
        assertEquals("SNAPDRAGON_7_PLUS_GEN_3", matchFreeQueryAttributeValue(chipsetDef, "sd7plusgen3"))
        assertEquals("SNAPDRAGON_7_PLUS_GEN_3", matchFreeQueryAttributeValue(chipsetDef, "sd7+gen3"))
    }
}
