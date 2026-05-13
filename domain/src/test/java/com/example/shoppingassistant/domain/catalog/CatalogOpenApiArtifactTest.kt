package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogOpenApiArtifactTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun openapi_artifact_must_cover_catalog_runtime_paths() {
        val document = loadDocument()
        assertEquals("3.1.0", document["openapi"]?.toString()?.trim('"'))

        val paths = document["paths"]?.let { it as JsonObject } ?: error("OpenAPI paths missing.")
        CatalogHttpContractPaths.openApiPaths.forEach { path ->
            assertTrue("OpenAPI path '$path' is missing.", paths.containsKey(path))
        }
        assertEquals(
            "OpenAPI path set must stay in sync with catalog HTTP contract paths.",
            CatalogHttpContractPaths.openApiPaths,
            paths.keys,
        )
        assertTrue("Legacy /api/catalog/constraints must be removed from OpenAPI.", !paths.containsKey("/api/catalog/constraints"))
    }

    private fun loadDocument(): JsonObject =
        json.parseToJsonElement(
            javaClass.classLoader
                .getResourceAsStream(CatalogArtifactPaths.openApiDocument)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("OpenAPI artifact '${CatalogArtifactPaths.openApiDocument}' not found."),
        ) as JsonObject
}
