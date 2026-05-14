package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FashTypedAttributeCoverageGateTest {
    private val stage22Engine: Stage22EffectiveSpecEngine by lazy {
        Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
    }

    @Test
    fun matrix_covers_every_allowed_fash_type_value_from_effective_constraints() {
        assertEquals("FASH_TYPED_ATTRIBUTE_COVERAGE_MATRIX_v1_0", FashTypeSurfaceResolver.matrix.matrixCode)

        val matrixCategories = FashTypeSurfaceResolver.matrix.categories
            .map { it.categoryCode }
            .toSet()
        assertEquals(FASH_PUBLIC_BRANCHES, matrixCategories)

        FASH_TYPE_ATTRIBUTES.forEach { (categoryCode, typeAttribute) ->
            val spec = stage22Engine.getEffectiveSpec(categoryCode)
            val expectedTypeValues = spec.constraints
                .allowedValueCodesByAttribute
                .getValue(typeAttribute)
                .map { it.trim().uppercase(Locale.ROOT) }
                .toSet()
            val category = requireNotNull(FashTypeSurfaceResolver.category(categoryCode)) {
                "$categoryCode must be present in FASH typed attribute matrix."
            }
            val actualTypeValues = category.groups
                .flatMap { group -> group.typeValues }
                .map { it.trim().uppercase(Locale.ROOT) }
            val duplicates = actualTypeValues.groupBy { it }.filterValues { it.size > 1 }.keys

            assertTrue("$categoryCode duplicate matrix type values: ${duplicates.joinToString()}", duplicates.isEmpty())
            assertEquals("$categoryCode matrix must cover every effective $typeAttribute value.", expectedTypeValues, actualTypeValues.toSet())
        }
    }

    @Test
    fun runtime_resolver_returns_type_specific_golden_surfaces() {
        FashTypeSurfaceResolver.matrix.goldenCases.forEach { golden ->
            val resolved = FashTypeSurfaceResolver.resolve(
                categoryCode = golden.categoryCode,
                typeValue = golden.typeValue,
            )
            assertNotNull("${golden.caseId} must resolve.", resolved)
            requireNotNull(resolved)

            assertEquals("${golden.caseId} group drifted.", golden.expectedGroupCode, resolved.groupCode)
            assertEquals(
                "${golden.caseId} primary surface drifted.",
                golden.expectedPrimaryAttributes.map { it.normalizeAttributeCode() },
                resolved.primaryAttributes,
            )
            assertTrue("${golden.caseId} primary facets must be <= 7.", resolved.primaryAttributes.size <= 7)
            assertTrue("${golden.caseId} initial fields must be <= 8.", resolved.initialAttributes.size <= 8)
        }
    }

    @Test
    fun type_surfaces_only_reference_effective_attributes_and_keep_hidden_fields_out_of_ui() {
        FASH_TYPE_ATTRIBUTES.keys.forEach { categoryCode ->
            val category = requireNotNull(FashTypeSurfaceResolver.category(categoryCode))
            val stage22Attributes = stage22Engine.getEffectiveSpec(categoryCode)
                .attributes
                .map { it.attributeCode.normalizeAttributeCode() }
                .toSet()
            val publicAttributes = publicEffectiveSpec(categoryCode)
                .allAttributes()
                .map { it.code.normalizeAttributeCode() }
                .toSet()
            val specAttributes = stage22Attributes + publicAttributes

            category.groups.forEach { group ->
                val resolved = requireNotNull(FashTypeSurfaceResolver.resolve(categoryCode, group.typeValues.first()))
                val referenced = (
                    resolved.initialAttributes +
                        resolved.primaryAttributes +
                        resolved.secondaryAttributes +
                        resolved.hiddenAttributes +
                        resolved.requiredAttributes +
                        resolved.validatorAttributes +
                        resolved.noGuessAttributes
                    ).toSet()
                val missing = referenced - specAttributes - UI_GROUP_NOT_ATTRIBUTES

                assertTrue(
                    "$categoryCode/${group.groupCode} references attributes outside effective spec: ${missing.sorted().joinToString(", ")}",
                    missing.isEmpty(),
                )
                assertTrue("$categoryCode/${group.groupCode} primary facets must be <= ${category.primaryFacetsLimit}.", resolved.primaryAttributes.size <= category.primaryFacetsLimit)
                assertTrue("$categoryCode/${group.groupCode} initial fields must be <= ${category.initialFieldsLimit}.", resolved.initialAttributes.size <= category.initialFieldsLimit)
                assertTrue(
                    "$categoryCode/${group.groupCode} hidden attributes leaked to primary surface.",
                    resolved.primaryAttributes.intersect(resolved.hiddenAttributes.toSet()).isEmpty(),
                )
                assertTrue(
                    "$categoryCode/${group.groupCode} hidden attributes leaked to initial surface.",
                    resolved.initialAttributes.intersect(resolved.hiddenAttributes.toSet()).isEmpty(),
                )
            }
        }
    }

    @Test
    fun t0_t1_and_t3_attributes_are_runtime_covered_for_every_fash_branch() {
        FASH_TYPE_ATTRIBUTES.keys.forEach { categoryCode ->
            val publicSpec = publicEffectiveSpec(categoryCode)
            val category = requireNotNull(FashTypeSurfaceResolver.category(categoryCode))
            val resolvedSurfaces = category.groups
                .flatMap { group -> group.typeValues }
                .mapNotNull { typeValue -> FashTypeSurfaceResolver.resolve(categoryCode, typeValue) }
            val coveredAttributes = resolvedSurfaces
                .flatMap { surface ->
                    surface.initialAttributes +
                        surface.primaryAttributes +
                        surface.secondaryAttributes +
                        surface.hiddenAttributes +
                        surface.requiredAttributes +
                        surface.validatorAttributes +
                        surface.noGuessAttributes
                }
                .map { it.normalizeAttributeCode() }
                .toSet()
            val hiddenAttributes = resolvedSurfaces
                .flatMap { it.hiddenAttributes }
                .map { it.normalizeAttributeCode() }
                .toSet()
            val validatorAttributes = resolvedSurfaces
                .flatMap { it.validatorAttributes }
                .map { it.normalizeAttributeCode() }
                .toSet()
            val nonFacetExplanations = category.nonFacetExplanations.keys
                .map { it.normalizeAttributeCode() }
                .toSet()
            val noGuessAttributes = resolvedSurfaces
                .flatMap { it.noGuessAttributes }
                .map { it.normalizeAttributeCode() }
                .toSet()

            publicSpec.allAttributes().forEach { attribute ->
                val code = attribute.code.normalizeAttributeCode()
                if (attribute.role == CatalogAttributeRole.T3_SYSTEM_HIDDEN) {
                    resolvedSurfaces.forEach { surface ->
                        assertFalse("$categoryCode T3 field '$code' leaked to primary facets.", code in surface.primaryAttributes)
                        assertFalse("$categoryCode T3 field '$code' leaked to initial fields.", code in surface.initialAttributes)
                    }
                    return@forEach
                }

                if (attribute.role == CatalogAttributeRole.T0_CORE || attribute.role == CatalogAttributeRole.T1_TYPE_CRITICAL) {
                    assertTrue("$categoryCode T0/T1 '$code' is not covered by typed surface matrix.", code in coveredAttributes)
                    assertTrue("$categoryCode T0/T1 '$code' must have usage scopes.", attribute.usageScopes.isNotEmpty())
                    assertTrue(
                        "$categoryCode T0/T1 '$code' must have facet template or non-facet explanation.",
                        attribute.facetTemplateCode != null || code in nonFacetExplanations || code in hiddenAttributes,
                    )
                    assertTrue(
                        "$categoryCode T0/T1 '$code' must be covered by validator policy.",
                        attribute.hasBuiltInRuntimeValidator() || code in validatorAttributes,
                    )
                    if (code.requiresNoGuessPolicy()) {
                        assertTrue("$categoryCode T0/T1 '$code' must be included in no-guess policy.", code in noGuessAttributes)
                    }
                }
            }
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

    private fun CatalogAttributeSpec.hasBuiltInRuntimeValidator(): Boolean =
        valueSetType != Stage22ValueSetType.OPEN ||
            enumOnly ||
            regexPattern != null ||
            minValue != null ||
            maxValue != null ||
            dataType != AttributeDataType.STRING

    private fun String.requiresNoGuessPolicy(): Boolean =
        contains("gender") ||
            contains("age") ||
            contains("brand") ||
            contains("model") ||
            contains("size") ||
            contains("authenticity")

    private fun String.normalizeAttributeCode(): String =
        trim().lowercase(Locale.ROOT)

    private companion object {
        private val FASH_TYPE_ATTRIBUTES = linkedMapOf(
            "FASH.MEN" to "apparel_type",
            "FASH.WOMEN" to "apparel_type",
            "FASH.KIDS" to "apparel_type",
            "FASH.SHOES" to "shoe_type",
            "FASH.BAGS" to "bag_type",
            "FASH.ACCESSORIES" to "accessory_type",
        )
        private val FASH_PUBLIC_BRANCHES = FASH_TYPE_ATTRIBUTES.keys.toSet()
        private val UI_GROUP_NOT_ATTRIBUTES = setOf("measurements")
    }
}
