package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage22SeedValidatorTest {
    private val validator = Stage22SeedValidator()

    @Test
    fun current_stage22_seed_is_valid() {
        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        assertTrue(report.summary(), report.isValid)
    }

    @Test
    fun unknown_attribute_code_is_reported() {
        val sourcePackage = GenericStage22PackageLoader.loadAll()
            .first { it.constraints.isNotEmpty() }
        val brokenConstraint = CatalogConstraints(
            scope = ConstraintScope.CATEGORY,
            categoryCode = "TECH.PHONES",
            attributeConstraints = listOf(
                AttributeValueConstraint(
                    attributeCode = "unknown_attribute_code",
                    allowedValues = listOf("x"),
                ),
            ),
        )
        val brokenPackage = sourcePackage.copy(
            constraints = sourcePackage.constraints + brokenConstraint,
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = listOf(brokenPackage),
        )

        assertTrue(report.issues.any { it.code == "REFERENTIAL_ATTRIBUTE_UNKNOWN" })
    }

    @Test
    fun missing_dictionary_for_closed_attribute_is_reported() {
        val sourceRegistry = Stage22RegistryLoader.loadSnapshot()
        val brokenRegistry = sourceRegistry.copy(
            dictionaries = sourceRegistry.dictionaries - "condition",
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = brokenRegistry,
            packages = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "CLOSED_SET_DICTIONARY_MISSING" })
    }

    @Test
    fun alias_collision_is_reported() {
        val sourceRegistry = Stage22RegistryLoader.loadSnapshot()
        val sourceDictionary = sourceRegistry.dictionaries.getValue("condition")
        val collidingAlias = sourceDictionary.entries.first().aliases.first()
        val brokenDictionary = sourceDictionary.copy(
            entries = sourceDictionary.entries + Stage22ValueDictionaryEntry(
                valueCode = "COLLIDING_VALUE",
                labels = mapOf("ru" to "Коллизия", "en" to "Collision"),
                aliases = listOf(collidingAlias),
            ),
        )
        val brokenRegistry = sourceRegistry.copy(
            dictionaries = sourceRegistry.dictionaries + ("condition" to brokenDictionary),
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = brokenRegistry,
            packages = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "ALIAS_COLLISION" })
    }

    @Test
    fun invalid_schema_version_is_reported() {
        val sourceRegistry = Stage22RegistryLoader.loadSnapshot()
        val brokenRegistry = sourceRegistry.copy(
            meta = sourceRegistry.meta.copy(schemaVersion = "invalid"),
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = brokenRegistry,
            packages = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "VERSIONING_SCHEMA_INVALID" })
    }

    @Test
    fun constraints_conflict_is_reported() {
        val sourcePackage = GenericStage22PackageLoader.loadAll()
            .first { it.descriptor.l0Code == "TECH" }
        val conflictingPackage = sourcePackage.copy(
            constraints = sourcePackage.constraints + CatalogConstraints(
                scope = ConstraintScope.CATEGORY,
                categoryCode = "TECH.PHONES",
                attributeConstraints = listOf(
                    AttributeValueConstraint(
                        attributeCode = "color",
                        forbiddenValues = listOf("BLACK"),
                    ),
                ),
            ),
        )
        val global = listOf(
            CatalogConstraints(
                scope = ConstraintScope.GLOBAL,
                attributeConstraints = listOf(
                    AttributeValueConstraint(
                        attributeCode = "color",
                        allowedValues = listOf("BLACK"),
                    ),
                ),
            ),
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = listOf(conflictingPackage),
            globalConstraints = global,
        )

        assertTrue(report.issues.any { it.code == "CONSTRAINTS_CONFLICT" })
    }

    @Test
    fun profile_without_attributes_is_rejected() {
        val categories = listOf(
            Category(
                code = "TECH",
                segment = CategorySegment.TECH,
                title = localizedTextOf("ru" to "TECH"),
                parentCode = null,
            ),
            Category(
                code = "TECH.PHONES",
                segment = CategorySegment.TECH,
                title = localizedTextOf("ru" to "TECH.PHONES"),
                parentCode = "TECH",
            ),
        )
        val packageData = Stage22PackageData(
            descriptor = Stage22PackageDescriptor(
                l0Code = "TECH",
                basePath = "taxonomy/stage2/2.2/TECH",
            ),
            profiles = listOf(
                Stage22ResourceProfile(
                    category = Stage22SeedCategoryRef(
                        code = categories[1].code,
                        segment = categories[1].segment,
                        title = categories[1].title.resolve(locale = "ru", fallback = categories[1].code),
                        parentCode = categories[1].parentCode,
                    ),
                    attributes = emptyList(),
                    categoryAttributes = emptyList(),
                    valueDictionaries = emptyList(),
                    requiredIfRules = emptyList(),
                ),
            ),
            constraints = emptyList(),
            valueDicts = emptyList(),
        )

        val report = validator.validate(
            categories = categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = listOf(packageData),
            globalConstraints = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "PROFILE_ATTRIBUTES_EMPTY_NOT_ALLOWED" })
    }

    @Test
    fun missing_profile_is_reported() {
        val categories = listOf(
            Category(
                code = "TECH",
                segment = CategorySegment.TECH,
                title = localizedTextOf("ru" to "TECH"),
                parentCode = null,
            ),
            Category(
                code = "TECH.PHONES",
                segment = CategorySegment.TECH,
                title = localizedTextOf("ru" to "TECH.PHONES"),
                parentCode = "TECH",
            ),
        )
        val packageData = Stage22PackageData(
            descriptor = Stage22PackageDescriptor(
                l0Code = "TECH",
                basePath = "taxonomy/stage2/2.2/TECH",
            ),
            profiles = emptyList(),
            constraints = emptyList(),
            valueDicts = emptyList(),
        )

        val report = validator.validate(
            categories = categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = listOf(packageData),
            globalConstraints = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "PROFILE_CATEGORY_MISSING" })
    }

    @Test
    fun closed_set_constraints_must_use_value_codes_only() {
        val sourcePackage = GenericStage22PackageLoader.loadAll()
            .first { it.descriptor.l0Code == "TECH" }
        val brokenPackage = sourcePackage.copy(
            constraints = sourcePackage.constraints + CatalogConstraints(
                scope = ConstraintScope.CATEGORY,
                categoryCode = "TECH.PHONES",
                attributeConstraints = listOf(
                    AttributeValueConstraint(
                        attributeCode = "color",
                        allowedValues = listOf("Черный"),
                    ),
                ),
            ),
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = listOf(brokenPackage),
            globalConstraints = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "REFERENTIAL_VALUE_NOT_VALUE_CODE" })
    }

    @Test
    fun invalid_constraint_effective_window_is_reported() {
        val sourcePackage = GenericStage22PackageLoader.loadAll()
            .first { it.descriptor.l0Code == "TECH" }
        val brokenPackage = sourcePackage.copy(
            constraints = sourcePackage.constraints + CatalogConstraints(
                scope = ConstraintScope.CATEGORY,
                categoryCode = "TECH.PHONES",
                effectiveFrom = "2026-12-31",
                effectiveTo = "2026-01-01",
                attributeConstraints = emptyList(),
            ),
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = listOf(brokenPackage),
            globalConstraints = emptyList(),
        )

        assertTrue(report.issues.any { it.code == "REFERENTIAL_CONSTRAINT_EFFECTIVE_WINDOW_INVALID" })
    }
}
