package com.example.shoppingassistant.core.data.ingest.replay

import com.example.shoppingassistant.domain.ingest.IngestStatus
import com.example.shoppingassistant.domain.ingest.SourceType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

data class IngestReplayPayload(
    val sourceType: SourceType,
    val url: String,
    val status: IngestStatus,
    val httpStatus: Int?,
    val body: String,
    val collectedAt: Long,
    val parserVersion: String?,
)

interface IngestReplayStore {
    fun shouldRecord(sourceType: SourceType, status: IngestStatus): Boolean
    fun record(payload: IngestReplayPayload)
}

class NoopIngestReplayStore : IngestReplayStore {
    override fun shouldRecord(sourceType: SourceType, status: IngestStatus): Boolean = false
    override fun record(payload: IngestReplayPayload) = Unit
}

class FileIngestReplayStore(
    private val rootDir: File,
    private val sampleRate: Double,
    private val maxEntries: Int,
    private val maxBodyBytes: Int,
) : IngestReplayStore {

    override fun shouldRecord(sourceType: SourceType, status: IngestStatus): Boolean {
        if (sampleRate <= 0.0) return false
        if (sampleRate >= 1.0) return true
        return Math.random() < sampleRate
    }

    override fun record(payload: IngestReplayPayload) {
        ensureDir()
        val timestamp = dateFormat.format(Date(payload.collectedAt))
        val safeHash = payload.url.hashCode().toString(16)
        val file = File(rootDir, "${payload.sourceType.name.lowercase()}-$timestamp-$safeHash.html")
        val truncatedBody = payload.body.substring(0, min(payload.body.length, maxBodyBytes))
        val header = buildString {
            append("<!--\n")
            append("url: ").append(payload.url).append("\n")
            append("status: ").append(payload.status).append("\n")
            append("http: ").append(payload.httpStatus ?: "-").append("\n")
            append("parser: ").append(payload.parserVersion ?: "-").append("\n")
            append("collectedAt: ").append(payload.collectedAt).append("\n")
            append("-->\n")
        }
        runCatching {
            file.writeText(header + truncatedBody)
            trimIfNeeded()
        }
    }

    private fun ensureDir() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    private fun trimIfNeeded() {
        val files = rootDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size <= maxEntries) return
        files.take(files.size - maxEntries).forEach { it.delete() }
    }

    private companion object {
        val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
