package com.example.shoppingassistant.core.push

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.math.max
import org.koin.core.context.GlobalContext

class TrackingPushWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
            ?: return Result.success()

        val notifier = TrackingPushNotifier(applicationContext)
        if (!notifier.canPostNotifications()) return Result.success()

        val remote = koin.get<TrackingInboxRemoteDataSource>()
        val state = koin.get<TrackingPushStateStorage>()
        val settingsStore = koin.get<TrackingNotificationsSettingsStorage>()
        val settings = settingsStore.get()
        if (!settings.pushEnabled) return Result.success()
        if (isQuietHours(settings)) return Result.success()

        val page = runCatching { remote.listNotificationsPage(limit = 20, offset = 0) }
            .getOrElse { t ->
                Log.w("TrackingPush", "Tracking push poll failed: ${t.message}", t)
                return Result.retry()
            }

        val lastNotified = state.getLastNotifiedId()
        val newItems = page.items
            .asSequence()
            .filter { it.id > lastNotified }
            .sortedBy { it.id }
            .take(5)
            .toList()

        newItems.forEach { notifier.notifyInboxItem(it, settings) }
        if (settings.groupNotifications && newItems.isNotEmpty()) {
            notifier.notifySummary(total = newItems.size, settings = settings)
        }

        val maxId = max(lastNotified, newItems.maxOfOrNull { it.id } ?: 0L)
        if (maxId > lastNotified) {
            state.setLastNotifiedId(maxId)
        }

        return Result.success()
    }

    private fun isQuietHours(settings: TrackingNotificationsSettings): Boolean {
        if (!settings.quietHoursEnabled) return false
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val start = settings.quietHoursStart.coerceIn(0, 23)
        val end = settings.quietHoursEnd.coerceIn(0, 23)
        return if (start == end) {
            true
        } else if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }
}

@Deprecated("Use TrackingPushWorker")
typealias SubscriptionsPushWorker = TrackingPushWorker
