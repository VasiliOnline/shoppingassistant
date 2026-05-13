package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.localoffer.LocalOfferDraftRuntime
import com.example.shoppingassistant.server.localoffer.LocalOfferDraftRuntimeAdapter
import com.example.shoppingassistant.server.shortlisting.ShortListingBackendService
import com.example.shoppingassistant.server.shortlisting.ShortListingBackendServiceImpl
import com.example.shoppingassistant.server.storage.PhotoStorageService
import com.example.shoppingassistant.server.vision.VisionService
import org.koin.dsl.module

val backendShortListingModule = module {
    single<ShortListingBackendService> {
        ShortListingBackendServiceImpl(
            catalogReadRepository = get<CatalogReadRepository>(),
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            stage4ExecutionLayer = get<Stage4ExecutionLayer>(),
            visionService = get<VisionService>(),
            photoStorageService = get<PhotoStorageService>(),
        )
    }
    single<LocalOfferDraftRuntime> {
        LocalOfferDraftRuntimeAdapter(
            shortListingBackendService = get<ShortListingBackendService>(),
        )
    }
}
