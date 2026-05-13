// Last synced: 2025-12-16 15:49:08
package com.example.shoppingassistant.core.di

import com.example.shoppingassistant.core.data.link.LinkTemplateBuilderImpl
import com.example.shoppingassistant.core.data.link.LinkTemplateBuilderTask
import com.example.shoppingassistant.core.data.link.LinkTemplateMapperImpl
import com.example.shoppingassistant.core.data.link.LinkTemplateMapperTask
import com.example.shoppingassistant.core.usecase.CreateOfferPriceAlertUseCase
import com.example.shoppingassistant.core.usecase.GetOfferDetailsUseCase
import com.example.shoppingassistant.core.usecase.GetTop3FromCandidatesUseCase
import com.example.shoppingassistant.core.usecase.SearchOffersUseCase
import com.example.shoppingassistant.core.usecase.SearchOffersWithFacetsUseCase
import com.example.shoppingassistant.core.usecase.TrackPresetObservabilityEventsUseCase
import com.example.shoppingassistant.domain.auth.ChangePasswordUseCase
import com.example.shoppingassistant.domain.auth.GetCurrentUserUseCase
import com.example.shoppingassistant.domain.auth.LoginUserUseCase
import com.example.shoppingassistant.domain.auth.LogoutUseCase
import com.example.shoppingassistant.domain.auth.RegisterUserUseCase
import com.example.shoppingassistant.domain.catalog.GetCategoryOfferCountsTask
import com.example.shoppingassistant.domain.catalog.GetAliasEntriesTask
import com.example.shoppingassistant.domain.catalog.GetBrowseNodeTask
import com.example.shoppingassistant.domain.catalog.GetBrowseNodesTask
import com.example.shoppingassistant.domain.catalog.GetGoogleMappingForCategoryTask
import com.example.shoppingassistant.domain.catalog.GetGoogleTaxonomyMappingsTask
import com.example.shoppingassistant.domain.catalog.RouteQueryTask
import com.example.shoppingassistant.domain.catalog.RunStage21TechGoldenTask
import com.example.shoppingassistant.domain.facet.GetFacetCountsTask
import com.example.shoppingassistant.domain.facet.GetFacetCollectionByBrowseCodeTask
import com.example.shoppingassistant.domain.facet.GetFacetCollectionTask
import com.example.shoppingassistant.domain.facet.GetFacetCollectionsTask
import com.example.shoppingassistant.domain.facet.GetFacetDefinitionsTask
import com.example.shoppingassistant.domain.facet.GetFacetPresetTask
import com.example.shoppingassistant.domain.facet.GetFacetPresetsTask
import com.example.shoppingassistant.domain.ingest.LoadRawOfferTask
import com.example.shoppingassistant.domain.localoffer.ConfirmLocalOfferGeoSnapshotTask
import com.example.shoppingassistant.domain.localoffer.CreateLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.CreateOrResumeLocalOfferSessionTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPreviewTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPublishPreflightTask
import com.example.shoppingassistant.domain.localoffer.ListLocalOfferDraftsTask
import com.example.shoppingassistant.domain.localoffer.PublishLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.UpdateLocalOfferDraftReviewTask
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferTask
import com.example.shoppingassistant.domain.useroffers.PerformUserOfferActionTask
import com.example.shoppingassistant.domain.useroffers.UpdateUserOfferPriceTask
import com.example.shoppingassistant.domain.useroffers.UpdateUserOfferPricesTask
import com.example.shoppingassistant.domain.profile.ClearExternalLinksTask
import com.example.shoppingassistant.domain.profile.ClearProfileCacheTask
import com.example.shoppingassistant.domain.profile.ConfirmEmailChangeTask
import com.example.shoppingassistant.domain.profile.DeleteAccountTask
import com.example.shoppingassistant.domain.profile.GetExternalLinksTask
import com.example.shoppingassistant.domain.profile.GetProfileViewTask
import com.example.shoppingassistant.domain.profile.GetProfileCacheTask
import com.example.shoppingassistant.domain.profile.GetProfileSettingsTask
import com.example.shoppingassistant.domain.profile.RequestEmailChangeTask
import com.example.shoppingassistant.domain.profile.SaveExternalLinksTask
import com.example.shoppingassistant.domain.profile.SaveProfileCacheTask
import com.example.shoppingassistant.domain.profile.SaveProfileSettingsTask
import com.example.shoppingassistant.domain.shortlisting.CreateShortListingDraftTask
import com.example.shoppingassistant.domain.shortlisting.GetShortListingDraftTask
import com.example.shoppingassistant.domain.shortlisting.GetShortListingPublishPreflightTask
import com.example.shoppingassistant.domain.shortlisting.ListShortListingDraftsTask
import com.example.shoppingassistant.domain.shortlisting.PublishShortListingDraftTask
import com.example.shoppingassistant.domain.shortlisting.UpdateShortListingDraftReviewTask
import com.example.shoppingassistant.domain.profile.UpdateProfilePrivacyTask
import com.example.shoppingassistant.domain.profile.UpdateProfilePhotosTask
import com.example.shoppingassistant.domain.profile.UpdateProfileTask
import com.example.shoppingassistant.domain.profile.UpdateSellerDeliveryZonesTask
import com.example.shoppingassistant.domain.profile.UpdatePublicProfileTask
import com.example.shoppingassistant.domain.storage.UploadPhotoUseCase
import com.example.shoppingassistant.domain.subscriptions.*
import com.example.shoppingassistant.domain.tracks.*
import com.example.shoppingassistant.domain.ugc.MirrorByUrlUseCase
import com.example.shoppingassistant.domain.ugc.draft.CreateDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.DeleteDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.GetDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.ListDraftOffersTask
import com.example.shoppingassistant.domain.ugc.draft.ObserveDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.SaveDraftOfferTask
import com.example.shoppingassistant.domain.visualsearch.BindVisualSearchQueryUseCase
import com.example.shoppingassistant.domain.visualsearch.GetVisualSearchRecoveryPlanUseCase
import com.example.shoppingassistant.domain.visualsearch.NormalizeVisualSearchDraftUseCase
import com.example.shoppingassistant.domain.visualsearch.ReuseVisualSearchContextUseCase
import com.example.shoppingassistant.domain.visualsearch.TrackVisualSearchEventsUseCase
import com.example.shoppingassistant.domain.vision.NormalizeImageUseCase
import com.example.shoppingassistant.domain.vision.NormalizePhotosUseCase
import com.example.shoppingassistant.domain.vision.GetVisionUsageUseCase
import com.example.shoppingassistant.domain.vision.ConsumeVisionUsageUseCase
import org.koin.dsl.module

val domainModule = module {
    single { GetTop3FromCandidatesUseCase(get()) }
    single { GetOfferDetailsUseCase(get(), get()) }
    single { SearchOffersUseCase(get(), get(), get()) }
    single { SearchOffersWithFacetsUseCase(get(), get(), get()) }
    single { TrackPresetObservabilityEventsUseCase(get(), get()) }
    single { CreateOfferPriceAlertUseCase(get(), get()) }

    single { RegisterUserUseCase(get()) }
    single { LoginUserUseCase(get()) }
    single { GetCurrentUserUseCase(get()) }
    single { LogoutUseCase(get()) }
    single { ChangePasswordUseCase(get()) }

    single { GetFacetCountsTask(get()) }
    single { GetFacetDefinitionsTask(get()) }
    single { GetFacetPresetsTask(get()) }
    single { GetFacetPresetTask(get()) }
    single { GetFacetCollectionsTask(get()) }
    single { GetFacetCollectionTask(get()) }
    single { GetFacetCollectionByBrowseCodeTask(get()) }
    single { GetCategoryOfferCountsTask(get()) }
    single { GetBrowseNodesTask(get()) }
    single { GetBrowseNodeTask(get()) }
    single { GetAliasEntriesTask(get()) }
    single { GetGoogleTaxonomyMappingsTask(get()) }
    single { GetGoogleMappingForCategoryTask(get()) }
    single { RouteQueryTask(get()) }
    single { RunStage21TechGoldenTask(get()) }

    single { UpdateProfileTask(get()) }
    single { UpdateProfilePhotosTask(get()) }
    single { RequestEmailChangeTask(get()) }
    single { ConfirmEmailChangeTask(get()) }
    single { DeleteAccountTask(get()) }
    single { GetProfileSettingsTask(get()) }
    single { GetProfileViewTask(get()) }
    single { SaveProfileSettingsTask(get()) }
    single { GetExternalLinksTask(get()) }
    single { SaveExternalLinksTask(get()) }
    single { ClearExternalLinksTask(get()) }
    single { GetProfileCacheTask(get()) }
    single { SaveProfileCacheTask(get()) }
    single { ClearProfileCacheTask(get()) }
    single { UpdatePublicProfileTask(get()) }
    single { UpdateProfilePrivacyTask(get()) }
    single { UpdateSellerDeliveryZonesTask(get()) }

    single { UploadPhotoUseCase(get()) }
    single { NormalizeImageUseCase(get()) }
    single { NormalizePhotosUseCase(get()) }
    single { GetVisionUsageUseCase(get()) }
    single { ConsumeVisionUsageUseCase(get()) }
    single { ReuseVisualSearchContextUseCase(get()) }
    single { NormalizeVisualSearchDraftUseCase(get()) }
    single { BindVisualSearchQueryUseCase(get()) }
    single { GetVisualSearchRecoveryPlanUseCase(get()) }
    single { TrackVisualSearchEventsUseCase(get()) }
    single { MirrorByUrlUseCase(get()) }
    single { CreateOrResumeLocalOfferSessionTask(get()) }
    single { ConfirmLocalOfferGeoSnapshotTask(get()) }
    single { ListLocalOfferDraftsTask(get()) }
    single { CreateLocalOfferDraftTask(get()) }
    single { GetLocalOfferDraftTask(get()) }
    single { UpdateLocalOfferDraftReviewTask(get()) }
    single { GetLocalOfferPreviewTask(get()) }
    single { GetLocalOfferPublishPreflightTask(get()) }
    single { PublishLocalOfferDraftTask(get()) }
    single { ListShortListingDraftsTask(get()) }
    single { CreateShortListingDraftTask(get()) }
    single { GetShortListingDraftTask(get()) }
    single { UpdateShortListingDraftReviewTask(get()) }
    single { GetShortListingPublishPreflightTask(get()) }
    single { PublishShortListingDraftTask(get()) }

    single { CreateDraftOfferTask(get()) }
    single { SaveDraftOfferTask(get()) }
    single { GetDraftOfferTask(get()) }
    single { ObserveDraftOfferTask(get()) }
    single { ListDraftOffersTask(get()) }
    single { DeleteDraftOfferTask(get()) }

    single { LoadRawOfferTask(get()) }
    single { CreateTrackedOfferTask(get()) }
    single { PerformUserOfferActionTask(get()) }
    single { UpdateUserOfferPriceTask(get()) }
    single { UpdateUserOfferPricesTask(get()) }

    // --- Link template tasks ---
    single { LinkTemplateBuilderImpl(get(), get(), get(), get(), get()) }
    single<LinkTemplateBuilderTask> { get<LinkTemplateBuilderImpl>() }

    single { LinkTemplateMapperImpl() }
    single<LinkTemplateMapperTask> { get<LinkTemplateMapperImpl>() }

    // --- Subscriptions use-cases ---
    single { ListSubscriptionsTask(get()) }
    single { ListNotificationsTask(get()) }
    single { ListNotificationsPageTask(get()) }
    single { AddSubscriptionTask(get()) }
    single { UpdateSubscriptionTask(get()) }
    single { DeleteSubscriptionTask(get()) }
    single { MarkNotificationReadTask(get()) }
    single { MarkAllNotificationsReadTask(get()) }

    // --- Tracked items use-cases ---
    single { GetTrackedItemsListTask(get()) }
    single { CreateTrackTask(get()) }
    single { ObserveTrackEventsCountTask(get()) }
    single { GetTop10Task(get()) }
    single { RefreshTop10Task(get()) }
    single { GetTrackOffersPageTask(get()) }
    single { PauseTrackTask(get()) }
    single { ResumeTrackTask(get()) }
    single { DeleteTrackTask(get()) }
    single { UpdateTrackFiltersTask(get()) }
    single { UpdateTrackTargetTask(get()) }
    single { GetTrackFilterOptionsTask(get()) }
    single { OpenSourcesInfoTask(get()) }
    single { GetTrackEventsPageTask(get()) }
    single { MarkTrackEventReadTask(get()) }
    single { MarkAllTrackEventsReadTask(get()) }
}
