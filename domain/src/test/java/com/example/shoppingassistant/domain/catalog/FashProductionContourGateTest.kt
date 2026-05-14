package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FashProductionContourGateTest {
    private val routeRouter = Stage21RuntimeQueryRouter()
    private val stage22Engine: Stage22EffectiveSpecEngine by lazy {
        Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
    }

    @Test
    fun final_fash_tree_contract_is_fixed_and_review_gated() {
        val contract = readJsonObject(TREE_CONTRACT_PATH)
        val categories = CatalogSeed.categories.associateBy { it.code }
        val actualPublicChildren = CatalogSeed.categories
            .filter { it.parentCode == "FASH" && it.status == CategoryStatus.ACTIVE }
            .map { it.code }
            .toSet()
        val contractPublicBranches = contract
            .jsonArray("publicBranches")
            .map { branch -> branch.jsonObject.getValue("code").jsonPrimitive.content }
            .toSet()
        val forbidden = contract
            .jsonObject("governance")
            .stringList("forbiddenPublicBranchExamples")

        assertEquals(PUBLIC_BRANCHES, contractPublicBranches)
        assertEquals(PUBLIC_BRANCHES, actualPublicChildren)
        assertTrue("FASH.APPAREL_COMMON must remain shared-only.", "FASH.APPAREL_COMMON" !in categories)
        assertTrue(contract.jsonObject("governance").getValue("publicBranchAdditionsRequireReview").jsonPrimitive.boolean)
        forbidden.forEach { forbiddenCode ->
            assertFalse("$forbiddenCode must not be a public category without review.", forbiddenCode in categories)
        }
        assertEquals(
            "Одежда, обувь, сумки и аксессуары",
            categories.getValue("FASH").title.resolve(locale = "ru", fallback = "FASH"),
        )
    }

    @Test
    fun route_conflict_golden_cases_do_not_cross_finalize_wrong_fash_branches() = runBlocking {
        val failures = mutableListOf<String>()

        readRouteGolden().forEach { golden ->
            val result = routeRouter.route(query = golden.queryText, locale = golden.locale)
            val actualType = result.routeType.name
            val actualTarget = result.primaryTargetCode.orEmpty()
            if (actualType != golden.expectedRouteType || actualTarget != golden.expectedTarget) {
                failures += "${golden.caseId}: '${golden.queryText}' expected ${golden.expectedRouteType}/${golden.expectedTarget}, got $actualType/$actualTarget"
            }
            golden.forbiddenTargets.forEach { forbidden ->
                assertNotEquals("${golden.caseId}: '${golden.queryText}' must not route to $forbidden", forbidden, actualTarget)
            }
        }

        assertTrue("FASH route conflict failures: ${failures.joinToString("; ")}", failures.isEmpty())
    }

    @Test
    fun effective_spec_snapshots_match_runtime_specs_for_all_public_fash_branches() {
        SNAPSHOT_PATHS.forEach { (categoryCode, snapshotPath) ->
            val snapshot = readJsonObject(snapshotPath)
            val gate = snapshot.jsonObject("production_gate")
            val spec = stage22Engine.getEffectiveSpec(categoryCode)
            val publicSpec = publicEffectiveSpec(categoryCode)
            val attributes = spec.attributes.map { it.attributeCode }.toSet()
            val required = spec.attributes.filter { it.required }.map { it.attributeCode }.toSet()
            val presentation = requireNotNull(CatalogFacetPresentationProfiles.resolve(categoryCode)) {
                "$categoryCode must have a presentation profile."
            }

            assertFalse("$categoryCode must use a materialized Stage 2.2 effective spec.", spec.meta.isFallback)
            assertEquals(categoryCode, snapshot.getValue("categoryCode").jsonPrimitive.content)
            assertTrue("$categoryCode attributes missing required snapshot fields.", attributes.containsAll(gate.stringList("required_present")))
            assertTrue("$categoryCode required fields drifted.", required.containsAll(gate.stringList("required_fields")))
            assertEquals(
                "$categoryCode archetype assignment drifted.",
                gate.stringList("archetypes").toSet(),
                CatalogPackV2RegistryLoader.archetypesFor(categoryCode).map { it.name }.toSet(),
            )

            val primaryFacets = gate.stringList("primary_facets")
            assertTrue("$categoryCode primary facets must be <= 7.", primaryFacets.size <= 7)
            assertTrue("$categoryCode runtime primary facets must be <= 7.", presentation.mainTypedFacetKeys.size <= 7)
            assertEquals(
                "$categoryCode primary facet set must match presentation profile.",
                primaryFacets.toSet(),
                presentation.mainTypedFacetKeys.toSet(),
            )

            gate.stringList("hidden_from_primary_ui").forEach { hiddenField ->
                assertTrue("$categoryCode hidden field '$hiddenField' must be hidden in presentation.", hiddenField in presentation.hiddenTypedFacetKeys)
                assertFalse("$categoryCode hidden field '$hiddenField' leaked to primary facets.", hiddenField in presentation.mainTypedFacetKeys)
            }

            publicSpec.allAttributes()
                .filter { it.role == CatalogAttributeRole.T3_SYSTEM_HIDDEN }
                .forEach { attribute ->
                    assertFalse(
                        "$categoryCode hidden/system field '${attribute.code}' leaked to required user surface.",
                        CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes,
                    )
                    assertFalse(
                        "$categoryCode hidden/system field '${attribute.code}' leaked to optional user surface.",
                        CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes,
                    )
                }
        }
    }

    @Test
    fun ui_surface_smoke_keeps_initial_fields_facets_and_type_specific_fields_safe() {
        SNAPSHOT_PATHS.forEach { (categoryCode, snapshotPath) ->
            val gate = readJsonObject(snapshotPath).jsonObject("production_gate")
            val attributes = stage22Engine.getEffectiveSpec(categoryCode).attributes.map { it.attributeCode }.toSet()
            val initialFields = gate.stringList("initial_fields")

            assertTrue("$categoryCode initial fields must be <= 8.", initialFields.size <= 8)
            assertTrue("$categoryCode initial fields must exist in effective spec.", attributes.containsAll(initialFields))

            val typeSpecific = gate["type_specific"]?.jsonObject ?: JsonObject(emptyMap())
            typeSpecific.forEach { (typeValue, fieldsElement) ->
                val fields = fieldsElement.jsonArray.map { it.jsonPrimitive.content }
                assertTrue(
                    "$categoryCode type=$typeValue references fields outside effective spec: ${(fields - attributes).joinToString(", ")}",
                    attributes.containsAll(fields),
                )
            }
        }
    }

    @Test
    fun visual_search_golden_cases_are_backed_by_no_guess_rules() {
        val visualGolden = readJsonObject(VISUAL_GOLDEN_PATH)
        val allVisionRules = VISUAL_RULE_PATHS
            .joinToString("\n") { path -> CatalogSeedResourceReader.readText(path) }
            .lowercase()

        val cases = visualGolden.getValue("cases").jsonArray
        assertEquals("FASH visual golden case count drifted.", 4, cases.size)
        cases.forEach { caseElement ->
            val case = caseElement.jsonObject
            case.stringList("forbiddenRoutes").forEach { forbiddenRoute ->
                assertTrue("$forbiddenRoute must be a known category or explicit route-out target.", forbiddenRoute in KNOWN_ROUTE_TARGETS)
            }
            case.stringList("ruleEvidence").forEach { evidence ->
                assertTrue(
                    "Visual golden ${case.getValue("caseId").jsonPrimitive.content} is not backed by declared vision rules: '$evidence'.",
                    allVisionRules.contains(evidence.lowercase()),
                )
            }
        }
    }

    @Test
    fun public_fash_branches_pass_readiness_thresholds_without_blockers() {
        val stage22Report = Stage22SeedValidator().validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        assertTrue(stage22Report.summary(), stage22Report.isValid)

        PUBLIC_BRANCHES.forEach { categoryCode ->
            val spec = publicEffectiveSpec(categoryCode)
            assertTrue(
                "$categoryCode readiness must be READY or BETA.",
                spec.readiness == CatalogCategoryReadiness.READY || spec.readiness == CatalogCategoryReadiness.BETA,
            )
            assertTrue("$categoryCode must have no readiness blocking issues.", spec.meta.readinessBlockingIssues.isEmpty())
            assertTrue("$categoryCode unknownAttributeRate must be < 5%.", spec.meta.operationalUnknownAttributeRate < 0.05)
            assertTrue("$categoryCode requiredMissingRate must be < 10%.", spec.meta.operationalRequiredMissingRate < 0.10)
            assertEquals("$categoryCode operational dropped rate must be zero in seed readiness.", 0.0, spec.meta.operationalDroppedRate, 0.0)
            assertEquals("$categoryCode low confidence rate must be zero in seed readiness.", 0.0, spec.meta.operationalLowConfidenceRate, 0.0)
        }
    }

    private fun publicEffectiveSpec(categoryCode: String): CatalogCategoryEffectiveSpec =
        CatalogSeed.categoryWriteSpecs
            .first { it.category.code == categoryCode }
            .toCategoryEffectiveSpec(
                constraints = CatalogSeed.constraints.filter { constraint ->
                    when (constraint.scope) {
                        ConstraintScope.GLOBAL -> true
                        ConstraintScope.CATEGORY -> constraint.categoryCode == categoryCode
                        ConstraintScope.BRAND,
                        ConstraintScope.MODEL,
                            -> false
                    }
                },
            )

    private fun readJsonObject(path: String): JsonObject =
        CatalogSeedResourceReader.json.parseToJsonElement(CatalogSeedResourceReader.readText(path)).jsonObject

    private fun readRouteGolden(): List<RouteConflictGoldenCase> {
        val lines = CatalogSeedResourceReader.readText(ROUTE_GOLDEN_PATH)
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toList()
        val header = lines.first().split("\t")
        return lines.drop(1).map { line ->
            val cols = line.split("\t")
            fun col(name: String): String = cols.getOrElse(header.indexOf(name)) { "" }.trim()
            RouteConflictGoldenCase(
                caseId = col("case_id"),
                queryText = col("query_text"),
                locale = col("locale"),
                expectedRouteType = col("expected_route_type"),
                expectedTarget = col("expected_target"),
                forbiddenTargets = col("forbidden_targets")
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
        }
    }

    private fun JsonObject.jsonObject(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.jsonArray(key: String) = getValue(key).jsonArray

    private fun JsonObject.stringList(key: String): List<String> =
        get(key)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    private data class RouteConflictGoldenCase(
        val caseId: String,
        val queryText: String,
        val locale: String,
        val expectedRouteType: String,
        val expectedTarget: String,
        val forbiddenTargets: List<String>,
    )

    private companion object {
        private const val TREE_CONTRACT_PATH = "taxonomy/stage2/2.2/FASH/fash_category_tree_contract.v1_0.json"
        private const val ROUTE_GOLDEN_PATH = "taxonomy/stage2/2.2/FASH/fash_route_conflict_golden.tsv"
        private const val VISUAL_GOLDEN_PATH = "taxonomy/stage2/2.2/FASH/fash_visual_search_golden.v1_0.json"

        private val PUBLIC_BRANCHES = setOf(
            "FASH.MEN",
            "FASH.WOMEN",
            "FASH.KIDS",
            "FASH.SHOES",
            "FASH.BAGS",
            "FASH.ACCESSORIES",
        )

        private val SNAPSHOT_PATHS = mapOf(
            "FASH.MEN" to "taxonomy/stage2/2.2/FASH/overrides/fash_men/v1_0/effective_spec_snapshot.expected.fash_men.v1_0.json",
            "FASH.WOMEN" to "taxonomy/stage2/2.2/FASH/overrides/fash_women/v1_0/effective_spec_snapshot.expected.fash_women.v1_0.json",
            "FASH.KIDS" to "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/effective_spec_snapshot.expected.fash_kids.v1_0.json",
            "FASH.SHOES" to "taxonomy/stage2/2.2/FASH/category_packs/fash_shoes/v1_0/effective_spec_snapshot.expected.fash_shoes.v1_0.json",
            "FASH.BAGS" to "taxonomy/stage2/2.2/FASH/overrides/fash_bags/v1_0/effective_spec_snapshot.expected.fash_bags.v1_0.json",
            "FASH.ACCESSORIES" to "taxonomy/stage2/2.2/FASH/overrides/fash_accessories/v1_0/effective_spec_snapshot.expected.fash_accessories.v1_0.json",
        )

        private val VISUAL_RULE_PATHS = listOf(
            "taxonomy/stage2/2.2/FASH/ai_vision_contract.fash_apparel_common.v1_0.json",
            "taxonomy/stage2/2.2/FASH/overrides/fash_men/v1_0/vision_rules.fash_men.v1_0.yaml",
            "taxonomy/stage2/2.2/FASH/category_packs/fash_shoes/v1_0/vision_rules.fash_shoes.v1_0.yaml",
            "taxonomy/stage2/2.2/FASH/overrides/fash_bags/v1_0/vision_rules.fash_bags.v1_0.yaml",
            "taxonomy/stage2/2.2/FASH/overrides/fash_accessories/v1_0/vision_rules.fash_accessories.v1_0.yaml",
        )

        private val KNOWN_ROUTE_TARGETS = PUBLIC_BRANCHES + setOf(
            "TECH.WEARABLES",
            "TECH.SMART_HOME_SECURITY",
            "TECH.PHONE_ACCESSORIES",
            "TECH.CAMERAS_DRONES",
            "PETS.ACCESSORIES",
            "HOME.REPAIR_TOOLS",
            "BEAUTY.HEALTH",
            "SPORT.OUTDOOR",
            "POLICY_REVIEW",
        )
    }
}
