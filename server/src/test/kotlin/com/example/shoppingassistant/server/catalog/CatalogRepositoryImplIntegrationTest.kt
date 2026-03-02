package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.AttributeDef
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryAttribute
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.Stage22ValueSetType
import com.example.shoppingassistant.domain.catalog.Stage22ValueType
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfCondition
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogRepositoryImplIntegrationTest {

    @Test
    fun listConstraints_returnsSeededAndScopedRowsFromDatabase() = runBlocking {
        requireDocker()

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
        requireDocker()

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
        requireDocker()

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

    @Test
    fun listCategories_returns_status_from_database() = runBlocking {
        requireDocker()

        val repository = CatalogRepositoryImpl()
        val categories = repository.listCategories()

        assertTrue(categories.isNotEmpty(), "Expected seeded categories")
        assertTrue(categories.all { it.status == CategoryStatus.ACTIVE })
    }

    @Test
    fun upsertProfile_roundTrip_persistsRequiredIfRules_andPreservesOtherCategoryRules() = runBlocking {
        requireDocker()

        val repository = CatalogRepositoryImpl()
        val categoryCode = "TECH.TEST.UPSERT.REQUIRED_IF"
        val triggerAttributeCode = "condition_test_required_if"
        val requiredAttributeCode = "model_number_test_required_if"
        val staleAttributeCode = "legacy_required_if_attr_test"
        val otherCategoryCode = "TECH.OTHER.REQUIRED_IF"

        DatabaseFactory.dbQuery {
            CategoryAttributesTable.deleteWhere { CategoryAttributesTable.categoryCode eq categoryCode }
            CategoriesTable.deleteWhere { CategoriesTable.code eq categoryCode }

            val stage4AttributeCodes = listOf(
                triggerAttributeCode,
                requiredAttributeCode,
                staleAttributeCode,
                "other_attr_for_required_if_upsert",
            )
            CatalogStage4TypedConstraintsTable.deleteWhere {
                CatalogStage4TypedConstraintsTable.attributeCode inList stage4AttributeCodes
            }
            CatalogStage4ImmutableAttributesTable.deleteWhere {
                CatalogStage4ImmutableAttributesTable.attributeCode inList stage4AttributeCodes
            }

            stage4AttributeCodes.forEach { attributeCode ->
                CatalogStage4ImmutableAttributesTable.insert { stmt ->
                    stmt[CatalogStage4ImmutableAttributesTable.attributeCode] = attributeCode
                    stmt[CatalogStage4ImmutableAttributesTable.valueType] = Stage22ValueType.STRING.name
                    stmt[CatalogStage4ImmutableAttributesTable.valueSetType] = Stage22ValueSetType.OPEN.name
                    stmt[CatalogStage4ImmutableAttributesTable.unit] = null
                    stmt[CatalogStage4ImmutableAttributesTable.isIdentity] = false
                    stmt[CatalogStage4ImmutableAttributesTable.isFacet] = false
                    stmt[CatalogStage4ImmutableAttributesTable.normalization] = null
                    stmt[CatalogStage4ImmutableAttributesTable.dictionaryRequired] = false
                    stmt[CatalogStage4ImmutableAttributesTable.immutableFingerprint] = "test-$attributeCode"
                }
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
                        categoryCode = categoryCode,
                        whenAll = listOf(
                            Stage40RequiredIfCondition(
                                attributeCode = triggerAttributeCode,
                                values = listOf("old"),
                            ),
                        ),
                    ),
                    Stage40RequiredIfRule(
                        categoryCode = otherCategoryCode,
                        whenAll = listOf(
                            Stage40RequiredIfCondition(
                                attributeCode = "other_attr_for_required_if_upsert",
                                values = listOf("x"),
                            ),
                        ),
                    ),
                )
                stmt[CatalogStage4TypedConstraintsTable.updatedAt] = System.currentTimeMillis()
            }

            CatalogStage4TypedConstraintsTable.insert { stmt ->
                stmt[CatalogStage4TypedConstraintsTable.attributeCode] = staleAttributeCode
                stmt[CatalogStage4TypedConstraintsTable.valueType] = Stage22ValueType.STRING.name
                stmt[CatalogStage4TypedConstraintsTable.enumOnly] = false
                stmt[CatalogStage4TypedConstraintsTable.expectedUnit] = null
                stmt[CatalogStage4TypedConstraintsTable.regexPattern] = null
                stmt[CatalogStage4TypedConstraintsTable.minValue] = null
                stmt[CatalogStage4TypedConstraintsTable.maxValue] = null
                stmt[CatalogStage4TypedConstraintsTable.requiredIf] = listOf(
                    Stage40RequiredIfRule(
                        categoryCode = categoryCode,
                        whenAll = listOf(
                            Stage40RequiredIfCondition(
                                attributeCode = triggerAttributeCode,
                                values = listOf("legacy"),
                            ),
                        ),
                    ),
                )
                stmt[CatalogStage4TypedConstraintsTable.updatedAt] = System.currentTimeMillis()
            }
        }

        repository.upsertProfile(
            CategoryProfile(
                category = Category(
                    code = categoryCode,
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.ACTIVE,
                    title = "RequiredIf Upsert Test",
                ),
                attributes = listOf(
                    AttributeDef(
                        code = triggerAttributeCode,
                        title = "Trigger",
                        dataType = AttributeDataType.STRING,
                    ),
                    AttributeDef(
                        code = requiredAttributeCode,
                        title = "Required",
                        dataType = AttributeDataType.STRING,
                    ),
                ),
                categoryAttributes = listOf(
                    CategoryAttribute(
                        categoryCode = categoryCode,
                        attributeCode = triggerAttributeCode,
                        uiOrder = 1,
                    ),
                    CategoryAttribute(
                        categoryCode = categoryCode,
                        attributeCode = requiredAttributeCode,
                        uiOrder = 2,
                    ),
                ),
                requiredIfRules = listOf(
                    RequiredIfRule(
                        requiredAttributeCode = requiredAttributeCode,
                        whenAll = listOf(
                            AttributeCondition(
                                attributeCode = triggerAttributeCode,
                                values = listOf("used"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val storedProfile = repository.getCategoryProfile(categoryCode)
        assertNotNull(storedProfile)
        val persistedRule = storedProfile.requiredIfRules.singleOrNull {
            it.requiredAttributeCode == requiredAttributeCode
        }
        assertNotNull(persistedRule)
        assertEquals(triggerAttributeCode, persistedRule.whenAll.single().attributeCode)
        assertEquals(listOf("used"), persistedRule.whenAll.single().values)

        DatabaseFactory.dbQuery {
            val requiredRules = CatalogStage4TypedConstraintsTable
                .selectAll()
                .toList()
                .single { row -> row[CatalogStage4TypedConstraintsTable.attributeCode] == requiredAttributeCode }
                .get(CatalogStage4TypedConstraintsTable.requiredIf)
            assertTrue(
                requiredRules.any { rule -> rule.categoryCode == otherCategoryCode },
                "required_if rules from other categories must be preserved",
            )
            assertTrue(
                requiredRules.any { rule ->
                    rule.categoryCode == categoryCode &&
                        rule.whenAll.any { condition ->
                            condition.attributeCode == triggerAttributeCode && condition.values == listOf("used")
                        }
                },
                "new category rules must be written",
            )
            assertTrue(
                requiredRules.none { rule ->
                    rule.categoryCode == categoryCode &&
                        rule.whenAll.any { condition -> condition.values == listOf("old") }
                },
                "old rules for the same category must be replaced",
            )

            val staleRules = CatalogStage4TypedConstraintsTable
                .selectAll()
                .toList()
                .single { row -> row[CatalogStage4TypedConstraintsTable.attributeCode] == staleAttributeCode }
                .get(CatalogStage4TypedConstraintsTable.requiredIf)
            assertTrue(
                staleRules.none { rule -> rule.categoryCode == categoryCode },
                "rules removed from profile must be removed from stage4 typed constraints",
            )
        }
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
