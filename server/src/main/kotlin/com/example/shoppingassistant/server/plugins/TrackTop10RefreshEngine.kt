package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.config.TrackTop10RefreshConfig
import com.example.shoppingassistant.server.tracks.top10.refresh.TrackTop10RefreshWorker
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

fun Application.configureTrackTop10RefreshEngine() {
    val config: TrackTop10RefreshConfig =
        KoinJavaComponent.get(TrackTop10RefreshConfig::class.java)
    val worker: TrackTop10RefreshWorker =
        KoinJavaComponent.get(TrackTop10RefreshWorker::class.java)

    val logger = LoggerFactory.getLogger("TrackTop10Refresh")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    monitor.subscribe(ApplicationStarted) {
        if (!config.enabled) return@subscribe
        scope.launch {
            while (isActive) {
                runCatching { worker.runOnce() }
                    .onSuccess { result ->
                        if (result.processed > 0) {
                            logger.info(
                                "Top10 refresh: processed={}, succeeded={}, failed={}",
                                result.processed,
                                result.succeeded,
                                result.failed,
                            )
                        }
                    }
                    .onFailure { t ->
                        logger.error("Top10 refresh failed", t)
                    }
                delay(config.pollIntervalMs)
            }
        }
    }

    monitor.subscribe(ApplicationStopped) {
        scope.cancel("Application stopping")
    }
}
