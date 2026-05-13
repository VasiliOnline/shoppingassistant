package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceAdminApiRepositoryTest {
    @Test
    fun listReadinessInventory_decodes_branch_readiness() = runBlocking {
        val repository = repositoryWithEngine { request ->
            assertEquals("/api/catalog/readiness", request.url.encodedPath)
            respondJson(
                json.encodeToString(
                    listOf(
                        CatalogGovernanceAdminReadinessItem(
                            category = Category(
                                code = "TECH.PHONES",
                                segment = CategorySegment.TECH,
                            ),
                            readiness = CatalogCategoryReadiness.BETA,
                            editorialReadiness = CatalogCategoryReadiness.BETA,
                            operationalReadiness = CatalogCategoryReadiness.READY,
                            completenessGatePassed = false,
                            blockingIssues = listOf("missing_models"),
                        ),
                    ),
                ),
            )
        }

        val actual = repository.listReadinessInventory()

        assertEquals(1, actual.size)
        assertEquals("TECH.PHONES", actual.single().category.code)
        assertEquals(CatalogCategoryReadiness.BETA, actual.single().readiness)
    }

    @Test
    fun getRefreshStatus_decodes_scheduler_state() = runBlocking {
        val repository = repositoryWithEngine { request ->
            assertEquals("/api/catalog/governance/refresh-status", request.url.encodedPath)
            assertEquals(DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE, request.url.parameters["categoryCode"])
            respondJson(
                json.encodeToString(
                    CatalogGovernanceAdminRefreshStatus(
                        enabled = true,
                        configuredCategoryCode = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
                        effectiveCategoryCode = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
                        pollIntervalMs = 21_600_000L,
                        trigger = "SCHEDULED",
                        connectorTypes = listOf("OFFICIAL_PHONE_WEB_SOURCE"),
                        availableSourceCount = 3,
                        matchedSourceCount = 2,
                        matchedRegistryCodes = listOf("APPLE", "SAMSUNG"),
                        latestRunId = 11L,
                        latestRunStatus = "COMPLETED",
                        latestRunPublishStatus = "PUBLISHED",
                    ),
                ),
            )
        }

        val actual = repository.getRefreshStatus()

        assertTrue(actual.enabled)
        assertEquals(2, actual.matchedSourceCount)
        assertEquals(listOf("APPLE", "SAMSUNG"), actual.matchedRegistryCodes)
    }

    @Test
    fun listSources_decodes_governance_sources_payload() = runBlocking {
        val expected = listOf(
            CatalogGovernanceAdminSourceRegistryEntry(
                registryCode = "APPLE",
                categoryCode = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
                connectorType = "OFFICIAL_PHONE_WEB_SOURCE",
                sourceCode = "apple",
                displayName = "Apple",
                tier = "AUTHORITATIVE",
                enabled = true,
                autoPublish = true,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )
        val repository = repositoryWithEngine { request ->
            assertEquals("/api/catalog/governance/sources", request.url.encodedPath)
            assertEquals(DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE, request.url.parameters["categoryCode"])
            respondJson(json.encodeToString(expected))
        }

        val actual = repository.listSources()

        assertEquals(expected, actual)
    }

    @Test
    fun submitReviewAction_sends_query_parameters_and_decodes_response() = runBlocking {
        val repository = repositoryWithEngine { request ->
            assertEquals("/api/catalog/governance/review-action", request.url.encodedPath)
            assertEquals(DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE, request.url.parameters["categoryCode"])
            assertEquals("77", request.url.parameters["candidateId"])
            assertEquals("PROMOTE", request.url.parameters["action"])
            assertEquals("android_debug", request.url.parameters["actor"])
            assertEquals("manual_promote", request.url.parameters["reasonCode"])
            respondJson(
                json.encodeToString(
                    CatalogGovernanceAdminReviewActionResult(
                        categoryCode = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
                        candidateId = 77L,
                        attributeCode = "model",
                        action = "PROMOTE",
                        candidateStatus = "PROMOTED",
                        decisionId = 5L,
                        decisionAction = "PROMOTE",
                        reasonCode = "manual_promote",
                        actor = "android_debug",
                        publishStatus = "PUBLISHED",
                        canonicalCode = "IPHONE_18_PRO",
                    ),
                ),
            )
        }

        val actual = repository.submitReviewAction(
            candidateId = 77L,
            action = CatalogGovernanceAdminReviewAction.PROMOTE,
            actor = "android_debug",
            reasonCode = "manual_promote",
        )

        assertEquals("PROMOTE", actual.action)
        assertEquals("IPHONE_18_PRO", actual.canonicalCode)
    }

    @Test
    fun triggerRefresh_passes_all_registry_codes() = runBlocking {
        val repository = repositoryWithEngine { request ->
            assertEquals("/api/catalog/governance/refresh", request.url.encodedPath)
            assertEquals(listOf("APPLE", "SAMSUNG"), request.url.parameters.getAll("registryCode"))
            assertEquals("SCHEDULED", request.url.parameters["trigger"])
            respondJson(
                json.encodeToString(
                    CatalogGovernanceAdminRefreshTriggerResult(
                        categoryCode = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
                        requestedRegistryCodes = listOf("APPLE", "SAMSUNG"),
                        runs = emptyList(),
                    ),
                ),
            )
        }

        val actual = repository.triggerRefresh(
            registryCodes = listOf("APPLE", "SAMSUNG"),
            trigger = "SCHEDULED",
        )

        assertTrue(actual.requestedRegistryCodes.contains("APPLE"))
        assertTrue(actual.requestedRegistryCodes.contains("SAMSUNG"))
    }

    private fun repositoryWithEngine(
        handler: MockRequestHandler,
    ): CatalogGovernanceAdminApiRepository {
        val engine = MockEngine(handler)
        val backendClient = BackendClient(
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(json)
                }
            },
        )
        return CatalogGovernanceAdminApiRepository(
            backendClient = backendClient,
            baseUrl = "https://backend.test",
        )
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
