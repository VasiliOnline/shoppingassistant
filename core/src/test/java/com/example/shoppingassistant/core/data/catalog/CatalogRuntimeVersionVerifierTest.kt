package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.network.createBackendHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRuntimeVersionVerifierTest {
    @Test
    fun compareSemver_orders_versions_correctly() {
        assertEquals(0, compareSemver("1.0.0", "1.0.0"))
        assertTrue(compareSemver("1.2.0", "1.1.9") > 0)
        assertTrue(compareSemver("2.0.0", "1.9.9") > 0)
        assertTrue(compareSemver("1.0.1", "1.1.0") < 0)
    }

    @Test
    fun isSemver_accepts_only_three_numeric_parts() {
        assertTrue(isSemver("1.0.0"))
        assertFalse(isSemver("1.0"))
        assertFalse(isSemver("1.0.x"))
        assertFalse(isSemver("v1.0.0"))
    }

    @Test
    fun ensureSchemaCompatibility_rejects_client_below_minimum_supported() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0",
            expectedSchemaVersion = "1.0.0",
        )

        val error = runCatching {
            verifier.ensureSchemaCompatibility(
                remoteSchemaVersion = "1.0.0",
                minSupportedClientSchemaVersion = "1.1.0",
                operation = "test",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("below minimum supported") == true)
    }

    @Test
    fun ensureSchemaCompatibility_rejects_schema_mismatch() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0",
            expectedSchemaVersion = "1.0.0",
        )

        verifier.ensureSchemaCompatibility(
            remoteSchemaVersion = "1.1.0",
            minSupportedClientSchemaVersion = "1.0.0",
            operation = "test",
        )
    }

    @Test
    fun ensureSchemaCompatibility_rejects_older_server_schema_with_same_major() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0",
            expectedSchemaVersion = "1.1.0",
        )

        val error = runCatching {
            verifier.ensureSchemaCompatibility(
                remoteSchemaVersion = "1.0.0",
                minSupportedClientSchemaVersion = "1.0.0",
                operation = "test",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("older than client") == true)
    }

    @Test
    fun ensureSchemaCompatibility_rejects_major_schema_mismatch() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0",
            expectedSchemaVersion = "1.1.0",
        )

        val error = runCatching {
            verifier.ensureSchemaCompatibility(
                remoteSchemaVersion = "2.0.0",
                minSupportedClientSchemaVersion = "2.0.0",
                operation = "test",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("schema major mismatch") == true)
    }

    @Test
    fun ensureDataVersionCompatibility_allows_server_authoritative_mode_without_embedded_parity() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0+embedded",
            expectedSchemaVersion = "1.1.0",
        )

        verifier.ensureDataVersionCompatibility(
            remoteDataVersion = "2.3.0+server",
            expectedNegotiatedDataVersion = null,
            requireDataVersionParity = false,
            operation = "test",
        )
    }

    @Test
    fun ensureDataVersionCompatibility_rejects_embedded_parity_mismatch_when_fallback_required() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0+embedded",
            expectedSchemaVersion = "1.1.0",
        )

        val error = runCatching {
            verifier.ensureDataVersionCompatibility(
                remoteDataVersion = "2.3.0+server",
                expectedNegotiatedDataVersion = null,
                requireDataVersionParity = true,
                operation = "test",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("dataVersion mismatch embedded") == true)
    }

    @Test
    fun ensureDataVersionCompatibility_allows_drift_when_policy_disables_negotiated_stability() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0+embedded",
            expectedSchemaVersion = "1.1.0",
        )

        verifier.ensureDataVersionCompatibility(
            remoteDataVersion = "2.3.0+server",
            expectedNegotiatedDataVersion = "2.2.0+server",
            requireDataVersionParity = false,
            operation = "test",
            policy = CatalogRuntimeCompatibilityPolicyDescriptor.default().copy(
                negotiatedDataVersionMustStayStable = false,
            ),
        )
    }

    @Test
    fun ensureSchemaCompatibility_rejects_unknown_policy_mode() {
        val verifier = StrictCatalogRuntimeVersionVerifier(
            backendClient = createBackendHttpClient(),
            embeddedDataVersion = "2.2.0",
            expectedSchemaVersion = "1.0.0",
        )

        val error = runCatching {
            verifier.ensureSchemaCompatibility(
                remoteSchemaVersion = "1.0.0",
                minSupportedClientSchemaVersion = "1.0.0",
                operation = "test",
                policy = CatalogRuntimeCompatibilityPolicyDescriptor.default().copy(
                    schemaVersioningMode = "UNSUPPORTED_MODE",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("Unsupported schemaVersioningMode") == true)
    }
}
