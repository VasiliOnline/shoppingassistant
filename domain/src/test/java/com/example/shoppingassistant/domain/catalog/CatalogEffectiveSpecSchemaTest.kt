package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
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

class CatalogEffectiveSpecSchemaTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val specsByCode = CatalogSeed.categoryWriteSpecs.associateBy { it.category.code }

    @Test
    fun effective_spec_schema_resource_must_be_valid_json() {
        val schema = parseSchema()
        assertTrue("Schema must declare object root type.", schema["type"]?.jsonPrimitive?.content == "object")
        assertTrue(
            "Schema must expose required top-level fields.",
            schema.requiredKeys().containsAll(
                listOf("category", "readiness", "attributes", "systemAttributes", "requiredIfRules", "constraints", "meta"),
            ),
        )
    }

    @Test
    fun active_leaf_effective_specs_must_validate_against_formal_schema() {
        val schema = parseSchema()
        val failures = leafCategoryCodes(CatalogSeed.categories)
            .mapNotNull { categoryCode ->
                val spec = buildEffectiveSpec(categoryCode) ?: return@mapNotNull "$categoryCode:missing_spec"
                val payload = json.parseToJsonElement(
                    json.encodeToString(CatalogCategoryEffectiveSpec.serializer(), spec),
                )
                val errors = validate(
                    element = payload,
                    schema = schema,
                    root = schema,
                    path = "$",
                )
                if (errors.isEmpty()) {
                    null
                } else {
                    "$categoryCode:${errors.take(3).joinToString(" | ")}"
                }
            }

        assertTrue(
            "Effective spec schema violations detected: ${failures.take(10)}",
            failures.isEmpty(),
        )
    }

    private fun buildEffectiveSpec(categoryCode: String): CatalogCategoryEffectiveSpec? {
        val spec = specsByCode[categoryCode] ?: return null
        val constraints = scopedConstraints(categoryCode)
        return spec.toCategoryEffectiveSpec(constraints = constraints)
    }

    private fun scopedConstraints(categoryCode: String): List<CatalogConstraints> =
        CatalogSeed.constraints.filter { constraint ->
            when (constraint.scope) {
                ConstraintScope.GLOBAL -> true
                ConstraintScope.CATEGORY ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true
                ConstraintScope.BRAND,
                ConstraintScope.MODEL,
                    -> false
            }
        }

    private fun parseSchema(): JsonObject =
        json.parseToJsonElement(
            CatalogSeedResourceReader.readText(SCHEMA_RESOURCE_PATH),
        ).jsonObject

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
            "taxonomy/stage2/2.2/_registry/catalog_category_effective_spec.schema.json"
    }
}

private val JsonPrimitive.contentOrNull: String?
    get() = content.takeIf { it.isNotBlank() }

private fun leafCategoryCodes(categories: List<Category>): List<String> {
    val parentCodes = categories
        .mapNotNull { category -> category.parentCode?.trim()?.takeIf(String::isNotBlank) }
        .toSet()
    return categories
        .asSequence()
        .filter { category ->
            category.status == CategoryStatus.ACTIVE &&
                category.code.trim().isNotEmpty() &&
                category.code !in parentCodes
        }
        .map { category -> category.code }
        .sorted()
        .toList()
}
