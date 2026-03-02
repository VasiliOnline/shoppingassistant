package com.example.shoppingassistant

import android.app.Application
import android.util.Log
import com.example.shoppingassistant.core.data.catalog.FacetSchemaGate
import com.example.shoppingassistant.core.data.catalog.TaxonomyGate
import com.example.shoppingassistant.core.data.tracks.TemplateSubscriptionsToTracksMigration
import com.example.shoppingassistant.core.di.coreModule
import com.example.shoppingassistant.core.di.domainModule
import com.example.shoppingassistant.core.di.ingestModule
import com.example.shoppingassistant.core.di.rankModule
import com.example.shoppingassistant.di.appModule
import com.example.shoppingassistant.core.push.TrackingPushScheduler
import com.example.shoppingassistant.feature.pages.di.debugAuthModule
import com.example.shoppingassistant.feature.pages.di.mainPageModule
import com.example.shoppingassistant.feature.pages.di.profileModule
import com.example.shoppingassistant.feature.pages.di.trackedItemsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.Koin

/**
 * Application класс, объявленный в манифесте.
 * Хранит глобальный контекст; остальная инициализация (Koin и т.п.)
 * остаётся в MainActivity, как настроено сейчас.
 */
class ShoppingAssistantApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@ShoppingAssistantApp)
                modules(
                    coreModule,
                    ingestModule,
                    domainModule,
                    rankModule,
                    appModule,
                    debugAuthModule,
                    mainPageModule,
                    profileModule,
                    trackedItemsModule,
                )
            }
        }

        val koin = runCatching { GlobalContext.get() }.getOrNull() ?: return
        appScope.launch {
            enforceTaxonomyGate(koin)
            enforceFacetSchemaGate(koin)
        }

        appScope.launch {
            runCatching { koin.get<TemplateSubscriptionsToTracksMigration>().migrateIfNeeded() }
        }
        runCatching { koin.get<TrackingPushScheduler>().ensureScheduled() }
    }

    private suspend fun enforceTaxonomyGate(koin: Koin) {
        val strictReleaseGate = true
        val failure = runCatching { koin.get<TaxonomyGate>().validateOrThrow() }.exceptionOrNull() ?: return

        if (failure.isRecoverableCatalogBootstrapFailure()) {
            Log.w(TAG, "Skipping taxonomy gate: catalog backend is unavailable", failure)
            return
        }

        if (BuildConfig.DEBUG || strictReleaseGate) {
            throw IllegalStateException("Taxonomy gate failed", failure)
        }
        Log.e(TAG, "Taxonomy gate failed in release: ${failure.message}", failure)
    }

    private suspend fun enforceFacetSchemaGate(koin: Koin) {
        val strictReleaseGate = true
        val failure = runCatching { koin.get<FacetSchemaGate>().validateOrThrow() }.exceptionOrNull() ?: return

        if (failure.isRecoverableCatalogBootstrapFailure()) {
            Log.w(TAG, "Skipping facet schema gate: catalog backend is unavailable", failure)
            return
        }

        if (BuildConfig.DEBUG || strictReleaseGate) {
            throw IllegalStateException("Facet schema gate failed", failure)
        }
        Log.e(TAG, "Facet schema gate failed in release: ${failure.message}", failure)
    }

    private fun Throwable.isRecoverableCatalogBootstrapFailure(): Boolean =
        causeChain().any { cause ->
            cause is IOException ||
                cause is TimeoutException ||
                cause.message?.startsWith("Catalog API call failed:") == true ||
                cause.message?.startsWith("Facet API call failed:") == true
        }

    private fun Throwable.causeChain(): Sequence<Throwable> = sequence {
        var current: Throwable? = this@causeChain
        while (current != null) {
            yield(current)
            current = current.cause
        }
    }

    private companion object {
        private const val TAG = "ShoppingAssistantApp"
    }
}
