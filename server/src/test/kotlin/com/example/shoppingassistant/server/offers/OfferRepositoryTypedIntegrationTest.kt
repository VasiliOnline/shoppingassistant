package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ScoreBreakdown
import com.example.shoppingassistant.core.rank.ScoringEngine
import com.example.shoppingassistant.domain.catalog.Stage40DedupEntity
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.TypedAttributeFilter
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.catalog.Stage4IngestNormalizationOutcome
import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OfferRepositoryTypedIntegrationTest {

    @Before
    fun cleanTables() {
        runBlocking {
            requireDocker()
            DatabaseFactory.dbQuery {
                OffersTable.deleteAll()
                ProductI18nTable.deleteAll()
                ProductsTable.deleteAll()
                UserReviewsTable.deleteAll()
                AlertsTable.deleteAll()
                OfferPriceHistoryTable.deleteAll()
                OfferSourcesTable.deleteAll()
                UserPreferencesTable.deleteAll()
                SellerStatsTable.deleteAll()
                UserProfilesTable.deleteAll()
                AuthUsersTable.deleteAll()
            }
        }
    }

    @Test
    fun searchOffers_filters_by_typed_specs_and_preserves_typed_values() = runBlocking {
        requireDocker()
        val seed = seedOffers()
        val repository = OfferRepositoryImpl(
            rankService = RankService(engine = ZeroScoringEngine),
            stage4ExecutionLayer = IdentityStage4ExecutionLayer(),
        )

        val result = repository.searchOffers(
            OfferSearchCriteria(
                brand = null,
                model = null,
                attributes = mapOf(
                    "ram_gb" to TypedAttributeValue.Number(8.0),
                    "wireless" to TypedAttributeValue.Bool(true),
                ),
                sort = OfferSort.PRICE_ASC,
                limit = 10,
            ),
        )

        assertEquals(1, result.size, "Typed filters should match exactly one offer")
        val only = result.single()
        assertEquals(seed.matchingOfferId.toString(), only.id)
        assertEquals("RUB", only.currency)

        val ramSpec = only.product.specs["ram_gb"]
        val wirelessSpec = only.product.specs["wireless"]
        assertIs<TypedAttributeValue.Number>(ramSpec)
        assertIs<TypedAttributeValue.Bool>(wirelessSpec)
        assertEquals(8.0, ramSpec.value)
        assertTrue(wirelessSpec.value)

        // В оффере намеренно нет ram_gb/wireless; матч должен идти по products.specs.
        assertTrue("ram_gb" !in only.attributes)
        assertTrue("wireless" !in only.attributes)
        assertEquals(TypedAttributeValue.Text("new"), only.attributes["condition"])

        val ranged = repository.searchOffers(
            OfferSearchCriteria(
                brand = null,
                model = null,
                attributeFilters = mapOf(
                    "ram_gb" to TypedAttributeFilter(
                        op = TypedAttributeOperator.GTE,
                        value = TypedAttributeValue.Number(7.0),
                    ),
                ),
                sort = OfferSort.PRICE_ASC,
                limit = 10,
            ),
        )
        assertEquals(1, ranged.size, "Range typed filter should match only the 8GB offer")
        assertEquals(seed.matchingOfferId.toString(), ranged.single().id)
    }

    private suspend fun seedOffers(): SeedResult = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        val sellerId = AuthUsersTable.insert {
            it[email] = "typed-it-seller@example.com"
            it[password] = "secret"
            it[displayName] = "Typed Seller"
            it[city] = "Moscow"
            it[emailVerified] = true
            it[createdAt] = now
        }.resultedValues!!.single()[AuthUsersTable.id]

        val matchingProductId = ProductsTable.insert {
            it[category] = "TECH.PHONES"
            it[brand] = "Acme"
            it[model] = "X1"
            it[titleNorm] = "Acme X1"
            it[specs] = mapOf(
                "ram_gb" to TypedAttributeValue.Number(8.0),
                "wireless" to TypedAttributeValue.Bool(true),
                "color" to TypedAttributeValue.Text("black"),
            )
            it[updatedAt] = now
        }.resultedValues!!.single()[ProductsTable.id]

        val nonMatchingProductId = ProductsTable.insert {
            it[category] = "TECH.PHONES"
            it[brand] = "Acme"
            it[model] = "X2"
            it[titleNorm] = "Acme X2"
            it[specs] = mapOf(
                "ram_gb" to TypedAttributeValue.Number(6.0),
                "wireless" to TypedAttributeValue.Bool(false),
                "color" to TypedAttributeValue.Text("white"),
            )
            it[updatedAt] = now
        }.resultedValues!!.single()[ProductsTable.id]

        val matchingOfferId = OffersTable.insert {
            it[productId] = matchingProductId
            it[userId] = sellerId
            it[priceCents] = 100_000
            it[currency] = "RUB"
            it[attributes] = mapOf(
                "condition" to TypedAttributeValue.Text("new"),
                "seller_note" to TypedAttributeValue.Text("box-opened"),
            )
            it[condition] = "new"
            it[deliveryChannel] = "pickup"
            it[status] = "ACTIVE"
            it[updatedAt] = now
        }.resultedValues!!.single()[OffersTable.id]

        OffersTable.insert {
            it[productId] = nonMatchingProductId
            it[userId] = sellerId
            it[priceCents] = 90_000
            it[currency] = "RUB"
            it[attributes] = mapOf(
                "condition" to TypedAttributeValue.Text("new"),
            )
            it[condition] = "new"
            it[deliveryChannel] = "pickup"
            it[status] = "ACTIVE"
            it[updatedAt] = now
        }

        SeedResult(matchingOfferId = matchingOfferId)
    }

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for server integration tests when strict mode is enabled.")
            }
            Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)
        }
    }

    private data class SeedResult(
        val matchingOfferId: Long,
    )

    private companion object {
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
                .withDatabaseName("shoppingassistant_offer_typed_search_it")
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

private object ZeroScoringEngine : ScoringEngine {
    override fun score(dto: ProductDto, q: NormalizedQuery, avgPrice: Double): ScoreBreakdown =
        ScoreBreakdown(
            price = 0f,
            delivery = 0f,
            rating = 0f,
            penalties = 0f,
            score = 0f,
        )
}

private class IdentityStage4ExecutionLayer : Stage4ExecutionLayer {
    override fun normalizeAttributesForIngest(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String> = normalize(attributes)

    override fun normalizeAttributesForIngestStrict(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Stage4IngestNormalizationOutcome {
        val normalized = normalize(attributes)
        return Stage4IngestNormalizationOutcome(
            normalizedAttributes = normalized,
            normalizedCount = normalized.size,
            droppedCount = attributes.size - normalized.size,
            logicalDedupCount = 0,
            unknownAttributeCount = 0,
            reasonCodes = emptyList(),
        )
    }

    override fun normalizeAttributesForSearch(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String> = normalize(attributes)

    override fun normalizeValueForSearch(attributeCode: String, value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    override fun normalizeCatalogCode(value: String?): String? =
        value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    override fun toTypedAttributes(
        attributes: Map<String, String>,
    ): Map<String, TypedAttributeValue> = attributes.mapValues { (_, value) ->
        TypedAttributeValue.fromRawString(value)
    }

    override fun toRawStringAttributes(
        attributes: Map<String, TypedAttributeValue>,
    ): Map<String, String> = attributes.mapValues { (_, value) -> value.asRawString() }

    override fun renderDedupKey(
        entity: Stage40DedupEntity,
        fields: Map<String, String>,
        fallback: String,
    ): String = fallback

    private fun normalize(
        attributes: Map<String, String>,
    ): Map<String, String> = attributes
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim().lowercase(Locale.ROOT)
            val normalizedValue = value.trim()
            if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap(LinkedHashMap())
}
