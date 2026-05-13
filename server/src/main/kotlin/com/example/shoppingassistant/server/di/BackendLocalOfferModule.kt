package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.server.localoffer.LocalOfferBackendService
import com.example.shoppingassistant.server.localoffer.LocalOfferBackendServiceImpl
import com.example.shoppingassistant.server.localoffer.LocalOfferDraftRuntime
import org.koin.dsl.module

val backendLocalOfferModule = module {
    single<LocalOfferBackendService> {
        LocalOfferBackendServiceImpl(
            draftRuntime = get<LocalOfferDraftRuntime>(),
        )
    }
}
