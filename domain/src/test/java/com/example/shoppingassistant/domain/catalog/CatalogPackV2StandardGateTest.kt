package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPackV2StandardGateTest {
    @Test
    fun contract_declares_universal_pack_v2_primitives() {
        val contract = CatalogPackV2RegistryLoader.contract
        val facetTemplates = CatalogPackV2RegistryLoader.facetTemplates.templates.map { it.code }.toSet()
        val routeLayers = CatalogPackV2RegistryLoader.routeGuardLayers.layers.map { it.code }.toSet()

        assertEquals("catalog_pack_v2", contract.standardCode)
        assertTrue(
            contract.sections.containsAll(
                listOf(
                    "shared_standard",
                    "foundation_manifest",
                    "category_schema_pack",
                    "child_override_pack",
                    "route_guard_pack",
                    "identity_pack",
                    "compatibility_pack",
                ),
            ),
        )
        assertEquals(CatalogAttributeRole.entries.toSet(), contract.attributeRoles.toSet())
        assertEquals(CatalogAttributeUsageScope.entries.toSet(), contract.usageScopes.toSet())
        assertTrue("compatibility_widget" in facetTemplates)
        assertTrue("ingredient/allergen_warning" in facetTemplates)
        assertTrue("life_stage_selector" in facetTemplates)
        assertTrue("FASH_ROUTE_GUARDRAILS_COMMON" in routeLayers)
        assertTrue("TECH_ROUTE_GUARDRAILS_COMMON" in routeLayers)
        assertTrue("AUTO_ROUTE_GUARDRAILS_COMMON" in routeLayers)
        assertTrue("KIDS_ROUTE_GUARDRAILS_COMMON" in routeLayers)
        assertTrue("FOOD_BEAUTY_HEALTH_GUARDRAILS_COMMON" in routeLayers)
    }

    @Test
    fun all_active_categories_are_assigned_to_archetypes_with_required_examples() {
        val activeCategories = CatalogSeed.categories
            .filter { it.status == CategoryStatus.ACTIVE }
            .map { it.code }
            .toSet()
        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments.assignments
            .associateBy { it.categoryCode }

        assertTrue(
            "Every active category must have a pack v2 archetype assignment.",
            activeCategories.all { it in assignments.keys },
        )
        assertEquals(
            setOf(CategoryArchetype.IDENTITY_CRITICAL, CategoryArchetype.SPEC_HEAVY),
            assignments.getValue("TECH.PHONES").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.COMPATIBILITY_DRIVEN, CategoryArchetype.TYPE_DRIVEN),
            assignments.getValue("TECH.PHONE_ACCESSORIES").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.SIZE_DRIVEN, CategoryArchetype.TYPE_DRIVEN),
            assignments.getValue("FASH.SHOES").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.COMPATIBILITY_DRIVEN, CategoryArchetype.SPEC_HEAVY),
            assignments.getValue("AUTO.PARTS").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.CONSUMABLE, CategoryArchetype.LIFE_STAGE_DRIVEN),
            assignments.getValue("PETS.FOOD").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.SAFETY_REGULATED, CategoryArchetype.CONSUMABLE),
            assignments.getValue("BEAUTY.HEALTH").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.TYPE_DRIVEN, CategoryArchetype.SIZE_DRIVEN),
            assignments.getValue("HOME.FURNITURE").archetypes.toSet(),
        )
    }

    @Test
    fun all_active_categories_have_pack_v2_manifest_coverage() {
        val activeCategories = CatalogSeed.categories
            .filter { it.status == CategoryStatus.ACTIVE }
            .map { it.code }
            .toSet()
        val manifestCoverage = CatalogPackV2RegistryLoader.packManifests.manifests
            .flatMap { manifest -> manifest.coveredCategoryCodesForTest() }
            .toSet()

        assertTrue(
            "Every active category/branch must be covered by a catalog_pack_v2 manifest. Missing: " +
                (activeCategories - manifestCoverage).sorted().joinToString(", "),
            manifestCoverage.containsAll(activeCategories),
        )
    }

    @Test
    fun fash_category_tree_matches_reference_contract() {
        val categories = CatalogSeed.categories.associateBy { it.code }
        val fashChildren = CatalogSeed.categories
            .filter { it.parentCode == "FASH" }
            .map { it.code }
            .toSet()

        assertEquals(
            "Одежда, обувь, сумки и аксессуары",
            categories.getValue("FASH").title.resolve(locale = "ru", fallback = "FASH"),
        )
        assertEquals(
            setOf(
                "FASH.MEN",
                "FASH.WOMEN",
                "FASH.KIDS",
                "FASH.SHOES",
                "FASH.BAGS",
                "FASH.ACCESSORIES",
            ),
            fashChildren,
        )
        assertTrue(
            "FASH.APPAREL_COMMON must remain a shared standard, not a public category.",
            "FASH.APPAREL_COMMON" !in categories.keys,
        )

        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments.assignments.associateBy { it.categoryCode }
        listOf("FASH.MEN", "FASH.WOMEN", "FASH.KIDS").forEach { categoryCode ->
            assertTrue(CategoryArchetype.SIZE_DRIVEN in assignments.getValue(categoryCode).archetypes)
            assertTrue(CategoryArchetype.TYPE_DRIVEN in assignments.getValue(categoryCode).archetypes)
            assertEquals(listOf("FASH.APPAREL_COMMON"), assignments.getValue(categoryCode).sharedStandards)
        }
        assertEquals(
            setOf(CategoryArchetype.SIZE_DRIVEN, CategoryArchetype.TYPE_DRIVEN),
            assignments.getValue("FASH.SHOES").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.TYPE_DRIVEN, CategoryArchetype.SIZE_DRIVEN, CategoryArchetype.VISUAL_SIMPLE),
            assignments.getValue("FASH.BAGS").archetypes.toSet(),
        )
        assertEquals(
            setOf(CategoryArchetype.TYPE_DRIVEN, CategoryArchetype.VISUAL_SIMPLE),
            assignments.getValue("FASH.ACCESSORIES").archetypes.toSet(),
        )
    }

    @Test
    fun pack_v2_validator_accepts_current_seed_and_pack_manifests() {
        val report = CatalogPackV2SeedValidator().validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )

        assertTrue(
            "catalog_pack_v2 validation issues: ${report.take(10).joinToString { "[${it.code}] ${it.message}" }}",
            report.isEmpty(),
        )
    }

    @Test
    fun effective_spec_exposes_roles_usage_scopes_and_archetypes_to_runtime() {
        val spec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == "FASH.SHOES" }
            .toCategoryEffectiveSpec(constraints = scopedConstraints("FASH.SHOES"))
        val allAttributes = spec.allAttributes().associateBy { it.code }

        assertEquals(
            setOf(CategoryArchetype.SIZE_DRIVEN, CategoryArchetype.TYPE_DRIVEN),
            spec.meta.archetypes.toSet(),
        )

        val shoeType = allAttributes.getValue("shoe_type")
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, shoeType.role)
        assertTrue(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in shoeType.usageScopes)
        assertTrue(CatalogAttributeUsageScope.PRIMARY_FACET in shoeType.usageScopes)
        assertEquals("enum_primary", shoeType.facetTemplateCode)

        val rawSize = allAttributes.getValue("raw_size_text")
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, rawSize.role)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in rawSize.usageScopes)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in rawSize.usageScopes)
        assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in rawSize.usageScopes)

        val brand = allAttributes.getValue("brand")
        assertEquals(CatalogAttributeRole.T0_CORE, brand.role)
        assertEquals("brand_optional", brand.facetTemplateCode)
    }

    @Test
    fun translated_pack_manifests_keep_existing_user_surfaces() {
        CatalogPackV2RegistryLoader.packManifests.manifests
            .filter { it.userSurfaceFile != null }
            .forEach { manifest ->
                val path = "${manifest.basePath}/${manifest.userSurfaceFile}"
                val userSurface = CatalogSeedResourceReader.readText(path)
                assertTrue("User surface for ${manifest.packId} must not be empty.", userSurface.isNotBlank())
                assertTrue(
                    "User surface for ${manifest.packId} must expose navigation or facet controls.",
                    userSurface.contains("facet", ignoreCase = true) ||
                        userSurface.contains("navigation", ignoreCase = true) ||
                        userSurface.contains("primary", ignoreCase = true),
                )
            }
    }

    private fun scopedConstraints(categoryCode: String) =
        CatalogSeed.constraints.filter { constraint ->
            when (constraint.scope) {
                ConstraintScope.GLOBAL -> true
                ConstraintScope.CATEGORY -> constraint.categoryCode == categoryCode
                ConstraintScope.BRAND,
                ConstraintScope.MODEL,
                    -> false
            }
        }

    private fun CatalogPackV2Manifest.coveredCategoryCodesForTest(): Set<String> =
        (listOfNotNull(categoryCode) + categoryCodes)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
