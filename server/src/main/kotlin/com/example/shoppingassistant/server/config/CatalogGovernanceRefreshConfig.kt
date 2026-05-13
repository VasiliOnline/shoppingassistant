package com.example.shoppingassistant.server.config

import com.example.shoppingassistant.server.catalog.CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshConnectorType
import java.util.concurrent.TimeUnit

data class CatalogGovernanceRefreshConfig(
    val enabled: Boolean,
    val pollIntervalMs: Long,
    val categoryCode: String,
    val connectorTypes: Set<CatalogGovernanceRefreshConnectorType>,
    val registryCodeAllowlist: Set<String>,
    val trigger: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): CatalogGovernanceRefreshConfig {
            val enabled = (env["CATALOG_GOVERNANCE_REFRESH_ENABLED"] ?: "true")
                .equals("true", ignoreCase = true)
            val pollIntervalMs = env["CATALOG_GOVERNANCE_REFRESH_POLL_MS"]
                ?.toLongOrNull()
                ?.takeIf { it >= 30_000L }
                ?: TimeUnit.HOURS.toMillis(6)
            val categoryCode = env["CATALOG_GOVERNANCE_REFRESH_CATEGORY_CODE"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val connectorTypes = parseConnectorTypes(
                env["CATALOG_GOVERNANCE_REFRESH_CONNECTOR_TYPES"],
            )
            val registryCodeAllowlist = env["CATALOG_GOVERNANCE_REFRESH_REGISTRY_CODES"]
                ?.split(',')
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val trigger = env["CATALOG_GOVERNANCE_REFRESH_TRIGGER"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "SCHEDULED"
            return CatalogGovernanceRefreshConfig(
                enabled = enabled,
                pollIntervalMs = pollIntervalMs,
                categoryCode = categoryCode,
                connectorTypes = connectorTypes,
                registryCodeAllowlist = registryCodeAllowlist,
                trigger = trigger,
            )
        }

        private fun parseConnectorTypes(raw: String?): Set<CatalogGovernanceRefreshConnectorType> {
            val parsed = raw
                ?.split(',')
                .orEmpty()
                .mapNotNull { candidate ->
                    candidate.trim()
                        .takeIf { it.isNotEmpty() }
                        ?.uppercase()
                        ?.let { normalized ->
                            runCatching { CatalogGovernanceRefreshConnectorType.valueOf(normalized) }
                                .getOrElse {
                                    throw IllegalStateException(
                                        "Unknown catalog governance connector type '$normalized'.",
                                    )
                                }
                        }
                }
                .toSet()
            return if (parsed.isEmpty()) {
                setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE)
            } else {
                parsed
            }
        }
    }
}
