package com.example.shoppingassistant.feature.pages.di

import com.example.shoppingassistant.feature.pages.profile.AccountProfileViewModel
import com.example.shoppingassistant.feature.pages.profile.CatalogGovernanceAdminViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    viewModel {
        AccountProfileViewModel(
            getCurrentUser = get(),
            getProfileView = get(),
            updatePublicProfile = get(),
            updateProfilePrivacy = get(),
            updateSellerDeliveryZonesTask = get(),
            requestEmailChangeTask = get(),
            confirmEmailChangeTask = get(),
            deleteAccountTask = get(),
            changePasswordUseCase = get(),
            uploadPhotoUseCase = get(),
            loginUserUseCase = get(),
            registerUserUseCase = get(),
            logoutUseCase = get(),
            authRepository = get(),
            getProfileSettings = get(),
            profileSettingsStore = get(),
            profileViewCacheRepository = get(),
            listDraftOffers = get(),
            getTrackedItems = get(),
            listNotificationsPage = get(),
            markNotificationRead = get(),
            markAllNotificationsRead = get(),
            notificationsSettingsStorage = get(),
        )
    }
    viewModel {
        CatalogGovernanceAdminViewModel(
            repository = get(),
        )
    }
}

