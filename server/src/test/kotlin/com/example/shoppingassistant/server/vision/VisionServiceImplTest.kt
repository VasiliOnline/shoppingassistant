package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryRedirectResolution
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.vision.VisionCategoryCandidate
import com.example.shoppingassistant.domain.vision.VisionConsumeRequest
import com.example.shoppingassistant.domain.vision.VisionNextAction
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult
import com.example.shoppingassistant.domain.vision.VisionPhotoInput
import com.example.shoppingassistant.domain.vision.VisionPhotoRole
import com.example.shoppingassistant.domain.vision.VisionSource
import com.example.shoppingassistant.domain.vision.VisionUsage
import com.example.shoppingassistant.domain.vision.VisionUsageRepository
import com.example.shoppingassistant.domain.vision.VisionUsageResult
import com.example.shoppingassistant.domain.vision.VisionUsageStatus
import com.example.shoppingassistant.server.config.VisionConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class VisionServiceImplTest {

    @Test
    fun normalize_photos_falls_back_without_fake_brand_or_model_when_ai_is_unavailable() {
        val service = VisionServiceImpl(
            catalogTaxonomyRepository = VisionTestTaxonomyRepository(),
            usageRepository = FakeVisionUsageRepository(),
            config = VisionConfig(),
            aiPhotoNormalizer = object : VisionAiPhotoNormalizer {
                override suspend fun normalize(request: VisionNormalizeRequest): VisionNormalizeResult? = null
            },
        )

        val result = runBlocking {
            service.normalizePhotos(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = "front"),
                        VisionPhotoInput(role = VisionPhotoRole.BACK, base64 = "back"),
                    ),
                    userKey = "user-1",
                    categoryHint = "TECH.PHONES",
                ),
            )
        }

        assertEquals("TECH.PHONES", result?.categoryCode)
        assertNull(result?.normalizedQuery)
        assertTrue(result?.warnings?.contains("AI_NORMALIZER_FALLBACK") == true)
        assertEquals(VisionNextAction.ADD_TECH_PHOTO, result?.nextAction)
    }

    @Test
    fun normalize_photos_uses_ai_result_when_available() {
        val aiResult = VisionNormalizeResult(
            normalizedQuery = NormalizedQuery(
                brand = "Apple",
                model = "iPhone 15",
            ),
            categoryCode = "TECH.PHONES",
            categoryCandidates = listOf(
                VisionCategoryCandidate(
                    code = "TECH.PHONES",
                    title = "Смартфоны",
                    score = 0.93f,
                ),
            ),
            title = "Apple iPhone 15",
            confidence = 0.93f,
            usedSources = setOf(VisionSource.VISUAL),
        )
        val service = VisionServiceImpl(
            catalogTaxonomyRepository = VisionTestTaxonomyRepository(),
            usageRepository = FakeVisionUsageRepository(),
            config = VisionConfig(),
            aiPhotoNormalizer = object : VisionAiPhotoNormalizer {
                override suspend fun normalize(request: VisionNormalizeRequest): VisionNormalizeResult = aiResult
            },
        )

        val result = runBlocking {
            service.normalizePhotos(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = "front"),
                        VisionPhotoInput(role = VisionPhotoRole.BACK, base64 = "back"),
                    ),
                    userKey = "user-1",
                ),
            )
        }

        assertEquals("Apple", result?.normalizedQuery?.brand)
        assertEquals("iPhone 15", result?.normalizedQuery?.model)
        assertEquals("TECH.PHONES", result?.categoryCode)
        assertTrue(result?.warnings?.contains("AI_NORMALIZER_FALLBACK") != true)
    }

    @Test
    fun normalize_photos_fallback_without_hint_does_not_assign_random_category() {
        val service = VisionServiceImpl(
            catalogTaxonomyRepository = VisionTestTaxonomyRepository(),
            usageRepository = FakeVisionUsageRepository(),
            config = VisionConfig(),
            aiPhotoNormalizer = object : VisionAiPhotoNormalizer {
                override suspend fun normalize(request: VisionNormalizeRequest): VisionNormalizeResult? = null
            },
        )

        val result = runBlocking {
            service.normalizePhotos(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = "front"),
                        VisionPhotoInput(role = VisionPhotoRole.BACK, base64 = "back"),
                    ),
                    userKey = "user-2",
                ),
            )
        }

        assertNull(result?.categoryCode)
        assertEquals(emptyList(), result?.categoryCandidates)
        assertEquals("CATEGORY_UNRESOLVED", result?.warnings?.lastOrNull())
    }
}

private class FakeVisionUsageRepository : VisionUsageRepository {
    override suspend fun getUsage(userKey: String?): VisionUsageResult =
        VisionUsageResult(
            status = VisionUsageStatus.OK,
            usage = VisionUsage(remainingToday = 10, remainingTotal = 100),
        )

    override suspend fun consume(request: VisionConsumeRequest): VisionUsageResult =
        VisionUsageResult(
            status = VisionUsageStatus.OK,
            usage = VisionUsage(remainingToday = 10, remainingTotal = 100),
        )
}

private class VisionTestTaxonomyRepository : CatalogTaxonomyRepository {
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
