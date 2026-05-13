package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceLoader
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

interface CatalogRuntimeVersionVerifier {
    suspend fun ensureCompatible(requireDataVersionParity: Boolean = false)
    fun verifyResponseVersion(
        response: HttpResponse,
        operation: String,
        requireDataVersionParity: Boolean = false,
    )
}

object NoopCatalogRuntimeVersionVerifier : CatalogRuntimeVersionVerifier {
    override suspend fun ensureCompatible(requireDataVersionParity: Boolean) = Unit
    override fun verifyResponseVersion(
        response: HttpResponse,
        operation: String,
        requireDataVersionParity: Boolean,
    ) = Unit
}

class StrictCatalogRuntimeVersionVerifier(
    private val backendClient: BackendClient,
    private val embeddedDataVersion: String = CatalogDataVersion.current,
    private val expectedSchemaVersion: String = CatalogSchemaVersion.current,
) : CatalogRuntimeVersionVerifier {

    private val baseUrl get() = BackendConfig.BASE_URL

    @Volatile
    private var negotiatedState: NegotiatedCatalogRuntimeState? = null

    override suspend fun ensureCompatible(requireDataVersionParity: Boolean) {
        val cachedState = negotiatedState
        if (cachedState != null && !requiresEmbeddedParity(cachedState.policy, requireDataVersionParity)) {
            return
        }
        if (
            cachedState != null &&
            requiresEmbeddedParity(cachedState.policy, requireDataVersionParity) &&
            cachedState.remoteDataVersion == embeddedDataVersion
        ) {
            return
        }

        val compatibilityResponse = backendClient.client.get("$baseUrl$RUNTIME_COMPATIBILITY_ENDPOINT")
        if (!compatibilityResponse.status.isSuccess()) {
            throw IllegalStateException("Catalog runtime compatibility negotiation failed: status=${compatibilityResponse.status}")
        }
        val compatibilityPayload = runCatching { compatibilityResponse.body<CatalogRuntimeCompatibilityPayload>() }
            .getOrElse { error ->
                throw IllegalStateException("Catalog runtime compatibility negotiation decode failed", error)
            }
        val policy = compatibilityPayload.toPolicyDescriptor()

        val response = backendClient.client.get(resolveVersionEndpoint(policy.versionEndpoint))
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Catalog version negotiation failed: status=${response.status}")
        }
        val payload = runCatching { response.body<CatalogVersionPayload>() }
            .getOrElse { error ->
                throw IllegalStateException("Catalog version negotiation decode failed", error)
            }

        val remoteSchemaVersion = payload.schemaVersion.trim()
        val remote = payload.dataVersion.trim()
        val minSupportedClientSchemaVersion = payload.minSupportedClientSchemaVersion.trim()
        ensureCompatibilityPayloadConsistency(
            compatibilityPayload = compatibilityPayload,
            remoteSchemaVersion = remoteSchemaVersion,
            remoteDataVersion = remote,
            minSupportedClientSchemaVersion = minSupportedClientSchemaVersion,
        )
        ensureSchemaCompatibility(
            remoteSchemaVersion = remoteSchemaVersion,
            minSupportedClientSchemaVersion = minSupportedClientSchemaVersion,
            operation = "catalogVersion",
            policy = policy,
        )
        if (remote.isEmpty()) {
            throw IllegalStateException("Catalog version negotiation failed: remote dataVersion is blank")
        }
        ensureDataVersionCompatibility(
            remoteDataVersion = remote,
            expectedNegotiatedDataVersion = null,
            requireDataVersionParity = requireDataVersionParity,
            operation = "catalogVersion",
            policy = policy,
        )
        negotiatedState = NegotiatedCatalogRuntimeState(
            remoteDataVersion = remote,
            remoteSchemaVersion = remoteSchemaVersion,
            minSupportedClientSchemaVersion = minSupportedClientSchemaVersion,
            policy = policy,
        )
    }

    override fun verifyResponseVersion(
        response: HttpResponse,
        operation: String,
        requireDataVersionParity: Boolean,
    ) {
        val state = negotiatedState
        val policy = state?.policy ?: CatalogRuntimeCompatibilityPolicyDescriptor.default()
        ensureRequiredHeaders(
            response = response,
            policy = policy,
            operation = operation,
        )
        val remote = response.headers[CATALOG_DATA_VERSION_HEADER]?.trim().orEmpty()
        val remoteSchemaVersion = response.headers[CATALOG_SCHEMA_VERSION_HEADER]?.trim().orEmpty()
        val minSupportedClientSchemaVersion =
            response.headers[CATALOG_MIN_SUPPORTED_CLIENT_SCHEMA_VERSION_HEADER]?.trim().orEmpty()
        ensureSchemaCompatibility(
            remoteSchemaVersion = remoteSchemaVersion,
            minSupportedClientSchemaVersion = minSupportedClientSchemaVersion,
            operation = operation,
            policy = policy,
        )
        if (remote.isEmpty()) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (missing '$CATALOG_DATA_VERSION_HEADER' response header)",
            )
        }
        ensureDataVersionCompatibility(
            remoteDataVersion = remote,
            expectedNegotiatedDataVersion = state?.remoteDataVersion,
            requireDataVersionParity = requireDataVersionParity,
            operation = operation,
            policy = policy,
        )
    }

    internal fun ensureSchemaCompatibility(
        remoteSchemaVersion: String,
        minSupportedClientSchemaVersion: String,
        operation: String,
        policy: CatalogRuntimeCompatibilityPolicyDescriptor = CatalogRuntimeCompatibilityPolicyDescriptor.default(),
    ) {
        ensureSupportedSchemaPolicy(policy, operation)
        if (remoteSchemaVersion.isEmpty()) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (missing '$CATALOG_SCHEMA_VERSION_HEADER' schema version)",
            )
        }
        if (minSupportedClientSchemaVersion.isEmpty()) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (missing '$CATALOG_MIN_SUPPORTED_CLIENT_SCHEMA_VERSION_HEADER' schema floor)",
            )
        }
        if (!isSemver(remoteSchemaVersion)) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (invalid schemaVersion '$remoteSchemaVersion')",
            )
        }
        if (!isSemver(minSupportedClientSchemaVersion)) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (invalid minSupportedClientSchemaVersion '$minSupportedClientSchemaVersion')",
            )
        }
        when (policy.schemaVersioningMode) {
            "SEMVER_SAME_MAJOR_SERVER_GTE_CLIENT" -> {
                if (majorSemver(remoteSchemaVersion) != majorSemver(expectedSchemaVersion)) {
                    throw IllegalStateException(
                        "Catalog API call failed: $operation (schema major mismatch expected='$expectedSchemaVersion' remote='$remoteSchemaVersion')",
                    )
                }
                if (compareSemver(expectedSchemaVersion, remoteSchemaVersion) > 0) {
                    throw IllegalStateException(
                        "Catalog API call failed: $operation (server schema '$remoteSchemaVersion' is older than client '$expectedSchemaVersion')",
                    )
                }
            }
            else -> error("Unsupported schemaVersioningMode='${policy.schemaVersioningMode}' for $operation")
        }
        when (policy.minSupportedClientStrategy) {
            "SERVER_DECLARED_MAJOR_FLOOR" -> {
                if (majorSemver(remoteSchemaVersion) != majorSemver(minSupportedClientSchemaVersion)) {
                    throw IllegalStateException(
                        "Catalog API call failed: $operation (invalid schema floor '$minSupportedClientSchemaVersion' for remote '$remoteSchemaVersion')",
                    )
                }
                if (compareSemver(expectedSchemaVersion, minSupportedClientSchemaVersion) < 0) {
                    throw IllegalStateException(
                        "Catalog API call failed: $operation (client schema '$expectedSchemaVersion' is below minimum supported '$minSupportedClientSchemaVersion')",
                    )
                }
            }
            else -> error("Unsupported minSupportedClientStrategy='${policy.minSupportedClientStrategy}' for $operation")
        }
    }

    internal fun ensureDataVersionCompatibility(
        remoteDataVersion: String,
        expectedNegotiatedDataVersion: String?,
        requireDataVersionParity: Boolean,
        operation: String,
        policy: CatalogRuntimeCompatibilityPolicyDescriptor = CatalogRuntimeCompatibilityPolicyDescriptor.default(),
    ) {
        ensureSupportedDataPolicy(policy, operation)
        if (remoteDataVersion.isBlank()) {
            throw IllegalStateException("Catalog API call failed: $operation (blank dataVersion)")
        }
        if (
            policy.negotiatedDataVersionMustStayStable &&
            expectedNegotiatedDataVersion != null &&
            remoteDataVersion != expectedNegotiatedDataVersion
        ) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (dataVersion drift negotiated='$expectedNegotiatedDataVersion' remote='$remoteDataVersion')",
            )
        }
        if (requiresEmbeddedParity(policy, requireDataVersionParity) && remoteDataVersion != embeddedDataVersion) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (dataVersion mismatch embedded='$embeddedDataVersion' remote='$remoteDataVersion')",
            )
        }
    }

    private fun ensureCompatibilityPayloadConsistency(
        compatibilityPayload: CatalogRuntimeCompatibilityPayload,
        remoteSchemaVersion: String,
        remoteDataVersion: String,
        minSupportedClientSchemaVersion: String,
    ) {
        if (compatibilityPayload.currentSchemaVersion.trim() != remoteSchemaVersion) {
            throw IllegalStateException(
                "Catalog runtime compatibility negotiation failed: schema mismatch runtime='${compatibilityPayload.currentSchemaVersion}' version='$remoteSchemaVersion'",
            )
        }
        if (compatibilityPayload.currentDataVersion.trim() != remoteDataVersion) {
            throw IllegalStateException(
                "Catalog runtime compatibility negotiation failed: dataVersion mismatch runtime='${compatibilityPayload.currentDataVersion}' version='$remoteDataVersion'",
            )
        }
        if (compatibilityPayload.minSupportedClientSchemaVersion.trim() != minSupportedClientSchemaVersion) {
            throw IllegalStateException(
                "Catalog runtime compatibility negotiation failed: schema floor mismatch runtime='${compatibilityPayload.minSupportedClientSchemaVersion}' version='$minSupportedClientSchemaVersion'",
            )
        }
    }

    private fun ensureRequiredHeaders(
        response: HttpResponse,
        policy: CatalogRuntimeCompatibilityPolicyDescriptor,
        operation: String,
    ) {
        val missingHeaders = policy.requiredVersionHeaders.filter { headerName ->
            response.headers[headerName]?.trim().isNullOrEmpty()
        }
        if (missingHeaders.isNotEmpty()) {
            throw IllegalStateException(
                "Catalog API call failed: $operation (missing version headers ${missingHeaders.joinToString()})",
            )
        }
    }

    private fun ensureSupportedSchemaPolicy(
        policy: CatalogRuntimeCompatibilityPolicyDescriptor,
        operation: String,
    ) {
        val supportedSchemaModes = setOf("SEMVER_SAME_MAJOR_SERVER_GTE_CLIENT")
        val supportedMinSupportedStrategies = setOf("SERVER_DECLARED_MAJOR_FLOOR")
        if (policy.schemaVersioningMode !in supportedSchemaModes) {
            error("Unsupported schemaVersioningMode='${policy.schemaVersioningMode}' for $operation")
        }
        if (policy.minSupportedClientStrategy !in supportedMinSupportedStrategies) {
            error("Unsupported minSupportedClientStrategy='${policy.minSupportedClientStrategy}' for $operation")
        }
    }

    private fun ensureSupportedDataPolicy(
        policy: CatalogRuntimeCompatibilityPolicyDescriptor,
        operation: String,
    ) {
        val supportedDataModes = setOf("SERVER_AUTHORITATIVE_NEGOTIATED_STABILITY")
        if (policy.dataVersionMode !in supportedDataModes) {
            error("Unsupported dataVersionMode='${policy.dataVersionMode}' for $operation")
        }
    }

    private fun requiresEmbeddedParity(
        policy: CatalogRuntimeCompatibilityPolicyDescriptor,
        requireDataVersionParity: Boolean,
    ): Boolean {
        if (!requireDataVersionParity) return false
        return policy.embeddedParityRequiredWhen.any { condition ->
            condition.equals("SEED_FALLBACK_ENABLED", ignoreCase = true)
        }
    }

    private fun resolveVersionEndpoint(versionEndpoint: String): String {
        val normalized = versionEndpoint.trim()
        require(normalized.isNotEmpty()) { "Catalog runtime compatibility policy versionEndpoint is blank" }
        return if (normalized.startsWith("http://") || normalized.startsWith("https://")) normalized
        else "$baseUrl$normalized"
    }

    private companion object {
        private const val RUNTIME_COMPATIBILITY_ENDPOINT = "/api/catalog/runtime-compatibility"
        private const val CATALOG_DATA_VERSION_HEADER = "X-Catalog-Data-Version"
        private const val CATALOG_SCHEMA_VERSION_HEADER = "X-Catalog-Schema-Version"
        private const val CATALOG_MIN_SUPPORTED_CLIENT_SCHEMA_VERSION_HEADER =
            "X-Catalog-Min-Supported-Client-Schema-Version"
    }
}

@Serializable
private data class CatalogVersionPayload(
    val schemaVersion: String,
    val dataVersion: String,
    val minSupportedClientSchemaVersion: String,
)

@Serializable
private data class CatalogRuntimeCompatibilityPayload(
    val schemaVersion: String,
    val currentSchemaVersion: String,
    val minSupportedClientSchemaVersion: String,
    val currentDataVersion: String,
    val policy: CatalogRuntimeCompatibilityPolicyPayload,
)

@Serializable
private data class CatalogRuntimeCompatibilityPolicyPayload(
    val schemaVersioningMode: String,
    val minSupportedClientStrategy: String,
    val dataVersionMode: String,
    val embeddedParityRequiredWhen: List<String>,
    val negotiatedDataVersionMustStayStable: Boolean,
    val requiredVersionHeaders: List<String>,
    val versionEndpoint: String,
)

private data class NegotiatedCatalogRuntimeState(
    val remoteDataVersion: String,
    val remoteSchemaVersion: String,
    val minSupportedClientSchemaVersion: String,
    val policy: CatalogRuntimeCompatibilityPolicyDescriptor,
)

internal data class CatalogRuntimeCompatibilityPolicyDescriptor(
    val schemaVersioningMode: String,
    val minSupportedClientStrategy: String,
    val dataVersionMode: String,
    val embeddedParityRequiredWhen: List<String>,
    val negotiatedDataVersionMustStayStable: Boolean,
    val requiredVersionHeaders: List<String>,
    val versionEndpoint: String,
) {
    companion object {
        fun default(): CatalogRuntimeCompatibilityPolicyDescriptor =
            CatalogGovernanceLoader.loadSnapshot().compatibilityPolicy.toDescriptor()
    }
}

private fun CatalogRuntimeCompatibilityPayload.toPolicyDescriptor(): CatalogRuntimeCompatibilityPolicyDescriptor =
    policy.toDescriptor()

private fun CatalogRuntimeCompatibilityPolicyPayload.toDescriptor(): CatalogRuntimeCompatibilityPolicyDescriptor =
    CatalogRuntimeCompatibilityPolicyDescriptor(
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

private fun com.example.shoppingassistant.domain.catalog.CatalogRuntimeCompatibilityPolicy.toDescriptor():
    CatalogRuntimeCompatibilityPolicyDescriptor =
    CatalogRuntimeCompatibilityPolicyDescriptor(
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

internal fun compareSemver(left: String, right: String): Int {
    val leftParts = parseSemver(left)
    val rightParts = parseSemver(right)
    return leftParts.zip(rightParts)
        .firstOrNull { (l, r) -> l != r }
        ?.let { (l, r) -> l.compareTo(r) }
        ?: 0
}

internal fun isSemver(value: String): Boolean =
    runCatching { parseSemver(value) }.isSuccess

internal fun majorSemver(value: String): Int = parseSemver(value).first()

private fun parseSemver(value: String): List<Int> {
    val parts = value.trim().split('.')
    require(parts.size == 3) { "Expected x.y.z semantic version, got '$value'" }
    return parts.map { part ->
        require(part.isNotBlank() && part.all(Char::isDigit)) { "Expected x.y.z semantic version, got '$value'" }
        part.toInt()
    }
}
