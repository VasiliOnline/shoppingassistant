package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetPurchaseFormat
import com.example.shoppingassistant.domain.facet.FacetRuntimeFilters
import com.example.shoppingassistant.domain.facet.FacetRuntimeFiltersApplier
import com.example.shoppingassistant.domain.model.BrandFacet
import com.example.shoppingassistant.domain.model.GeoMode
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.OfferFacetType
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchFacets
import com.example.shoppingassistant.domain.model.OfferSearchMeta
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.ProductFull
import com.example.shoppingassistant.domain.model.TypedAttributeFilter
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.UserBadge
import com.example.shoppingassistant.domain.model.UserPreferences
import com.example.shoppingassistant.domain.model.UserProfile
import com.example.shoppingassistant.domain.model.UserRating
import com.example.shoppingassistant.domain.model.ValueFacet
import com.example.shoppingassistant.domain.model.rawAttributes
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.server.catalog.FacetCollectionsTable
import com.example.shoppingassistant.server.catalog.FacetPresetsTable
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.catalog.Stage4ExecutionMetricSample
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepository
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepositoryImpl
import com.example.shoppingassistant.server.catalog.Stage4ExecutionStream
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale
import org.slf4j.LoggerFactory

/**
 * Реализация репозитория офферов на Exposed.
 * Ранжирование через RankService (общий модуль).
 */
class OfferRepositoryImpl(
    private val rankService: RankService,
    private val stage4ExecutionLayer: Stage4ExecutionLayer,
    private val stage4ExecutionObservabilityRepository: Stage4ExecutionObservabilityRepository =
        Stage4ExecutionObservabilityRepositoryImpl(),
) : OfferRepository {
    private val typedQueryCompiler = TypedAttributeQueryCompiler(stage4ExecutionLayer)
    private val logger = LoggerFactory.getLogger(OfferRepositoryImpl::class.java)

    override suspend fun searchOffers(criteria: OfferSearchCriteria): List<OfferFull> =
        DatabaseFactory.dbQuery {
            val normalizedCriteria = normalizeCriteria(criteria)
            val joined = buildBaseJoin(normalizedCriteria)
            val geoFilter = resolveGeoFilter(normalizedCriteria)
            val distanceMetersExpr = distanceMetersExprOrNull(geoFilter.centerLat, geoFilter.centerLon)
            val sliceColumns = joined.columns.toMutableList<Expression<*>>()
            if (distanceMetersExpr != null) {
                sliceColumns.add(distanceMetersExpr)
            }

            val offersQuery = joined
                .select(sliceColumns)
                .apply {
                    applyCriteriaFilters(normalizedCriteria, includeBrandFilter = true, geoFilter = geoFilter)
                    applySort(normalizedCriteria)
                }
                .limit(normalizedCriteria.limit)

            val mapped = offersQuery.map { row ->
                row.toOfferFull(
                    lang = normalizedCriteria.userLanguage,
                    distanceMetersExpr = distanceMetersExpr,
                )
            }
            val withSourceUrls = attachOfferSourceUrls(mapped)
            val withRatings = aggregateRatings(withSourceUrls)
            rankBy(normalizedCriteria, withRatings)
        }

    override suspend fun searchOffersWithFacets(
        req: OfferSearchWithFacetsRequest,
    ): OfferSearchWithFacetsResponse =
        DatabaseFactory.dbQuery {
            val criteria = normalizeCriteria(req.criteria)
            val attributeFacetKeys = normalizeAttributeFacetKeys(req.attributeFacetKeys)
            val joined = buildBaseJoin(criteria)
            val geoFilter = resolveGeoFilter(criteria)
            val computedAtMs = System.currentTimeMillis()

            val distanceMetersExpr = distanceMetersExprOrNull(geoFilter.centerLat, geoFilter.centerLon)
            val sliceColumns = joined.columns.toMutableList<Expression<*>>()
            if (distanceMetersExpr != null) {
                sliceColumns.add(distanceMetersExpr)
            }

            val offersQuery = joined
                .select(sliceColumns)
                .apply {
                    applyCriteriaFilters(criteria, includeBrandFilter = true, geoFilter = geoFilter)
                    applySort(criteria)
                }
                .limit(criteria.limit)

            val offers = offersQuery.map { row ->
                row.toOfferFull(
                    lang = criteria.userLanguage,
                    distanceMetersExpr = distanceMetersExpr,
                )
            }
            val offersWithSourceUrls = attachOfferSourceUrls(offers)
            val ranked = rankBy(criteria, aggregateRatings(offersWithSourceUrls))

            val totalExpr = OffersTable.id.countDistinct()
            val total = joined
                .select(totalExpr)
                .apply { applyCriteriaFilters(criteria, includeBrandFilter = true, geoFilter = geoFilter) }
                .firstOrNull()
                ?.get(totalExpr)
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?: 0

            val brands = if (OfferFacetType.BRAND in req.facets) {
                val includeBrandFilter = OfferFacetType.BRAND !in req.excludeFacetFilters
                buildBrandFacets(
                    base = joined,
                    criteria = criteria,
                    includeBrandFilter = includeBrandFilter,
                    geoFilter = geoFilter,
                )
            } else {
                emptyList()
            }

            val conditions = if (OfferFacetType.CONDITION in req.facets) {
                val includeConditionFilter = OfferFacetType.CONDITION !in req.excludeFacetFilters
                buildConditionFacets(
                    base = joined,
                    criteria = criteria,
                    includeConditionFilter = includeConditionFilter,
                    geoFilter = geoFilter,
                )
            } else {
                emptyList()
            }

            val deliveryChannels = if (OfferFacetType.DELIVERY_CHANNEL in req.facets) {
                val includeDeliveryFilter = OfferFacetType.DELIVERY_CHANNEL !in req.excludeFacetFilters
                buildDeliveryChannelFacets(
                    base = joined,
                    criteria = criteria,
                    includeDeliveryChannelFilter = includeDeliveryFilter,
                    geoFilter = geoFilter,
                )
            } else {
                emptyList()
            }

            val attributeFacets = if (attributeFacetKeys.isNotEmpty()) {
                buildAttributeFacets(
                    base = joined,
                    criteria = criteria,
                    attributeFacetKeys = attributeFacetKeys,
                    geoFilter = geoFilter,
                )
            } else {
                emptyMap()
            }

            val facets = OfferSearchFacets(
                brands = brands,
                conditions = conditions,
                deliveryChannels = deliveryChannels,
                attributes = attributeFacets,
            )

            OfferSearchWithFacetsResponse(
                offers = ranked,
                total = total,
                facets = facets,
                generatedAtMs = computedAtMs,
                meta = OfferSearchMeta(
                    computedAtMs = computedAtMs,
                    totalCount = total,
                    filtersHash = buildFiltersHash(
                        criteria = criteria,
                        facets = req.facets,
                        excludeFacetFilters = req.excludeFacetFilters,
                        attributeFacetKeys = attributeFacetKeys,
                        geoFilter = geoFilter,
                    ),
                    geoMode = geoFilter.mode,
                    radiusKmApplied = geoFilter.radiusKmApplied,
                ),
            )
        }

    private fun buildBaseJoin(criteria: OfferSearchCriteria): ColumnSet =
        OffersTable
            .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
            .leftJoin(UserPreferencesTable, { OffersTable.userId }, { UserPreferencesTable.userId })
            .leftJoin(UserProfilesTable, { OffersTable.userId }, { UserProfilesTable.userId })
            .leftJoin(SellerStatsTable, { OffersTable.userId }, { SellerStatsTable.userId })
            .let { base ->
                if (criteria.userLanguage != null) {
                    base.leftJoin(
                        ProductI18nTable,
                        { ProductsTable.id },
                        { ProductI18nTable.productId },
                    )
                } else {
                    base
                }
            }

    private fun normalizeCriteria(criteria: OfferSearchCriteria): OfferSearchCriteria {
        val normalizedFacetCollectionCode = stage4ExecutionLayer.normalizeCatalogCode(criteria.facetCollectionCode)
        val normalizedFacetPresetCode = stage4ExecutionLayer.normalizeCatalogCode(criteria.facetPresetCode)

        val trimmedCriteria = criteria.copy(
            brand = criteria.brand?.trim()?.takeIf { it.isNotEmpty() },
            model = criteria.model?.trim()?.takeIf { it.isNotEmpty() },
            brands = criteria.brands.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            deliveryChannels = criteria.deliveryChannels.map { it.trim() }.filter { it.isNotEmpty() },
            location = criteria.location?.trim()?.takeIf { it.isNotEmpty() },
            sellerQuery = criteria.sellerQuery?.trim()?.takeIf { it.isNotEmpty() },
            sellerCity = criteria.sellerCity?.trim()?.takeIf { it.isNotEmpty() },
            sellerCountryCode = criteria.sellerCountryCode?.trim()?.takeIf { it.isNotEmpty() },
            userCountry = criteria.userCountry?.trim()?.takeIf { it.isNotEmpty() },
            userLanguage = criteria.userLanguage?.trim()?.takeIf { it.isNotEmpty() },
            facetCollectionCode = normalizedFacetCollectionCode,
            facetPresetCode = normalizedFacetPresetCode,
            querySessionId = criteria.querySessionId?.trim()?.takeIf { it.isNotEmpty() },
        )
        val withServerPreset = applyFacetPresetCriteria(trimmedCriteria)

        val normalizedCategoryCode = withServerPreset.categoryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
        val normalizedRawAttributes = stage4ExecutionLayer.normalizeAttributesForSearch(
            categoryCode = normalizedCategoryCode,
            attributes = withServerPreset.rawAttributes(),
        )
        val normalizedAttributes = stage4ExecutionLayer.toTypedAttributes(normalizedRawAttributes)
        val normalizedAttributeFilters = normalizeAttributeFilters(
            attributeFilters = withServerPreset.attributeFilters,
        )
        val normalizedCondition = stage4ExecutionLayer.normalizeValueForSearch("condition", withServerPreset.condition)
            ?: withServerPreset.condition?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedConditions = withServerPreset.conditions
            .mapNotNull { condition ->
                stage4ExecutionLayer.normalizeValueForSearch("condition", condition)
                    ?: condition.trim().takeIf { it.isNotEmpty() }
            }
            .distinct()

        return withServerPreset.copy(
            categoryCode = normalizedCategoryCode,
            condition = normalizedCondition,
            conditions = normalizedConditions,
            attributes = normalizedAttributes,
            attributeFilters = normalizedAttributeFilters,
            facetCollectionCode = stage4ExecutionLayer.normalizeCatalogCode(withServerPreset.facetCollectionCode),
            facetPresetCode = stage4ExecutionLayer.normalizeCatalogCode(withServerPreset.facetPresetCode),
        )
    }

    private fun applyFacetPresetCriteria(criteria: OfferSearchCriteria): OfferSearchCriteria {
        val collectionCode = stage4ExecutionLayer.normalizeCatalogCode(criteria.facetCollectionCode)
        val explicitPresetCode = stage4ExecutionLayer.normalizeCatalogCode(criteria.facetPresetCode)
        val normalizedQuerySessionId = criteria.querySessionId?.trim()?.takeIf { it.isNotEmpty() }
        val sessionPreset = if (collectionCode == null && explicitPresetCode == null && normalizedQuerySessionId != null) {
            loadSessionPresetContext(normalizedQuerySessionId)
        } else {
            null
        }
        val resolvedCollectionCode = collectionCode ?: sessionPreset?.facetCollectionCode
        val collection = resolvedCollectionCode?.let(::loadFacetCollection)
        val resolvedPresetCode = explicitPresetCode
            ?: collection?.presetCode?.let(stage4ExecutionLayer::normalizeCatalogCode)
            ?: sessionPreset?.facetPresetCode
        val preset = resolvedPresetCode?.let(::loadFacetPreset)
        if (normalizedQuerySessionId != null &&
            collectionCode == null &&
            explicitPresetCode == null &&
            (sessionPreset == null || preset == null)
        ) {
            recordMissingQuerySessionPresetGuardrail(
                querySessionId = normalizedQuerySessionId,
                resolvedCollectionCode = resolvedCollectionCode,
                resolvedPresetCode = resolvedPresetCode,
                reason = if (sessionPreset == null) "SESSION_CONTEXT_NOT_FOUND" else "PRESET_NOT_FOUND",
            )
        }
        if (collection == null && preset == null && sessionPreset == null) return criteria

        val runtimeBase = FacetRuntimeFilters(
            categoryCode = criteria.categoryCode ?: sessionPreset?.categoryCode,
            facetCollectionCode = resolvedCollectionCode ?: criteria.facetCollectionCode,
            facetPresetCode = resolvedPresetCode ?: explicitPresetCode ?: criteria.facetPresetCode,
            attributes = criteria.attributes,
            brands = buildSet {
                addAll(criteria.brands)
                criteria.brand?.takeIf { it.isNotBlank() }?.let { add(it) }
            },
            priceMin = criteria.priceMin?.toInt(),
            priceMax = criteria.priceMax?.toInt(),
            conditions = buildSet {
                addAll(criteria.conditions)
                criteria.condition?.takeIf { it.isNotBlank() }?.let { add(it) }
                criteria.attributes.entries
                    .firstOrNull { (key, _) -> key.equals("condition", ignoreCase = true) }
                    ?.value
                    ?.asRawString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            },
            purchaseFormat = criteria.deliveryChannels.toFacetPurchaseFormat(),
        )
        val applied = FacetRuntimeFiltersApplier.apply(
            base = runtimeBase,
            collection = collection,
            preset = preset,
        )
        val normalizedBrands = applied.brands
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val normalizedConditions = applied.conditions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val deliveryChannels = applied.purchaseFormat.toDeliveryChannels() ?: criteria.deliveryChannels

        return criteria.copy(
            categoryCode = applied.categoryCode ?: sessionPreset?.categoryCode ?: criteria.categoryCode,
            facetCollectionCode = applied.facetCollectionCode ?: resolvedCollectionCode ?: criteria.facetCollectionCode,
            facetPresetCode = applied.facetPresetCode ?: resolvedPresetCode ?: criteria.facetPresetCode,
            brand = if (normalizedBrands.isNotEmpty()) null else criteria.brand,
            brands = if (normalizedBrands.isNotEmpty()) normalizedBrands else criteria.brands,
            priceMin = applied.priceMin?.toDouble() ?: criteria.priceMin,
            priceMax = applied.priceMax?.toDouble() ?: criteria.priceMax,
            condition = normalizedConditions.firstOrNull() ?: criteria.condition,
            conditions = if (normalizedConditions.isNotEmpty()) normalizedConditions else criteria.conditions,
            deliveryChannels = deliveryChannels,
            attributes = applied.attributes,
        )
    }

    private fun recordMissingQuerySessionPresetGuardrail(
        querySessionId: String,
        resolvedCollectionCode: String?,
        resolvedPresetCode: String?,
        reason: String,
    ) {
        logger.warn(
            "offers.search.guardrail.query_session_preset_missing querySessionId={} reason={} resolvedCollectionCode={} resolvedPresetCode={}",
            querySessionId,
            reason,
            resolvedCollectionCode ?: "",
            resolvedPresetCode ?: "",
        )
        stage4ExecutionObservabilityRepository.recordInTransaction(
            Stage4ExecutionMetricSample(
                stream = Stage4ExecutionStream.OFFERS_SEARCH,
                normalizedCount = 1,
                droppedCount = 1,
                logicalDedupCount = 0,
                unknownAttributeCount = 0,
                reasonCodes = listOf("QUERY_SESSION_PRESET_NOT_FOUND"),
                metadata = mapOf(
                    "source" to "OfferRepositoryImpl.applyFacetPresetCriteria",
                    "reason" to reason,
                    "querySessionId" to querySessionId,
                    "facetCollectionCode" to resolvedCollectionCode.orEmpty(),
                    "facetPresetCode" to resolvedPresetCode.orEmpty(),
                ),
            ),
        )
    }

    private fun loadSessionPresetContext(querySessionId: String): SessionPresetContext? {
        val normalizedSessionId = querySessionId.trim()
        if (normalizedSessionId.isEmpty()) return null
        val query = CatalogPresetEventsTable.selectAll()
        query.andWhere { CatalogPresetEventsTable.querySessionId eq normalizedSessionId }
        val row = query
            .orderBy(
                CatalogPresetEventsTable.occurredAt to SortOrder.DESC,
                CatalogPresetEventsTable.receivedAt to SortOrder.DESC,
            )
            .limit(1)
            .singleOrNull()
            ?: return null

        val normalizedPresetCode = stage4ExecutionLayer.normalizeCatalogCode(row[CatalogPresetEventsTable.facetPresetCode])
            ?: return null
        val normalizedCollectionCode = stage4ExecutionLayer.normalizeCatalogCode(
            row[CatalogPresetEventsTable.facetCollectionCode],
        )
        val normalizedCategoryCode = stage4ExecutionLayer.normalizeCatalogCode(row[CatalogPresetEventsTable.categoryCode])
        return SessionPresetContext(
            categoryCode = normalizedCategoryCode,
            facetCollectionCode = normalizedCollectionCode,
            facetPresetCode = normalizedPresetCode,
        )
    }

    private fun loadFacetCollection(collectionCode: String): FacetCollection? {
        val query = FacetCollectionsTable.selectAll()
        query.andWhere { FacetCollectionsTable.collectionCode eq collectionCode }
        val row = query.limit(1).singleOrNull() ?: return null
        return FacetCollection(
            collectionCode = row[FacetCollectionsTable.collectionCode],
            categoryCode = row[FacetCollectionsTable.categoryCode],
            titleRu = row[FacetCollectionsTable.titleRu],
            browseCode = row[FacetCollectionsTable.browseCode],
            presetCode = row[FacetCollectionsTable.presetCode],
            order = row[FacetCollectionsTable.order],
            tags = row[FacetCollectionsTable.tags],
            notes = row[FacetCollectionsTable.notes],
        )
    }

    private fun loadFacetPreset(presetCode: String): FacetPreset? {
        val query = FacetPresetsTable.selectAll()
        query.andWhere { FacetPresetsTable.presetCode eq presetCode }
        val row = query.limit(1).singleOrNull() ?: return null
        return FacetPreset(
            presetCode = row[FacetPresetsTable.presetCode],
            categoryCode = row[FacetPresetsTable.categoryCode],
            titleRu = row[FacetPresetsTable.titleRu],
            order = row[FacetPresetsTable.order],
            effectiveFrom = row[FacetPresetsTable.effectiveFrom],
            effectiveTo = row[FacetPresetsTable.effectiveTo],
            rules = row[FacetPresetsTable.rules],
            notes = row[FacetPresetsTable.notes],
        )
    }

    private fun List<String>.toFacetPurchaseFormat(): FacetPurchaseFormat? {
        if (isEmpty()) return null
        val normalized = map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.toSet()
        val hasDelivery = "delivery" in normalized
        val hasPickup = "pickup" in normalized
        return when {
            hasDelivery && !hasPickup -> FacetPurchaseFormat.DELIVERY
            hasPickup && !hasDelivery -> FacetPurchaseFormat.PICKUP
            else -> null
        }
    }

    private fun FacetPurchaseFormat?.toDeliveryChannels(): List<String>? = when (this) {
        FacetPurchaseFormat.DELIVERY -> listOf("delivery")
        FacetPurchaseFormat.PICKUP -> listOf("pickup")
        null -> null
    }

    private data class SessionPresetContext(
        val categoryCode: String?,
        val facetCollectionCode: String?,
        val facetPresetCode: String,
    )

    private fun org.jetbrains.exposed.sql.Query.applyCriteriaFilters(
        criteria: OfferSearchCriteria,
        includeBrandFilter: Boolean,
        geoFilter: GeoFilter,
        includeConditionFilter: Boolean = true,
        includeDeliveryChannelFilter: Boolean = true,
    ) {
        criteria.categoryCode?.takeIf { it.isNotBlank() }?.let { category ->
            andWhere { ProductsTable.category eq category }
        }
        if (includeBrandFilter) {
            val brandKeys = criteria.brands
                .map { Normalization.key(it) }
                .filter { it.isNotBlank() }
            val brandExpr = normalizedBrandExpr()
            if (brandKeys.isNotEmpty()) {
                andWhere { brandExpr inList brandKeys }
            } else {
                criteria.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                    val key = Normalization.key(brand)
                    if (key.isNotBlank()) {
                        andWhere { brandExpr eq key }
                    }
                }
            }
        }
        criteria.model?.takeIf { it.isNotBlank() }?.let { model ->
            andWhere { ProductsTable.model eq model }
        }
        criteria.priceMin?.let { minPrice ->
            andWhere { OffersTable.priceCents greaterEq toMinorUnits(minPrice) }
        }
        criteria.priceMax?.let { maxPrice ->
            andWhere { OffersTable.priceCents lessEq toMinorUnits(maxPrice) }
        }
        val sellerCountryCode = criteria.sellerCountryCode
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: criteria.userCountry?.trim()?.takeIf { it.isNotBlank() }
        sellerCountryCode?.let { code ->
            andWhere { UserProfilesTable.countryCode eq code }
        }
        val sellerCity = criteria.sellerCity?.trim()?.takeIf { it.isNotBlank() }
        if (sellerCity != null) {
            andWhere { UserProfilesTable.city eq sellerCity }
        } else {
            criteria.location?.trim()?.takeIf { it.isNotBlank() }?.let { location ->
                andWhere { UserProfilesTable.city like "%$location%" }
            }
        }
        criteria.sellerQuery?.trim()?.takeIf { it.isNotBlank() }?.let { sellerQuery ->
            val pattern = "%$sellerQuery%"
            andWhere {
                (UserProfilesTable.displayName like pattern) or
                    (UserProfilesTable.city like pattern) or
                    (UserProfilesTable.countryCode like pattern)
            }
        }
        criteria.userLanguage?.let { lang ->
            andWhere { ProductI18nTable.lang eq lang }
        }
        criteria.updatedAfterMs?.let { updatedAfter ->
            andWhere { OffersTable.updatedAt greaterEq updatedAfter }
        }
        if (includeConditionFilter) {
            applyConditionFilters(criteria)
        }
        if (includeDeliveryChannelFilter) {
            val deliveryChannels = criteria.deliveryChannels
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
            if (deliveryChannels.isNotEmpty()) {
                val deliveryAttrOps = deliveryChannels
                    .map { channel ->
                        typedQueryCompiler.containsRaw(
                            OffersTable.attributes,
                            mapOf("delivery_channel" to channel),
                        ) or
                            typedQueryCompiler.containsRaw(
                                OffersTable.attributes,
                                mapOf("delivery" to channel),
                            )
                    }
                val deliveryAttrExpr = deliveryAttrOps.reduceOrNull { acc, op -> acc or op }
                val columnExpr = OffersTable.deliveryChannel inList deliveryChannels
                if (deliveryAttrExpr != null) {
                    andWhere { columnExpr or deliveryAttrExpr }
                } else {
                    andWhere { columnExpr }
                }
            }
        }
        if (criteria.deliverableOnly) {
            applyDeliverableOnlyFilter(criteria.userCountry)
        }
        if (geoFilter.mode == GeoMode.RADIUS &&
            geoFilter.centerLat != null &&
            geoFilter.centerLon != null &&
            geoFilter.radiusKmApplied != null
        ) {
            andWhere {
                OffersTable.locationGeog.isNotNull() or
                    (OffersTable.lat.isNotNull() and OffersTable.lon.isNotNull())
            }
            val radiusMeters = geoFilter.radiusKmApplied.toDouble() * 1000.0
            andWhere { stDWithinOp(geoFilter.centerLat, geoFilter.centerLon, radiusMeters) }
        }
        var attrs = criteria.attributes.filterKeys { key -> !key.equals("condition", ignoreCase = true) }
        if (!includeDeliveryChannelFilter) {
            attrs = attrs.filterKeys { key ->
                !key.equals("delivery_channel", ignoreCase = true) &&
                    !key.equals("delivery", ignoreCase = true)
            }
        }
        if (attrs.isNotEmpty()) {
            andWhere {
                typedQueryCompiler.containsTyped(OffersTable.attributes, attrs) or
                    typedQueryCompiler.containsTyped(ProductsTable.specs, attrs)
            }
        }

        val attributeFilters = criteria.attributeFilters.filterKeys { key ->
            val isCondition = key.equals("condition", ignoreCase = true)
            val isDelivery = key.equals("delivery_channel", ignoreCase = true) ||
                key.equals("delivery", ignoreCase = true)
            (!isCondition || includeConditionFilter) &&
                (!isDelivery || includeDeliveryChannelFilter)
        }
        val typedFilterOp = typedQueryCompiler.compileAcrossColumns(
            primaryColumn = OffersTable.attributes,
            secondaryColumn = ProductsTable.specs,
            filters = attributeFilters,
        )
        if (typedFilterOp != null) {
            andWhere { typedFilterOp }
        }
    }

    private fun org.jetbrains.exposed.sql.Query.applyConditionFilters(criteria: OfferSearchCriteria) {
        val rawConditions = buildList {
            addAll(criteria.conditions)
            criteria.condition?.let { add(it) }
            criteria.attributes.entries
                .firstOrNull { (key, _) -> key.equals("condition", ignoreCase = true) }
                ?.value
                ?.let { add(it.asRawString()) }
        }
        val expanded = rawConditions
            .flatMap { expandOfferConditionAliases(it) }
            .distinct()
        if (expanded.isEmpty()) return
        val ops = expanded.map { value ->
            val attrs = mapOf("condition" to value)
            (OffersTable.condition eq value) or
                typedQueryCompiler.containsRaw(OffersTable.attributes, attrs) or
                typedQueryCompiler.containsRaw(ProductsTable.specs, attrs)
        }
        val combined = ops.reduceOrNull { acc, op -> acc or op } ?: return
        andWhere { combined }
    }

    private fun org.jetbrains.exposed.sql.Query.applyDeliverableOnlyFilter(userCountryCode: String?) {
        val normalizedCountryCode = userCountryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val acceptedTokens = buildDeliverabilityTokens(normalizedCountryCode)
        if (acceptedTokens.isEmpty()) return

        andWhere {
            UserPreferencesTable.shippingCountries.isNull() or
                jsonArrayIsEmptyOp(UserPreferencesTable.shippingCountries) or
                jsonArrayContainsAnyIgnoreCaseOp(
                    column = UserPreferencesTable.shippingCountries,
                    acceptedValues = acceptedTokens,
                )
        }
    }

    private fun buildDeliverabilityTokens(userCountryCode: String): List<String> {
        val tokens = LinkedHashSet<String>()
        val normalizedCode = userCountryCode.trim().uppercase(Locale.ROOT)
        if (normalizedCode.isEmpty()) return emptyList()
        tokens += normalizedCode.lowercase(Locale.ROOT)

        val countryLocale = Locale("", normalizedCode)
        val countryNames = listOf(
            countryLocale.getDisplayCountry(Locale.ENGLISH),
            countryLocale.getDisplayCountry(Locale("ru", "RU")),
        )
        countryNames
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .forEach { tokens += it }

        GLOBAL_DELIVERABILITY_TOKENS.forEach { token -> tokens += token }
        return tokens.toList()
    }

    private fun jsonArrayIsEmptyOp(column: Column<*>): Op<Boolean> =
        object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("COALESCE(jsonb_array_length(")
                queryBuilder.append(column)
                queryBuilder.append("), 0) = 0")
            }
        }

    private fun jsonArrayContainsAnyIgnoreCaseOp(
        column: Column<*>,
        acceptedValues: List<String>,
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(")
            queryBuilder.append(column)
            queryBuilder.append(", '[]'::jsonb)) AS shipping_country(value) WHERE LOWER(TRIM(shipping_country.value)) IN (")
            acceptedValues.forEachIndexed { index, value ->
                if (index > 0) queryBuilder.append(", ")
                queryBuilder.registerArgument(TextColumnType(), value)
            }
            queryBuilder.append("))")
        }
    }

    private fun org.jetbrains.exposed.sql.Query.applySort(criteria: OfferSearchCriteria) {
        when (criteria.sort) {
            OfferSort.PRICE_ASC -> orderBy(OffersTable.priceCents to SortOrder.ASC)
            OfferSort.PRICE_DESC -> orderBy(OffersTable.priceCents to SortOrder.DESC)
            OfferSort.NEWEST -> orderBy(OffersTable.updatedAt to SortOrder.DESC)
            OfferSort.DELIVERY_ASC, OfferSort.DISTANCE_ASC -> {
                val distanceExpr = distanceMetersExprOrNull(criteria.centerLat, criteria.centerLon)
                if (distanceExpr != null) {
                    orderBy(distanceExpr to SortOrder.ASC)
                } else {
                    orderBy(OffersTable.updatedAt to SortOrder.DESC)
                }
            }
            else -> {} // остальное сортируем позже
        }
    }

    private fun buildBrandFacets(
        base: ColumnSet,
        criteria: OfferSearchCriteria,
        includeBrandFilter: Boolean,
        geoFilter: GeoFilter,
    ): List<BrandFacet> {
        val countExpr = OffersTable.id.countDistinct()
        val rows = base
            .select(ProductsTable.brand, countExpr)
            .apply { applyCriteriaFilters(criteria, includeBrandFilter = includeBrandFilter, geoFilter = geoFilter) }
            .groupBy(ProductsTable.brand)
            .orderBy(countExpr to SortOrder.DESC, ProductsTable.brand to SortOrder.ASC)
            .limit(BRAND_FACET_SQL_LIMIT)
            .toList()

        val merged = LinkedHashMap<String, BrandMerge>()
        rows.forEach { row ->
            val raw = row[ProductsTable.brand]?.trim().orEmpty()
            if (raw.isBlank()) return@forEach
            val key = Normalization.key(raw)
            if (key.isBlank()) return@forEach
            val count = row[countExpr].toInt()
            val existing = merged[key]
            if (existing == null) {
                merged[key] = BrandMerge(name = raw, total = count, topCount = count)
            } else {
                existing.total += count
                if (count > existing.topCount) {
                    existing.name = raw
                    existing.topCount = count
                }
            }
        }

        val locale = Locale.getDefault()
        return merged.map { (id, agg) ->
            BrandFacet(id = id, name = agg.name, count = agg.total)
        }.sortedWith(
            compareByDescending<BrandFacet> { it.count }
                .thenBy { it.name.lowercase(locale) }
        ).take(BRAND_FACET_LIMIT)
    }

    private fun buildConditionFacets(
        base: ColumnSet,
        criteria: OfferSearchCriteria,
        includeConditionFilter: Boolean,
        geoFilter: GeoFilter,
    ): List<ValueFacet> = buildValueFacets(
        base = base,
        column = OffersTable.condition,
        criteria = criteria,
        includeBrandFilter = true,
        includeConditionFilter = includeConditionFilter,
        includeDeliveryChannelFilter = true,
        geoFilter = geoFilter,
    )

    private fun buildDeliveryChannelFacets(
        base: ColumnSet,
        criteria: OfferSearchCriteria,
        includeDeliveryChannelFilter: Boolean,
        geoFilter: GeoFilter,
    ): List<ValueFacet> = buildValueFacets(
        base = base,
        column = OffersTable.deliveryChannel,
        criteria = criteria,
        includeBrandFilter = true,
        includeConditionFilter = true,
        includeDeliveryChannelFilter = includeDeliveryChannelFilter,
        geoFilter = geoFilter,
    )

    private fun buildValueFacets(
        base: ColumnSet,
        column: Column<String?>,
        criteria: OfferSearchCriteria,
        includeBrandFilter: Boolean,
        includeConditionFilter: Boolean,
        includeDeliveryChannelFilter: Boolean,
        geoFilter: GeoFilter,
    ): List<ValueFacet> {
        val countExpr = OffersTable.id.countDistinct()
        val rows = base
            .select(column, countExpr)
            .apply {
                applyCriteriaFilters(
                    criteria = criteria,
                    includeBrandFilter = includeBrandFilter,
                    includeConditionFilter = includeConditionFilter,
                    includeDeliveryChannelFilter = includeDeliveryChannelFilter,
                    geoFilter = geoFilter,
                )
            }
            .groupBy(column)
            .orderBy(countExpr to SortOrder.DESC, column to SortOrder.ASC)
            .limit(SIMPLE_FACET_SQL_LIMIT)
            .toList()

        val locale = Locale.getDefault()
        return rows.mapNotNull { row ->
            val rawValue = row[column]?.trim().orEmpty()
            if (rawValue.isBlank()) return@mapNotNull null
            ValueFacet(
                id = Normalization.key(rawValue),
                name = rawValue,
                count = row[countExpr].toInt(),
            )
        }.sortedWith(
            compareByDescending<ValueFacet> { it.count }
                .thenBy { it.name.lowercase(locale) },
        ).take(SIMPLE_FACET_LIMIT)
    }

    private fun normalizeAttributeFacetKeys(rawKeys: Set<String>): Set<String> =
        rawKeys.asSequence()
            .map(::normalizeAttributeCode)
            .filter { key -> key.isNotEmpty() }
            .filterNot { key -> key in SYSTEM_ATTRIBUTE_FACET_KEYS }
            .distinct()
            .take(ATTRIBUTE_FACET_KEY_LIMIT)
            .toCollection(LinkedHashSet())

    private fun buildAttributeFacets(
        base: ColumnSet,
        criteria: OfferSearchCriteria,
        attributeFacetKeys: Set<String>,
        geoFilter: GeoFilter,
    ): Map<String, List<ValueFacet>> {
        if (attributeFacetKeys.isEmpty()) return emptyMap()
        val locale = Locale.getDefault()
        val result = LinkedHashMap<String, List<ValueFacet>>()
        attributeFacetKeys.sorted().forEach { facetKey ->
            val criteriaWithoutSelf = criteria.withoutAttributeFacetKey(facetKey)
            val rows = base
                .select(OffersTable.attributes, ProductsTable.specs)
                .apply {
                    applyCriteriaFilters(
                        criteria = criteriaWithoutSelf,
                        includeBrandFilter = true,
                        geoFilter = geoFilter,
                    )
                }
                .toList()

            val counts = LinkedHashMap<String, Int>()
            rows.forEach { row ->
                val rawValue = resolveFacetAttributeValue(row, facetKey)
                    ?.asRawString()
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }
                    ?: return@forEach
                val normalizedValue = stage4ExecutionLayer.normalizeValueForSearch(
                    attributeCode = facetKey,
                    value = rawValue,
                ) ?: rawValue
                val rendered = normalizedValue.trim()
                if (rendered.isEmpty()) return@forEach
                counts[rendered] = (counts[rendered] ?: 0) + 1
            }

            val facets = counts.entries
                .map { (name, count) ->
                    ValueFacet(
                        id = Normalization.key(name),
                        name = name,
                        count = count,
                    )
                }
                .sortedWith(
                    compareByDescending<ValueFacet> { it.count }
                        .thenBy { it.name.lowercase(locale) },
                )
                .take(SIMPLE_FACET_LIMIT)
            result[facetKey] = facets
        }
        return result
    }

    private fun resolveFacetAttributeValue(
        row: ResultRow,
        facetKey: String,
    ): TypedAttributeValue? {
        val offerAttributes = row.tryGet(OffersTable.attributes).orEmpty()
        val productAttributes = row.tryGet(ProductsTable.specs).orEmpty()
        return findAttributeValue(offerAttributes, facetKey)
            ?: findAttributeValue(productAttributes, facetKey)
    }

    private fun findAttributeValue(
        attributes: Map<String, TypedAttributeValue>,
        facetKey: String,
    ): TypedAttributeValue? {
        attributes[facetKey]?.let { return it }
        return attributes.entries
            .firstOrNull { (key, _) -> key.equals(facetKey, ignoreCase = true) }
            ?.value
    }

    private fun OfferSearchCriteria.withoutAttributeFacetKey(facetKey: String): OfferSearchCriteria {
        val normalizedFacetKey = normalizeAttributeCode(facetKey)
        if (normalizedFacetKey.isEmpty()) return this
        val normalizedAttributes = attributes
            .filterKeys { key -> !key.equals(normalizedFacetKey, ignoreCase = true) }
        val normalizedAttributeFilters = attributeFilters
            .filterKeys { key -> !key.equals(normalizedFacetKey, ignoreCase = true) }
        return copy(
            attributes = normalizedAttributes,
            attributeFilters = normalizedAttributeFilters,
        )
    }

    private fun normalizeAttributeFilters(
        attributeFilters: Map<String, TypedAttributeFilter>,
    ): Map<String, TypedAttributeFilter> {
        if (attributeFilters.isEmpty()) return emptyMap()
        val normalized = LinkedHashMap<String, TypedAttributeFilter>()
        attributeFilters.forEach { (rawAttributeCode, filter) ->
            val attributeCode = normalizeAttributeCode(rawAttributeCode)
            if (attributeCode.isEmpty()) return@forEach

            val op = filter.op
            val value = normalizeFilterValue(attributeCode, op, filter.value)
            val values = filter.values
                .mapNotNull { item -> normalizeFilterValue(attributeCode, op, item) }
                .distinct()
            val from = normalizeFilterValue(attributeCode, op, filter.from)
            val to = normalizeFilterValue(attributeCode, op, filter.to)

            val normalizedFilter = filter.copy(
                value = value,
                values = values,
                from = from,
                to = to,
            )
            normalized[attributeCode] = normalizedFilter
        }
        return normalized
    }

    private fun normalizeFilterValue(
        attributeCode: String,
        operator: TypedAttributeOperator,
        value: TypedAttributeValue?,
    ): TypedAttributeValue? {
        value ?: return null
        return when (value) {
            is TypedAttributeValue.Text -> {
                val rawValue = value.value.trim()
                if (rawValue.isEmpty()) return null
                if (operator == TypedAttributeOperator.CONTAINS) {
                    TypedAttributeValue.Text(rawValue)
                } else {
                    val normalized = stage4ExecutionLayer.normalizeValueForSearch(attributeCode, rawValue) ?: rawValue
                    TypedAttributeValue.Text(normalized)
                }
            }
            is TypedAttributeValue.Number -> value
            is TypedAttributeValue.Bool -> value
        }
    }

    private fun normalizeAttributeCode(rawCode: String): String =
        rawCode.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(ATTRIBUTE_SEPARATOR_REGEX, "_")
            .replace(ATTRIBUTE_DISALLOWED_REGEX, "")
            .replace(MULTI_UNDERSCORE_REGEX, "_")
            .trim('_')

    private fun resolveGeoFilter(criteria: OfferSearchCriteria): GeoFilter {
        val hasCoords = criteria.centerLat != null && criteria.centerLon != null
        val hasRadius = criteria.radiusKm != null
        val requestedMode = criteria.geoMode ?: if (hasCoords && hasRadius) {
            GeoMode.RADIUS
        } else {
            GeoMode.CITY_FALLBACK
        }
        if (requestedMode != GeoMode.RADIUS || !hasCoords || !hasRadius) {
            return GeoFilter(
                mode = GeoMode.CITY_FALLBACK,
                radiusKmApplied = null,
                centerLat = null,
                centerLon = null,
            )
        }
        val applied = clampRadiusKm(criteria.radiusKm!!)
        return GeoFilter(
            mode = GeoMode.RADIUS,
            radiusKmApplied = applied,
            centerLat = criteria.centerLat,
            centerLon = criteria.centerLon,
        )
    }

    private fun clampRadiusKm(value: Int): Int =
        value.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)

    private fun buildFiltersHash(
        criteria: OfferSearchCriteria,
        facets: Set<OfferFacetType>,
        excludeFacetFilters: Set<OfferFacetType>,
        attributeFacetKeys: Set<String>,
        geoFilter: GeoFilter,
    ): String {
        val mergedConditions = buildList {
            addAll(criteria.conditions)
            criteria.condition?.let { add(it) }
            criteria.attributes.entries
                .firstOrNull { (key, _) -> key.equals("condition", ignoreCase = true) }
                ?.value
                ?.let { add(it.asRawString()) }
        }
        val normalizedConditions = mergedConditions
            .mapNotNull { normalizeOfferCondition(it) }
            .distinct()
            .sorted()
        val normalizedCondition = normalizedConditions.firstOrNull()
        val normalizedAttributes = criteria.attributes
            .filterKeys { key -> !key.equals("condition", ignoreCase = true) }
            .toSortedMap()
        val normalizedAttributeFilters = criteria.attributeFilters
            .toList()
            .sortedBy { it.first }
            .associate { (key, filter) ->
                key to filter.copy(
                    values = filter.values.sortedBy { value -> value.asRawString() },
                )
            }
        val normalizedCriteria = criteria.copy(
            brand = criteria.brand?.trim()?.ifBlank { null },
            model = criteria.model?.trim()?.ifBlank { null },
            brands = criteria.brands.map { it.trim() }.filter { it.isNotBlank() }.sorted(),
            categoryCode = criteria.categoryCode?.trim()?.ifBlank { null },
            location = criteria.location?.trim()?.ifBlank { null },
            radiusKm = geoFilter.radiusKmApplied ?: criteria.radiusKm,
            geoMode = geoFilter.mode,
            deliverableOnly = criteria.deliverableOnly,
            condition = normalizedCondition,
            conditions = normalizedConditions,
            deliveryChannels = criteria.deliveryChannels.map { it.trim() }.filter { it.isNotBlank() }.sorted(),
            attributes = normalizedAttributes,
            attributeFilters = normalizedAttributeFilters,
            userCountry = criteria.userCountry?.trim()?.ifBlank { null },
            userLanguage = criteria.userLanguage?.trim()?.ifBlank { null },
            sellerCity = criteria.sellerCity?.trim()?.ifBlank { null },
            sellerCountryCode = criteria.sellerCountryCode?.trim()?.ifBlank { null },
        )
        val payload = FilterHashPayload(
            criteria = normalizedCriteria,
            facets = facets.sortedBy { it.name },
            excludeFacetFilters = excludeFacetFilters.sortedBy { it.name },
            attributeFacetKeys = attributeFacetKeys.sorted(),
        )
        val json = filtersJson.encodeToString(payload)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
        val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "sha256:$hex"
    }

    private fun normalizedBrandExpr(): ExpressionWithColumnType<String> {
        val replaced = regexpReplace(
            expr = ProductsTable.brand,
            pattern = "[^[:alnum:]]+",
            replacement = "-",
        )
        val lowered = CustomFunction<String>("lower", TextColumnType(), replaced)
        return regexpReplace(
            expr = lowered,
            pattern = "^-+|-+$",
            replacement = "",
        )
    }

    private fun regexpReplace(
        expr: Expression<*>,
        pattern: String,
        replacement: String,
    ): ExpressionWithColumnType<String> =
        CustomFunction(
            "regexp_replace",
            TextColumnType(),
            expr,
            QueryParameter(pattern, TextColumnType()),
            QueryParameter(replacement, TextColumnType()),
            QueryParameter("g", TextColumnType()),
        )

    private fun distanceMetersExprOrNull(centerLat: Double?, centerLon: Double?): ExpressionWithColumnType<Double>? {
        if (centerLat == null || centerLon == null) return null
        return distanceMetersExpr(centerLat, centerLon)
    }

    private fun distanceMetersExpr(centerLat: Double, centerLon: Double): ExpressionWithColumnType<Double> {
        val sourcePoint = offersGeographyExpr()
        val centerPoint = geographyPointParam(centerLat, centerLon)
        return CustomFunction("ST_Distance", DoubleColumnType(), sourcePoint, centerPoint)
    }

    private fun stDWithinOp(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
    ): Op<Boolean> =
        object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("ST_DWithin(")
                offersGeographyExpr().toQueryBuilder(queryBuilder)
                queryBuilder.append(", ")
                geographyPointParam(centerLat, centerLon).toQueryBuilder(queryBuilder)
                queryBuilder.append(", ")
                queryBuilder.registerArgument(DoubleColumnType(), radiusMeters)
                queryBuilder.append(")")
            }
        }

    private fun offersGeographyExpr(): Expression<Any> =
        object : Expression<Any>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("COALESCE(")
                queryBuilder.append(OffersTable.locationGeog)
                queryBuilder.append(", ")
                geographyPointExpr(OffersTable.lat, OffersTable.lon).toQueryBuilder(queryBuilder)
                queryBuilder.append(")")
            }
        }

    private fun geographyPointExpr(
        latExpr: Expression<*>,
        lonExpr: Expression<*>,
    ): Expression<Any> =
        object : Expression<Any>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("ST_SetSRID(ST_MakePoint(")
                queryBuilder.append(lonExpr)
                queryBuilder.append(", ")
                queryBuilder.append(latExpr)
                queryBuilder.append("), 4326)::geography")
            }
        }

    private fun geographyPointParam(
        centerLat: Double,
        centerLon: Double,
    ): Expression<Any> =
        object : Expression<Any>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("ST_SetSRID(ST_MakePoint(")
                queryBuilder.registerArgument(DoubleColumnType(), centerLon)
                queryBuilder.append(", ")
                queryBuilder.registerArgument(DoubleColumnType(), centerLat)
                queryBuilder.append("), 4326)::geography")
            }
        }

    private data class BrandMerge(
        var name: String,
        var total: Int,
        var topCount: Int,
    )

    private data class GeoFilter(
        val mode: GeoMode,
        val radiusKmApplied: Int?,
        val centerLat: Double?,
        val centerLon: Double?,
    )

    @Serializable
    private data class FilterHashPayload(
        val criteria: OfferSearchCriteria,
        val facets: List<OfferFacetType>,
        val excludeFacetFilters: List<OfferFacetType>,
        val attributeFacetKeys: List<String>,
    )

    private fun rankBy(
        criteria: OfferSearchCriteria,
        offers: List<OfferFull>,
    ): List<OfferFull> {
        return when (criteria.sort) {
            OfferSort.RANK -> rankByRankService(criteria, offers)
            else -> OfferRanking.rank(offers, criteria.sort)
        }
    }

    private fun rankByRankService(
        criteria: OfferSearchCriteria,
        offers: List<OfferFull>,
    ): List<OfferFull> {
        val dtos = offers.map {
            com.example.shoppingassistant.domain.model.ProductDto(
                id = it.product.id,
                title = it.product.title,
                brand = it.product.brand,
                model = it.product.model,
                price = it.price.toMajor(),
                deliveryTime = null, // нет в модели OfferFull; заполняйте при расширении данных
                sellerRating = it.seller.rating?.value,
                sellerRatingCount = it.seller.rating?.count,
            )
        }
        val rankedIds = rankService.topN(
            dtos,
            com.example.shoppingassistant.domain.model.NormalizedQuery(
                brand = criteria.brand ?: criteria.brands.firstOrNull().orEmpty(),
                model = criteria.model.orEmpty(),
                attributes = criteria.attributes,
            ),
            n = dtos.size,
        ).map { it.first.id }

        val order = rankedIds.withIndex().associate { it.value to it.index }
        return offers.sortedBy { order[it.product.id] ?: Int.MAX_VALUE }
    }

    private fun aggregateRatings(offers: List<OfferFull>): List<OfferFull> {
        val ids = offers.mapNotNull { it.seller.id.toLongOrNull() }.distinct()
        if (ids.isEmpty()) return offers

        val rows = UserReviewsTable
            .selectAll()
            .where { UserReviewsTable.toUserId inList ids }
            .toList()

        val aggregates = rows
            .groupBy { it[UserReviewsTable.toUserId] }
            .mapValues { (_, items) ->
                val scores = items.mapNotNull { it[UserReviewsTable.score]?.toDouble() }
                val avg = if (scores.isNotEmpty()) scores.average() else 0.0
                val cnt = scores.size.toLong()
                avg to cnt
            }

        if (aggregates.isEmpty()) return offers

        return offers.map { offer ->
            val sid = offer.seller.id.toLongOrNull()
            val agg = sid?.let { aggregates[it] }
            if (agg == null) offer
            else {
                val (avg, cnt) = agg
                offer.copy(
                    seller = offer.seller.copy(
                        rating = UserRating(
                            value = avg,
                            count = cnt.toInt(),
                        ),
                    ),
                )
            }
        }
    }

    private fun attachOfferSourceUrls(offers: List<OfferFull>): List<OfferFull> {
        if (offers.isEmpty()) return offers
        val offerIds = offers.mapNotNull { offer -> offer.id.toLongOrNull() }.distinct()
        if (offerIds.isEmpty()) return offers

        val urlByOfferId = LinkedHashMap<Long, String>()
        OfferSourcesTable
            .select(OfferSourcesTable.offerId, OfferSourcesTable.canonicalUrl, OfferSourcesTable.sourceUrl)
            .where { OfferSourcesTable.offerId inList offerIds }
            .forEach { row ->
                val offerId = row[OfferSourcesTable.offerId]
                val canonical = row[OfferSourcesTable.canonicalUrl]?.trim()?.takeIf { value -> value.isNotEmpty() }
                val source = row[OfferSourcesTable.sourceUrl].trim().takeIf { value -> value.isNotEmpty() }
                val resolved = canonical ?: source ?: return@forEach
                val current = urlByOfferId[offerId]
                // Prefer canonical URL if available; otherwise keep the first valid source URL.
                if (current == null || canonical != null) {
                    urlByOfferId[offerId] = resolved
                }
            }

        if (urlByOfferId.isEmpty()) return offers

        return offers.map { offer ->
            val offerId = offer.id.toLongOrNull() ?: return@map offer
            val externalUrl = urlByOfferId[offerId] ?: return@map offer
            val enrichedAttributes = offer.attributes.toMutableMap().apply {
                put("external_url", TypedAttributeValue.Text(externalUrl))
                put("source_url", TypedAttributeValue.Text(externalUrl))
            }
            offer.copy(attributes = enrichedAttributes)
        }
    }

    private fun ResultRow.toOfferFull(
        lang: String?,
        distanceMetersExpr: ExpressionWithColumnType<Double>?,
    ): OfferFull {
        val productId = this[OffersTable.productId]
        val product = ProductFull(
            id = productId.toString(),
            brand = this.tryGet(ProductsTable.brand),
            model = this.tryGet(ProductsTable.model),
            title = this.tryGet(ProductI18nTable.title) ?: this.tryGet(ProductsTable.titleNorm).orEmpty(),
            imageUrls = this.tryGet(ProductsTable.imageUrls) ?: emptyList(),
            specs = this.tryGet(ProductsTable.specs) ?: emptyMap(),
            description = this.tryGet(ProductI18nTable.description) ?: this.tryGet(ProductsTable.description),
            gtin = this.tryGet(ProductsTable.gtin),
            mpn = this.tryGet(ProductsTable.mpn),
            sku = this.tryGet(ProductsTable.sku),
            updatedAt = this.tryGet(ProductsTable.updatedAt),
            i18n = if (lang != null && this.tryGet(ProductI18nTable.title) != null) {
                listOf(
                    com.example.shoppingassistant.domain.model.ProductI18n(
                        lang = lang,
                        title = this.tryGet(ProductI18nTable.title)!!,
                        description = this.tryGet(ProductI18nTable.description),
                    ),
                )
            } else {
                emptyList()
            },
        )

        val userPrefs = UserPreferences(
            badges = this.tryGet(UserPreferencesTable.badges)?.mapNotNull { name -> runCatching { UserBadge.valueOf(name) }.getOrNull() }
                ?: emptyList(),
            shippingCountries = this.tryGet(UserPreferencesTable.shippingCountries) ?: emptyList(),
        )

        val seller = UserProfile(
            id = this[OffersTable.userId].toString(),
            name = this.tryGet(UserProfilesTable.displayName) ?: this[OffersTable.userId].toString(),
            avatarUrl = this.tryGet(UserProfilesTable.avatarUrl),
            countryCode = this.tryGet(UserProfilesTable.countryCode),
            city = this.tryGet(UserProfilesTable.city),
            rating = UserRating(
                value = this.tryGet(SellerStatsTable.ratingValue) ?: this.tryGet(UserPreferencesTable.ratingValue) ?: 0.0,
                count = this.tryGet(SellerStatsTable.ratingCount) ?: this.tryGet(UserPreferencesTable.ratingCount) ?: 0,
            ),
            preferences = userPrefs,
        )

        val baseAttrs = this.tryGet(OffersTable.attributes) ?: emptyMap()
        val distanceMeters = distanceMetersExpr?.let { this[it] }
        val distanceKm = distanceMeters?.div(1000.0)
        val attrs = if (distanceKm != null) {
            baseAttrs + ("distance_km" to TypedAttributeValue.Number(roundDistanceKm(distanceKm)))
        } else {
            baseAttrs
        }

        return OfferFull(
            id = this[OffersTable.id].toString(),
            product = product,
            seller = seller,
            price = Money(this[OffersTable.priceCents]),
            currency = this[OffersTable.currency],
            attributes = attrs,
            description = this.tryGet(OffersTable.description),
            imageUrls = this.tryGet(OffersTable.imageUrls) ?: emptyList(),
            status = this[OffersTable.status].let { runCatching { com.example.shoppingassistant.domain.model.OfferStatus.valueOf(it) }.getOrDefault(com.example.shoppingassistant.domain.model.OfferStatus.ACTIVE) },
            updatedAt = this.tryGet(OffersTable.updatedAt),
        )
    }

    private fun roundDistanceKm(distanceKm: Double): Double =
        BigDecimal(distanceKm).setScale(2, RoundingMode.HALF_UP).toDouble()

    private fun <T> ResultRow.tryGet(column: Column<T>): T? =
        runCatching { this[column] }.getOrNull()

    private companion object {
        private const val BRAND_FACET_SQL_LIMIT = 100
        private const val BRAND_FACET_LIMIT = 50
        private const val SIMPLE_FACET_SQL_LIMIT = 100
        private const val SIMPLE_FACET_LIMIT = 50
        private const val ATTRIBUTE_FACET_KEY_LIMIT = 32
        private const val MIN_RADIUS_KM = 1
        private const val MAX_RADIUS_KM = 5
        private val SYSTEM_ATTRIBUTE_FACET_KEYS = setOf(
            "brand",
            "price",
            "price_rub",
            "condition",
            "delivery_channel",
            "delivery",
            "purchase_format",
        )
        private val GLOBAL_DELIVERABILITY_TOKENS = setOf(
            "world",
            "worldwide",
            "global",
            "international",
            "intl",
            "all",
        )
        private val ATTRIBUTE_SEPARATOR_REGEX = Regex("[\\s\\-]+")
        private val ATTRIBUTE_DISALLOWED_REGEX = Regex("[^\\p{L}\\p{N}_]")
        private val MULTI_UNDERSCORE_REGEX = Regex("_+")
        private val filtersJson = Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }

    private fun toMinorUnits(price: Double): Long =
        Money.fromMajor(price).minor
}
