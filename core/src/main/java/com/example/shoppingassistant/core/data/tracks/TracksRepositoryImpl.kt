package com.example.shoppingassistant.core.data.tracks

import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.ingest.DefaultUrlNormalizer
import com.example.shoppingassistant.domain.ingest.UrlNormalizer
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.subscriptions.AddSubscriptionRequest
import com.example.shoppingassistant.domain.subscriptions.AddSubscriptionResult
import com.example.shoppingassistant.domain.subscriptions.Subscription
import com.example.shoppingassistant.domain.subscriptions.SubscriptionCondition
import com.example.shoppingassistant.domain.subscriptions.SubscriptionConditionType
import com.example.shoppingassistant.domain.subscriptions.SubscriptionScope
import com.example.shoppingassistant.domain.subscriptions.SubscriptionsRepository
import com.example.shoppingassistant.domain.subscriptions.UpdateSubscriptionRequest
import com.example.shoppingassistant.domain.tracks.AlertRule
import com.example.shoppingassistant.domain.tracks.Badge
import com.example.shoppingassistant.domain.tracks.DeliveryInfo
import com.example.shoppingassistant.domain.tracks.RankExplanation
import com.example.shoppingassistant.domain.tracks.RankedOffer
import com.example.shoppingassistant.domain.tracks.SourceStamp
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackCreateInput
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackId
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackOffersPage
import com.example.shoppingassistant.domain.tracks.TrackOffersRepository
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackStats
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackAttributeRange
import com.example.shoppingassistant.domain.tracks.TrackTargetSpec
import com.example.shoppingassistant.domain.tracks.TrackTargetUpdateInput
import com.example.shoppingassistant.domain.tracks.TrackTargetUpdateResult
import com.example.shoppingassistant.domain.tracks.TrackTop10
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.tracks.TopOffersRepository
import com.example.shoppingassistant.core.data.tracks.top10.Top10CachePolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

class TracksRepositoryImpl(
    private val subscriptionsRepository: SubscriptionsRepository,
    private val filtersStore: TrackFiltersStore,
    private val authRepository: AuthRepository,
    private val remoteDataSource: TracksRemoteDataSource? = null,
    private val urlNormalizer: UrlNormalizer = DefaultUrlNormalizer(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TrackRepository, TopOffersRepository, TrackOffersRepository {

    private companion object {
        const val TOP10_TTL_SEC: Int = 300
        const val MAX_TOP10: Int = 10
    }

    private val mutex = Mutex()
    private val top10Cache = LinkedHashMap<TrackId, TrackTop10>()
    private val statsCache = LinkedHashMap<TrackId, TrackStats>()
    private val top10Policy = Top10CachePolicy()

    override suspend fun listTracks(): List<Track> {
        if (canUseRemote()) {
            val remote = runCatching { remoteDataSource!!.listTracks() }
                .getOrElse { return listTracksLocal() }
            return mergeRemoteTracks(remote)
        }
        return listTracksLocal()
    }

    override suspend fun getTrack(trackId: TrackId): Track {
        if (canUseRemote()) {
            val remote = runCatching { remoteDataSource!!.getTrack(trackId) }
                .getOrElse { return getTrackLocal(trackId) }
            return remote ?: error("Track not found")
        }
        return getTrackLocal(trackId)
    }

    override suspend fun upsertTrack(track: Track): Track {
        if (canUseRemote()) {
            val remote = runCatching { upsertRemote(track) }
                .getOrElse { return upsertTrackLocal(track) }
            storeFilters(remote.id, remote.filters)
            return remote
        }
        return upsertTrackLocal(track)
    }

    override suspend fun createTrack(input: TrackCreateInput): Track {
        val normalizedTargetSpec = normalizeTargetSpec(
            candidate = input.targetSpec,
        )
        if (canUseRemote()) {
            val request = TrackCreateRequest(
                type = input.type,
                target = TrackTarget(
                    spec = normalizedTargetSpec,
                    categoryCode = normalizedTargetSpec?.categoryCode,
                    attributes = normalizedTargetSpec?.attributes.orEmpty(),
                    attributesMulti = normalizedTargetSpec?.attributesMulti.orEmpty(),
                    attributesRange = normalizedTargetSpec?.attributesRange.orEmpty(),
                    matchKey = normalizedTargetSpec?.matchKey,
                    queryText = normalizedTargetSpec?.queryText,
                    schemaVersion = normalizedTargetSpec?.schemaVersion ?: 1,
                    taxonomyVersion = normalizedTargetSpec?.taxonomyVersion,
                    locale = normalizedTargetSpec?.locale,
                    unboundTokens = normalizedTargetSpec?.unboundTokens.orEmpty(),
                ),
                filters = input.filters,
                title = input.title,
            )
            val created = remoteDataSource!!.createTrack(request)
            storeFilters(created.id, created.filters)
            return created
        }

        val now = clock()
        val generatedId = "local-${now}_${input.type.name.lowercase()}"
        val localTrack = Track(
            id = generatedId,
            title = input.title ?: normalizedTargetSpec?.matchKey ?: normalizedTargetSpec?.categoryCode ?: "Отслеживание",
            categoryCode = normalizedTargetSpec?.categoryCode,
            type = input.type,
            target = TrackTarget(
                spec = normalizedTargetSpec,
                categoryCode = normalizedTargetSpec?.categoryCode,
                attributes = normalizedTargetSpec?.attributes.orEmpty(),
                attributesMulti = normalizedTargetSpec?.attributesMulti.orEmpty(),
                attributesRange = normalizedTargetSpec?.attributesRange.orEmpty(),
                matchKey = normalizedTargetSpec?.matchKey,
                queryText = normalizedTargetSpec?.queryText,
                schemaVersion = normalizedTargetSpec?.schemaVersion ?: 1,
                taxonomyVersion = normalizedTargetSpec?.taxonomyVersion,
                locale = normalizedTargetSpec?.locale,
                unboundTokens = normalizedTargetSpec?.unboundTokens.orEmpty(),
                query = normalizedTargetSpec?.categoryCode.takeIf { input.type == TrackType.CATEGORY },
            ),
            filters = input.filters,
            state = TrackState.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        return upsertTrackLocal(localTrack)
    }

    override suspend fun updateTrackTarget(
        trackId: TrackId,
        input: TrackTargetUpdateInput,
    ): TrackTargetUpdateResult {
        val normalizedTargetSpec = normalizeTargetSpec(
            candidate = input.targetSpec,
        )
        if (canUseRemote()) {
            return when (
                val remote = remoteDataSource!!.updateTrackTarget(
                    trackId = trackId,
                    request = TrackTargetUpdateRequest(
                        type = input.type,
                        target = TrackTarget(
                            spec = normalizedTargetSpec,
                            categoryCode = normalizedTargetSpec?.categoryCode,
                            attributes = normalizedTargetSpec?.attributes.orEmpty(),
                            attributesMulti = normalizedTargetSpec?.attributesMulti.orEmpty(),
                            attributesRange = normalizedTargetSpec?.attributesRange.orEmpty(),
                            matchKey = normalizedTargetSpec?.matchKey,
                            queryText = normalizedTargetSpec?.queryText,
                            schemaVersion = normalizedTargetSpec?.schemaVersion ?: 1,
                            taxonomyVersion = normalizedTargetSpec?.taxonomyVersion,
                            locale = normalizedTargetSpec?.locale,
                            unboundTokens = normalizedTargetSpec?.unboundTokens.orEmpty(),
                        ),
                        title = input.title,
                    ),
                )
            ) {
                is TrackTargetUpdateRemoteResult.Updated ->
                    TrackTargetUpdateResult.Updated(remote.track)
                is TrackTargetUpdateRemoteResult.AlreadyExists ->
                    TrackTargetUpdateResult.AlreadyExists(remote.track)
                is TrackTargetUpdateRemoteResult.InvalidInput ->
                    TrackTargetUpdateResult.InvalidInput(remote.reason)
                TrackTargetUpdateRemoteResult.NotFound ->
                    TrackTargetUpdateResult.NotFound
            }
        }

        val current = runCatching { getTrackLocal(trackId) }.getOrNull()
            ?: return TrackTargetUpdateResult.NotFound
        val nextTitle = input.title?.trim()?.takeIf { it.isNotBlank() } ?: current.title
        val nextTrack = current.copy(
            title = nextTitle,
            type = input.type,
            categoryCode = normalizedTargetSpec?.categoryCode,
            target = TrackTarget(
                spec = normalizedTargetSpec,
                categoryCode = normalizedTargetSpec?.categoryCode,
                attributes = normalizedTargetSpec?.attributes.orEmpty(),
                attributesMulti = normalizedTargetSpec?.attributesMulti.orEmpty(),
                attributesRange = normalizedTargetSpec?.attributesRange.orEmpty(),
                matchKey = normalizedTargetSpec?.matchKey,
                queryText = normalizedTargetSpec?.queryText,
                schemaVersion = normalizedTargetSpec?.schemaVersion ?: 1,
                taxonomyVersion = normalizedTargetSpec?.taxonomyVersion,
                locale = normalizedTargetSpec?.locale,
                unboundTokens = normalizedTargetSpec?.unboundTokens.orEmpty(),
                query = normalizedTargetSpec?.categoryCode
                    .takeIf { input.type == TrackType.CATEGORY },
            ),
            filters = current.filters.copy(
                extra = normalizedTargetSpec?.attributes.orEmpty(),
            ),
        )
        val saved = upsertTrackLocal(nextTrack)
        return TrackTargetUpdateResult.Updated(saved)
    }

    override suspend fun pause(trackId: TrackId) {
        if (canUseRemote()) {
            val updated = runCatching { remoteDataSource!!.pauseTrack(trackId) }.getOrNull()
            if (updated != null) return
        }
        pauseLocal(trackId)
    }

    override suspend fun resume(trackId: TrackId) {
        if (canUseRemote()) {
            val updated = runCatching { remoteDataSource!!.resumeTrack(trackId) }.getOrNull()
            if (updated != null) return
        }
        resumeLocal(trackId)
    }

    override suspend fun delete(trackId: TrackId) {
        if (canUseRemote()) {
            val deleted = runCatching { remoteDataSource!!.deleteTrack(trackId) }.getOrElse { false }
            if (deleted) return
        }
        deleteLocal(trackId)
    }

    private suspend fun canUseRemote(): Boolean {
        if (remoteDataSource == null) return false
        return !authRepository.currentToken().isNullOrBlank()
    }

    private suspend fun upsertRemote(track: Track): Track {
        val updateRequest = TrackUpdateRequest(
            title = track.title,
            filters = track.filters,
            isActive = track.state == TrackState.ACTIVE,
        )
        val updated = remoteDataSource!!.updateTrack(track.id, updateRequest)
        if (updated != null) return updated

        val createRequest = TrackCreateRequest(
            type = track.type,
            target = normalizeTrackTarget(
                candidate = track.target.spec,
                fallbackCategoryCode = track.target.categoryCode ?: track.categoryCode,
                fallbackMatchKey = track.target.matchKey,
                fallbackAttributes = track.target.attributes,
            ),
            filters = track.filters,
            title = track.title,
        )
        return remoteDataSource.createTrack(createRequest)
    }

    private suspend fun getTrackLocal(trackId: TrackId): Track {
        val subscription = requireSubscription(trackId)
        val notifications = runCatching { subscriptionsRepository.listNotifications() }.getOrElse { emptyList() }
        val unreadCount = notifications.count { !it.isRead && it.subscriptionId == subscription.id }
        val lastEventAt = notifications
            .filter { it.subscriptionId == subscription.id }
            .maxOfOrNull { it.createdAtMillis }

        val cachedStats = mutex.withLock { statsCache[trackId] } ?: TrackStats(offersCount = 0)
        val stats = cachedStats.copy(
            newEventsCount = unreadCount,
            lastEventAt = lastEventAt ?: cachedStats.lastEventAt,
        )

        val now = clock()
        val lastCheckedAt = mutex.withLock { top10Cache[trackId]?.computedAt } ?: subscription.updatedAtMillis
        val freshnessSec = freshness(lastCheckedAt, now)
        val filters = filtersStore.get(trackId) ?: TrackFilters()

        return mapSubscriptionToTrack(
            subscription = subscription,
            trackId = trackId,
            filters = filters,
            stats = stats,
            lastCheckedAt = lastCheckedAt,
            freshnessSec = freshnessSec,
        )
    }

    private suspend fun upsertTrackLocal(track: Track): Track {
        val subscriptionId = trackIdToSubscriptionId(track.id)
        val existing = subscriptionId?.let { findSubscription(it) }
        if (existing != null) {
            if (existing.isActive != (track.state == TrackState.ACTIVE)) {
                updateSubscriptionActive(existing, track.state == TrackState.ACTIVE)
            }
            storeFilters(track.id, track.filters)
            return getTrackLocal(track.id)
        }

        val rawInput = resolveRawInput(track)?.trim().orEmpty()
        if (rawInput.isBlank()) error("Track target is empty")

        return when (val result = subscriptionsRepository.addSubscription(AddSubscriptionRequest(rawInput))) {
            is AddSubscriptionResult.Created -> {
                val createdId = subscriptionIdToTrackId(result.id)
                if (track.state == TrackState.PAUSED) {
                    pauseLocal(createdId)
                }
                storeFilters(createdId, track.filters)
                getTrackLocal(createdId)
            }
            is AddSubscriptionResult.AlreadyExists -> getTrackLocal(subscriptionIdToTrackId(result.id))
            is AddSubscriptionResult.LimitReached -> error("Достигнут лимит треков: ${result.max}")
            is AddSubscriptionResult.InvalidInput -> error(result.reason)
        }
    }

    private suspend fun pauseLocal(trackId: TrackId) {
        val subscription = requireSubscription(trackId)
        if (subscription.isActive) {
            updateSubscriptionActive(subscription, isActive = false)
        }
    }

    private suspend fun resumeLocal(trackId: TrackId) {
        val subscription = requireSubscription(trackId)
        if (!subscription.isActive) {
            updateSubscriptionActive(subscription, isActive = true)
        }
    }

    private suspend fun deleteLocal(trackId: TrackId) {
        val subscriptionId = trackIdToSubscriptionId(trackId) ?: error("Track not found")
        subscriptionsRepository.deleteSubscription(subscriptionId)
        filtersStore.clear(trackId)
        mutex.withLock {
            top10Cache.remove(trackId)
            statsCache.remove(trackId)
        }
    }

    private suspend fun listTracksLocal(): List<Track> {
        val subscriptions = subscriptionsRepository.listSubscriptions()
        val notifications = runCatching { subscriptionsRepository.listNotifications() }.getOrElse { emptyList() }
        val unreadBySubscriptionId = notifications
            .filter { !it.isRead && it.subscriptionId != null }
            .groupBy { it.subscriptionId!! }
            .mapValues { it.value.size }
        val lastEventAtBySubscriptionId = notifications
            .filter { it.subscriptionId != null }
            .groupBy { it.subscriptionId!! }
            .mapValues { entry -> entry.value.maxBy { it.createdAtMillis }.createdAtMillis }

        val now = clock()
        val (statsSnapshot, top10Snapshot) = mutex.withLock {
            statsCache.toMap() to top10Cache.toMap()
        }

        return subscriptions.map { subscription ->
            val trackId = subscriptionIdToTrackId(subscription.id)
            val cachedStats = statsSnapshot[trackId] ?: TrackStats(offersCount = 0)
            val lastEventAt = lastEventAtBySubscriptionId[subscription.id] ?: cachedStats.lastEventAt
            val newEventsCount = unreadBySubscriptionId[subscription.id] ?: 0
            val stats = cachedStats.copy(
                newEventsCount = newEventsCount,
                lastEventAt = lastEventAt,
            )
            val lastCheckedAt = top10Snapshot[trackId]?.computedAt ?: subscription.updatedAtMillis
            val freshnessSec = freshness(lastCheckedAt, now)
            val filters = filtersStore.get(trackId) ?: TrackFilters()

            mapSubscriptionToTrack(
                subscription = subscription,
                trackId = trackId,
                filters = filters,
                stats = stats,
                lastCheckedAt = lastCheckedAt,
                freshnessSec = freshnessSec,
            )
        }
    }

    private suspend fun mergeRemoteTracks(tracks: List<Track>): List<Track> {
        val now = clock()
        val (statsSnapshot, top10Snapshot) = mutex.withLock {
            statsCache.toMap() to top10Cache.toMap()
        }
        return tracks.map { track ->
            val cachedStats = statsSnapshot[track.id]
            val cachedTop10 = top10Snapshot[track.id]
            if (cachedStats == null && cachedTop10 == null) return@map track

            val mergedStats = if (cachedStats != null) {
                track.stats.copy(
                    offersCount = if (cachedStats.offersCount > 0) cachedStats.offersCount else track.stats.offersCount,
                    bestPrice = cachedStats.bestPrice ?: track.stats.bestPrice,
                    sourcesCount = if (cachedStats.sourcesCount > 0) cachedStats.sourcesCount else track.stats.sourcesCount,
                )
            } else {
                track.stats
            }
            val lastCheckedAt = cachedTop10?.computedAt ?: track.lastCheckedAt
            val freshnessSec = freshness(lastCheckedAt, now)
            track.copy(stats = mergedStats, lastCheckedAt = lastCheckedAt, freshnessSec = freshnessSec)
        }
    }

    override suspend fun getTop10(trackId: TrackId, forceRefresh: Boolean): TrackTop10 {
        if (canUseRemote()) {
            val snapshot = top10Policy.get(trackId, forceRefresh) { force ->
                remoteDataSource!!.getTop10(trackId, forceRefresh = force, preferCache = !force)
            }
            cacheSnapshot(trackId, snapshot)
            return snapshot
        }
        return getTop10Local(trackId, forceRefresh)
    }

    override suspend fun listOffers(
        trackId: TrackId,
        limit: Int,
        offset: Int,
        sort: TrackOfferSort,
    ): TrackOffersPage {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        if (canUseRemote()) {
            return remoteDataSource!!.listOffers(
                trackId = trackId,
                limit = safeLimit,
                offset = safeOffset,
                sort = sort,
            )
        }

        val snapshot = getTop10Local(trackId, forceRefresh = false)
        val sorted = when (sort) {
            TrackOfferSort.PRICE_ASC -> snapshot.items.sortedBy { it.price.minor }
            TrackOfferSort.NEWEST -> snapshot.items.sortedByDescending { it.offerId }
            TrackOfferSort.SELLER_RATING_DESC ->
                snapshot.items.sortedByDescending { it.trustScore ?: Float.MIN_VALUE }
        }
        val page = sorted.drop(safeOffset).take(safeLimit)
        return TrackOffersPage(
            items = page,
            limit = safeLimit,
            offset = safeOffset,
            canLoadMore = safeOffset + page.size < sorted.size,
            sort = sort,
        )
    }

    private suspend fun getTop10Local(trackId: TrackId, forceRefresh: Boolean): TrackTop10 {
        val now = clock()
        val cached = mutex.withLock { top10Cache[trackId] }
        if (cached != null && !forceRefresh && !isExpired(cached, now)) {
            return cached.copy(freshnessSec = max(0, ((now - cached.computedAt) / 1000L).toInt()))
        }

        val track = getTrack(trackId)
        val candidates = buildCandidates(track)
        val ranked = rankCandidates(candidates)
        val deduped = dedupCandidates(ranked)
        val topItems = deduped.take(MAX_TOP10).map { it.offer }
        val explanation = buildExplanation(candidates)
        val sourceStamps = buildSourceStamps(candidates, now)

        val top10 = TrackTop10(
            trackId = trackId,
            computedAt = now,
            freshnessSec = 0,
            items = topItems,
            explanation = explanation,
            sourceStamps = sourceStamps,
        )

        val stats = TrackStats(
            offersCount = deduped.size,
            bestPrice = deduped.minByOrNull { it.offer.price.minor }?.offer?.price,
            newEventsCount = track.stats.newEventsCount,
            lastEventAt = track.stats.lastEventAt,
            sourcesCount = candidates.map { it.sourceType }.distinct().size,
        )
        mutex.withLock {
            top10Cache[trackId] = top10
            statsCache[trackId] = stats
        }
        return top10
    }

    private suspend fun cacheSnapshot(trackId: TrackId, snapshot: TrackTop10) {
        val stats = TrackStats(
            offersCount = snapshot.items.size,
            bestPrice = snapshot.items.minByOrNull { it.price.minor }?.price,
            newEventsCount = 0,
            lastEventAt = null,
            sourcesCount = snapshot.sourceStamps.size,
        )
        mutex.withLock {
            top10Cache[trackId] = snapshot
            statsCache[trackId] = stats
        }
    }

    private suspend fun requireSubscription(trackId: TrackId): Subscription {
        val subscriptionId = trackIdToSubscriptionId(trackId) ?: error("Track not found")
        return findSubscription(subscriptionId) ?: error("Track not found")
    }

    private suspend fun findSubscription(id: Long): Subscription? {
        val subscriptions = subscriptionsRepository.listSubscriptions()
        return subscriptions.firstOrNull { it.id == id }
    }

    private fun resolveRawInput(track: Track): String? {
        return track.target.url
            ?: track.target.query
            ?: track.target.matchKey
            ?: track.categoryCode
    }

    private suspend fun updateSubscriptionActive(subscription: Subscription, isActive: Boolean) {
        val request = UpdateSubscriptionRequest(
            id = subscription.id,
            maxPriceMinor = subscription.maxPriceMinor(),
            currency = subscription.maxPriceCurrency(),
            dropPercent = subscription.dropPercent(),
            analogAppeared = subscription.hasAnalogAppeared(),
            ladderSteps = subscription.ladderSteps(),
            minAlertIntervalMinutes = subscription.minAlertIntervalMinutes,
            isActive = isActive,
        )
        val ok = subscriptionsRepository.updateSubscription(request)
        if (!ok) error("Failed to update subscription")
    }

    private suspend fun storeFilters(trackId: TrackId, filters: TrackFilters) {
        if (filters == TrackFilters()) {
            filtersStore.clear(trackId)
        } else {
            filtersStore.set(trackId, filters)
        }
    }

    private fun normalizeAttributes(values: Map<String, String>): Map<String, String> {
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

    private fun normalizeAttributesMulti(values: Map<String, List<String>>): Map<String, List<String>> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, rawValues) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) return@mapNotNull null
                val normalizedValues = rawValues
                    .asSequence()
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotBlank() }
                    .distinct()
                    .toList()
                if (normalizedValues.isEmpty()) null else normalizedKey to normalizedValues
            }
            .sortedBy { it.first.lowercase() }
            .toMap(LinkedHashMap())
    }

    private fun normalizeAttributesRange(values: Map<String, TrackAttributeRange>): Map<String, TrackAttributeRange> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, range) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) return@mapNotNull null
                val normalizedRange = TrackAttributeRange(
                    min = range.min?.trim()?.takeIf { it.isNotBlank() },
                    max = range.max?.trim()?.takeIf { it.isNotBlank() },
                    unit = range.unit?.trim()?.takeIf { it.isNotBlank() },
                )
                if (normalizedRange.min == null && normalizedRange.max == null && normalizedRange.unit == null) {
                    null
                } else {
                    normalizedKey to normalizedRange
                }
            }
            .sortedBy { it.first.lowercase() }
            .toMap(LinkedHashMap())
    }

    private fun normalizeQueryText(raw: String?): String? =
        raw?.replace("\\s+".toRegex(), " ")?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeLocale(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeUnboundTokens(values: List<String>): List<String> =
        values
            .asSequence()
            .map { token -> token.trim() }
            .filter { token -> token.isNotBlank() }
            .distinct()
            .toList()

    private fun normalizeTargetSpec(
        candidate: TrackTargetSpec?,
    ): TrackTargetSpec? {
        val categoryCode = candidate?.categoryCode
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val matchKey = candidate?.matchKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val attributes = normalizeAttributes(
            candidate?.attributes.orEmpty(),
        )
        val attributesMulti = normalizeAttributesMulti(candidate?.attributesMulti.orEmpty())
        val attributesRange = normalizeAttributesRange(candidate?.attributesRange.orEmpty())
        val queryText = normalizeQueryText(candidate?.queryText)
        val schemaVersion = (candidate?.schemaVersion ?: 1).coerceAtLeast(1)
        val taxonomyVersion = candidate?.taxonomyVersion?.trim()?.takeIf { it.isNotBlank() }
        val locale = normalizeLocale(candidate?.locale)
        val unboundTokens = normalizeUnboundTokens(candidate?.unboundTokens.orEmpty())
        if (categoryCode == null &&
            matchKey == null &&
            attributes.isEmpty() &&
            attributesMulti.isEmpty() &&
            attributesRange.isEmpty() &&
            queryText == null
        ) return null
        return TrackTargetSpec(
            categoryCode = categoryCode,
            attributes = attributes,
            matchKey = matchKey,
            attributesMulti = attributesMulti,
            attributesRange = attributesRange,
            queryText = queryText,
            schemaVersion = schemaVersion,
            taxonomyVersion = taxonomyVersion,
            locale = locale,
            unboundTokens = unboundTokens,
        )
    }

    private fun normalizeTrackTarget(
        candidate: TrackTargetSpec?,
        fallbackCategoryCode: String?,
        fallbackMatchKey: String?,
        fallbackAttributes: Map<String, String>,
    ): TrackTarget {
        val normalizedSpec = normalizeTargetSpec(
            candidate = candidate ?: TrackTargetSpec(
                categoryCode = fallbackCategoryCode,
                matchKey = fallbackMatchKey,
                attributes = fallbackAttributes,
            ),
        )
        return TrackTarget(
            spec = normalizedSpec,
            categoryCode = normalizedSpec?.categoryCode,
            attributes = normalizedSpec?.attributes.orEmpty(),
            attributesMulti = normalizedSpec?.attributesMulti.orEmpty(),
            attributesRange = normalizedSpec?.attributesRange.orEmpty(),
            matchKey = normalizedSpec?.matchKey,
            queryText = normalizedSpec?.queryText,
            schemaVersion = normalizedSpec?.schemaVersion ?: 1,
            taxonomyVersion = normalizedSpec?.taxonomyVersion,
            locale = normalizedSpec?.locale,
            unboundTokens = normalizedSpec?.unboundTokens.orEmpty(),
        )
    }

    private fun mapSubscriptionToTrack(
        subscription: Subscription,
        trackId: TrackId,
        filters: TrackFilters,
        stats: TrackStats,
        lastCheckedAt: Long?,
        freshnessSec: Int?,
    ): Track {
        val type = when (subscription.scope) {
            SubscriptionScope.QUERY -> TrackType.SEARCH
            SubscriptionScope.OFFER -> TrackType.URL
        }
        val target = when (subscription.scope) {
            SubscriptionScope.QUERY -> TrackTarget(query = subscription.input)
            SubscriptionScope.OFFER -> TrackTarget(url = subscription.input)
        }
        val state = if (subscription.isActive) TrackState.ACTIVE else TrackState.PAUSED
        val alertRules = mapAlertRules(subscription.conditions)

        return Track(
            id = trackId,
            title = subscription.title,
            categoryCode = null,
            type = type,
            target = target,
            filters = filters,
            alertRules = alertRules,
            state = state,
            createdAt = subscription.createdAtMillis,
            updatedAt = subscription.updatedAtMillis,
            lastCheckedAt = lastCheckedAt,
            freshnessSec = freshnessSec,
            stats = stats,
        )
    }

    private fun mapAlertRules(conditions: List<SubscriptionCondition>): List<AlertRule> {
        return conditions.map { condition ->
            val value = when {
                condition.moneyMinor != null -> condition.moneyMinor.toString()
                condition.numberValue != null -> condition.numberValue.toString()
                else -> null
            }
            AlertRule(type = condition.type.name, value = value)
        }
    }

    private fun Subscription.maxPriceMinor(): Long? =
        conditions.firstOrNull { it.type == SubscriptionConditionType.MAX_PRICE }?.moneyMinor

    private fun Subscription.maxPriceCurrency(): String? =
        conditions.firstOrNull { it.type == SubscriptionConditionType.MAX_PRICE }?.currency

    private fun Subscription.dropPercent(): Double? =
        conditions.firstOrNull { it.type == SubscriptionConditionType.DROP_PERCENT }?.numberValue

    private fun Subscription.hasAnalogAppeared(): Boolean =
        conditions.any { it.type == SubscriptionConditionType.ANALOG_APPEARED }

    private fun Subscription.ladderSteps(): List<Double> =
        conditions.filter { it.type == SubscriptionConditionType.LADDER_STEP_PERCENT }
            .mapNotNull { it.numberValue }
            .distinct()

    private fun subscriptionIdToTrackId(id: Long): TrackId = id.toString()

    private fun trackIdToSubscriptionId(trackId: TrackId): Long? = trackId.toLongOrNull()

    private fun isExpired(top10: TrackTop10, now: Long): Boolean =
        (now - top10.computedAt) / 1000L > TOP10_TTL_SEC

    private fun freshness(lastCheckedAt: Long?, now: Long): Int? {
        if (lastCheckedAt == null || lastCheckedAt <= 0L) return null
        return max(0, ((now - lastCheckedAt) / 1000L).toInt())
    }

    private fun normalizeUrl(raw: String): String = urlNormalizer.normalize(raw).normalized

    private fun buildExplanation(candidates: List<CandidateOffer>): RankExplanation {
        val hasSignals = candidates.any { it.trustScore != null || it.deliveryDays != null || it.distanceKm != null }
        return if (hasSignals) {
            RankExplanation(
                summary = "Сначала цена, затем доверие продавцу, доставка и расстояние.",
                details = listOf("Дубликаты скрыты по source+listingId или canonicalUrl."),
            )
        } else {
            RankExplanation(
                summary = "Сортировка по цене, затем стабильный идентификатор.",
                details = listOf("Дубликаты скрыты по source+listingId или canonicalUrl."),
            )
        }
    }

    private fun buildSourceStamps(candidates: List<CandidateOffer>, now: Long): List<SourceStamp> {
        return candidates
            .groupBy { it.sourceType to it.sourceName }
            .map { (key, _) ->
                SourceStamp(
                    sourceType = key.first,
                    sourceName = key.second,
                    fetchedAt = now,
                    ttlSec = TOP10_TTL_SEC,
                )
            }
            .sortedBy { it.sourceName }
    }

    private fun dedupCandidates(candidates: List<ScoredOffer>): List<ScoredOffer> {
        val seen = HashSet<String>()
        val result = ArrayList<ScoredOffer>()
        candidates.forEach { scored ->
            val key = scored.offer.dedupKey
            if (key.isBlank()) return@forEach
            if (seen.add(key)) {
                result += scored
            }
        }
        return result
    }

    private fun rankCandidates(candidates: List<CandidateOffer>): List<ScoredOffer> {
        val hasSignals = candidates.any { it.trustScore != null || it.deliveryDays != null || it.distanceKm != null }
        val mapped = candidates.map { candidate ->
            val score = if (hasSignals) scoreCandidate(candidate) else 0.0
            ScoredOffer(score = score, offer = candidate.toRankedOffer())
        }
        val baseComparator = compareByDescending<ScoredOffer> { it.score }
            .thenBy { it.offer.price.minor }
            .thenBy { it.offer.delivery?.etaDays ?: Int.MAX_VALUE }
            .thenByDescending { it.offer.trustScore ?: Float.MIN_VALUE }
            .thenBy { it.offer.offerId }

        return if (hasSignals) {
            mapped.sortedWith(baseComparator)
        } else {
            mapped.sortedWith(
                compareBy<ScoredOffer> { it.offer.price.minor }
                    .thenBy { it.offer.offerId }
            )
        }
    }

    private fun scoreCandidate(candidate: CandidateOffer): Double {
        val price = max(1L, candidate.priceMinor)
        val priceScore = 1_000_000.0 / price.toDouble()
        val trustScore = (candidate.trustScore ?: 0f).toDouble() * 120.0
        val deliveryScore = candidate.deliveryDays?.let { (30 - it).coerceAtLeast(0) * 6.0 } ?: 0.0
        val distanceScore = candidate.distanceKm?.let { (100 - it).coerceAtLeast(0f) * 2.0 } ?: 0.0
        return priceScore + trustScore + deliveryScore + distanceScore
    }

    private fun buildCandidates(track: Track): List<CandidateOffer> {
        val seedBase = track.id.hashCode().toLong().absoluteValue
        val priceSeed = min(120_000L, 8_000L + (seedBase % 90_000L))
        val urlBase = track.target.url ?: "https://example.com/item/${track.id}"
        return listOf(
            CandidateOffer(
                offerId = "${track.id}-a",
                sellerId = "seller-1",
                priceMinor = priceSeed,
                currency = "RUB",
                deliveryDays = 2,
                deliveryMinor = 0L,
                trustScore = 4.6f,
                distanceKm = 3.2f,
                badges = listOf(Badge(label = "Ниже рынка", priority = 2)),
                deeplink = urlBase,
                sourceType = "marketplace",
                sourceName = "Avito",
                listingId = "listing-42",
                canonicalUrl = urlBase,
            ),
            CandidateOffer(
                offerId = "${track.id}-b",
                sellerId = "seller-2",
                priceMinor = priceSeed + 5_000,
                currency = "RUB",
                deliveryDays = 4,
                deliveryMinor = 300L,
                trustScore = 4.9f,
                distanceKm = 8.1f,
                badges = listOf(Badge(label = "Trust score высокий", priority = 1)),
                deeplink = "$urlBase?utm_source=promo",
                sourceType = "marketplace",
                sourceName = "Avito",
                listingId = "listing-42",
                canonicalUrl = urlBase,
            ),
            CandidateOffer(
                offerId = "${track.id}-c",
                sellerId = "seller-3",
                priceMinor = max(1_000L, priceSeed - 1_500),
                currency = "RUB",
                deliveryDays = null,
                deliveryMinor = null,
                trustScore = null,
                distanceKm = null,
                badges = emptyList(),
                deeplink = "$urlBase?ref=alt",
                sourceType = "ugc",
                sourceName = "Ugc",
                listingId = "ugc-${track.id}",
                canonicalUrl = urlBase,
            ),
            CandidateOffer(
                offerId = "${track.id}-d",
                sellerId = "seller-4",
                priceMinor = priceSeed + 9_000,
                currency = "RUB",
                deliveryDays = 1,
                deliveryMinor = 0L,
                trustScore = 4.2f,
                distanceKm = 1.4f,
                badges = listOf(Badge(label = "Ближе", priority = 1)),
                deeplink = "$urlBase?src=fast",
                sourceType = "marketplace",
                sourceName = "Avito",
                listingId = "listing-99",
                canonicalUrl = urlBase,
            ),
        )
    }

    private fun CandidateOffer.toRankedOffer(): RankedOffer {
        val dedupKey = offerDedupKey(this)
        val delivery = if (deliveryDays != null || deliveryMinor != null) {
            DeliveryInfo(
                price = deliveryMinor?.let { Money(it) },
                etaDays = deliveryDays,
                method = null,
                isFree = (deliveryMinor ?: 0L) == 0L,
            )
        } else null
        return RankedOffer(
            offerId = offerId,
            sellerId = sellerId,
            price = Money(priceMinor),
            delivery = delivery,
            trustScore = trustScore,
            distanceKm = distanceKm,
            badges = badges,
            deeplink = deeplink,
            dedupKey = dedupKey,
        )
    }

    private fun offerDedupKey(candidate: CandidateOffer): String {
        val sourceKey = candidate.sourceType.trim().ifBlank { "unknown" }
        val listingId = candidate.listingId?.trim().orEmpty()
        if (listingId.isNotBlank()) return "$sourceKey:$listingId"

        val canonical = candidate.canonicalUrl?.trim()
        if (!canonical.isNullOrBlank()) return normalizeUrl(canonical)

        return normalizeUrl(candidate.deeplink)
    }

    private data class CandidateOffer(
        val offerId: String,
        val sellerId: String,
        val priceMinor: Long,
        val currency: String,
        val deliveryDays: Int?,
        val deliveryMinor: Long?,
        val trustScore: Float?,
        val distanceKm: Float?,
        val badges: List<Badge>,
        val deeplink: String,
        val sourceType: String,
        val sourceName: String,
        val listingId: String?,
        val canonicalUrl: String?,
    )

    private data class ScoredOffer(
        val score: Double,
        val offer: RankedOffer,
    )
}
