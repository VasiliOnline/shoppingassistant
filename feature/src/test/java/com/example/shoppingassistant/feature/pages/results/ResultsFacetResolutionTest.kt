package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.ValueFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsFacetResolutionTest {

    @Test
    fun excludes_requested_raw_value_when_seed_live_values_do_not_contain_it() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 16",
                attributes = mapOf("color" to TypedAttributeValue.Text("Orange")),
            ),
            rawQueryText = "iphone 16 orange",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(enumFacet("color")),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "color" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "color",
                        title = "Цвет",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("Black", "White", "Orange"),
                        seedAvailableValues = listOf("Black", "White"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertTrue(resolution.effectiveTypedAttributeFilters.isEmpty())
        assertEquals("Orange", resolution.noticesByRuntimeKey["color"]?.requestedValue)
        assertEquals(listOf("Black", "White"), resolution.noticesByRuntimeKey["color"]?.availableValues)
    }

    @Test
    fun relaxes_query_backed_value_when_runtime_scope_excludes_it() {
        val conflicting = collectRuntimeRelaxedQueryTypedFacetKeys(
            queryBackedTypedAttributeFilters = mapOf(
                "color" to TypedAttributeFilterDraft(
                    op = com.example.shoppingassistant.domain.model.TypedAttributeOperator.EQ,
                    value = "White",
                ),
            ),
            runtimeAttributeFacets = mapOf(
                "color" to listOf(
                    ValueFacet(id = "black", name = "Black", count = 4),
                ),
            ),
        )

        assertEquals(setOf("color"), conflicting)
    }

    @Test
    fun auto_applies_runtime_singleton_when_attribute_not_requested() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 16",
            ),
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(enumFacet("os")),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "os" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "os",
                        title = "ОС",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iOS", "Android"),
                        seedAvailableValues = listOf("iOS"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
            runtimeAttributeFacets = mapOf(
                "os" to listOf(ValueFacet(id = "ios", name = "iOS", count = 8)),
            ),
        )

        assertEquals("iOS", resolution.autoAppliedTypedAttributeFilters["os"]?.value)
        assertEquals("iOS", resolution.effectiveTypedAttributeFilters["os"]?.value)
    }

    @Test
    fun requested_attribute_blocks_singleton_auto_apply_for_same_facet() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 16",
                attributes = mapOf("os" to TypedAttributeValue.Text("Android")),
            ),
            rawQueryText = "iphone 16 android",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(enumFacet("os")),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "os" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "os",
                        title = "ОС",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iOS", "Android"),
                        seedAvailableValues = listOf("iOS"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
            runtimeAttributeFacets = mapOf(
                "os" to listOf(ValueFacet(id = "ios", name = "iOS", count = 8)),
            ),
        )

        assertFalse(resolution.autoAppliedTypedAttributeFilters.containsKey("os"))
        assertEquals("Android", resolution.noticesByRuntimeKey["os"]?.requestedValue)
    }

    @Test
    fun valid_query_model_becomes_query_backed_typed_filter() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iphone 16",
            ),
            rawQueryText = "apple iphone 16",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(enumFacet("model")),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "model" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "model",
                        title = "Модель",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iPhone 15", "iPhone 16"),
                        knownValueAliases = mapOf(
                            "iPhone 16" to listOf("iphone 16", "айфон 16"),
                        ),
                        seedAvailableValues = listOf("iPhone 16"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertEquals("iPhone 16", resolution.queryBackedTypedAttributeFilters["model"]?.value)
        assertEquals("iPhone 16", resolution.effectiveTypedAttributeFilters["model"]?.value)
        assertTrue(resolution.noticesByRuntimeKey.isEmpty())
    }

    @Test
    fun valid_query_model_and_attributes_become_query_backed_typed_filters() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iphone 17 pro",
                attributes = mapOf(
                    "memory_gb" to TypedAttributeValue.Text("512"),
                    "color" to TypedAttributeValue.Text("orange"),
                ),
            ),
            rawQueryText = "apple iphone 17 pro orange 512gb",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(
                enumFacet("model"),
                enumFacet("memory_gb"),
                enumFacet("color"),
            ),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "model" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "model",
                        title = "Модель",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iPhone 16", "iPhone 17 Pro"),
                        knownValueAliases = mapOf(
                            "iPhone 17 Pro" to listOf("iphone 17 pro", "айфон 17 про"),
                        ),
                        seedAvailableValues = listOf("iPhone 17 Pro"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "memory_gb" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "memory_gb",
                        title = "Память (встроенная)",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("128", "256", "512"),
                        seedAvailableValues = listOf("128", "256", "512"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "color" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "color",
                        title = "Цвет",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("Silver", "Cosmic Orange", "Deep Blue"),
                        knownValueAliases = mapOf(
                            "Cosmic Orange" to listOf("orange", "cosmic orange", "оранжевый"),
                        ),
                        seedAvailableValues = listOf("Silver", "Cosmic Orange", "Deep Blue"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertEquals("iPhone 17 Pro", resolution.queryBackedTypedAttributeFilters["model"]?.value)
        assertEquals("512", resolution.queryBackedTypedAttributeFilters["memory_gb"]?.value)
        assertEquals("Cosmic Orange", resolution.queryBackedTypedAttributeFilters["color"]?.value)
        assertTrue(resolution.noticesByRuntimeKey.isEmpty())
    }

    @Test
    fun suppresses_notice_for_inferred_value_not_present_in_raw_query() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 18 Pro",
                attributes = mapOf("os" to TypedAttributeValue.Text("iOS")),
            ),
            rawQueryText = "apple iphone 18 pro белый 512гб",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(enumFacet("os")),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "os" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "os",
                        title = "ОС",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iOS", "Android"),
                        knownValueAliases = mapOf(
                            "iOS" to listOf("iOS", "айос"),
                            "Android" to listOf("Android", "андроид"),
                        ),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertTrue(resolution.effectiveTypedAttributeFilters.isEmpty())
        assertTrue(resolution.noticesByRuntimeKey.isEmpty())
    }

    @Test
    fun keeps_notice_when_raw_query_mentions_alias_of_requested_value() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 18 Pro",
                attributes = mapOf("color" to TypedAttributeValue.Text("White")),
            ),
            rawQueryText = "apple iphone 18 pro белый 512гб",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(enumFacet("color")),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "color" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "color",
                        title = "Цвет",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("White", "Black"),
                        knownValueAliases = mapOf(
                            "White" to listOf("White", "белый"),
                            "Black" to listOf("Black", "черный"),
                        ),
                        seedAvailableValues = listOf("Black"),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertTrue(resolution.effectiveTypedAttributeFilters.isEmpty())
        assertEquals("White", resolution.noticesByRuntimeKey["color"]?.requestedValue)
        assertEquals(listOf("Black"), resolution.noticesByRuntimeKey["color"]?.availableValues)
    }

    @Test
    fun unresolved_model_from_raw_query_becomes_primary_notice_and_suppresses_memory_notice() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "",
                attributes = mapOf("memory_gb" to TypedAttributeValue.Text("512")),
            ),
            rawQueryText = "Apple iPhone 18 про белый 512гб",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(
                enumFacet("model"),
                enumFacet("memory_gb"),
            ),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "model" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "model",
                        title = "Модель",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf(
                            "iPhone 13",
                            "iPhone 14",
                            "iPhone 15",
                            "iPhone 16",
                        ),
                        knownValueAliases = mapOf(
                            "iPhone 16" to listOf("iphone 16", "айфон 16"),
                        ),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "memory_gb" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "memory_gb",
                        title = "Память (встроенная)",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("64", "128", "256", "512", "1024"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertEquals(setOf("model"), resolution.noticesByRuntimeKey.keys)
        assertEquals("Модель", resolution.noticesByRuntimeKey["model"]?.title)
        assertEquals(ResultsTypedFacetNoticeReason.UnknownInCatalog, resolution.noticesByRuntimeKey["model"]?.reason)
        assertTrue(resolution.effectiveTypedAttributeFilters.isEmpty())
    }

    @Test
    fun unknown_model_notice_outranks_secondary_known_attribute_notice() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 18 Pro белый 512гб",
                attributes = mapOf("memory_gb" to TypedAttributeValue.Text("512")),
            ),
            rawQueryText = "Apple iPhone 18 про белый 512гб",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(
                enumFacet("model"),
                enumFacet("memory_gb"),
            ),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "model" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "model",
                        title = "Модель",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf(
                            "iPhone 13",
                            "iPhone 14",
                            "iPhone 15",
                            "iPhone 16",
                        ),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "memory_gb" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "memory_gb",
                        title = "Память (встроенная)",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("64", "128", "256", "512", "1024"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertEquals(setOf("model"), resolution.noticesByRuntimeKey.keys)
        assertFalse(resolution.noticesByRuntimeKey.containsKey("memory_gb"))
    }

    @Test
    fun branch_priority_keeps_only_highest_priority_attribute_notice() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 16",
                attributes = mapOf(
                    "color" to TypedAttributeValue.Text("White"),
                    "memory_gb" to TypedAttributeValue.Text("512"),
                ),
            ),
            rawQueryText = "iphone 16 white 512gb",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(
                enumFacet("color"),
                enumFacet("memory_gb"),
            ),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "color" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "color",
                        title = "Цвет",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("White", "Black"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "memory_gb" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "memory_gb",
                        title = "Память (встроенная)",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("128", "256", "512"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
            noticePriorityTypedFacetKeys = listOf("memory_gb", "color"),
        )

        assertEquals(setOf("memory_gb"), resolution.noticesByRuntimeKey.keys)
        assertFalse(resolution.noticesByRuntimeKey.containsKey("color"))
    }

    @Test
    fun model_notice_sanitizes_brand_and_attribute_tail_from_english_query() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "Apple iPhone 18 pro white 256gb",
                attributes = mapOf(
                    "color" to TypedAttributeValue.Text("White"),
                    "memory_gb" to TypedAttributeValue.Text("256"),
                ),
            ),
            rawQueryText = "Apple iPhone 18 pro white 256gb",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(
                enumFacet("model"),
                enumFacet("color"),
                enumFacet("memory_gb"),
            ),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "model" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "model",
                        title = "Модель",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iPhone 13", "iPhone 14", "iPhone 15", "iPhone 16"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "color" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "color",
                        title = "Цвет",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("White", "Black"),
                        knownValueAliases = mapOf(
                            "White" to listOf("white", "белый"),
                            "Black" to listOf("black", "черный"),
                        ),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "memory_gb" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "memory_gb",
                        title = "Память (встроенная)",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("128", "256", "512"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertEquals("iPhone 18 pro", resolution.noticesByRuntimeKey["model"]?.requestedValue)
    }

    @Test
    fun model_notice_sanitizes_attribute_tail_from_russian_query() {
        val resolution = resolveResultsFacetResolution(
            query = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 18 про белый 512гб",
                attributes = mapOf(
                    "color" to TypedAttributeValue.Text("White"),
                    "memory_gb" to TypedAttributeValue.Text("512"),
                ),
            ),
            rawQueryText = "Apple iPhone 18 про белый 512гб",
            presetAttributes = emptyMap(),
            explicitTypedAttributeFilters = emptyMap(),
            facetDefinitions = listOf(
                enumFacet("model"),
                enumFacet("color"),
                enumFacet("memory_gb"),
            ),
            typedFacetUniverse = ResultsTypedFacetUniverse(
                entriesByRuntimeKey = mapOf(
                    "model" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "model",
                        title = "Модель",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("iPhone 13", "iPhone 14", "iPhone 15", "iPhone 16"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "color" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "color",
                        title = "Цвет",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("White", "Black"),
                        knownValueAliases = mapOf(
                            "White" to listOf("white", "белый"),
                            "Black" to listOf("black", "черный"),
                        ),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                    "memory_gb" to ResultsTypedFacetUniverseEntry(
                        runtimeKey = "memory_gb",
                        title = "Память (встроенная)",
                        valueType = FacetDataType.ENUM,
                        knownValues = listOf("128", "256", "512"),
                        seedAvailableValues = emptyList(),
                        seedAvailabilityKnown = true,
                        isClosedSet = true,
                    ),
                ),
            ),
        )

        assertEquals("iPhone 18 про", resolution.noticesByRuntimeKey["model"]?.requestedValue)
    }

    private fun enumFacet(attributeCode: String): FacetDefinition = FacetDefinition(
        facetKey = attributeCode,
        title = localizedTextOf("ru" to attributeCode),
        valueType = FacetDataType.ENUM,
        appliesToCategoryCodes = listOf("TECH.PHONES"),
        attributeCode = attributeCode,
    )
}
