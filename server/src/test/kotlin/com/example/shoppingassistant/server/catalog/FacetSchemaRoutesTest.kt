package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class FacetSchemaRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<FacetDefinitionRepository> { InMemoryFacetDefinitionRepository(CatalogSeed.facetDefinitions) }
                    single<FacetPresetRepository> { InMemoryFacetPresetRepository(CatalogSeed.facetPresets) }
                    single<FacetCollectionRepository> { InMemoryFacetCollectionRepository(CatalogSeed.facetCollections) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun definitions_endpoint_supports_category_filter() = testApplication {
        application {
            configureSerialization()
            routing { facetSchemaRoutes() }
        }

        val response = client.get("/api/catalog/facets/definitions?categoryCode=FOOD.READY_MEALS")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString(
            deserializer = ListSerializer(FacetDefinition.serializer()),
            string = response.bodyAsText(),
        )
        assertTrue(body.isNotEmpty())
        assertTrue(body.all { definition -> "FOOD.READY_MEALS" in definition.appliesToCategoryCodes })
    }

    @Test
    fun collection_by_browse_endpoint_returns_linked_collection() = testApplication {
        application {
            configureSerialization()
            routing { facetSchemaRoutes() }
        }

        val response = client.get("/api/catalog/facets/collections/by-browse/B.FOOD.READY.05")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString(
            deserializer = FacetCollection.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("B.FOOD.READY.05", body.collectionCode)
        assertEquals("FP.FOOD.READY.PIZZA", body.presetCode)
    }

    @Test
    fun preset_endpoint_returns_404_for_missing_code() = testApplication {
        application {
            configureSerialization()
            routing { facetSchemaRoutes() }
        }

        val response = client.get("/api/catalog/facets/presets/UNKNOWN.PRESET")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}

private class InMemoryFacetDefinitionRepository(
    private val definitions: List<FacetDefinition>,
) : FacetDefinitionRepository {
    override suspend fun listFacetDefinitions(): List<FacetDefinition> = definitions

    override suspend fun listFacetDefinitions(categoryCode: String): List<FacetDefinition> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()
        return definitions.filter { definition ->
            definition.appliesToCategoryCodes.any { it.equals(normalized, ignoreCase = true) }
        }
    }

    override suspend fun getFacetDefinition(facetKey: String): FacetDefinition? {
        val normalized = facetKey.trim()
        if (normalized.isBlank()) return null
        return definitions.firstOrNull { definition -> definition.facetKey.equals(normalized, ignoreCase = true) }
    }
}

private class InMemoryFacetPresetRepository(
    private val presets: List<FacetPreset>,
) : FacetPresetRepository {
    override suspend fun listFacetPresets(): List<FacetPreset> = presets

    override suspend fun listFacetPresets(categoryCode: String): List<FacetPreset> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()
        return presets.filter { preset -> preset.categoryCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun getFacetPreset(presetCode: String): FacetPreset? {
        val normalized = presetCode.trim()
        if (normalized.isBlank()) return null
        return presets.firstOrNull { preset -> preset.presetCode.equals(normalized, ignoreCase = true) }
    }
}

private class InMemoryFacetCollectionRepository(
    private val collections: List<FacetCollection>,
) : FacetCollectionRepository {
    override suspend fun listFacetCollections(): List<FacetCollection> = collections

    override suspend fun listFacetCollections(categoryCode: String): List<FacetCollection> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()
        return collections.filter { collection -> collection.categoryCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun getFacetCollection(collectionCode: String): FacetCollection? {
        val normalized = collectionCode.trim()
        if (normalized.isBlank()) return null
        return collections.firstOrNull { collection -> collection.collectionCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun getFacetCollectionByBrowseCode(browseCode: String): FacetCollection? {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return null
        return collections.firstOrNull { collection -> collection.browseCode?.equals(normalized, ignoreCase = true) == true }
    }
}
