// Last synced: 2025-12-10 15:35:15
package com.example.shoppingassistant.server

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.TaxonomyValidator
import com.example.shoppingassistant.domain.facet.FacetSchemaValidator
import com.example.shoppingassistant.server.config.ServerConfig
import com.example.shoppingassistant.server.catalog.CatalogStage20BackfillService
import com.example.shoppingassistant.server.catalog.CatalogStage20BackfillStartupConfig
import com.example.shoppingassistant.server.catalog.CatalogGovernanceCuratedSeedSyncService
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshSurfaceService
import com.example.shoppingassistant.server.catalog.CatalogSeedSyncMode
import com.example.shoppingassistant.server.catalog.GovernanceCatalogCanonicalModelRegistryProvider
import com.example.shoppingassistant.server.catalog.GovernanceCatalogCanonicalProductFamilyRegistryProvider
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.di.backendAuthModule
import com.example.shoppingassistant.server.di.backendAiModule
import com.example.shoppingassistant.server.di.backendCatalogModule
import com.example.shoppingassistant.server.di.backendLocalOfferModule
import com.example.shoppingassistant.server.di.backendOffersModule
import com.example.shoppingassistant.server.di.backendPriceModule
import com.example.shoppingassistant.server.di.backendProfileModule
import com.example.shoppingassistant.server.di.backendRankModule
import com.example.shoppingassistant.server.di.backendShortListingModule
import com.example.shoppingassistant.server.di.backendSourcesModule
import com.example.shoppingassistant.server.di.backendStorageModule
import com.example.shoppingassistant.server.di.backendSubscriptionsModule
import com.example.shoppingassistant.server.di.backendTracksModule
import com.example.shoppingassistant.server.di.backendUgcModule
import com.example.shoppingassistant.server.di.backendVisualSearchModule
import com.example.shoppingassistant.server.di.backendVisionModule
import com.example.shoppingassistant.server.offers.Stage4RuntimeBackfillService
import com.example.shoppingassistant.server.tracks.TrackDedupBackfillService
import com.example.shoppingassistant.server.tracks.TrackTargetPostMigrationGuardService
import com.example.shoppingassistant.server.plugins.configureMonitoring
import com.example.shoppingassistant.server.plugins.configureCatalogReadinessSnapshotEngine
import com.example.shoppingassistant.server.plugins.configureCatalogGovernanceRefreshEngine
import com.example.shoppingassistant.server.plugins.configurePriceFetcherEngine
import com.example.shoppingassistant.server.plugins.configureSubscriptionsEngine
import com.example.shoppingassistant.server.plugins.configureRouting
import com.example.shoppingassistant.server.plugins.configureSerialization
import com.example.shoppingassistant.server.plugins.configureCors
import com.example.shoppingassistant.server.plugins.configureSecurity
import com.example.shoppingassistant.server.plugins.configureTrackTop10RefreshEngine
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

/**
 * Точка входа backend-приложения.
 *
 * Пример запуска локально:
 * APP_PORT=8080 ./gradlew :server:run
 */
fun main() {
    val logger = LoggerFactory.getLogger("ServerMain")
    val config = ServerConfig.fromEnv()

    // Инициализируем подключение к Postgres через Exposed.
    // Используем параметры, которые ты уже задал в ServerConfig/DatabaseConfig.
    DatabaseFactory.init(config.db)
    enforceCatalogContractsOnStartup()

    // Backend-уровень Koin: поднимаем доменный слой для профиля/авторизации
    // и вспомогательные сервисы (офферы, ранжирование, storage, vision, UGC).
    //
    // Важно: используем только backend-* модули (репозитории + use-case'ы),
    // без Android-специфичных coreModule/rankModule и DI из app-модуля.
    val koinApp = startKoin {
        modules(
            backendAuthModule,
            backendAiModule,
            backendCatalogModule,
            backendLocalOfferModule,
            backendOffersModule,
            backendProfileModule,
            backendPriceModule,
            backendRankModule,
            backendShortListingModule,
            backendSourcesModule,
            backendStorageModule,
            backendVisionModule,
            backendVisualSearchModule,
            backendUgcModule,
            backendSubscriptionsModule,
            backendTracksModule,
        )
    }
    if (envFlagDefaultTrue("CATALOG_GOVERNANCE_CURATED_SYNC_ENABLED")) {
        runCatching {
            runBlocking {
                koinApp.koin.get<CatalogGovernanceCuratedSeedSyncService>()
                    .syncTopCategoryCoverage(syncMode = CatalogSeedSyncMode.UPSERT_ONLY)
            }
        }.onFailure { throwable ->
            logger.warn("Catalog governance curated seed sync skipped on startup", throwable)
        }
    }
    runCatching {
        runBlocking {
            koinApp.koin.get<CatalogGovernanceRefreshSurfaceService>()
                .ensurePhonesSourceRegistry()
        }
    }.onFailure { throwable ->
        logger.warn("Catalog governance phones refresh registry bootstrap skipped on startup", throwable)
    }
    koinApp.koin.get<GovernanceCatalogCanonicalProductFamilyRegistryProvider>().installIntoRuntime()
    koinApp.koin.get<GovernanceCatalogCanonicalModelRegistryProvider>().installIntoRuntime()

    val stage20BackfillConfig = CatalogStage20BackfillStartupConfig.fromEnv()
    val stage20BackfillService = koinApp.koin.get<CatalogStage20BackfillService>()
    if (stage20BackfillConfig.enabled) {
        runBlocking {
            stage20BackfillService.runBackfill(
                syncMode = stage20BackfillConfig.syncMode,
                ensureReferencedCategories = stage20BackfillConfig.ensureReferencedCategories,
            )
        }
        if (stage20BackfillConfig.exitAfterRun) {
            return
        }
    } else if (stage20BackfillConfig.autoRepairEnabled) {
        runBlocking {
            stage20BackfillService.runAutoRepairIfNeeded()
        }
    }

    val runStage4Backfill = envFlag("STAGE4_RUNTIME_BACKFILL_ON_STARTUP")
    val backfillExitAfterRun = envFlag("STAGE4_RUNTIME_BACKFILL_EXIT_AFTER_RUN")
    if (runStage4Backfill) {
        val backfillBatchSize = System.getenv("STAGE4_RUNTIME_BACKFILL_BATCH_SIZE")
            ?.trim()
            ?.toIntOrNull()
            ?: 500
        runBlocking {
            val service = koinApp.koin.get<Stage4RuntimeBackfillService>()
            service.runHistoricalBackfill(batchSize = backfillBatchSize)
        }
        if (backfillExitAfterRun) {
            return
        }
    }

    val runTrackDedupBackfill = envFlag("TRACK_DEDUP_BACKFILL_ON_STARTUP")
    val trackDedupBackfillExitAfterRun = envFlag("TRACK_DEDUP_BACKFILL_EXIT_AFTER_RUN")
    if (runTrackDedupBackfill) {
        runBlocking {
            val service = koinApp.koin.get<TrackDedupBackfillService>()
            service.runBackfill()
        }
        if (trackDedupBackfillExitAfterRun) {
            return
        }
    }

    val skipTrackTargetGuards = envFlag("TRACK_TARGET_GUARDS_SKIP")
    if (!skipTrackTargetGuards) {
        val guardLimit = System.getenv("TRACK_TARGET_GUARDS_LIMIT")
            ?.trim()
            ?.toIntOrNull()
            ?: 20_000
        runBlocking {
            val service = koinApp.koin.get<TrackTargetPostMigrationGuardService>()
            service.runChecks(limit = guardLimit)
        }
    }

    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host,
        module = { shoppingAssistantModule(config) },
    ).start(wait = true)
}

/**
 * Главный Ktor-модуль сервиса ShoppingAssistant.
 *
 * Здесь регистрируем плагины верхнего уровня.
 * Дальше будем выносить auth, users, products в отдельные модули/пакеты.
 */
fun Application.shoppingAssistantModule(config: ServerConfig) {
    configureMonitoring()
    configureSerialization()
    configureCors(config)
    configureSecurity(config)
    configureRouting()
    configureSubscriptionsEngine()
    configurePriceFetcherEngine()
    configureTrackTop10RefreshEngine()
    configureCatalogReadinessSnapshotEngine()
    configureCatalogGovernanceRefreshEngine()
}

private fun envFlag(name: String): Boolean =
    (System.getenv(name) ?: "false").equals("true", ignoreCase = true)

private fun envFlagDefaultTrue(name: String): Boolean =
    !(System.getenv(name) ?: "true").equals("false", ignoreCase = true)

private fun enforceCatalogContractsOnStartup() {
    val taxonomyReport = TaxonomyValidator().validate(
        categories = CatalogSeed.categories,
        aliases = CatalogSeed.categoryAliases,
        mappings = CatalogSeed.googleMappings,
        browseNodes = CatalogSeed.browseNodes,
        aliasEntries = CatalogSeed.aliasEntries,
    )
    check(taxonomyReport.isValid) {
        "Catalog taxonomy contract validation failed on server startup.\n${taxonomyReport.summary()}"
    }

    val facetSchemaReport = FacetSchemaValidator().validate(
        categories = CatalogSeed.categories,
        definitions = CatalogSeed.facetDefinitions,
        presets = CatalogSeed.facetPresets,
        collections = CatalogSeed.facetCollections,
    )
    check(facetSchemaReport.isValid) {
        "Catalog facet schema contract validation failed on server startup.\n${facetSchemaReport.summary()}"
    }
}
