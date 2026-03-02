package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class CatalogRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var repository: InMemoryCatalogRepository

    @Before
    fun setUp() {
        repository = InMemoryCatalogRepository()
        startKoin {
            modules(
                module {
                    single<CatalogRepository> { repository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun categories_endpoint_returns_catalog_categories() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/categories")

        assertEquals(HttpStatusCode.OK, response.status)
        val categories = json.decodeFromString(
            deserializer = ListSerializer(Category.serializer()),
            string = response.bodyAsText(),
        )
        assertTrue(categories.isNotEmpty())
        assertTrue(categories.any { it.code == "TECH.PHONES" })
    }

    @Test
    fun profile_endpoint_returns_404_for_unknown_category() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/profiles/UNKNOWN.CATEGORY")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun constraints_endpoint_requires_category_code() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/constraints")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun constraints_endpoint_forwards_brand_and_model_filters() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get(
            "/api/catalog/constraints?categoryCode=TECH.PHONES&brand=apple&model=iphone%2016%20pro",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val request = repository.lastConstraintsRequest
        assertNotNull(request)
        assertEquals("TECH.PHONES", request?.categoryCode)
        assertEquals("apple", request?.brand)
        assertEquals("iphone 16 pro", request?.model)

        val constraints = json.decodeFromString(
            deserializer = ListSerializer(CatalogConstraints.serializer()),
            string = response.bodyAsText(),
        )
        assertTrue(constraints.any { it.scope == ConstraintScope.CATEGORY })
    }
}

private class InMemoryCatalogRepository : CatalogRepository {
    private val profiles = CatalogSeed.profiles
    private val constraints = CatalogSeed.constraints

    var lastConstraintsRequest: ConstraintsRequest? = null
        private set

    override suspend fun listCategories(): List<Category> = profiles.map { it.category }

    override suspend fun getCategoryProfile(categoryCode: String): CategoryProfile? {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return null
        return profiles.firstOrNull { profile ->
            profile.category.code.equals(normalized, ignoreCase = true)
        }
    }

    override suspend fun listAttributeValueDict(attributeCode: String): AttributeValueDict? {
        val normalized = attributeCode.trim()
        if (normalized.isBlank()) return null
        return profiles
            .asSequence()
            .flatMap { profile -> profile.valueDictionaries.asSequence() }
            .firstOrNull { dict -> dict.attributeCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun listConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        lastConstraintsRequest = ConstraintsRequest(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
        if (categoryCode.isBlank()) return emptyList()
        return constraints.filter { constraint ->
            when (constraint.scope) {
                ConstraintScope.GLOBAL -> true
                ConstraintScope.CATEGORY ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true
                ConstraintScope.BRAND ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand ?: "", ignoreCase = true) == true
                ConstraintScope.MODEL ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand ?: "", ignoreCase = true) == true &&
                        constraint.model?.equals(model ?: "", ignoreCase = true) == true
            }
        }
    }

    override suspend fun upsertProfile(profile: CategoryProfile) = Unit

    data class ConstraintsRequest(
        val categoryCode: String,
        val brand: String?,
        val model: String?,
    )
}
