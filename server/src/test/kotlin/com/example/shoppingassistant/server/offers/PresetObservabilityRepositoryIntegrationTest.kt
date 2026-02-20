package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityEvent
import com.example.shoppingassistant.domain.model.PresetObservabilityEventType
import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
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
import kotlin.test.assertTrue

class PresetObservabilityRepositoryIntegrationTest {

    @Before
    fun cleanTable() {
        runBlocking {
            requireDocker()
            DatabaseFactory.dbQuery {
                CatalogPresetEventsTable.deleteAll()
            }
        }
    }

    @Test
    fun ingestBatch_rejects_blank_facetPresetCode() = runBlocking {
        requireDocker()
        val repo = PresetObservabilityRepositoryImpl()

        val response = repo.ingestBatch(
            PresetObservabilityBatchRequest(
                events = listOf(
                    validEvent(
                        idempotencyKey = "imp|qs-invalid|offer-1|1",
                        facetPresetCode = "   ",
                    ),
                ),
            ),
        )

        assertEquals(0, response.acceptedCount)
        assertEquals(0, response.dedupedCount)
        assertEquals(1, response.rejectedCount)
        assertTrue(
            response.rejectedEventKeys.any { it.startsWith("FACET_PRESET_CODE_BLANK:") },
            "Expected FACET_PRESET_CODE_BLANK rejection key",
        )
    }

    @Test
    fun ingestBatch_rejects_unknown_facetCollectionCode_without_throwing() = runBlocking {
        requireDocker()
        val repo = PresetObservabilityRepositoryImpl()

        val response = repo.ingestBatch(
            PresetObservabilityBatchRequest(
                events = listOf(
                    validEvent(
                        idempotencyKey = "imp|qs-invalid-collection|offer-1|1",
                        facetCollectionCode = "B.UNKNOWN.COLLECTION",
                    ),
                ),
            ),
        )

        assertEquals(0, response.acceptedCount)
        assertEquals(0, response.dedupedCount)
        assertEquals(1, response.rejectedCount)
        assertTrue(
            response.rejectedEventKeys.any { it.startsWith("FACET_COLLECTION_CODE_UNKNOWN:") },
            "Expected FACET_COLLECTION_CODE_UNKNOWN rejection key",
        )
    }

    @Test
    fun ingestBatch_dedups_by_idempotency_across_batches() = runBlocking {
        requireDocker()
        val repo = PresetObservabilityRepositoryImpl()
        val event = validEvent(idempotencyKey = "imp|qs-dedup|offer-1|1")

        val first = repo.ingestBatch(PresetObservabilityBatchRequest(events = listOf(event)))
        val second = repo.ingestBatch(PresetObservabilityBatchRequest(events = listOf(event)))

        assertEquals(1, first.acceptedCount)
        assertEquals(0, first.dedupedCount)
        assertEquals(0, first.rejectedCount)

        assertEquals(0, second.acceptedCount)
        assertEquals(1, second.dedupedCount)
        assertEquals(0, second.rejectedCount)

        val stored = DatabaseFactory.dbQuery {
            CatalogPresetEventsTable
                .selectAll()
                .where { CatalogPresetEventsTable.idempotencyKey eq event.idempotencyKey }
                .count()
        }
        assertEquals(1, stored)
    }

    @Test
    fun ingestBatch_handles_mixed_accepted_deduped_and_rejected_events() = runBlocking {
        requireDocker()
        val repo = PresetObservabilityRepositoryImpl()
        val existingEvent = validEvent(idempotencyKey = "imp|qs-mixed|offer-1|1")
        val newEvent = validEvent(idempotencyKey = "imp|qs-mixed|offer-2|2")

        repo.ingestBatch(PresetObservabilityBatchRequest(events = listOf(existingEvent)))

        val response = repo.ingestBatch(
            PresetObservabilityBatchRequest(
                events = listOf(
                    existingEvent.copy(occurredAtMs = System.currentTimeMillis()),
                    newEvent,
                    validEvent(
                        idempotencyKey = "imp|qs-mixed|offer-3|3",
                        facetPresetCode = " ",
                    ),
                    newEvent.copy(occurredAtMs = System.currentTimeMillis()),
                ),
            ),
        )

        assertEquals(1, response.acceptedCount)
        assertEquals(2, response.dedupedCount)
        assertEquals(1, response.rejectedCount)
        assertTrue(
            response.rejectedEventKeys.any { it.startsWith("FACET_PRESET_CODE_BLANK:") },
            "Expected FACET_PRESET_CODE_BLANK rejection key",
        )
    }

    private fun validEvent(
        idempotencyKey: String,
        facetPresetCode: String = "FP.FOOD.READY.DEFAULT",
        facetCollectionCode: String? = "B.FOOD.READY",
    ): PresetObservabilityEvent = PresetObservabilityEvent(
        idempotencyKey = idempotencyKey,
        eventType = PresetObservabilityEventType.IMPRESSION,
        querySessionId = "qs-integration",
        categoryCode = "FOOD.READY_MEALS",
        facetCollectionCode = facetCollectionCode,
        facetPresetCode = facetPresetCode,
        offerId = "offer-1",
        position = 1,
        occurredAtMs = System.currentTimeMillis(),
        dataVersion = "2.2.5",
    )

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for server integration tests when strict mode is enabled.")
            }
            Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)
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
                    error("Docker is required for server integration tests in strict mode.")
                }
                return
            }

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_observability_it")
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

            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            ).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        INSERT INTO categories(code, segment, status, title)
                        VALUES ('FOOD.READY_MEALS', 'FOOD', 'ACTIVE', 'Ready meals')
                        ON CONFLICT (code) DO NOTHING
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        INSERT INTO facet_presets(preset_code, category_code, title_ru, order_index, rules)
                        VALUES ('FP.FOOD.READY.DEFAULT', 'FOOD.READY_MEALS', 'Default ready meals', 0, '[]'::jsonb)
                        ON CONFLICT (preset_code) DO NOTHING
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        INSERT INTO facet_collections(collection_code, category_code, title_ru, order_index, tags)
                        VALUES ('B.FOOD.READY', 'FOOD.READY_MEALS', 'Ready browse', 0, '[]'::jsonb)
                        ON CONFLICT (collection_code) DO NOTHING
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        CREATE TABLE IF NOT EXISTS catalog_preset_events (
                            id BIGSERIAL PRIMARY KEY,
                            idempotency_key VARCHAR(128) NOT NULL,
                            event_type VARCHAR(16) NOT NULL,
                            query_session_id VARCHAR(128) NOT NULL,
                            category_code VARCHAR(64) NOT NULL,
                            facet_collection_code VARCHAR(64) NULL,
                            facet_preset_code VARCHAR(64) NOT NULL,
                            offer_id VARCHAR(64) NULL,
                            position INT NULL,
                            occurred_at BIGINT NOT NULL,
                            received_at BIGINT NOT NULL,
                            event_date DATE NOT NULL,
                            data_version VARCHAR(32) NULL,
                            payload_json JSONB NOT NULL DEFAULT '{}'::jsonb
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_preset_events_event_date_key
                        ON catalog_preset_events (event_date, idempotency_key)
                        """.trimIndent(),
                    )
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            container?.stop()
            container = null
        }
    }
}
