package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogPhoneModelEnrichmentServiceIntegrationTest {

    private val repository = DatabaseCatalogPhoneModelEnrichmentRepository()
    private val service = CatalogPhoneModelEnrichmentServiceImpl(repository = repository)

    @Before
    fun setUp() {
        runBlocking {
            requireDocker()
            DatabaseFactory.dbQuery {
                CatalogPhoneModelEnrichmentCandidatesTable.deleteAll()
            }
        }
    }

    @Test
    fun repeated_runtime_usage_promotes_unknown_phone_model_to_ready_for_official_enrichment() = runBlocking {
        requireDocker()

        service.ingest(
            CatalogPhoneModelRuntimeSignal(
                categoryCode = "TECH.PHONES",
                brandRaw = "Honor",
                modelRaw = "500 Ultra",
                offerRef = "offer-1",
                sellerRef = "seller-1",
                confidence = 0.88,
                title = "Honor 500 Ultra 12/512",
            ),
        )
        service.ingest(
            CatalogPhoneModelRuntimeSignal(
                categoryCode = "TECH.PHONES",
                brandRaw = "Honor",
                modelRaw = "500 Ultra",
                offerRef = "offer-2",
                sellerRef = "seller-2",
                confidence = 0.91,
                title = "Honor 500 Ultra sealed",
            ),
        )
        service.ingest(
            CatalogPhoneModelRuntimeSignal(
                categoryCode = "TECH.PHONES",
                brandRaw = "Honor",
                modelRaw = "500 Ultra",
                offerRef = "offer-3",
                sellerRef = "seller-1",
                confidence = 0.93,
                title = "Honor 500 Ultra global",
            ),
        )

        val candidate = service.listCandidates(
            categoryCode = "TECH.PHONES",
            statuses = setOf(CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT),
            limit = 10,
        ).singleOrNull()

        assertNotNull(candidate)
        assertEquals(CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT, candidate.status)
        assertEquals("HONOR", candidate.brandCode)
        assertEquals("HONOR_OFFICIAL_PHONES", candidate.officialSourceCode)
        assertEquals(3, candidate.observedCount)
        assertEquals(2, candidate.distinctSellerCount)
        assertTrue(candidate.reasonCodes.isEmpty())
    }

    @Test
    fun officially_seeded_model_is_marked_as_enriched_in_runtime_queue() = runBlocking {
        requireDocker()

        service.ingest(
            CatalogPhoneModelRuntimeSignal(
                categoryCode = "TECH.PHONES",
                brandRaw = "Honor",
                modelRaw = "400",
                offerRef = "offer-covered-1",
                sellerRef = "seller-1",
                confidence = 0.95,
                title = "Honor 400 12/512",
            ),
        )

        val candidate = service.listCandidates(
            categoryCode = "TECH.PHONES",
            statuses = setOf(CatalogPhoneModelEnrichmentStatus.ENRICHED),
            limit = 10,
        ).singleOrNull()

        assertNotNull(candidate)
        assertEquals(CatalogPhoneModelEnrichmentStatus.ENRICHED, candidate.status)
        assertEquals("HONOR_400", candidate.canonicalModelCode)
        assertEquals("HONOR_OFFICIAL_PHONES", candidate.officialSourceCode)
        assertEquals("HONOR_400", candidate.officialEndpointCode)
        assertEquals(listOf("already_officially_covered"), candidate.reasonCodes)
    }

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for phone model enrichment integration tests in strict mode.")
            }
            Assume.assumeTrue("Docker is required for phone model enrichment integration tests.", dockerAvailable)
        }
    }

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
                    error("Docker is required for phone model enrichment integration tests in strict mode.")
                }
                return
            }

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_phone_model_enrichment_it")
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
