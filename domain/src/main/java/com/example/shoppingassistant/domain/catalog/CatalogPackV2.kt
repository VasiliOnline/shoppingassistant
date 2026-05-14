package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class CategoryArchetype {
    TYPE_DRIVEN,
    SIZE_DRIVEN,
    COMPATIBILITY_DRIVEN,
    IDENTITY_CRITICAL,
    SPEC_HEAVY,
    CONSUMABLE,
    LIFE_STAGE_DRIVEN,
    SAFETY_REGULATED,
    VISUAL_SIMPLE,
}

@Serializable
enum class CatalogAttributeRole {
    T0_CORE,
    T1_TYPE_CRITICAL,
    T2_ADVANCED,
    T3_SYSTEM_HIDDEN,
}

@Serializable
enum class CatalogAttributeUsageScope {
    USER_VISIBLE_REQUIRED,
    USER_VISIBLE_OPTIONAL,
    PRIMARY_FACET,
    SECONDARY_FACET,
    MATCHING_ONLY,
    AI_EXTRACTION_ONLY,
    ADMIN_REVIEW_ONLY,
    RESERVED,
}

@Serializable
internal data class CatalogPackV2ContractDocument(
    val schemaVersion: String,
    val standardCode: String,
    val sections: List<String>,
    val attributeRoles: List<CatalogAttributeRole>,
    val usageScopes: List<CatalogAttributeUsageScope>,
)

@Serializable
internal data class CategoryArchetypeRegistryDocument(
    val schemaVersion: String,
    val archetypes: List<CategoryArchetypeRule>,
)

@Serializable
internal data class CategoryArchetypeRule(
    val archetype: CategoryArchetype,
    val mandatoryFields: List<String>,
    val requiredAnyAttributeCodes: List<List<String>> = emptyList(),
    val typicalFacetTemplates: List<String>,
    val validators: List<String>,
    val runtimeSignals: List<String>,
    val aiVisionDoNotGuess: List<String>,
    val seedData: List<String>,
    val liveValues: List<String>,
)

@Serializable
internal data class CategoryArchetypeAssignmentDocument(
    val schemaVersion: String,
    val assignments: List<CategoryArchetypeAssignment>,
)

@Serializable
internal data class CategoryArchetypeAssignment(
    val categoryCode: String,
    val archetypes: List<CategoryArchetype>,
    val sharedStandards: List<String> = emptyList(),
    val routeGuardLayer: String? = null,
    val identityMode: String = "NONE",
    val seedAttributeCodes: List<String> = emptyList(),
    val liveCandidateAttributeCodes: List<String> = emptyList(),
)

@Serializable
internal data class FacetTemplateRegistryDocument(
    val schemaVersion: String,
    val templates: List<FacetTemplateDefinition>,
)

@Serializable
internal data class FacetTemplateDefinition(
    val code: String,
    val usageScopes: List<CatalogAttributeUsageScope>,
    val widgetHint: CatalogAttributeWidgetHint,
    val allowedValueTypes: List<Stage22ValueType>,
    val notes: String,
)

@Serializable
internal data class GeneratedAliasRuleRegistryDocument(
    val schemaVersion: String,
    val rules: List<GeneratedAliasRuleDefinition>,
)

@Serializable
internal data class GeneratedAliasRuleDefinition(
    val code: String,
    val sourceFields: List<String>,
    val appliesToArchetypes: List<CategoryArchetype>,
    val generatedAliasKinds: List<String>,
    val manualOnlyKinds: List<String>,
    val negativeGuardKinds: List<String>,
)

@Serializable
internal data class RouteGuardLayerRegistryDocument(
    val schemaVersion: String,
    val layers: List<RouteGuardLayerDefinition>,
)

@Serializable
internal data class RouteGuardLayerDefinition(
    val code: String,
    val appliesToL0: List<String>,
    val negativeGuardTargets: List<String>,
    val requiredGoldenKinds: List<String>,
)

@Serializable
internal data class CatalogPackV2ManifestRegistryDocument(
    val schemaVersion: String,
    val manifests: List<CatalogPackV2Manifest>,
)

@Serializable
internal data class CatalogPackV2Manifest(
    val packId: String,
    val categoryCode: String? = null,
    val categoryCodes: List<String> = emptyList(),
    val packSections: List<String>,
    val archetypes: List<CategoryArchetype> = emptyList(),
    val basePath: String,
    val requiredFiles: List<String>,
    val userSurfaceFile: String? = null,
    val routeGuardLayer: String? = null,
)

internal object CatalogPackV2RegistryLoader {
    private const val CONTRACT_FILE = "catalog_pack_v2_contract.json"
    private const val ARCHETYPES_FILE = "category_archetypes.json"
    private const val ASSIGNMENTS_FILE = "category_archetype_assignments.json"
    private const val FACET_TEMPLATES_FILE = "facet_templates.json"
    private const val ALIAS_RULES_FILE = "generated_alias_rules.json"
    private const val ROUTE_GUARD_LAYERS_FILE = "route_guard_layers.json"
    private const val PACK_MANIFESTS_FILE = "catalog_pack_v2_manifests.json"

    val contract: CatalogPackV2ContractDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(CONTRACT_FILE),
            deserializer = CatalogPackV2ContractDocument.serializer(),
        )
    }

    val archetypeRegistry: CategoryArchetypeRegistryDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(ARCHETYPES_FILE),
            deserializer = CategoryArchetypeRegistryDocument.serializer(),
        )
    }

    val archetypeAssignments: CategoryArchetypeAssignmentDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(ASSIGNMENTS_FILE),
            deserializer = CategoryArchetypeAssignmentDocument.serializer(),
        )
    }

    val facetTemplates: FacetTemplateRegistryDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(FACET_TEMPLATES_FILE),
            deserializer = FacetTemplateRegistryDocument.serializer(),
        )
    }

    val generatedAliasRules: GeneratedAliasRuleRegistryDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(ALIAS_RULES_FILE),
            deserializer = GeneratedAliasRuleRegistryDocument.serializer(),
        )
    }

    val routeGuardLayers: RouteGuardLayerRegistryDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(ROUTE_GUARD_LAYERS_FILE),
            deserializer = RouteGuardLayerRegistryDocument.serializer(),
        )
    }

    val packManifests: CatalogPackV2ManifestRegistryDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = registryPath(PACK_MANIFESTS_FILE),
            deserializer = CatalogPackV2ManifestRegistryDocument.serializer(),
        )
    }

    private val assignmentsByCategory: Map<String, CategoryArchetypeAssignment> by lazy {
        archetypeAssignments.assignments.associateBy { assignment -> assignment.categoryCode.trim() }
    }

    fun assignmentFor(categoryCode: String): CategoryArchetypeAssignment? =
        assignmentsByCategory[categoryCode.trim()]

    fun archetypesFor(categoryCode: String): List<CategoryArchetype> =
        assignmentFor(categoryCode)?.archetypes.orEmpty()

    private fun registryPath(fileName: String): String =
        "${CatalogContractPaths.stage22RegistryBase}/$fileName"
}
