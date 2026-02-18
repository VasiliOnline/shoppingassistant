package com.example.shoppingassistant.server.tracks

import com.example.shoppingassistant.domain.tracks.TrackEvent
import com.example.shoppingassistant.domain.tracks.TrackEventType
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.insertIgnore

class TrackEventsRepositoryImpl : TrackEventsRepository {
    override suspend fun listEventsPage(
        userId: Long,
        trackId: Long,
        limit: Int,
        offset: Int,
    ): TrackEventsPage = DatabaseFactory.dbQuery {
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)

        val rows = TrackEventsTable
            .selectAll()
            .where { (TrackEventsTable.userId eq userId) and (TrackEventsTable.trackId eq trackId) }
            .orderBy(TrackEventsTable.createdAt, SortOrder.DESC)
            .limit(safeLimit + 1)
            .offset(safeOffset.toLong())
            .toList()

        val canLoadMore = rows.size > safeLimit
        val items = rows.take(safeLimit).map { row ->
            TrackEvent(
                id = row[TrackEventsTable.id].toString(),
                trackId = row[TrackEventsTable.trackId].toString(),
                type = runCatching { TrackEventType.valueOf(row[TrackEventsTable.type]) }
                    .getOrDefault(TrackEventType.OTHER),
                createdAt = row[TrackEventsTable.createdAt],
                title = row[TrackEventsTable.title],
                subtitle = row[TrackEventsTable.subtitle],
                dedupKey = row[TrackEventsTable.dedupKey],
                isRead = row[TrackEventsTable.isRead],
            )
        }

        TrackEventsPage(
            items = items,
            limit = safeLimit,
            offset = safeOffset,
            canLoadMore = canLoadMore,
        )
    }

    override suspend fun markRead(userId: Long, eventId: Long): Boolean =
        DatabaseFactory.dbQuery {
            val updated = TrackEventsTable.update(
                where = {
                    (TrackEventsTable.userId eq userId) and
                        (TrackEventsTable.id eq eventId) and
                        (TrackEventsTable.isRead eq false)
                },
            ) { stmt ->
                stmt[isRead] = true
            }
            updated > 0
        }

    override suspend fun markAllRead(userId: Long, trackId: Long): Int =
        DatabaseFactory.dbQuery {
            TrackEventsTable.update(
                where = {
                    (TrackEventsTable.userId eq userId) and
                        (TrackEventsTable.trackId eq trackId) and
                        (TrackEventsTable.isRead eq false)
                },
            ) { stmt ->
                stmt[isRead] = true
            }
        }

    override suspend fun addEvent(
        userId: Long,
        trackId: Long,
        type: String,
        title: String,
        subtitle: String?,
        dedupKey: String,
    ) {
        DatabaseFactory.dbQuery {
            TrackEventsTable.insertIgnore { stmt ->
                stmt[TrackEventsTable.userId] = userId
                stmt[TrackEventsTable.trackId] = trackId
                stmt[TrackEventsTable.type] = type
                stmt[TrackEventsTable.title] = title
                stmt[TrackEventsTable.subtitle] = subtitle
                stmt[TrackEventsTable.dedupKey] = dedupKey
                stmt[TrackEventsTable.isRead] = false
            }
        }
    }
}
