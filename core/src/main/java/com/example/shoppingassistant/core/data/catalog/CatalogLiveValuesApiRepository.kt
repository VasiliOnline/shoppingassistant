package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

class CatalogLiveValuesApiRepository(
    private val backendClient: BackendClient,
    private val versionVerifier: CatalogRuntimeVersionVerifier = NoopCatalogRuntimeVersionVerifier,
) : CatalogLiveValuesRepository {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun getLiveValues(
        request: CatalogLiveValuesRequest,
    ): CatalogLiveValuesSnapshot {
        versionVerifier.ensureCompatible()
        val response = backendClient.client.get("$baseUrl/api/catalog/live-values") {
            request.categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { categoryCode ->
                parameter("categoryCode", categoryCode)
            }
            request.brand?.trim()?.takeIf { it.isNotEmpty() }?.let { brand ->
                parameter("brand", brand)
            }
            request.model?.trim()?.takeIf { it.isNotEmpty() }?.let { model ->
                parameter("model", model)
            }
            request.localeTag?.trim()?.takeIf { it.isNotEmpty() }?.let { localeTag ->
                parameter("locale", localeTag)
            }
            request.attributeCodes
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { attributeCode ->
                    parameter("attributeCode", attributeCode)
                }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Catalog live-values API status=${response.status}")
        }
        versionVerifier.verifyResponseVersion(response, operation = "getLiveValues")
        return response.body()
    }
}
