package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.config.PriceFetcherConfig
import com.example.shoppingassistant.server.price.PriceFetcherRunner
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

fun Application.configurePriceFetcherEngine() {
    val config: PriceFetcherConfig =
        KoinJavaComponent.get(PriceFetcherConfig::class.java)
    val runner: PriceFetcherRunner =
        KoinJavaComponent.get(PriceFetcherRunner::class.java)

    val logger = LoggerFactory.getLogger("PriceFetcher")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    monitor.subscribe(ApplicationStarted) {
        if (config.enabled) {
            scope.launch {
                while (isActive) {
                    runCatching { runner.runOnce() }
                        .onSuccess { result ->
                            if (result.attempted > 0 || result.updated > 0 || result.failed > 0) {
                                logger.info(
                                    "Price fetch run: attempted={}, updated={}, skipped={}, blocked={}, failed={}",
                                    result.attempted,
                                    result.updated,
                                    result.skipped,
                                    result.blocked,
                                    result.failed,
                                )
                            }
                        }
                        .onFailure { t ->
                            logger.error("Price fetch run failed", t)
                        }
                    delay(config.intervalMillis)
                }
            }
        }
    }

    monitor.subscribe(ApplicationStopped) {
        scope.cancel("Application stopping")
    }
}
