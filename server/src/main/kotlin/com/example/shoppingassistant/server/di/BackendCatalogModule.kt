package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistryProvider
import com.example.shoppingassistant.server.catalog.GovernanceCatalogCanonicalModelRegistryProvider
import com.example.shoppingassistant.domain.catalog.CatalogWriteRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import com.example.shoppingassistant.server.catalog.CatalogStage20BackfillService
import com.example.shoppingassistant.server.catalog.CatalogStage20BackfillServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogRepositoryImpl
import com.example.shoppingassistant.server.catalog.CatalogLiveValuesRepositoryImpl
import com.example.shoppingassistant.server.catalog.CatalogReadinessGovernanceService
import com.example.shoppingassistant.server.catalog.CatalogReadinessGovernanceServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogReadinessInventoryService
import com.example.shoppingassistant.server.catalog.CatalogReadinessInventoryServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogGovernanceReportsService
import com.example.shoppingassistant.server.catalog.CatalogGovernanceReportsServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogGovernanceReportRepository
import com.example.shoppingassistant.server.catalog.CatalogGovernanceHookDeliveryRepository
import com.example.shoppingassistant.server.catalog.CatalogGovernanceHookExecutor
import com.example.shoppingassistant.server.catalog.CatalogGovernanceHookExecutorImpl
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRepositoryImpl
import com.example.shoppingassistant.server.catalog.DatabaseCatalogGovernanceReportRepository
import com.example.shoppingassistant.server.catalog.DatabaseCatalogGovernanceHookDeliveryRepository
import com.example.shoppingassistant.server.catalog.CatalogGovernanceProjectionPolicy
import com.example.shoppingassistant.server.catalog.CatalogGovernanceWorkflowService
import com.example.shoppingassistant.server.catalog.CatalogGovernanceAttributePolicyResolver
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRuntimeInvalidator
import com.example.shoppingassistant.server.catalog.CatalogGovernanceCuratedSeedSyncService
import com.example.shoppingassistant.server.catalog.CatalogGovernanceCuratedSeedPackConnector
import com.example.shoppingassistant.server.catalog.CatalogGovernanceOfficialPhonesConnector
import com.example.shoppingassistant.server.catalog.HttpCatalogGovernanceOfficialPageFetcher
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshSurfaceService
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshSurfaceServiceImpl
import com.example.shoppingassistant.server.catalog.DatabaseCatalogGovernanceRefreshRepository
import com.example.shoppingassistant.server.catalog.CatalogGovernanceServingProjectionService
import com.example.shoppingassistant.server.catalog.CatalogAutomationLeaseRepository
import com.example.shoppingassistant.server.catalog.DatabaseCatalogAutomationLeaseRepository
import com.example.shoppingassistant.server.catalog.CatalogOpenApiService
import com.example.shoppingassistant.server.catalog.CatalogOpenApiServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentService
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentServiceImpl
import com.example.shoppingassistant.server.catalog.DatabaseCatalogPhoneModelEnrichmentRepository
import com.example.shoppingassistant.server.catalog.CatalogGovernanceOfficialPhoneEndpointOverlayRepository
import com.example.shoppingassistant.server.catalog.DatabaseCatalogGovernanceOfficialPhoneEndpointOverlayRepository
import com.example.shoppingassistant.server.catalog.CatalogReadinessSnapshotRunner
import com.example.shoppingassistant.server.catalog.CatalogReadinessHistoryService
import com.example.shoppingassistant.server.catalog.CatalogReadinessHistoryServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogReadinessSnapshotRepository
import com.example.shoppingassistant.server.catalog.CatalogRuntimeCompatibilityService
import com.example.shoppingassistant.server.catalog.CatalogRuntimeCompatibilityServiceImpl
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshRunner
import com.example.shoppingassistant.server.catalog.DatabaseCatalogReadinessSnapshotRepository
import com.example.shoppingassistant.server.catalog.FacetSchemaRepositoryImpl
import com.example.shoppingassistant.server.catalog.GovernanceCatalogRuntimeRegistryInvalidator
import com.example.shoppingassistant.server.catalog.GovernanceCatalogCanonicalProductFamilyRegistryProvider
import com.example.shoppingassistant.server.catalog.TaxonomyRepositoryImpl
import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig
import com.example.shoppingassistant.server.config.CatalogReadinessSnapshotConfig
import org.koin.dsl.module

val backendCatalogModule = module {
    single { CatalogRepositoryImpl() }
    single<CatalogReadRepository> { get<CatalogRepositoryImpl>() }
    single<CatalogTaxonomyRepository> { get<CatalogRepositoryImpl>() }
    single<CatalogWriteRepository> { get<CatalogRepositoryImpl>() }
    single<CatalogGovernanceRepository> { CatalogGovernanceRepositoryImpl() }
    single { CatalogGovernanceProjectionPolicy() }
    single { CatalogGovernanceAttributePolicyResolver() }
    single {
        CatalogGovernanceWorkflowService(
            repository = get<CatalogGovernanceRepository>(),
            projectionPolicy = get<CatalogGovernanceProjectionPolicy>(),
            policyResolver = get<CatalogGovernanceAttributePolicyResolver>(),
        )
    }
    single {
        CatalogGovernanceServingProjectionService(
            repository = get<CatalogGovernanceRepository>(),
            policy = get<CatalogGovernanceProjectionPolicy>(),
            workflowService = get<CatalogGovernanceWorkflowService>(),
            runtimeInvalidatorProvider = { get<CatalogGovernanceRuntimeInvalidator>() },
        )
    }
    single {
        CatalogGovernanceCuratedSeedSyncService(
            repository = get<CatalogGovernanceRepository>(),
            projectionService = get<CatalogGovernanceServingProjectionService>(),
        )
    }
    single { DatabaseCatalogGovernanceRefreshRepository() }
    single { CatalogGovernanceCuratedSeedPackConnector() }
    single { HttpCatalogGovernanceOfficialPageFetcher() }
    single { DatabaseCatalogGovernanceOfficialPhoneEndpointOverlayRepository() }
    single<CatalogGovernanceOfficialPhoneEndpointOverlayRepository> {
        get<DatabaseCatalogGovernanceOfficialPhoneEndpointOverlayRepository>()
    }
    single {
        CatalogGovernanceOfficialPhonesConnector(
            fetcher = get<HttpCatalogGovernanceOfficialPageFetcher>(),
            endpointOverlayRepository = get<CatalogGovernanceOfficialPhoneEndpointOverlayRepository>(),
        )
    }
    single<CatalogGovernanceRefreshSurfaceService> {
        CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = get<DatabaseCatalogGovernanceRefreshRepository>(),
            governanceRepository = get<CatalogGovernanceRepository>(),
            workflowService = get<CatalogGovernanceWorkflowService>(),
            projectionService = get<CatalogGovernanceServingProjectionService>(),
            curatedSeedSyncService = get<CatalogGovernanceCuratedSeedSyncService>(),
            phoneModelEnrichmentService = get<CatalogPhoneModelEnrichmentService>(),
            endpointOverlayRepository = get<CatalogGovernanceOfficialPhoneEndpointOverlayRepository>(),
            refreshConfig = get<CatalogGovernanceRefreshConfig>(),
            connectors = listOf(
                get<CatalogGovernanceCuratedSeedPackConnector>(),
                get<CatalogGovernanceOfficialPhonesConnector>(),
            ),
        )
    }
    single { CatalogGovernanceRefreshConfig.fromEnv() }
    single {
        CatalogGovernanceRefreshRunner(
            refreshSurfaceService = get<CatalogGovernanceRefreshSurfaceService>(),
            config = get<CatalogGovernanceRefreshConfig>(),
        )
    }
    single {
        GovernanceCatalogCanonicalProductFamilyRegistryProvider(
            projectionService = get<CatalogGovernanceServingProjectionService>(),
        )
    }
    single {
        GovernanceCatalogCanonicalModelRegistryProvider(
            repository = get<CatalogGovernanceRepository>(),
        )
    }
    single<CatalogGovernanceRuntimeInvalidator> {
        GovernanceCatalogRuntimeRegistryInvalidator(
            familyRegistryProvider = get<GovernanceCatalogCanonicalProductFamilyRegistryProvider>(),
            modelRegistryProvider = get<GovernanceCatalogCanonicalModelRegistryProvider>(),
        )
    }
    single<CatalogCanonicalProductFamilyRegistryProvider> {
        get<GovernanceCatalogCanonicalProductFamilyRegistryProvider>()
    }
    single<CatalogLiveValuesRepository> { CatalogLiveValuesRepositoryImpl(governanceRepository = get()) }
    single { DatabaseCatalogPhoneModelEnrichmentRepository() }
    single<CatalogPhoneModelEnrichmentService> {
        CatalogPhoneModelEnrichmentServiceImpl(
            repository = get<DatabaseCatalogPhoneModelEnrichmentRepository>(),
            endpointOverlayRepository = get<CatalogGovernanceOfficialPhoneEndpointOverlayRepository>(),
        )
    }
    single<CatalogStage20BackfillService> { CatalogStage20BackfillServiceImpl() }
    single { CatalogReadinessSnapshotConfig.fromEnv() }
    single<CatalogReadinessSnapshotRepository> { DatabaseCatalogReadinessSnapshotRepository() }
    single<CatalogAutomationLeaseRepository> { DatabaseCatalogAutomationLeaseRepository() }
    single<CatalogGovernanceReportRepository> { DatabaseCatalogGovernanceReportRepository() }
    single<CatalogGovernanceHookDeliveryRepository> { DatabaseCatalogGovernanceHookDeliveryRepository() }
    single<CatalogGovernanceHookExecutor> {
        CatalogGovernanceHookExecutorImpl(
            deliveryRepository = get<CatalogGovernanceHookDeliveryRepository>(),
        )
    }
    single<CatalogReadinessInventoryService> {
        CatalogReadinessInventoryServiceImpl(
            repository = get<CatalogReadRepository>(),
            taxonomyRepository = get<CatalogTaxonomyRepository>(),
        )
    }
    single {
        CatalogReadinessSnapshotRunner(
            inventoryService = get<CatalogReadinessInventoryService>(),
            snapshotRepository = get<CatalogReadinessSnapshotRepository>(),
            leaseRepository = get<CatalogAutomationLeaseRepository>(),
            governanceReportRepository = get<CatalogGovernanceReportRepository>(),
            hookExecutor = get<CatalogGovernanceHookExecutor>(),
            leaseOwnerId = get<CatalogReadinessSnapshotConfig>().ownerId,
            leaseTtlMs = get<CatalogReadinessSnapshotConfig>().leaseTtlMs,
            leaseHeartbeatIntervalMs = get<CatalogReadinessSnapshotConfig>().leaseHeartbeatIntervalMs,
        )
    }
    single<CatalogReadinessHistoryService> {
        CatalogReadinessHistoryServiceImpl(
            repository = get<CatalogReadRepository>(),
            snapshotRepository = get<CatalogReadinessSnapshotRepository>(),
        )
    }
    single<CatalogReadinessGovernanceService> { CatalogReadinessGovernanceServiceImpl() }
    single<CatalogGovernanceReportsService> {
        CatalogGovernanceReportsServiceImpl(get<CatalogGovernanceReportRepository>())
    }
    single<CatalogOpenApiService> { CatalogOpenApiServiceImpl() }
    single<CatalogRuntimeCompatibilityService> { CatalogRuntimeCompatibilityServiceImpl() }
    single { TaxonomyRepositoryImpl() }
    single<CategoryAliasRepository> { get<TaxonomyRepositoryImpl>() }
    single<BrowseNodeRepository> { get<TaxonomyRepositoryImpl>() }
    single<AliasEntryRepository> { get<TaxonomyRepositoryImpl>() }
    single<GoogleTaxonomyMappingRepository> { get<TaxonomyRepositoryImpl>() }
    single { FacetSchemaRepositoryImpl() }
    single<FacetDefinitionRepository> { get<FacetSchemaRepositoryImpl>() }
    single<FacetPresetRepository> { get<FacetSchemaRepositoryImpl>() }
    single<FacetCollectionRepository> { get<FacetSchemaRepositoryImpl>() }
}

