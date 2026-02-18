// Last synced: 2025-12-16 15:27:10
package com.example.shoppingassistant.core.di

import androidx.room.Room
import com.example.shoppingassistant.core.analytics.LogcatProfileAnalyticsLogger
import com.example.shoppingassistant.core.analytics.ProfileAnalyticsLogger
import com.example.shoppingassistant.core.config.AuthRepositoryConfig
import com.example.shoppingassistant.core.data.AttributeService
import com.example.shoppingassistant.core.data.ProductRepository
import com.example.shoppingassistant.core.data.ProductRepositoryImpl
import com.example.shoppingassistant.core.data.auth.AuthTokenStorage
import com.example.shoppingassistant.core.data.auth.InMemoryAuthRepository
import com.example.shoppingassistant.core.data.auth.RemoteAuthRepository
import com.example.shoppingassistant.core.data.auth.SecureAuthTokenStorage
import com.example.shoppingassistant.core.data.catalog.CategoryAliasRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.AliasEntryRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.BrowseNodeRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.CategoryOfferCountsRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.CatalogDataSource
import com.example.shoppingassistant.core.data.catalog.FacetCollectionRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.FacetDefinitionRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.FacetPresetRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.FacetSchemaGate
import com.example.shoppingassistant.core.data.catalog.CatalogRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.GoogleTaxonomyMappingRepositoryImpl
import com.example.shoppingassistant.core.data.catalog.SeededCatalogDataSource
import com.example.shoppingassistant.core.data.catalog.TaxonomyGate
import com.example.shoppingassistant.core.data.catalog.constraints.CatalogConstraintsResolverImpl
import com.example.shoppingassistant.core.data.db.AppDatabase
import com.example.shoppingassistant.core.data.db.productDatabaseName
import com.example.shoppingassistant.core.data.db.CategoryOfferCountsDao
import com.example.shoppingassistant.core.data.facet.FacetCountsRepositoryImpl
import com.example.shoppingassistant.core.data.offers.OfferAlertsRemoteDataSource
import com.example.shoppingassistant.core.data.offers.OfferAlertsRemoteDataSourceImpl
import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSourceImpl
import com.example.shoppingassistant.core.data.offers.TrackedOfferRemoteRepository
import com.example.shoppingassistant.core.data.useroffers.actions.UserOffersActionsRemoteDataSource
import com.example.shoppingassistant.core.data.useroffers.actions.UserOffersActionsRemoteDataSourceImpl
import com.example.shoppingassistant.core.data.useroffers.actions.UserOffersActionsRepositoryImpl
import com.example.shoppingassistant.core.data.useroffers.UserOffersRemoteDataSource
import com.example.shoppingassistant.core.data.useroffers.UserOffersRemoteDataSourceImpl
import com.example.shoppingassistant.core.data.useroffers.UserOffersRepositoryImpl
import com.example.shoppingassistant.core.data.useroffers.price.UserOffersPriceRemoteDataSource
import com.example.shoppingassistant.core.data.useroffers.price.UserOffersPriceRemoteDataSourceImpl
import com.example.shoppingassistant.core.data.useroffers.price.UserOffersPriceRepositoryImpl
import com.example.shoppingassistant.core.data.tracks.TrackEventsRepositoryImpl
import com.example.shoppingassistant.core.data.tracks.TrackFilterOptionsHistoryStore
import com.example.shoppingassistant.core.data.tracks.TrackFilterOptionsHistoryStoreImpl
import com.example.shoppingassistant.core.data.tracks.TrackFilterOptionsRepositoryImpl
import com.example.shoppingassistant.core.data.tracks.TrackFiltersStore
import com.example.shoppingassistant.core.data.tracks.TrackFiltersStoreImpl
import com.example.shoppingassistant.core.data.tracks.TracksRemoteDataSource
import com.example.shoppingassistant.core.data.tracks.TracksRemoteDataSourceImpl
import com.example.shoppingassistant.core.data.tracks.TracksRepositoryImpl
import com.example.shoppingassistant.core.data.tracks.TemplateSubscriptionsToTracksMigration
import com.example.shoppingassistant.core.data.profile.ProfilePreferencesStorage
import com.example.shoppingassistant.core.data.profile.ProfileSettingsStore
import com.example.shoppingassistant.core.data.profile.ProfileRepositoryImpl
import com.example.shoppingassistant.core.data.menu.UserPanelStorage
import com.example.shoppingassistant.core.data.menu.UserPanelStorageImpl
import com.example.shoppingassistant.core.data.menu.UserPanelStore
import com.example.shoppingassistant.core.data.storage.PhotoStorageRemoteRepository
import com.example.shoppingassistant.core.data.subscriptions.db.SubscriptionsDatabase
import com.example.shoppingassistant.core.data.subscriptions.db.SubscriptionsRepositoryImpl
import com.example.shoppingassistant.core.data.subscriptions.db.SUBSCRIPTIONS_MIGRATION_1_2
import com.example.shoppingassistant.core.data.subscriptions.db.subscriptionsDatabaseName
import com.example.shoppingassistant.core.data.suggest.ProductSuggestRepositoryImpl
import com.example.shoppingassistant.core.data.suggest.ProductSuggestRepository
import com.example.shoppingassistant.core.data.template.TemplateIdTaskImpl
import com.example.shoppingassistant.core.data.template.presets.TemplatePresetsRepositoryImpl
import com.example.shoppingassistant.core.data.template.presets.generate.GenerateTemplatePresetsTaskImpl
import com.example.shoppingassistant.core.data.template.presets.generate.PresetAnchorSourceImpl
import com.example.shoppingassistant.core.data.template.draft.SaveTemplateDraftTaskImpl
import com.example.shoppingassistant.core.data.template.status.TemplateStatusResolverImpl
import com.example.shoppingassistant.core.data.templatehistory.TemplateHistoryRepositoryImpl
import com.example.shoppingassistant.core.data.templatehistory.db.TemplateHistoryDatabase
import com.example.shoppingassistant.core.data.templatehistory.db.templateHistoryDatabaseName
import com.example.shoppingassistant.core.data.templatelists.TemplateListsRepositoryImpl
import com.example.shoppingassistant.core.data.templatesubscriptions.db.TemplateSubscriptionsDatabase
import com.example.shoppingassistant.core.data.templatesubscriptions.db.templateSubscriptionsDatabaseName
import com.example.shoppingassistant.core.data.ugc.UgcMirrorRemoteRepository
import com.example.shoppingassistant.core.data.ugc.draft.DraftOffersDatabase
import com.example.shoppingassistant.core.data.ugc.draft.DraftOffersRepositoryImpl
import com.example.shoppingassistant.core.data.ugc.draft.draftOffersDatabaseName
import com.example.shoppingassistant.core.data.vision.VisionRemoteRepository
import com.example.shoppingassistant.core.data.vision.VisionCacheStorage
import com.example.shoppingassistant.core.data.vision.VisionUsageRemoteRepository
import com.example.shoppingassistant.core.data.vision.VisionUsageStorage
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.core.network.createBackendHttpClient
import com.example.shoppingassistant.core.push.TrackingInboxRemoteDataSource
import com.example.shoppingassistant.core.push.TrackingInboxRemoteDataSourceImpl
import com.example.shoppingassistant.core.push.TrackingNotificationsSettingsStorage
import com.example.shoppingassistant.core.push.TrackingNotificationsSettingsStorageImpl
import com.example.shoppingassistant.core.push.TrackingPushScheduler
import com.example.shoppingassistant.core.push.TrackingPushSchedulerImpl
import com.example.shoppingassistant.core.push.TrackingPushStateStorage
import com.example.shoppingassistant.core.push.TrackingPushStateStorageImpl
import com.example.shoppingassistant.core.push.TrackingPushTokensRemoteDataSource
import com.example.shoppingassistant.core.push.TrackingPushTokensRemoteDataSourceImpl
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CategoryOfferCountsRepository
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import com.example.shoppingassistant.domain.catalog.QueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21ApplQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21AutoQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21BeautyQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21FashQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21FoodQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21TechGoldenRunner
import com.example.shoppingassistant.domain.catalog.Stage21HomeQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21HybridQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21KidsQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21PetsQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21SportQueryRouter
import com.example.shoppingassistant.domain.catalog.Stage21TechQueryRouter
import com.example.shoppingassistant.domain.catalog.SeedAliasFirstQueryRouter
import com.example.shoppingassistant.domain.catalog.TaxonomyValidator
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.facet.FacetCountsRepository
import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import com.example.shoppingassistant.domain.facet.FacetSchemaValidator
import com.example.shoppingassistant.domain.offers.TrackedOfferRepository
import com.example.shoppingassistant.domain.useroffers.UserOffersActionsRepository
import com.example.shoppingassistant.domain.useroffers.UserOffersPriceRepository
import com.example.shoppingassistant.domain.useroffers.UserOffersRepository
import com.example.shoppingassistant.domain.profile.ExternalLinksRepository
import com.example.shoppingassistant.domain.profile.ProfileCacheRepository
import com.example.shoppingassistant.domain.profile.ProfileRepository
import com.example.shoppingassistant.domain.profile.ProfileSettingsRepository
import com.example.shoppingassistant.domain.storage.PhotoStorageRepository
import com.example.shoppingassistant.domain.subscriptions.SubscriptionsRepository
import com.example.shoppingassistant.domain.tracks.TrackEventsRepository
import com.example.shoppingassistant.domain.tracks.TrackFilterOptionsRepository
import com.example.shoppingassistant.domain.tracks.TrackOffersRepository
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.TopOffersRepository
import com.example.shoppingassistant.domain.template.TemplateHistoryRepository
import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.TemplateListsRepository
import com.example.shoppingassistant.domain.template.draft.SaveTemplateDraftTask
import com.example.shoppingassistant.domain.template.presets.TemplatePresetsRepository
import com.example.shoppingassistant.domain.template.presets.generate.GenerateTemplatePresetsTask
import com.example.shoppingassistant.domain.template.presets.generate.PresetAnchorSource
import com.example.shoppingassistant.domain.template.status.TemplateStatusResolver
import com.example.shoppingassistant.domain.ugc.UgcMirrorRepository
import com.example.shoppingassistant.domain.ugc.draft.DraftOffersRepository
import com.example.shoppingassistant.domain.vision.VisionRepository
import com.example.shoppingassistant.domain.vision.VisionUsageRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Core DI module: база данных, репозитории и shared-инфраструктура.
 *
 * Важно:
 * - наружу отдаём интерфейсы домена (SubscriptionsRepository, AuthRepository и т.д.)
 * - конкретные реализации живут в core.
 */
val coreModule: Module = module {

    // --- Room database & DAO (products) ---
    single {
        Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            productDatabaseName,
        )
            .addCallback(AppDatabase.SeedCallback)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<AppDatabase>().productDao() }
    single { get<AppDatabase>().categoryOfferCountsDao() }

    // --- Room database & DAO (subscriptions) ---
    single {
        Room.databaseBuilder(
            get(),
            SubscriptionsDatabase::class.java,
            subscriptionsDatabaseName,
        )
            .addMigrations(SUBSCRIPTIONS_MIGRATION_1_2)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<SubscriptionsDatabase>().subscriptionsDao() }

    // --- Room database & DAO (template history) ---
    single {
        Room.databaseBuilder(
            get(),
            TemplateHistoryDatabase::class.java,
            templateHistoryDatabaseName,
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<TemplateHistoryDatabase>().templateHistoryDao() }

    // --- Room database & DAO (template subscriptions) ---
    single {
        Room.databaseBuilder(
            get(),
            TemplateSubscriptionsDatabase::class.java,
            templateSubscriptionsDatabaseName,
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<TemplateSubscriptionsDatabase>().templateSubscriptionsDao() }

    // --- Room database & DAO (draft offers) ---
    single {
        Room.databaseBuilder(
            get(),
            DraftOffersDatabase::class.java,
            draftOffersDatabaseName,
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<DraftOffersDatabase>().draftOffersDao() }

    // --- HTTP client ---
    single<BackendClient> { createBackendHttpClient() }

    // --- Core services ---
    single { AttributeService(get()) }
    single<ProfileAnalyticsLogger> { LogcatProfileAnalyticsLogger() }

    // --- Facet counts ---
    single { FacetCountsRepositoryImpl(get()) }
    single<FacetCountsRepository> { get<FacetCountsRepositoryImpl>() }

    // --- Catalog ---
    single { SeededCatalogDataSource() }
    single<CatalogDataSource> { get<SeededCatalogDataSource>() }

    single { CatalogRepositoryImpl(get()) }
    single<CatalogRepository> { get<CatalogRepositoryImpl>() }

    single { CatalogConstraintsResolverImpl() }
    single<CatalogConstraintsResolver> { get<CatalogConstraintsResolverImpl>() }

    // --- Category aliases ---
    single { CategoryAliasRepositoryImpl() }
    single<CategoryAliasRepository> { get<CategoryAliasRepositoryImpl>() }

    // --- Stage 2.0: browse nodes + typed aliases ---
    single { BrowseNodeRepositoryImpl() }
    single<BrowseNodeRepository> { get<BrowseNodeRepositoryImpl>() }

    single { AliasEntryRepositoryImpl() }
    single<AliasEntryRepository> { get<AliasEntryRepositoryImpl>() }

    // --- Google taxonomy mappings ---
    single { GoogleTaxonomyMappingRepositoryImpl() }
    single<GoogleTaxonomyMappingRepository> { get<GoogleTaxonomyMappingRepositoryImpl>() }

    // --- Taxonomy validation gate ---
    single { TaxonomyValidator() }
    single { TaxonomyGate(get(), get(), get(), get(), get(), get()) }

    // --- Stage 3.0 facet schema: definitions/presets/collections + gate ---
    single { FacetDefinitionRepositoryImpl() }
    single<FacetDefinitionRepository> { get<FacetDefinitionRepositoryImpl>() }

    single { FacetPresetRepositoryImpl() }
    single<FacetPresetRepository> { get<FacetPresetRepositoryImpl>() }

    single { FacetCollectionRepositoryImpl() }
    single<FacetCollectionRepository> { get<FacetCollectionRepositoryImpl>() }

    single { FacetSchemaValidator() }
    single { FacetSchemaGate(get(), get(), get(), get(), get()) }

    // --- Stage 2.1 query routing (TECH + APPL + HOME + FASH + BEAUTY + KIDS + FOOD + PETS + SPORT + AUTO) ---
    single { Stage21TechGoldenRunner(get()) }
    single { Stage21TechQueryRouter() }
    single { Stage21ApplQueryRouter() }
    single { Stage21HomeQueryRouter() }
    single { Stage21FashQueryRouter() }
    single { Stage21BeautyQueryRouter() }
    single { Stage21KidsQueryRouter() }
    single { Stage21FoodQueryRouter() }
    single { Stage21PetsQueryRouter() }
    single { Stage21SportQueryRouter() }
    single { Stage21AutoQueryRouter() }
    single { Stage21HybridQueryRouter(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { SeedAliasFirstQueryRouter(get(), get<Stage21HybridQueryRouter>()) }
    single<QueryRouter> { get<SeedAliasFirstQueryRouter>() }

    // --- Category offer counts ---
    single { CategoryOfferCountsRepositoryImpl(get<CategoryOfferCountsDao>()) }
    single<CategoryOfferCountsRepository> { get<CategoryOfferCountsRepositoryImpl>() }

    // --- Products ---
    single { ProductRepositoryImpl(dao = get(), rankService = get()) }
    single<ProductRepository> { get<ProductRepositoryImpl>() }

    // --- Product suggestions (anchors) ---
    single { ProductSuggestRepositoryImpl(get()) }
    single<ProductSuggestRepository> { get<ProductSuggestRepositoryImpl>() }

    // --- Template snapshot ID + history ---
    single { TemplateIdTaskImpl() }
    single<TemplateIdTask> { get<TemplateIdTaskImpl>() }

    // --- Template presets (popular + generated) ---
    single { TemplatePresetsRepositoryImpl(get()) }
    single<TemplatePresetsRepository> { get<TemplatePresetsRepositoryImpl>() }

    single { PresetAnchorSourceImpl(get()) }
    single<PresetAnchorSource> { get<PresetAnchorSourceImpl>() }

    single {
        GenerateTemplatePresetsTaskImpl(
            anchorSource = get<PresetAnchorSource>(),
            dao = get(),
            catalogRepository = get(),
            constraintsResolver = get(),
            idTask = get(),
            presetsRepository = get(),
        )
    }
    single<GenerateTemplatePresetsTask> { get<GenerateTemplatePresetsTaskImpl>() }

    single { TemplateStatusResolverImpl(get()) }
    single<TemplateStatusResolver> { get<TemplateStatusResolverImpl>() }

    single { TemplateHistoryRepositoryImpl(dao = get(), idTask = get()) }
    single<TemplateHistoryRepository> { get<TemplateHistoryRepositoryImpl>() }

    single { TemplateListsRepositoryImpl(get()) }
    single<TemplateListsRepository> { get<TemplateListsRepositoryImpl>() }

    // --- Template drafts ---
    single { SaveTemplateDraftTaskImpl(get()) }
    single<SaveTemplateDraftTask> { get<SaveTemplateDraftTaskImpl>() }

    // --- Bearer storage ---
    single { SecureAuthTokenStorage(get()) }
    single<AuthTokenStorage> { get<SecureAuthTokenStorage>() }

    // --- AuthRepository ---
    single { RemoteAuthRepository(backendClient = get(), tokenStorage = get()) }
    single { InMemoryAuthRepository() }
    single<AuthRepository> {
        when (AuthRepositoryConfig.mode) {
            AuthRepositoryConfig.Mode.REMOTE -> get<RemoteAuthRepository>()
            AuthRepositoryConfig.Mode.IN_MEMORY -> get<InMemoryAuthRepository>()
        }
    }

    // --- Subscriptions repository (local, auth-gated, user-scoped) ---
    single { SubscriptionsRepositoryImpl(dao = get(), authRepository = get()) }
    single<SubscriptionsRepository> { get<SubscriptionsRepositoryImpl>() }

    // --- Profile / photo / vision / ugc ---
    single { ProfilePreferencesStorage(get()) }
    single<ProfileSettingsRepository> { get<ProfilePreferencesStorage>() }
    single<ExternalLinksRepository> { get<ProfilePreferencesStorage>() }
    single<ProfileCacheRepository> { get<ProfilePreferencesStorage>() }
    single { ProfileSettingsStore(get()) }
    single { UserPanelStorageImpl(get()) }
    single<UserPanelStorage> { get<UserPanelStorageImpl>() }
    single { UserPanelStore(get()) }

    single { ProfileRepositoryImpl(get()) }
    single<ProfileRepository> { get<ProfileRepositoryImpl>() }

    single { PhotoStorageRemoteRepository(get()) }
    single<PhotoStorageRepository> { get<PhotoStorageRemoteRepository>() }

    single { VisionCacheStorage(get()) }
    single { VisionRemoteRepository(get(), get()) }
    single<VisionRepository> { get<VisionRemoteRepository>() }

    single { VisionUsageStorage(get()) }
    single { VisionUsageRemoteRepository(get(), get()) }
    single<VisionUsageRepository> { get<VisionUsageRemoteRepository>() }

    single { UgcMirrorRemoteRepository(get()) }
    single<UgcMirrorRepository> { get<UgcMirrorRemoteRepository>() }

    // --- Draft offers (local) ---
    single { DraftOffersRepositoryImpl(get()) }
    single<DraftOffersRepository> { get<DraftOffersRepositoryImpl>() }

    // --- Offers remote ---
    single { OfferRemoteDataSourceImpl(get(), get()) }
    single<OfferRemoteDataSource> { get<OfferRemoteDataSourceImpl>() }

    single { OfferAlertsRemoteDataSourceImpl(get(), get()) }
    single<OfferAlertsRemoteDataSource> { get<OfferAlertsRemoteDataSourceImpl>() }

    // --- User offers ---
    single { UserOffersRemoteDataSourceImpl(get(), get()) }
    single<UserOffersRemoteDataSource> { get<UserOffersRemoteDataSourceImpl>() }

    single { UserOffersRepositoryImpl(get()) }
    single<UserOffersRepository> { get<UserOffersRepositoryImpl>() }

    single { UserOffersActionsRemoteDataSourceImpl(get(), get()) }
    single<UserOffersActionsRemoteDataSource> { get<UserOffersActionsRemoteDataSourceImpl>() }

    single { UserOffersActionsRepositoryImpl(get()) }
    single<UserOffersActionsRepository> { get<UserOffersActionsRepositoryImpl>() }

    single { UserOffersPriceRemoteDataSourceImpl(get(), get()) }
    single<UserOffersPriceRemoteDataSource> { get<UserOffersPriceRemoteDataSourceImpl>() }

    single { UserOffersPriceRepositoryImpl(get()) }
    single<UserOffersPriceRepository> { get<UserOffersPriceRepositoryImpl>() }

    // --- Tracked offers ---
    single { TrackedOfferRemoteRepository(get(), get()) }
    single<TrackedOfferRepository> { get<TrackedOfferRemoteRepository>() }

    // --- Tracked items (tracks + top-10) ---
    single { TrackFiltersStoreImpl(get()) }
    single<TrackFiltersStore> { get<TrackFiltersStoreImpl>() }

    single { TracksRemoteDataSourceImpl(get(), get()) }
    single<TracksRemoteDataSource> { get<TracksRemoteDataSourceImpl>() }

    single {
        TracksRepositoryImpl(
            subscriptionsRepository = get(),
            filtersStore = get(),
            authRepository = get(),
            remoteDataSource = get(),
        )
    }
    single<TrackRepository> { get<TracksRepositoryImpl>() }
    single<TopOffersRepository> { get<TracksRepositoryImpl>() }
    single<TrackOffersRepository> { get<TracksRepositoryImpl>() }
    single {
        TemplateSubscriptionsToTracksMigration(
            context = get(),
            templateSubscriptionsDao = get(),
            trackRepository = get(),
        )
    }
    single { TrackEventsRepositoryImpl(remoteDataSource = get()) }
    single<TrackEventsRepository> { get<TrackEventsRepositoryImpl>() }
    single { TrackFilterOptionsHistoryStoreImpl(get()) }
    single<TrackFilterOptionsHistoryStore> { get<TrackFilterOptionsHistoryStoreImpl>() }
    single {
        TrackFilterOptionsRepositoryImpl(
            trackRepository = get(),
            nearbyFiltersStorage = get(),
            facetCountsRepository = get(),
            categoryAliasRepository = get(),
            historyStore = get(),
        )
    }
    single<TrackFilterOptionsRepository> { get<TrackFilterOptionsRepositoryImpl>() }

    // --- Subscriptions "push" (polling inbox + system notifications) ---
    single { TrackingInboxRemoteDataSourceImpl(get(), get()) }
    single<TrackingInboxRemoteDataSource> { get<TrackingInboxRemoteDataSourceImpl>() }

    single { TrackingNotificationsSettingsStorageImpl(get()) }
    single<TrackingNotificationsSettingsStorage> { get<TrackingNotificationsSettingsStorageImpl>() }

    single { TrackingPushStateStorageImpl(get()) }
    single<TrackingPushStateStorage> { get<TrackingPushStateStorageImpl>() }

    single { TrackingPushSchedulerImpl(get()) }
    single<TrackingPushScheduler> { get<TrackingPushSchedulerImpl>() }

    single { TrackingPushTokensRemoteDataSourceImpl(get(), get()) }
    single<TrackingPushTokensRemoteDataSource> { get<TrackingPushTokensRemoteDataSourceImpl>() }
}
