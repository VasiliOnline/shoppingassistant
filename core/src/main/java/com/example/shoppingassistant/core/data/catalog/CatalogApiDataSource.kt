package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.Category
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.util.Locale

class CatalogApiDataSource(
    private val backendClient: BackendClient,
    private val fallback: CatalogDataSource,
    private val allowSeedFallback: Boolean = false,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
) : CatalogDataSource {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listCategories(): List<Category> = withFallback(
        operation = "listCategories",
        fallbackCall = { fallback.listCategories() },
    ) {
        versionVerifier.ensureCompatible(requireDataVersionParity = allowSeedFallback)
        val response = backendClient.client.get("$baseUrl/api/catalog/categories")
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Catalog API status=${response.status}")
        }
        versionVerifier.verifyResponseVersion(
            response,
            operation = "listCategories",
            requireDataVersionParity = allowSeedFallback,
        )
        mergeCategoriesWithSeed(
            remoteCategories = response.body(),
            seedCategories = fallback.listCategories(),
        )
    }

    override suspend fun getEffectiveSpec(
        code: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val normalizedCode = normalizeCode(code)
        if (normalizedCode.isEmpty()) return null

        return withFallbackNullable(
            operation = "getEffectiveSpec",
            fallbackCall = {
                fallback.getEffectiveSpec(
                    code = normalizedCode,
                    brand = brand,
                    model = model,
                )
            },
        ) {
            versionVerifier.ensureCompatible(requireDataVersionParity = allowSeedFallback)
            val response = backendClient.client.get("$baseUrl/api/catalog/effective-spec/$normalizedCode") {
                brand?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedBrand ->
                    parameter("brand", normalizedBrand)
                }
                model?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedModel ->
                    parameter("model", normalizedModel)
                }
            }
            versionVerifier.verifyResponseVersion(
                response,
                operation = "getEffectiveSpec",
                requireDataVersionParity = allowSeedFallback,
            )
            when {
                response.status == HttpStatusCode.NotFound -> fallback.getEffectiveSpec(
                    code = normalizedCode,
                    brand = brand,
                    model = model,
                )
                response.status.isSuccess() -> response.body<CatalogCategoryEffectiveSpec>()
                else -> throw IllegalStateException("Catalog API status=${response.status}")
            }
        }
    }

    private suspend fun <T> withFallback(
        operation: String,
        fallbackCall: suspend () -> T,
        call: suspend () -> T,
    ): T {
        return runCatching { call() }.getOrElse { error ->
            if (allowSeedFallback) {
                fallbackCall()
            } else {
                throw IllegalStateException("Catalog API call failed: $operation", error)
            }
        }
    }

    private suspend fun <T> withFallbackNullable(
        operation: String,
        fallbackCall: suspend () -> T?,
        call: suspend () -> T?,
    ): T? {
        return runCatching { call() }.getOrElse { error ->
            if (allowSeedFallback) {
                fallbackCall()
            } else {
                throw IllegalStateException("Catalog API call failed: $operation", error)
            }
        }
    }

    private fun normalizeCode(code: String?): String =
        code?.trim()?.uppercase(Locale.ROOT).orEmpty()
}

internal fun mergeCategoriesWithSeed(
    remoteCategories: List<Category>,
    seedCategories: List<Category>,
): List<Category> {
    if (remoteCategories.isEmpty()) return seedCategories
    if (seedCategories.isEmpty()) return remoteCategories

    val merged = LinkedHashMap<String, Category>()
    remoteCategories.forEach { category ->
        val key = category.code.trim().uppercase(Locale.ROOT)
        if (key.isNotEmpty()) merged[key] = category
    }
    seedCategories.forEach { category ->
        val key = category.code.trim().uppercase(Locale.ROOT)
        if (key.isNotEmpty()) merged.putIfAbsent(key, category)
    }
    return merged.values.toList()
}
