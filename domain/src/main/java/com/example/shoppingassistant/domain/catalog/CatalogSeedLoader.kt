package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import kotlinx.serialization.builtins.ListSerializer

internal object CatalogSeedLoader {
    private const val STAGE20_BASE = "taxonomy/stage2/2.0"
    private const val STAGE11_BASE = "taxonomy/stage1/1.1"
    private const val STAGE12_BASE = "taxonomy/stage1/1.2"

    /**
     * Stage 2.0 data contract may live under taxonomy/stage2/2.0,
     * but current production seed is still hosted under stage1/1.1.
     * We prefer stage2 path when present and keep stage1 as backward-compatible fallback.
     */
    private fun stage20Or11(fileName: String): String {
        val stage20Path = "$STAGE20_BASE/$fileName"
        return if (CatalogSeedResourceReader.resourceExists(stage20Path)) {
            stage20Path
        } else {
            "$STAGE11_BASE/$fileName"
        }
    }

    val categories: List<Category> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = stage20Or11("categories.json"),
            deserializer = ListSerializer(Category.serializer()),
        )
    }

    val categoryAliases: List<CategoryAlias> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = stage20Or11("category_aliases.json"),
            deserializer = ListSerializer(CategoryAlias.serializer()),
        )
    }

    val browseNodes: List<BrowseNode> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = stage20Or11("browse_nodes.json"),
            deserializer = ListSerializer(BrowseNode.serializer()),
        )
    }

    val aliasEntries: List<AliasEntry> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = stage20Or11("alias_entries.json"),
            deserializer = ListSerializer(AliasEntry.serializer()),
        )
    }

    val googleMappings: List<GoogleTaxonomyMapping> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "$STAGE12_BASE/google_taxonomy_mappings.json",
            deserializer = ListSerializer(GoogleTaxonomyMapping.serializer()),
        )
    }

    private val stage22Packages: List<Stage22PackageData> by lazy {
        GenericStage22PackageLoader.loadAll()
    }

    private val validatedStage22Packages: List<Stage22PackageData> by lazy {
        Stage22SeedValidator().validateOrThrow(
            categories = categories,
            packages = stage22Packages,
        )
        stage22Packages
    }

    init {
        // Fail fast during seed initialization so invalid stage 2.2 data never reaches runtime.
        validatedStage22Packages
    }

    val profiles: List<CategoryProfile> by lazy {
        val profileByCode = LinkedHashMap<String, CategoryProfile>()
        validatedStage22Packages
            .flatMap { it.profiles }
            .forEach { profile ->
                profileByCode.putIfAbsent(profile.category.code, profile)
            }

        categories.map { category ->
            profileByCode[category.code] ?: CategoryProfile(
                category = category,
                attributes = emptyList(),
                categoryAttributes = emptyList(),
                valueDictionaries = emptyList(),
                requiredIfRules = emptyList(),
            )
        }
    }

    val constraints: List<CatalogConstraints> by lazy {
        val ordered = buildList {
            addAll(GenericStage22PackageLoader.loadGlobalConstraints())
            addAll(validatedStage22Packages.flatMap { it.constraints })
        }
        dedupeConstraints(ordered)
    }

    private fun dedupeConstraints(constraints: List<CatalogConstraints>): List<CatalogConstraints> {
        val deduped = LinkedHashMap<String, CatalogConstraints>()
        constraints.forEach { constraint ->
            val key = buildString {
                append(constraint.scope.name)
                append("|")
                append(constraint.categoryCode.orEmpty())
                append("|")
                append(constraint.brand.orEmpty())
                append("|")
                append(constraint.model.orEmpty())
                append("|")
                append(constraint.attributeConstraints.joinToString { it.attributeCode })
                append("|")
                append(constraint.compatibilityRules.size)
            }
            deduped.putIfAbsent(key, constraint)
        }
        return deduped.values.toList()
    }
}
