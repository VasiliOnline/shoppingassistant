package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints

internal class CatalogPackV2SeedValidator {
    fun validate(
        categories: List<Category>,
        registry: Stage22RegistrySnapshot,
        packages: List<Stage22PackageData>,
        globalConstraints: List<CatalogConstraints>,
    ): List<Stage22SeedValidationIssue> {
        val issues = mutableListOf<Stage22SeedValidationIssue>()
        val contract = CatalogPackV2RegistryLoader.contract
        val archetypeRegistry = CatalogPackV2RegistryLoader.archetypeRegistry
        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments
        val facetTemplates = CatalogPackV2RegistryLoader.facetTemplates
        val aliasRules = CatalogPackV2RegistryLoader.generatedAliasRules
        val routeGuardLayers = CatalogPackV2RegistryLoader.routeGuardLayers
        val packManifests = CatalogPackV2RegistryLoader.packManifests

        validateContract(contract, issues)
        validateFacetTemplates(facetTemplates, issues)
        validateArchetypeRegistry(archetypeRegistry, facetTemplates, issues)
        validateAliasRules(aliasRules, issues)
        validateRouteGuardLayers(routeGuardLayers, issues)
        validateAssignments(categories, assignments, archetypeRegistry, routeGuardLayers, issues)
        validatePackManifests(packManifests, contract, routeGuardLayers, assignments, categories, issues)
        validateEffectiveSpecArchetypeGates(
            categories = categories,
            registry = registry,
            packages = packages,
            globalConstraints = globalConstraints,
            archetypeRegistry = archetypeRegistry,
            assignments = assignments,
            issues = issues,
        )

        return issues
    }

    private fun validateContract(
        contract: CatalogPackV2ContractDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        if (contract.standardCode != "catalog_pack_v2") {
            issue(issues, "PACK_V2_CONTRACT_STANDARD_INVALID", "catalog_pack_v2_contract.standardCode must be 'catalog_pack_v2'.")
        }
        val requiredSections = setOf(
            "shared_standard",
            "category_schema_pack",
            "child_override_pack",
            "route_guard_pack",
            "identity_pack",
            "compatibility_pack",
        )
        val sections = contract.sections.map { it.trim() }.toSet()
        val missingSections = requiredSections - sections
        if (missingSections.isNotEmpty()) {
            issue(
                issues,
                "PACK_V2_CONTRACT_SECTIONS_MISSING",
                "catalog_pack_v2_contract misses sections: ${missingSections.sorted().joinToString(", ")}.",
            )
        }

        val missingRoles = CatalogAttributeRole.entries.toSet() - contract.attributeRoles.toSet()
        if (missingRoles.isNotEmpty()) {
            issue(issues, "PACK_V2_ATTRIBUTE_ROLES_MISSING", "Missing attribute roles: ${missingRoles.sorted().joinToString(", ")}.")
        }
        val missingScopes = CatalogAttributeUsageScope.entries.toSet() - contract.usageScopes.toSet()
        if (missingScopes.isNotEmpty()) {
            issue(issues, "PACK_V2_USAGE_SCOPES_MISSING", "Missing usage scopes: ${missingScopes.sorted().joinToString(", ")}.")
        }
    }

    private fun validateFacetTemplates(
        facetTemplates: FacetTemplateRegistryDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val required = setOf(
            "enum_primary",
            "enum_secondary",
            "numeric_range",
            "size_selector",
            "brand_optional",
            "condition_price_base",
            "compatibility_widget",
            "spec_range",
            "ingredient/allergen_warning",
            "life_stage_selector",
        )
        val seen = facetTemplates.templates.map { it.code.trim() }
        val duplicate = seen.groupBy { it }.filterValues { it.size > 1 }.keys
        duplicate.forEach { code ->
            issue(issues, "PACK_V2_FACET_TEMPLATE_DUPLICATE", "Duplicate facet template '$code'.")
        }
        val missing = required - seen.toSet()
        if (missing.isNotEmpty()) {
            issue(issues, "PACK_V2_FACET_TEMPLATES_MISSING", "Missing facet templates: ${missing.sorted().joinToString(", ")}.")
        }
        facetTemplates.templates.forEach { template ->
            if (template.usageScopes.isEmpty()) {
                issue(issues, "PACK_V2_FACET_TEMPLATE_SCOPES_EMPTY", "Facet template '${template.code}' must define usage scopes.")
            }
            if (template.allowedValueTypes.isEmpty()) {
                issue(issues, "PACK_V2_FACET_TEMPLATE_VALUE_TYPES_EMPTY", "Facet template '${template.code}' must define allowed value types.")
            }
        }
    }

    private fun validateArchetypeRegistry(
        archetypeRegistry: CategoryArchetypeRegistryDocument,
        facetTemplates: FacetTemplateRegistryDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val knownFacetTemplates = facetTemplates.templates.map { it.code }.toSet()
        val rulesByArchetype = archetypeRegistry.archetypes.groupBy { it.archetype }
        val missing = CategoryArchetype.entries.toSet() - rulesByArchetype.keys
        if (missing.isNotEmpty()) {
            issue(issues, "PACK_V2_ARCHETYPES_MISSING", "Missing archetype rules: ${missing.sorted().joinToString(", ")}.")
        }
        rulesByArchetype.filterValues { it.size > 1 }.keys.forEach { archetype ->
            issue(issues, "PACK_V2_ARCHETYPE_DUPLICATE", "Duplicate rule for archetype '$archetype'.")
        }
        archetypeRegistry.archetypes.forEach { rule ->
            if (rule.mandatoryFields.isEmpty()) {
                issue(issues, "PACK_V2_ARCHETYPE_MANDATORY_FIELDS_EMPTY", "Archetype '${rule.archetype}' must define mandatory fields.")
            }
            if (rule.typicalFacetTemplates.isEmpty()) {
                issue(issues, "PACK_V2_ARCHETYPE_FACETS_EMPTY", "Archetype '${rule.archetype}' must define typical facet templates.")
            }
            val unknownFacetTemplates = rule.typicalFacetTemplates.filterNot { it in knownFacetTemplates }
            if (unknownFacetTemplates.isNotEmpty()) {
                issue(
                    issues,
                    "PACK_V2_ARCHETYPE_FACET_TEMPLATE_UNKNOWN",
                    "Archetype '${rule.archetype}' references unknown facet templates: ${unknownFacetTemplates.joinToString(", ")}.",
                )
            }
            if (rule.validators.isEmpty() || rule.runtimeSignals.isEmpty() || rule.aiVisionDoNotGuess.isEmpty()) {
                issue(
                    issues,
                    "PACK_V2_ARCHETYPE_RUNTIME_CONTRACT_INCOMPLETE",
                    "Archetype '${rule.archetype}' must define validators, runtime signals, and AI/vision no-guess rules.",
                )
            }
            if (rule.seedData.isEmpty() || rule.liveValues.isEmpty()) {
                issue(
                    issues,
                    "PACK_V2_ARCHETYPE_DATA_OWNERSHIP_INCOMPLETE",
                    "Archetype '${rule.archetype}' must define both seed data and live values/candidates ownership.",
                )
            }
        }
    }

    private fun validateAliasRules(
        aliasRules: GeneratedAliasRuleRegistryDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val seen = HashSet<String>()
        aliasRules.rules.forEach { rule ->
            if (!seen.add(rule.code)) {
                issue(issues, "PACK_V2_ALIAS_RULE_DUPLICATE", "Duplicate generated alias rule '${rule.code}'.")
            }
            if (rule.sourceFields.isEmpty() || rule.generatedAliasKinds.isEmpty()) {
                issue(issues, "PACK_V2_ALIAS_RULE_INCOMPLETE", "Generated alias rule '${rule.code}' must define source fields and generated kinds.")
            }
            if (rule.manualOnlyKinds.isEmpty() || rule.negativeGuardKinds.isEmpty()) {
                issue(issues, "PACK_V2_ALIAS_MANUAL_GUARDS_MISSING", "Generated alias rule '${rule.code}' must keep manual-only kinds and negative guards.")
            }
        }
    }

    private fun validateRouteGuardLayers(
        routeGuardLayers: RouteGuardLayerRegistryDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val required = setOf(
            "FASH_ROUTE_GUARDRAILS_COMMON",
            "TECH_ROUTE_GUARDRAILS_COMMON",
            "AUTO_ROUTE_GUARDRAILS_COMMON",
            "KIDS_ROUTE_GUARDRAILS_COMMON",
            "FOOD_BEAUTY_HEALTH_GUARDRAILS_COMMON",
        )
        val layerCodes = routeGuardLayers.layers.map { it.code.trim() }.toSet()
        val missing = required - layerCodes
        if (missing.isNotEmpty()) {
            issue(issues, "PACK_V2_ROUTE_GUARD_LAYERS_MISSING", "Missing common route guard layers: ${missing.sorted().joinToString(", ")}.")
        }
        routeGuardLayers.layers.forEach { layer ->
            if (layer.appliesToL0.isEmpty() || layer.requiredGoldenKinds.isEmpty()) {
                issue(issues, "PACK_V2_ROUTE_GUARD_LAYER_INCOMPLETE", "Route guard layer '${layer.code}' must define L0 scope and golden kinds.")
            }
        }
    }

    private fun validateAssignments(
        categories: List<Category>,
        assignments: CategoryArchetypeAssignmentDocument,
        archetypeRegistry: CategoryArchetypeRegistryDocument,
        routeGuardLayers: RouteGuardLayerRegistryDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val categoryCodes = categories.map { it.code }.toSet()
        val activeCategoryCodes = categories
            .filter { it.status == CategoryStatus.ACTIVE }
            .map { it.code }
            .toSet()
        val archetypes = archetypeRegistry.archetypes.map { it.archetype }.toSet()
        val routeLayers = routeGuardLayers.layers.map { it.code }.toSet()
        val assignmentsByCategory = assignments.assignments.groupBy { it.categoryCode }

        assignmentsByCategory.filterValues { it.size > 1 }.keys.forEach { categoryCode ->
            issue(issues, "PACK_V2_ARCHETYPE_ASSIGNMENT_DUPLICATE", "Duplicate archetype assignment for category '$categoryCode'.")
        }
        val missingAssignments = activeCategoryCodes - assignmentsByCategory.keys
        if (missingAssignments.isNotEmpty()) {
            issue(
                issues,
                "PACK_V2_ARCHETYPE_ASSIGNMENT_MISSING",
                "Active categories missing archetype assignments: ${missingAssignments.sorted().take(20).joinToString(", ")}.",
            )
        }

        assignments.assignments.forEach { assignment ->
            if (assignment.categoryCode !in categoryCodes) {
                issue(issues, "PACK_V2_ARCHETYPE_ASSIGNMENT_CATEGORY_UNKNOWN", "Archetype assignment references unknown category '${assignment.categoryCode}'.")
            }
            if (assignment.archetypes.isEmpty()) {
                issue(issues, "PACK_V2_ARCHETYPE_ASSIGNMENT_EMPTY", "Category '${assignment.categoryCode}' must have at least one archetype.")
            }
            val unknownArchetypes = assignment.archetypes.filterNot { it in archetypes }
            if (unknownArchetypes.isNotEmpty()) {
                issue(
                    issues,
                    "PACK_V2_ARCHETYPE_ASSIGNMENT_UNKNOWN",
                    "Category '${assignment.categoryCode}' references unknown archetypes: ${unknownArchetypes.joinToString(", ")}.",
                )
            }
            val routeGuardLayer = assignment.routeGuardLayer?.trim()?.takeIf { it.isNotEmpty() }
            if (routeGuardLayer != null && routeGuardLayer !in routeLayers) {
                issue(
                    issues,
                    "PACK_V2_ROUTE_GUARD_ASSIGNMENT_UNKNOWN",
                    "Category '${assignment.categoryCode}' references unknown route guard layer '$routeGuardLayer'.",
                )
            }
            if (CategoryArchetype.COMPATIBILITY_DRIVEN in assignment.archetypes &&
                assignment.identityMode == "NONE" &&
                assignment.liveCandidateAttributeCodes.isEmpty()
            ) {
                issue(
                    issues,
                    "PACK_V2_COMPATIBILITY_ASSIGNMENT_WEAK",
                    "Compatibility-driven category '${assignment.categoryCode}' must define identity mode or live candidate attributes.",
                )
            }
        }
    }

    private fun validatePackManifests(
        packManifests: CatalogPackV2ManifestRegistryDocument,
        contract: CatalogPackV2ContractDocument,
        routeGuardLayers: RouteGuardLayerRegistryDocument,
        assignments: CategoryArchetypeAssignmentDocument,
        categories: List<Category>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val contractSections = contract.sections.toSet()
        val routeLayers = routeGuardLayers.layers.map { it.code }.toSet()
        val assignmentByCategory = assignments.assignments.associateBy { it.categoryCode }
        val activeCategoryCodes = categories
            .filter { it.status == CategoryStatus.ACTIVE }
            .map { it.code }
            .toSet()
        val categoryCodes = categories.map { it.code }.toSet()
        val seen = HashSet<String>()
        val manifestCategoryCoverage = mutableSetOf<String>()

        packManifests.manifests.forEach { manifest ->
            if (!seen.add(manifest.packId)) {
                issue(issues, "PACK_V2_MANIFEST_DUPLICATE", "Duplicate pack v2 manifest '${manifest.packId}'.")
            }
            val unknownSections = manifest.packSections.filterNot { it in contractSections }
            if (unknownSections.isNotEmpty()) {
                issue(
                    issues,
                    "PACK_V2_MANIFEST_SECTION_UNKNOWN",
                    "Pack '${manifest.packId}' references unknown sections: ${unknownSections.joinToString(", ")}.",
                )
            }
            manifest.requiredFiles.forEach { file ->
                val path = "${manifest.basePath.trimEnd('/')}/$file"
                if (!CatalogSeedResourceReader.resourceExists(path)) {
                    issue(issues, "PACK_V2_MANIFEST_FILE_MISSING", "Pack '${manifest.packId}' missing required file '$path'.")
                }
            }
            manifest.userSurfaceFile?.let { file ->
                val path = "${manifest.basePath.trimEnd('/')}/$file"
                if (!CatalogSeedResourceReader.resourceExists(path)) {
                    issue(issues, "PACK_V2_UI_SURFACE_FILE_MISSING", "Pack '${manifest.packId}' missing user surface file '$path'.")
                } else if (CatalogSeedResourceReader.readText(path).isBlank()) {
                    issue(issues, "PACK_V2_UI_SURFACE_FILE_EMPTY", "Pack '${manifest.packId}' has empty user surface file '$path'.")
                }
            }
            val routeGuardLayer = manifest.routeGuardLayer?.trim()?.takeIf { it.isNotEmpty() }
            if (routeGuardLayer != null && routeGuardLayer !in routeLayers) {
                issue(issues, "PACK_V2_MANIFEST_ROUTE_LAYER_UNKNOWN", "Pack '${manifest.packId}' references unknown route guard layer '$routeGuardLayer'.")
            }
            val coveredCategoryCodes = manifest.coveredCategoryCodes()
            coveredCategoryCodes.forEach { categoryCode ->
                if (categoryCode !in categoryCodes) {
                    issue(issues, "PACK_V2_MANIFEST_CATEGORY_UNKNOWN", "Pack '${manifest.packId}' references unknown category '$categoryCode'.")
                }
                manifestCategoryCoverage += categoryCode
                val assignment = assignmentByCategory[categoryCode]
                if (assignment == null) {
                    issue(issues, "PACK_V2_MANIFEST_ASSIGNMENT_MISSING", "Pack '${manifest.packId}' category '$categoryCode' has no archetype assignment.")
                } else if (routeGuardLayer != null && assignment.routeGuardLayer != null && routeGuardLayer != assignment.routeGuardLayer) {
                    issue(
                        issues,
                        "PACK_V2_MANIFEST_ROUTE_LAYER_MISMATCH",
                        "Pack '${manifest.packId}' route layer '$routeGuardLayer' differs from category '$categoryCode' assignment '${assignment.routeGuardLayer}'.",
                    )
                } else if (manifest.archetypes.isNotEmpty() && manifest.archetypes.toSet() != assignment.archetypes.toSet()) {
                    issue(
                        issues,
                        "PACK_V2_MANIFEST_ARCHETYPE_MISMATCH",
                        "Pack '${manifest.packId}' archetypes ${manifest.archetypes} differ from category assignment ${assignment.archetypes}.",
                    )
                }
            }
        }

        val missingManifestCoverage = activeCategoryCodes - manifestCategoryCoverage
        if (missingManifestCoverage.isNotEmpty()) {
            issue(
                issues,
                "PACK_V2_MANIFEST_COVERAGE_MISSING",
                "Active categories missing catalog_pack_v2 manifest coverage: ${missingManifestCoverage.sorted().take(20).joinToString(", ")}.",
            )
        }
    }

    private fun CatalogPackV2Manifest.coveredCategoryCodes(): Set<String> =
        (listOfNotNull(categoryCode) + categoryCodes)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun validateEffectiveSpecArchetypeGates(
        categories: List<Category>,
        registry: Stage22RegistrySnapshot,
        packages: List<Stage22PackageData>,
        globalConstraints: List<CatalogConstraints>,
        archetypeRegistry: CategoryArchetypeRegistryDocument,
        assignments: CategoryArchetypeAssignmentDocument,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val parentCodes = categories
            .mapNotNull { it.parentCode?.trim()?.takeIf(String::isNotEmpty) }
            .toSet()
        val activeLeafCategories = categories
            .filter { it.status == CategoryStatus.ACTIVE && it.code !in parentCodes }
            .sortedBy { it.code }
        val assignmentByCategory = assignments.assignments.associateBy { it.categoryCode }
        val ruleByArchetype = archetypeRegistry.archetypes.associateBy { it.archetype }
        val engine = try {
            Stage22EffectiveSpecEngine.fromSeed(
                categories = categories,
                registry = registry,
                packages = packages,
                globalConstraints = globalConstraints,
            )
        } catch (error: Exception) {
            issue(issues, "PACK_V2_EFFECTIVE_SPEC_ENGINE_FAILED", "Failed to build effective spec engine for pack v2 gates: ${error.message}")
            return
        }

        activeLeafCategories.forEach { category ->
            val assignment = assignmentByCategory[category.code] ?: return@forEach
            val spec = try {
                engine.getEffectiveSpec(category.code)
            } catch (error: Exception) {
                issue(issues, "PACK_V2_EFFECTIVE_SPEC_MISSING", "Failed to resolve effective spec for '${category.code}': ${error.message}")
                return@forEach
            }
            val attributes = spec.attributes.map { it.attributeCode }.toSet()

            assignment.archetypes.forEach { archetype ->
                val rule = ruleByArchetype[archetype] ?: return@forEach
                rule.requiredAnyAttributeCodes.forEach { alternatives ->
                    if (alternatives.isEmpty()) return@forEach
                    if (alternatives.none { it in attributes }) {
                        issue(
                            issues,
                            "PACK_V2_ARCHETYPE_REQUIRED_ATTRIBUTE_MISSING",
                            "Category '${category.code}' has archetype '$archetype' but none of required alternatives are present: ${alternatives.joinToString(", ")}.",
                        )
                    }
                }
            }

            if (CategoryArchetype.LIFE_STAGE_DRIVEN in assignment.archetypes) {
                val hasSeedLifeStageSignal = attributes.any { attributeCode ->
                    attributeCode.contains("age", ignoreCase = true) ||
                        attributeCode.contains("life_stage", ignoreCase = true) ||
                        attributeCode == "school_grade_group"
                }
                val hasLiveLifeStageSignal = assignment.liveCandidateAttributeCodes.any { attributeCode ->
                    attributeCode.contains("age", ignoreCase = true) ||
                        attributeCode.contains("life_stage", ignoreCase = true)
                }
                if (!hasSeedLifeStageSignal && !hasLiveLifeStageSignal) {
                    issue(
                        issues,
                        "PACK_V2_LIFE_STAGE_SIGNAL_MISSING",
                        "Category '${category.code}' is LIFE_STAGE_DRIVEN but has no seed or live life-stage signal.",
                    )
                }
            }
        }
    }

    private fun issue(
        issues: MutableList<Stage22SeedValidationIssue>,
        code: String,
        message: String,
    ) {
        issues += Stage22SeedValidationIssue(code = code, message = message)
    }
}
