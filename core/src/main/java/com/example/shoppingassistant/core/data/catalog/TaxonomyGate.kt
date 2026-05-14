package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import com.example.shoppingassistant.domain.catalog.TaxonomyValidationReport
import com.example.shoppingassistant.domain.catalog.TaxonomyValidator

class TaxonomyGate(
    private val catalogRepository: CatalogTaxonomyRepository,
    private val categoryAliasRepository: CategoryAliasRepository,
    private val browseNodeRepository: BrowseNodeRepository,
    private val aliasEntryRepository: AliasEntryRepository,
    private val googleTaxonomyMappingRepository: GoogleTaxonomyMappingRepository,
    private val validator: TaxonomyValidator = TaxonomyValidator(),
) {
    suspend fun validate(maxIssues: Int = DEFAULT_MAX_ISSUES): TaxonomyValidationReport {
        val categories = catalogRepository.listCategories()
        val aliases = categoryAliasRepository.listAliases()
        val browseNodes = browseNodeRepository.listBrowseNodes()
        val aliasEntries = aliasEntryRepository.listAliasEntries()
        val mappings = googleTaxonomyMappingRepository.listMappings()
        val legacyAliasesForValidation = if (aliasEntries.isEmpty()) aliases else emptyList()
        return validator.validate(
            categories = categories,
            aliases = legacyAliasesForValidation,
            mappings = mappings,
            browseNodes = browseNodes,
            aliasEntries = aliasEntries,
        )
    }

    suspend fun validateOrThrow(maxIssues: Int = DEFAULT_MAX_ISSUES) {
        val report = validate(maxIssues = maxIssues)
        if (!report.isValid) {
            throw IllegalStateException(report.summary(maxIssues))
        }
    }

    private companion object {
        private const val DEFAULT_MAX_ISSUES = 20
    }
}

