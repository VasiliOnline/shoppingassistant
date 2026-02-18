package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogRepositoryImplIntegrationTest {

    @Test
    fun listConstraints_returnsSeededAndScopedRowsFromDatabase() = runBlocking {
        Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)

        val seededCount = DatabaseFactory.dbQuery { CatalogConstraintsTable.selectAll().count() }
        assertTrue(seededCount > 0, "Expected seeded constraints in DB")

        val repository = CatalogRepositoryImpl()

        val categoryOnly = repository.listConstraints(
            categoryCode = "TECH.PHONES",
            brand = null,
            model = null,
        )
        assertEquals(listOf(ConstraintScope.CATEGORY), categoryOnly.map { it.scope })

        val withModel = repository.listConstraints(
            categoryCode = "TECH.PHONES",
            brand = "apple",
            model = "iphone 16 pro",
        )
        assertEquals(
            listOf(ConstraintScope.CATEGORY, ConstraintScope.MODEL),
            withModel.map { it.scope },
        )

        val unmatchedBrandModel = repository.listConstraints(
            categoryCode = "TECH.PHONES",
            brand = "Samsung",
            model = "Galaxy S24",
        )
        assertEquals(listOf(ConstraintScope.CATEGORY), unmatchedBrandModel.map { it.scope })
    }

    @Test
    fun listConstraints_returnsEmptyForUnknownCategory() = runBlocking {
        Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)

        val repository = CatalogRepositoryImpl()
        val result = repository.listConstraints(
            categoryCode = "UNKNOWN.CATEGORY",
            brand = "Apple",
            model = "iPhone 16 Pro",
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun facetSchemaRepositories_returnSeededStage3Rows() = runBlocking {
        Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)

        val repository = FacetSchemaRepositoryImpl()

        val definitions = repository.listFacetDefinitions()
        val presets = repository.listFacetPresets()
        val collections = repository.listFacetCollections()

        assertTrue(definitions.isNotEmpty(), "Expected seeded facet definitions")
        assertTrue(presets.isNotEmpty(), "Expected seeded facet presets")
        assertTrue(collections.isNotEmpty(), "Expected seeded facet collections")

        val pizzaCollection = repository.getFacetCollectionByBrowseCode("B.FOOD.READY.05")
        assertEquals("FP.FOOD.READY.PIZZA", pizzaCollection?.presetCode)
    }

    private companion object {
        private var dockerAvailable: Boolean = false
        private var container: PostgreSQLContainer<*>? = null

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            if (!dockerAvailable) return

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_test")
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
