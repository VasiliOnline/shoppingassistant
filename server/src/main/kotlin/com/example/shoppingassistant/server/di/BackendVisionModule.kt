package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.vision.VisionUsageRepository
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import com.example.shoppingassistant.server.config.VisionConfig
import com.example.shoppingassistant.server.vision.VisionAiPhotoNormalizer
import com.example.shoppingassistant.server.vision.VisionService
import com.example.shoppingassistant.server.vision.VisionServiceImpl
import com.example.shoppingassistant.server.vision.VisionUsageRepositoryImpl
import com.example.shoppingassistant.server.vision.YandexListingVisionAiNormalizer
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import org.koin.dsl.module

val backendVisionModule = module {
    single { VisionConfig.fromEnv() }
    single { ListingVisionAiConfig.fromEnv() }
    single<VisionUsageRepository> { VisionUsageRepositoryImpl(get()) }
    single<VisionAiPhotoNormalizer> {
        YandexListingVisionAiNormalizer(
            catalogRepository = get<CatalogReadRepository>(),
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            config = get<ListingVisionAiConfig>(),
            orchestrator = get(),
        )
    }
    single<VisionService> {
        VisionServiceImpl(
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            usageRepository = get<VisionUsageRepository>(),
            config = get<VisionConfig>(),
            aiPhotoNormalizer = get<VisionAiPhotoNormalizer>(),
        )
    }
}

