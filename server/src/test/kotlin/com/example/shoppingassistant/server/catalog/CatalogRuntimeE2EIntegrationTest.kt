package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.di.backendCatalogModule
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.deleteAll
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogRuntimeE2EIntegrationTest {

    @Before
    fun setUp() {
        requireDocker()
        runBlocking {
            DatabaseFactory.dbQuery {
                CatalogReadinessSnapshotsTable.deleteAll()
                CatalogGovernanceReportsTable.deleteAll()
                CatalogReadinessAutomationLeasesTable.deleteAll()
            }
        }
        startKoin {
            modules(backendCatalogModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun catalog_runtime_flow_persists_snapshot_and_exposes_history_reports_and_openapi() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val runner = getKoin().get<CatalogReadinessSnapshotRunner>()
        val runResult = runner.runIfDue(LocalDateTime(2026, 3, 9, 9, 31))

        assertTrue(runResult.captured)
        assertTrue(runResult.weeklySummaryCaptured)

        val historyResponse = client.get("/api/catalog/readiness/TECH.PHONES/history?days=7")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        val historyPayload = historyResponse.bodyAsText()
        assertTrue(historyPayload.contains("\"windowEndDate\":\"2026-03-09\""))

        val reportsResponse = client.get("/api/catalog/readiness/governance/reports?limit=5")
        assertEquals(HttpStatusCode.OK, reportsResponse.status)
        val reportsPayload = reportsResponse.bodyAsText()
        assertTrue(reportsPayload.contains("\"reportType\":\"WEEKLY_SUMMARY\""))
        assertTrue(reportsPayload.contains("\"reportDate\":\"2026-03-09\""))

        val openApiResponse = client.get("/api/catalog/openapi.json")
        assertEquals(HttpStatusCode.OK, openApiResponse.status)
        val openApiPayload = openApiResponse.bodyAsText()
        assertTrue(openApiPayload.contains("\"/api/catalog/effective-spec/{categoryCode}\""))
        assertTrue(!openApiPayload.contains("\"/api/catalog/constraints\""))
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
                .withDatabaseName("shoppingassistant_catalog_e2e")
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
