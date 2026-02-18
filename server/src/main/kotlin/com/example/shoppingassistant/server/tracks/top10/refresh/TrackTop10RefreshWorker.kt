package com.example.shoppingassistant.server.tracks.top10.refresh

import com.example.shoppingassistant.server.config.TrackTop10RefreshConfig
import com.example.shoppingassistant.server.tracks.top10.TrackTop10SnapshotRepository
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class TrackTop10RefreshResult(
    val processed: Int,
    val succeeded: Int,
    val failed: Int,
)

class TrackTop10RefreshWorker(
    private val config: TrackTop10RefreshConfig,
    private val planner: TrackTop10RefreshPlanner,
    private val snapshotRepository: TrackTop10SnapshotRepository,
    private val builder: TrackTop10SnapshotBuilder,
    private val workerId: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun runOnce(): TrackTop10RefreshResult {
        val candidates = planner.pickCandidates()
        var processed = 0
        var succeeded = 0
        var failed = 0

        for (candidate in candidates) {
            if (!snapshotRepository.tryLock(candidate.trackId, workerId, config.lockTtlMs)) continue
            processed += 1
            val now = clock()
            snapshotRepository.markAttempt(candidate.trackId, now)
            try {
                val snapshot = builder.buildSnapshot(candidate)
                snapshotRepository.upsertSnapshot(candidate.trackId, snapshot, now)
                snapshotRepository.markSuccess(candidate.trackId, now)
                succeeded += 1
            } catch (t: Throwable) {
                val nextRetryAt = now + computeBackoff(candidate.failCount + 1)
                snapshotRepository.markFailure(candidate.trackId, now, t.message, nextRetryAt)
                failed += 1
            } finally {
                snapshotRepository.unlock(candidate.trackId, workerId)
            }
        }

        return TrackTop10RefreshResult(processed = processed, succeeded = succeeded, failed = failed)
    }

    private fun computeBackoff(failCount: Int): Long {
        val exp = max(0, failCount - 1)
        val base = config.baseBackoffMs
        val maxBackoff = config.maxBackoffMs
        val raw = base * (1L shl min(exp, 10))
        val bounded = min(raw, maxBackoff)
        val jitter = (bounded * 0.2).toLong()
        val delta = if (jitter > 0) Random.nextLong(-jitter, jitter) else 0L
        return max(base, bounded + delta)
    }
}
