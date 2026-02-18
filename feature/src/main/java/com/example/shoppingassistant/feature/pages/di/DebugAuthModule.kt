package com.example.shoppingassistant.feature.pages.di

import com.example.shoppingassistant.feature.auth.DebugAuthStore
import com.example.shoppingassistant.feature.auth.DebugAuthStoreImpl
import org.koin.dsl.module

val debugAuthModule = module {
    single<DebugAuthStore> { DebugAuthStoreImpl() }
}
