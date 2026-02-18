package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class FacetDefinitionApiRepository(
    private val backendClient: BackendClient,
    private val fallback: FacetDefinitionRepository,
) : FacetDefinitionRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listFacetDefinitions(): List<FacetDefinition> {
        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/definitions")
        }.getOrNull() ?: return fallback.listFacetDefinitions()

        return if (response.status.isSuccess()) {
            runCatching { response.body<List<FacetDefinition>>() }.getOrElse { fallback.listFacetDefinitions() }
        } else {
            fallback.listFacetDefinitions()
        }
    }

    override suspend fun listFacetDefinitions(categoryCode: String): List<FacetDefinition> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/definitions") {
                parameter("categoryCode", normalized)
            }
        }.getOrNull() ?: return fallback.listFacetDefinitions(normalized)

        return if (response.status.isSuccess()) {
            runCatching { response.body<List<FacetDefinition>>() }.getOrElse { fallback.listFacetDefinitions(normalized) }
        } else {
            fallback.listFacetDefinitions(normalized)
        }
    }

    override suspend fun getFacetDefinition(facetKey: String): FacetDefinition? {
        val normalized = facetKey.trim()
        if (normalized.isBlank()) return null

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/definitions/$normalized")
        }.getOrNull() ?: return fallback.getFacetDefinition(normalized)

        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> runCatching { response.body<FacetDefinition>() }.getOrElse {
                fallback.getFacetDefinition(normalized)
            }
            else -> fallback.getFacetDefinition(normalized)
        }
    }
}

class FacetPresetApiRepository(
    private val backendClient: BackendClient,
    private val fallback: FacetPresetRepository,
) : FacetPresetRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listFacetPresets(): List<FacetPreset> {
        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/presets")
        }.getOrNull() ?: return fallback.listFacetPresets()

        return if (response.status.isSuccess()) {
            runCatching { response.body<List<FacetPreset>>() }.getOrElse { fallback.listFacetPresets() }
        } else {
            fallback.listFacetPresets()
        }
    }

    override suspend fun listFacetPresets(categoryCode: String): List<FacetPreset> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/presets") {
                parameter("categoryCode", normalized)
            }
        }.getOrNull() ?: return fallback.listFacetPresets(normalized)

        return if (response.status.isSuccess()) {
            runCatching { response.body<List<FacetPreset>>() }.getOrElse { fallback.listFacetPresets(normalized) }
        } else {
            fallback.listFacetPresets(normalized)
        }
    }

    override suspend fun getFacetPreset(presetCode: String): FacetPreset? {
        val normalized = presetCode.trim()
        if (normalized.isBlank()) return null

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/presets/$normalized")
        }.getOrNull() ?: return fallback.getFacetPreset(normalized)

        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> runCatching { response.body<FacetPreset>() }.getOrElse {
                fallback.getFacetPreset(normalized)
            }
            else -> fallback.getFacetPreset(normalized)
        }
    }
}

class FacetCollectionApiRepository(
    private val backendClient: BackendClient,
    private val fallback: FacetCollectionRepository,
) : FacetCollectionRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listFacetCollections(): List<FacetCollection> {
        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections")
        }.getOrNull() ?: return fallback.listFacetCollections()

        return if (response.status.isSuccess()) {
            runCatching { response.body<List<FacetCollection>>() }.getOrElse { fallback.listFacetCollections() }
        } else {
            fallback.listFacetCollections()
        }
    }

    override suspend fun listFacetCollections(categoryCode: String): List<FacetCollection> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections") {
                parameter("categoryCode", normalized)
            }
        }.getOrNull() ?: return fallback.listFacetCollections(normalized)

        return if (response.status.isSuccess()) {
            runCatching { response.body<List<FacetCollection>>() }.getOrElse { fallback.listFacetCollections(normalized) }
        } else {
            fallback.listFacetCollections(normalized)
        }
    }

    override suspend fun getFacetCollection(collectionCode: String): FacetCollection? {
        val normalized = collectionCode.trim()
        if (normalized.isBlank()) return null

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections/$normalized")
        }.getOrNull() ?: return fallback.getFacetCollection(normalized)

        return decodeFacetCollectionResponse(response, fallbackValue = fallback.getFacetCollection(normalized))
    }

    override suspend fun getFacetCollectionByBrowseCode(browseCode: String): FacetCollection? {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return null

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections/by-browse/$normalized")
        }.getOrNull() ?: return fallback.getFacetCollectionByBrowseCode(normalized)

        return decodeFacetCollectionResponse(
            response = response,
            fallbackValue = fallback.getFacetCollectionByBrowseCode(normalized),
        )
    }

    private suspend fun decodeFacetCollectionResponse(
        response: HttpResponse,
        fallbackValue: FacetCollection?,
    ): FacetCollection? = when {
        response.status == HttpStatusCode.NotFound -> null
        response.status.isSuccess() -> runCatching { response.body<FacetCollection>() }.getOrElse { fallbackValue }
        else -> fallbackValue
    }
}
