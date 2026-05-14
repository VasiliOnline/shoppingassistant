package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryAlias
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import com.example.shoppingassistant.domain.catalog.TaxonomyValidationReport
import com.example.shoppingassistant.domain.catalog.TaxonomyValidator
import java.util.Locale

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
        val normalizedAliasEntries = normalizeAliasEntries(aliasEntries)
        val legacyAliasesForValidation = if (normalizedAliasEntries.isEmpty()) {
            normalizeCategoryAliases(aliases)
        } else {
            emptyList()
        }
        return validator.validate(
            categories = categories,
            aliases = legacyAliasesForValidation,
            mappings = normalizeMappings(mappings),
            browseNodes = normalizeBrowseNodes(browseNodes),
            aliasEntries = normalizedAliasEntries,
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

        private val LEGACY_CATEGORY_REPLACEMENTS = mapOf(
            "TECH.CAMERAS" to "TECH.CAMERAS_DRONES",
            "TECH.LAPTOPS" to "TECH.COMPUTERS",
            "TECH.SMART_HOME" to "TECH.SMART_HOME_SECURITY",
            "TECH.TABLETS_EBOOKS" to "TECH.TABLETS_E_READERS",
            "TECH.TV_VIDEO" to "TECH.TV_HOME_THEATER",
        )

        private fun normalizeCategoryCode(code: String): String =
            LEGACY_CATEGORY_REPLACEMENTS[code.trim().uppercase(Locale.ROOT)] ?: code

        private fun normalizeAliasEntries(entries: List<AliasEntry>): List<AliasEntry> =
            entries
                .map { entry -> entry.copy(targetCode = normalizeCategoryCode(entry.targetCode)) }
                .distinctBy { entry ->
                    listOf(
                        entry.locale.trim().lowercase(),
                        entry.normalizedTerm.trim(),
                        entry.kind.name,
                        entry.targetCode.trim(),
                    ).joinToString("|")
                }

        private fun normalizeCategoryAliases(aliases: List<CategoryAlias>): List<CategoryAlias> =
            aliases
                .map { alias -> alias.copy(categoryCode = normalizeCategoryCode(alias.categoryCode)) }
                .distinctBy { alias ->
                    "${alias.alias.trim().lowercase()}|${alias.categoryCode.trim()}"
                }

        private fun normalizeBrowseNodes(nodes: List<BrowseNode>): List<BrowseNode> =
            nodes.map { node ->
                val targetCategoryCode = node.targetCategoryCode
                if (targetCategoryCode.isNullOrBlank()) {
                    node
                } else {
                    node.copy(targetCategoryCode = normalizeCategoryCode(targetCategoryCode))
                }
            }

        private fun normalizeMappings(mappings: List<GoogleTaxonomyMapping>): List<GoogleTaxonomyMapping> =
            mappings
                .map { mapping -> mapping.copy(canonicalCode = normalizeCategoryCode(mapping.canonicalCode)) }
                .distinctBy { mapping -> mapping.canonicalCode.trim() }
    }
}

