package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.catalog.CatalogReadinessSnapshotRunner
import com.example.shoppingassistant.server.config.CatalogReadinessSnapshotConfig
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

fun Application.configureCatalogReadinessSnapshotEngine() {
    val config: CatalogReadinessSnapshotConfig =
        KoinJavaComponent.get(CatalogReadinessSnapshotConfig::class.java)
    val runner: CatalogReadinessSnapshotRunner =
        KoinJavaComponent.get(CatalogReadinessSnapshotRunner::class.java)

    val logger = LoggerFactory.getLogger("CatalogReadinessSnapshot")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    monitor.subscribe(ApplicationStarted) {
        if (!config.enabled) return@subscribe
        scope.launch {
            while (isActive) {
                runCatching { runner.runIfDue() }
                    .onSuccess { result ->
                        if (result.captured) {
                            logger.info(
                                "Catalog readiness snapshot captured: date={}, categories={}",
                                result.snapshotDate,
                                result.capturedCategories,
                            )
                        }
                        if (result.weeklySummaryCaptured) {
                            logger.info(
                                "Catalog governance weekly summary captured: date={}",
                                result.snapshotDate,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        logger.error("Catalog readiness snapshot run failed", throwable)
                    }
                delay(config.pollIntervalMs)
            }
        }
    }

    monitor.subscribe(ApplicationStopped) {
        scope.cancel("Application stopping")
    }
}
