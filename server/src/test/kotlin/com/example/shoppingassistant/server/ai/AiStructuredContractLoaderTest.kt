package com.example.shoppingassistant.server.ai

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class AiStructuredContractLoaderTest {

    @Test
    fun loads_visual_search_contract_assets() {
        val contract = AiStructuredContractLoader.load("visual_search")

        assertTrue(contract.systemPrompt.contains("marketplace visual product normalizer"))
        assertTrue(contract.userPromptTemplate.contains("{{grounding_packet}}"))
        assertEquals(
            "boolean",
            contract.schema["properties"]
                ?.jsonObject
                ?.get("needs_retake")
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertTrue(
            contract.schema["properties"]
                ?.jsonObject
                ?.containsKey("needs_more_photos") == true,
        )
        assertTrue(
            contract.schema["properties"]
                ?.jsonObject
                ?.containsKey("hypotheses") == true,
        )
        assertTrue(
            contract.schema["required"]
                ?.toString()
                ?.contains("primary_object") == true,
        )
    }

    @Test
    fun loads_visual_search_router_contract_assets() {
        val contract = AiStructuredContractLoader.load("visual_search_router")

        assertTrue(contract.systemPrompt.contains("marketplace visual search router"))
        assertTrue(contract.userPromptTemplate.contains("{{grounding_packet}}"))
        assertEquals(
            "string",
            contract.schema["properties"]
                ?.jsonObject
                ?.get("route_status")
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertTrue(
            contract.schema["properties"]
                ?.jsonObject
                ?.containsKey("free_text_type") == true,
        )
        assertTrue(
            contract.schema["properties"]
                ?.jsonObject
                ?.containsKey("visible_brand") == true,
        )
        assertTrue(
            contract.schema["required"]
                ?.toString()
                ?.contains("identity_mode") == true,
        )
    }

    @Test
    fun loads_visual_search_identity_contract_assets() {
        val contract = AiStructuredContractLoader.load("visual_search_identity")

        assertTrue(contract.systemPrompt.contains("marketplace visual identity enricher"))
        assertTrue(contract.userPromptTemplate.contains("{{router_result}}"))
        assertTrue(contract.userPromptTemplate.contains("{{grounding_packet}}"))
        assertEquals(
            "string",
            contract.schema["properties"]
                ?.jsonObject
                ?.get("identity_status")
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertTrue(
            contract.schema["properties"]
                ?.jsonObject
                ?.containsKey("model_candidates") == true,
        )
        assertTrue(
            contract.schema["required"]
                ?.toString()
                ?.contains("route_conflict") == true,
        )
    }

    @Test
    fun loads_listing_offer_contract_assets() {
        val contract = AiStructuredContractLoader.load("listing_offer")

        assertTrue(contract.systemPrompt.contains("strict seller draft"))
        assertTrue(contract.userPromptTemplate.contains("{{seller_hints}}"))
        assertTrue(
            contract.schema["properties"]
                ?.jsonObject
                ?.containsKey("next_action") == true,
        )
    }
}
