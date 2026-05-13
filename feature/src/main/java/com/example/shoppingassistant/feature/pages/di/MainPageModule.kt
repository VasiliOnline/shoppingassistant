// Last synced: 2025-12-21 15:04:07
// GPT: task=MainPage part=di/MainPageModule role=di v=1
package com.example.shoppingassistant.feature.pages.di

import com.example.shoppingassistant.core.data.ProductRepository
import com.example.shoppingassistant.core.data.nearby.NearbyFiltersStorage
import com.example.shoppingassistant.core.data.nearby.NearbyFiltersStorageImpl
import com.example.shoppingassistant.core.data.suggest.ProductSuggestRepository
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.data.link.LinkTemplateBuilderTask
import com.example.shoppingassistant.core.data.link.LinkTemplateMapperTask
import com.example.shoppingassistant.core.usecase.SearchOffersWithFacetsUseCase
import com.example.shoppingassistant.domain.auth.GetCurrentUserUseCase
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.facet.GetFacetCountsTask
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferTask
import com.example.shoppingassistant.domain.template.TemplateHistoryRepository
import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.presets.TemplatePresetsRepository
import com.example.shoppingassistant.domain.template.presets.generate.GenerateTemplatePresetsTask
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.template.status.TemplateStatusResolver
import com.example.shoppingassistant.domain.profile.GetProfileCacheTask
import com.example.shoppingassistant.domain.localoffer.ConfirmLocalOfferGeoSnapshotTask
import com.example.shoppingassistant.domain.localoffer.CreateLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.CreateOrResumeLocalOfferSessionTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPreviewTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPublishPreflightTask
import com.example.shoppingassistant.domain.localoffer.PublishLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.UpdateLocalOfferDraftReviewTask
import com.example.shoppingassistant.domain.visualsearch.BindVisualSearchQueryUseCase
import com.example.shoppingassistant.domain.visualsearch.GetVisualSearchRecoveryPlanUseCase
import com.example.shoppingassistant.domain.visualsearch.NormalizeVisualSearchDraftUseCase
import com.example.shoppingassistant.domain.visualsearch.ReuseVisualSearchContextUseCase
import com.example.shoppingassistant.domain.visualsearch.TrackVisualSearchEventsUseCase
import com.example.shoppingassistant.feature.pages.main.suggest.MainSuggestEngine
import com.example.shoppingassistant.feature.pages.main.suggest.MainSuggestEngineImpl
import com.example.shoppingassistant.feature.pages.main.state.MainPageViewModel
import com.example.shoppingassistant.feature.pages.localoffer.LocalOfferFlowSessionStore
import com.example.shoppingassistant.feature.pages.localoffer.LocalOfferFlowSessionStoreImpl
import com.example.shoppingassistant.feature.pages.localoffer.LocalOfferViewModel
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStoreTask
import com.example.shoppingassistant.feature.pages.useroffers.sync.UserOffersSyncQueueStore
import com.example.shoppingassistant.feature.pages.useroffers.sync.UserOffersSyncQueueStoreTask
import com.example.shoppingassistant.feature.pages.useroffers.sync.UserOffersSyncTask
import com.example.shoppingassistant.feature.pages.useroffers.sync.UserOffersSyncTaskImpl
import com.example.shoppingassistant.feature.auth.DebugAuthStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel

val mainPageModule = module {
    single<UserOffersCreatedStore> { UserOffersCreatedStoreTask(androidContext()) }
    single<UserOffersSyncQueueStore> { UserOffersSyncQueueStoreTask(androidContext()) }
    single<LocalOfferFlowSessionStore> { LocalOfferFlowSessionStoreImpl(androidContext()) }
    single<NearbyFiltersStorage> { NearbyFiltersStorageImpl(androidContext()) }
    single<UserOffersSyncTask> {
        UserOffersSyncTaskImpl(
            queueStore = get<UserOffersSyncQueueStore>(),
            createdStore = get<UserOffersCreatedStore>(),
            createTask = get<CreateTrackedOfferTask>(),
        )
    }

    single<MainSuggestEngine> {
        MainSuggestEngineImpl(
            catalogRepository = get<CatalogReadRepository>(),
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            categoryAliasRepository = get<CategoryAliasRepository>(),
            productSuggestRepository = get<ProductSuggestRepository>(),
            historyRepository = get<TemplateHistoryRepository>(),
            presetsRepository = get<TemplatePresetsRepository>(),
            statusResolver = get<TemplateStatusResolver>(),
        )
    }

    viewModel {
        MainPageViewModel(
            repository = get<ProductRepository>(),
            rankService = get<RankService>(),
            liveValuesRepository = get<CatalogLiveValuesRepository>(),
            catalogRepository = get<CatalogReadRepository>(),
            catalogTaxonomyRepository = get<CatalogTaxonomyRepository>(),
            constraintsResolver = get<CatalogConstraintsResolver>(),
            getFacetCounts = get<GetFacetCountsTask>(),
            getCurrentUser = get<GetCurrentUserUseCase>(),
            getProfileCache = get<GetProfileCacheTask>(),
            createdStore = get<UserOffersCreatedStore>(),
            userOffersSyncTask = get<UserOffersSyncTask>(),
            linkTemplateBuilder = get<LinkTemplateBuilderTask>(),
            linkTemplateMapper = get<LinkTemplateMapperTask>(),
            suggestEngine = get<MainSuggestEngine>(),
            templateIdTask = get<TemplateIdTask>(),
            templateHistoryRepository = get<TemplateHistoryRepository>(),
            trackRepository = get<TrackRepository>(),
            templatePresetsRepository = get<TemplatePresetsRepository>(),
            generateTemplatePresetsTask = get<GenerateTemplatePresetsTask>(),
            reuseVisualSearchContext = get<ReuseVisualSearchContextUseCase>(),
            normalizeVisualSearchDraft = get<NormalizeVisualSearchDraftUseCase>(),
            bindVisualSearchQuery = get<BindVisualSearchQueryUseCase>(),
            getVisualSearchRecoveryPlan = get<GetVisualSearchRecoveryPlanUseCase>(),
            trackVisualSearchEvents = get<TrackVisualSearchEventsUseCase>(),
            searchOffersWithFacets = get<SearchOffersWithFacetsUseCase>(),
            nearbyFiltersStorage = get<NearbyFiltersStorage>(),
            debugAuthStore = get<DebugAuthStore>(),
        )
    }

    viewModel {
        LocalOfferViewModel(
            createOrResumeSession = get<CreateOrResumeLocalOfferSessionTask>(),
            confirmGeoSnapshot = get<ConfirmLocalOfferGeoSnapshotTask>(),
            createDraft = get<CreateLocalOfferDraftTask>(),
            getDraft = get<GetLocalOfferDraftTask>(),
            updateDraftReview = get<UpdateLocalOfferDraftReviewTask>(),
            getPreview = get<GetLocalOfferPreviewTask>(),
            getPublishPreflight = get<GetLocalOfferPublishPreflightTask>(),
            publishDraft = get<PublishLocalOfferDraftTask>(),
            createdStore = get<UserOffersCreatedStore>(),
            sessionStore = get<LocalOfferFlowSessionStore>(),
        )
    }
}


