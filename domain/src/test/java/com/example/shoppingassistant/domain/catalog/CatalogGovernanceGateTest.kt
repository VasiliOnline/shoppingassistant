package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CatalogGovernanceGateTest {
    @Test
    fun governance_artifacts_must_include_current_data_version_and_policy() {
        val meta = Stage22RegistryLoader.loadSnapshot().meta

        val policy = parseObject(POLICY_PATH)
        val releaseCadence = policy.value("releaseCadence")
        val owner = policy.value("owner")
        assertTrue("Governance releaseCadence must be set.", releaseCadence.isNotBlank())
        assertTrue("Governance owner must be set.", owner.isNotBlank())

        val deprecation = policy.objectValue("deprecation")
        val announceBeforeDays = deprecation.value("announceBeforeDays").toIntOrNull() ?: -1
        val removeAfterDays = deprecation.value("removeAfterDays").toIntOrNull() ?: -1
        assertTrue(
            "deprecation.announceBeforeDays/removeAfterDays must be positive and ordered.",
            announceBeforeDays > 0 && removeAfterDays > announceBeforeDays,
        )

        val requiredArtifacts = policy.arrayValue("requiredArtifacts")
            .map { it.jsonPrimitive.content.trim() }
            .filter { it.isNotEmpty() }
        val missingArtifacts = requiredArtifacts.filterNot { file ->
            val resourcePath = when {
                file.startsWith("taxonomy/") -> file
                else -> "taxonomy/stage2/2.2/_registry/$file"
            }
            CatalogSeedResourceReader.resourceExists(resourcePath)
        }
        assertTrue(
            "Missing governance artifacts: ${missingArtifacts.joinToString()}",
            missingArtifacts.isEmpty(),
        )

        val changelog = parseArray(CHANGELOG_PATH)
        val versionEntry = changelog.firstOrNull { entry ->
            entry.objectValue("version") == meta.dataVersion
        }
        assertTrue(
            "Current dataVersion '${meta.dataVersion}' must exist in $CHANGELOG_PATH.",
            versionEntry != null,
        )

        val releaseDateRaw = versionEntry!!.objectValue("releaseDate")
        val releaseDate = runCatching { LocalDate.parse(releaseDateRaw) }.getOrNull()
        assertTrue("releaseDate for ${meta.dataVersion} must be ISO yyyy-MM-dd.", releaseDate != null)

        val changes = versionEntry.arrayValue("changes")
        assertTrue("Changelog entry for ${meta.dataVersion} must contain changes.", changes.isNotEmpty())

        val readinessPolicy = parseObject("taxonomy/stage2/2.2/_registry/readiness_governance_policy.json")
        val governanceHooks = readinessPolicy.arrayValue("governanceHooks")
        assertTrue("Governance hooks must be declared.", governanceHooks.isNotEmpty())
        governanceHooks.forEach { element ->
            val hook = element as? JsonObject ?: error("governanceHooks entries must be objects.")
            assertTrue("governanceHooks.code must be set.", hook.value("code").isNotBlank())
            assertTrue("governanceHooks.trigger must be set.", hook.value("trigger").isNotBlank())
            assertTrue("governanceHooks.transport must be set.", hook.value("transport").isNotBlank())
            assertTrue("governanceHooks.targetEnvVar must be set.", hook.value("targetEnvVar").isNotBlank())
            assertTrue("governanceHooks.description must be set.", hook.value("description").isNotBlank())
        }
    }

    private fun parseObject(resourcePath: String): JsonObject =
        Json.parseToJsonElement(CatalogSeedResourceReader.readText(resourcePath)) as JsonObject

    private fun parseArray(resourcePath: String): JsonArray =
        Json.parseToJsonElement(CatalogSeedResourceReader.readText(resourcePath)) as JsonArray

    private fun JsonObject.value(key: String): String = this[key]?.jsonPrimitive?.content?.trim().orEmpty()

    private fun JsonObject.objectValue(key: String): JsonObject =
        this[key]?.let { element -> element as? JsonObject } ?: JsonObject(emptyMap())

    private fun JsonObject.arrayValue(key: String): JsonArray =
        this[key]?.let { element -> element as? JsonArray } ?: JsonArray(emptyList())

    private fun JsonElement.objectValue(key: String): String =
        (this as? JsonObject)?.value(key).orEmpty()

    private fun JsonElement.arrayValue(key: String): JsonArray =
        (this as? JsonObject)?.arrayValue(key) ?: JsonArray(emptyList())

    private companion object {
        private const val POLICY_PATH = "taxonomy/stage2/2.2/_registry/data_release_policy.json"
        private const val CHANGELOG_PATH = "taxonomy/stage2/2.2/_registry/data_version_changelog.json"
    }
}
