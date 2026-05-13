package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.ai.AiAgentFlowPolicy
import com.example.shoppingassistant.server.ai.AiAgentRegistryAdminService
import com.example.shoppingassistant.server.ai.AiAgentRegistryManifest
import com.example.shoppingassistant.server.ai.AiAgentRegistryOverrideRepository
import com.example.shoppingassistant.server.ai.AiAgentRegistryPersistentOverride
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.ai.PersistentAiAgentRegistryOverrideStore
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AiNormalizationAdminRoutesTest {

    @Before
    fun setUp() {
        val baseManifest = AiAgentRegistryManifest(
            registryVersion = "registry-v1",
            flows = mapOf(
                AiNormalizationFlow.VISUAL_SEARCH to AiAgentFlowPolicy(
                    enabled = true,
                    registryVersion = "registry-v1",
                    routeVersion = "visual-search/route/1",
                    contractName = "visual_search",
                    contractVersion = "visual-search/v1",
                ),
                AiNormalizationFlow.LISTING_OFFER to AiAgentFlowPolicy(
                    enabled = true,
                    registryVersion = "registry-v1",
                    routeVersion = "listing-offer/route/1",
                    contractName = "listing_offer",
                    contractVersion = "listing-offer/v1",
                ),
            ),
        )
        startKoin {
            modules(
                module {
                    single<AiAgentRegistryOverrideRepository> { InMemoryAdminRouteOverrideRepository() }
                    single {
                        PersistentAiAgentRegistryOverrideStore(
                            repository = get(),
                            baseManifestLoader = { baseManifest },
                            runtimeOverrideApplier = { it },
                        )
                    }
                    single { AiAgentRegistryAdminService(get()) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun admin_registry_requires_token() = testApplication {
        application {
            configureSerialization()
            routing {
                aiNormalizationAdminRoutes(
                    config = AiNormalizationAdminApiConfig(
                        enabled = true,
                        adminToken = "test-admin-token",
                    ),
                )
            }
        }

        val response = client.get("/api/admin/ai-normalization/registry")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun admin_registry_updates_and_deletes_persistent_override() = testApplication {
        application {
            configureSerialization()
            routing {
                aiNormalizationAdminRoutes(
                    config = AiNormalizationAdminApiConfig(
                        enabled = true,
                        adminToken = "test-admin-token",
                    ),
                )
            }
        }

        val updateResponse = client.put("/api/admin/ai-normalization/registry/visual_search") {
            header("X-Admin-Token", "test-admin-token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "enabled": false,
                  "routeVersion": "visual-search/route/2",
                  "contractVersion": "visual-search/v2",
                  "killSwitchReason": "manual-disable",
                  "updatedBy": "ops"
                }
                """.trimIndent(),
            )
        }
        val registryResponse = client.get("/api/admin/ai-normalization/registry") {
            header(HttpHeaders.Authorization, "Bearer test-admin-token")
        }
        val deleteResponse = client.delete("/api/admin/ai-normalization/registry/visual_search") {
            header("X-Admin-Token", "test-admin-token")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        assertTrue(updateResponse.bodyAsText().contains("manual-disable"))
        assertEquals(HttpStatusCode.OK, registryResponse.status)
        assertTrue(registryResponse.bodyAsText().contains("visual-search/route/2"))
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        assertTrue(deleteResponse.bodyAsText().contains("\"effectiveRegistryVersion\":\"registry-v1\""))
    }
}

private class InMemoryAdminRouteOverrideRepository : AiAgentRegistryOverrideRepository {
    private val rows = linkedMapOf<AiNormalizationFlow, AiAgentRegistryPersistentOverride>()

    override suspend fun listAll(): List<AiAgentRegistryPersistentOverride> = rows.values.toList()

    override suspend fun upsert(
        override: AiAgentRegistryPersistentOverride,
    ): AiAgentRegistryPersistentOverride {
        rows[override.flow] = override
        return override
    }

    override suspend fun delete(flow: AiNormalizationFlow): Boolean = rows.remove(flow) != null
}
