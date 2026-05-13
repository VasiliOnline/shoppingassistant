package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistry
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshEndpoint
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSource
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSources
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.json.jsonb
import org.slf4j.LoggerFactory
import java.util.Locale

enum class CatalogPhoneModelEnrichmentStatus {
    MONITORING,
    READY_FOR_OFFICIAL_ENRICHMENT,
    ENRICHED,
    REJECTED,
}

data class CatalogPhoneModelRuntimeSignal(
    val categoryCode: String,
    val brandRaw: String?,
    val modelRaw: String?,
    val familyRaw: String? = null,
    val offerRef: String? = null,
    val sellerRef: String? = null,
    val confidence: Double? = null,
    val title: String? = null,
    val sourceType: String? = null,
    val sourceUrl: String? = null,
    val observedAt: Long = System.currentTimeMillis(),
)

data class CatalogPhoneModelEnrichmentCandidate(
    val id: Long? = null,
    val candidateKey: String,
    val categoryCode: String,
    val brandRaw: String,
    val brandNormalized: String,
    val brandCode: String? = null,
    val familyRaw: String? = null,
    val familyCode: String? = null,
    val canonicalModelCode: String? = null,
    val modelRaw: String,
    val modelNormalized: String,
    val officialSourceCode: String? = null,
    val officialEndpointCode: String? = null,
    val status: CatalogPhoneModelEnrichmentStatus = CatalogPhoneModelEnrichmentStatus.MONITORING,
    val observedCount: Int = 0,
    val distinctSellerCount: Int = 0,
    val sellerRefs: List<String> = emptyList(),
    val sampleOfferRefs: List<String> = emptyList(),
    val maxConfidence: Double = 0.0,
    val reasonCodes: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

interface CatalogPhoneModelEnrichmentService {
    suspend fun ingest(signal: CatalogPhoneModelRuntimeSignal)

    suspend fun listCandidates(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        statuses: Set<CatalogPhoneModelEnrichmentStatus> = emptySet(),
        limit: Int = 100,
    ): List<CatalogPhoneModelEnrichmentCandidate>

    suspend fun getCandidate(candidateId: Long): CatalogPhoneModelEnrichmentCandidate?

    suspend fun markOfficiallySeeded(
        candidateId: Long,
        officialSourceCode: String,
        officialEndpointCode: String,
        metadata: Map<String, String> = emptyMap(),
    ): CatalogPhoneModelEnrichmentCandidate?
}

object NoopCatalogPhoneModelEnrichmentService : CatalogPhoneModelEnrichmentService {
    override suspend fun ingest(signal: CatalogPhoneModelRuntimeSignal) = Unit

    override suspend fun listCandidates(
        categoryCode: String,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogPhoneModelEnrichmentCandidate> = emptyList()

    override suspend fun getCandidate(candidateId: Long): CatalogPhoneModelEnrichmentCandidate? = null

    override suspend fun markOfficiallySeeded(
        candidateId: Long,
        officialSourceCode: String,
        officialEndpointCode: String,
        metadata: Map<String, String>,
    ): CatalogPhoneModelEnrichmentCandidate? = null
}

class DatabaseCatalogPhoneModelEnrichmentRepository {
    fun findByIdInTransaction(candidateId: Long): CatalogPhoneModelEnrichmentCandidate? =
        CatalogPhoneModelEnrichmentCandidatesTable.selectAll()
            .singleOrNull { row -> row[CatalogPhoneModelEnrichmentCandidatesTable.id] == candidateId }
            ?.toCandidate()

    fun findByCandidateKeyInTransaction(candidateKey: String): CatalogPhoneModelEnrichmentCandidate? =
        CatalogPhoneModelEnrichmentCandidatesTable.selectAll()
            .singleOrNull { row -> row[CatalogPhoneModelEnrichmentCandidatesTable.candidateKey] == candidateKey }
            ?.toCandidate()

    fun upsertInTransaction(candidate: CatalogPhoneModelEnrichmentCandidate): CatalogPhoneModelEnrichmentCandidate {
        val existing = findByCandidateKeyInTransaction(candidate.candidateKey)
        if (existing == null) {
            return CatalogPhoneModelEnrichmentCandidatesTable.insert { stmt ->
                stmt[candidateKey] = candidate.candidateKey
                stmt[categoryCode] = candidate.categoryCode
                stmt[brandRaw] = candidate.brandRaw
                stmt[brandNormalized] = candidate.brandNormalized
                stmt[brandCode] = candidate.brandCode
                stmt[familyRaw] = candidate.familyRaw
                stmt[familyCode] = candidate.familyCode
                stmt[canonicalModelCode] = candidate.canonicalModelCode
                stmt[modelRaw] = candidate.modelRaw
                stmt[modelNormalized] = candidate.modelNormalized
                stmt[officialSourceCode] = candidate.officialSourceCode
                stmt[officialEndpointCode] = candidate.officialEndpointCode
                stmt[status] = candidate.status.name
                stmt[observedCount] = candidate.observedCount.coerceAtLeast(0)
                stmt[distinctSellerCount] = candidate.distinctSellerCount.coerceAtLeast(0)
                stmt[sellerRefs] = candidate.sellerRefs
                stmt[sampleOfferRefs] = candidate.sampleOfferRefs
                stmt[maxConfidence] = candidate.maxConfidence.coerceIn(0.0, 1.0)
                stmt[reasonCodes] = candidate.reasonCodes
                stmt[metadata] = candidate.metadata
                stmt[firstSeenAt] = candidate.firstSeenAt
                stmt[lastSeenAt] = candidate.lastSeenAt
                stmt[createdAt] = candidate.createdAt
                stmt[updatedAt] = candidate.updatedAt
            }.resultedValues!!.single().toCandidate()
        }
        CatalogPhoneModelEnrichmentCandidatesTable.update({
            CatalogPhoneModelEnrichmentCandidatesTable.id eq (existing.id ?: 0L)
        }) { stmt ->
            stmt[categoryCode] = candidate.categoryCode
            stmt[brandRaw] = candidate.brandRaw
            stmt[brandNormalized] = candidate.brandNormalized
            stmt[brandCode] = candidate.brandCode
            stmt[familyRaw] = candidate.familyRaw
            stmt[familyCode] = candidate.familyCode
            stmt[canonicalModelCode] = candidate.canonicalModelCode
            stmt[modelRaw] = candidate.modelRaw
            stmt[modelNormalized] = candidate.modelNormalized
            stmt[officialSourceCode] = candidate.officialSourceCode
            stmt[officialEndpointCode] = candidate.officialEndpointCode
            stmt[status] = candidate.status.name
            stmt[observedCount] = candidate.observedCount.coerceAtLeast(0)
            stmt[distinctSellerCount] = candidate.distinctSellerCount.coerceAtLeast(0)
            stmt[sellerRefs] = candidate.sellerRefs
            stmt[sampleOfferRefs] = candidate.sampleOfferRefs
            stmt[maxConfidence] = candidate.maxConfidence.coerceIn(0.0, 1.0)
            stmt[reasonCodes] = candidate.reasonCodes
            stmt[metadata] = candidate.metadata
            stmt[firstSeenAt] = candidate.firstSeenAt
            stmt[lastSeenAt] = candidate.lastSeenAt
            stmt[createdAt] = candidate.createdAt
            stmt[updatedAt] = candidate.updatedAt
        }
        return CatalogPhoneModelEnrichmentCandidatesTable.selectAll()
            .single { row -> row[CatalogPhoneModelEnrichmentCandidatesTable.id] == existing.id }
            .toCandidate()
    }

    suspend fun listCandidates(
        categoryCode: String?,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogPhoneModelEnrichmentCandidate> = DatabaseFactory.dbQuery {
        val query = CatalogPhoneModelEnrichmentCandidatesTable.selectAll()
        categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedCategoryCode ->
            query.andWhere { CatalogPhoneModelEnrichmentCandidatesTable.categoryCode eq normalizedCategoryCode }
        }
        if (statuses.isNotEmpty()) {
            query.andWhere {
                CatalogPhoneModelEnrichmentCandidatesTable.status inList statuses.map { it.name }
            }
        }
        query.orderBy(CatalogPhoneModelEnrichmentCandidatesTable.updatedAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { row -> row.toCandidate() }
    }

    suspend fun getCandidate(candidateId: Long): CatalogPhoneModelEnrichmentCandidate? = DatabaseFactory.dbQuery {
        findByIdInTransaction(candidateId)
    }

    suspend fun updateCandidate(
        candidate: CatalogPhoneModelEnrichmentCandidate,
    ): CatalogPhoneModelEnrichmentCandidate = DatabaseFactory.dbQuery {
        upsertInTransaction(candidate)
    }
}

class CatalogPhoneModelEnrichmentServiceImpl(
    private val repository: DatabaseCatalogPhoneModelEnrichmentRepository = DatabaseCatalogPhoneModelEnrichmentRepository(),
    private val endpointOverlayRepository: CatalogGovernanceOfficialPhoneEndpointOverlayRepository =
        NoopCatalogGovernanceOfficialPhoneEndpointOverlayRepository,
) : CatalogPhoneModelEnrichmentService {
    private val logger = LoggerFactory.getLogger(CatalogPhoneModelEnrichmentServiceImpl::class.java)

    override suspend fun ingest(signal: CatalogPhoneModelRuntimeSignal) {
        val normalizedSignal = normalizeSignal(signal) ?: return
        runCatching {
            DatabaseFactory.dbQuery {
                val existing = repository.findByCandidateKeyInTransaction(normalizedSignal.candidateKey)
                val merged = mergeCandidate(
                    existing = existing,
                    signal = normalizedSignal,
                )
                repository.upsertInTransaction(
                    evaluateCandidate(merged),
                )
            }
        }.onFailure { error ->
            logger.warn(
                "catalog.phone_model_enrichment.ingest_failed category={} brand={} model={} reason={}",
                signal.categoryCode,
                signal.brandRaw,
                signal.modelRaw,
                error.message,
            )
        }
    }

    override suspend fun listCandidates(
        categoryCode: String,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogPhoneModelEnrichmentCandidate> =
        repository.listCandidates(categoryCode = categoryCode, statuses = statuses, limit = limit)
            .sortedWith(
                compareBy<CatalogPhoneModelEnrichmentCandidate> { statusOrder(it.status) }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.observedCount }
                    .thenBy { it.candidateKey },
            )

    override suspend fun getCandidate(candidateId: Long): CatalogPhoneModelEnrichmentCandidate? =
        repository.getCandidate(candidateId)

    override suspend fun markOfficiallySeeded(
        candidateId: Long,
        officialSourceCode: String,
        officialEndpointCode: String,
        metadata: Map<String, String>,
    ): CatalogPhoneModelEnrichmentCandidate? {
        val existing = repository.getCandidate(candidateId) ?: return null
        return repository.updateCandidate(
            existing.copy(
                officialSourceCode = officialSourceCode.trim().ifEmpty { existing.officialSourceCode.orEmpty() },
                officialEndpointCode = officialEndpointCode.trim().ifEmpty { existing.officialEndpointCode.orEmpty() },
                status = CatalogPhoneModelEnrichmentStatus.ENRICHED,
                reasonCodes = listOf("official_endpoint_seeded"),
                metadata = (existing.metadata + metadata)
                    .filterValues { value -> value.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun normalizeSignal(signal: CatalogPhoneModelRuntimeSignal): NormalizedSignal? {
        val categoryCode = signal.categoryCode.trim().uppercase(Locale.ROOT)
        if (categoryCode != CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE) return null
        val brandRaw = signal.brandRaw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val modelRaw = signal.modelRaw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val brandNormalized = normalizeLookupKey(brandRaw)
        val modelNormalized = normalizeModelKey(
            modelRaw = modelRaw,
            brandRaw = brandRaw,
        )
        if (brandNormalized.isBlank() || modelNormalized.isBlank()) return null

        val modelMatch = resolveModelMatch(brandRaw = brandRaw, modelRaw = modelRaw)
        val familyMatch = resolveFamilyMatch(
            brandRaw = brandRaw,
            familyRaw = signal.familyRaw,
            modelRaw = modelRaw,
        )
        val brandCode = sequenceOf(
            CatalogGovernanceCuratedSeed.resolveBrandCode(brandRaw),
            modelMatch?.brandCanonical?.let(CatalogGovernanceCuratedSeed::resolveBrandCode),
            familyMatch?.brandCanonical?.let(CatalogGovernanceCuratedSeed::resolveBrandCode),
        )
            .firstOrNull { it != null }
        val officialSource = brandCode?.let(officialSourcesByBrandCode::get)
        val coveredEndpoint = findCoveredEndpoint(
            source = officialSource,
            canonicalModelCode = modelMatch?.modelCode,
            brandRaw = brandRaw,
            modelRaw = modelRaw,
        )
        val canonicalModelCode = modelMatch?.modelCode ?: coveredEndpoint?.modelCode
        val familyCode = modelMatch?.familyCode ?: familyMatch?.familyCode ?: coveredEndpoint?.familyCode
        val familyRaw = signal.familyRaw?.trim()?.ifEmpty { null } ?: familyMatch?.prettyModelPrefix
        val candidateKey = buildCandidateKey(
            categoryCode = categoryCode,
            canonicalModelCode = canonicalModelCode,
            brandCode = brandCode,
            brandNormalized = brandNormalized,
            modelNormalized = modelNormalized,
        )
        return NormalizedSignal(
            candidateKey = candidateKey,
            categoryCode = categoryCode,
            brandRaw = brandRaw,
            brandNormalized = brandNormalized,
            brandCode = brandCode,
            familyRaw = familyRaw,
            familyCode = familyCode,
            canonicalModelCode = canonicalModelCode,
            modelRaw = modelRaw,
            modelNormalized = modelNormalized,
            officialSourceCode = officialSource?.sourceCode,
            officialEndpointCode = coveredEndpoint?.endpointCode,
            offerRef = signal.offerRef?.trim()?.ifEmpty { null },
            sellerRef = signal.sellerRef?.trim()?.ifEmpty { null },
            confidence = signal.confidence?.coerceIn(0.0, 1.0) ?: 1.0,
            observedAt = signal.observedAt,
            metadata = buildMap {
                signal.title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("sampleTitle", it) }
                signal.sourceType?.trim()?.takeIf { it.isNotEmpty() }?.let { put("sourceType", it) }
                signal.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("sourceUrl", it) }
            },
        )
    }

    private fun resolveModelMatch(
        brandRaw: String,
        modelRaw: String,
    ) = sequenceOf(
        "$brandRaw $modelRaw",
        modelRaw,
    )
        .map { query -> query.trim() }
        .filter { query -> query.isNotEmpty() }
        .mapNotNull { query -> CatalogCanonicalModelRegistry.matchQuery(query) }
        .firstOrNull { match ->
            val resolvedBrandCode = CatalogGovernanceCuratedSeed.resolveBrandCode(match.brandCanonical)
            resolvedBrandCode == null || resolvedBrandCode == CatalogGovernanceCuratedSeed.resolveBrandCode(brandRaw)
        }

    private fun resolveFamilyMatch(
        brandRaw: String,
        familyRaw: String?,
        modelRaw: String,
    ) = sequenceOf(
        familyRaw?.let { "$brandRaw $it" },
        "$brandRaw $modelRaw",
    )
        .mapNotNull { it?.trim()?.takeIf { query -> query.isNotEmpty() } }
        .mapNotNull { query -> CatalogCanonicalProductFamilyRegistry.matchQuery(query) }
        .firstOrNull()

    private suspend fun findCoveredEndpoint(
        source: CatalogGovernanceOfficialRefreshSource?,
        canonicalModelCode: String?,
        brandRaw: String,
        modelRaw: String,
    ): CatalogGovernanceOfficialRefreshEndpoint? {
        val normalizedCandidates = buildSet {
            add(normalizeModelKey(modelRaw = modelRaw, brandRaw = brandRaw))
            add(normalizeLookupKey(modelRaw))
            add(normalizeLookupKey("$brandRaw $modelRaw"))
        }
        val endpoints = source?.let { declaredSource ->
            declaredSource.endpoints + endpointOverlayRepository.listRefreshEndpoints(
                categoryCode = declaredSource.categoryCode,
                sourceCode = declaredSource.sourceCode,
            )
        }.orEmpty()
        return endpoints.firstOrNull { endpoint ->
            endpoint.modelCode == canonicalModelCode ||
                endpointNormalizedAliases(endpoint).any { alias -> alias in normalizedCandidates }
        }
    }

    private fun mergeCandidate(
        existing: CatalogPhoneModelEnrichmentCandidate?,
        signal: NormalizedSignal,
    ): CatalogPhoneModelEnrichmentCandidate {
        val mergedOfferRefs = (existing?.sampleOfferRefs.orEmpty() + listOfNotNull(signal.offerRef))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SAMPLE_REFS)
        val mergedSellerRefs = (existing?.sellerRefs.orEmpty() + listOfNotNull(signal.sellerRef))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SELLER_REFS)
        val isNewObservation = when {
            signal.offerRef == null -> true
            existing == null -> true
            signal.offerRef !in existing.sampleOfferRefs -> true
            else -> false
        }
        val observedCount = when {
            existing == null -> 1
            isNewObservation -> existing.observedCount + 1
            else -> existing.observedCount
        }
        val firstSeenAt = minOf(existing?.firstSeenAt ?: signal.observedAt, signal.observedAt)
        val lastSeenAt = maxOf(existing?.lastSeenAt ?: signal.observedAt, signal.observedAt)
        val metadata = (existing?.metadata.orEmpty() + signal.metadata)
            .filterValues { value -> value.isNotBlank() }
        return CatalogPhoneModelEnrichmentCandidate(
            id = existing?.id,
            candidateKey = signal.candidateKey,
            categoryCode = signal.categoryCode,
            brandRaw = if (!existing?.brandRaw.isNullOrBlank()) existing!!.brandRaw else signal.brandRaw,
            brandNormalized = signal.brandNormalized,
            brandCode = signal.brandCode ?: existing?.brandCode,
            familyRaw = signal.familyRaw ?: existing?.familyRaw,
            familyCode = signal.familyCode ?: existing?.familyCode,
            canonicalModelCode = signal.canonicalModelCode ?: existing?.canonicalModelCode,
            modelRaw = if (!existing?.modelRaw.isNullOrBlank()) choosePreferredModelRaw(existing!!.modelRaw, signal.modelRaw) else signal.modelRaw,
            modelNormalized = signal.modelNormalized,
            officialSourceCode = signal.officialSourceCode ?: existing?.officialSourceCode,
            officialEndpointCode = signal.officialEndpointCode ?: existing?.officialEndpointCode,
            status = existing?.status ?: CatalogPhoneModelEnrichmentStatus.MONITORING,
            observedCount = observedCount,
            distinctSellerCount = mergedSellerRefs.size,
            sellerRefs = mergedSellerRefs,
            sampleOfferRefs = mergedOfferRefs,
            maxConfidence = maxOf(existing?.maxConfidence ?: 0.0, signal.confidence),
            reasonCodes = existing?.reasonCodes.orEmpty(),
            metadata = metadata,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            createdAt = existing?.createdAt ?: signal.observedAt,
            updatedAt = signal.observedAt,
        )
    }

    private fun evaluateCandidate(
        candidate: CatalogPhoneModelEnrichmentCandidate,
    ): CatalogPhoneModelEnrichmentCandidate {
        if (looksLikeAccessoryNoise(candidate.modelNormalized)) {
            return candidate.copy(
                status = CatalogPhoneModelEnrichmentStatus.REJECTED,
                reasonCodes = listOf("accessory_noise_detected"),
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (!candidate.officialEndpointCode.isNullOrBlank()) {
            return candidate.copy(
                status = CatalogPhoneModelEnrichmentStatus.ENRICHED,
                reasonCodes = listOf("already_officially_covered"),
                updatedAt = System.currentTimeMillis(),
            )
        }

        val reasons = buildList {
            if (candidate.brandCode.isNullOrBlank()) add("brand_unresolved")
            if (candidate.officialSourceCode.isNullOrBlank()) add("official_source_missing")
            if (candidate.observedCount < MIN_OBSERVED_COUNT) add("observed_below_min:$MIN_OBSERVED_COUNT")
            if (candidate.distinctSellerCount < MIN_DISTINCT_SELLERS) add("seller_count_below_min:$MIN_DISTINCT_SELLERS")
            if (candidate.maxConfidence < MIN_CONFIDENCE) add("confidence_below_min:${MIN_CONFIDENCE.formatThreshold()}")
        }
        val nextStatus = if (reasons.isEmpty()) {
            CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT
        } else {
            CatalogPhoneModelEnrichmentStatus.MONITORING
        }
        return candidate.copy(
            status = nextStatus,
            reasonCodes = reasons,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun statusOrder(status: CatalogPhoneModelEnrichmentStatus): Int = when (status) {
        CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT -> 0
        CatalogPhoneModelEnrichmentStatus.MONITORING -> 1
        CatalogPhoneModelEnrichmentStatus.ENRICHED -> 2
        CatalogPhoneModelEnrichmentStatus.REJECTED -> 3
    }

    private fun normalizeModelKey(
        modelRaw: String,
        brandRaw: String?,
    ): String {
        val normalizedModel = normalizeLookupKey(modelRaw)
        val normalizedBrand = normalizeLookupKey(brandRaw.orEmpty())
        return when {
            normalizedBrand.isNotBlank() && normalizedModel.startsWith("$normalizedBrand ") ->
                normalizedModel.removePrefix("$normalizedBrand ").trim()

            else -> normalizedModel
        }
    }

    private fun buildCandidateKey(
        categoryCode: String,
        canonicalModelCode: String?,
        brandCode: String?,
        brandNormalized: String,
        modelNormalized: String,
    ): String = when {
        !canonicalModelCode.isNullOrBlank() -> "$categoryCode|MODEL|${canonicalModelCode.trim().uppercase(Locale.ROOT)}"
        !brandCode.isNullOrBlank() -> "$categoryCode|BRAND|${brandCode.trim().uppercase(Locale.ROOT)}|$modelNormalized"
        else -> "$categoryCode|RAW|$brandNormalized|$modelNormalized"
    }

    private fun choosePreferredModelRaw(
        existing: String,
        incoming: String,
    ): String = if (incoming.length > existing.length) incoming else existing

    private fun looksLikeAccessoryNoise(modelNormalized: String): Boolean =
        accessoryNoiseTokens.any { token ->
            modelNormalized.contains(token)
        }

    private fun endpointNormalizedAliases(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
    ): Set<String> = buildSet {
        add(normalizeLookupKey(endpoint.modelLabel))
        endpoint.aliases.values.flatten()
            .map(::normalizeLookupKey)
            .filter { alias -> alias.isNotBlank() }
            .forEach(::add)
    }

    private fun normalizeLookupKey(raw: String): String =
        SearchTextNormalizer.normalize(raw)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class NormalizedSignal(
        val candidateKey: String,
        val categoryCode: String,
        val brandRaw: String,
        val brandNormalized: String,
        val brandCode: String?,
        val familyRaw: String?,
        val familyCode: String?,
        val canonicalModelCode: String?,
        val modelRaw: String,
        val modelNormalized: String,
        val officialSourceCode: String?,
        val officialEndpointCode: String?,
        val offerRef: String?,
        val sellerRef: String?,
        val confidence: Double,
        val observedAt: Long,
        val metadata: Map<String, String>,
    )

    private companion object {
        private const val MIN_OBSERVED_COUNT = 3
        private const val MIN_DISTINCT_SELLERS = 2
        private const val MIN_CONFIDENCE = 0.70
        private const val MAX_SAMPLE_REFS = 20
        private const val MAX_SELLER_REFS = 20
        private val officialSourcesByBrandCode: Map<String, CatalogGovernanceOfficialRefreshSource> by lazy {
            CatalogGovernanceOfficialRefreshSources.resolve(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE)
                .associateBy { source -> source.brandCode }
        }
        private val accessoryNoiseTokens = listOf(
            "чехол",
            "case",
            "cover",
            "glass",
            "защит",
            "кабель",
            "cable",
            "charger",
            "заряд",
        )
    }
}

object CatalogPhoneModelEnrichmentCandidatesTable : Table("catalog_phone_model_enrichment_candidates") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = long("id").autoIncrement()
    val candidateKey = varchar("candidate_key", 255)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code)
    val brandRaw = varchar("brand_raw", 255)
    val brandNormalized = varchar("brand_normalized", 255)
    val brandCode = varchar("brand_code", 64).nullable()
    val familyRaw = varchar("family_raw", 255).nullable()
    val familyCode = varchar("family_code", 64).nullable()
    val canonicalModelCode = varchar("canonical_model_code", 96).nullable()
    val modelRaw = varchar("model_raw", 255)
    val modelNormalized = varchar("model_normalized", 255)
    val officialSourceCode = varchar("official_source_code", 64).nullable()
    val officialEndpointCode = varchar("official_endpoint_code", 96).nullable()
    val status = varchar("status", 40)
    val observedCount = integer("observed_count").default(0)
    val distinctSellerCount = integer("distinct_seller_count").default(0)
    val sellerRefs = jsonb("seller_refs", json, ListSerializer(String.serializer()))
    val sampleOfferRefs = jsonb("sample_offer_refs", json, ListSerializer(String.serializer()))
    val maxConfidence = double("max_confidence").default(0.0)
    val reasonCodes = jsonb("reason_codes", json, ListSerializer(String.serializer()))
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val firstSeenAt = long("first_seen_at")
    val lastSeenAt = long("last_seen_at")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(candidateKey)
        index(false, categoryCode, status)
        index(false, brandCode, status)
        index(false, canonicalModelCode)
        index(false, updatedAt)
    }
}

private fun ResultRow.toCandidate(): CatalogPhoneModelEnrichmentCandidate =
    CatalogPhoneModelEnrichmentCandidate(
        id = this[CatalogPhoneModelEnrichmentCandidatesTable.id],
        candidateKey = this[CatalogPhoneModelEnrichmentCandidatesTable.candidateKey],
        categoryCode = this[CatalogPhoneModelEnrichmentCandidatesTable.categoryCode],
        brandRaw = this[CatalogPhoneModelEnrichmentCandidatesTable.brandRaw],
        brandNormalized = this[CatalogPhoneModelEnrichmentCandidatesTable.brandNormalized],
        brandCode = this[CatalogPhoneModelEnrichmentCandidatesTable.brandCode],
        familyRaw = this[CatalogPhoneModelEnrichmentCandidatesTable.familyRaw],
        familyCode = this[CatalogPhoneModelEnrichmentCandidatesTable.familyCode],
        canonicalModelCode = this[CatalogPhoneModelEnrichmentCandidatesTable.canonicalModelCode],
        modelRaw = this[CatalogPhoneModelEnrichmentCandidatesTable.modelRaw],
        modelNormalized = this[CatalogPhoneModelEnrichmentCandidatesTable.modelNormalized],
        officialSourceCode = this[CatalogPhoneModelEnrichmentCandidatesTable.officialSourceCode],
        officialEndpointCode = this[CatalogPhoneModelEnrichmentCandidatesTable.officialEndpointCode],
        status = runCatching {
            CatalogPhoneModelEnrichmentStatus.valueOf(this[CatalogPhoneModelEnrichmentCandidatesTable.status])
        }.getOrDefault(CatalogPhoneModelEnrichmentStatus.MONITORING),
        observedCount = this[CatalogPhoneModelEnrichmentCandidatesTable.observedCount],
        distinctSellerCount = this[CatalogPhoneModelEnrichmentCandidatesTable.distinctSellerCount],
        sellerRefs = this[CatalogPhoneModelEnrichmentCandidatesTable.sellerRefs],
        sampleOfferRefs = this[CatalogPhoneModelEnrichmentCandidatesTable.sampleOfferRefs],
        maxConfidence = this[CatalogPhoneModelEnrichmentCandidatesTable.maxConfidence],
        reasonCodes = this[CatalogPhoneModelEnrichmentCandidatesTable.reasonCodes],
        metadata = this[CatalogPhoneModelEnrichmentCandidatesTable.metadata],
        firstSeenAt = this[CatalogPhoneModelEnrichmentCandidatesTable.firstSeenAt],
        lastSeenAt = this[CatalogPhoneModelEnrichmentCandidatesTable.lastSeenAt],
        createdAt = this[CatalogPhoneModelEnrichmentCandidatesTable.createdAt],
        updatedAt = this[CatalogPhoneModelEnrichmentCandidatesTable.updatedAt],
    )

private fun Double.formatThreshold(): String = String.format(Locale.US, "%.2f", this)
