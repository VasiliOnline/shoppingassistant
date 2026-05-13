package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.server.config.VisualSearchConfig
import com.example.shoppingassistant.server.visualsearch.InMemoryVisualSearchContextStore
import com.example.shoppingassistant.server.visualsearch.VisualSearchAiDraftNormalizer
import com.example.shoppingassistant.server.visualsearch.VisualSearchContextStore
import com.example.shoppingassistant.server.visualsearch.VisualSearchService
import com.example.shoppingassistant.server.visualsearch.VisualSearchServiceImpl
import com.example.shoppingassistant.server.visualsearch.YandexVisualSearchAiDraftNormalizer
import org.koin.dsl.module

val backendVisualSearchModule = module {
    single { VisualSearchConfig.fromEnv() }
    single<VisualSearchContextStore> { InMemoryVisualSearchContextStore(get()) }
    single<VisualSearchAiDraftNormalizer> {
        YandexVisualSearchAiDraftNormalizer(
            catalogRepository = get<CatalogReadRepository>(),
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            config = get<VisualSearchConfig>(),
            orchestrator = get(),
        )
    }
    single<VisualSearchService> {
        VisualSearchServiceImpl(
            catalogRepository = get<CatalogReadRepository>(),
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            contextStore = get<VisualSearchContextStore>(),
            config = get<VisualSearchConfig>(),
            aiDraftNormalizer = get<VisualSearchAiDraftNormalizer>(),
        )
    }
}
