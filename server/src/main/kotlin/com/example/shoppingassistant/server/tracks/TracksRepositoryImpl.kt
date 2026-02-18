package com.example.shoppingassistant.server.tracks

import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.tracks.Badge
import com.example.shoppingassistant.domain.tracks.RankedOffer
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackEventType
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackOffersPage
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackStats
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.tracks.TrackMatchKeyFactory
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.UserProfilesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.math.max

class TracksRepositoryImpl(
    private val eventsRepository: TrackEventsRepository,
    private val offerRepository: OfferRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TracksRepository {

    private companion object {
        const val MAX_TRACKS_PER_USER: Int = 100
        const val MAX_TRACK_OFFERS_FETCH: Int = 500
        const val NEW_BADGE_WINDOW_MS: Long = 3L * 24 * 60 * 60 * 1000
    }

    private data class ValidatedTarget(
        val type: TrackType,
        val matchKey: String?,
        val categoryCode: String?,
        val dedupKey: String,
    )

    override suspend fun list(userId: Long): List<Track> = DatabaseFactory.dbQuery {
        val tracks = TracksTable
            .selectAll()
            .where { TracksTable.userId eq userId }
            .orderBy(TracksTable.updatedAt, SortOrder.DESC)
            .toList()

        val events = TrackEventsTable
            .selectAll()
            .where { TrackEventsTable.userId eq userId }
            .toList()

        val eventsByTrack = events.groupBy { it[TrackEventsTable.trackId] }
        tracks.map { row -> mapTrack(row, eventsByTrack[row[TracksTable.id]]) }
    }

    override suspend fun get(userId: Long, trackId: Long): Track? = DatabaseFactory.dbQuery {
        val row = TracksTable
            .selectAll()
            .where { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) }
            .limit(1)
            .singleOrNull()
            ?: return@dbQuery null

        val events = TrackEventsTable
            .selectAll()
            .where { (TrackEventsTable.userId eq userId) and (TrackEventsTable.trackId eq trackId) }
            .toList()

        mapTrack(row, events)
    }

    override suspend fun create(userId: Long, request: TrackCreateRequest): TrackCreateResult {
        val validated = validateTarget(
            type = request.type,
            matchKeyRaw = request.target.matchKey,
            categoryCodeRaw = request.categoryCode,
        )
        if (validated == null) {
            return TrackCreateResult.InvalidInput("Only PRODUCT/CATEGORY targets are supported")
        }

        return DatabaseFactory.dbQuery {
            val count = TracksTable.selectAll().where { TracksTable.userId eq userId }.count()
            if (count >= MAX_TRACKS_PER_USER) {
                return@dbQuery TrackCreateResult.LimitReached(MAX_TRACKS_PER_USER)
            }

            val existing = TracksTable
                .selectAll()
                .where {
                    (TracksTable.userId eq userId) and (TracksTable.dedupKey eq validated.dedupKey)
                }
                .limit(1)
                .singleOrNull()

            if (existing != null) {
                return@dbQuery TrackCreateResult.AlreadyExists(mapTrack(existing, emptyList()))
            }

            val now = clock()
            val filters = sanitizeFilters(request.filters)
            val title = buildTitle(validated, request.title)
            val id = TracksTable.insert { stmt ->
                stmt[TracksTable.userId] = userId
                stmt[TracksTable.type] = validated.type.name
                stmt[TracksTable.matchKey] = validated.matchKey
                stmt[TracksTable.categoryCode] = validated.categoryCode
                stmt[TracksTable.target] = TrackTarget(matchKey = validated.matchKey)
                stmt[TracksTable.filters] = filters
                stmt[TracksTable.title] = title
                stmt[TracksTable.state] = TrackState.ACTIVE.name
                stmt[TracksTable.dedupKey] = validated.dedupKey
                stmt[TracksTable.createdAt] = now
                stmt[TracksTable.updatedAt] = now
            }[TracksTable.id]

            eventsRepository.addEvent(
                userId = userId,
                trackId = id,
                type = TrackEventType.OTHER.name,
                title = "Трек создан",
                subtitle = title,
                dedupKey = "created:$id:$now",
            )

            val row = TracksTable
                .selectAll()
                .where { (TracksTable.userId eq userId) and (TracksTable.id eq id) }
                .limit(1)
                .single()

            TrackCreateResult.Created(mapTrack(row, emptyList()))
        }
    }

    override suspend fun update(userId: Long, trackId: Long, request: TrackUpdateRequest): Track? {
        return DatabaseFactory.dbQuery {
            val row = TracksTable
                .selectAll()
                .where { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) }
                .limit(1)
                .singleOrNull()
                ?: return@dbQuery null

            val now = clock()
            val updatedTitle = request.title?.trim()?.takeIf { it.isNotBlank() }
            val updatedFilters = request.filters?.let(::sanitizeFilters)
            val updatedState = request.isActive?.let { if (it) TrackState.ACTIVE.name else TrackState.PAUSED.name }

            if (updatedTitle != null || updatedFilters != null || updatedState != null) {
                TracksTable.update(
                    where = { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) },
                ) { stmt ->
                    updatedTitle?.let { stmt[TracksTable.title] = it }
                    updatedFilters?.let { stmt[TracksTable.filters] = it }
                    updatedState?.let { stmt[TracksTable.state] = it }
                    stmt[TracksTable.updatedAt] = now
                }

                eventsRepository.addEvent(
                    userId = userId,
                    trackId = trackId,
                    type = TrackEventType.OTHER.name,
                    title = "Трек обновлён",
                    subtitle = updatedTitle,
                    dedupKey = "updated:$trackId:$now",
                )
            }

            get(userId, trackId)
        }
    }

    override suspend fun updateTarget(
        userId: Long,
        trackId: Long,
        request: TrackTargetUpdateRequest,
    ): TrackTargetUpdateResult = DatabaseFactory.dbQuery {
        val row = TracksTable
            .selectAll()
            .where { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) }
            .limit(1)
            .singleOrNull()
            ?: return@dbQuery TrackTargetUpdateResult.NotFound

        val validated = validateTarget(
            type = request.type,
            matchKeyRaw = request.matchKey,
            categoryCodeRaw = request.categoryCode,
        ) ?: return@dbQuery TrackTargetUpdateResult.InvalidInput("Invalid target")

        val conflict = TracksTable
            .selectAll()
            .where {
                (TracksTable.userId eq userId) and
                    (TracksTable.id neq trackId) and
                    (TracksTable.dedupKey eq validated.dedupKey)
            }
            .limit(1)
            .singleOrNull()
        if (conflict != null) {
            return@dbQuery TrackTargetUpdateResult.AlreadyExists(mapTrack(conflict, emptyList()))
        }

        val now = clock()
        val mergedFilters = sanitizeFilters(
            row[TracksTable.filters].copy(extra = sanitizeAttributes(request.attributes)),
        )
        val title = request.title?.trim()?.takeIf { it.isNotBlank() } ?: row[TracksTable.title]

        TracksTable.update(
            where = { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) },
        ) { stmt ->
            stmt[TracksTable.type] = validated.type.name
            stmt[TracksTable.matchKey] = validated.matchKey
            stmt[TracksTable.categoryCode] = validated.categoryCode
            stmt[TracksTable.target] = TrackTarget(matchKey = validated.matchKey)
            stmt[TracksTable.filters] = mergedFilters
            stmt[TracksTable.title] = title
            stmt[TracksTable.dedupKey] = validated.dedupKey
            stmt[TracksTable.updatedAt] = now
        }

        eventsRepository.addEvent(
            userId = userId,
            trackId = trackId,
            type = TrackEventType.OTHER.name,
            title = "Цель трека обновлена",
            subtitle = title,
            dedupKey = "target:$trackId:$now",
        )

        val updated = TracksTable
            .selectAll()
            .where { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) }
            .limit(1)
            .single()
        TrackTargetUpdateResult.Updated(mapTrack(updated, emptyList()))
    }

    override suspend fun listOffers(
        userId: Long,
        trackId: Long,
        limit: Int,
        offset: Int,
        sort: TrackOfferSort,
    ): TrackOffersPage {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val track = get(userId, trackId) ?: return TrackOffersPage(
            items = emptyList(),
            limit = safeLimit,
            offset = safeOffset,
            canLoadMore = false,
            sort = sort,
        )

        val categoryCode = track.categoryCode?.trim()?.takeIf { it.isNotBlank() }
        val brandModel = if (track.type == TrackType.PRODUCT) {
            TrackMatchKeyFactory.parse(track.target.matchKey)
        } else {
            null
        }

        val fetchLimit = (safeOffset + safeLimit + 1).coerceAtMost(MAX_TRACK_OFFERS_FETCH)
        val userCountry = resolveUserCountry(userId)
        val criteria = OfferSearchCriteria(
            brand = brandModel?.first,
            model = brandModel?.second,
            categoryCode = categoryCode,
            attributes = sanitizeAttributes(track.filters.extra),
            userCountry = userCountry,
            sellerCountryCode = userCountry,
            limit = fetchLimit,
            sort = sort.toOfferSort(),
        )

        val offers = offerRepository.searchOffers(criteria)
        val ranked = rankAndBadgeOffers(
            offers = offers,
            sort = sort,
            isCategoryTrack = track.type == TrackType.CATEGORY,
        )
        val deduped = dedupRanked(ranked)
        val page = deduped.drop(safeOffset).take(safeLimit)
        return TrackOffersPage(
            items = page,
            limit = safeLimit,
            offset = safeOffset,
            canLoadMore = deduped.size > safeOffset + safeLimit,
            sort = sort,
        )
    }

    override suspend fun setActive(userId: Long, trackId: Long, isActive: Boolean): Track? {
        return DatabaseFactory.dbQuery {
            val row = TracksTable
                .selectAll()
                .where { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) }
                .limit(1)
                .singleOrNull()
                ?: return@dbQuery null

            val nextState = if (isActive) TrackState.ACTIVE.name else TrackState.PAUSED.name
            if (row[TracksTable.state] != nextState) {
                val now = clock()
                TracksTable.update(
                    where = { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) },
                ) { stmt ->
                    stmt[TracksTable.state] = nextState
                    stmt[TracksTable.updatedAt] = now
                }

                eventsRepository.addEvent(
                    userId = userId,
                    trackId = trackId,
                    type = TrackEventType.OTHER.name,
                    title = if (isActive) "Трек возобновлён" else "Трек поставлен на паузу",
                    subtitle = row[TracksTable.title],
                    dedupKey = "state:$trackId:$now",
                )
            }
            get(userId, trackId)
        }
    }

    override suspend fun delete(userId: Long, trackId: Long): Boolean =
        DatabaseFactory.dbQuery {
            val deleted = TracksTable
                .deleteWhere { (TracksTable.userId eq userId) and (TracksTable.id eq trackId) }
            deleted > 0
        }

    private suspend fun resolveUserCountry(userId: Long): String? = DatabaseFactory.dbQuery {
        UserProfilesTable
            .selectAll()
            .where { UserProfilesTable.userId eq userId }
            .limit(1)
            .singleOrNull()
            ?.get(UserProfilesTable.countryCode)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun dedupRanked(items: List<RankedOffer>): List<RankedOffer> {
        if (items.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<RankedOffer>(items.size)
        items.forEach { offer ->
            if (seen.add(offer.dedupKey)) {
                result += offer
            }
        }
        return result
    }

    private fun rankAndBadgeOffers(
        offers: List<com.example.shoppingassistant.domain.model.OfferFull>,
        sort: TrackOfferSort,
        isCategoryTrack: Boolean,
    ): List<RankedOffer> {
        if (offers.isEmpty()) return emptyList()
        val now = clock()
        val prices = offers.map { it.price.minor }.sorted()
        val meanPrice = prices.average()
        val medianPrice = prices[prices.size / 2].toDouble()

        val mapped = offers.map { offer ->
            val rating = offer.seller.rating?.value
            val badges = buildList {
                val updatedAt = offer.updatedAt ?: 0L
                if (updatedAt > 0L && now - updatedAt <= NEW_BADGE_WINDOW_MS) {
                    add(Badge(label = "Новинка", priority = 3))
                }
                if (offer.price.minor <= meanPrice * 0.95) {
                    add(Badge(label = "Ниже средней цены", priority = 2))
                }
                if ((rating ?: 0.0) >= 4.5) {
                    add(Badge(label = "Рейтинг продавца", priority = 1))
                }
                if (isCategoryTrack && offer.price.minor <= medianPrice) {
                    add(Badge(label = "Выгодно в категории", priority = 0))
                }
            }
            offer to RankedOffer(
                offerId = offer.id,
                sellerId = offer.seller.id,
                price = offer.price,
                delivery = null,
                trustScore = rating?.toFloat(),
                distanceKm = offer.attributes["distance_km"]?.toFloatOrNull(),
                badges = badges,
                deeplink = "https://example.com/offers/${offer.id}",
                dedupKey = "offer:${offer.id}",
            )
        }

        return when (sort) {
            TrackOfferSort.PRICE_ASC -> mapped.sortedBy { it.first.price.minor }
            TrackOfferSort.NEWEST -> mapped.sortedByDescending { it.first.updatedAt ?: 0L }
            TrackOfferSort.SELLER_RATING_DESC ->
                mapped.sortedByDescending { it.first.seller.rating?.value ?: 0.0 }
        }.map { it.second }
    }

    private fun mapTrack(row: ResultRow, events: List<ResultRow>?): Track {
        val eventRows = events.orEmpty()
        val unreadCount = eventRows.count { !it[TrackEventsTable.isRead] }
        val lastEventAt = eventRows.maxOfOrNull { it[TrackEventsTable.createdAt] }

        val stats = TrackStats(
            offersCount = 0,
            bestPrice = null,
            newEventsCount = unreadCount,
            lastEventAt = lastEventAt,
            sourcesCount = 0,
        )

        val storedTarget = row[TracksTable.target]
        val matchKey = row[TracksTable.matchKey] ?: storedTarget.matchKey
        val type = runCatching { TrackType.valueOf(row[TracksTable.type]) }.getOrElse { TrackType.PRODUCT }
        val safeTarget = if (type == TrackType.URL || type == TrackType.SEARCH) {
            storedTarget
        } else {
            TrackTarget(matchKey = matchKey)
        }

        return Track(
            id = row[TracksTable.id].toString(),
            title = row[TracksTable.title],
            categoryCode = row[TracksTable.categoryCode],
            type = type,
            target = safeTarget,
            filters = sanitizeFilters(row[TracksTable.filters]),
            alertRules = emptyList(),
            state = TrackState.valueOf(row[TracksTable.state]),
            createdAt = row[TracksTable.createdAt],
            updatedAt = row[TracksTable.updatedAt],
            lastCheckedAt = row[TracksTable.lastCheckedAt],
            freshnessSec = null,
            stats = stats,
        )
    }

    private fun buildTitle(target: ValidatedTarget, rawTitle: String?): String {
        val explicit = rawTitle?.trim()?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return when (target.type) {
            TrackType.CATEGORY -> target.categoryCode ?: "Категория"
            TrackType.PRODUCT -> {
                val parsed = TrackMatchKeyFactory.parse(target.matchKey)
                if (parsed == null) target.matchKey.orEmpty().take(80).ifBlank { "Товар" }
                else "${parsed.first} ${parsed.second}".take(80)
            }
            else -> "Отслеживание"
        }
    }

    private fun validateTarget(
        type: TrackType,
        matchKeyRaw: String?,
        categoryCodeRaw: String?,
    ): ValidatedTarget? {
        return when (type) {
            TrackType.PRODUCT -> {
                val normalizedMatchKey = normalizeMatchKey(matchKeyRaw) ?: return null
                val normalizedCategoryCode = normalizeCategoryCode(categoryCodeRaw)
                ValidatedTarget(
                    type = TrackType.PRODUCT,
                    matchKey = normalizedMatchKey,
                    categoryCode = normalizedCategoryCode,
                    dedupKey = normalizedMatchKey.take(512),
                )
            }
            TrackType.CATEGORY -> {
                val normalizedCategoryCode = normalizeCategoryCode(categoryCodeRaw) ?: return null
                ValidatedTarget(
                    type = TrackType.CATEGORY,
                    matchKey = null,
                    categoryCode = normalizedCategoryCode,
                    dedupKey = normalizedCategoryCode.take(512),
                )
            }
            else -> null
        }
    }

    private fun normalizeMatchKey(raw: String?): String? {
        val parsed = TrackMatchKeyFactory.parse(raw) ?: return null
        return TrackMatchKeyFactory.fromBrandModel(parsed.first, parsed.second)
    }

    private fun normalizeCategoryCode(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }?.uppercase()

    private fun sanitizeFilters(filters: TrackFilters): TrackFilters =
        filters.copy(extra = sanitizeAttributes(filters.extra))

    private fun sanitizeAttributes(values: Map<String, String>): Map<String, String> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .sortedBy { it.first.lowercase() }
            .toMap(LinkedHashMap())
    }

    private fun TrackOfferSort.toOfferSort(): OfferSort = when (this) {
        TrackOfferSort.PRICE_ASC -> OfferSort.PRICE_ASC
        TrackOfferSort.NEWEST -> OfferSort.NEWEST
        TrackOfferSort.SELLER_RATING_DESC -> OfferSort.RATING_DESC
    }
}
