package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.data.AttributeService
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot

class CatalogLiveValuesRepositoryImpl(
    private val attributeService: AttributeService,
) : CatalogLiveValuesRepository {

    override suspend fun getLiveValues(
        request: CatalogLiveValuesRequest,
    ): CatalogLiveValuesSnapshot {
        val brand = request.brand?.trim()?.takeIf { it.isNotEmpty() }
        val model = request.model?.trim()?.takeIf { it.isNotEmpty() }
        val requestedCodes = request.attributeCodes
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .distinct()
            .toSet()

        val valuesByAttributeCode = if (brand == null && model == null) {
            emptyMap()
        } else {
            attributeService
                .defsFor(brand, model)
                .asSequence()
                .filter { raw -> requestedCodes.isEmpty() || raw.key.trim().lowercase() in requestedCodes }
                .mapNotNull { raw ->
                    val key = raw.key.trim()
                    val values = raw.values
                        .asSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .toList()
                    if (key.isEmpty() || values.isEmpty()) {
                        null
                    } else {
                        key to values
                    }
                }
                .toMap(LinkedHashMap())
        }

        val brandOptions = when {
            brand != null -> listOf(brand)
            else -> attributeService.brands()
        }.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .toList()

        val modelOptions = when {
            model != null -> listOf(model)
            else -> attributeService.models(brand)
        }.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .toList()

        return CatalogLiveValuesSnapshot(
            valuesByAttributeCode = valuesByAttributeCode,
            knownValuesByAttributeCode = valuesByAttributeCode,
            brandOptions = brandOptions,
            modelOptions = modelOptions,
        )
    }
}
