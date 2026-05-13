package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CategoryAlias
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class CategoryAliasApiRepository(
    private val backendClient: BackendClient,
    private val fallback: CategoryAliasRepository,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
    private val baseUrl: String = BackendConfig.BASE_URL,
) : CategoryAliasRepository {

    override suspend fun listAliases(): List<CategoryAlias> {
        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listCategoryAliases.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listAliases() },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/aliases")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listCategoryAliases",
                error = error,
                fallbackCall = { fallback.listAliases() },
            )
        }

        if (!response.status.isSuccess()) {
            return fallbackOrThrow(
                operation = "listCategoryAliases.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listAliases() },
            )
        }
        runCatching { versionVerifier.verifyResponseVersion(response, operation = "listCategoryAliases") }
            .getOrElse { error ->
                return fallbackOrThrow(
                    operation = "listCategoryAliases.versionHeader",
                    error = error,
                    fallbackCall = { fallback.listAliases() },
                )
            }
        val remoteRows = runCatching { response.body<List<CategoryAlias>>() }.getOrElse { error ->
            fallbackOrThrow(
                operation = "listCategoryAliases.decode",
                error = error,
                fallbackCall = { fallback.listAliases() },
            )
        }
        return preferFallbackWhenRemoteEmpty(remoteRows) { fallback.listAliases() }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Catalog API call failed: $operation", error)
    }
}

class BrowseNodeApiRepository(
    private val backendClient: BackendClient,
    private val fallback: BrowseNodeRepository,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
    private val baseUrl: String = BackendConfig.BASE_URL,
) : BrowseNodeRepository {

    override suspend fun listBrowseNodes(): List<BrowseNode> {
        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listBrowseNodes.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listBrowseNodes() },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/browse-nodes")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listBrowseNodes",
                error = error,
                fallbackCall = { fallback.listBrowseNodes() },
            )
        }

        if (!response.status.isSuccess()) {
            return fallbackOrThrow(
                operation = "listBrowseNodes.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listBrowseNodes() },
            )
        }
        runCatching { versionVerifier.verifyResponseVersion(response, operation = "listBrowseNodes") }
            .getOrElse { error ->
                return fallbackOrThrow(
                    operation = "listBrowseNodes.versionHeader",
                    error = error,
                    fallbackCall = { fallback.listBrowseNodes() },
                )
            }
        val remoteRows = runCatching { response.body<List<BrowseNode>>() }.getOrElse { error ->
            fallbackOrThrow(
                operation = "listBrowseNodes.decode",
                error = error,
                fallbackCall = { fallback.listBrowseNodes() },
            )
        }
        return preferFallbackWhenRemoteEmpty(remoteRows) { fallback.listBrowseNodes() }
    }

    override suspend fun getBrowseNode(browseCode: String): BrowseNode? {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return null

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getBrowseNode.versionNegotiation",
                error = error,
                fallbackCall = { fallback.getBrowseNode(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/browse-nodes/$normalized")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getBrowseNode",
                error = error,
                fallbackCall = { fallback.getBrowseNode(normalized) },
            )
        }

        runCatching { versionVerifier.verifyResponseVersion(response, operation = "getBrowseNode") }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getBrowseNode.versionHeader",
                error = error,
                fallbackCall = { fallback.getBrowseNode(normalized) },
            )
        }

        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> runCatching { response.body<BrowseNode>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "getBrowseNode.decode",
                    error = error,
                    fallbackCall = { fallback.getBrowseNode(normalized) },
                )
            }
            else -> fallbackOrThrow(
                operation = "getBrowseNode.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.getBrowseNode(normalized) },
            )
        }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Catalog API call failed: $operation", error)
    }
}

class AliasEntryApiRepository(
    private val backendClient: BackendClient,
    private val fallback: AliasEntryRepository,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
    private val baseUrl: String = BackendConfig.BASE_URL,
) : AliasEntryRepository {

    override suspend fun listAliasEntries(locale: String?): List<AliasEntry> {
        val normalizedLocale = locale?.trim()?.takeIf { it.isNotEmpty() }

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listAliasEntries.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listAliasEntries(normalizedLocale) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/alias-entries") {
                normalizedLocale?.let { value -> parameter("locale", value) }
            }
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listAliasEntries",
                error = error,
                fallbackCall = { fallback.listAliasEntries(normalizedLocale) },
            )
        }

        if (!response.status.isSuccess()) {
            return fallbackOrThrow(
                operation = "listAliasEntries.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listAliasEntries(normalizedLocale) },
            )
        }
        runCatching { versionVerifier.verifyResponseVersion(response, operation = "listAliasEntries") }
            .getOrElse { error ->
                return fallbackOrThrow(
                    operation = "listAliasEntries.versionHeader",
                    error = error,
                    fallbackCall = { fallback.listAliasEntries(normalizedLocale) },
                )
            }
        val remoteRows = runCatching { response.body<List<AliasEntry>>() }.getOrElse { error ->
            fallbackOrThrow(
                operation = "listAliasEntries.decode",
                error = error,
                fallbackCall = { fallback.listAliasEntries(normalizedLocale) },
            )
        }
        return preferFallbackWhenRemoteEmpty(remoteRows) { fallback.listAliasEntries(normalizedLocale) }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Catalog API call failed: $operation", error)
    }
}

class GoogleTaxonomyMappingApiRepository(
    private val backendClient: BackendClient,
    private val fallback: GoogleTaxonomyMappingRepository,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
    private val baseUrl: String = BackendConfig.BASE_URL,
) : GoogleTaxonomyMappingRepository {

    override suspend fun listMappings(): List<GoogleTaxonomyMapping> {
        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listGoogleMappings.versionNegotiation",
                error = error,
                fallbackCall = { fallback.listMappings() },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/google-mappings")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "listGoogleMappings",
                error = error,
                fallbackCall = { fallback.listMappings() },
            )
        }

        if (!response.status.isSuccess()) {
            return fallbackOrThrow(
                operation = "listGoogleMappings.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.listMappings() },
            )
        }
        runCatching { versionVerifier.verifyResponseVersion(response, operation = "listGoogleMappings") }
            .getOrElse { error ->
                return fallbackOrThrow(
                    operation = "listGoogleMappings.versionHeader",
                    error = error,
                    fallbackCall = { fallback.listMappings() },
                )
            }
        val remoteRows = runCatching { response.body<List<GoogleTaxonomyMapping>>() }.getOrElse { error ->
            fallbackOrThrow(
                operation = "listGoogleMappings.decode",
                error = error,
                fallbackCall = { fallback.listMappings() },
            )
        }
        return preferFallbackWhenRemoteEmpty(remoteRows) { fallback.listMappings() }
    }

    override suspend fun getMapping(categoryCode: String): GoogleTaxonomyMapping? {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return null

        runCatching { versionVerifier.ensureCompatible() }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getGoogleMapping.versionNegotiation",
                error = error,
                fallbackCall = { fallback.getMapping(normalized) },
            )
        }

        val response = runCatching {
            backendClient.client.get("$baseUrl/api/catalog/google-mappings/$normalized")
        }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getGoogleMapping",
                error = error,
                fallbackCall = { fallback.getMapping(normalized) },
            )
        }

        runCatching { versionVerifier.verifyResponseVersion(response, operation = "getGoogleMapping") }.getOrElse { error ->
            return fallbackOrThrow(
                operation = "getGoogleMapping.versionHeader",
                error = error,
                fallbackCall = { fallback.getMapping(normalized) },
            )
        }

        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> runCatching { response.body<GoogleTaxonomyMapping>() }.getOrElse { error ->
                fallbackOrThrow(
                    operation = "getGoogleMapping.decode",
                    error = error,
                    fallbackCall = { fallback.getMapping(normalized) },
                )
            }
            else -> fallbackOrThrow(
                operation = "getGoogleMapping.status=${response.status}",
                error = IllegalStateException("Unexpected status=${response.status}"),
                fallbackCall = { fallback.getMapping(normalized) },
            )
        }
    }

    private suspend fun <T> fallbackOrThrow(
        operation: String,
        error: Throwable,
        fallbackCall: suspend () -> T,
    ): T {
        if (allowSeedFallback) return fallbackCall()
        throw IllegalStateException("Catalog API call failed: $operation", error)
    }
}

private suspend fun <T> preferFallbackWhenRemoteEmpty(
    remoteRows: List<T>,
    fallbackCall: suspend () -> List<T>,
): List<T> {
    if (remoteRows.isNotEmpty()) return remoteRows
    val fallbackRows = fallbackCall()
    return if (fallbackRows.isNotEmpty()) fallbackRows else remoteRows
}
