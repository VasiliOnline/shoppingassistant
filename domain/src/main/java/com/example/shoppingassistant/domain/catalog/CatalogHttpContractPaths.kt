package com.example.shoppingassistant.domain.catalog

object CatalogHttpContractPaths {
    const val base = "/api/catalog"

    const val version = "$base/version"
    const val runtimeCompatibility = "$base/runtime-compatibility"
    const val openApi = "$base/openapi.json"
    const val categories = "$base/categories"
    const val categoryResolve = "$base/categories/{categoryCode}/resolve"
    const val aliases = "$base/aliases"
    const val browseNodes = "$base/browse-nodes"
    const val browseNodeByCode = "$base/browse-nodes/{browseCode}"
    const val aliasEntries = "$base/alias-entries"
    const val googleMappings = "$base/google-mappings"
    const val googleMappingByCategory = "$base/google-mappings/{categoryCode}"
    const val effectiveSpec = "$base/effective-spec/{categoryCode}"
    const val readiness = "$base/readiness"
    const val readinessGovernance = "$base/readiness/governance"
    const val readinessGovernanceReports = "$base/readiness/governance/reports"
    const val readinessHistory = "$base/readiness/{categoryCode}/history"
    const val liveValues = "$base/live-values"
    const val governanceSources = "$base/governance/sources"
    const val governanceRefreshStatus = "$base/governance/refresh-status"
    const val governanceRefreshRuns = "$base/governance/refresh-runs"
    const val governanceModelEnrichmentQueue = "$base/governance/model-enrichment-queue"
    const val governanceModelEnrichmentPromote = "$base/governance/model-enrichment-promote"
    const val governanceReviewQueue = "$base/governance/review-queue"
    const val governanceReviewAction = "$base/governance/review-action"
    const val governancePublishEvents = "$base/governance/publish-events"
    const val governanceRefresh = "$base/governance/refresh"
    const val governanceRebuild = "$base/governance/rebuild"

    val openApiPaths: Set<String> = linkedSetOf(
        version,
        runtimeCompatibility,
        openApi,
        categories,
        categoryResolve,
        aliases,
        browseNodes,
        browseNodeByCode,
        aliasEntries,
        googleMappings,
        googleMappingByCategory,
        effectiveSpec,
        readiness,
        readinessGovernance,
        readinessGovernanceReports,
        readinessHistory,
        liveValues,
        governanceSources,
        governanceRefreshStatus,
        governanceRefreshRuns,
        governanceModelEnrichmentQueue,
        governanceModelEnrichmentPromote,
        governanceReviewQueue,
        governanceReviewAction,
        governancePublishEvents,
        governanceRefresh,
        governanceRebuild,
    )
}
