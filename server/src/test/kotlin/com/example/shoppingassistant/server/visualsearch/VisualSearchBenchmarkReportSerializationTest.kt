package com.example.shoppingassistant.server.visualsearch

import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class VisualSearchBenchmarkReportSerializationTest {

    @Test
    fun serializes_contract_and_config_snapshots() {
        val report = VisualSearchBenchmarkReport(
            manifestName = "snapshot-test",
            runs = 1,
            timeoutMs = 30_000,
            contractSnapshot = VisualSearchBenchmarkContractSnapshot(
                contractName = "visual_search",
                schemaName = "visual_search_normalize_draft",
                promptProfile = "default",
                systemPromptSha256 = "system-hash",
                userPromptTemplateSha256 = "user-hash",
                schemaSha256 = "schema-hash",
                systemPrompt = "system prompt",
                userPromptTemplate = "user prompt {{grounding_packet}}",
                schemaJson = """{"type":"object"}""",
            ),
            modelReports = listOf(
                VisualSearchBenchmarkModelReport(
                    model = "gpt-test",
                    modelUri = "gpt-test",
                    configSnapshot = VisualSearchBenchmarkConfigSnapshot(
                        provider = "OPENAI",
                        activeModel = "gpt-test",
                        activeModelRef = "gpt-test",
                        timeoutMs = 30_000,
                        promptProfile = "default",
                        maxCompletionTokens = 900,
                        shortlistCategoryLimit = 8,
                        shortlistFamilyLimit = 4,
                        shortlistModelLimit = 4,
                        groundingAttributeLimit = 3,
                        groundingAllowedValueLimit = 3,
                        hintLimit = 8,
                        requireHintEvidenceForIdentity = true,
                    ),
                    invocationCount = 0,
                    usableRate = 0.0,
                    bindSuccessRate = 0.0,
                ),
            ),
        )

        val encoded = Json.encodeToString(report)

        assertTrue(encoded.contains("contractSnapshot"))
        assertTrue(encoded.contains("systemPromptSha256"))
        assertTrue(encoded.contains("configSnapshot"))
        assertTrue(encoded.contains("requireHintEvidenceForIdentity"))
    }
}
