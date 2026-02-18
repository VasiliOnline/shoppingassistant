package com.example.shoppingassistant.core.di

import android.content.Context
import com.example.shoppingassistant.core.config.IngestReplayConfig
import com.example.shoppingassistant.core.config.SourceRolloutConfig
import com.example.shoppingassistant.core.data.ingest.OfferIngestRepositoryImpl
import com.example.shoppingassistant.core.data.ingest.SourceParser
import com.example.shoppingassistant.core.data.ingest.analytics.IngestAnalyticsLogger
import com.example.shoppingassistant.core.data.ingest.analytics.LogcatIngestAnalyticsLogger
import com.example.shoppingassistant.core.data.ingest.avito.AvitoSourceParser
import com.example.shoppingassistant.core.data.ingest.cache.InMemoryIngestCache
import com.example.shoppingassistant.core.data.ingest.cache.IngestCache
import com.example.shoppingassistant.core.data.ingest.registry.InMemorySourceRegistry
import com.example.shoppingassistant.core.data.ingest.replay.FileIngestReplayStore
import com.example.shoppingassistant.core.data.ingest.replay.IngestReplayStore
import com.example.shoppingassistant.core.data.ingest.replay.NoopIngestReplayStore
import com.example.shoppingassistant.core.network.IngestClient
import com.example.shoppingassistant.core.network.createIngestHttpClient
import com.example.shoppingassistant.domain.ingest.DefaultSourceCatalog
import com.example.shoppingassistant.domain.ingest.DefaultSourceResolver
import com.example.shoppingassistant.domain.ingest.OfferIngestRepository
import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.SourceResolver
import com.example.shoppingassistant.domain.ingest.UrlNormalizer
import com.example.shoppingassistant.domain.ingest.DefaultUrlNormalizer
import java.io.File
import org.koin.core.module.Module
import org.koin.dsl.module

val ingestModule: Module = module {
    single<UrlNormalizer> { DefaultUrlNormalizer() }
    single<SourceRegistry> {
        InMemorySourceRegistry(
            entries = DefaultSourceCatalog.entries,
            rolloutOverrides = SourceRolloutConfig.overrides,
        )
    }
    single<SourceResolver> { DefaultSourceResolver(get(), get()) }

    single<IngestCache> { InMemoryIngestCache() }
    single<IngestAnalyticsLogger> { LogcatIngestAnalyticsLogger() }
    single<IngestReplayStore> {
        if (IngestReplayConfig.enabled) {
            val context: Context = get()
            val dir = File(context.cacheDir, "ingest_replays")
            FileIngestReplayStore(
                rootDir = dir,
                sampleRate = IngestReplayConfig.sampleRate,
                maxEntries = IngestReplayConfig.maxEntries,
                maxBodyBytes = IngestReplayConfig.maxBodyBytes,
            )
        } else {
            NoopIngestReplayStore()
        }
    }

    single<IngestClient> { createIngestHttpClient() }

    single { AvitoSourceParser(get<IngestClient>().client, get()) }
    single<SourceParser> { get<AvitoSourceParser>() }

    single {
        OfferIngestRepositoryImpl(
            parsers = listOf(get<SourceParser>()),
            sourceRegistry = get(),
            urlNormalizer = get(),
            analyticsLogger = get(),
            cache = get(),
        )
    }
    single<OfferIngestRepository> { get<OfferIngestRepositoryImpl>() }
}
