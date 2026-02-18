package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.domain.ingest.DefaultSourceCatalog
import com.example.shoppingassistant.domain.ingest.DefaultSourceResolver
import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.SourceResolver
import com.example.shoppingassistant.domain.ingest.UrlNormalizer
import com.example.shoppingassistant.domain.ingest.DefaultUrlNormalizer
import com.example.shoppingassistant.server.ingest.registry.InMemorySourceRegistry
import org.koin.dsl.module

val backendSourcesModule = module {
    single<UrlNormalizer> { DefaultUrlNormalizer() }
    single<SourceRegistry> {
        InMemorySourceRegistry(entries = DefaultSourceCatalog.entries)
    }
    single<SourceResolver> { DefaultSourceResolver(get(), get()) }
}
