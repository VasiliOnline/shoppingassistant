package com.example.shoppingassistant.domain.visualsearch

object VisualSearchSchemaVersion {
    const val current: String = "visual-search/1.0"
    const val minSupportedClientSchemaVersion: String = current
}

object VisualSearchHttpHeaders {
    const val visualSessionId: String = "X-Visual-Session-Id"
    const val clientSchemaVersion: String = "X-Client-Schema-Version"
    const val catalogDataVersion: String = "X-Catalog-Data-Version"
    const val idempotencyKey: String = "X-Idempotency-Key"
    const val serverTraceId: String = "X-Server-Trace-Id"
}

object VisualSearchHttpContractPaths {
    const val base: String = "/api/search/visual"
    const val contextReuse: String = "$base/context/reuse"
    const val normalizeDraft: String = "$base/normalize-draft"
    const val bindQuery: String = "$base/bind-query"
    const val recoveryPlan: String = "$base/recovery-plan"
    const val eventsBatch: String = "$base/events/batch"

    val openApiPaths: Set<String> = linkedSetOf(
        contextReuse,
        normalizeDraft,
        bindQuery,
        recoveryPlan,
        eventsBatch,
    )
}
