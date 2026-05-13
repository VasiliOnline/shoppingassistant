package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferResult
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferStatus
import com.example.shoppingassistant.domain.offers.TrackedOfferInput
import com.example.shoppingassistant.domain.offers.TrackedOfferRepository
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferInput
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferResult
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferStatus
import com.example.shoppingassistant.domain.offers.resolveCategoryCode
import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.UrlNormalizer
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentService
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelRuntimeSignal
import com.example.shoppingassistant.server.catalog.NoopCatalogPhoneModelEnrichmentService
import com.example.shoppingassistant.server.catalog.Stage4ExecutionMetricSample
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepository
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepositoryImpl
import com.example.shoppingassistant.server.catalog.Stage4ExecutionStream
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.andWhere
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Репозиторий создания отслеживаемых офферов в Postgres (Exposed).
 * Уникальность гарантируется по паре (userId, sourceUrl) через OfferSourcesTable.
 */
class TrackedOfferRepositoryImpl(
    private val urlNormalizer: UrlNormalizer,
    private val sourceRegistry: SourceRegistry,
    private val stage4ExecutionLayer: Stage4ExecutionLayer,
    private val stage4ExecutionObservabilityRepository: Stage4ExecutionObservabilityRepository =
        Stage4ExecutionObservabilityRepositoryImpl(),
    private val phoneModelEnrichmentService: CatalogPhoneModelEnrichmentService =
        NoopCatalogPhoneModelEnrichmentService,
    private val requiredForCategoryHardFail: Boolean = resolveRequiredForCategoryHardFail(),
    private val categoryConfidenceHardFail: Boolean = resolveCategoryConfidenceHardFail(),
    private val minCategoryConfidence: Double = resolveMinCategoryConfidence(),
) : TrackedOfferRepository {

    override suspend fun createTrackedOffer(request: TrackedOfferInput): CreateTrackedOfferResult {
        val outcome = DatabaseFactory.dbQuery {
            val categoryCode = stage4ExecutionLayer.normalizeCatalogCode(request.resolveCategoryCode())
            validate(request, categoryCode)?.let { issue ->
                return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = issue.message,
                        reasonCodes = listOf(issue.code),
                    ),
                )
            }
            val normalizedCategoryCode = categoryCode
                ?: return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "categoryCode is required",
                        reasonCodes = listOf("CATEGORY_CODE_REQUIRED"),
                    ),
                )

            val userId = request.userId.toLongOrNull()
                ?: return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "userId must be numeric",
                        reasonCodes = listOf("USER_ID_NOT_NUMERIC"),
                    ),
                )

            val normalizedSource = urlNormalizer.normalize(request.source.url)
            val normalizedUrl = normalizedSource.normalized
            val canonicalUrl = request.source.canonicalUrl
                ?.let { urlNormalizer.normalize(it).normalized }
            val listingId = request.source.listingId?.trim()?.ifBlank { null }?.lowercase()
            val sourceType = request.source.sourceType.name
            val sourceId = request.source.sourceId ?: sourceRegistry.findBySourceType(request.source.sourceType)?.id

            val canonicalCond = if (canonicalUrl == null) OfferSourcesTable.canonicalUrl.isNull()
            else OfferSourcesTable.canonicalUrl eq canonicalUrl

            val listingCond = if (listingId == null) OfferSourcesTable.listingId.isNull()
            else OfferSourcesTable.listingId eq listingId

            val existingByCanonical = run {
                val q = OfferSourcesTable.selectAll()
                q.andWhere { OfferSourcesTable.userId eq userId }
                q.andWhere { OfferSourcesTable.sourceType eq sourceType }
                q.andWhere { canonicalCond }
                q.andWhere { listingCond }
                q.limit(1).singleOrNull()
            } ?: run {
                val q = OfferSourcesTable.selectAll()
                q.andWhere { OfferSourcesTable.userId eq userId }
                q.andWhere { OfferSourcesTable.sourceUrl eq normalizedUrl }
                q.limit(1).singleOrNull()
            }
            if (existingByCanonical != null) {
                val existingOfferId = existingByCanonical[OfferSourcesTable.offerId].toString()
                return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.ALREADY_EXISTS,
                        existingOfferId = existingOfferId,
                        offerId = existingOfferId,
                        message = "Offer already exists for this user and URL",
                        reasonCodes = listOf("ALREADY_EXISTS"),
                    ),
                )
            }

            val now = System.currentTimeMillis()
            val currency = Money.normalizeCurrencyCode(request.currency)
                ?: return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "Currency must be ISO-4217 code",
                        reasonCodes = listOf("CURRENCY_INVALID"),
                    ),
                )
            val priceCents = toMinorUnits(request.priceValue)

            // Собираем характеристики (primaryAttribute кладём как отдельный ключ, если есть).
            val rawAttrs = buildMap<String, String> {
                putAll(request.attributes.filterValues { it.isNotBlank() })
                request.primaryAttribute?.let { put("primary_attribute", it) }
            }
            val normalizationOutcome = stage4ExecutionLayer.normalizeAttributesForIngestStrict(
                categoryCode = normalizedCategoryCode,
                attributes = rawAttrs,
            )
            val attrs = normalizationOutcome.normalizedAttributes
            val requiredForCategoryReasons = evaluateRequiredForCategory(
                categoryCode = normalizedCategoryCode,
                normalizedAttributes = attrs,
                title = request.title,
                brand = request.brand,
                model = request.model,
            )
            val categoryConfidenceReasons = evaluateCategoryConfidence(request)
            val reasonCodes = (
                normalizationOutcome.reasonCodes +
                    requiredForCategoryReasons +
                    categoryConfidenceReasons
                ).distinct()
            if (requiredForCategoryHardFail && requiredForCategoryReasons.isNotEmpty()) {
                return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "Missing required attributes for category",
                        reasonCodes = reasonCodes,
                    ),
                )
            }
            if (categoryConfidenceHardFail && categoryConfidenceReasons.isNotEmpty()) {
                return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "Low category confidence from parser",
                        reasonCodes = reasonCodes,
                    ),
                )
            }
            val typedAttrs = stage4ExecutionLayer.toTypedAttributes(attrs)
            val condition = normalizeCondition(attrs["condition"])
            val deliveryChannel = normalizeDeliveryChannel(
                attrs["delivery_channel"] ?: attrs["delivery"]
            )
            val categoryConfidence = request.categoryConfidence?.coerceIn(0.0, 1.0)
            val parserVersion = request.parserVersion?.trim()?.ifBlank { null }

            stage4ExecutionObservabilityRepository.recordInTransaction(
                Stage4ExecutionMetricSample(
                    stream = Stage4ExecutionStream.OFFERS_INGEST,
                    normalizedCount = normalizationOutcome.normalizedCount,
                    droppedCount = normalizationOutcome.droppedCount,
                    logicalDedupCount = normalizationOutcome.logicalDedupCount,
                    unknownAttributeCount = normalizationOutcome.unknownAttributeCount,
                    reasonCodes = reasonCodes,
                    metadata = mapOf(
                        "operation" to "create",
                        "categoryCode" to normalizedCategoryCode,
                        "categoryConfidence" to (categoryConfidence?.toString() ?: "null"),
                        "parserVersion" to (parserVersion ?: "null"),
                        "categoryConfidenceLowCount" to categoryConfidenceReasons.size.toString(),
                        "categoryConfidenceHardFail" to categoryConfidenceHardFail.toString(),
                        "requiredForCategoryMissingCount" to requiredForCategoryReasons.size.toString(),
                        "requiredForCategoryHardFail" to requiredForCategoryHardFail.toString(),
                    ),
                    createdAtMs = now,
                ),
            )
            if (reasonCodes.isNotEmpty()) {
                logger.info(
                    "stage4.ingest.validation operation=create userId={} category={} reasons={}",
                    userId,
                    normalizedCategoryCode,
                    reasonCodes.joinToString(","),
                )
            }

            val brand = request.brand?.trim()?.ifBlank { null }
            val model = request.model?.trim()?.ifBlank { null }
            val images = request.imageUrls.filter { it.isNotBlank() }

            val productId = ProductsTable.insert { stmt ->
                stmt[ProductsTable.category] = normalizedCategoryCode
                stmt[ProductsTable.brand] = brand
                stmt[ProductsTable.model] = model
                stmt[ProductsTable.titleNorm] = request.title
                stmt[ProductsTable.imageUrls] = images
                stmt[ProductsTable.specs] = if (typedAttrs.isNotEmpty()) typedAttrs else null
                stmt[ProductsTable.description] = request.description
                stmt[ProductsTable.updatedAt] = now
            }.resultedValues?.single()?.get(ProductsTable.id)
                ?: return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "Failed to insert product",
                        reasonCodes = listOf("PRODUCT_INSERT_FAILED"),
                    ),
                )

            val offerId = OffersTable.insert { stmt ->
                stmt[OffersTable.productId] = productId
                stmt[OffersTable.userId] = userId
                stmt[OffersTable.priceCents] = priceCents
                stmt[OffersTable.currency] = currency
                stmt[OffersTable.attributes] = if (typedAttrs.isNotEmpty()) typedAttrs else null
                stmt[OffersTable.description] = request.description
                stmt[OffersTable.imageUrls] = images
                stmt[OffersTable.condition] = condition
                stmt[OffersTable.deliveryChannel] = deliveryChannel
                stmt[OffersTable.status] = "ACTIVE"
                stmt[OffersTable.updatedAt] = now
            }.resultedValues?.single()?.get(OffersTable.id)
                ?: return@dbQuery TrackedOfferPersistenceOutcome(
                    result = CreateTrackedOfferResult(
                        status = CreateTrackedOfferStatus.INVALID_INPUT,
                        message = "Failed to insert offer",
                        reasonCodes = listOf("OFFER_INSERT_FAILED"),
                    ),
                )

            val registryEntry = sourceRegistry.findBySourceType(request.source.sourceType)
            val canTrackPrice = registryEntry?.let {
                it.capabilities.canTrackPrice && it.rolloutEnabled && !it.capabilities.requiresBrowser
            } ?: false

            OfferSourcesTable.insert { stmt ->
                stmt[OfferSourcesTable.offerId] = offerId
                stmt[OfferSourcesTable.userId] = userId
                stmt[OfferSourcesTable.sourceType] = sourceType
                stmt[OfferSourcesTable.sourceUrl] = normalizedUrl
                stmt[OfferSourcesTable.canonicalUrl] = canonicalUrl
                stmt[OfferSourcesTable.listingId] = listingId
                stmt[OfferSourcesTable.domainName] =
                    normalizedSource.host?.trim()?.ifBlank { null } ?: request.source.domainName?.trim()?.ifBlank { null }
                stmt[OfferSourcesTable.sourceIconUrl] = request.source.sourceIconUrl?.trim()?.ifBlank { null }
                stmt[OfferSourcesTable.canTrackPrice] = canTrackPrice
                stmt[OfferSourcesTable.createdAt] = now
            }

            OfferPriceHistoryTable.insert { stmt ->
                stmt[OfferPriceHistoryTable.offerId] = offerId
                stmt[OfferPriceHistoryTable.priceMinor] = priceCents
                stmt[OfferPriceHistoryTable.currency] = currency
                stmt[OfferPriceHistoryTable.dataSource] = sourceId ?: request.source.sourceType.name
            }

            TrackedOfferPersistenceOutcome(
                result = CreateTrackedOfferResult(
                    status = CreateTrackedOfferStatus.CREATED,
                    offerId = offerId.toString(),
                    message = "Created",
                    reasonCodes = reasonCodes,
                ),
                enrichmentSignal = buildPhoneModelEnrichmentSignal(
                    categoryCode = normalizedCategoryCode,
                    brand = brand ?: attrs["brand"],
                    model = model ?: attrs["model"],
                    family = request.attributes["model_line"]?.trim()?.ifBlank { null } ?: attrs["model_line"],
                    offerId = offerId.toString(),
                    userId = userId.toString(),
                    categoryConfidence = categoryConfidence,
                    title = request.title,
                    sourceType = sourceType,
                    sourceUrl = normalizedUrl,
                    observedAt = now,
                ),
            )
        }
        if (outcome.enrichmentSignal != null) {
            ingestPhoneModelSignalSafely(outcome.enrichmentSignal)
        }
        return outcome.result
    }

    override suspend fun refreshTrackedOffer(request: RefreshTrackedOfferInput): RefreshTrackedOfferResult {
        val outcome = DatabaseFactory.dbQuery {
            val offerId = request.offerId.toLongOrNull()
                ?: return@dbQuery TrackedOfferRefreshOutcome(
                    result = RefreshTrackedOfferResult(
                        status = RefreshTrackedOfferStatus.INVALID_INPUT,
                        message = "offerId must be numeric",
                        reasonCodes = listOf("OFFER_ID_NOT_NUMERIC"),
                    ),
                )

            val price = request.priceValue
            val currency = Money.normalizeCurrencyCode(request.currency)
            if (price.isNaN() || price <= 0 || currency == null) {
                return@dbQuery TrackedOfferRefreshOutcome(
                    result = RefreshTrackedOfferResult(
                        status = RefreshTrackedOfferStatus.INVALID_INPUT,
                        message = "Invalid price or currency",
                        reasonCodes = listOf("PRICE_OR_CURRENCY_INVALID"),
                    ),
                )
            }
            val existing = run {
                val q = OffersTable
                    .innerJoin(ProductsTable, { OffersTable.productId }, { ProductsTable.id })
                    .selectAll()
                q.andWhere { OffersTable.id eq offerId }
                q.limit(1).singleOrNull()
            }
                ?: return@dbQuery TrackedOfferRefreshOutcome(
                    result = RefreshTrackedOfferResult(
                        status = RefreshTrackedOfferStatus.NOT_FOUND,
                        message = "Offer not found",
                        reasonCodes = listOf("OFFER_NOT_FOUND"),
                    ),
                )

            val currentAttrs = stage4ExecutionLayer.toRawStringAttributes(
                existing[OffersTable.attributes].orEmpty(),
            )
            val mergedRaw = if (request.attributes.isNotEmpty()) {
                currentAttrs + request.attributes.filterValues { it.isNotBlank() }
            } else {
                currentAttrs
            }
            val normalizationOutcome = stage4ExecutionLayer.normalizeAttributesForIngestStrict(
                categoryCode = existing[ProductsTable.category],
                attributes = mergedRaw,
            )
            val merged = normalizationOutcome.normalizedAttributes
            val requiredForCategoryReasons = evaluateRequiredForCategory(
                categoryCode = existing[ProductsTable.category],
                normalizedAttributes = merged,
                title = existing[ProductsTable.titleNorm],
                brand = existing[ProductsTable.brand],
                model = existing[ProductsTable.model],
            )
            val reasonCodes = (normalizationOutcome.reasonCodes + requiredForCategoryReasons).distinct()
            if (requiredForCategoryHardFail && requiredForCategoryReasons.isNotEmpty()) {
                return@dbQuery TrackedOfferRefreshOutcome(
                    result = RefreshTrackedOfferResult(
                        status = RefreshTrackedOfferStatus.INVALID_INPUT,
                        message = "Missing required attributes for category",
                        reasonCodes = reasonCodes,
                    ),
                )
            }
            val mergedTyped = stage4ExecutionLayer.toTypedAttributes(merged)
            val condition = normalizeCondition(merged["condition"])
                ?: existing[OffersTable.condition]
            val deliveryChannel = normalizeDeliveryChannel(
                merged["delivery_channel"] ?: merged["delivery"]
            ) ?: existing[OffersTable.deliveryChannel]

            stage4ExecutionObservabilityRepository.recordInTransaction(
                Stage4ExecutionMetricSample(
                    stream = Stage4ExecutionStream.OFFERS_INGEST,
                    normalizedCount = normalizationOutcome.normalizedCount,
                    droppedCount = normalizationOutcome.droppedCount,
                    logicalDedupCount = normalizationOutcome.logicalDedupCount,
                    unknownAttributeCount = normalizationOutcome.unknownAttributeCount,
                    reasonCodes = reasonCodes,
                    metadata = mapOf(
                        "operation" to "refresh",
                        "offerId" to offerId.toString(),
                        "categoryCode" to existing[ProductsTable.category],
                        "requiredForCategoryMissingCount" to requiredForCategoryReasons.size.toString(),
                        "requiredForCategoryHardFail" to requiredForCategoryHardFail.toString(),
                    ),
                ),
            )
            if (reasonCodes.isNotEmpty()) {
                logger.info(
                    "stage4.ingest.validation operation=refresh offerId={} category={} reasons={}",
                    offerId,
                    existing[ProductsTable.category],
                    reasonCodes.joinToString(","),
                )
            }

            val priceCents = toMinorUnits(price)
            val updatedAtMs = System.currentTimeMillis()
            OffersTable.update({ OffersTable.id eq offerId }) { stmt ->
                stmt[OffersTable.priceCents] = priceCents
                stmt[OffersTable.currency] = currency
                stmt[OffersTable.attributes] = mergedTyped.ifEmpty { null }
                stmt[OffersTable.condition] = condition
                stmt[OffersTable.deliveryChannel] = deliveryChannel
                stmt[OffersTable.updatedAt] = updatedAtMs
            }

            ProductsTable.update({ ProductsTable.id eq existing[OffersTable.productId] }) { stmt ->
                stmt[ProductsTable.specs] = mergedTyped.ifEmpty { null }
                stmt[ProductsTable.updatedAt] = updatedAtMs
            }

            OfferPriceHistoryTable.insert { stmt ->
                stmt[OfferPriceHistoryTable.offerId] = offerId
                stmt[OfferPriceHistoryTable.priceMinor] = priceCents
                stmt[OfferPriceHistoryTable.currency] = currency
                stmt[OfferPriceHistoryTable.dataSource] =
                    request.dataSource?.trim()?.ifBlank { null } ?: "REFRESH_MANUAL"
            }

            TrackedOfferRefreshOutcome(
                result = RefreshTrackedOfferResult(
                    status = RefreshTrackedOfferStatus.UPDATED,
                    message = "Updated",
                    reasonCodes = reasonCodes,
                ),
                enrichmentSignal = buildPhoneModelEnrichmentSignal(
                    categoryCode = existing[ProductsTable.category],
                    brand = existing[ProductsTable.brand] ?: merged["brand"],
                    model = existing[ProductsTable.model] ?: merged["model"],
                    family = request.attributes["model_line"]?.trim()?.ifBlank { null } ?: merged["model_line"],
                    offerId = offerId.toString(),
                    userId = existing[OffersTable.userId].toString(),
                    categoryConfidence = null,
                    title = existing[ProductsTable.titleNorm],
                    sourceType = null,
                    sourceUrl = null,
                    observedAt = updatedAtMs,
                ),
            )
        }
        if (outcome.enrichmentSignal != null) {
            ingestPhoneModelSignalSafely(outcome.enrichmentSignal)
        }
        return outcome.result
    }

    private fun validate(request: TrackedOfferInput, categoryCode: String?): ValidationIssue? {
        val title = request.title.trim()
        if (title.length !in 3..160) {
            return ValidationIssue("TITLE_LENGTH_INVALID", "Title length must be 3..160")
        }
        if (categoryCode.isNullOrBlank()) {
            return ValidationIssue("CATEGORY_CODE_REQUIRED", "categoryCode is required")
        }
        if (categoryCode !in knownCategoryCodes) {
            return ValidationIssue("CATEGORY_CODE_UNKNOWN", "Unknown categoryCode '$categoryCode'")
        }
        val brand = request.brand?.trim()
        if (brand != null && brand.length > 120) {
            return ValidationIssue("BRAND_TOO_LONG", "Brand is too long")
        }
        val model = request.model
        if (model != null && model.length > 160) {
            return ValidationIssue("MODEL_TOO_LONG", "Model is too long")
        }
        val price = request.priceValue
        if (price.isNaN() || price <= 0) {
            return ValidationIssue("PRICE_INVALID", "Price must be greater than zero")
        }
        if (Money.normalizeCurrencyCode(request.currency) == null) {
            return ValidationIssue("CURRENCY_INVALID", "Currency must be ISO-4217 code")
        }
        val categoryConfidence = request.categoryConfidence
        if (categoryConfidence != null && (!categoryConfidence.isFinite() || categoryConfidence !in 0.0..1.0)) {
            return ValidationIssue("CATEGORY_CONFIDENCE_INVALID", "categoryConfidence must be in range 0..1")
        }
        val parserVersion = request.parserVersion
        if (parserVersion != null && parserVersion.length > 64) {
            return ValidationIssue("PARSER_VERSION_TOO_LONG", "parserVersion is too long")
        }
        if (request.imageUrls.none { it.isNotBlank() }) {
            return ValidationIssue("IMAGE_URL_REQUIRED", "At least one imageUrl is required")
        }
        val url = request.source.url.trim()
        if (url.isEmpty()) {
            return ValidationIssue("SOURCE_URL_REQUIRED", "Source URL is required")
        }
        return null
    }

    private fun toMinorUnits(price: Double): Long =
        Money.fromMajor(price).minor

    private fun normalizeCondition(value: String?): String? {
        return normalizeOfferCondition(value)
    }

    private fun normalizeDeliveryChannel(value: String?): String? {
        val trimmed = value?.trim()?.lowercase().orEmpty()
        return when (trimmed) {
            "delivery", "pickup", "meeting" -> trimmed
            else -> null
        }
    }

    private fun evaluateRequiredForCategory(
        categoryCode: String,
        normalizedAttributes: Map<String, String>,
        title: String?,
        brand: String?,
        model: String?,
    ): List<String> {
        val code = categoryCode.trim().uppercase(Locale.ROOT)
        if (code.isEmpty()) return emptyList()
        val required = requiredAttributesByCategory[code].orEmpty()
        if (required.isEmpty()) return emptyList()
        return required
            .filter { attributeCode ->
                when (attributeCode) {
                    "brand" -> brand.isNullOrBlank() && normalizedAttributes["brand"].isNullOrBlank()
                    "model" -> model.isNullOrBlank() && normalizedAttributes["model"].isNullOrBlank()
                    "product_name" -> title.isNullOrBlank() && normalizedAttributes["product_name"].isNullOrBlank()
                    else -> normalizedAttributes[attributeCode].isNullOrBlank()
                }
            }
            .map { attributeCode -> "REQUIRED_FOR_CATEGORY_MISSING:$attributeCode" }
    }

    private fun evaluateCategoryConfidence(request: TrackedOfferInput): List<String> {
        val confidence = request.categoryConfidence ?: return emptyList()
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            return listOf("CATEGORY_CONFIDENCE_INVALID")
        }
        if (confidence < minCategoryConfidence) {
            return listOf("CATEGORY_CONFIDENCE_LOW")
        }
        return emptyList()
    }

    private data class ValidationIssue(
        val code: String,
        val message: String,
    )

    private data class TrackedOfferPersistenceOutcome(
        val result: CreateTrackedOfferResult,
        val enrichmentSignal: CatalogPhoneModelRuntimeSignal? = null,
    )

    private data class TrackedOfferRefreshOutcome(
        val result: RefreshTrackedOfferResult,
        val enrichmentSignal: CatalogPhoneModelRuntimeSignal? = null,
    )

    private suspend fun ingestPhoneModelSignalSafely(signal: CatalogPhoneModelRuntimeSignal) {
        runCatching {
            phoneModelEnrichmentService.ingest(signal)
        }.onFailure { error ->
            logger.warn(
                "catalog.phone_model_enrichment.enqueue_failed category={} brand={} model={} reason={}",
                signal.categoryCode,
                signal.brandRaw,
                signal.modelRaw,
                error.message,
            )
        }
    }

    private fun buildPhoneModelEnrichmentSignal(
        categoryCode: String,
        brand: String?,
        model: String?,
        family: String?,
        offerId: String,
        userId: String,
        categoryConfidence: Double?,
        title: String?,
        sourceType: String?,
        sourceUrl: String?,
        observedAt: Long,
    ): CatalogPhoneModelRuntimeSignal? {
        val normalizedCategory = categoryCode.trim().uppercase(Locale.ROOT)
        if (normalizedCategory != CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE) return null
        val normalizedBrand = brand?.trim()?.ifBlank { null } ?: return null
        val normalizedModel = model?.trim()?.ifBlank { null } ?: return null
        return CatalogPhoneModelRuntimeSignal(
            categoryCode = normalizedCategory,
            brandRaw = normalizedBrand,
            modelRaw = normalizedModel,
            familyRaw = family?.trim()?.ifBlank { null },
            offerRef = offerId,
            sellerRef = userId,
            confidence = categoryConfidence?.coerceIn(0.0, 1.0) ?: 1.0,
            title = title?.trim()?.ifBlank { null },
            sourceType = sourceType?.trim()?.ifBlank { null },
            sourceUrl = sourceUrl?.trim()?.ifBlank { null },
            observedAt = observedAt,
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(TrackedOfferRepositoryImpl::class.java)
        private const val CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE = "TECH.PHONES"
        private val knownCategoryCodes: Set<String> = CatalogSeed.categories
            .map { category -> category.code.trim().uppercase(Locale.ROOT) }
            .filter { code -> code.isNotEmpty() }
            .toSet()
        private val requiredAttributesByCategory: Map<String, Set<String>> = CatalogSeed.categoryWriteSpecs
            .associate { spec ->
                val categoryCode = spec.category.code.trim().uppercase(Locale.ROOT)
                val requiredCodes = spec.categoryAttributes
                    .asSequence()
                    .filter { categoryAttribute -> categoryAttribute.isRequiredForCategory }
                    .map { categoryAttribute -> categoryAttribute.attributeCode.trim().lowercase(Locale.ROOT) }
                    .filter { attributeCode -> attributeCode.isNotEmpty() }
                    .toSet()
                categoryCode to requiredCodes
            }
        private fun resolveRequiredForCategoryHardFail(): Boolean =
            (System.getenv("OFFERS_REQUIRED_FOR_CATEGORY_HARD_FAIL")
                ?: System.getenv("STAGE4_REQUIRED_FOR_CATEGORY_HARD_FAIL")
                ?: "false")
                .trim()
                .equals("true", ignoreCase = true)
        private fun resolveCategoryConfidenceHardFail(): Boolean =
            (System.getenv("OFFERS_CATEGORY_CONFIDENCE_HARD_FAIL")
                ?: "false")
                .trim()
                .equals("true", ignoreCase = true)
        private fun resolveMinCategoryConfidence(): Double =
            System.getenv("OFFERS_MIN_CATEGORY_CONFIDENCE")
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, 1.0)
                ?: 0.35
    }
}
