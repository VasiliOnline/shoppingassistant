package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogHttpContractPaths
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRuntimeClientIntegrationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    @Test
    fun repository_reads_catalog_runtime_contract_end_to_end() = runBlocking {
        val categories = listOf(
            Category(
                code = "TECH.PHONES",
                segment = CategorySegment.TECH,
                title = localizedTextOf("ru" to "Смартфоны", "en" to "Phones"),
                status = CategoryStatus.ACTIVE,
            ),
            Category(
                code = "TECH.OLD_PHONES",
                segment = CategorySegment.TECH,
                title = localizedTextOf("ru" to "Старые смартфоны", "en" to "Old Phones"),
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.PHONES",
            ),
        )
        val effectiveSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == "TECH.PHONES" }
            .toCategoryEffectiveSpec(constraints = emptyList<CatalogConstraints>())

        var compatibilityCalls = 0
        var versionCalls = 0
        var categoriesCalls = 0
        var effectiveSpecCalls = 0
        var lastBrand: String? = null
        var lastModel: String? = null

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                CatalogHttpContractPaths.runtimeCompatibility -> {
                    compatibilityCalls += 1
                    respond(
                        content = """
                            {
                              "schemaVersion": "1.0.0",
                              "currentSchemaVersion": "1.0.0",
                              "minSupportedClientSchemaVersion": "1.0.0",
                              "currentDataVersion": "2.2.0",
                              "policy": {
                                "schemaVersioningMode": "SEMVER_SAME_MAJOR_SERVER_GTE_CLIENT",
                                "minSupportedClientStrategy": "SERVER_DECLARED_MAJOR_FLOOR",
                                "dataVersionMode": "SERVER_AUTHORITATIVE_NEGOTIATED_STABILITY",
                                "embeddedParityRequiredWhen": ["SEED_FALLBACK_ENABLED"],
                                "negotiatedDataVersionMustStayStable": true,
                                "requiredVersionHeaders": [
                                  "X-Catalog-Schema-Version",
                                  "X-Catalog-Data-Version",
                                  "X-Catalog-Min-Supported-Client-Schema-Version"
                                ],
                                "versionEndpoint": "/api/catalog/version"
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = versionHeaders(),
                    )
                }

                CatalogHttpContractPaths.version -> {
                    versionCalls += 1
                    respond(
                        content = """
                            {
                              "schemaVersion": "1.0.0",
                              "dataVersion": "2.2.0",
                              "minSupportedClientSchemaVersion": "1.0.0"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = versionHeaders(),
                    )
                }

                CatalogHttpContractPaths.categories -> {
                    categoriesCalls += 1
                    respond(
                        content = json.encodeToString(categories),
                        status = HttpStatusCode.OK,
                        headers = versionHeaders(),
                    )
                }

                "/api/catalog/effective-spec/TECH.PHONES" -> {
                    effectiveSpecCalls += 1
                    lastBrand = request.url.parameters["brand"]
                    lastModel = request.url.parameters["model"]
                    respond(
                        content = json.encodeToString(CatalogCategoryEffectiveSpec.serializer(), effectiveSpec),
                        status = HttpStatusCode.OK,
                        headers = versionHeaders(),
                    )
                }

                else -> error("Unexpected request path: ${request.url.encodedPath}")
            }
        }

        val backendClient = BackendClient(
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(json)
                }
            },
        )
        val dataSource = CatalogApiDataSource(
            backendClient = backendClient,
            fallback = object : CatalogDataSource {
                override suspend fun listCategories(): List<Category> = error("fallback should not be used")
                override suspend fun getEffectiveSpec(
                    code: String,
                    brand: String?,
                    model: String?,
                ): CatalogCategoryEffectiveSpec? = error("fallback should not be used")
            },
            allowSeedFallback = false,
            versionVerifier = StrictCatalogRuntimeVersionVerifier(
                backendClient = backendClient,
                embeddedDataVersion = "2.2.0",
                expectedSchemaVersion = "1.0.0",
            ),
        )
        val repository = CatalogRepositoryImpl(dataSource)

        val listedCategories = repository.listCategories()
        val spec = repository.getCategoryEffectiveSpec(
            categoryCode = "TECH.PHONES",
            brand = "Apple",
            model = "iPhone 16 Pro",
        )
        val resolution = repository.resolveCategoryCode("TECH.OLD_PHONES")

        assertEquals(2, categoriesCalls)
        assertEquals(1, compatibilityCalls)
        assertEquals(1, versionCalls)
        assertEquals(1, effectiveSpecCalls)
        assertEquals("Apple", lastBrand)
        assertEquals("iPhone 16 Pro", lastModel)
        assertEquals(2, listedCategories.size)
        assertNotNull(spec)
        assertNotNull(resolution)
        assertEquals("TECH.PHONES", spec?.category?.code)
        assertTrue(spec?.attributes?.any { it.code == "brand" } == true)
        assertTrue(resolution?.wasRedirected == true)
        assertEquals("TECH.PHONES", resolution?.resolvedCode)
    }

    private fun versionHeaders(): Headers =
        Headers.build {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            append("X-Catalog-Schema-Version", "1.0.0")
            append("X-Catalog-Data-Version", "2.2.0")
            append("X-Catalog-Min-Supported-Client-Schema-Version", "1.0.0")
        }
}
