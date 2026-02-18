package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.server.config.PriceFetcherConfig
import com.example.shoppingassistant.server.price.PriceFetcherRunner
import com.example.shoppingassistant.server.price.PriceFetcherRunnerImpl
import com.example.shoppingassistant.server.price.PriceProbe
import com.example.shoppingassistant.server.price.PriceProbeRegistry
import com.example.shoppingassistant.server.price.SourceRateLimiter
import com.example.shoppingassistant.server.price.avito.AvitoPriceProbe
import java.net.http.HttpClient
import org.koin.dsl.module

val backendPriceModule = module {
    single { PriceFetcherConfig.fromEnv() }

    single { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build() }

    single<PriceProbe> { AvitoPriceProbe(get()) }
    single { PriceProbeRegistry(listOf(get<PriceProbe>())) }

    single {
        val config = get<PriceFetcherConfig>()
        SourceRateLimiter(
            baseCooldownMs = config.blockCooldownMillis,
            maxCooldownMs = config.blockMaxCooldownMillis,
            errorCooldownMs = config.errorCooldownMillis,
        )
    }

    single<PriceFetcherRunner> {
        PriceFetcherRunnerImpl(
            config = get(),
            probeRegistry = get(),
            rateLimiter = get(),
            trackedOfferRepository = get(),
            sourceRegistry = get(),
            urlNormalizer = get(),
        )
    }
}
