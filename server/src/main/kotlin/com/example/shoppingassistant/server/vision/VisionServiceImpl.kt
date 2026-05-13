package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.vision.VisionBindOutcome
import com.example.shoppingassistant.domain.vision.VisionCategoryCandidate
import com.example.shoppingassistant.domain.vision.VisionConsumeRequest
import com.example.shoppingassistant.domain.vision.VisionNextAction
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult
import com.example.shoppingassistant.domain.vision.VisionPhotoInput
import com.example.shoppingassistant.domain.vision.VisionPhotoRole
import com.example.shoppingassistant.domain.vision.VisionSource
import com.example.shoppingassistant.domain.vision.VisionUsageMode
import com.example.shoppingassistant.domain.vision.VisionUsageRepository
import com.example.shoppingassistant.domain.vision.VisionUsageStatus
import com.example.shoppingassistant.server.config.VisionConfig
import org.slf4j.LoggerFactory

class VisionServiceImpl(
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val usageRepository: VisionUsageRepository,
    private val config: VisionConfig,
    private val aiPhotoNormalizer: VisionAiPhotoNormalizer? = null,
) : VisionService {

    private val logger = LoggerFactory.getLogger(VisionServiceImpl::class.java)

    override suspend fun normalizeImage(base64: String): NormalizedQuery? {
        if (base64.isBlank()) return null
        return aiPhotoNormalizer?.normalize(
            VisionNormalizeRequest(
                photos = listOf(
                    VisionPhotoInput(
                        role = VisionPhotoRole.FRONT,
                        base64 = base64,
                    ),
                ),
                userKey = "vision-single-image",
                usageConsumed = true,
            ),
        )?.normalizedQuery
    }

    override suspend fun normalizePhotos(request: VisionNormalizeRequest): VisionNormalizeResult? {
        if (request.photos.isEmpty()) return null

        val startedAt = System.currentTimeMillis()
        val techPhotos = request.photos.filter { photo -> photo.role in techRoles }
        val appearance = request.photos.filter { photo -> photo.role in appearanceRoles }

        logger.info(
            "vision.normalize.start userKey={} photos={} techPhotos={}",
            request.userKey,
            request.photos.size,
            techPhotos.size,
        )

        val effectiveParseFrontBackOnly = request.parseFrontBackOnly || techPhotos.isEmpty()

        if (appearance.size < 2) {
            logger.info("vision.normalize.fail reason=min_appearance userKey={}", request.userKey)
            return VisionNormalizeResult(
                errors = listOf("MIN_APPEARANCE_PHOTOS"),
                nextAction = VisionNextAction.ADD_BACK_PHOTO,
            )
        }

        if (effectiveParseFrontBackOnly) {
            val hasFront = appearance.any { photo -> photo.role == VisionPhotoRole.FRONT }
            val hasBack = appearance.any { photo -> photo.role == VisionPhotoRole.BACK }
            if (!hasFront || !hasBack) {
                logger.info("vision.normalize.fail reason=missing_front_back userKey={}", request.userKey)
                return VisionNormalizeResult(
                    errors = listOf("MISSING_FRONT_BACK"),
                    nextAction = VisionNextAction.ADD_BACK_PHOTO,
                )
            }
        }

        val usageMode = if (techPhotos.isNotEmpty()) VisionUsageMode.TECH_ONLY else VisionUsageMode.VISUAL
        val units = if (usageMode == VisionUsageMode.TECH_ONLY) config.techCost else config.visualCost
        if (!request.usageConsumed) {
            val usageResult = usageRepository.consume(
                VisionConsumeRequest(
                    userKey = request.userKey,
                    units = units,
                    mode = usageMode,
                ),
            )
            if (usageResult.status != VisionUsageStatus.OK) {
                logger.info("vision.normalize.fail reason=limit userKey={} status={}", request.userKey, usageResult.status)
                return VisionNormalizeResult(
                    errors = listOf("LIMIT_EXCEEDED"),
                )
            }
        }

        val aiResult = aiPhotoNormalizer?.normalize(
            request.copy(
                parseFrontBackOnly = effectiveParseFrontBackOnly,
                usageConsumed = true,
            ),
        )
        val fallback = buildDeterministicFallback(
            request = request.copy(parseFrontBackOnly = effectiveParseFrontBackOnly),
            techPhotos = techPhotos,
        )
        val result = aiResult?.mergeWithFallback(fallback) ?: fallback

        logger.info(
            "vision.normalize.success userKey={} elapsedMs={} sources={} missingKeys={} aiUsed={}",
            request.userKey,
            System.currentTimeMillis() - startedAt,
            result.usedSources.joinToString(","),
            result.missingRequiredKeys.size,
            aiResult != null,
        )

        return result
    }

    private suspend fun buildDeterministicFallback(
        request: VisionNormalizeRequest,
        techPhotos: List<VisionPhotoInput>,
    ): VisionNormalizeResult {
        val candidates = resolveCategoryCandidates(request.categoryHint)
        val categoryCode = candidates.firstOrNull()?.code
        val title = candidates.firstOrNull()?.title
        val nextAction = if (techPhotos.isEmpty()) {
            VisionNextAction.ADD_TECH_PHOTO
        } else {
            null
        }
        val usedSources = if (techPhotos.isNotEmpty()) {
            setOf(VisionSource.VISUAL, VisionSource.TECH_OCR)
        } else {
            setOf(VisionSource.VISUAL)
        }
        return VisionNormalizeResult(
            normalizedQuery = null,
            categoryCode = categoryCode,
            categoryCandidates = candidates,
            bindOutcome = VisionBindOutcome(
                rawCategoryHint = request.categoryHint?.trim()?.takeIf { it.isNotEmpty() },
                resolvedCategoryCode = categoryCode,
            ),
            title = title,
            missingRequiredKeys = emptyList(),
            confidence = if (categoryCode != null) 0.35f else null,
            usedSources = usedSources,
            warnings = buildList {
                add("AI_NORMALIZER_FALLBACK")
                if (categoryCode == null) add("CATEGORY_UNRESOLVED")
            },
            errors = emptyList(),
            nextAction = nextAction,
        )
    }

    private suspend fun resolveCategoryCandidates(categoryHint: String?): List<VisionCategoryCandidate> {
        val normalizedHint = categoryHint?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val categories = runCatching {
            catalogTaxonomyRepository.listCategories()
                .filter { category -> category.status == CategoryStatus.ACTIVE }
        }.getOrElse { emptyList() }
        if (categories.isEmpty()) return emptyList()

        val primary = categories.firstOrNull { category ->
            category.code.equals(normalizedHint, ignoreCase = true)
        } ?: return emptyList()
        val rest = categories.filterNot { category -> category.code == primary.code }
        val picked = buildList {
            add(primary)
            addAll(rest.take((3 - size).coerceAtLeast(0)))
        }

        return picked.mapIndexed { index, category ->
            VisionCategoryCandidate(
                code = category.code,
                title = category.displayTitle(),
                score = when (index) {
                    0 -> 0.72f
                    1 -> 0.58f
                    else -> 0.48f
                },
            )
        }
    }

    private fun VisionNormalizeResult.mergeWithFallback(
        fallback: VisionNormalizeResult,
    ): VisionNormalizeResult =
        copy(
            categoryCode = categoryCode ?: fallback.categoryCode,
            categoryCandidates = if (categoryCandidates.isNotEmpty()) categoryCandidates else fallback.categoryCandidates,
            bindOutcome = bindOutcome ?: fallback.bindOutcome,
            title = title ?: fallback.title,
            confidence = confidence ?: fallback.confidence,
            usedSources = if (usedSources.isNotEmpty()) usedSources else fallback.usedSources,
            warnings = (warnings + fallback.warnings.takeIf { categoryCode == null && categoryCandidates.isEmpty() }.orEmpty())
                .distinct(),
            nextAction = nextAction ?: fallback.nextAction,
        )

    private companion object {
        private val appearanceRoles = setOf(
            VisionPhotoRole.FRONT,
            VisionPhotoRole.BACK,
            VisionPhotoRole.LEFT,
            VisionPhotoRole.RIGHT,
            VisionPhotoRole.TOP,
            VisionPhotoRole.BOTTOM,
        )

        private val techRoles = setOf(
            VisionPhotoRole.TECH_1,
            VisionPhotoRole.TECH_2,
        )
    }
}
