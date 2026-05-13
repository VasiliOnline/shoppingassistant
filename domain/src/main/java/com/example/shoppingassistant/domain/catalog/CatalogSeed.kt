package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPreset

/**
 * Public facade for catalog seed data.
 * Source of truth is resource files under domain/src/main/resources/taxonomy.
 */
object CatalogSeed {
    val categories: List<Category> by lazy { CatalogSeedLoader.categories }
    val categoryAliases: List<CategoryAlias> by lazy { CatalogSeedLoader.categoryAliases }
    val browseNodes: List<BrowseNode> by lazy { CatalogSeedLoader.browseNodes }
    val aliasEntries: List<AliasEntry> by lazy { CatalogSeedLoader.aliasEntries }
    val googleMappings: List<GoogleTaxonomyMapping> by lazy { CatalogSeedLoader.googleMappings }
    val categoryWriteSpecs: List<CatalogCategoryWriteSpec> by lazy { CatalogSeedLoader.categoryWriteSpecs }
    val valueDictionaries: List<AttributeValueDict> by lazy { CatalogSeedLoader.valueDictionaries }
    val constraints: List<CatalogConstraints> by lazy { CatalogSeedLoader.constraints }
    val facetDefinitions: List<FacetDefinition> by lazy { CatalogSeedLoader.facetDefinitions }
    val facetPresets: List<FacetPreset> by lazy { CatalogSeedLoader.facetPresets }
    val facetCollections: List<FacetCollection> by lazy { CatalogSeedLoader.facetCollections }
    val stage40ImmutableSchema: Stage40ImmutableSchemaDocument by lazy { CatalogSeedLoader.stage40ImmutableSchema }
    val stage40NormalizationContract: Stage40NormalizationContractDocument by lazy { CatalogSeedLoader.stage40NormalizationContract }
    val stage40DedupKeys: Stage40DedupKeysDocument by lazy { CatalogSeedLoader.stage40DedupKeys }
    val stage40TypedConstraints: Stage40TypedConstraintsDocument by lazy { CatalogSeedLoader.stage40TypedConstraints }

    internal val stage21TechGoldenQueries: List<GoldenQuery> by lazy { Stage21TechPackageLoader.goldenQueries }
    internal val stage21TechCoverageGate: Stage21CoverageGate by lazy { Stage21TechPackageLoader.coverageGate }
    internal val stage21TechRoutingRulesYaml: String by lazy { Stage21TechPackageLoader.routingRulesYaml }

    internal val stage21ApplGoldenQueries: List<GoldenQuery> by lazy { Stage21ApplPackageLoader.goldenQueries }
    internal val stage21ApplCoverageGate: Stage21ApplCoverageGate by lazy { Stage21ApplPackageLoader.coverageGate }
    internal val stage21ApplRoutingRulesYaml: String by lazy { Stage21ApplPackageLoader.routingRulesYaml }

    internal val stage21HomeGoldenQueries: List<GoldenQuery> by lazy { Stage21HomePackageLoader.goldenQueries }
    internal val stage21HomeCoverageGate: Stage21HomeCoverageGate by lazy { Stage21HomePackageLoader.coverageGate }
    internal val stage21HomeRoutingRulesYaml: String by lazy { Stage21HomePackageLoader.routingRulesYaml }

    internal val stage21BeautyGoldenQueries: List<GoldenQuery> by lazy { Stage21BeautyPackageLoader.goldenQueries }
    internal val stage21BeautyCoverageGate: Stage21HomeCoverageGate by lazy { Stage21BeautyPackageLoader.coverageGate }
    internal val stage21BeautyRoutingRulesYaml: String by lazy { Stage21BeautyPackageLoader.routingRulesYaml }

    internal val stage21KidsGoldenQueries: List<GoldenQuery> by lazy { Stage21KidsPackageLoader.goldenQueries }
    internal val stage21KidsCoverageGate: Stage21HomeCoverageGate by lazy { Stage21KidsPackageLoader.coverageGate }
    internal val stage21KidsRoutingRulesYaml: String by lazy { Stage21KidsPackageLoader.routingRulesYaml }

    internal val stage21FoodGoldenQueries: List<GoldenQuery> by lazy { Stage21FoodPackageLoader.goldenQueries }
    internal val stage21FoodCoverageGate: Stage21HomeCoverageGate by lazy { Stage21FoodPackageLoader.coverageGate }
    internal val stage21FoodRoutingRulesYaml: String by lazy { Stage21FoodPackageLoader.routingRulesYaml }

    internal val stage21PetsGoldenQueries: List<GoldenQuery> by lazy { Stage21PetsPackageLoader.goldenQueries }
    internal val stage21PetsCoverageGate: Stage21HomeCoverageGate by lazy { Stage21PetsPackageLoader.coverageGate }
    internal val stage21PetsRoutingRulesYaml: String by lazy { Stage21PetsPackageLoader.routingRulesYaml }

    internal val stage21SportGoldenQueries: List<GoldenQuery> by lazy { Stage21SportPackageLoader.goldenQueries }
    internal val stage21SportCoverageGate: Stage21HomeCoverageGate by lazy { Stage21SportPackageLoader.coverageGate }
    internal val stage21SportRoutingRulesYaml: String by lazy { Stage21SportPackageLoader.routingRulesYaml }

    internal val stage21AutoGoldenQueries: List<GoldenQuery> by lazy { Stage21AutoPackageLoader.goldenQueries }
    internal val stage21AutoCoverageGate: Stage21HomeCoverageGate by lazy { Stage21AutoPackageLoader.coverageGate }
    internal val stage21AutoRoutingRulesYaml: String by lazy { Stage21AutoPackageLoader.routingRulesYaml }
}
