package com.example.shoppingassistant.feature.pages.useroffers.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferResult
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferStatus
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferTask
import com.example.shoppingassistant.domain.offers.TrackedOfferInput
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.get as koinGet

internal class UserOffersSyncTaskImpl(
    private val queueStore: UserOffersSyncQueueStore,
    private val createdStore: UserOffersCreatedStore,
    private val createTask: CreateTrackedOfferTask,
) : UserOffersSyncTask {

    private val mutex = Mutex()

    override suspend fun enqueueAndSync(
        localId: String,
        payload: TrackedOfferInput,
    ): CreateTrackedOfferResult {
        val entry = UserOfferSyncEntry(
            localId = localId,
            payload = payload,
            status = UserOfferSyncStatus.CREATED,
            updatedAtMillis = System.currentTimeMillis(),
        )
        queueStore.enqueue(entry)
        return mutex.withLock { syncEntry(entry) }
    }

    override suspend fun syncPending() {
        mutex.withLock {
            val items = queueStore.entries.value
            val pending = items.filter {
                it.status == UserOfferSyncStatus.CREATED || it.status == UserOfferSyncStatus.FAILED
            }
            pending.forEach { syncEntry(it) }
        }
    }

    private suspend fun syncEntry(entry: UserOfferSyncEntry): CreateTrackedOfferResult {
        val syncing = entry.copy(
            status = UserOfferSyncStatus.SYNCING,
            attempts = entry.attempts + 1,
            updatedAtMillis = System.currentTimeMillis(),
            errorMessage = null,
        )
        queueStore.update(syncing)

        val result = runCatching { createTask(syncing.payload) }.getOrElse { throwable ->
            queueStore.update(
                syncing.copy(
                    status = UserOfferSyncStatus.FAILED,
                    errorMessage = throwable.message ?: "Sync failed",
                    updatedAtMillis = System.currentTimeMillis(),
                )
            )
            return CreateTrackedOfferResult(
                status = CreateTrackedOfferStatus.INVALID_INPUT,
                message = throwable.message ?: "Sync failed",
            )
        }

        val serverId = result.offerId ?: result.existingOfferId
        when (result.status) {
            CreateTrackedOfferStatus.CREATED,
            CreateTrackedOfferStatus.ALREADY_EXISTS,
            -> {
                if (!serverId.isNullOrBlank() && serverId != syncing.localId) {
                    createdStore.replaceId(syncing.localId, serverId)
                }
                queueStore.update(
                    syncing.copy(
                        status = UserOfferSyncStatus.SYNCED,
                        serverId = serverId,
                        errorMessage = null,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                )
                return result
            }
            CreateTrackedOfferStatus.INVALID_INPUT -> {
                queueStore.update(
                    syncing.copy(
                        status = UserOfferSyncStatus.FAILED,
                        serverId = serverId,
                        errorMessage = result.message ?: "Invalid input",
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                )
                return result
            }
        }
    }
}

@Composable
fun rememberUserOffersSyncTask(): UserOffersSyncTask = remember {
    UserOffersSyncTaskImpl(
        queueStore = koinGet(UserOffersSyncQueueStore::class.java),
        createdStore = koinGet(UserOffersCreatedStore::class.java),
        createTask = koinGet(CreateTrackedOfferTask::class.java),
    )
}
