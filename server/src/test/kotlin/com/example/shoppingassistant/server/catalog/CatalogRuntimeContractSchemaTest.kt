package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRuntimeContractSchemaTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun runtime_contract_schema_resource_must_expose_required_defs() {
        val schema = parseSchema()
        val defs = schema["\$defs"]?.jsonObject ?: error("Schema defs missing.")
        assertTrue(defs.containsKey("versionResponse"))
        assertTrue(defs.containsKey("inventoryResponse"))
        assertTrue(defs.containsKey("historyResponse"))
        assertTrue(defs.containsKey("governanceResponse"))
        assertTrue(defs.containsKey("governanceReportsResponse"))
        assertTrue(defs.containsKey("runtimeCompatibilityResponse"))
        assertTrue(defs.containsKey("liveValuesSnapshot"))
    }

    @Test
    fun sample_runtime_responses_must_validate_against_formal_schema() {
        assertSchemaValid(
            schemaDef = "versionResponse",
            serializer = CatalogVersionResponse.serializer(),
            value = CatalogVersionResponse(
                schemaVersion = CatalogSchemaVersion.current,
                dataVersion = CatalogDataVersion.current,
                minSupportedClientSchemaVersion = CatalogSchemaVersion.minSupportedClient,
            ),
        )
        assertSchemaValid(
            schemaDef = "inventoryResponse",
            serializer = ListSerializer(CatalogReadinessInventoryItem.serializer()),
            value = listOf(sampleInventoryItem()),
        )
        assertSchemaValid(
            schemaDef = "historyResponse",
            serializer = CatalogReadinessHistoryResponse.serializer(),
            value = CatalogReadinessHistoryResponse(
                category = sampleCategory(),
                requestedDays = 30,
                operationalWindowDays = 14,
                points = listOf(
                    CatalogReadinessHistoryPoint(
                        windowEndDate = "2026-03-10",
                        readiness = CatalogCategoryReadiness.READY,
                        editorialReadiness = CatalogCategoryReadiness.READY,
                        operationalReadiness = CatalogCategoryReadiness.READY,
                        blockingIssues = emptyList(),
                        editorialBlockingIssues = emptyList(),
                        operationalBlockingIssues = emptyList(),
                        operationalSampleCount = 12,
                        operationalDroppedRate = 0.0,
                        operationalUnknownAttributeRate = 0.0,
                        operationalRequiredMissingRate = 0.0,
                        operationalLowConfidenceRate = 0.0,
                    ),
                ),
            ),
        )
        assertSchemaValid(
            schemaDef = "governanceResponse",
            serializer = CatalogReadinessGovernanceResponse.serializer(),
            value = CatalogReadinessGovernanceServiceImpl().getGovernance(),
        )
        assertSchemaValid(
            schemaDef = "governanceReportsResponse",
            serializer = ListSerializer(CatalogGovernanceReportResponse.serializer()),
            value = listOf(
                CatalogGovernanceReportResponse(
                    reportType = "WEEKLY_SUMMARY",
                    reportDate = "2026-03-10",
                    generatedAt = "2026-03-10T09:31:00Z",
                    windowStartDate = "2026-03-04",
                    windowEndDate = "2026-03-10",
                    totalCategories = 12,
                    readyCategories = 10,
                    betaCategories = 2,
                    internalCategories = 0,
                    categoriesWithBlockingIssues = listOf("APPL.SMALL", "TECH.PHONES"),
                    dataVersion = CatalogDataVersion.current,
                    schemaVersion = CatalogSchemaVersion.current,
                ),
            ),
        )
        assertSchemaValid(
            schemaDef = "runtimeCompatibilityResponse",
            serializer = CatalogRuntimeCompatibilityResponse.serializer(),
            value = CatalogRuntimeCompatibilityServiceImpl().getRuntimeCompatibility(),
        )
        assertSchemaValid(
            schemaDef = "liveValuesSnapshot",
            serializer = com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot.serializer(),
            value = com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot(
                valuesByAttributeCode = linkedMapOf(
                    "color" to listOf("Black", "Blue"),
                    "storage" to listOf("128GB"),
                ),
                knownValuesByAttributeCode = linkedMapOf(
                    "color" to listOf("Black", "Blue", "Cosmic Blue"),
                ),
                knownValueAliasesByAttributeCode = linkedMapOf(
                    "color" to linkedMapOf(
                        "Cosmic Blue" to listOf("cosmic blue", "космический синий"),
                    ),
                ),
                brandOptions = listOf("Apple"),
                modelOptions = listOf("iPhone 16 Pro"),
            ),
        )
    }

    private fun sampleInventoryItem(): CatalogReadinessInventoryItem =
        CatalogReadinessInventoryItem(
            category = sampleCategory(),
            readiness = CatalogCategoryReadiness.READY,
            editorialReadiness = CatalogCategoryReadiness.READY,
            operationalReadiness = CatalogCategoryReadiness.READY,
            completenessGatePassed = true,
            blockingIssues = emptyList(),
            editorialBlockingIssues = emptyList(),
            operationalBlockingIssues = emptyList(),
            operationalSampleCount = 12,
            operationalDroppedRate = 0.0,
            operationalUnknownAttributeRate = 0.0,
            operationalRequiredMissingRate = 0.0,
            operationalLowConfidenceRate = 0.0,
        )

    private fun sampleCategory(): Category =
        Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Смартфоны", "en" to "Phones"),
            parentCode = "TECH",
            description = "Phones and smartphones.",
            status = CategoryStatus.ACTIVE,
            replacementCode = null,
        )

    private fun <T> assertSchemaValid(
        schemaDef: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        val root = parseSchema()
        val schema = root.schemaDef(schemaDef)
        val payload = json.parseToJsonElement(json.encodeToString(serializer, value))
        val errors = validate(
            element = payload,
            schema = schema,
            root = root,
            path = "$",
        )
        assertTrue(
            "Schema violations for $schemaDef: ${errors.take(5)}",
            errors.isEmpty(),
        )
    }

    private fun parseSchema(): JsonObject =
        json.parseToJsonElement(
            javaClass.classLoader
                .getResourceAsStream(SCHEMA_RESOURCE_PATH)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("Schema resource '$SCHEMA_RESOURCE_PATH' not found."),
        ).jsonObject

    private fun JsonObject.schemaDef(name: String): JsonObject =
        (this["\$defs"]?.jsonObject ?: error("Schema defs missing."))[name]?.jsonObject
            ?: error("Schema def '$name' missing.")

    private fun validate(
        element: JsonElement,
        schema: JsonObject,
        root: JsonObject,
        path: String,
    ): List<String> {
        val ref = schema["\$ref"]?.jsonPrimitive?.contentOrNull
        if (ref != null) {
            return validate(
                element = element,
                schema = resolveRef(ref, root),
                root = root,
                path = path,
            )
        }

        val errors = mutableListOf<String>()
        val allowedTypes = schema.allowedTypes()
        if (allowedTypes.isNotEmpty() && allowedTypes.none { type -> matchesType(element, type) }) {
            errors += "$path:type expected=${allowedTypes.joinToString("|")} actual=${describeType(element)}"
            return errors
        }

        val enumValues = schema["enum"] as? JsonArray
        if (enumValues != null) {
            val actual = (element as? JsonPrimitive)?.contentOrNull
            val allowed = enumValues.map { it.jsonPrimitive.content }
            if (actual == null || actual !in allowed) {
                errors += "$path:enum expected=${allowed.joinToString("|")} actual=${actual.orEmpty()}"
                return errors
            }
        }

        when (element) {
            is JsonObject -> {
                val requiredKeys = schema.requiredKeys()
                requiredKeys.filterNot(element::containsKey).forEach { key ->
                    errors += "$path.$key:missing"
                }

                val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
                properties.forEach { (key, propertySchema) ->
                    val value = element[key] ?: return@forEach
                    errors += validate(
                        element = value,
                        schema = propertySchema.jsonObject,
                        root = root,
                        path = "$path.$key",
                    )
                }

                val additionalProperties = schema["additionalProperties"]
                val unknownKeys = element.keys - properties.keys
                when {
                    additionalProperties is JsonPrimitive && additionalProperties.booleanOrNull == false -> {
                        unknownKeys.forEach { key -> errors += "$path.$key:unexpected" }
                    }

                    additionalProperties is JsonObject -> {
                        unknownKeys.forEach { key ->
                            errors += validate(
                                element = element.getValue(key),
                                schema = additionalProperties,
                                root = root,
                                path = "$path.$key",
                            )
                        }
                    }
                }
            }

            is JsonArray -> {
                val itemSchema = schema["items"] as? JsonObject
                if (itemSchema != null) {
                    element.forEachIndexed { index, item ->
                        errors += validate(
                            element = item,
                            schema = itemSchema,
                            root = root,
                            path = "$path[$index]",
                        )
                    }
                }
            }

            else -> Unit
        }

        return errors
    }

    private fun resolveRef(ref: String, root: JsonObject): JsonObject {
        require(ref.startsWith("#/")) { "Unsupported schema ref '$ref'" }
        val segments = ref.removePrefix("#/").split('/')
        var current: JsonElement = root
        segments.forEach { segment ->
            current = (current as JsonObject)[segment]
                ?: error("Schema ref '$ref' segment '$segment' not found.")
        }
        return current.jsonObject
    }

    private fun JsonObject.allowedTypes(): Set<String> {
        val typeElement = this["type"] ?: return emptySet()
        return when (typeElement) {
            is JsonPrimitive -> setOf(typeElement.content)
            is JsonArray -> typeElement.map { it.jsonPrimitive.content }.toSet()
            else -> emptySet()
        }
    }

    private fun JsonObject.requiredKeys(): Set<String> =
        (this["required"] as? JsonArray)
            ?.map { it.jsonPrimitive.content }
            ?.toSet()
            .orEmpty()

    private fun matchesType(element: JsonElement, type: String): Boolean = when (type) {
        "object" -> element is JsonObject
        "array" -> element is JsonArray
        "string" -> (element as? JsonPrimitive)?.isString == true
        "integer" -> {
            val primitive = element as? JsonPrimitive ?: return false
            !primitive.isString && primitive.longOrNull != null
        }

        "number" -> {
            val primitive = element as? JsonPrimitive ?: return false
            !primitive.isString && primitive.doubleOrNull != null
        }

        "boolean" -> {
            val primitive = element as? JsonPrimitive ?: return false
            !primitive.isString && primitive.booleanOrNull != null
        }

        "null" -> element is JsonNull
        else -> false
    }

    private fun describeType(element: JsonElement): String = when (element) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            element.longOrNull != null -> "integer"
            element.doubleOrNull != null -> "number"
            else -> "primitive"
        }
    }

    private companion object {
        private const val SCHEMA_RESOURCE_PATH =
            "taxonomy/stage2/2.2/_registry/catalog_runtime_contract.schema.json"
    }
}

private val JsonPrimitive.contentOrNull: String?
    get() = content.takeIf { it.isNotBlank() }
