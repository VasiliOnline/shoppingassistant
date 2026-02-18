package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class GoogleTaxonomyMappingType {
    SINGLE,
    MULTI,
    NONE,
}

@Serializable
data class GoogleTaxonomyMapping(
    val canonicalCode: String,
    val mappingType: GoogleTaxonomyMappingType = GoogleTaxonomyMappingType.NONE,
    val googleIds: List<Long> = emptyList(),
    val googlePaths: List<String> = emptyList(),
    val notes: String? = null,
)

interface GoogleTaxonomyMappingRepository {
    suspend fun listMappings(): List<GoogleTaxonomyMapping>
    suspend fun getMapping(categoryCode: String): GoogleTaxonomyMapping?
}

class GetGoogleTaxonomyMappingsTask(
    private val repository: GoogleTaxonomyMappingRepository,
) {
    suspend operator fun invoke(): List<GoogleTaxonomyMapping> = repository.listMappings()
}

class GetGoogleMappingForCategoryTask(
    private val repository: GoogleTaxonomyMappingRepository,
) {
    suspend operator fun invoke(categoryCode: String): GoogleTaxonomyMapping? =
        repository.getMapping(categoryCode)
}
