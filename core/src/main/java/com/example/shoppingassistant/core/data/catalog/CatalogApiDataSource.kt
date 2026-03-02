package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CatalogApiDataSource(
    private val backendClient: BackendClient,
    private val fallback: CatalogDataSource,
    private val allowSeedFallback: Boolean = false,
) : CatalogDataSource {

    private val baseUrl get() = BackendConfig.BASE_URL
    private val localOverrides = ConcurrentHashMap<String, CategoryProfile>()

    override suspend fun listCategories(): List<Category> = withFallback(
        operation = "listCategories",
        fallbackCall = { fallback.listCategories() },
    ) {
        val response = backendClient.client.get("$baseUrl/api/catalog/categories")
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Catalog API status=${response.status}")
        }
        val remoteCategories = response.body<List<Category>>()
        mergeCategories(remoteCategories, localOverrides.values.map { it.category })
    }

    override suspend fun listProfiles(): List<CategoryProfile> = withFallback(
        operation = "listProfiles",
        fallbackCall = { fallback.listProfiles() },
    ) {
        val categories = listCategories()
        val profiles = categories.mapNotNull { category ->
            getProfile(category.code)
        }
        if (profiles.isNotEmpty()) {
            profiles
        } else {
            localOverrides.values.toList()
        }
    }

    override suspend fun getProfile(code: String): CategoryProfile? {
        val normalizedCode = normalizeCode(code)
        if (normalizedCode.isEmpty()) return null
        localOverrides[normalizedCode]?.let { return it }

        return withFallbackNullable(
            operation = "getProfile",
            fallbackCall = { fallback.getProfile(normalizedCode) },
        ) {
            val response = backendClient.client.get("$baseUrl/api/catalog/profiles/$normalizedCode")
            when {
                response.status == HttpStatusCode.NotFound -> null
                response.status.isSuccess() -> response.body<CategoryProfile>()
                else -> throw IllegalStateException("Catalog API status=${response.status}")
            }
        }
    }

    override suspend fun getAttributeValueDict(attributeCode: String): AttributeValueDict? {
        val normalizedCode = normalizeAttributeCode(attributeCode)
        if (normalizedCode.isEmpty()) return null

        return withFallbackNullable(
            operation = "getAttributeValueDict",
            fallbackCall = { fallback.getAttributeValueDict(normalizedCode) },
        ) {
            val response = backendClient.client.get("$baseUrl/api/catalog/dictionaries/$normalizedCode")
            when {
                response.status == HttpStatusCode.NotFound -> null
                response.status.isSuccess() -> response.body<AttributeValueDict>()
                else -> throw IllegalStateException("Catalog API status=${response.status}")
            }
        }
    }

    override suspend fun listConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        val normalizedCategoryCode = normalizeCode(categoryCode)
        if (normalizedCategoryCode.isEmpty()) return emptyList()

        return withFallback(
            operation = "listConstraints",
            fallbackCall = {
                fallback.listConstraints(
                    categoryCode = normalizedCategoryCode,
                    brand = brand,
                    model = model,
                )
            },
        ) {
            val response = backendClient.client.get("$baseUrl/api/catalog/constraints") {
                parameter("categoryCode", normalizedCategoryCode)
                brand?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedBrand ->
                    parameter("brand", normalizedBrand)
                }
                model?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedModel ->
                    parameter("model", normalizedModel)
                }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Catalog API status=${response.status}")
            }
            response.body<List<CatalogConstraints>>()
        }
    }

    override suspend fun saveProfile(profile: CategoryProfile) {
        val code = normalizeCode(profile.category.code)
        if (code.isEmpty()) return
        localOverrides[code] = profile
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

    private fun mergeCategories(
        remote: List<Category>,
        overrides: List<Category>,
    ): List<Category> {
        val byCode = LinkedHashMap<String, Category>()
        remote.forEach { category ->
            val code = normalizeCode(category.code)
            if (code.isNotEmpty()) {
                byCode[code] = category
            }
        }
        overrides.forEach { category ->
            val code = normalizeCode(category.code)
            if (code.isNotEmpty()) {
                byCode[code] = category
            }
        }
        return byCode.values.toList()
    }

    private fun normalizeCode(code: String?): String =
        code?.trim()?.uppercase(Locale.ROOT).orEmpty()

    private fun normalizeAttributeCode(code: String?): String =
        code?.trim()?.lowercase(Locale.ROOT).orEmpty()
}
