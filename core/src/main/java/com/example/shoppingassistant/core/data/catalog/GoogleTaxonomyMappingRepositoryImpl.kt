package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository

class GoogleTaxonomyMappingRepositoryImpl(
    private val seeded: List<GoogleTaxonomyMapping> = CatalogSeed.googleMappings,
) : GoogleTaxonomyMappingRepository {
    private val byCode: Map<String, GoogleTaxonomyMapping> = seeded.associateBy { it.canonicalCode }

    override suspend fun listMappings(): List<GoogleTaxonomyMapping> = seeded

    override suspend fun getMapping(categoryCode: String): GoogleTaxonomyMapping? =
        byCode[categoryCode]
}
