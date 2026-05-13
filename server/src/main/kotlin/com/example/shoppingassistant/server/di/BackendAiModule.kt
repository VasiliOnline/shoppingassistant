package com.example.shoppingassistant.server.di

import com.example.shoppingassistant.server.ai.AiAgentRegistry
import com.example.shoppingassistant.server.ai.AiAgentRegistryAdminService
import com.example.shoppingassistant.server.ai.AiAgentRegistryOverrideRepository
import com.example.shoppingassistant.server.ai.AiNormalizationMetricsStore
import com.example.shoppingassistant.server.ai.AiNormalizationObservabilityService
import com.example.shoppingassistant.server.ai.AiStructuredClient
import com.example.shoppingassistant.server.ai.AiTargetHealthPolicy
import com.example.shoppingassistant.server.ai.CompositeAiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.DatabaseAiAgentRegistryOverrideRepository
import com.example.shoppingassistant.server.ai.InMemoryAiNormalizationMetricsStore
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.AiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.InMemoryAiTargetHealthPolicy
import com.example.shoppingassistant.server.ai.LoggingAiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.PersistentAiAgentRegistry
import com.example.shoppingassistant.server.ai.PersistentAiAgentRegistryOverrideStore
import com.example.shoppingassistant.server.config.VisualSearchConfig
import com.example.shoppingassistant.server.ai.yandex.DefaultYandexAiStudioClient
import org.koin.dsl.module

val backendAiModule = module {
    single<AiStructuredClient> {
        val config = get<VisualSearchConfig>()
        DefaultYandexAiStudioClient(
            retry429MaxAttempts = config.aiRetry429MaxAttempts,
            retry5xxMaxAttempts = config.aiRetry5xxMaxAttempts,
            retryBaseDelayMs = config.aiRetryBaseDelayMs,
            retryMaxDelayMs = config.aiRetryMaxDelayMs,
        )
    }
    single<AiAgentRegistryOverrideRepository> { DatabaseAiAgentRegistryOverrideRepository() }
    single { PersistentAiAgentRegistryOverrideStore(get()) }
    single<AiAgentRegistry> { PersistentAiAgentRegistry(get(), get(), get()) }
    single<AiNormalizationMetricsStore> { InMemoryAiNormalizationMetricsStore() }
    single<AiNormalizationTelemetry> {
        CompositeAiNormalizationTelemetry(
            listOf(
                LoggingAiNormalizationTelemetry(),
                get<AiNormalizationMetricsStore>() as AiNormalizationTelemetry,
            ),
        )
    }
    single<AiTargetHealthPolicy> { InMemoryAiTargetHealthPolicy() }
    single { AiNormalizationOrchestrator(get(), get(), get(), get()) }
    single { AiNormalizationObservabilityService(get(), get(), get()) }
    single { AiAgentRegistryAdminService(get()) }
}
