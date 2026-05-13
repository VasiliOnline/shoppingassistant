package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage22EffectiveSpecEngineTest {
    @Test
    fun returns_effective_spec_for_existing_profile() {
        val categories = testCategories()
        val registry = testRegistry()
        val packageData = Stage22PackageData(
            descriptor = Stage22PackageDescriptor(
                l0Code = "TECH",
                basePath = "taxonomy/stage2/2.2/TECH",
            ),
            profiles = listOf(
                resourceProfile(
                    categoryCode = "TECH.PHONES",
                    attributes = listOf(
                        AttributeDef(
                            code = "brand",
                            title = "Бренд",
                            dataType = AttributeDataType.STRING,
                            requiredForOffer = true,
                            facetEnabled = true,
                        ),
                        AttributeDef(
                            code = "color",
                            title = "Цвет",
                            dataType = AttributeDataType.ENUM,
                            facetEnabled = true,
                            valueDictCode = "color",
                        ),
                    ),
                    categoryAttributes = listOf(
                        CategoryAttribute("TECH.PHONES", "brand", uiOrder = 1, isRequiredForCategory = true),
                        CategoryAttribute("TECH.PHONES", "color", uiOrder = 2, isRequiredForCategory = false),
                    ),
                ),
            ),
            constraints = listOf(
                CatalogConstraints(
                    scope = ConstraintScope.CATEGORY,
                    categoryCode = "TECH.PHONES",
                    attributeConstraints = listOf(
                        AttributeValueConstraint(
                            attributeCode = "color",
                            forbiddenValues = listOf("WHITE"),
                        ),
                    ),
                ),
            ),
            valueDicts = emptyList(),
        )
        val globalConstraints = listOf(
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

        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = categories,
            registry = registry,
            packages = listOf(packageData),
            globalConstraints = globalConstraints,
        )

        val spec = engine.getEffectiveSpec("TECH.PHONES")
        assertFalse(spec.meta.isFallback)
        assertTrue(spec.attributes.any { it.attributeCode == "brand" })
        assertTrue(spec.attributes.any { it.attributeCode == "color" })
        assertEquals(listOf("BLACK"), spec.constraints.allowedValueCodesByAttribute["color"])
        assertEquals(listOf("WHITE"), spec.constraints.forbiddenValueCodesByAttribute["color"])
    }

    @Test
    fun missing_profile_throws() {
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = testCategories(),
            registry = testRegistry(),
            packages = emptyList(),
            globalConstraints = emptyList(),
        )

        var thrown = false
        try {
            engine.getEffectiveSpec("TECH.PHONES")
        } catch (_: IllegalStateException) {
            thrown = true
        }
        assertTrue("Missing category profile must throw", thrown)
    }

    @Test
    fun constraints_conflict_throws() {
        val categories = testCategories()
        val registry = testRegistry()
        val packageData = Stage22PackageData(
            descriptor = Stage22PackageDescriptor(
                l0Code = "TECH",
                basePath = "taxonomy/stage2/2.2/TECH",
            ),
            profiles = listOf(
                resourceProfile(
                    categoryCode = "TECH.PHONES",
                    attributes = listOf(
                        AttributeDef(
                            code = "color",
                            title = "Цвет",
                            dataType = AttributeDataType.ENUM,
                            facetEnabled = true,
                            valueDictCode = "color",
                        ),
                    ),
                    categoryAttributes = listOf(
                        CategoryAttribute("TECH.PHONES", "color", uiOrder = 1, isRequiredForCategory = true),
                    ),
                ),
            ),
            constraints = listOf(
                CatalogConstraints(
                    scope = ConstraintScope.CATEGORY,
                    categoryCode = "TECH.PHONES",
                    attributeConstraints = listOf(
                        AttributeValueConstraint(
                            attributeCode = "color",
                            forbiddenValues = listOf("BLACK"),
                        ),
                    ),
                ),
            ),
            valueDicts = emptyList(),
        )
        val globalConstraints = listOf(
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
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = categories,
            registry = registry,
            packages = listOf(packageData),
            globalConstraints = globalConstraints,
        )

        var thrown = false
        try {
            engine.getEffectiveSpec("TECH.PHONES")
        } catch (_: IllegalStateException) {
            thrown = true
        }
        assertTrue("Conflicting constraints must throw", thrown)
    }

    @Test
    fun determinism_is_independent_of_package_order() {
        val categories = testCategories()
        val registry = testRegistry()
        val techPackage = Stage22PackageData(
            descriptor = Stage22PackageDescriptor("TECH", "taxonomy/stage2/2.2/TECH"),
            profiles = listOf(
                resourceProfile(
                    categoryCode = "TECH.PHONES",
                    attributes = listOf(
                        AttributeDef(code = "brand", title = "Бренд", dataType = AttributeDataType.STRING),
                        AttributeDef(code = "model", title = "Модель", dataType = AttributeDataType.STRING),
                    ),
                    categoryAttributes = listOf(
                        CategoryAttribute("TECH.PHONES", "brand", uiOrder = 1, isRequiredForCategory = true),
                        CategoryAttribute("TECH.PHONES", "model", uiOrder = 2, isRequiredForCategory = true),
                    ),
                ),
            ),
            constraints = emptyList(),
            valueDicts = emptyList(),
        )
        val homePackage = Stage22PackageData(
            descriptor = Stage22PackageDescriptor("HOME", "taxonomy/stage2/2.2/HOME"),
            profiles = emptyList(),
            constraints = emptyList(),
            valueDicts = emptyList(),
        )

        val engineA = Stage22EffectiveSpecEngine.fromSeed(
            categories = categories,
            registry = registry,
            packages = listOf(techPackage, homePackage),
            globalConstraints = emptyList(),
        )
        val engineB = Stage22EffectiveSpecEngine.fromSeed(
            categories = categories,
            registry = registry,
            packages = listOf(homePackage, techPackage),
            globalConstraints = emptyList(),
        )

        val specA = engineA.getEffectiveSpec("TECH.PHONES")
        val specB = engineB.getEffectiveSpec("TECH.PHONES")
        assertEquals(specA, specB)
    }

    @Test
    fun cache_avoids_recomputing_same_category() {
        val packageData = Stage22PackageData(
            descriptor = Stage22PackageDescriptor(
                l0Code = "TECH",
                basePath = "taxonomy/stage2/2.2/TECH",
            ),
            profiles = listOf(
                resourceProfile(
                    categoryCode = "TECH.PHONES",
                    attributes = listOf(
                        AttributeDef(
                            code = "brand",
                            title = "Бренд",
                            dataType = AttributeDataType.STRING,
                            requiredForOffer = true,
                            facetEnabled = true,
                        ),
                    ),
                    categoryAttributes = listOf(
                        CategoryAttribute("TECH.PHONES", "brand", uiOrder = 1, isRequiredForCategory = true),
                    ),
                ),
            ),
            constraints = emptyList(),
            valueDicts = emptyList(),
        )
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = testCategories(),
            registry = testRegistry(),
            packages = listOf(packageData),
            globalConstraints = emptyList(),
        )

        engine.getEffectiveSpec("TECH.PHONES")
        engine.getEffectiveSpec("TECH.PHONES")

        assertEquals(1, engine.cacheSize())
        assertEquals(1, engine.computedSpecCount())
    }

    private fun testCategories(): List<Category> = listOf(
        Category(
            code = "TECH",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Электроника"),
            parentCode = null,
        ),
        Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Смартфоны"),
            parentCode = "TECH",
        ),
    )

    private fun testRegistry(): Stage22RegistrySnapshot {
        val attributes = linkedMapOf(
            "brand" to Stage22AttributeDef(
                attributeCode = "brand",
                valueType = Stage22ValueType.STRING,
                valueSetType = Stage22ValueSetType.OPEN,
                isIdentity = true,
                isFacet = true,
                labels = mapOf("ru" to "Бренд", "en" to "Brand"),
            ),
            "model" to Stage22AttributeDef(
                attributeCode = "model",
                valueType = Stage22ValueType.STRING,
                valueSetType = Stage22ValueSetType.OPEN,
                isIdentity = true,
                isFacet = true,
                labels = mapOf("ru" to "Модель", "en" to "Model"),
            ),
            "product_name" to Stage22AttributeDef(
                attributeCode = "product_name",
                valueType = Stage22ValueType.STRING,
                valueSetType = Stage22ValueSetType.OPEN,
                isIdentity = true,
                isFacet = false,
                labels = mapOf("ru" to "Название", "en" to "Product name"),
            ),
            "color" to Stage22AttributeDef(
                attributeCode = "color",
                valueType = Stage22ValueType.ENUM,
                valueSetType = Stage22ValueSetType.CLOSED,
                isIdentity = false,
                isFacet = true,
                labels = mapOf("ru" to "Цвет", "en" to "Color"),
            ),
        )
        val dictionaries = linkedMapOf(
            "color" to Stage22ValueDictionary(
                attributeCode = "color",
                entries = listOf(
                    Stage22ValueDictionaryEntry(
                        valueCode = "BLACK",
                        labels = mapOf("ru" to "Черный", "en" to "Black"),
                        aliases = listOf("black", "черный"),
                    ),
                    Stage22ValueDictionaryEntry(
                        valueCode = "WHITE",
                        labels = mapOf("ru" to "Белый", "en" to "White"),
                        aliases = listOf("white", "белый"),
                    ),
                ),
            ),
        )
        return Stage22RegistrySnapshot(
            attributes = attributes,
            dictionaries = dictionaries,
            meta = Stage22RegistryMeta(
                schemaVersion = "1.0.0",
                dataVersion = "2.2.0",
                generatedAt = "2026-02-15T00:00:00Z",
            ),
        )
    }

    private fun resourceProfile(
        categoryCode: String,
        attributes: List<AttributeDef>,
        categoryAttributes: List<CategoryAttribute>,
    ): Stage22ResourceProfile = Stage22ResourceProfile(
        category = Stage22SeedCategoryRef(
            code = categoryCode,
            segment = CategorySegment.valueOf(categoryCode.substringBefore('.')),
            title = categoryCode,
            parentCode = categoryCode.substringBefore('.', missingDelimiterValue = "").ifBlank { null },
        ),
        attributes = attributes,
        categoryAttributes = categoryAttributes,
        valueDictionaries = emptyList(),
        requiredIfRules = emptyList(),
    )
}
