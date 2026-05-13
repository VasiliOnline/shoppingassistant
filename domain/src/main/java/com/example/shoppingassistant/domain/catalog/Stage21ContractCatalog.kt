package com.example.shoppingassistant.domain.catalog

import java.util.Locale

internal object Stage21ContractCatalog {
    private val requiredFileTemplates = listOf(
        "browse_nodes.%s.tsv",
        "aliases.%s.tsv",
        "queries_golden.%s.tsv",
        "coverage.%s.json",
        "routing_rules.%s.yaml",
    )

    fun requiredResourcePaths(stage21BasePath: String = CatalogContractPaths.stage21Base): List<String> =
        CatalogL0Registry.requiredPackageCodes.flatMap { packageCode ->
            val normalizedCode = packageCode.trim().uppercase(Locale.ROOT)
            val lowerCode = normalizedCode.lowercase(Locale.ROOT)
            requiredFileTemplates.map { template ->
                val fileName = String.format(Locale.ROOT, template, lowerCode)
                "$stage21BasePath/$normalizedCode/$fileName"
            }
        }
}
