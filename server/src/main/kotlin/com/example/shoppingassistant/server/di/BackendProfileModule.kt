package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.server.profile.ProfileContractService
import org.koin.dsl.module

val backendProfileModule = module {
    single { ProfileContractService() }
}
