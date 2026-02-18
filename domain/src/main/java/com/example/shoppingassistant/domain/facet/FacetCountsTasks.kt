package com.example.shoppingassistant.domain.facet

import kotlinx.serialization.Serializable

@Serializable
enum class FacetCountMode {
    REPLACE,
    ADD,
}

@Serializable
data class FacetCountsQuery(
    val categoryCode: String,
    val brand: String? = null,
    val model: String? = null,
    /** Selected values per attribute; OR within key, AND across keys. */
    val selectedFilters: Map<String, List<String>> = emptyMap(),
    val targetFacetKey: String,
    val mode: FacetCountMode = FacetCountMode.REPLACE,
    val excludeTargetFacet: Boolean = true,
)

@Serializable
data class FacetValueCount(
    val value: String,
    val count: Int,
)

interface FacetCountsRepository {
    suspend fun getFacetCounts(query: FacetCountsQuery): List<FacetValueCount>
}

class GetFacetCountsTask(
    private val repository: FacetCountsRepository,
) {
    suspend operator fun invoke(query: FacetCountsQuery): List<FacetValueCount> =
        repository.getFacetCounts(query)
}
