package com.example.shoppingassistant.feature.pages.di

import com.example.shoppingassistant.feature.pages.trackeditems.TrackEditViewModel
import com.example.shoppingassistant.feature.pages.trackeditems.TrackEventsViewModel
import com.example.shoppingassistant.feature.pages.trackeditems.TrackTop10ViewModel
import com.example.shoppingassistant.feature.pages.trackeditems.TrackedItemsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val trackedItemsModule = module {
    viewModel {
        TrackedItemsViewModel(
            getTrackedItems = get(),
            pauseTrack = get(),
            resumeTrack = get(),
            deleteTrack = get(),
            refreshTop10 = get(),
        )
    }
    viewModel {
        TrackTop10ViewModel(
            trackRepository = get(),
            getTop10 = get(),
            refreshTop10 = get(),
            getTrackOffersPage = get(),
            markAllEventsRead = get(),
        )
    }
    viewModel {
        TrackEditViewModel(
            trackRepository = get(),
            updateTrackFilters = get(),
            getTrackFilterOptions = get(),
        )
    }
    viewModel {
        TrackEventsViewModel(
            trackRepository = get(),
            getEventsPage = get(),
            markEventRead = get(),
            markAllRead = get(),
        )
    }
}

