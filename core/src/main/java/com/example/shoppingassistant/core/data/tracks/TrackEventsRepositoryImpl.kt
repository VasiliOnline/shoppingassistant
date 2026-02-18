package com.example.shoppingassistant.core.data.tracks

import com.example.shoppingassistant.domain.tracks.TrackEventsPage
import com.example.shoppingassistant.domain.tracks.TrackEventsRepository
import com.example.shoppingassistant.domain.tracks.TrackId

class TrackEventsRepositoryImpl(
    private val remoteDataSource: TracksRemoteDataSource? = null,
) : TrackEventsRepository {

    override suspend fun listEvents(trackId: TrackId, limit: Int, offset: Int): TrackEventsPage {
        if (remoteDataSource == null) {
            return TrackEventsPage(items = emptyList(), limit = limit, offset = offset, canLoadMore = false)
        }
        return remoteDataSource.listEvents(trackId, limit, offset)
    }

    override suspend fun markEventRead(eventId: String): Boolean {
        if (remoteDataSource == null) return false
        return remoteDataSource.markEventRead(eventId)
    }

    override suspend fun markAllEventsRead(trackId: TrackId): Int {
        if (remoteDataSource == null) return 0
        return remoteDataSource.markAllEventsRead(trackId)
    }
}
