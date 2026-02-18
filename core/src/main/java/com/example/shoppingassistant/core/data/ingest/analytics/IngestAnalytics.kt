package com.example.shoppingassistant.core.data.ingest.analytics

import android.util.Log
import com.example.shoppingassistant.domain.ingest.IngestStatus
import com.example.shoppingassistant.domain.ingest.SourceType

data class IngestAttempt(
    val sourceId: String?,
    val sourceType: SourceType,
    val host: String?,
    val status: IngestStatus,
    val latencyMs: Long?,
    val httpStatus: Int?,
    val bytes: Long?,
    val parserVersion: String?,
)

interface IngestAnalyticsLogger {
    fun log(attempt: IngestAttempt)
}

class LogcatIngestAnalyticsLogger : IngestAnalyticsLogger {
    override fun log(attempt: IngestAttempt) {
        Log.d(
            "Ingest",
            "attempt sourceId=${attempt.sourceId} sourceType=${attempt.sourceType} host=${attempt.host} " +
                "status=${attempt.status} latencyMs=${attempt.latencyMs} http=${attempt.httpStatus} " +
                "bytes=${attempt.bytes} parserVersion=${attempt.parserVersion}",
        )
    }
}
