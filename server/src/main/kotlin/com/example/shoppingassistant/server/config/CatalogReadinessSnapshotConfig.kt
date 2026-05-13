package com.example.shoppingassistant.server.config

import java.util.concurrent.TimeUnit

data class CatalogReadinessSnapshotConfig(
    val enabled: Boolean,
    val pollIntervalMs: Long,
    val leaseTtlMs: Long,
    val leaseHeartbeatIntervalMs: Long,
    val ownerId: String,
) {
    companion object {
        fun fromEnv(): CatalogReadinessSnapshotConfig {
            val enabled = (System.getenv("CATALOG_READINESS_SNAPSHOT_ENABLED") ?: "true")
                .equals("true", ignoreCase = true)
            val pollIntervalMs = System.getenv("CATALOG_READINESS_SNAPSHOT_POLL_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 60_000L }
                ?: TimeUnit.MINUTES.toMillis(15)
            val leaseTtlMs = System.getenv("CATALOG_READINESS_SNAPSHOT_LEASE_TTL_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 30_000L }
                ?: TimeUnit.MINUTES.toMillis(10)
            val leaseHeartbeatIntervalMs = System.getenv("CATALOG_READINESS_SNAPSHOT_HEARTBEAT_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 5_000L }
                ?: (leaseTtlMs / 3).coerceAtLeast(5_000L)
            val ownerId = System.getenv("CATALOG_READINESS_SNAPSHOT_OWNER_ID")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: buildDefaultOwnerId()
            return CatalogReadinessSnapshotConfig(
                enabled = enabled,
                pollIntervalMs = pollIntervalMs,
                leaseTtlMs = leaseTtlMs,
                leaseHeartbeatIntervalMs = leaseHeartbeatIntervalMs,
                ownerId = ownerId,
            )
        }

        private fun buildDefaultOwnerId(): String {
            val host = System.getenv("HOSTNAME")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: System.getenv("COMPUTERNAME")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: "catalog-node"
            val pid = ProcessHandle.current().pid()
            return "$host-$pid"
        }
    }
}
