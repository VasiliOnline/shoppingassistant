package com.example.shoppingassistant.server.contracts

import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ScoreBreakdown
import com.example.shoppingassistant.core.rank.ScoringEngine
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.Stage22ValueType
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfCondition
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule
import com.example.shoppingassistant.domain.facet.FacetPresetRule
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.profile.DeliveryAddressLocation
import com.example.shoppingassistant.domain.profile.DeliveryAreaScope
import com.example.shoppingassistant.domain.profile.SellerDeliveryZone
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackMatchKeyFactory
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackTargetSpec
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.server.catalog.AttributeDefsTable
import com.example.shoppingassistant.server.catalog.CatalogRepositoryImpl
import com.example.shoppingassistant.server.catalog.CatalogStage4ExecutionMetricsTable
import com.example.shoppingassistant.server.catalog.CatalogStage4TypedConstraintsTable
import com.example.shoppingassistant.server.catalog.CategoryAttributesTable
import com.example.shoppingassistant.server.catalog.FacetCollectionsTable
import com.example.shoppingassistant.server.catalog.FacetPresetsTable
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayerImpl
import com.example.shoppingassistant.server.catalog.Stage4ExecutionStream
import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.AlertsTable
import com.example.shoppingassistant.server.offers.CatalogPresetEventsTable
import com.example.shoppingassistant.server.offers.OfferPriceHistoryTable
import com.example.shoppingassistant.server.offers.OfferRepositoryImpl
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductI18nTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.SellerStatsTable
import com.example.shoppingassistant.server.offers.UserPreferencesTable
import com.example.shoppingassistant.server.offers.UserProfilesTable
import com.example.shoppingassistant.server.offers.UserReviewsTable
import com.example.shoppingassistant.server.tracks.TrackCreateRequest
import com.example.shoppingassistant.server.tracks.TrackCreateResult
import com.example.shoppingassistant.server.tracks.TrackDedupBackfillServiceImpl
import com.example.shoppingassistant.server.tracks.TrackEventsPage
import com.example.shoppingassistant.server.tracks.TrackEventsRepository
import com.example.shoppingassistant.server.tracks.TrackEventsTable
import com.example.shoppingassistant.server.tracks.TrackTargetPostMigrationGuardServiceImpl
import com.example.shoppingassistant.server.tracks.TracksRepositoryImpl
import com.example.shoppingassistant.server.tracks.TracksTable
import com.example.shoppingassistant.server.tracks.top10.TrackTop10SnapshotsTable
import com.example.shoppingassistant.server.tracks.top10.refresh.NoopRateLimiter
import com.example.shoppingassistant.server.tracks.top10.refresh.TrackRefreshCandidate
import com.example.shoppingassistant.server.tracks.top10.refresh.TrackTop10SnapshotBuilder
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogTracksContractIntegrationTest {
    private lateinit var offerRepository: OfferRepositoryImpl
    private lateinit var tracksRepository: TracksRepositoryImpl
    private lateinit var top10Builder: TrackTop10SnapshotBuilder
    private lateinit var catalogRepository: CatalogRepositoryImpl

    @Before
    fun setUp() {
        requireDocker()
        runBlocking {
            cleanTables()
        }
        val stage4ExecutionLayer = Stage4ExecutionLayerImpl()
        offerRepository = OfferRepositoryImpl(
            rankService = RankService(engine = ZeroScoringEngineContract),
            stage4ExecutionLayer = stage4ExecutionLayer,
        )
        tracksRepository = TracksRepositoryImpl(
            eventsRepository = NoopTrackEventsRepository,
            offerRepository = offerRepository,
        )
        top10Builder = TrackTop10SnapshotBuilder(
            offerRepository = offerRepository,
            rateLimiter = NoopRateLimiter(),
        )
        catalogRepository = CatalogRepositoryImpl()
    }

    @Test
    fun track_filters_apply_for_track_offers_and_top10() = runBlocking {
        val ownerId = createUser(
            displayName = "Owner",
            city = "Moscow",
            countryCode = "RU",
        )
        val sellerMatchId = createUser(
            displayName = "Best Seller",
            city = "Moscow",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )
        val sellerMismatchId = createUser(
            displayName = "Other Seller",
            city = "Saint-Petersburg",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )
        val productId = createProduct(
            categoryCode = TECH_PHONES,
            brand = "acme",
            model = "x1",
            specs = mapOf(
                "color" to TypedAttributeValue.Text("black"),
            ),
        )
        val matchedOfferId = createOffer(
            productId = productId,
            sellerId = sellerMatchId,
            priceCents = 100_000L,
            condition = "new",
            deliveryChannel = "delivery",
            attributes = mapOf("color" to TypedAttributeValue.Text("black")),
        )
        createOffer(
            productId = productId,
            sellerId = sellerMismatchId,
            priceCents = 90_000L,
            condition = "used",
            deliveryChannel = "pickup",
            attributes = mapOf("color" to TypedAttributeValue.Text("white")),
        )

        val matchKey = TrackMatchKeyFactory.fromBrandModel("acme", "x1")
        assertNotNull(matchKey)
        val createResult = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.PRODUCT,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        matchKey = matchKey,
                    ),
                ),
                filters = TrackFilters(
                    region = "Moscow",
                    delivery = "Доставка",
                    condition = "Новый",
                    seller = "Best",
                ),
                title = "Contract Filters",
            ),
        )
        val created = assertIs<TrackCreateResult.Created>(createResult).track
        val trackId = created.id.toLong()

        val offersPage = tracksRepository.listOffers(
            userId = ownerId,
            trackId = trackId,
            limit = 20,
            offset = 0,
            sort = TrackOfferSort.PRICE_ASC,
        )
        assertEquals(listOf(matchedOfferId.toString()), offersPage.items.map { it.offerId })

        val snapshot = top10Builder.buildSnapshot(
            TrackRefreshCandidate(
                trackId = trackId,
                userId = ownerId,
                type = TrackType.PRODUCT,
                matchKey = matchKey,
                categoryCode = TECH_PHONES,
                targetAttributes = emptyMap(),
                filters = TrackFilters(
                    region = "Moscow",
                    delivery = "Доставка",
                    condition = "Новый",
                    seller = "Best",
                ),
                userCountry = "RU",
                failCount = 0,
            ),
        )
        assertEquals(listOf(matchedOfferId.toString()), snapshot.items.map { it.offerId })
    }

    @Test
    fun product_track_without_match_key_uses_category_and_attributes() = runBlocking {
        val ownerId = createUser(
            displayName = "Owner Without Match",
            city = "Moscow",
            countryCode = "RU",
        )
        val sellerId = createUser(
            displayName = "Attribute Seller",
            city = "Moscow",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )

        val productMatched = createProduct(
            categoryCode = TECH_PHONES,
            brand = "no-brand",
            model = "alpha",
        )
        val productMismatched = createProduct(
            categoryCode = TECH_PHONES,
            brand = "no-brand",
            model = "beta",
        )

        val matchedOfferId = createOffer(
            productId = productMatched,
            sellerId = sellerId,
            priceCents = 88_000L,
            condition = "new",
            deliveryChannel = "delivery",
            attributes = mapOf("battery_health_percent" to TypedAttributeValue.Number(98.0)),
        )
        createOffer(
            productId = productMismatched,
            sellerId = sellerId,
            priceCents = 79_000L,
            condition = "new",
            deliveryChannel = "delivery",
            attributes = mapOf("battery_health_percent" to TypedAttributeValue.Number(85.0)),
        )

        val createResult = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.PRODUCT,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        attributes = mapOf("battery_health_percent" to "98"),
                    ),
                ),
                filters = TrackFilters(
                    extra = mapOf("battery_health_percent" to "98"),
                ),
                title = "Product Without Match",
            ),
        )
        val created = assertIs<TrackCreateResult.Created>(createResult).track
        val trackId = created.id.toLong()

        val offersPage = tracksRepository.listOffers(
            userId = ownerId,
            trackId = trackId,
            limit = 20,
            offset = 0,
            sort = TrackOfferSort.PRICE_ASC,
        )
        assertEquals(listOf(matchedOfferId.toString()), offersPage.items.map { it.offerId })

        val snapshot = top10Builder.buildSnapshot(
            TrackRefreshCandidate(
                trackId = trackId,
                userId = ownerId,
                type = TrackType.PRODUCT,
                matchKey = null,
                categoryCode = TECH_PHONES,
                targetAttributes = mapOf("battery_health_percent" to "98"),
                filters = TrackFilters(extra = mapOf("battery_health_percent" to "98")),
                userCountry = "RU",
                failCount = 0,
            ),
        )
        assertEquals(listOf(matchedOfferId.toString()), snapshot.items.map { it.offerId })
    }

    @Test
    fun dedup_uses_target_plus_filter_profile_and_backfill_rewrites_legacy_keys() = runBlocking {
        val ownerId = createUser(displayName = "Dedup Owner", city = "Moscow", countryCode = "RU")
        val matchKey = TrackMatchKeyFactory.fromBrandModel("acme", "x1")
        assertNotNull(matchKey)

        val first = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.PRODUCT,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        matchKey = matchKey,
                    ),
                ),
                filters = TrackFilters(extra = mapOf("color" to "black")),
                title = "Black",
            ),
        )
        val second = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.PRODUCT,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        matchKey = matchKey,
                    ),
                ),
                filters = TrackFilters(extra = mapOf("color" to "white")),
                title = "White",
            ),
        )
        val duplicate = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.PRODUCT,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        matchKey = matchKey,
                    ),
                ),
                filters = TrackFilters(extra = mapOf("color" to "black")),
                title = "Black Duplicate",
            ),
        )

        val firstTrack = assertIs<TrackCreateResult.Created>(first).track
        val secondTrack = assertIs<TrackCreateResult.Created>(second).track
        assertIs<TrackCreateResult.AlreadyExists>(duplicate)

        DatabaseFactory.dbQuery {
            TracksTable.update({ TracksTable.id eq firstTrack.id.toLong() }) {
                it[dedupKey] = "legacy:${firstTrack.id}"
            }
            TracksTable.update({ TracksTable.id eq secondTrack.id.toLong() }) {
                it[dedupKey] = "legacy:${secondTrack.id}"
            }
        }

        val report = TrackDedupBackfillServiceImpl().runBackfill()
        assertTrue(report.updatedTracks >= 2)

        val dedupKeys = DatabaseFactory.dbQuery {
            TracksTable
                .selectAll()
                .where { TracksTable.userId eq ownerId }
                .map { row -> row[TracksTable.dedupKey] }
        }
        assertEquals(2, dedupKeys.distinct().size)
        assertTrue(dedupKeys.all { key -> key.startsWith("v2:") })
    }

    @Test
    fun dedup_backfill_detects_collisions() = runBlocking {
        val ownerId = createUser(displayName = "Collision Owner", city = "Moscow", countryCode = "RU")
        val matchKey = TrackMatchKeyFactory.fromBrandModel("acme", "x1")
        assertNotNull(matchKey)
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            repeat(2) { idx ->
                TracksTable.insert { stmt ->
                    stmt[userId] = ownerId
                    stmt[type] = TrackType.PRODUCT.name
                    stmt[TracksTable.matchKey] = matchKey
                    stmt[categoryCode] = TECH_PHONES
                    stmt[TracksTable.targetCategoryCode] = TECH_PHONES
                    stmt[TracksTable.targetAttributes] = emptyMap()
                    stmt[target] = TrackTarget(
                        spec = TrackTargetSpec(
                            categoryCode = TECH_PHONES,
                            matchKey = matchKey,
                        ),
                    )
                    stmt[filters] = TrackFilters(extra = mapOf("color" to "black"))
                    stmt[title] = "Collision $idx"
                    stmt[state] = TrackState.ACTIVE.name
                    stmt[dedupKey] = "legacy-collision-$idx"
                    stmt[createdAt] = now + idx
                    stmt[updatedAt] = now + idx
                }
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            TrackDedupBackfillServiceImpl().runBackfill()
        }
        assertTrue(error.message?.contains("collision", ignoreCase = true) == true)
    }

    @Test
    fun product_track_with_target_attributes_ignores_match_key_filter() = runBlocking {
        val ownerId = createUser(
            displayName = "Owner Attr Priority",
            city = "Moscow",
            countryCode = "RU",
        )
        val sellerId = createUser(
            displayName = "Attr Priority Seller",
            city = "Moscow",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )
        val productId = createProduct(
            categoryCode = TECH_PHONES,
            brand = "actual-brand",
            model = "actual-model",
        )
        val matchedOfferId = createOffer(
            productId = productId,
            sellerId = sellerId,
            priceCents = 91_000L,
            condition = "used",
            deliveryChannel = "delivery",
            attributes = mapOf("battery_health_percent" to TypedAttributeValue.Number(98.0)),
        )

        val mismatchingMatchKey = TrackMatchKeyFactory.fromBrandModel("other-brand", "other-model")
        assertNotNull(mismatchingMatchKey)
        val createResult = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.PRODUCT,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        attributes = mapOf("battery_health_percent" to "98"),
                        matchKey = mismatchingMatchKey,
                    ),
                ),
                filters = TrackFilters(extra = mapOf("battery_health_percent" to "98")),
                title = "Attr Priority",
            ),
        )
        val created = assertIs<TrackCreateResult.Created>(createResult).track

        val page = tracksRepository.listOffers(
            userId = ownerId,
            trackId = created.id.toLong(),
            limit = 20,
            offset = 0,
            sort = TrackOfferSort.PRICE_ASC,
        )
        assertEquals(listOf(matchedOfferId.toString()), page.items.map { it.offerId })

        val snapshot = top10Builder.buildSnapshot(
            TrackRefreshCandidate(
                trackId = created.id.toLong(),
                userId = ownerId,
                type = TrackType.PRODUCT,
                matchKey = mismatchingMatchKey,
                categoryCode = TECH_PHONES,
                targetAttributes = mapOf("battery_health_percent" to "98"),
                filters = TrackFilters(extra = mapOf("battery_health_percent" to "98")),
                userCountry = "RU",
                failCount = 0,
            ),
        )
        assertEquals(listOf(matchedOfferId.toString()), snapshot.items.map { it.offerId })
    }

    @Test
    fun legacy_target_fields_and_filters_extra_are_not_used_as_target_spec_fallback() = runBlocking {
        val ownerId = createUser(
            displayName = "Owner Strict Spec",
            city = "Moscow",
            countryCode = "RU",
        )
        val sellerId = createUser(
            displayName = "Strict Spec Seller",
            city = "Moscow",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )

        val productA = createProduct(
            categoryCode = TECH_PHONES,
            brand = "legacy",
            model = "a",
        )
        val productB = createProduct(
            categoryCode = TECH_PHONES,
            brand = "legacy",
            model = "b",
        )
        val offerA = createOffer(
            productId = productA,
            sellerId = sellerId,
            priceCents = 80_000L,
            condition = "used",
            deliveryChannel = "delivery",
            attributes = mapOf("battery_health_percent" to TypedAttributeValue.Number(98.0)),
        )
        val offerB = createOffer(
            productId = productB,
            sellerId = sellerId,
            priceCents = 81_000L,
            condition = "used",
            deliveryChannel = "delivery",
            attributes = mapOf("battery_health_percent" to TypedAttributeValue.Number(85.0)),
        )
        val now = System.currentTimeMillis()
        val insertedTrackId = DatabaseFactory.dbQuery {
            TracksTable.insert { stmt ->
                stmt[userId] = ownerId
                stmt[type] = TrackType.CATEGORY.name
                stmt[TracksTable.matchKey] = null
                stmt[TracksTable.categoryCode] = TECH_PHONES
                stmt[TracksTable.targetCategoryCode] = TECH_PHONES
                stmt[TracksTable.targetAttributes] = emptyMap()
                stmt[target] = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        attributes = emptyMap(),
                    ),
                    categoryCode = TECH_PHONES,
                    attributes = mapOf("battery_health_percent" to "98"),
                )
                stmt[filters] = TrackFilters(extra = mapOf("battery_health_percent" to "98"))
                stmt[title] = "Strict Spec"
                stmt[state] = TrackState.ACTIVE.name
                stmt[dedupKey] = "strict-spec-$now"
                stmt[createdAt] = now
                stmt[updatedAt] = now
            }[TracksTable.id]
        }

        val page = tracksRepository.listOffers(
            userId = ownerId,
            trackId = insertedTrackId,
            limit = 20,
            offset = 0,
            sort = TrackOfferSort.PRICE_ASC,
        )
        assertEquals(
            setOf(offerA.toString(), offerB.toString()),
            page.items.map { it.offerId }.toSet(),
        )
    }

    @Test
    fun post_migration_guard_reports_empty_category_and_target_attributes_quality_issues() = runBlocking {
        val ownerId = createUser(
            displayName = "Guard Owner",
            city = "Moscow",
            countryCode = "RU",
        )
        val now = System.currentTimeMillis()
        DatabaseFactory.dbQuery {
            TracksTable.insert { stmt ->
                stmt[userId] = ownerId
                stmt[type] = TrackType.CATEGORY.name
                stmt[TracksTable.matchKey] = null
                stmt[TracksTable.categoryCode] = null
                stmt[TracksTable.targetCategoryCode] = null
                stmt[TracksTable.targetAttributes] = emptyMap()
                stmt[target] = TrackTarget(spec = TrackTargetSpec())
                stmt[filters] = TrackFilters()
                stmt[title] = "Missing Category"
                stmt[state] = TrackState.ACTIVE.name
                stmt[dedupKey] = "guard-empty-category-$now"
                stmt[createdAt] = now
                stmt[updatedAt] = now
            }
            TracksTable.insert { stmt ->
                stmt[userId] = ownerId
                stmt[type] = TrackType.PRODUCT.name
                stmt[TracksTable.matchKey] = null
                stmt[TracksTable.categoryCode] = TECH_PHONES
                stmt[TracksTable.targetCategoryCode] = TECH_PHONES
                stmt[TracksTable.targetAttributes] = mapOf(
                    "   " to "bad",
                    "color" to "white",
                )
                stmt[target] = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        attributes = mapOf("color" to "black"),
                    ),
                )
                stmt[filters] = TrackFilters()
                stmt[title] = "Attributes Quality"
                stmt[state] = TrackState.ACTIVE.name
                stmt[dedupKey] = "guard-attrs-quality-$now"
                stmt[createdAt] = now + 1
                stmt[updatedAt] = now + 1
            }
        }

        val report = TrackTargetPostMigrationGuardServiceImpl().runChecks(limit = 10_000)
        assertTrue(report.scannedTracks >= 2)
        assertTrue(report.emptyTargetCategoryCode >= 1)
        assertTrue(report.targetAttributesQualityIssues >= 1)
        assertTrue(report.reasonCounts["blank-key-or-value"] ?: 0 >= 1)
        assertTrue(report.reasonCounts["spec-column-mismatch"] ?: 0 >= 1)
    }

    @Test
    fun deliverableOnly_filters_by_shippingCountries_synonyms_and_tokens() = runBlocking {
        val productId = createProduct(
            categoryCode = TECH_PHONES,
            brand = "deliver",
            model = "x1",
        )

        data class Case(
            val token: List<String>?,
            val expectedMatch: Boolean,
            val displayName: String,
            val createPreferencesRow: Boolean = true,
        )

        val cases = listOf(
            Case(token = listOf("RU"), expectedMatch = true, displayName = "Direct RU"),
            Case(token = listOf("  rUsSiA "), expectedMatch = true, displayName = "English Name"),
            Case(token = listOf(" РОССИЯ "), expectedMatch = true, displayName = "Russian Name"),
            Case(token = listOf("WorldWide"), expectedMatch = true, displayName = "Global Token"),
            Case(token = listOf("US"), expectedMatch = false, displayName = "Foreign Only"),
            Case(token = emptyList(), expectedMatch = true, displayName = "Empty Means All"),
            Case(token = null, expectedMatch = true, displayName = "Null Means All", createPreferencesRow = false),
        )

        val expectedMatchedOffers = LinkedHashSet<String>()
        val allOffers = ArrayList<String>()
        cases.forEachIndexed { index, case ->
            val sellerId = createUser(
                displayName = case.displayName,
                city = "Moscow",
                countryCode = "RU",
                shippingCountries = case.token,
                createPreferencesRow = case.createPreferencesRow,
            )
            val offerId = createOffer(
                productId = productId,
                sellerId = sellerId,
                priceCents = 70_000L + index,
                condition = "used",
                deliveryChannel = "delivery",
            )
            allOffers += offerId.toString()
            if (case.expectedMatch) {
                expectedMatchedOffers += offerId.toString()
            }
        }

        val allResult = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "deliver",
                model = "x1",
                categoryCode = TECH_PHONES,
                deliverableOnly = false,
                userCountry = "RU",
                limit = 100,
                sort = OfferSort.PRICE_ASC,
            ),
        )
        assertEquals(allOffers.toSet(), allResult.map { it.id }.toSet())

        val deliverableResult = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "deliver",
                model = "x1",
                categoryCode = TECH_PHONES,
                deliverableOnly = true,
                userCountry = "RU",
                limit = 100,
                sort = OfferSort.PRICE_ASC,
            ),
        )
        assertEquals(expectedMatchedOffers, deliverableResult.map { it.id }.toSet())
    }

    @Test
    fun deliverableOnly_matches_city_delivery_zones_against_active_address() = runBlocking {
        val productId = createProduct(
            categoryCode = TECH_PHONES,
            brand = "zone",
            model = "city",
        )
        val sellerMatchId = createUser(
            displayName = "City Match",
            city = "Moscow",
            countryCode = "RU",
            deliveryZones = listOf(
                SellerDeliveryZone(
                    id = "moscow-city",
                    scope = DeliveryAreaScope.CITY,
                    location = DeliveryAddressLocation(
                        countryCode = "RU",
                        adminArea = "Moscow",
                        locality = "Moscow",
                    ),
                ),
            ),
        )
        val sellerMissId = createUser(
            displayName = "City Miss",
            city = "Saint Petersburg",
            countryCode = "RU",
            deliveryZones = listOf(
                SellerDeliveryZone(
                    id = "spb-city",
                    scope = DeliveryAreaScope.CITY,
                    location = DeliveryAddressLocation(
                        countryCode = "RU",
                        adminArea = "Saint Petersburg",
                        locality = "Saint Petersburg",
                    ),
                ),
            ),
        )
        val matchingOfferId = createOffer(
            productId = productId,
            sellerId = sellerMatchId,
            priceCents = 90_000L,
            condition = "used",
            deliveryChannel = "delivery",
        )
        createOffer(
            productId = productId,
            sellerId = sellerMissId,
            priceCents = 91_000L,
            condition = "used",
            deliveryChannel = "delivery",
        )

        val result = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "zone",
                model = "city",
                categoryCode = TECH_PHONES,
                deliverableOnly = true,
                userCountry = "RU",
                deliveryAddress = DeliveryAddressLocation(
                    countryCode = "RU",
                    adminArea = "Moscow",
                    locality = "Moscow",
                    addressLine = "Tverskaya 1",
                ),
                limit = 100,
                sort = OfferSort.PRICE_ASC,
            ),
        )

        assertEquals(listOf(matchingOfferId.toString()), result.map { it.id })
    }

    @Test
    fun facetPreset_collection_querySession_and_guardrail_metric_work() = runBlocking {
        val sellerId = createUser(
            displayName = "Preset Seller",
            city = "Moscow",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )
        val productId = createProduct(
            categoryCode = TECH_PHONES,
            brand = "preset",
            model = "x1",
        )
        val usedOfferId = createOffer(
            productId = productId,
            sellerId = sellerId,
            priceCents = 120_000L,
            condition = "used",
            deliveryChannel = "delivery",
        )
        createOffer(
            productId = productId,
            sellerId = sellerId,
            priceCents = 110_000L,
            condition = "new",
            deliveryChannel = "delivery",
        )

        val presetCode = "FP.CONTRACT.USED"
        val collectionCode = "FC.CONTRACT.USED"
        val now = System.currentTimeMillis()
        DatabaseFactory.dbQuery {
            FacetPresetsTable.insert { stmt ->
                stmt[FacetPresetsTable.presetCode] = presetCode
                stmt[FacetPresetsTable.categoryCode] = TECH_PHONES
                stmt[FacetPresetsTable.titleRu] = "Contract Used"
                stmt[FacetPresetsTable.order] = 1
                stmt[FacetPresetsTable.effectiveFrom] = null
                stmt[FacetPresetsTable.effectiveTo] = null
                stmt[FacetPresetsTable.rules] = listOf(
                    FacetPresetRule(
                        facetKey = "condition",
                        includeValues = listOf("used"),
                    ),
                )
                stmt[FacetPresetsTable.notes] = "contract"
            }
            FacetCollectionsTable.insert { stmt ->
                stmt[FacetCollectionsTable.collectionCode] = collectionCode
                stmt[FacetCollectionsTable.categoryCode] = TECH_PHONES
                stmt[FacetCollectionsTable.titleRu] = "Contract Collection"
                stmt[FacetCollectionsTable.browseCode] = "B.CONTRACT.USED"
                stmt[FacetCollectionsTable.presetCode] = presetCode
                stmt[FacetCollectionsTable.order] = 1
                stmt[FacetCollectionsTable.tags] = emptyList()
                stmt[FacetCollectionsTable.notes] = "contract"
            }
        }

        val byPreset = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "preset",
                model = "x1",
                categoryCode = TECH_PHONES,
                facetPresetCode = presetCode.lowercase(),
                limit = 20,
                sort = OfferSort.PRICE_ASC,
            ),
        )
        assertEquals(listOf(usedOfferId.toString()), byPreset.map { it.id })

        val byCollection = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "preset",
                model = "x1",
                categoryCode = TECH_PHONES,
                facetCollectionCode = collectionCode.lowercase(),
                limit = 20,
                sort = OfferSort.PRICE_ASC,
            ),
        )
        assertEquals(listOf(usedOfferId.toString()), byCollection.map { it.id })

        val querySessionId = "qs-contract-hit"
        DatabaseFactory.dbQuery {
            CatalogPresetEventsTable.insert { stmt ->
                stmt[CatalogPresetEventsTable.idempotencyKey] = "contract-$now"
                stmt[CatalogPresetEventsTable.eventType] = "CLICK"
                stmt[CatalogPresetEventsTable.querySessionId] = querySessionId
                stmt[CatalogPresetEventsTable.categoryCode] = TECH_PHONES
                stmt[CatalogPresetEventsTable.facetCollectionCode] = collectionCode
                stmt[CatalogPresetEventsTable.facetPresetCode] = presetCode
                stmt[CatalogPresetEventsTable.offerId] = usedOfferId.toString()
                stmt[CatalogPresetEventsTable.position] = 1
                stmt[CatalogPresetEventsTable.occurredAt] = now
                stmt[CatalogPresetEventsTable.receivedAt] = now
                stmt[CatalogPresetEventsTable.eventDate] = Instant.fromEpochMilliseconds(now)
                    .toLocalDateTime(TimeZone.UTC)
                    .date
                stmt[CatalogPresetEventsTable.dataVersion] = "contract"
                stmt[CatalogPresetEventsTable.payloadJson] = emptyMap()
            }
        }

        val byQuerySession = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "preset",
                model = "x1",
                categoryCode = TECH_PHONES,
                querySessionId = querySessionId,
                limit = 20,
                sort = OfferSort.PRICE_ASC,
            ),
        )
        assertEquals(listOf(usedOfferId.toString()), byQuerySession.map { it.id })

        val missingSessionId = "qs-contract-missing"
        val missingSessionResult = offerRepository.searchOffers(
            OfferSearchCriteria(
                brand = "preset",
                model = "x1",
                categoryCode = TECH_PHONES,
                querySessionId = missingSessionId,
                limit = 20,
                sort = OfferSort.PRICE_ASC,
            ),
        )
        assertEquals(2, missingSessionResult.size)

        val guardrails = DatabaseFactory.dbQuery {
            CatalogStage4ExecutionMetricsTable
                .selectAll()
                .where { CatalogStage4ExecutionMetricsTable.stream eq Stage4ExecutionStream.OFFERS_SEARCH.code }
                .toList()
        }
        assertTrue(
            guardrails.any { row ->
                "QUERY_SESSION_PRESET_NOT_FOUND" in row[CatalogStage4ExecutionMetricsTable.reasonCodes] &&
                    row[CatalogStage4ExecutionMetricsTable.metadata]["querySessionId"] == missingSessionId
            },
        )
    }

    @Test
    fun getCategoryEffectiveSpec_returns_requiredIf_rules_from_stage4_constraints() = runBlocking {
        val requiredAttributeCode = "contract_required_if_model"
        val triggerAttributeCode = "contract_required_if_condition"
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            AttributeDefsTable.insert { stmt ->
                stmt[AttributeDefsTable.code] = triggerAttributeCode
                stmt[AttributeDefsTable.title] = "Trigger"
                stmt[AttributeDefsTable.dataType] = AttributeDataType.STRING.name
                stmt[AttributeDefsTable.requiredForSearch] = false
                stmt[AttributeDefsTable.requiredForOffer] = false
                stmt[AttributeDefsTable.requiredForExpress] = false
                stmt[AttributeDefsTable.requiredBy] = null
                stmt[AttributeDefsTable.facetEnabled] = false
                stmt[AttributeDefsTable.multiValued] = false
                stmt[AttributeDefsTable.valueDictCode] = null
            }
            AttributeDefsTable.insert { stmt ->
                stmt[AttributeDefsTable.code] = requiredAttributeCode
                stmt[AttributeDefsTable.title] = "Required if used"
                stmt[AttributeDefsTable.dataType] = AttributeDataType.STRING.name
                stmt[AttributeDefsTable.requiredForSearch] = false
                stmt[AttributeDefsTable.requiredForOffer] = false
                stmt[AttributeDefsTable.requiredForExpress] = false
                stmt[AttributeDefsTable.requiredBy] = null
                stmt[AttributeDefsTable.facetEnabled] = false
                stmt[AttributeDefsTable.multiValued] = false
                stmt[AttributeDefsTable.valueDictCode] = null
            }
            CategoryAttributesTable.insert { stmt ->
                stmt[CategoryAttributesTable.categoryCode] = TECH_PHONES
                stmt[CategoryAttributesTable.attributeCode] = triggerAttributeCode
                stmt[CategoryAttributesTable.uiOrder] = 700
                stmt[CategoryAttributesTable.isRequired] = false
            }
            CategoryAttributesTable.insert { stmt ->
                stmt[CategoryAttributesTable.categoryCode] = TECH_PHONES
                stmt[CategoryAttributesTable.attributeCode] = requiredAttributeCode
                stmt[CategoryAttributesTable.uiOrder] = 701
                stmt[CategoryAttributesTable.isRequired] = false
            }
            CatalogStage4TypedConstraintsTable.insert { stmt ->
                stmt[CatalogStage4TypedConstraintsTable.attributeCode] = requiredAttributeCode
                stmt[CatalogStage4TypedConstraintsTable.valueType] = Stage22ValueType.STRING.name
                stmt[CatalogStage4TypedConstraintsTable.enumOnly] = false
                stmt[CatalogStage4TypedConstraintsTable.expectedUnit] = null
                stmt[CatalogStage4TypedConstraintsTable.regexPattern] = null
                stmt[CatalogStage4TypedConstraintsTable.minValue] = null
                stmt[CatalogStage4TypedConstraintsTable.maxValue] = null
                stmt[CatalogStage4TypedConstraintsTable.requiredIf] = listOf(
                    Stage40RequiredIfRule(
                        categoryCode = TECH_PHONES,
                        whenAll = listOf(
                            Stage40RequiredIfCondition(
                                attributeCode = triggerAttributeCode,
                                op = AttributeConditionOp.EQUALS_ANY,
                                values = listOf("used"),
                            ),
                        ),
                    ),
                )
                stmt[CatalogStage4TypedConstraintsTable.updatedAt] = now
            }
        }

        val spec = assertNotNull(catalogRepository.getCategoryEffectiveSpec(TECH_PHONES))
        val rule = spec.requiredIfRules.firstOrNull { it.requiredAttributeCode == requiredAttributeCode }
        assertNotNull(rule)
        assertEquals(1, rule.whenAll.size)
        assertEquals(triggerAttributeCode, rule.whenAll.first().attributeCode)
        assertEquals(listOf("used"), rule.whenAll.first().values)
    }

    @Test
    fun track_extra_filters_support_non_hardcoded_attribute_keys() = runBlocking {
        val ownerId = createUser(
            displayName = "Extra Owner",
            city = "Moscow",
            countryCode = "RU",
        )
        val sellerId = createUser(
            displayName = "Extra Seller",
            city = "Moscow",
            countryCode = "RU",
            shippingCountries = listOf("RU"),
        )
        val productId = createProduct(
            categoryCode = TECH_PHONES,
            brand = "extra",
            model = "x1",
        )
        val matchedOfferId = createOffer(
            productId = productId,
            sellerId = sellerId,
            priceCents = 80_000L,
            condition = "used",
            deliveryChannel = "delivery",
            attributes = mapOf(
                "battery_health_percent" to TypedAttributeValue.Number(98.0),
            ),
        )
        createOffer(
            productId = productId,
            sellerId = sellerId,
            priceCents = 81_000L,
            condition = "used",
            deliveryChannel = "delivery",
            attributes = mapOf(
                "battery_health_percent" to TypedAttributeValue.Number(85.0),
            ),
        )

        val createResult = tracksRepository.create(
            userId = ownerId,
            request = TrackCreateRequest(
                type = TrackType.CATEGORY,
                target = TrackTarget(
                    spec = TrackTargetSpec(
                        categoryCode = TECH_PHONES,
                        attributes = mapOf("battery_health_percent" to "98"),
                    ),
                ),
                filters = TrackFilters(
                    extra = mapOf(
                        "battery_health_percent" to "98",
                    ),
                ),
                title = "Extra Non Hardcoded",
            ),
        )
        val created = assertIs<TrackCreateResult.Created>(createResult).track
        val page = tracksRepository.listOffers(
            userId = ownerId,
            trackId = created.id.toLong(),
            limit = 20,
            offset = 0,
            sort = TrackOfferSort.PRICE_ASC,
        )
        assertEquals(listOf(matchedOfferId.toString()), page.items.map { it.offerId })
    }

    private suspend fun cleanTables() = DatabaseFactory.dbQuery {
        TrackTop10SnapshotsTable.deleteAll()
        TrackEventsTable.deleteAll()
        TracksTable.deleteAll()

        CatalogPresetEventsTable.deleteAll()
        OfferPriceHistoryTable.deleteAll()
        OfferSourcesTable.deleteAll()
        OffersTable.deleteAll()
        ProductI18nTable.deleteAll()
        ProductsTable.deleteAll()
        UserReviewsTable.deleteAll()
        AlertsTable.deleteAll()
        UserPreferencesTable.deleteAll()
        SellerStatsTable.deleteAll()
        UserProfilesTable.deleteAll()
        AuthUsersTable.deleteAll()

        CatalogStage4ExecutionMetricsTable.deleteAll()

        FacetCollectionsTable.deleteWhere { FacetCollectionsTable.collectionCode like "FC.CONTRACT.%" }
        FacetPresetsTable.deleteWhere { FacetPresetsTable.presetCode like "FP.CONTRACT.%" }

        CatalogStage4TypedConstraintsTable.deleteWhere {
            CatalogStage4TypedConstraintsTable.attributeCode like "contract_%"
        }
        CategoryAttributesTable.deleteWhere {
            CategoryAttributesTable.attributeCode like "contract_%"
        }
        AttributeDefsTable.deleteWhere { AttributeDefsTable.code like "contract_%" }
    }

    private suspend fun createUser(
        displayName: String,
        city: String,
        countryCode: String,
        shippingCountries: List<String>? = listOf(countryCode),
        deliveryZones: List<SellerDeliveryZone>? = null,
        createPreferencesRow: Boolean = true,
    ): Long = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        val sequence = USER_SEQUENCE.incrementAndGet()
        val userId = AuthUsersTable.insert { stmt ->
            stmt[AuthUsersTable.email] = "contract-$sequence@example.com"
            stmt[AuthUsersTable.password] = "secret"
            stmt[AuthUsersTable.displayName] = displayName
            stmt[AuthUsersTable.city] = city
            stmt[AuthUsersTable.emailVerified] = true
            stmt[AuthUsersTable.createdAt] = now
        }.resultedValues!!.single()[AuthUsersTable.id]

        UserProfilesTable.insert { stmt ->
            stmt[UserProfilesTable.userId] = userId
            stmt[UserProfilesTable.displayName] = displayName
            stmt[UserProfilesTable.countryCode] = countryCode
            stmt[UserProfilesTable.city] = city
        }
        SellerStatsTable.insert { stmt ->
            stmt[SellerStatsTable.userId] = userId
            stmt[SellerStatsTable.ratingValue] = 4.6
            stmt[SellerStatsTable.ratingCount] = 10
        }
        if (createPreferencesRow) {
            UserPreferencesTable.insert { stmt ->
                stmt[UserPreferencesTable.userId] = userId
                stmt[UserPreferencesTable.badges] = emptyList()
                stmt[UserPreferencesTable.shippingCountries] = shippingCountries
                stmt[UserPreferencesTable.deliveryZones] = deliveryZones
                stmt[UserPreferencesTable.ratingValue] = 4.6
                stmt[UserPreferencesTable.ratingCount] = 10
            }
        }
        userId
    }

    private suspend fun createProduct(
        categoryCode: String,
        brand: String,
        model: String,
        specs: Map<String, TypedAttributeValue> = emptyMap(),
    ): Long = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        ProductsTable.insert { stmt ->
            stmt[ProductsTable.category] = categoryCode
            stmt[ProductsTable.brand] = brand
            stmt[ProductsTable.model] = model
            stmt[ProductsTable.titleNorm] = "$brand $model"
            stmt[ProductsTable.specs] = specs
            stmt[ProductsTable.updatedAt] = now
        }.resultedValues!!.single()[ProductsTable.id]
    }

    private suspend fun createOffer(
        productId: Long,
        sellerId: Long,
        priceCents: Long,
        condition: String,
        deliveryChannel: String,
        attributes: Map<String, TypedAttributeValue> = emptyMap(),
    ): Long = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        val offerAttributes = LinkedHashMap(attributes)
        offerAttributes.putIfAbsent("condition", TypedAttributeValue.Text(condition))
        OffersTable.insert { stmt ->
            stmt[OffersTable.productId] = productId
            stmt[OffersTable.userId] = sellerId
            stmt[OffersTable.priceCents] = priceCents
            stmt[OffersTable.currency] = "RUB"
            stmt[OffersTable.attributes] = offerAttributes
            stmt[OffersTable.condition] = condition
            stmt[OffersTable.deliveryChannel] = deliveryChannel
            stmt[OffersTable.status] = "ACTIVE"
            stmt[OffersTable.updatedAt] = now
        }.resultedValues!!.single()[OffersTable.id]
    }

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for server integration tests when strict mode is enabled.")
            }
            Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)
        }
    }

    private companion object {
        private const val TECH_PHONES = "TECH.PHONES"
        private val USER_SEQUENCE = AtomicLong(0)
        private var dockerAvailable: Boolean = false
        private val strictIntegration: Boolean by lazy {
            System.getenv("SERVER_IT_STRICT")?.equals("true", ignoreCase = true) == true ||
                System.getenv("CI")?.equals("true", ignoreCase = true) == true
        }
        private var container: PostgreSQLContainer<*>? = null

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            if (!dockerAvailable) {
                if (strictIntegration) {
                    error("Docker is required for server integration tests in strict mode.")
                }
                return
            }

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_catalog_tracks_contract_it")
                .withUsername("test")
                .withPassword("test")
            postgres.start()
            container = postgres

            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            ).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE EXTENSION IF NOT EXISTS postgis")
                }
            }

            DatabaseFactory.init(
                DatabaseConfig(
                    url = postgres.jdbcUrl,
                    user = postgres.username,
                    password = postgres.password,
                ),
            )
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            container?.stop()
            container = null
        }
    }
}

private object NoopTrackEventsRepository : TrackEventsRepository {
    override suspend fun listEventsPage(userId: Long, trackId: Long, limit: Int, offset: Int): TrackEventsPage =
        TrackEventsPage(
            items = emptyList(),
            limit = limit,
            offset = offset,
            canLoadMore = false,
        )

    override suspend fun markRead(userId: Long, eventId: Long): Boolean = false

    override suspend fun markAllRead(userId: Long, trackId: Long): Int = 0

    override suspend fun addEvent(
        userId: Long,
        trackId: Long,
        type: String,
        title: String,
        subtitle: String?,
        dedupKey: String,
    ) = Unit
}

private object ZeroScoringEngineContract : ScoringEngine {
    override fun score(dto: ProductDto, q: NormalizedQuery, avgPrice: Double): ScoreBreakdown =
        ScoreBreakdown(
            price = 0f,
            delivery = 0f,
            rating = 0f,
            penalties = 0f,
            score = 0f,
        )
}

