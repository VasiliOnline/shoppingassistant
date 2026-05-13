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
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
) : FacetDefinitionRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listFacetDefinitions(): List<FacetDefinition> {
        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetDefinitions.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listFacetDefinitions() },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/definitions")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetDefinitions",
                error = error,
                fallbackCall = { fallback.listFacetDefinitions() },
            )
        }

        return if (response.status.isSuccess()) {
            runCatching { versionVerifier.verifyResponseVersion(response, operation = "listFacetDefinitions") }
                .getOrElse { error ->
                    return fallbackOrThrow(
                        operation = "listFacetDefinitions.versionHeader",
                        error = error,
                        fallbackCall = { fallback.listFacetDefinitions() },
                    )
                }
            runCatching { response.body<List<FacetDefinition>>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "listFacetDefinitions.decode",
                    error = error,
                    fallbackCall = { fallback.listFacetDefinitions() },
                )
            }
        } else {
            fallbackOrThrow(
                operation = "listFacetDefinitions.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listFacetDefinitions() },
            )
        }
    }

    override suspend fun listFacetDefinitions(categoryCode: String): List<FacetDefinition> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetDefinitionsByCategory.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listFacetDefinitions(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/definitions") {
                parameter("categoryCode", normalized)
            }
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetDefinitionsByCategory",
                error = error,
                fallbackCall = { fallback.listFacetDefinitions(normalized) },
            )
        }

        return if (response.status.isSuccess()) {
            runCatching { versionVerifier.verifyResponseVersion(response, operation = "listFacetDefinitionsByCategory") }
                .getOrElse { error ->
                    return fallbackOrThrow(
                        operation = "listFacetDefinitionsByCategory.versionHeader",
                        error = error,
                        fallbackCall = { fallback.listFacetDefinitions(normalized) },
                    )
                }
            runCatching { response.body<List<FacetDefinition>>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "listFacetDefinitionsByCategory.decode",
                    error = error,
                    fallbackCall = { fallback.listFacetDefinitions(normalized) },
                )
            }
        } else {
            fallbackOrThrow(
                operation = "listFacetDefinitionsByCategory.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listFacetDefinitions(normalized) },
            )
        }
    }

    override suspend fun getFacetDefinition(facetKey: String): FacetDefinition? {
        val normalized = facetKey.trim()
        if (normalized.isBlank()) return null

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetDefinition.versionNegotiation",
                error = error,
                fallbackCall = { fallback.getFacetDefinition(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/definitions/$normalized")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetDefinition",
                error = error,
                fallbackCall = { fallback.getFacetDefinition(normalized) },
            )
        }

        runCatching { versionVerifier.verifyResponseVersion(response, operation = "getFacetDefinition") }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetDefinition.versionHeader",
                error = error,
                fallbackCall = { fallback.getFacetDefinition(normalized) },
            )
        }

        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> runCatching { response.body<FacetDefinition>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "getFacetDefinition.decode",
                    error = error,
                    fallbackCall = { fallback.getFacetDefinition(normalized) },
                )
            }
            else -> fallbackOrThrow(
                operation = "getFacetDefinition.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.getFacetDefinition(normalized) },
            )
        }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Facet API call failed: $operation", error)
    }
}

class FacetPresetApiRepository(
    private val backendClient: BackendClient,
    private val fallback: FacetPresetRepository,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
) : FacetPresetRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listFacetPresets(): List<FacetPreset> {
        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetPresets.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listFacetPresets() },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/presets")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetPresets",
                error = error,
                fallbackCall = { fallback.listFacetPresets() },
            )
        }

        return if (response.status.isSuccess()) {
            runCatching { versionVerifier.verifyResponseVersion(response, operation = "listFacetPresets") }
                .getOrElse { error ->
                    return fallbackOrThrow(
                        operation = "listFacetPresets.versionHeader",
                        error = error,
                        fallbackCall = { fallback.listFacetPresets() },
                    )
                }
            runCatching { response.body<List<FacetPreset>>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "listFacetPresets.decode",
                    error = error,
                    fallbackCall = { fallback.listFacetPresets() },
                )
            }
        } else {
            fallbackOrThrow(
                operation = "listFacetPresets.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listFacetPresets() },
            )
        }
    }

    override suspend fun listFacetPresets(categoryCode: String): List<FacetPreset> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetPresetsByCategory.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listFacetPresets(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/presets") {
                parameter("categoryCode", normalized)
            }
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetPresetsByCategory",
                error = error,
                fallbackCall = { fallback.listFacetPresets(normalized) },
            )
        }

        return if (response.status.isSuccess()) {
            runCatching { versionVerifier.verifyResponseVersion(response, operation = "listFacetPresetsByCategory") }
                .getOrElse { error ->
                    return fallbackOrThrow(
                        operation = "listFacetPresetsByCategory.versionHeader",
                        error = error,
                        fallbackCall = { fallback.listFacetPresets(normalized) },
                    )
                }
            runCatching { response.body<List<FacetPreset>>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "listFacetPresetsByCategory.decode",
                    error = error,
                    fallbackCall = { fallback.listFacetPresets(normalized) },
                )
            }
        } else {
            fallbackOrThrow(
                operation = "listFacetPresetsByCategory.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listFacetPresets(normalized) },
            )
        }
    }

    override suspend fun getFacetPreset(presetCode: String): FacetPreset? {
        val normalized = presetCode.trim()
        if (normalized.isBlank()) return null

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetPreset.versionNegotiation",
                error = error,
                fallbackCall = { fallback.getFacetPreset(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/presets/$normalized")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetPreset",
                error = error,
                fallbackCall = { fallback.getFacetPreset(normalized) },
            )
        }

        runCatching { versionVerifier.verifyResponseVersion(response, operation = "getFacetPreset") }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetPreset.versionHeader",
                error = error,
                fallbackCall = { fallback.getFacetPreset(normalized) },
            )
        }

        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> runCatching { response.body<FacetPreset>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "getFacetPreset.decode",
                    error = error,
                    fallbackCall = { fallback.getFacetPreset(normalized) },
                )
            }
            else -> fallbackOrThrow(
                operation = "getFacetPreset.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.getFacetPreset(normalized) },
            )
        }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Facet API call failed: $operation", error)
    }
}

class FacetCollectionApiRepository(
    private val backendClient: BackendClient,
    private val fallback: FacetCollectionRepository,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
) : FacetCollectionRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listFacetCollections(): List<FacetCollection> {
        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetCollections.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listFacetCollections() },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetCollections",
                error = error,
                fallbackCall = { fallback.listFacetCollections() },
            )
        }

        return if (response.status.isSuccess()) {
            runCatching { versionVerifier.verifyResponseVersion(response, operation = "listFacetCollections") }
                .getOrElse { error ->
                    return fallbackOrThrow(
                        operation = "listFacetCollections.versionHeader",
                        error = error,
                        fallbackCall = { fallback.listFacetCollections() },
                    )
                }
            runCatching { response.body<List<FacetCollection>>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "listFacetCollections.decode",
                    error = error,
                    fallbackCall = { fallback.listFacetCollections() },
                )
            }
        } else {
            fallbackOrThrow(
                operation = "listFacetCollections.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listFacetCollections() },
            )
        }
    }

    override suspend fun listFacetCollections(categoryCode: String): List<FacetCollection> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetCollectionsByCategory.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listFacetCollections(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections") {
                parameter("categoryCode", normalized)
            }
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listFacetCollectionsByCategory",
                error = error,
                fallbackCall = { fallback.listFacetCollections(normalized) },
            )
        }

        return if (response.status.isSuccess()) {
            runCatching { versionVerifier.verifyResponseVersion(response, operation = "listFacetCollectionsByCategory") }
                .getOrElse { error ->
                    return fallbackOrThrow(
                        operation = "listFacetCollectionsByCategory.versionHeader",
                        error = error,
                        fallbackCall = { fallback.listFacetCollections(normalized) },
                    )
                }
            runCatching { response.body<List<FacetCollection>>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "listFacetCollectionsByCategory.decode",
                    error = error,
                    fallbackCall = { fallback.listFacetCollections(normalized) },
                )
            }
        } else {
            fallbackOrThrow(
                operation = "listFacetCollectionsByCategory.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listFacetCollections(normalized) },
            )
        }
    }

    override suspend fun getFacetCollection(collectionCode: String): FacetCollection? {
        val normalized = collectionCode.trim()
        if (normalized.isBlank()) return null

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetCollection.versionNegotiation",
                error = error,
                fallbackCall = { fallback.getFacetCollection(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections/$normalized")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetCollection",
                error = error,
                fallbackCall = { fallback.getFacetCollection(normalized) },
            )
        }

        return decodeFacetCollectionResponse(
            response = response,
            operation = "getFacetCollection",
            fallbackCall = { fallback.getFacetCollection(normalized) },
        )
    }

    override suspend fun getFacetCollectionByBrowseCode(browseCode: String): FacetCollection? {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return null

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetCollectionByBrowseCode.versionNegotiation",
                error = error,
                fallbackCall = { fallback.getFacetCollectionByBrowseCode(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/facets/collections/by-browse/$normalized")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getFacetCollectionByBrowseCode",
                error = error,
                fallbackCall = { fallback.getFacetCollectionByBrowseCode(normalized) },
            )
        }

        return decodeFacetCollectionResponse(
            response = response,
            operation = "getFacetCollectionByBrowseCode",
            fallbackCall = { fallback.getFacetCollectionByBrowseCode(normalized) },
        )
    }

    private suspend fun decodeFacetCollectionResponse(
        response: HttpResponse,
        operation: String,
        fallbackCall: suspend () -> FacetCollection?,
    ): FacetCollection? {
        versionVerifier.verifyResponseVersion(response, operation = operation)
        return when {
        response.status == HttpStatusCode.NotFound -> null
        response.status.isSuccess() -> runCatching { response.body<FacetCollection>() }.getOrElse { error ->
            fallbackOrThrow(
                operation = "$operation.decode",
                error = error,
                fallbackCall = fallbackCall,
            )
        }
        else -> fallbackOrThrow(
            operation = "$operation.status=${response.status}",
            error = IllegalStateException("Unexpected status=${response.status}"),
            fallbackCall = fallbackCall,
        )
    }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Facet API call failed: $operation", error)
    }
}
