package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogReleaseDeprecationPolicy(
    val announceBeforeDays: Int,
    val removeAfterDays: Int,
)

@Serializable
data class CatalogReleasePolicy(
    val releaseCadence: String,
    val owner: String,
    val deprecation: CatalogReleaseDeprecationPolicy,
    val requiredArtifacts: List<String>,
)

@Serializable
data class CatalogReadinessRunCadence(
    val dailyOperationalTimeUtc: String,
    val weeklySummaryDay: String,
    val weeklySummaryTimeUtc: String,
)

@Serializable
data class CatalogGovernanceHook(
    val code: String,
    val trigger: String,
    val transport: String,
    val targetEnvVar: String,
    val description: String,
)

@Serializable
data class CatalogReadinessGovernancePolicy(
    val schemaVersion: String,
    val runCadence: CatalogReadinessRunCadence,
    val sqlChecks: List<String>,
    val automationScripts: List<String>,
    val runbooks: List<String>,
    val monthlyReleaseArtifacts: List<String>,
    val slaDaysByIssueType: Map<String, Int>,
    val governanceHooks: List<CatalogGovernanceHook>,
)

@Serializable
data class CatalogRuntimeCompatibilityPolicy(
    val schemaVersioningMode: String,
    val minSupportedClientStrategy: String,
    val dataVersionMode: String,
    val embeddedParityRequiredWhen: List<String>,
    val negotiatedDataVersionMustStayStable: Boolean,
    val requiredVersionHeaders: List<String>,
    val versionEndpoint: String,
)

@Serializable
data class CatalogGovernanceArtifactStatus(
    val declaredPath: String,
    val resourcePath: String,
    val exists: Boolean,
)

@Serializable
data class CatalogDataReleaseEntry(
    val version: String,
    val releaseDate: String,
    val changes: List<String>,
)

@Serializable
data class CatalogGovernanceSnapshot(
    val schemaVersion: String,
    val dataVersion: String,
    val generatedAt: String,
    val releasePolicy: CatalogReleasePolicy,
    val readinessPolicy: CatalogReadinessGovernancePolicy,
    val compatibilityPolicy: CatalogRuntimeCompatibilityPolicy,
    val requiredArtifactStatuses: List<CatalogGovernanceArtifactStatus>,
    val releaseHistory: List<CatalogDataReleaseEntry>,
)

@Serializable
private data class CatalogReleasePolicyDocument(
    val releaseCadence: String,
    val owner: String,
    val deprecation: CatalogReleaseDeprecationPolicyDocument,
    val requiredArtifacts: List<String> = emptyList(),
)

@Serializable
private data class CatalogReleaseDeprecationPolicyDocument(
    val announceBeforeDays: Int,
    val removeAfterDays: Int,
)

@Serializable
private data class CatalogReadinessGovernancePolicyDocument(
    val schemaVersion: String,
    val runCadence: CatalogReadinessRunCadenceDocument,
    val sqlChecks: List<String> = emptyList(),
    val automationScripts: List<String> = emptyList(),
    val runbooks: List<String> = emptyList(),
    val monthlyReleaseArtifacts: List<String> = emptyList(),
    val slaDaysByIssueType: Map<String, Int> = emptyMap(),
    val governanceHooks: List<CatalogGovernanceHookDocument> = emptyList(),
)

@Serializable
private data class CatalogGovernanceHookDocument(
    val code: String,
    val trigger: String,
    val transport: String,
    val targetEnvVar: String,
    val description: String,
)

@Serializable
private data class CatalogRuntimeCompatibilityPolicyDocument(
    val schemaVersioningMode: String,
    val minSupportedClientStrategy: String,
    val dataVersionMode: String,
    val embeddedParityRequiredWhen: List<String> = emptyList(),
    val negotiatedDataVersionMustStayStable: Boolean = true,
    val requiredVersionHeaders: List<String> = emptyList(),
    val versionEndpoint: String,
)

@Serializable
private data class CatalogReadinessRunCadenceDocument(
    val dailyOperationalTimeUtc: String,
    val weeklySummaryDay: String,
    val weeklySummaryTimeUtc: String,
)

@Serializable
private data class CatalogDataReleaseEntryDocument(
    val version: String,
    val releaseDate: String,
    val changes: List<String> = emptyList(),
)

object CatalogGovernanceLoader {
    private val snapshotCache: CatalogGovernanceSnapshot by lazy { loadSnapshotInternal() }

    fun loadSnapshot(): CatalogGovernanceSnapshot = snapshotCache

    private fun loadSnapshotInternal(): CatalogGovernanceSnapshot {
        val meta = Stage22RegistryLoader.loadSnapshot().meta
        val releasePolicy = CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage22RegistryBase}/data_release_policy.json",
            deserializer = CatalogReleasePolicyDocument.serializer(),
        ).toReleasePolicy()
        val readinessPolicy = CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage22RegistryBase}/readiness_governance_policy.json",
            deserializer = CatalogReadinessGovernancePolicyDocument.serializer(),
        ).toReadinessPolicy()
        val compatibilityPolicy = CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage22RegistryBase}/runtime_compatibility_policy.json",
            deserializer = CatalogRuntimeCompatibilityPolicyDocument.serializer(),
        ).toCompatibilityPolicy()
        val artifactStatuses = releasePolicy.requiredArtifacts
            .map { declaredPath ->
                val resourcePath = resolveGovernanceArtifactPath(declaredPath)
                CatalogGovernanceArtifactStatus(
                    declaredPath = declaredPath,
                    resourcePath = resourcePath,
                    exists = CatalogSeedResourceReader.resourceExists(resourcePath),
                )
            }
        val releaseHistory = CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage22RegistryBase}/data_version_changelog.json",
            deserializer = kotlinx.serialization.builtins.ListSerializer(CatalogDataReleaseEntryDocument.serializer()),
        ).map { entry ->
            CatalogDataReleaseEntry(
                version = entry.version.trim(),
                releaseDate = entry.releaseDate.trim(),
                changes = entry.changes
                    .map { change -> change.trim() }
                    .filter { change -> change.isNotEmpty() },
            )
        }

        return CatalogGovernanceSnapshot(
            schemaVersion = meta.schemaVersion,
            dataVersion = meta.dataVersion,
            generatedAt = meta.generatedAt,
            releasePolicy = releasePolicy,
            readinessPolicy = readinessPolicy,
            compatibilityPolicy = compatibilityPolicy,
            requiredArtifactStatuses = artifactStatuses,
            releaseHistory = releaseHistory,
        )
    }

    private fun CatalogReleasePolicyDocument.toReleasePolicy(): CatalogReleasePolicy =
        CatalogReleasePolicy(
            releaseCadence = releaseCadence.trim(),
            owner = owner.trim(),
            deprecation = CatalogReleaseDeprecationPolicy(
                announceBeforeDays = deprecation.announceBeforeDays,
                removeAfterDays = deprecation.removeAfterDays,
            ),
            requiredArtifacts = requiredArtifacts
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
        )

    private fun CatalogReadinessGovernancePolicyDocument.toReadinessPolicy(): CatalogReadinessGovernancePolicy =
        CatalogReadinessGovernancePolicy(
            schemaVersion = schemaVersion.trim(),
            runCadence = CatalogReadinessRunCadence(
                dailyOperationalTimeUtc = runCadence.dailyOperationalTimeUtc.trim(),
                weeklySummaryDay = runCadence.weeklySummaryDay.trim(),
                weeklySummaryTimeUtc = runCadence.weeklySummaryTimeUtc.trim(),
            ),
            sqlChecks = sqlChecks.normalizePathList(),
            automationScripts = automationScripts.normalizePathList(),
            runbooks = runbooks.normalizePathList(),
            monthlyReleaseArtifacts = monthlyReleaseArtifacts.normalizePathList(),
            slaDaysByIssueType = slaDaysByIssueType
                .mapNotNull { (issueType, days) ->
                    issueType.trim().takeIf { it.isNotEmpty() }?.let { normalizedKey ->
                        normalizedKey to days
                    }
                }
                .toMap(),
            governanceHooks = governanceHooks
                .mapNotNull { hook ->
                    val code = hook.code.trim()
                    val trigger = hook.trigger.trim()
                    val transport = hook.transport.trim()
                    val targetEnvVar = hook.targetEnvVar.trim()
                    val description = hook.description.trim()
                    if (
                        code.isEmpty() ||
                        trigger.isEmpty() ||
                        transport.isEmpty() ||
                        targetEnvVar.isEmpty() ||
                        description.isEmpty()
                    ) {
                        null
                    } else {
                        CatalogGovernanceHook(
                            code = code,
                            trigger = trigger,
                            transport = transport,
                            targetEnvVar = targetEnvVar,
                            description = description,
                        )
                    }
                }
                .distinctBy { hook -> hook.code },
        )

    private fun CatalogRuntimeCompatibilityPolicyDocument.toCompatibilityPolicy(): CatalogRuntimeCompatibilityPolicy =
        CatalogRuntimeCompatibilityPolicy(
            schemaVersioningMode = schemaVersioningMode.trim(),
            minSupportedClientStrategy = minSupportedClientStrategy.trim(),
            dataVersionMode = dataVersionMode.trim(),
            embeddedParityRequiredWhen = embeddedParityRequiredWhen
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            negotiatedDataVersionMustStayStable = negotiatedDataVersionMustStayStable,
            requiredVersionHeaders = requiredVersionHeaders
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            versionEndpoint = versionEndpoint.trim(),
        )

    private fun resolveGovernanceArtifactPath(declaredPath: String): String =
        declaredPath.trim().let { normalized ->
            when {
                normalized.startsWith("taxonomy/") -> normalized
                else -> "${CatalogContractPaths.stage22RegistryBase}/$normalized"
            }
        }

    private fun List<String>.normalizePathList(): List<String> =
        map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}
