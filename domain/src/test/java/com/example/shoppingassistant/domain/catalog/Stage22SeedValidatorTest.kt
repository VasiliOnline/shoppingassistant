package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
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
}
