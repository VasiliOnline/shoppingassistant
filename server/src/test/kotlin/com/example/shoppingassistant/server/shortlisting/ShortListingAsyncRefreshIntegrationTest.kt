package com.example.shoppingassistant.server.shortlisting

import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryRedirectResolution
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingIncomingPhoto
import com.example.shoppingassistant.domain.shortlisting.ShortListingPhotoRole
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionRuntimeFlags
import com.example.shoppingassistant.domain.vision.VisionCategoryCandidate
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.UserProfilesTable
import com.example.shoppingassistant.server.storage.PhotoStorageService
import com.example.shoppingassistant.server.vision.VisionService
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShortListingAsyncRefreshIntegrationTest {

    @Before
    fun cleanTables() {
        requireDocker()
        runBlocking {
            DatabaseFactory.dbQuery {
                ShortListingDraftsTable.deleteAll()
                OffersTable.deleteAll()
                ProductsTable.deleteAll()
                UserProfilesTable.deleteAll()
                AuthUsersTable.deleteAll()
            }
        }
    }

    @Test
    fun create_draft_returns_pending_and_background_refresh_populates_predicted_fields() = runBlocking {
        requireDocker()
        val userId = seedUser("async-refresh")
        val visionService = DelayedVisionService(
            result = VisionNormalizeResult(
                normalizedQuery = NormalizedQuery(
                    brand = "Apple",
                    model = "iPhone 15 Pro",
                    attributes = mapOf("color" to TypedAttributeValue.Text("black")),
                ),
                categoryCode = "TECH.PHONES",
                rawExtraction = com.example.shoppingassistant.domain.vision.VisionRawExtraction(
                    categoryHint = "смартфоны",
                    brand = "Apple",
                    model = "iPhone 15 Pro",
                    title = "Apple iPhone 15 Pro",
                    reasonCodes = listOf("VISIBLE_BRAND", "VISIBLE_MODEL"),
                    attributes = listOf(
                        com.example.shoppingassistant.domain.vision.VisionAttributeCandidate(
                            code = "condition",
                            text = "used",
                            confidence = 0.88f,
                        ),
                    ),
                ),
                bindOutcome = com.example.shoppingassistant.domain.vision.VisionBindOutcome(
                    rawCategoryHint = "смартфоны",
                    resolvedCategoryCode = "TECH.PHONES",
                    acceptedAttributeCodes = listOf("brand", "model"),
                    unresolvedAttributeCodes = listOf("condition"),
                    missingRequiredKeys = emptyList(),
                ),
                categoryCandidates = listOf(
                    VisionCategoryCandidate(
                        code = "TECH.PHONES",
                        title = "Смартфоны",
                        score = 0.96f,
                    ),
                ),
            ),
        )
        val service = ShortListingBackendServiceImpl(
            catalogReadRepository = NoopCatalogReadRepository(),
            catalogTaxonomyRepository = FixedTaxonomyRepository(),
            stage4ExecutionLayer = PassthroughStage4ExecutionLayer(),
            visionService = visionService,
            photoStorageService = TempPhotoStorageService(),
        )

        val created = service.createDraft(
            userId = userId,
            request = ShortListingCreateDraftRequest(
                photos = listOf(
                    ShortListingIncomingPhoto(
                        role = ShortListingPhotoRole.FRONT,
                        filename = "front.jpg",
                        contentType = "image/jpeg",
                        dataBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)),
                    ),
                ),
                requestedCategoryCode = "TECH.PHONES",
            ),
        )

        assertTrue(created.draft.aiRefreshPending)
        assertEquals(emptyMap(), created.draft.predictedFields)
        assertTrue(
            created.visionResult?.warnings?.contains(ShortListingVisionRuntimeFlags.AI_REFRESH_PENDING) == true,
        )

        val refreshed = awaitDraftReady(
            service = service,
            userId = userId,
            draftId = created.draft.draftId,
        )

        assertEquals(1, visionService.normalizeCalls)
        assertEquals(false, refreshed.draft.aiRefreshPending)
        assertEquals(created.draft.revision + 1, refreshed.draft.revision)
        assertEquals("Apple", refreshed.draft.predictedFields["brand"]?.normalizedValue)
        assertEquals("iPhone 15 Pro", refreshed.draft.predictedFields["model"]?.normalizedValue)
        assertEquals("смартфоны", refreshed.visionResult?.rawExtraction?.categoryHint)
        assertEquals("Apple", refreshed.visionResult?.rawExtraction?.brand)
        assertEquals(listOf("condition"), refreshed.visionResult?.bindOutcome?.unresolvedAttributeCodes)
        assertTrue(
            refreshed.visionResult?.warnings?.contains(ShortListingVisionRuntimeFlags.AI_REFRESH_PENDING) != true,
        )
    }

    private suspend fun awaitDraftReady(
        service: ShortListingBackendService,
        userId: Long,
        draftId: String,
    ): com.example.shoppingassistant.domain.shortlisting.ShortListingDraftEnvelope {
        repeat(40) {
            val current = service.getDraft(userId, draftId)
            if (current != null && !current.draft.aiRefreshPending) {
                return current
            }
            delay(100)
        }
        error("Timed out waiting for async shortlisting AI refresh.")
    }

    private suspend fun seedUser(tag: String): Long = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        AuthUsersTable.insert {
            it[email] = "shortlisting-$tag@example.com"
            it[password] = "secret"
            it[displayName] = "ShortListing $tag"
            it[city] = "Moscow"
            it[emailVerified] = true
            it[createdAt] = now
        }.resultedValues!!.single()[AuthUsersTable.id]
    }

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for shortlisting integration tests in strict mode.")
            }
            Assume.assumeTrue("Docker is required for shortlisting integration tests.", dockerAvailable)
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
                    error("Docker is required for shortlisting integration tests in strict mode.")
                }
                return
            }

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_shortlisting_async_it")
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

            val database = Database.connect(
                url = postgres.jdbcUrl,
                driver = postgres.driverClassName,
                user = postgres.username,
                password = postgres.password,
            )
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ
            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(
                    AuthUsersTable,
                    UserProfilesTable,
                    ProductsTable,
                    OffersTable,
                    ShortListingDraftsTable,
                )
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

private class DelayedVisionService(
    private val result: VisionNormalizeResult,
) : VisionService {
    var normalizeCalls: Int = 0
        private set

    override suspend fun normalizeImage(base64: String): NormalizedQuery? = null

    override suspend fun normalizePhotos(request: VisionNormalizeRequest): VisionNormalizeResult {
        normalizeCalls += 1
        delay(250)
        return result
    }
}

private class TempPhotoStorageService : PhotoStorageService {
    private val root = Files.createTempDirectory("shortlisting-async-photos")

    override suspend fun save(bytes: ByteArray, filename: String, contentType: String?): String {
        val safeName = filename.ifBlank { "photo.bin" }
        val path = Files.createTempFile(root, "upload-", "-$safeName")
        Files.write(path, bytes)
        return path.toString()
    }
}

private class NoopCatalogReadRepository : CatalogReadRepository {
    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? = null
}

private class FixedTaxonomyRepository : CatalogTaxonomyRepository {
    private val categories = listOf(
        Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Смартфоны"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
    )

    override suspend fun listCategories(): List<Category> = categories

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ): CategoryRedirectResolution? =
        categories.firstOrNull { category -> category.code.equals(categoryCode, ignoreCase = true) }?.let { category ->
            CategoryRedirectResolution(
                requestedCode = categoryCode,
                resolvedCode = category.code,
                redirectChain = listOf(category.code),
                wasRedirected = false,
                cycleDetected = false,
            )
        }
}

private class PassthroughStage4ExecutionLayer : Stage4ExecutionLayer {
    override fun normalizeAttributesForIngest(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String> = attributes

    override fun normalizeAttributesForIngestStrict(
        categoryCode: String?,
        attributes: Map<String, String>,
    ) = com.example.shoppingassistant.server.catalog.Stage4IngestNormalizationOutcome(
        normalizedAttributes = attributes,
        normalizedCount = attributes.size,
        droppedCount = 0,
        logicalDedupCount = 0,
        unknownAttributeCount = 0,
        reasonCodes = emptyList(),
    )

    override fun normalizeAttributesForSearch(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String> = attributes

    override fun normalizeValueForSearch(
        attributeCode: String,
        value: String?,
    ): String? = value?.trim()

    override fun normalizeCatalogCode(
        value: String?,
    ): String? = value?.trim()?.uppercase()

    override fun toTypedAttributes(
        attributes: Map<String, String>,
    ): Map<String, TypedAttributeValue> =
        attributes.mapValues { (_, value) -> TypedAttributeValue.Text(value) }

    override fun toRawStringAttributes(
        attributes: Map<String, TypedAttributeValue>,
    ): Map<String, String> =
        attributes.mapValues { (_, value) -> value.asRawString() }

    override fun renderDedupKey(
        entity: com.example.shoppingassistant.domain.catalog.Stage40DedupEntity,
        fields: Map<String, String>,
        fallback: String,
    ): String = fallback
}
