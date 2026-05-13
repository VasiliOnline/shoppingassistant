package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshRunner
import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig
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

fun Application.configureCatalogGovernanceRefreshEngine() {
    val config: CatalogGovernanceRefreshConfig =
        KoinJavaComponent.get(CatalogGovernanceRefreshConfig::class.java)
    val runner: CatalogGovernanceRefreshRunner =
        KoinJavaComponent.get(CatalogGovernanceRefreshRunner::class.java)

    val logger = LoggerFactory.getLogger("CatalogGovernanceRefresh")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    monitor.subscribe(ApplicationStarted) {
        if (!config.enabled) return@subscribe
        scope.launch {
            while (isActive) {
                runCatching { runner.runOnce() }
                    .onSuccess { result ->
                        if (result.sourceCount > 0) {
                            logger.info(
                                "Catalog governance refresh: category={}, sources={}, completed={}, failed={}, skipped={}",
                                result.categoryCode,
                                result.sourceCount,
                                result.completedRuns,
                                result.failedRuns,
                                result.skippedReason ?: "none",
                            )
                        }
                    }
                    .onFailure { throwable ->
                        logger.error("Catalog governance refresh run failed", throwable)
                    }
                delay(config.pollIntervalMs)
            }
        }
    }

    monitor.subscribe(ApplicationStopped) {
        scope.cancel("Application stopping")
    }
}
