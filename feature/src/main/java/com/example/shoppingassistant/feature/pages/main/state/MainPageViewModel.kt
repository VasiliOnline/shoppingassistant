// Last synced: 2025-12-21 15:52:45
// GPT: task=MainPage part=state/MainPageViewModel role=state v=1
package com.example.shoppingassistant.feature.pages.main.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.core.data.Normalizer
import com.example.shoppingassistant.core.data.ProductRepository
import com.example.shoppingassistant.core.data.nearby.NearbyBrand
import com.example.shoppingassistant.core.data.nearby.NearbyCondition
import com.example.shoppingassistant.core.data.nearby.NearbyDelivery
import com.example.shoppingassistant.core.data.nearby.NearbyFiltersAppliedState
import com.example.shoppingassistant.core.data.nearby.NearbyFiltersState
import com.example.shoppingassistant.core.data.nearby.NearbyFiltersStorage
import com.example.shoppingassistant.core.data.nearby.NearbyPlace
import com.example.shoppingassistant.core.data.nearby.NearbyPostedAt
import com.example.shoppingassistant.core.data.nearby.NearbyScope
import com.example.shoppingassistant.core.data.nearby.NearbySort
import com.example.shoppingassistant.core.data.nearby.MAX_NEARBY_RADIUS_KM
import com.example.shoppingassistant.core.data.link.LinkTemplateBuilderTask
import com.example.shoppingassistant.core.data.link.LinkTemplateMapperTask
import com.example.shoppingassistant.core.data.link.LinkTemplateRaw
import com.example.shoppingassistant.core.data.searchTopExplained
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.usecase.SearchOffersWithFacetsUseCase
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.template.TemplateAnchorType
import com.example.shoppingassistant.domain.template.TemplateHistoryEntry
import com.example.shoppingassistant.domain.template.TemplateHistoryRepository
import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.TemplateSnapshot
import com.example.shoppingassistant.domain.template.TemplateSnapshotAttr
import com.example.shoppingassistant.domain.template.TemplateSnapshotData
import com.example.shoppingassistant.domain.template.TemplateSnapshotMode
import com.example.shoppingassistant.domain.template.presets.TemplatePreset
import com.example.shoppingassistant.domain.template.presets.TemplatePresetSource
import com.example.shoppingassistant.domain.template.presets.TemplatePresetsRepository
import com.example.shoppingassistant.domain.template.presets.generate.GenerateTemplatePresetsRequest
import com.example.shoppingassistant.domain.template.presets.generate.GenerateTemplatePresetsTask
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.GeoMode
import com.example.shoppingassistant.domain.model.OfferFacetType
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.TypedAttributeFilter
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.ValueFacet
import com.example.shoppingassistant.domain.model.toTypedAttributesGuess
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.domain.auth.GetCurrentUserUseCase
import com.example.shoppingassistant.domain.profile.GetProfileCacheTask
import com.example.shoppingassistant.domain.ingest.IngestStatus
import com.example.shoppingassistant.domain.ingest.SourceType
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferResult
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferStatus
import com.example.shoppingassistant.domain.facet.FacetCountMode
import com.example.shoppingassistant.domain.facet.FacetCountsQuery
import com.example.shoppingassistant.domain.facet.GetFacetCountsTask
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackMatchKeyFactory
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackTargetSpec
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.visualsearch.BindVisualSearchQueryUseCase
import com.example.shoppingassistant.domain.visualsearch.GetVisualSearchRecoveryPlanUseCase
import com.example.shoppingassistant.domain.visualsearch.NormalizeVisualSearchDraftUseCase
import com.example.shoppingassistant.domain.visualsearch.ReuseVisualSearchContextUseCase
import com.example.shoppingassistant.domain.visualsearch.TrackVisualSearchEventsUseCase
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBinderStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateProjection
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChip
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChipKind
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEnvelopeStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEvent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryActionType
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectedRegion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.feature.pages.main.context.buildCategoryDictionary
import com.example.shoppingassistant.feature.pages.main.context.attributeCatalogFor
import com.example.shoppingassistant.feature.pages.main.context.resolveProduct
import com.example.shoppingassistant.feature.pages.main.suggest.CategoryAnchorSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.HistoryTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.MainSuggestEngine
import com.example.shoppingassistant.feature.pages.main.suggest.MainSuggestItem
import com.example.shoppingassistant.feature.pages.main.suggest.AutoPresetSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.AutoTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.PresetTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.ProductAnchorSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.SectionHeaderSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.TextFixSuggest
import com.example.shoppingassistant.feature.pages.main.visualsearch.VisualSearchPreflightCategoryRouter
import com.example.shoppingassistant.feature.pages.main.ui.InputMode
import com.example.shoppingassistant.feature.pages.model.AttributeDef
import com.example.shoppingassistant.feature.pages.model.BoundSegment
import com.example.shoppingassistant.feature.pages.model.FilterStage
import com.example.shoppingassistant.feature.pages.model.ValueDef
import com.example.shoppingassistant.feature.pages.model.matchFreeQueryAttributeValue
import com.example.shoppingassistant.feature.pages.model.parseFreeQueryAttributes
import com.example.shoppingassistant.feature.pages.model.toFeatureParseableAttributeDefs
import com.example.shoppingassistant.feature.pages.model.Product
import com.example.shoppingassistant.feature.pages.results.ResultsOrigin
import com.example.shoppingassistant.feature.pages.results.ResultsPayload
import com.example.shoppingassistant.feature.pages.results.ResultsVisualContext
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import com.example.shoppingassistant.feature.pages.useroffers.sync.UserOffersSyncTask
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import com.example.shoppingassistant.feature.pages.common.buildSnapshotFromQuery
import com.example.shoppingassistant.feature.auth.DebugAuthStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import kotlin.math.round

class MainPageViewModel(
    private val repository: ProductRepository,
    private val rankService: RankService,
    private val liveValuesRepository: CatalogLiveValuesRepository,
    private val catalogRepository: CatalogReadRepository,
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val constraintsResolver: CatalogConstraintsResolver,
    private val getFacetCounts: GetFacetCountsTask,
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getProfileCache: GetProfileCacheTask,
    private val createdStore: UserOffersCreatedStore,
    private val userOffersSyncTask: UserOffersSyncTask,
    private val linkTemplateBuilder: LinkTemplateBuilderTask,
    private val linkTemplateMapper: LinkTemplateMapperTask,
    private val suggestEngine: MainSuggestEngine,
    private val templateIdTask: TemplateIdTask,
    private val templateHistoryRepository: TemplateHistoryRepository,
    private val trackRepository: TrackRepository,
    private val templatePresetsRepository: TemplatePresetsRepository,
    private val generateTemplatePresetsTask: GenerateTemplatePresetsTask,
    private val reuseVisualSearchContext: ReuseVisualSearchContextUseCase,
    private val normalizeVisualSearchDraft: NormalizeVisualSearchDraftUseCase,
    private val bindVisualSearchQuery: BindVisualSearchQueryUseCase,
    private val getVisualSearchRecoveryPlan: GetVisualSearchRecoveryPlanUseCase,
    private val trackVisualSearchEvents: TrackVisualSearchEventsUseCase,
    private val searchOffersWithFacets: SearchOffersWithFacetsUseCase,
    private val nearbyFiltersStorage: NearbyFiltersStorage,
    private val debugAuthStore: DebugAuthStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MainPageState())
    val state: StateFlow<MainPageState> = _state.asStateFlow()

    private val templateEngine: TemplateEngine = TemplateEngineTaskImpl(constraintsResolver)
    private var categoryRequiredIfRules: List<RequiredIfRule> = emptyList()
    private var categoryConstraints: List<CatalogConstraints> = emptyList()
    private var categoryDictionary: CategoryDictionary = buildCategoryDictionary(null, emptyList(), emptyList(), emptyList())
    @Volatile private var submitLeafAttributeDefsCache: Map<String, List<AttributeDef>>? = null
    private data class CategoryIndex(
        val byCode: Map<String, Category>,
        val parentCodes: Set<String>,
        val breadcrumbByCode: Map<String, String>,
    )
    private data class CategorySelectionResult(
        val codes: Set<String>,
        val chips: List<CategoryChipUi>,
        val leafCategoryCode: String?,
    )
    @Volatile private var categoryIndex: CategoryIndex? = null
    private val atomicIdentityKeys = setOf("brand", "model", "model_line", "product_name")
    private val submitIdentityKeys = setOf("brand", "model", "model_line", "product_name")
    private val expressRequiredKeys = setOf("price", "currency", "condition")
    private val linkRequiredKeys = setOf("price", "currency", "brand")
    private val breadcrumbSeparator = " → "
    private val breadcrumbSplitRegex = Regex("\\s*(?:→|/|\\u001A)\\s*")
    private val categorySelectorKey = "category"
    private var facetCountsJob: Job? = null
    private val attrFacetCountsJobs: MutableMap<String, Job> = mutableMapOf()
    private var suggestJob: Job? = null
    private var nearbyFetchJob: Job? = null
    private var nearbyCountJob: Job? = null
    private var lastFacetCountsRequestKey: String? = null
    private val lastAttrFacetRequestKey: MutableMap<String, String> = mutableMapOf()
    private var realUserId: String? = null

    init {
        viewModelScope.launch {
            val user = runCatching { getCurrentUser() }.getOrNull()
            val resolved = user?.id?.toString()
            realUserId = resolved
            val debugUser = debugAuthStore.user.value?.id
            reduce { it.copy(currentUserId = debugUser ?: resolved) }
            loadNearbyLocation(debugUser ?: resolved)
        }
        viewModelScope.launch {
            debugAuthStore.user.collect { debugUser ->
                reduce { it.copy(currentUserId = debugUser?.id ?: realUserId) }
            }
        }
        viewModelScope.launch {
            runCatching { ensureCategoryIndex() }
        }
        updateSuggestions("")
        refreshCategoryChips()
        refreshSearchQueryInsights()
        loadNearbyFilters()
    }

    fun setState(s: MainPageState) { _state.value = s }
    fun reduce(block: (MainPageState) -> MainPageState) = _state.update(block)

    private fun showCatalogError(message: String) {
        reduce { state -> state.copy(catalogErrorMessage = message) }
    }

    private fun clearCatalogError() {
        reduce { state ->
            if (state.catalogErrorMessage == null) state
            else state.copy(catalogErrorMessage = null)
        }
    }

    fun retryCatalogLoad() {
        clearCatalogError()
        categoryIndex = null
        submitLeafAttributeDefsCache = null
        viewModelScope.launch {
            ensureCategoryIndex()
        }
        refreshCategoryChips()
        refreshAttributes()
        refreshFacetCountsIfVisible()
        refreshAttrFacetCountsIfVisible()
    }

    private fun recordAtomicTemplate(base: UiTemplate) {
        val snapshot = buildSnapshot(base) ?: return
        recordTemplateUsage(snapshot)
        resetTemplate()
    }

    private suspend fun ensureCategoryIndex(): CategoryIndex {
        val cached = categoryIndex
        if (cached != null) return cached
        val categories = runCatching { catalogTaxonomyRepository.listCategories() }
            .onFailure { throwable ->
                showCatalogError(
                    throwable.message
                        ?: "Не удалось загрузить каталог. Доступен ограниченный режим.",
                )
            }
            .getOrElse { categoryIndex?.byCode?.values?.toList().orEmpty() }
        if (categories.isNotEmpty()) {
            clearCatalogError()
        }
        val index = buildCategoryIndex(categories)
        categoryIndex = index
        return index
    }

    private fun buildCategoryIndex(categories: List<Category>): CategoryIndex {
        val byCode = categories.associateBy { it.code }
        val parentCodes = categories.mapNotNull { it.parentCode }.toSet()
        val localeTag = java.util.Locale.getDefault().toLanguageTag()

        fun titleOf(code: String): String = byCode[code]?.displayTitle(locale = localeTag) ?: code
        fun breadcrumb(code: String): String {
            val path = ArrayList<String>()
            var cur: Category? = byCode[code]
            val seen = HashSet<String>()
            while (cur != null && seen.add(cur.code)) {
                val t = cur.displayTitle(locale = localeTag)
                path.add(t)
                cur = cur.parentCode?.let { byCode[it] }
            }
            return path.asReversed().joinToString(breadcrumbSeparator).ifBlank { titleOf(code) }
        }

        val breadcrumbByCode = categories.associate { it.code to breadcrumb(it.code) }
        return CategoryIndex(
            byCode = byCode,
            parentCodes = parentCodes,
            breadcrumbByCode = breadcrumbByCode,
        )
    }

    suspend fun resolveCategoryRedirect(categoryCode: String): String? {
        val normalizedCode = categoryCode.trim().uppercase(Locale.ROOT)
        if (normalizedCode.isBlank()) return null
        val index = ensureCategoryIndex()
        val resolution = runCatching {
            catalogTaxonomyRepository.resolveCategoryCode(normalizedCode)
        }.getOrNull() ?: return normalizedCode
        if (resolution.cycleDetected || resolution.unresolvedTarget != null) {
            return normalizedCode
        }
        return resolution.resolvedCode
    }

    private fun splitBreadcrumb(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()
        return value
            .split(breadcrumbSplitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun normalizeBreadcrumb(raw: String?): String? {
        val parts = splitBreadcrumb(raw)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(breadcrumbSeparator)
    }

    private fun breadcrumbTail(raw: String?): String =
        splitBreadcrumb(raw).lastOrNull().orEmpty()

    private fun categoryTrailFor(categoryCode: String?, fallback: String? = null): List<String> {
        val code = categoryCode?.takeIf { it.isNotBlank() }
        val fromIndex = code?.let { categoryIndex?.breadcrumbByCode?.get(it) }
        val raw = fromIndex ?: normalizeBreadcrumb(fallback) ?: code ?: return emptyList()
        return splitBreadcrumb(raw)
    }

    private fun resolveCategoryCodeFromBreadcrumb(value: String, index: CategoryIndex): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        index.byCode[trimmed]?.let { return it.code }
        val normalized = normalizeBreadcrumb(trimmed)?.lowercase() ?: return null
        return index.breadcrumbByCode.entries.firstOrNull { entry ->
            normalizeBreadcrumb(entry.value)?.lowercase() == normalized
        }?.key
    }

    private fun extractFreeTextRemainder(input: String, lockedTitle: String?): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        val header = lockedTitle?.trim().orEmpty()
        if (header.isNotBlank() && trimmed.startsWith(header, ignoreCase = true)) {
            return trimmed.removePrefix(header).trimStart()
        }
        return trimmed
    }

    private fun categoryBreadcrumbFor(categoryCode: String?): String? {
        val trail = categoryTrailFor(categoryCode)
        if (trail.isEmpty()) return null
        return trail.joinToString(breadcrumbSeparator)
    }

    private fun categoryLevelKeysFor(
        categoryCode: String?,
        fallback: String? = null,
        defs: List<AttributeDef> = emptyList(),
    ): List<String> {
        if (defs.any { it.key == categorySelectorKey }) return listOf(categorySelectorKey)
        val trail = categoryTrailFor(categoryCode, fallback)
        return if (trail.isNotEmpty()) listOf(categorySelectorKey) else emptyList()
    }

    private fun isCategoryLevelKey(key: String): Boolean =
        key.startsWith("category_level_") || key == categorySelectorKey

    private fun stripCategoryFilters(filters: Map<String, String>): Map<String, String> =
        filters.filterKeys { key -> !isCategoryLevelKey(key) }

    suspend fun parseSubmitAttributes(
        queryText: String,
        categoryCode: String?,
        baseFilters: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val normalizedQuery = SearchTextNormalizer.normalize(queryText)
        if (normalizedQuery.isBlank()) return emptyMap()

        val base = stripCategoryFilters(baseFilters)
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .toMap(LinkedHashMap())

        val normalizedCategory = categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return base
        val defs = runCatching {
            attributeCatalogFor(
                product = null,
                liveValuesRepository = liveValuesRepository,
                catalog = catalogRepository,
                constraintsResolver = constraintsResolver,
                categoryCode = normalizedCategory,
                selectedFilters = base,
            ).defs
        }.getOrElse { emptyList() }

        if (defs.isEmpty()) return base
        val parsed = parseAttributesFromFreeQuery(
            queryText = normalizedQuery,
            attributeDefs = defs,
        )
        if (parsed.isEmpty()) return base
        return (base + parsed).toMap(LinkedHashMap())
    }

    suspend fun inferLeafCategoryByFacets(
        queryText: String,
        baseFilters: Map<String, String> = emptyMap(),
    ): String? {
        val normalizedQuery = SearchTextNormalizer.normalize(queryText)
        if (normalizedQuery.isBlank()) return null

        val cleanedBase = stripCategoryFilters(baseFilters)
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .toMap(LinkedHashMap())

        val defsByLeaf = resolveLeafSubmitAttributeDefs()
        if (defsByLeaf.isEmpty()) return null

        data class Candidate(
            val categoryCode: String,
            val score: Int,
            val totalMatches: Int,
            val nonIdentityMatches: Int,
        )

        val candidates = defsByLeaf.mapNotNull { (leafCode, defs) ->
            val parsed = parseAttributesFromFreeQuery(
                queryText = normalizedQuery,
                attributeDefs = defs,
            )
            val merged = (cleanedBase + parsed)
            val totalMatches = merged.keys.count { key -> !isCategoryLevelKey(key) }
            val nonIdentityMatches = merged.keys.count { key ->
                !isCategoryLevelKey(key) && key !in submitIdentityKeys
            }
            if (totalMatches < 2 || nonIdentityMatches == 0) return@mapNotNull null
            val score = merged.keys
                .filterNot { key -> isCategoryLevelKey(key) }
                .sumOf { key -> if (key in submitIdentityKeys) 1 else 2 }
            Candidate(
                categoryCode = leafCode,
                score = score,
                totalMatches = totalMatches,
                nonIdentityMatches = nonIdentityMatches,
            )
        }.sortedWith(
            compareByDescending<Candidate> { it.score }
                .thenByDescending { it.nonIdentityMatches }
                .thenByDescending { it.totalMatches },
        )

        val winner = candidates.firstOrNull() ?: return null
        if (winner.score < 4) return null
        val runnerUp = candidates.getOrNull(1)
        if (runnerUp != null) {
            val scoreGap = winner.score - runnerUp.score
            if (scoreGap <= 0) return null
            if (
                scoreGap == 1 &&
                winner.nonIdentityMatches <= runnerUp.nonIdentityMatches &&
                winner.totalMatches <= runnerUp.totalMatches
            ) {
                return null
            }
        }
        return winner.categoryCode
    }

    private suspend fun resolveLeafSubmitAttributeDefs(): Map<String, List<AttributeDef>> {
        val cached = submitLeafAttributeDefsCache
        if (cached != null) return cached

        val index = ensureCategoryIndex()
        val leafCodes = index.byCode.keys
            .filterNot { code -> code in index.parentCodes }
            .sorted()
        val resolved = linkedMapOf<String, List<AttributeDef>>()

        leafCodes.forEach { leafCode ->
            val spec = runCatching { catalogRepository.getCategoryEffectiveSpec(leafCode) }.getOrNull()
                ?: return@forEach
            val defs = spec.toFeatureParseableAttributeDefs()
            if (defs.isNotEmpty()) {
                resolved[leafCode] = defs
            }
        }

        submitLeafAttributeDefsCache = resolved
        return resolved
    }

    private fun injectCategoryAttributes(
        attrs: Map<String, TemplateAttribute>,
        categoryCode: String?,
        fallback: String? = null,
    ): Map<String, TemplateAttribute> {
        val trail = categoryTrailFor(categoryCode, fallback)
        val cleaned = attrs.filterKeys { key -> !isCategoryLevelKey(key) }
        if (trail.isEmpty()) return cleaned
        val breadcrumb = trail.joinToString(breadcrumbSeparator)
        val categoryAttr = mapOf(
            categorySelectorKey to TemplateAttribute(
                code = categorySelectorKey,
                canonicalValue = breadcrumb,
                source = ValueSource.FromSuggestion,
            )
        )
        return cleaned + categoryAttr
    }

    private fun filterTemplateAttributes(
        attrs: Map<String, TemplateAttribute>,
        allowedKeys: Set<String>,
        categoryCode: String?,
        fallback: String? = null,
    ): Map<String, TemplateAttribute> {
        val kept = attrs.filterKeys { key -> key in allowedKeys }
        return injectCategoryAttributes(kept, categoryCode, fallback)
    }

    private fun leafCategoryBreadcrumbs(index: CategoryIndex): List<String> {
        return index.byCode.values
            .filter { category -> category.code !in index.parentCodes }
            .mapNotNull { category -> index.breadcrumbByCode[category.code] }
            .mapNotNull { normalizeBreadcrumb(it) }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    private fun withCategoryDefs(
        defs: List<AttributeDef>,
    ): List<AttributeDef> {
        val cleaned = defs.filterNot { def -> isCategoryLevelKey(def.key) }
        val index = categoryIndex ?: return cleaned
        val options = leafCategoryBreadcrumbs(index)
        if (options.isEmpty()) return cleaned
        val selector = AttributeDef(
            key = categorySelectorKey,
            title = "Категория",
            options = options,
            selectionOnly = true,
        )
        return listOf(selector) + cleaned
    }

    private suspend fun isAtomicCategory(categoryCode: String?): Boolean {
        val code = categoryCode?.takeIf { it.isNotBlank() } ?: return false
        val index = ensureCategoryIndex()
        if (code in index.parentCodes) return false
        val spec = runCatching { catalogRepository.getCategoryEffectiveSpec(code) }.getOrNull() ?: return false
        val attrCodes = spec.allAttributes()
            .map { it.code.lowercase() }
            .toSet()
        return atomicIdentityKeys.none { key -> key in attrCodes }
    }

    fun applyTemplateSnapshot(snapshot: TemplateSnapshot, openAttributes: Boolean = false) {
        val data = snapshot.data
        val lockedTitle = when (data.anchorType) {
            TemplateAnchorType.CATEGORY -> {
                val trail = categoryTrailFor(data.categoryCode)
                trail.lastOrNull() ?: data.categoryCode ?: data.anchorId
            }
            TemplateAnchorType.PRODUCT -> data.anchorId
        }.replace("\\s+".toRegex(), " ").trim()

        val baseInput = listOfNotNull(
            lockedTitle.takeIf { it.isNotBlank() },
            data.freeText?.takeIf { it.isNotBlank() },
        ).joinToString(" ").trim()

        val attrs = injectCategoryAttributes(
            data.attrs.associate { a ->
                a.key to TemplateAttribute(
                    code = a.key,
                    canonicalValue = a.value,
                    source = ValueSource.FromSuggestion,
                )
            },
            data.categoryCode,
            null,
        )

        val base = UiTemplate(
            inputText = baseInput,
            anchorType = data.anchorType,
            anchorId = data.anchorId,
            lockedTitle = lockedTitle,
            isLocked = true,
            categoryCode = data.categoryCode,
            attributes = attrs,
            mode = TemplateMode.SearchOrSubscribe,
        )
        rebuildDictionary(data.categoryCode, constraints = emptyList())
        val updated = recomputeTemplate(base)
        val chosenText = if (data.anchorType == TemplateAnchorType.CATEGORY) {
            categoryBreadcrumbFor(data.categoryCode) ?: lockedTitle
        } else lockedTitle
        applyTemplate(updated) { it.copy(chosenText = chosenText, product = null, templatePrefillDone = true) }
        refreshAttributes(product = null, categoryCode = data.categoryCode)
        recordTemplateUsage()
        if (openAttributes) {
            openAttributesSheet()
        }
    }
    private fun updateSuggestions(queryText: String = _state.value.template.inputText) {
        val s = _state.value
        if (s.template.isLocked) {
            suggestJob?.cancel()
            reduce { it.copy(suggestions = emptyList()) }
            return
        }
        val query = queryText
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            val items = runCatching { suggestEngine.suggest(query) }.getOrElse { emptyList() }
            if (_state.value.template.inputText != query || _state.value.template.isLocked) return@launch
            reduce { it.copy(suggestions = items) }
        }
    }

    fun onSuggestChosen(item: MainSuggestItem) {
        when (item) {
            is TextFixSuggest -> {
                onQueryChange(item.fixedText)
            }
            is ProductAnchorSuggest -> {
                val categoryHint = item.categoryCode ?: _state.value.template.categoryCode
                val brand = item.brand?.takeIf { it.isNotBlank() }
                val model = item.model?.takeIf { it.isNotBlank() }
                val product = if (brand != null && model != null) {
                    Product(
                        id = item.productId.toString(),
                        title = item.text,
                        brand = brand,
                        model = model,
                        categoryCode = categoryHint,
                    )
                } else {
                    val q = BrandModelRules.fromRaw(item.text)
                    if (q.brand.isBlank() || q.model.isBlank()) null
                    else Product(
                        id = item.productId.toString(),
                        title = item.text,
                        brand = q.brand,
                        model = q.model,
                        categoryCode = categoryHint,
                    )
                }
                onSuggestionChosen(item.text, product)
                reduce { it.copy(suggestions = emptyList()) }
            }
            is CategoryAnchorSuggest -> {
                onCategoryAnchorChosen(item)
                reduce { it.copy(suggestions = emptyList()) }
            }
            is HistoryTemplateSuggest -> {
                onHistoryTemplateChosen(item)
                reduce { it.copy(suggestions = emptyList()) }
            }
            is PresetTemplateSuggest -> {
                onPresetTemplateChosen(item)
                reduce { it.copy(suggestions = emptyList()) }
            }
            is AutoPresetSuggest -> {
                onAutoPresetChosen()
                reduce { it.copy(suggestions = emptyList()) }
            }
            is AutoTemplateSuggest -> {
                onAutoTemplateChosen(item)
                reduce { it.copy(suggestions = emptyList()) }
            }
            is SectionHeaderSuggest -> Unit
            else -> Unit
        }
    }

    private fun onCategoryAnchorChosen(item: CategoryAnchorSuggest) {
        val current = _state.value
        val raw = current.template.inputText
        val alias = item.matchedAlias
        val hasAlias = !alias.isNullOrBlank() && raw.contains(alias, ignoreCase = true)
        val remainder = if (hasAlias) stripFirstOccurrence(raw, alias) else ""
        val normalizedBreadcrumb = normalizeBreadcrumb(item.breadcrumb) ?: item.breadcrumb
        val tail = breadcrumbTail(normalizedBreadcrumb)
        val heading = tail.ifBlank { normalizedBreadcrumb }
        val desiredInput = listOfNotNull(
            heading.takeIf { it.isNotBlank() },
            remainder.takeIf { it.isNotBlank() },
        ).joinToString(" ").trim()

        val baseAttrs = injectCategoryAttributes(
            emptyMap(),
            item.categoryCode,
            normalizedBreadcrumb,
        )
        val base = UiTemplate(
            inputText = desiredInput,
            anchorType = TemplateAnchorType.CATEGORY,
            anchorId = item.categoryCode,
            lockedTitle = heading,
            isLocked = true,
            categoryCode = item.categoryCode,
            attributes = baseAttrs,
            mode = TemplateMode.SearchOrSubscribe,
        )
        viewModelScope.launch {
            if (isAtomicCategory(item.categoryCode)) {
                recordAtomicTemplate(base)
                return@launch
            }
            rebuildDictionary(item.categoryCode, constraints = emptyList())
            val updated = templateEngine.onInputTextChanged(base, desiredInput, categoryDictionary)
            applyTemplate(updated) { it.copy(chosenText = normalizedBreadcrumb, product = null, templatePrefillDone = false) }
            refreshAttributes(product = null, categoryCode = item.categoryCode)
            recordTemplateUsage()
        }
    }

    private fun onHistoryTemplateChosen(item: HistoryTemplateSuggest) {
        val data = item.entry.snapshot.data
        val lockedTitle = when (data.anchorType) {
            TemplateAnchorType.CATEGORY -> {
                val trail = categoryTrailFor(data.categoryCode)
                trail.lastOrNull() ?: data.categoryCode ?: data.anchorId
            }
            TemplateAnchorType.PRODUCT -> data.anchorId
        }.replace("\\s+".toRegex(), " ").trim()

        val baseInput = listOfNotNull(
            lockedTitle.takeIf { it.isNotBlank() },
            data.freeText?.takeIf { it.isNotBlank() },
        ).joinToString(" ").trim()

        val attrs = injectCategoryAttributes(
            data.attrs.associate { a ->
                a.key to TemplateAttribute(
                    code = a.key,
                    canonicalValue = a.value,
                    source = ValueSource.FromSuggestion,
                )
            },
            data.categoryCode,
            null,
        )

        val base = UiTemplate(
            inputText = baseInput,
            anchorType = data.anchorType,
            anchorId = data.anchorId,
            lockedTitle = lockedTitle,
            isLocked = true,
            categoryCode = data.categoryCode,
            attributes = attrs,
            mode = TemplateMode.SearchOrSubscribe,
        )
        rebuildDictionary(data.categoryCode, constraints = emptyList())
        val updated = recomputeTemplate(base)
        val chosenText = if (data.anchorType == TemplateAnchorType.CATEGORY) {
            categoryBreadcrumbFor(data.categoryCode) ?: lockedTitle
        } else lockedTitle
        applyTemplate(updated) { it.copy(chosenText = chosenText, product = null, templatePrefillDone = true) }
        val focusKey = if (item.status == com.example.shoppingassistant.domain.template.status.TemplateStatus.DRAFT) {
            item.firstErrorKey
        } else null
        refreshAttributes(product = null, categoryCode = data.categoryCode, focusOnKey = focusKey)
        recordTemplateUsage()
    }

    private fun onPresetTemplateChosen(item: PresetTemplateSuggest) {
        applyPreset(item.preset, item.text)
    }

    private fun onAutoPresetChosen() {
        val resolvedCategory = _state.value.template.categoryCode
            ?: _state.value.product?.categoryCode
        if (resolvedCategory.isNullOrBlank()) return
        viewModelScope.launch {
            val presets = runCatching {
                generateTemplatePresetsTask.generate(
                    GenerateTemplatePresetsRequest(
                        categoryCode = resolvedCategory,
                        limit = 20,
                        maxAttributes = 4,
                    )
                )
            }.getOrElse { emptyList() }
            val picked = presets.maxByOrNull { it.rank } ?: return@launch
            applyPreset(picked, picked.title)
        }
    }

    private fun onAutoTemplateChosen(item: AutoTemplateSuggest) {
        val heading = item.text.replace("\\s+".toRegex(), " ").trim()
        val resolvedCategoryCode = item.categoryCode ?: _state.value.template.categoryCode
        val placeholder = item.modelLine ?: item.model

        val baseAttrs = buildMap<String, TemplateAttribute> {
            item.brand.takeIf { it.isNotBlank() }?.let { b ->
                put(
                    "brand",
                    TemplateAttribute(
                        code = "brand",
                        canonicalValue = b,
                        source = ValueSource.FromSuggestion,
                    )
                )
            }
            item.model.takeIf { it.isNotBlank() }?.let { m ->
                put(
                    "model",
                    TemplateAttribute(
                        code = "model",
                        canonicalValue = m,
                        source = ValueSource.FromSuggestion,
                    )
                )
            }
            item.modelLine?.takeIf { it.isNotBlank() }?.let { line ->
                put(
                    "model_line",
                    TemplateAttribute(
                        code = "model_line",
                        canonicalValue = line,
                        source = ValueSource.FromSuggestion,
                    )
                )
            }
        }

        val decoratedAttrs = injectCategoryAttributes(
            attrs = baseAttrs,
            categoryCode = resolvedCategoryCode,
            fallback = item.categoryBreadcrumb,
        )

        val base = UiTemplate(
            inputText = heading,
            anchorType = TemplateAnchorType.PRODUCT,
            anchorId = heading,
            lockedTitle = heading,
            isLocked = true,
            categoryCode = resolvedCategoryCode,
            attributes = decoratedAttrs,
            mode = TemplateMode.SearchOrSubscribe,
        )
        viewModelScope.launch {
            if (isAtomicCategory(resolvedCategoryCode)) {
                recordAtomicTemplate(base)
                return@launch
            }
            rebuildDictionary(base.categoryCode, constraints = emptyList())
            val updated = templateEngine.onInputTextChanged(base, heading, categoryDictionary)
            applyTemplate(updated) {
                it.copy(
                    chosenText = heading,
                    product = null,
                    linkMeta = null,
                    photoUrl = null,
                    photoPlaceholderCategory = placeholder,
                    templatePrefillDone = false,
                )
            }
            refreshAttributes(product = null, categoryCode = resolvedCategoryCode)
            recordTemplateUsage()
        }
    }

    private fun applyPreset(preset: TemplatePreset, preferredTitle: String?) {
        val data = preset.snapshot
        val lockedTitle = (preferredTitle ?: "").ifBlank {
            when (data.anchorType) {
                TemplateAnchorType.CATEGORY -> {
                    val trail = categoryTrailFor(data.categoryCode)
                    trail.lastOrNull() ?: data.categoryCode ?: data.anchorId
                }
                TemplateAnchorType.PRODUCT -> data.anchorId
            }
        }.replace("\\s+".toRegex(), " ").trim()

        val baseInput = listOfNotNull(
            lockedTitle.takeIf { it.isNotBlank() },
            data.freeText?.takeIf { it.isNotBlank() },
        ).joinToString(" ").trim()

        val attrs = injectCategoryAttributes(
            data.attrs.associate { a ->
                a.key to TemplateAttribute(
                    code = a.key,
                    canonicalValue = a.value,
                    source = ValueSource.FromSuggestion,
                )
            },
            data.categoryCode,
            null,
        )

        val base = UiTemplate(
            inputText = baseInput,
            anchorType = data.anchorType,
            anchorId = data.anchorId,
            lockedTitle = lockedTitle,
            isLocked = true,
            categoryCode = data.categoryCode,
            attributes = attrs,
            mode = TemplateMode.SearchOrSubscribe,
        )
        viewModelScope.launch {
            if (isAtomicCategory(data.categoryCode)) {
                recordAtomicTemplate(base)
                return@launch
            }
            rebuildDictionary(data.categoryCode, constraints = emptyList())
            val updated = recomputeTemplate(base)
            val chosenText = if (data.anchorType == TemplateAnchorType.CATEGORY) {
                categoryBreadcrumbFor(data.categoryCode) ?: lockedTitle
            } else lockedTitle
            applyTemplate(updated) { it.copy(chosenText = chosenText, product = null, templatePrefillDone = true) }
            refreshAttributes(product = null, categoryCode = data.categoryCode)
            recordTemplateUsage()
        }
    }

    private fun stripFirstOccurrence(text: String, needle: String?): String {
        if (needle.isNullOrBlank()) return text.trim()
        val idx = text.indexOf(needle, ignoreCase = true)
        if (idx < 0) return text.trim()
        val out = (text.removeRange(idx, idx + needle.length))
            .replace("\\s+".toRegex(), " ")
            .trim()
        return out
    }

    private fun buildSuggestedInput(heading: String, raw: String): String {
        val normalizedHeading = heading.replace("\\s+".toRegex(), " ").trim()
        val normalizedRaw = raw.replace("\\s+".toRegex(), " ").trim()
        if (normalizedHeading.isBlank()) return normalizedRaw
        if (normalizedRaw.isBlank()) return normalizedHeading
        if (normalizedRaw.startsWith(normalizedHeading, ignoreCase = true)) return normalizedRaw
        val withoutHeading = stripFirstOccurrence(normalizedRaw, normalizedHeading)
        val tail = withoutHeading.takeIf { it.isNotBlank() && !it.equals(normalizedRaw, ignoreCase = true) }
        return listOfNotNull(
            normalizedHeading,
            tail?.takeIf { it.isNotBlank() }
        ).joinToString(" ").trim()
    }

    fun addCurrentTemplateToRecent(onResult: (Boolean) -> Unit = {}) {
        recordTemplateUsage(onResult = onResult)
    }

    private fun recordTemplateUsage(
        onResult: (Boolean) -> Unit = {},
    ) {
        val snapshot = buildSnapshot(_state.value.template)
        if (snapshot == null) {
            onResult(false)
            return
        }
        recordTemplateUsage(snapshot, onResult)
    }

    private fun recordTemplateUsage(
        snapshot: TemplateSnapshot,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            templateHistoryRepository.upsert(
                TemplateHistoryEntry(
                    snapshot = snapshot,
                    usedAtMillis = System.currentTimeMillis(),
                )
            )
            val history = runCatching { templateHistoryRepository.listRecent(limit = 20) }
                .getOrElse { emptyList() }
                .sortedByDescending { it.usedAtMillis }
            if (history.size > 10) {
                val toDrop = history.drop(10).map { it.snapshot.templateId }
                templateHistoryRepository.deleteByIds(toDrop)
            }
            refreshCategoryChips()
            refreshSearchQueryInsights()
            onResult(true)
        }
    }

    fun recordSearchHistory(
        queryText: String,
        query: NormalizedQuery?,
        categoryCode: String? = null,
    ) {
        val snapshot = buildSnapshotFromQuery(
            query = query,
            queryText = queryText,
            categoryCode = categoryCode,
            templateIdTask = templateIdTask,
        ) ?: return
        recordTemplateUsage(snapshot)
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            val history = runCatching { templateHistoryRepository.listRecent(limit = 100) }
                .getOrElse { emptyList() }
            if (history.isNotEmpty()) {
                templateHistoryRepository.deleteByIds(history.map { it.snapshot.templateId })
            }
            refreshCategoryChips()
            refreshSearchQueryInsights()
            updateSuggestions(_state.value.template.inputText)
        }
    }

    private fun refreshSearchQueryInsights() {
        viewModelScope.launch {
            val history = runCatching { templateHistoryRepository.listRecent(limit = 100) }
                .getOrElse { emptyList() }
                .sortedByDescending { it.usedAtMillis }

            val recent = history
                .mapNotNull { entry ->
                    val text = entry.snapshot.toSearchQueryText() ?: return@mapNotNull null
                    RecentSearchQueryUi(
                        text = text,
                        usedAtMillis = entry.usedAtMillis,
                    )
                }
                .distinctBy { item -> item.text.lowercase(Locale.getDefault()) }
                .take(20)

            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val startOfMonth = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val startOfYear = today.withDayOfYear(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            data class Counter(
                val text: String,
                var totalCount: Int = 0,
                var todayCount: Int = 0,
                var monthCount: Int = 0,
                var yearCount: Int = 0,
                var lastUsedAtMillis: Long = 0L,
            )

            val byNormalizedQuery = linkedMapOf<String, Counter>()
            history.forEach { entry ->
                val text = entry.snapshot.toSearchQueryText() ?: return@forEach
                val normalizedKey = normalizeSearchQueryKey(text)
                if (normalizedKey.isBlank()) return@forEach
                val counter = byNormalizedQuery.getOrPut(normalizedKey) { Counter(text = text) }
                counter.totalCount += 1
                if (entry.usedAtMillis >= startOfYear) counter.yearCount += 1
                if (entry.usedAtMillis >= startOfMonth) counter.monthCount += 1
                if (entry.usedAtMillis >= startOfToday) counter.todayCount += 1
                counter.lastUsedAtMillis = maxOf(counter.lastUsedAtMillis, entry.usedAtMillis)
            }

            val popular = byNormalizedQuery
                .values
                .map { counter ->
                    PopularSearchQueryUi(
                        text = counter.text,
                        totalCount = counter.totalCount,
                        todayCount = counter.todayCount,
                        monthCount = counter.monthCount,
                        yearCount = counter.yearCount,
                        lastUsedAtMillis = counter.lastUsedAtMillis,
                    )
                }
                .sortedWith(
                    compareByDescending<PopularSearchQueryUi> { item -> item.totalCount }
                        .thenByDescending { item -> item.lastUsedAtMillis },
                )
                .take(30)

            reduce {
                it.copy(
                    recentSearchQueries = recent,
                    popularSearchQueries = popular,
                )
            }
        }
    }

    private fun TemplateSnapshot.toSearchQueryText(): String? {
        val attrsByKey = data.attrs.associate { attr -> attr.key to attr.value }
        val brand = attrsByKey["brand"]?.trim().orEmpty()
        val model = attrsByKey["model"]?.trim().orEmpty()
        val titleFromAttrs = SearchTextNormalizer.normalize(
            listOfNotNull(
            brand.takeIf { value -> value.isNotBlank() },
            model.takeIf { value -> value.isNotBlank() },
            ).joinToString(" "),
        )
        if (titleFromAttrs.isNotBlank()) return titleFromAttrs

        val freeText = SearchTextNormalizer.normalize(data.freeText.orEmpty())
        if (freeText.isNotBlank()) return freeText

        if (data.anchorType == TemplateAnchorType.CATEGORY) {
            val categoryCode = data.categoryCode?.trim().orEmpty()
            if (categoryCode.isNotBlank()) {
                val trail = categoryTrailFor(categoryCode)
                if (trail.isNotEmpty()) {
                    return trail.last()
                }
            }
        }

        val anchor = SearchTextNormalizer.normalize(data.anchorId)
        return anchor.takeIf { value -> value.isNotBlank() }
    }

    private fun normalizeSearchQueryKey(raw: String): String =
        SearchTextNormalizer.normalizeKey(raw, Locale.getDefault())

    private fun refreshCategoryChips() {
        viewModelScope.launch {
            val categories = runCatching { catalogTaxonomyRepository.listCategories() }
                .onFailure { throwable ->
                    showCatalogError(
                        throwable.message
                            ?: "Не удалось обновить категории. Доступен кешированный режим.",
                    )
                }
                .getOrElse { categoryIndex?.byCode?.values?.toList().orEmpty() }
            if (categories.isNotEmpty()) {
                clearCatalogError()
            }
            val byCode = categories.associateBy { it.code }

            val history = runCatching { templateHistoryRepository.listRecent(limit = 30) }
                .getOrElse { emptyList() }
            val frequent = history
                .mapNotNull { it.snapshot.data.categoryCode }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .mapNotNull { entry ->
                    val category = byCode[entry.key] ?: return@mapNotNull null
                    CategoryChipUi(
                        code = category.code,
                        title = category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()),
                        breadcrumb = categoryBreadcrumbFor(category.code),
                    )
                }
                .take(6)

            val popularPresets = runCatching {
                templatePresetsRepository.listPresets(source = TemplatePresetSource.POPULAR, limit = 20)
            }.getOrElse { emptyList() }
            val popular = popularPresets
                .mapNotNull { it.snapshot.categoryCode }
                .distinct()
                .mapNotNull { code ->
                    val category = byCode[code] ?: return@mapNotNull null
                    CategoryChipUi(
                        code = category.code,
                        title = category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()),
                        breadcrumb = categoryBreadcrumbFor(category.code),
                    )
                }
                .take(6)

            reduce { it.copy(frequentCategories = frequent, popularCategories = popular) }
        }
    }

    fun onSearchCommitted() {
        recordTemplateUsage()
    }

    suspend fun resolveNearbyCategoryChips(codes: Set<String>): List<CategoryChipUi> =
        resolveCategorySelection(codes).chips

    suspend fun resolveNearbyCategorySelection(
        codes: Set<String>,
    ): Pair<Set<String>, List<CategoryChipUi>> {
        val result = resolveCategorySelection(codes)
        return result.codes to result.chips
    }

    suspend fun resolveNearbyLeafCategoryCode(codes: Set<String>): String? =
        resolveLeafCategoryCode(codes, ensureCategoryIndex())

    private suspend fun resolveCategorySelection(
        codes: Set<String>,
    ): CategorySelectionResult {
        if (codes.isEmpty()) {
            return CategorySelectionResult(
                codes = emptySet(),
                chips = emptyList(),
                leafCategoryCode = null,
            )
        }
        val index = ensureCategoryIndex()
        val compacted = codes.filter { code ->
            var parent = index.byCode[code]?.parentCode
            while (!parent.isNullOrBlank()) {
                if (codes.contains(parent)) return@filter false
                parent = index.byCode[parent]?.parentCode
            }
            true
        }.toSet()
        val items = compacted.map { code ->
            val category = index.byCode[code]
            CategoryChipUi(
                code = code,
                title = category?.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()) ?: code,
                breadcrumb = index.breadcrumbByCode[code],
            )
        }.sortedBy { it.title }
        val leafCategoryCode = resolveLeafCategoryCode(compacted, index)
        return CategorySelectionResult(
            codes = compacted,
            chips = items,
            leafCategoryCode = leafCategoryCode,
        )
    }

    private fun resolveLeafCategoryCode(
        codes: Set<String>,
        index: CategoryIndex,
    ): String? {
        if (codes.size != 1) return null
        val code = codes.first()
        return if (index.parentCodes.contains(code)) null else code
    }

    private fun clampNearbyRadius(value: Int): Int =
        value.coerceIn(1, MAX_NEARBY_RADIUS_KM)

    private fun coarseGeo(value: Double): Double {
        val factor = 10.0.pow(NEARBY_PRIVACY_DECIMALS.toDouble())
        return round(value * factor) / factor
    }

    private fun coarsenLatLon(lat: Double?, lon: Double?): Pair<Double?, Double?> {
        if (lat == null || lon == null) return null to null
        return coarseGeo(lat) to coarseGeo(lon)
    }

    private fun normalizePostedAt(value: NearbyPostedAt): NearbyPostedAt = value

    private fun normalizeNearbyBrands(brands: List<NearbyBrand>): List<NearbyBrand> {
        if (brands.isEmpty()) return emptyList()
        val deduped = LinkedHashMap<String, NearbyBrand>()
        brands.forEach { brand ->
            val rawName = brand.name.trim()
            if (rawName.isBlank()) return@forEach
            val normalizedName = Normalizer.normBrand(rawName) ?: rawName
            val id = brand.id.trim().ifBlank { Normalizer.key(normalizedName) }
            if (id.isBlank()) return@forEach
            deduped.putIfAbsent(id, NearbyBrand(id = id, name = normalizedName))
        }
        return deduped.values.toList()
    }

    private fun sanitizeNearbyFilters(
        filters: NearbyFiltersState,
        allowBrands: Boolean,
    ): NearbyFiltersState {
        val permission = _state.value.nearbyLocationPermission
        val allowNearby = permission != NearbyLocationPermission.DeniedTemporary &&
            permission != NearbyLocationPermission.DeniedPermanent
        val sanitizedScope = when (filters.locationScope) {
            NearbyScope.NEARBY -> if (allowNearby) NearbyScope.NEARBY else NearbyScope.CITY
            NearbyScope.CITY -> NearbyScope.CITY
            NearbyScope.COUNTRY -> NearbyScope.CITY
        }
        val fallbackCity = filters.selectedPlace?.city?.trim()?.takeIf { it.isNotBlank() }
            ?: _state.value.nearbyProfileCity
            ?: _state.value.nearbyUserLocation
        val sanitizedPlace = when (sanitizedScope) {
            NearbyScope.NEARBY -> null
            NearbyScope.CITY, NearbyScope.COUNTRY -> fallbackCity?.let { NearbyPlace(city = it) }
        }
        val updatedSort = if (sanitizedScope != NearbyScope.NEARBY && filters.sort == NearbySort.Distance) {
            NearbySort.Newest
        } else {
            filters.sort
        }
        val normalizedBrands = if (allowBrands) normalizeNearbyBrands(filters.brands) else emptyList()
        val normalizedDelivery = filters.delivery.filterNot { delivery -> delivery == NearbyDelivery.Meeting }.toSet()
        return filters.copy(
            locationScope = sanitizedScope,
            radiusKm = clampNearbyRadius(filters.radiusKm),
            selectedPlace = sanitizedPlace,
            brands = normalizedBrands,
            delivery = normalizedDelivery,
            postedAt = normalizePostedAt(filters.postedAt),
            sort = updatedSort,
        )
    }

    private fun applyNearbyFiltersResolved(
        filters: NearbyFiltersState,
        categoryChips: List<CategoryChipUi>,
        leafCategoryCode: String?,
        persist: Boolean,
        refresh: Boolean,
        resetPagination: Boolean = true,
    ) {
        reduce {
            val updated = it.copy(
                nearbyFilters = filters,
                nearbyCategoryChips = categoryChips,
                nearbyLeafCategoryCode = leafCategoryCode,
                nearbyBrandFacets = if (leafCategoryCode == null) emptyList() else it.nearbyBrandFacets,
                nearbyConditionFacets = if (leafCategoryCode == null) emptyList() else it.nearbyConditionFacets,
                nearbyDeliveryChannelFacets = if (leafCategoryCode == null) emptyList() else it.nearbyDeliveryChannelFacets,
                nearbyError = null,
                nearbyLastAction = null,
                nearbyDraftLoading = false,
            )
            if (resetPagination) {
                updated.copy(
                    nearbyFetchLimit = NEARBY_INITIAL_FETCH_LIMIT,
                    nearbyVisibleCount = NEARBY_INITIAL_VISIBLE_COUNT,
                    nearbyIsLoadingMore = false,
                )
            } else {
                updated
            }
        }
        if (persist) persistNearbyFilters(filters = filters)
        if (refresh) refreshNearbyFeed(filters = filters)
    }

    fun updateFeedCategories(
        codes: Set<String>,
        persist: Boolean = true,
        refresh: Boolean = true,
    ) {
        viewModelScope.launch {
            val result = resolveCategorySelection(codes)
            val categoryChanged = result.codes != _state.value.nearbyFilters.categoryCodes
            val base = _state.value.nearbyFilters.copy(
                categoryCodes = result.codes,
                brands = if (categoryChanged) emptyList() else _state.value.nearbyFilters.brands,
            )
            val updated = sanitizeNearbyFilters(base, allowBrands = result.leafCategoryCode != null)
            applyNearbyFiltersResolved(
                filters = updated,
                categoryChips = result.chips,
                leafCategoryCode = result.leafCategoryCode,
                persist = persist,
                refresh = refresh,
            )
        }
    }

    fun applyNearbyFilters(filters: NearbyFiltersState) {
        viewModelScope.launch {
            val result = resolveCategorySelection(filters.categoryCodes)
            val categoryChanged = result.codes != _state.value.nearbyFilters.categoryCodes
            val base = filters.copy(
                categoryCodes = result.codes,
                brands = if (categoryChanged) emptyList() else filters.brands,
            )
            val sanitized = sanitizeNearbyFilters(base, allowBrands = result.leafCategoryCode != null)
            val updated = sanitized.copy(categoryCodes = result.codes)
            applyNearbyFiltersResolved(
                filters = updated,
                categoryChips = result.chips,
                leafCategoryCode = result.leafCategoryCode,
                persist = true,
                refresh = true,
            )
        }
    }

    fun resetNearbyFilter(section: NearbyFilterSection) {
        val updated = _state.value.nearbyFilters.resetSection(section)
        applyNearbyFilters(updated)
    }

    private suspend fun loadNearbyLocation(userId: String?) {
        val profile = runCatching { getProfileCache(userId) }.getOrNull()
        val city = profile?.city?.trim()?.takeIf { it.isNotBlank() }
        val locale = Locale.getDefault()
        val country = locale.country.takeIf { it.isNotBlank() }
        reduce {
            val currentLocation = it.nearbyUserLocation ?: city
            val currentCountry = it.nearbyUserCountry ?: country
            it.copy(
                nearbyProfileCity = city,
                nearbyUserLocation = currentLocation,
                nearbyUserCountry = currentCountry,
            )
        }
        refreshNearbyFeed()
    }

    fun updateNearbyLocationPermission(status: NearbyLocationPermission) {
        reduce { it.copy(nearbyLocationPermission = status) }
        val current = _state.value.nearbyFilters
        if (status == NearbyLocationPermission.Granted) {
            if (current.locationScope != NearbyScope.NEARBY) {
                applyNearbyFilters(current.copy(locationScope = NearbyScope.NEARBY, selectedPlace = null))
            }
            return
        }
        if (current.locationScope == NearbyScope.NEARBY || current.locationScope == NearbyScope.COUNTRY) {
            val fallbackCity = current.selectedPlace?.city
                ?: _state.value.nearbyProfileCity
            val updatedSort =
                if (current.sort == NearbySort.Distance) NearbySort.Newest else current.sort
            val updated = current.copy(
                locationScope = NearbyScope.CITY,
                selectedPlace = fallbackCity?.let { NearbyPlace(city = it) } ?: current.selectedPlace,
                sort = updatedSort,
            )
            applyNearbyFilters(updated)
        }
    }

    fun updateNearbyCurrentLocation(
        city: String?,
        country: String?,
        lat: Double?,
        lon: Double?,
        addressLine: String? = null,
        replaceMissing: Boolean = false,
    ) {
        val trimmedCity = city?.trim()?.takeIf { it.isNotBlank() }
        val trimmedAddress = addressLine?.trim()?.takeIf { it.isNotBlank() }
        val trimmedCountry = country?.trim()?.takeIf { it.isNotBlank() }
        reduce {
            val clearedError = it.nearbyError?.takeIf { err -> err.kind != NearbyErrorKind.Location }
            it.copy(
                nearbyUserLocation = if (replaceMissing) trimmedCity else trimmedCity ?: it.nearbyUserLocation,
                nearbyUserAddressLine = if (replaceMissing) trimmedAddress else trimmedAddress ?: it.nearbyUserAddressLine,
                nearbyUserCountry = if (replaceMissing) trimmedCountry else trimmedCountry ?: it.nearbyUserCountry,
                nearbyUserLat = if (replaceMissing) lat else lat ?: it.nearbyUserLat,
                nearbyUserLon = if (replaceMissing) lon else lon ?: it.nearbyUserLon,
                nearbyError = clearedError,
            )
        }
        if (_state.value.nearbyFilters.locationScope == NearbyScope.NEARBY) {
            refreshNearbyFeed()
        }
    }

    fun setNearbyError(kind: NearbyErrorKind, message: String, action: NearbyRetryAction?) {
        reduce {
            it.copy(
                nearbyError = NearbyErrorState(kind = kind, message = message),
                nearbyLastAction = action,
                nearbyDraftLoading = false,
            )
        }
    }

    fun retryNearby(filters: NearbyFiltersState = _state.value.nearbyFilters) {
        when (_state.value.nearbyLastAction) {
            NearbyRetryAction.Feed -> refreshNearbyFeed(filters = filters)
            NearbyRetryAction.Count -> requestNearbyDraftCount(filters)
            null -> refreshNearbyFeed(filters = filters)
        }
    }

    private fun loadNearbyFilters() {
        viewModelScope.launch {
            val stored = runCatching { nearbyFiltersStorage.get() }.getOrNull()
            val rawFilters = stored?.filters ?: NearbyFiltersState()
            val legacyCodes = stored?.categoryCodes.orEmpty()
            val incomingCodes = if (rawFilters.categoryCodes.isNotEmpty()) rawFilters.categoryCodes else legacyCodes
            val result = resolveCategorySelection(incomingCodes)
            val filters = sanitizeNearbyFilters(
                rawFilters.copy(categoryCodes = result.codes),
                allowBrands = result.leafCategoryCode != null,
            )
            applyNearbyFiltersResolved(
                filters = filters,
                categoryChips = result.chips,
                leafCategoryCode = result.leafCategoryCode,
                persist = false,
                refresh = true,
            )
        }
    }

    private fun persistNearbyFilters(
        filters: NearbyFiltersState = _state.value.nearbyFilters,
    ) {
        viewModelScope.launch {
            runCatching {
                nearbyFiltersStorage.set(
                    NearbyFiltersAppliedState(
                        filters = filters,
                        categoryCodes = filters.categoryCodes,
                    )
                )
            }
        }
    }

    private fun refreshNearbyFeed(
        filters: NearbyFiltersState = _state.value.nearbyFilters,
        fetchLimit: Int = _state.value.nearbyFetchLimit,
        debounceMs: Long = NEARBY_FETCH_DEBOUNCE_MS,
    ) {
        nearbyFetchJob?.cancel()
        nearbyFetchJob = viewModelScope.launch {
            reduce { it.copy(nearbyError = null, nearbyLastAction = NearbyRetryAction.Feed) }
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            val criteria = buildNearbyCriteria(
                filters = filters,
                categoryCodes = filters.categoryCodes,
                leafCategoryCode = _state.value.nearbyLeafCategoryCode,
                currentLocation = _state.value.nearbyUserLocation,
                currentCountry = _state.value.nearbyUserCountry,
                currentLat = _state.value.nearbyUserLat,
                currentLon = _state.value.nearbyUserLon,
                limit = fetchLimit,
            )
            val includeRuntimeFacets = _state.value.nearbyLeafCategoryCode != null
            val requestedFacets = if (includeRuntimeFacets) {
                setOf(
                    OfferFacetType.BRAND,
                    OfferFacetType.CONDITION,
                    OfferFacetType.DELIVERY_CHANNEL,
                )
            } else {
                emptySet()
            }
            val request = OfferSearchWithFacetsRequest(
                criteria = criteria,
                facets = requestedFacets,
                excludeFacetFilters = requestedFacets,
            )
            val result = runCatching { searchOffersWithFacets(request) }
            if (result.isFailure) {
                setNearbyError(
                    kind = NearbyErrorKind.Network,
                    message = result.exceptionOrNull()?.message ?: "Не удалось обновить результаты",
                    action = NearbyRetryAction.Feed,
                )
                reduce { it.copy(nearbyIsLoadingMore = false) }
                return@launch
            }
            val payload = result.getOrThrow()
            val items = payload.items
            val brandFacets = if (includeRuntimeFacets) {
                payload.brandFacets.map { facet ->
                    NearbyBrandFacet(id = facet.id, name = facet.name, count = facet.count)
                }
            } else {
                emptyList()
            }
            val conditionFacets = if (includeRuntimeFacets) {
                payload.conditionFacets.map { it.toNearbyValueFacet() }
            } else {
                emptyList()
            }
            val deliveryChannelFacets = if (includeRuntimeFacets) {
                payload.deliveryChannelFacets.map { it.toNearbyValueFacet() }
            } else {
                emptyList()
            }
            reduce { state ->
                val visible = state.nearbyVisibleCount.coerceAtMost(items.size)
                state.copy(
                    nearbyOffers = items,
                    nearbyFoundCount = payload.total,
                    nearbyBrandFacets = brandFacets,
                    nearbyConditionFacets = conditionFacets,
                    nearbyDeliveryChannelFacets = deliveryChannelFacets,
                    nearbyError = null,
                    nearbyFetchLimit = fetchLimit,
                    nearbyVisibleCount = visible,
                    nearbyIsLoadingMore = false,
                )
            }
        }
    }

    fun loadMoreNearby() {
        val current = _state.value
        if (current.nearbyIsLoadingMore) return
        if (current.nearbyOffers.size < current.nearbyFetchLimit &&
            current.nearbyVisibleCount >= current.nearbyOffers.size
        ) {
            return
        }
        val nextLimit = current.nearbyFetchLimit + NEARBY_FETCH_STEP
        val nextVisible = (current.nearbyVisibleCount + NEARBY_FETCH_STEP).coerceAtMost(nextLimit)
        reduce {
            it.copy(
                nearbyFetchLimit = nextLimit,
                nearbyVisibleCount = nextVisible,
                nearbyIsLoadingMore = true,
            )
        }
        refreshNearbyFeed(filters = current.nearbyFilters, fetchLimit = nextLimit, debounceMs = 0L)
    }

    fun requestNearbyDraftCount(filters: NearbyFiltersState) {
        nearbyCountJob?.cancel()
        reduce {
            it.copy(
                nearbyDraftCount = null,
                nearbyDraftLoading = true,
                nearbyLastAction = NearbyRetryAction.Count,
                nearbyError = null,
            )
        }
        nearbyCountJob = viewModelScope.launch {
            delay(NEARBY_COUNT_DEBOUNCE_MS)
            val leafCategoryCode = resolveLeafCategoryCode(filters.categoryCodes, ensureCategoryIndex())
            val sanitized = sanitizeNearbyFilters(filters, allowBrands = leafCategoryCode != null)
            val criteria = buildNearbyCriteria(
                filters = sanitized,
                categoryCodes = sanitized.categoryCodes,
                leafCategoryCode = leafCategoryCode,
                currentLocation = _state.value.nearbyUserLocation,
                currentCountry = _state.value.nearbyUserCountry,
                currentLat = _state.value.nearbyUserLat,
                currentLon = _state.value.nearbyUserLon,
            ).copy(limit = NEARBY_COUNT_LIMIT)
            val request = OfferSearchWithFacetsRequest(criteria = criteria)
            val result = runCatching { searchOffersWithFacets(request) }
            if (result.isFailure) {
                setNearbyError(
                    kind = NearbyErrorKind.Network,
                    message = result.exceptionOrNull()?.message ?: "Не удалось получить количество",
                    action = NearbyRetryAction.Count,
                )
                return@launch
            }
            val payload = result.getOrThrow()
            reduce {
                it.copy(
                    nearbyDraftCount = payload.total,
                    nearbyDraftLoading = false,
                    nearbyError = null,
                )
            }
        }
    }

    private fun buildNearbyCriteria(
        filters: NearbyFiltersState,
        categoryCodes: Set<String>,
        leafCategoryCode: String?,
        currentLocation: String?,
        currentCountry: String?,
        currentLat: Double?,
        currentLon: Double?,
        limit: Int = NEARBY_INITIAL_FETCH_LIMIT,
    ): OfferSearchCriteria {
        val locale = Locale.getDefault()
        val attrs = emptyMap<String, String>()
        val condition = filters.condition.value.trim().takeIf {
            filters.condition != NearbyCondition.Any && it.isNotBlank()
        }
        val deliveryChannels = filters.delivery
            .mapNotNull { delivery -> delivery.value.trim().takeIf { it.isNotBlank() } }
            .distinct()
        val brandIds = filters.brands
            .mapNotNull { brand -> brand.id.trim().takeIf { it.isNotBlank() } }
            .distinct()
        val brandName = filters.brands
            .firstOrNull { brand -> brand.name.trim().isNotBlank() }
            ?.name
            ?.trim()
        val resolvedScope = if (filters.locationScope == NearbyScope.COUNTRY) NearbyScope.CITY else filters.locationScope
        val canUseGeo = resolvedScope == NearbyScope.NEARBY && currentLat != null && currentLon != null
        val (coarseLat, coarseLon) = if (canUseGeo) coarsenLatLon(currentLat, currentLon) else null to null
        val resolvedSort =
            if (resolvedScope != NearbyScope.NEARBY && filters.sort == NearbySort.Distance) {
                NearbySort.Newest
            } else {
                filters.sort
            }
        val sort = when (resolvedSort) {
            NearbySort.Distance -> if (canUseGeo) OfferSort.DISTANCE_ASC else OfferSort.NEWEST
            NearbySort.Newest -> OfferSort.NEWEST
            NearbySort.PriceAsc -> OfferSort.PRICE_ASC
            NearbySort.PriceDesc -> OfferSort.PRICE_DESC
        }
        val categoryCode = leafCategoryCode
            ?: categoryCodes.singleOrNull()?.takeIf { it.isNotBlank() }
        val radius = if (resolvedScope == NearbyScope.NEARBY) clampNearbyRadius(filters.radiusKm) else null
        val selectedCity = filters.selectedPlace?.city?.trim()?.takeIf { it.isNotBlank() }
        val resolvedLocation = when (resolvedScope) {
            NearbyScope.NEARBY -> if (canUseGeo) null else currentLocation?.trim()?.takeIf { it.isNotBlank() }
            NearbyScope.CITY, NearbyScope.COUNTRY -> selectedCity ?: currentLocation?.trim()?.takeIf { it.isNotBlank() }
        }
        val updatedAfterMs = filters.postedAt.days?.let { days ->
            System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        }
        val sellerCountryCode = when (filters.locationScope) {
            NearbyScope.COUNTRY -> {
                filters.selectedPlace?.country?.trim()?.takeIf { it.isNotBlank() }
                    ?: currentCountry?.trim()?.takeIf { it.isNotBlank() }
                    ?: locale.country.takeIf { it.isNotBlank() }
            }
            NearbyScope.NEARBY -> if (!canUseGeo) {
                currentCountry?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            NearbyScope.CITY -> null
        }
        val sellerCity = when (resolvedScope) {
            NearbyScope.NEARBY -> if (canUseGeo) null else resolvedLocation
            NearbyScope.CITY -> resolvedLocation
            NearbyScope.COUNTRY -> null
        }
        val location = when (resolvedScope) {
            NearbyScope.NEARBY -> if (canUseGeo) null else resolvedLocation
            NearbyScope.CITY -> resolvedLocation
            NearbyScope.COUNTRY -> null
        }
        val attributeFilters = buildMap<String, TypedAttributeFilter> {
            condition?.let { selectedCondition ->
                put(
                    "condition",
                    TypedAttributeFilter(
                        op = TypedAttributeOperator.EQ,
                        value = TypedAttributeValue.Text(selectedCondition),
                    ),
                )
            }
            if (deliveryChannels.isNotEmpty()) {
                put(
                    "delivery_channel",
                    TypedAttributeFilter(
                        op = TypedAttributeOperator.IN,
                        values = deliveryChannels.map { channel -> TypedAttributeValue.Text(channel) },
                    ),
                )
            }
        }

        return OfferSearchCriteria(
            brand = brandName,
            model = null,
            brands = brandIds,
            categoryCode = categoryCode,
            priceMin = filters.priceMin?.toDouble(),
            priceMax = filters.priceMax?.toDouble(),
            location = location,
            radiusKm = radius,
            centerLat = coarseLat,
            centerLon = coarseLon,
            geoMode = if (canUseGeo) GeoMode.RADIUS else GeoMode.CITY_FALLBACK,
            deliverableOnly = false,
            condition = condition,
            conditions = listOfNotNull(condition),
            deliveryChannels = deliveryChannels,
            attributes = attrs.toTypedAttributesGuess(),
            attributeFilters = attributeFilters,
            userCountry = locale.country.takeIf { it.isNotBlank() },
            userLanguage = locale.language.takeIf { it.isNotBlank() },
            limit = limit,
            sort = sort,
            sellerCity = sellerCity,
            sellerCountryCode = sellerCountryCode,
            updatedAfterMs = updatedAfterMs,
        )
    }

    fun subscribeToCurrentTemplate(onResult: (created: Boolean) -> Unit = {}) {
        val current = _state.value.template
        val snapshot = buildSnapshot(current)
        if (snapshot == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val heading = current.lockedTitle?.trim().takeIf { !it.isNullOrBlank() }
                ?: categoryBreadcrumbFor(current.categoryCode)
                ?: snapshot.data.anchorId
            val freeText = snapshot.data.freeText?.trim().takeIf { !it.isNullOrBlank() }
            val query = listOfNotNull(
                heading?.takeIf { it.isNotBlank() },
                freeText?.takeIf { it.isNotBlank() },
            ).joinToString(" ").trim()
                .ifBlank { current.inputText.trim() }

            if (query.isBlank()) {
                onResult(false)
                return@launch
            }

            val normalizedCategoryCode = current.categoryCode
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
            val selectedFilters = stripCategoryFilters(current.asSelectedFilters())
            val explicitBrand = selectedFilters.entries
                .firstOrNull { (key, _) -> key.equals("brand", ignoreCase = true) }
                ?.value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val explicitModel = selectedFilters.entries
                .firstOrNull { (key, _) -> key.equals("model", ignoreCase = true) }
                ?.value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val parsedQuery = BrandModelRules.fromRaw(query, Normalization.normalizeAttrs(selectedFilters))
            val trackMatchKey = TrackMatchKeyFactory.fromBrandModel(
                brand = explicitBrand ?: parsedQuery.brand,
                model = explicitModel ?: parsedQuery.model,
            )
            if (normalizedCategoryCode == null) {
                onResult(false)
                return@launch
            }

            val trackExtra = LinkedHashMap<String, String>()
            selectedFilters.forEach { (rawKey, rawValue) ->
                val key = rawKey.trim()
                val value = rawValue.trim()
                if (key.isBlank() || value.isBlank()) return@forEach
                trackExtra[key] = value
            }
            val normalizedBrandForTarget = (explicitBrand ?: parsedQuery.brand)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val normalizedModelForTarget = (explicitModel ?: parsedQuery.model)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (normalizedBrandForTarget != null) {
                trackExtra.putIfAbsent("brand", normalizedBrandForTarget)
            }
            if (normalizedModelForTarget != null) {
                trackExtra.putIfAbsent("model", normalizedModelForTarget)
            }
            val trackFilters = TrackFilters(
                extra = trackExtra.entries
                    .sortedBy { (key, _) -> key.lowercase(Locale.ROOT) }
                    .map { it.toPair() }
                    .toMap(LinkedHashMap<String, String>()),
            )
            val hasTargetAttributes = trackFilters.extra.isNotEmpty() || trackMatchKey != null
            val trackType = if (hasTargetAttributes) TrackType.PRODUCT else TrackType.CATEGORY
            val effectiveMatchKey = trackMatchKey.takeIf { trackFilters.extra.isEmpty() }
            val targetQueryText = query.trim().takeIf { it.isNotBlank() }
            val targetSpec = TrackTargetSpec(
                categoryCode = normalizedCategoryCode,
                attributes = trackFilters.extra,
                matchKey = effectiveMatchKey,
                queryText = targetQueryText,
                schemaVersion = 1,
                taxonomyVersion = com.example.shoppingassistant.domain.catalog.CatalogDataVersion.current,
                locale = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() },
            )

            val normalizedExtra = trackFilters.extra
                .mapKeys { it.key.trim().lowercase(Locale.ROOT) }
                .mapValues { it.value.trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
            val existing = runCatching { trackRepository.listTracks() }
                .getOrElse { emptyList() }
                .firstOrNull { track ->
                    if (track.type != trackType) return@firstOrNull false
                    val sameCategory =
                        track.categoryCode?.trim()?.uppercase(Locale.ROOT) == normalizedCategoryCode
                    if (!sameCategory) return@firstOrNull false

                    val trackExtra = track.target.spec?.attributes.orEmpty()
                        .mapKeys { it.key.trim().lowercase(Locale.ROOT) }
                        .mapValues { it.value.trim() }
                        .filterKeys { it.isNotBlank() }
                        .filterValues { it.isNotBlank() }
                        .toSortedMap()
                    if (trackExtra != normalizedExtra) return@firstOrNull false
                    val normalizedTrackQueryText = track.target.spec?.queryText
                        ?.replace("\\s+".toRegex(), " ")
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()
                    val normalizedTargetQueryText = targetQueryText
                        ?.replace("\\s+".toRegex(), " ")
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()
                    if (normalizedTrackQueryText != normalizedTargetQueryText) return@firstOrNull false

                    if (normalizedExtra.isNotEmpty()) {
                        true
                    } else {
                        TrackMatchKeyFactory.parse(track.target.spec?.matchKey) ==
                            TrackMatchKeyFactory.parse(effectiveMatchKey)
                    }
                }
            if (existing != null) {
                onResult(false)
                return@launch
            }

            val now = System.currentTimeMillis()
            val created = runCatching {
                trackRepository.upsertTrack(
                    Track(
                        id = "new",
                        title = heading.takeIf { !it.isNullOrBlank() }?.take(80) ?: query.take(80),
                        categoryCode = normalizedCategoryCode,
                        type = trackType,
                        target = if (trackType == TrackType.PRODUCT) {
                            TrackTarget(
                                spec = targetSpec,
                                categoryCode = normalizedCategoryCode,
                                attributes = trackFilters.extra,
                                matchKey = effectiveMatchKey,
                                queryText = targetQueryText,
                                schemaVersion = 1,
                                taxonomyVersion = com.example.shoppingassistant.domain.catalog.CatalogDataVersion.current,
                                locale = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() },
                            )
                        } else {
                            TrackTarget(
                                spec = targetSpec,
                                categoryCode = normalizedCategoryCode,
                                attributes = trackFilters.extra,
                                queryText = targetQueryText,
                                schemaVersion = 1,
                                taxonomyVersion = com.example.shoppingassistant.domain.catalog.CatalogDataVersion.current,
                                locale = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() },
                            )
                        },
                        filters = trackFilters,
                        alertRules = emptyList(),
                        state = TrackState.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }.isSuccess
            onResult(created)
        }
    }

    private fun buildSnapshot(template: UiTemplate): TemplateSnapshot? {
        val anchorType = template.anchorType ?: return null
        if (!template.isLocked) return null

        val data = TemplateSnapshotData(
            anchorType = anchorType,
            anchorId = template.anchorId ?: template.lockedTitle ?: template.inputText,
            categoryCode = template.categoryCode,
            attrs = template.attributes.values
                .asSequence()
                .filter { !isCategoryLevelKey(it.code) }
                .mapNotNull { a -> a.canonicalValue?.takeIf { it.isNotBlank() }?.let { v -> TemplateSnapshotAttr(key = a.code, value = v) } }
                .sortedBy { it.key.lowercase() }
                .toList(),
            freeText = template.lockedTitle?.let { lt ->
                template.inputText.removePrefix(lt).trim().takeIf { it.isNotBlank() }
            } ?: template.inputText.trim().takeIf { it.isNotBlank() },
            mode = when (template.mode) {
                TemplateMode.OfferFromLink -> TemplateSnapshotMode.OFFER
                TemplateMode.ExpressFromPhoto, TemplateMode.OfferFromVoice -> TemplateSnapshotMode.EXPRESS
                TemplateMode.SearchOrSubscribe -> TemplateSnapshotMode.SEARCH
            },
            schemaVersion = 1,
            taxonomyVersion = com.example.shoppingassistant.domain.catalog.CatalogDataVersion.current,
            locale = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() },
        )
        val id = templateIdTask.computeId(data)
        return TemplateSnapshot(data = data, templateId = id)
    }

    private fun rebuildDictionary(
        categoryCode: String?,
        defs: List<AttributeDef> = _state.value.attributeDefs,
        requiredIfRules: List<RequiredIfRule> = categoryRequiredIfRules,
        constraints: List<CatalogConstraints> = categoryConstraints,
    ) {
        categoryRequiredIfRules = requiredIfRules
        categoryConstraints = constraints
        categoryDictionary = buildCategoryDictionary(categoryCode, defs, requiredIfRules, constraints)
    }

    private fun applyTemplate(
        template: UiTemplate,
        block: (MainPageState) -> MainPageState = { it },
    ) {
        val filters = template.asSelectedFilters()
        val isActive = template.isLocked || template.mode != TemplateMode.SearchOrSubscribe
        val shouldDropSearchState = !template.isLocked && template.mode == TemplateMode.SearchOrSubscribe
        _state.update { state ->
            val forcedActive = isActive || state.chosenText != null
            block(
                state.copy(
                    template = template,
                    queryText = template.inputText,
                    selectedFilters = if (shouldDropSearchState) emptyMap() else filters,
                    requiredKeys = template.requiredKeys,
                    errorKeys = template.errorKeys,
                    errorMessages = template.errorMessages,
                    linkMeta = template.linkMeta,
                    photoUrl = template.primaryPhotoUrl,
                    photoUrls = template.photoUrls,
                    templateMode = template.mode,
                    isTemplateActive = forcedActive,
                    product = if (shouldDropSearchState) null else state.product,
                    attributeDefs = if (shouldDropSearchState) emptyList() else state.attributeDefs,
                    attributeLiveValuesByKey = if (shouldDropSearchState) emptyMap() else state.attributeLiveValuesByKey,
                    foundCount = if (shouldDropSearchState) null else state.foundCount,
                    prefetchedOffers = if (shouldDropSearchState) emptyList() else state.prefetchedOffers,
                )
            )
        }
        refreshFacetCountsIfVisible()
        refreshAttrFacetCountsIfVisible()
    }

    private fun recomputeTemplate(template: UiTemplate): UiTemplate =
        templateEngine.onInputTextChanged(template, template.inputText, categoryDictionary)

    private fun updateVisualSearchSession(
        transform: (VisualSearchSessionState) -> VisualSearchSessionState,
    ) {
        reduce { state ->
            state.copy(
                visualSearch = synchronizeVisualSearchSession(transform(state.visualSearch)),
            )
        }
    }

    private fun synchronizeVisualSearchSession(
        session: VisualSearchSessionState,
    ): VisualSearchSessionState {
        val effectiveAsset = session.asset ?: session.capturedAssets.lastOrNull()
        if (effectiveAsset == null) {
            return session.copy(
                selectedRegion = null,
                preflight = null,
            )
        }
        if (session.asset == null) {
            return synchronizeVisualSearchSession(
                session.copy(
                    asset = effectiveAsset,
                ),
            )
        }
        val selectedRegion = when (session.selectionMode) {
            VisualSearchSelectionMode.MANUAL_CROP -> session.selectedRegion ?: defaultVisualSearchRegion()
            VisualSearchSelectionMode.AUTO_TARGET -> session.selectedRegion ?: session.insight?.suggestedRegion
            VisualSearchSelectionMode.WHOLE_FRAME -> null
        }
        val normalized = session.copy(
            selectedRegion = selectedRegion,
        )
        return normalized.copy(
            preflight = buildVisualSearchPreflightUi(normalized),
        )
    }

    fun onInputModeChange(mode: InputMode) {
        reduce {
            it.copy(
                inputMode = mode,
                showFilterSheet = false,
                filterStage = FilterStage.ATTRS,
                currentAttrKey = null,
                facetCountsKey = null,
                facetCounts = null,
                facetCountsLoading = false,
            )
        }
    }

    fun openVisualSearchEntry() {
        val sessionId = UUID.randomUUID().toString()
        reduce { state ->
            state.copy(
                inputMode = InputMode.Photo,
                visualSearch = synchronizeVisualSearchSession(
                    VisualSearchSessionState(
                        visible = true,
                        sessionId = sessionId,
                        step = VisualSearchSessionStep.Source,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                ),
            )
        }
        trackVisualSearchEvent(
            sessionId = sessionId,
            name = "visual_search_entry_tap",
            payload = mapOf("placement" to "search_hub_card"),
        )
    }

    fun dismissVisualSearch() {
        reduce { state ->
            state.copy(
                inputMode = InputMode.Text,
                visualSearch = VisualSearchSessionState(),
            )
        }
    }

    fun onVisualSearchAssetPicked(
        source: VisualSearchSource,
        asset: VisualSearchAssetUi,
        captureMode: VisualSearchCaptureMode = VisualSearchCaptureMode.IMAGE,
        insight: VisualSearchInsightUi? = null,
    ) {
        val current = _state.value.visualSearch
        val sessionId = current.sessionId ?: UUID.randomUUID().toString()
        val resolvedCategoryCode = current.selectedCategoryCode
            ?: insight?.takeIf { candidate -> candidate.promoteSuggestedCategory }?.suggestedCategoryCode
        val resolvedCategoryTitle = current.selectedCategoryTitle
            ?: insight?.takeIf { candidate -> candidate.promoteSuggestedCategory }?.suggestedCategoryTitle
            ?: resolveVisualSearchCategoryTitle(resolvedCategoryCode)
        val resolvedSelectionMode = when {
            current.selectionMode == VisualSearchSelectionMode.MANUAL_CROP && current.selectedRegion != null ->
                VisualSearchSelectionMode.MANUAL_CROP
            captureMode == VisualSearchCaptureMode.IMAGE && insight?.objectLabel != null ->
                VisualSearchSelectionMode.AUTO_TARGET
            captureMode == VisualSearchCaptureMode.IMAGE ->
                VisualSearchSelectionMode.AUTO_TARGET
            else ->
                VisualSearchSelectionMode.WHOLE_FRAME
        }
        updateVisualSearchSession { session ->
            val nextCapturedAssets = appendVisualSearchCapturedAsset(
                current = session.capturedAssets,
                incoming = asset,
            )
            session.copy(
                visible = true,
                sessionId = sessionId,
                step = VisualSearchSessionStep.Source,
                source = if (captureMode == VisualSearchCaptureMode.BARCODE) {
                    VisualSearchSource.BARCODE_MODE
                } else {
                    source
                },
                asset = asset,
                capturedAssets = nextCapturedAssets,
                captureMode = captureMode,
                selectionMode = resolvedSelectionMode,
                selectedRegion = if (resolvedSelectionMode == VisualSearchSelectionMode.AUTO_TARGET) {
                    insight?.suggestedRegion
                } else {
                    session.selectedRegion
                },
                selectedCategoryCode = resolvedCategoryCode,
                selectedCategoryTitle = resolvedCategoryTitle,
                insight = insight,
                binderStatus = null,
                isSubmitting = false,
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
                pendingResultsPayload = null,
            )
        }
        trackVisualSearchEvent(
            sessionId = sessionId,
            name = "visual_search_source_selected",
            payload = mapOf(
                "source" to source.name.lowercase(Locale.ROOT),
                "captureMode" to captureMode.name.lowercase(Locale.ROOT),
                "has_category" to (!resolvedCategoryCode.isNullOrBlank()).toString(),
            ),
        )
    }

    fun updateVisualSearchCaptureMode(mode: VisualSearchCaptureMode) {
        val sessionId = _state.value.visualSearch.sessionId
        updateVisualSearchSession { session ->
            session.copy(
                captureMode = mode,
                selectionMode = if (mode == VisualSearchCaptureMode.IMAGE) {
                    if (session.selectionMode == VisualSearchSelectionMode.MANUAL_CROP) {
                        session.selectionMode
                    } else {
                        VisualSearchSelectionMode.AUTO_TARGET
                    }
                } else {
                    VisualSearchSelectionMode.WHOLE_FRAME
                },
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
            )
        }
        sessionId?.let {
            trackVisualSearchEvent(
                sessionId = it,
                name = "visual_search_capture_mode_changed",
                payload = mapOf("captureMode" to mode.name.lowercase(Locale.ROOT)),
            )
        }
    }

    fun updateVisualSearchIntent(intent: VisualSearchIntent) {
        val sessionId = _state.value.visualSearch.sessionId
        updateVisualSearchSession { session ->
            session.copy(
                intent = intent,
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
            )
        }
        sessionId?.let {
            trackVisualSearchEvent(
                sessionId = it,
                name = "visual_search_intent_changed",
                payload = mapOf("intent" to intent.name.lowercase(Locale.ROOT)),
            )
        }
    }

    fun updateVisualSearchSelectionMode(mode: VisualSearchSelectionMode) {
        val sessionId = _state.value.visualSearch.sessionId
        updateVisualSearchSession { session ->
            session.copy(
                selectionMode = mode,
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
            )
        }
        sessionId?.let {
            trackVisualSearchEvent(
                sessionId = it,
                name = "visual_search_selection_mode_changed",
                payload = mapOf("selectionMode" to mode.name.lowercase(Locale.ROOT)),
            )
        }
    }

    fun selectVisualSearchRegion(region: VisualSearchRegionUi?) {
        val sessionId = _state.value.visualSearch.sessionId
        updateVisualSearchSession { session ->
            session.copy(
                selectionMode = VisualSearchSelectionMode.MANUAL_CROP,
                selectedRegion = region ?: defaultVisualSearchRegion(),
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
            )
        }
        sessionId?.let {
            trackVisualSearchEvent(
                sessionId = it,
                name = "visual_search_region_selected",
                payload = mapOf("region" to (region?.label ?: "default")),
            )
        }
    }

    fun selectVisualSearchCategory(
        categoryCode: String,
        categoryTitle: String? = null,
    ) {
        val normalizedCode = categoryCode.trim()
        if (normalizedCode.isEmpty()) return
        val sessionId = _state.value.visualSearch.sessionId
        updateVisualSearchSession { session ->
            session.copy(
                selectedCategoryCode = normalizedCode,
                selectedCategoryTitle = categoryTitle
                    ?.takeIf { it.isNotBlank() }
                    ?: resolveVisualSearchCategoryTitle(normalizedCode),
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
            )
        }
        sessionId?.let {
            trackVisualSearchEvent(
                sessionId = it,
                name = "visual_search_category_selected",
                payload = mapOf("categoryCode" to normalizedCode),
            )
        }
    }

    fun clearVisualSearchMessage() {
        reduce { state ->
            state.copy(
                visualSearch = state.visualSearch.copy(
                    errorMessage = null,
                    recoveryMessage = null,
                    recoveryActions = emptyList(),
                ),
            )
        }
    }

    fun consumePendingVisualResults() {
        reduce { state ->
            state.copy(
                visualSearch = state.visualSearch.copy(
                    pendingResultsPayload = null,
                ),
            )
        }
    }

    fun applyVisualSearchRecoveryAction(actionType: VisualSearchRecoveryActionType) {
        val current = _state.value.visualSearch
        when (actionType) {
            VisualSearchRecoveryActionType.RETAKE_PHOTO -> {
                updateVisualSearchSession { session ->
                    session.copy(
                        step = VisualSearchSessionStep.Source,
                        asset = null,
                        capturedAssets = emptyList(),
                        source = null,
                        insight = null,
                        binderStatus = null,
                        isSubmitting = false,
                        errorMessage = null,
                        recoveryMessage = null,
                        recoveryActions = emptyList(),
                        pendingResultsPayload = null,
                    )
                }
            }

            VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY -> {
                updateVisualSearchSession { session ->
                    session.copy(
                        step = VisualSearchSessionStep.Source,
                        errorMessage = null,
                        recoveryMessage = null,
                        recoveryActions = emptyList(),
                    )
                }
            }

            VisualSearchRecoveryActionType.ADD_TEXT -> {
                val fallbackText = current.selectedCategoryTitle
                    ?.takeIf { it.isNotBlank() }
                    ?: current.insight?.title?.takeIf { it.isNotBlank() }
                    ?: current.selectedCategoryCode
                    ?: return
                dismissVisualSearch()
                onQueryChange(fallbackText)
            }

            VisualSearchRecoveryActionType.REFINE_INTENT -> {
                val nextIntent = when (current.intent) {
                    VisualSearchIntent.EXACT_SAME -> VisualSearchIntent.SIMILAR
                    VisualSearchIntent.SIMILAR -> VisualSearchIntent.IDENTIFY_FIRST
                    else -> VisualSearchIntent.IDENTIFY_FIRST
                }
                updateVisualSearchIntent(nextIntent)
            }

            VisualSearchRecoveryActionType.CONTINUE_WITHOUT_AI,
            VisualSearchRecoveryActionType.RETRY,
                -> submitVisualSearch(autoTriggered = false)
        }
    }

    fun submitVisualSearch(
        autoTriggered: Boolean = false,
    ) {
        val snapshot = _state.value.visualSearch
        val sessionId = snapshot.sessionId ?: UUID.randomUUID().toString()
        val asset = snapshot.asset
        val categoryCode = snapshot.selectedCategoryCode?.trim()?.takeIf { it.isNotEmpty() }
        if (asset == null) {
            reduce { state ->
                state.copy(
                    visualSearch = state.visualSearch.copy(
                        sessionId = sessionId,
                        errorMessage = "Добавьте фото для поиска.",
                    ),
                )
            }
            return
        }

        updateVisualSearchSession { session ->
            session.copy(
                sessionId = sessionId,
                isSubmitting = true,
                binderStatus = null,
                errorMessage = null,
                recoveryMessage = null,
                recoveryActions = emptyList(),
            )
        }

        viewModelScope.launch {
            val current = _state.value.visualSearch
            val source = current.source ?: VisualSearchSource.GALLERY
            val metadata = VisualSearchTransportMetadata(visualSessionId = sessionId)
            val querySessionId = "qs-${UUID.randomUUID()}"
            val locale = Locale.getDefault().toLanguageTag()
            val photoUris = buildVisualResultsPhotoUris(current)
            val preflight = current.preflight?.signals ?: buildVisualSearchPreflightUi(current)?.signals ?: VisualSearchPreflightSignals()
            val selectedRegion = current.selectedRegion?.toDomainModel()
            val cheapProjection = buildVisualSearchCheapProjection(
                session = current,
                categoryCode = categoryCode,
                preflight = preflight,
            )

            trackVisualSearchEvent(
                sessionId = sessionId,
                name = "visual_search_submit_started",
                payload = mapOf(
                    "autoTriggered" to autoTriggered.toString(),
                    "intent" to current.intent.name.lowercase(Locale.ROOT),
                    "categoryCode" to (categoryCode ?: "none"),
                    "captureMode" to current.captureMode.name.lowercase(Locale.ROOT),
                    "selectionMode" to current.selectionMode.name.lowercase(Locale.ROOT),
                ),
            )

            val reusedQuery = runCatching {
                reuseVisualSearchContext(
                    metadata = metadata,
                    request = VisualSearchContextReuseRequest(
                        assetFingerprint = asset.fingerprint,
                        source = source,
                        entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
                    ),
                )
            }.getOrNull()?.reusedQuery

            if (reusedQuery?.qualityApproved == true) {
                openVisualResultsFromBoundQuery(
                    sessionId = sessionId,
                    captureMode = current.captureMode,
                    intent = current.intent,
                    categoryTitle = current.selectedCategoryTitle ?: current.insight?.suggestedCategoryTitle,
                    hypothesisTitle = current.insight?.title,
                    hypothesisSubtitle = current.insight?.subtitle,
                    boundQuery = reusedQuery,
                    rankedCandidates = emptyList(),
                    photoUris = photoUris,
                )
                return@launch
            }

            val normalizeResponse = runCatching {
                normalizeVisualSearchDraft(
                    metadata = metadata.copy(
                        idempotencyKey = "normalize-$sessionId-${System.currentTimeMillis()}",
                    ),
                    request = VisualSearchNormalizeDraftRequest(
                        asset = asset.toDomainModel(),
                        contextAssets = buildVisualSearchContextAssets(current, asset),
                        source = source,
                        entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
                        selectionMode = current.selectionMode,
                        intent = current.intent,
                        selectedRegion = selectedRegion,
                        preflightSignals = preflight,
                        manualCategoryCode = categoryCode,
                        locale = locale,
                    ),
                )
            }.getOrNull()
            val normalizationDraft = normalizeResponse?.draft
                ?.takeIf { normalizeResponse.status != VisualSearchEnvelopeStatus.FAILED }
            val normalizeReasonCodes = collectVisualSearchNormalizeReasonCodes(normalizeResponse)
            val normalizedTitle = normalizationDraft?.projection?.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (!normalizedTitle.isNullOrBlank()) {
                updateVisualSearchSession { session ->
                    val currentInsight = session.insight
                    session.copy(
                        insight = (currentInsight ?: VisualSearchInsightUi()).copy(
                            title = currentInsight?.title ?: normalizedTitle,
                            subtitle = currentInsight?.subtitle ?: "Собрали ориентир по фото и готовы открыть выдачу.",
                        ),
                    )
                }
            }

            trackVisualSearchEvent(
                sessionId = sessionId,
                name = "visual_search_normalize_completed",
                payload = mapOf(
                    "status" to (normalizeResponse?.status?.name?.lowercase(Locale.ROOT) ?: "missing"),
                    "provider" to (normalizationDraft?.providerName ?: "none"),
                    "reasonCodes" to normalizeReasonCodes.joinToString(","),
                ),
            )

            val bindResponse = runCatching {
                bindVisualSearchQuery(
                    metadata = metadata.copy(
                        idempotencyKey = "bind-$sessionId-${System.currentTimeMillis()}",
                    ),
                    request = VisualSearchBindQueryRequest(
                        intent = current.intent,
                        source = source,
                        selectionMode = current.selectionMode,
                        querySessionId = querySessionId,
                        manualCategoryCode = categoryCode,
                        preflightSignals = preflight,
                        cheapProjection = cheapProjection,
                        normalizationDraft = normalizationDraft,
                        locale = locale,
                        fingerprint = asset.fingerprint,
                    ),
                )
            }.getOrNull()

            val boundQuery = bindResponse?.boundQuery
            if (boundQuery?.qualityApproved == true) {
                openVisualResultsFromBoundQuery(
                    sessionId = sessionId,
                    captureMode = current.captureMode,
                    intent = current.intent,
                    categoryTitle = current.selectedCategoryTitle ?: current.insight?.suggestedCategoryTitle,
                    hypothesisTitle = current.insight?.title,
                    hypothesisSubtitle = current.insight?.subtitle,
                    boundQuery = boundQuery,
                    rankedCandidates = bindResponse?.rankedCandidates.orEmpty(),
                    photoUris = photoUris,
                )
                return@launch
            }

            val weakConfidenceRetakePreferred = shouldPreferRetakeForVisualSearch(
                session = current,
                preflight = preflight,
            )
            val reasonCodes = (
                parseVisualSearchReasonCodes(bindResponse?.error?.details?.get("reasonCodes")) +
                    normalizeReasonCodes
                ).distinct()
            val fallbackSubtitle = when {
                normalizationDraft?.needsMorePhotos == true || normalizationDraft?.missingEvidence?.isNotEmpty() == true ->
                    "Не удалось точно определить модель по первому фото. Добавьте снимок с другого ракурса."
                weakConfidenceRetakePreferred ->
                    "Если выдача слишком широкая, добавьте фото крупнее или с другого ракурса."
                else ->
                    visualSearchErrorMessage(bindResponse?.error?.messageKey)
            }
            val fallbackBoundQuery = boundQuery
                ?: bindResponse
                    ?.rankedCandidates
                    ?.sortedWith(
                        compareByDescending<com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate> { candidate ->
                            candidate.isPrimary
                        }.thenBy { candidate -> candidate.rank },
                    )
                    ?.firstOrNull()
                    ?.query

            if (fallbackBoundQuery != null) {
                openVisualResultsFromBoundQuery(
                    sessionId = sessionId,
                    captureMode = current.captureMode,
                    intent = current.intent,
                    categoryTitle = current.selectedCategoryTitle ?: current.insight?.suggestedCategoryTitle,
                    hypothesisTitle = current.insight?.title,
                    hypothesisSubtitle = fallbackSubtitle,
                    boundQuery = fallbackBoundQuery,
                    rankedCandidates = bindResponse?.rankedCandidates.orEmpty(),
                    photoUris = photoUris,
                )
                return@launch
            }

            openVisualResultsFromFallbackProjection(
                sessionId = sessionId,
                captureMode = current.captureMode,
                intent = current.intent,
                querySessionId = querySessionId,
                selectedCategoryCode = categoryCode,
                selectedCategoryTitle = current.selectedCategoryTitle ?: current.insight?.suggestedCategoryTitle,
                hypothesisTitle = current.insight?.title,
                hypothesisSubtitle = fallbackSubtitle,
                cheapProjection = cheapProjection,
                normalizationDraft = normalizationDraft,
                reusableFingerprint = asset.fingerprint,
                reasonCodes = reasonCodes,
                photoUris = photoUris,
            )
        }
    }

    private fun openVisualResultsFromBoundQuery(
        sessionId: String,
        captureMode: VisualSearchCaptureMode,
        intent: VisualSearchIntent,
        categoryTitle: String?,
        hypothesisTitle: String?,
        hypothesisSubtitle: String?,
        boundQuery: com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundQuery,
        rankedCandidates: List<com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate>,
        photoUris: List<String>,
    ) {
        val searchCriteria = boundQuery.searchCriteria
        val previewTitle = boundQuery.previewTitle
            ?.takeIf { value -> value.isNotBlank() }
            ?: hypothesisTitle?.takeIf { value -> value.isNotBlank() }
        val resultsCandidates = mapResultsVisualCandidates(
            selected = boundQuery,
            rankedCandidates = rankedCandidates,
        )
        val payload = ResultsPayload(
            query = boundQuery.normalizedQuery,
            queryText = previewTitle
                ?: categoryTitle
                ?: resolveVisualSearchCategoryTitle(boundQuery.categoryCode)
                ?: "Поиск по фото",
            categoryCode = boundQuery.categoryCode,
            facetCollectionCode = searchCriteria.facetCollectionCode,
            facetPresetCode = searchCriteria.facetPresetCode,
            sellerId = searchCriteria.sellerId,
            querySessionId = searchCriteria.querySessionId ?: "qs-${UUID.randomUUID()}",
            location = searchCriteria.location,
            radiusKm = searchCriteria.radiusKm,
            conditions = searchCriteria.conditions,
            sort = searchCriteria.sort,
            origin = ResultsOrigin.Photo,
            visualContext = ResultsVisualContext(
                visualSessionId = sessionId,
                binderStatus = boundQuery.binderStatus,
                routeKind = boundQuery.routeKind,
                qualityApproved = boundQuery.qualityApproved,
                captureMode = captureMode,
                intent = intent,
                previewTitle = previewTitle,
                previewSubtitle = hypothesisSubtitle,
                chips = boundQuery.chips,
                modelCandidates = boundQuery.modelCandidates,
                rankedCandidates = resultsCandidates,
                selectedCandidateRank = resolveResultsVisualSelectedCandidateRank(
                    selected = boundQuery,
                    rankedCandidates = resultsCandidates,
                ),
                exactRoute = boundQuery.exactRoute,
                reusableFingerprint = boundQuery.reusableFingerprint,
                photoUris = photoUris,
            ),
        )
        reduce { state ->
            state.copy(
                inputMode = InputMode.Text,
                visualSearch = state.visualSearch.copy(
                    visible = false,
                    step = VisualSearchSessionStep.Hidden,
                    binderStatus = boundQuery.binderStatus,
                    isSubmitting = false,
                    errorMessage = null,
                    recoveryMessage = null,
                    recoveryActions = emptyList(),
                    pendingResultsPayload = payload,
                ),
            )
        }
        trackVisualSearchEvent(
            sessionId = sessionId,
            name = "visual_search_results_opened",
            payload = mapOf(
                "binder_status" to boundQuery.binderStatus.name.lowercase(Locale.ROOT),
                "categoryCode" to boundQuery.categoryCode,
                "exactRoute" to boundQuery.exactRoute.toString(),
            ),
        )
    }

    private fun openVisualResultsFromFallbackProjection(
        sessionId: String,
        captureMode: VisualSearchCaptureMode,
        intent: VisualSearchIntent,
        querySessionId: String,
        selectedCategoryCode: String?,
        selectedCategoryTitle: String?,
        hypothesisTitle: String?,
        hypothesisSubtitle: String?,
        cheapProjection: VisualSearchCandidateProjection,
        normalizationDraft: com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizationDraft?,
        reusableFingerprint: String?,
        reasonCodes: List<String>,
        photoUris: List<String>,
    ) {
        val resolvedCategoryCode = selectedCategoryCode?.trim()?.takeIf { value -> value.isNotEmpty() }
            ?: normalizationDraft?.projection?.categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() }
            ?: cheapProjection.categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() }
        val projection = normalizationDraft?.projection
        val fallbackBrand = projection?.brand.visualSearchSafeAnchorText()
        val fallbackModel = projection?.model.visualSearchSafeExactModelText()
        val fallbackModelCandidates = projection?.modelCandidates
            .orEmpty()
            .mapNotNull { candidate -> candidate.visualSearchSafeModelCandidate() }
            .distinctBy { candidate -> normalizeSearchQueryKey(candidate.text) }
            .take(2)
        val resolvedCategoryLabel = resolveVisualSearchCategoryTitle(resolvedCategoryCode)
            ?: selectedCategoryTitle?.takeIf { value -> value.isNotBlank() }
            ?: resolvedCategoryCode
            ?: "Категория не определена"
        val resolvedPreviewTitle = buildVisualSearchPhotoPreviewTitle(
            rawTitles = listOf(hypothesisTitle, projection?.title, cheapProjection.title),
            brand = fallbackBrand,
            model = fallbackModel,
            modelCandidates = fallbackModelCandidates.map { candidate -> candidate.text },
            categoryLabel = resolvedCategoryLabel.takeUnless { it == "Категория не определена" },
        )
        val fallbackQuery = resolveVisualSearchFallbackQuery(
            brand = fallbackBrand,
            model = fallbackModel,
            categoryLabel = resolvedCategoryLabel.takeUnless { it == "Категория не определена" },
        )
        val chips = buildList {
            resolvedCategoryCode?.let { code ->
                add(
                    VisualSearchChip(
                        kind = VisualSearchChipKind.CATEGORY,
                        code = code,
                        label = resolvedCategoryLabel,
                    ),
                )
            }
            fallbackBrand?.let { brand ->
                add(
                    VisualSearchChip(
                        kind = VisualSearchChipKind.BRAND,
                        label = brand,
                    ),
                )
            }
            fallbackModel?.let { model ->
                add(
                    VisualSearchChip(
                        kind = VisualSearchChipKind.MODEL,
                        label = model,
                    ),
                )
            }
        }
            .distinctBy { chip -> "${chip.kind.name}:${chip.code ?: chip.label.lowercase(Locale.ROOT)}" }
            .take(3)
        val payload = ResultsPayload(
            query = fallbackQuery,
            queryText = resolvedPreviewTitle,
            categoryCode = resolvedCategoryCode,
            querySessionId = querySessionId,
            sort = OfferSort.RANK,
            origin = ResultsOrigin.Photo,
            visualContext = ResultsVisualContext(
                visualSessionId = sessionId,
                binderStatus = VisualSearchBinderStatus.REJECTED,
                routeKind = null,
                qualityApproved = false,
                captureMode = captureMode,
                intent = intent,
                previewTitle = resolvedPreviewTitle,
                previewSubtitle = hypothesisSubtitle,
                chips = chips,
                modelCandidates = fallbackModelCandidates,
                rankedCandidates = emptyList(),
                selectedCandidateRank = null,
                exactRoute = false,
                reusableFingerprint = reusableFingerprint,
                photoUris = photoUris,
            ),
        )
        reduce { state ->
            state.copy(
                inputMode = InputMode.Text,
                visualSearch = state.visualSearch.copy(
                    visible = false,
                    step = VisualSearchSessionStep.Hidden,
                    binderStatus = VisualSearchBinderStatus.REJECTED,
                    isSubmitting = false,
                    errorMessage = null,
                    recoveryMessage = null,
                    recoveryActions = emptyList(),
                    pendingResultsPayload = payload,
                ),
            )
        }
        trackVisualSearchEvent(
            sessionId = sessionId,
            name = "visual_search_results_opened",
            payload = mapOf(
                "binder_status" to "rejected_fallback",
                "categoryCode" to (resolvedCategoryCode ?: "none"),
                "exactRoute" to "false",
                "reasonCodes" to reasonCodes.joinToString(","),
            ),
        )
    }

    private fun resolveVisualSearchFallbackQuery(
        brand: String?,
        model: String?,
        categoryLabel: String?,
    ): NormalizedQuery? {
        val normalizedCandidates = buildList {
            if (!brand.isNullOrBlank() && !model.isNullOrBlank()) add("$brand $model")
            if (!brand.isNullOrBlank()) add(brand)
            add(model.orEmpty())
            add(categoryLabel.orEmpty())
        }
            .map { candidate -> normalizeVisualSearchFallbackCandidate(candidate) }
            .map { candidate -> SearchTextNormalizer.normalize(candidate) }
            .map { candidate -> candidate.trim() }
            .filter { candidate -> candidate.isNotEmpty() }
            .filterNot(::isWeakVisualSearchFallbackTextValue)
            .distinct()
        val rawQuery = normalizedCandidates.firstOrNull(::isStrongVisualSearchFallbackText)
            ?: normalizedCandidates.firstOrNull()
            ?: return null
        return BrandModelRules.fromRaw(rawQuery)
    }

    private fun VisualSearchCandidateValue?.visualSearchSafeAnchorText(): String? {
        val candidate = this ?: return null
        val text = candidate.text.trim().takeIf { value -> value.isNotEmpty() } ?: return null
        if (isGenericVisualSearchIdentity(text)) return null
        val source = normalizeVisualSearchIdentitySource(candidate.source)
        val confidence = candidate.confidence ?: 0f
        if (source in visualSearchExactIdentitySources || source in visualSearchVisualAnchorSources) return text
        return text.takeIf { confidence >= visualSearchFallbackAnchorConfidence }
    }

    private fun VisualSearchCandidateValue?.visualSearchSafeExactModelText(): String? {
        val candidate = this ?: return null
        val text = candidate.text.trim().takeIf { value -> value.isNotEmpty() } ?: return null
        if (isGenericVisualSearchIdentity(text)) return null
        val source = normalizeVisualSearchIdentitySource(candidate.source)
        val confidence = candidate.confidence ?: 0f
        return when {
            source in visualSearchExactIdentitySources && confidence >= visualSearchFallbackAnchorConfidence -> text
            source == "VISUAL_DISTINCTIVE" && confidence >= visualSearchFallbackExactModelConfidence -> text
            else -> null
        }
    }

    private fun VisualSearchCandidateValue.visualSearchSafeModelCandidate(): VisualSearchCandidateValue? {
        val text = this.text.trim().takeIf { value -> value.isNotEmpty() } ?: return null
        if (isGenericVisualSearchIdentity(text)) return null
        val confidence = this.confidence ?: 0f
        if (confidence < visualSearchFallbackModelCandidateConfidence) return null
        val source = normalizeVisualSearchIdentitySource(this.source)
        if (source != null && source !in visualSearchExactIdentitySources && source !in visualSearchVisualAnchorSources) {
            return null
        }
        return copy(text = text)
    }

    private fun normalizeVisualSearchIdentitySource(raw: String?): String? =
        raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { value -> value.isNotEmpty() }

    private fun isGenericVisualSearchIdentity(raw: String): Boolean {
        val normalized = SearchTextNormalizer.normalizeKey(raw, Locale.ROOT)
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        return normalized in genericVisualSearchIdentityAnchors
    }

    private fun buildVisualSearchPhotoPreviewTitle(
        rawTitles: List<String?>,
        brand: String?,
        model: String?,
        modelCandidates: List<String>,
        categoryLabel: String?,
    ): String {
        val identityTitle = listOfNotNull(
            brand?.trim()?.takeIf { it.isNotEmpty() },
            model?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").takeIf { it.isNotBlank() }
        if (identityTitle != null) return identityTitle
        modelCandidates
            .firstOrNull { candidate -> candidate.isNotBlank() }
            ?.let { return it.trim() }
        categoryLabel
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return rawTitles
            .asSequence()
            .mapNotNull { title -> title?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull { title -> isSafeVisualSearchPreviewTitle(title) }
            ?: "Поиск по фото"
    }

    private fun isSafeVisualSearchPreviewTitle(raw: String): Boolean =
        !isGenericVisualSearchPreviewTitle(raw) && !isWeakVisualSearchFallbackTextValue(raw)

    private fun isGenericVisualSearchPreviewTitle(raw: String): Boolean {
        val normalized = SearchTextNormalizer.normalizeKey(raw, Locale.ROOT)
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        return normalized in genericVisualSearchPreviewTitles
    }

    private fun firstSafeVisualSearchHint(vararg candidates: String?): String? =
        candidates
            .asSequence()
            .mapNotNull { candidate -> candidate?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull(::isSafeVisualSearchPreviewTitle)

    private fun List<String>.firstSafeVisualSearchHint(): String? =
        asSequence()
            .map { candidate -> candidate.trim() }
            .filter { candidate -> candidate.isNotEmpty() }
            .firstOrNull(::isSafeVisualSearchPreviewTitle)

    private fun normalizeVisualSearchFallbackCandidate(raw: String): String {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return ""
        return when (normalized.lowercase(Locale.ROOT)) {
            "mouse",
            "computer mouse",
            "wireless mouse",
                -> "компьютерная мышь"
            "keyboard" -> "клавиатура"
            "laptop",
            "laptop computer",
                -> "ноутбук"
            "smartphone",
            "cell phone",
                -> "смартфон"
            "computer monitor",
            "monitor",
                -> "монитор"
            "television",
            "tv",
                -> "телевизор"
            else -> normalized
        }
    }

    private fun buildVisualSearchPreflightUi(
        session: VisualSearchSessionState,
    ): VisualSearchPreflightUi? {
        val asset = session.asset ?: return null
        val categoryCode = session.selectedCategoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val insight = session.insight
        val barcodeValue = insight?.barcodeValue?.trim()?.takeIf { it.isNotEmpty() }
        val ocrTextHints = insight?.recognizedText
            ?.split(textLineBreakRegex)
            ?.map { line -> line.trim() }
            ?.filter { line -> line.isNotEmpty() }
            .orEmpty()
            .take(3)
        val imageLabelHints = insight?.imageLabelHints.orEmpty()
        val width = asset.widthPx
        val height = asset.heightPx
        val minSide = minOf(width ?: Int.MAX_VALUE, height ?: Int.MAX_VALUE)
        val pixelCount = if (width != null && height != null) width * height else null
        val lowResolution = (minSide != Int.MAX_VALUE && minSide < 720) ||
            (pixelCount != null && pixelCount < 900_000)
        val aspectRatio = if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            1f
        }
        val wideAspect = aspectRatio >= 1.8f || aspectRatio <= 0.62f
        val exactRouteReady = !barcodeValue.isNullOrBlank()
        val requiresObjectPicker = session.captureMode == VisualSearchCaptureMode.IMAGE &&
            session.selectionMode == VisualSearchSelectionMode.WHOLE_FRAME &&
            (wideAspect || session.intent == VisualSearchIntent.EXACT_SAME)
        val hasTextHints = ocrTextHints.isNotEmpty()
        val hasImageHints = imageLabelHints.isNotEmpty() || !insight?.objectLabel.isNullOrBlank()
        val categoryCandidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = categoryCode,
            barcodeValue = barcodeValue,
            ocrTextHints = ocrTextHints,
            imageLabelHints = imageLabelHints,
            objectLabel = insight?.objectLabel,
            objectConfidence = insight?.objectConfidence,
        )
        val cheapProjectionReady = !categoryCode.isNullOrBlank() || exactRouteReady || hasTextHints || hasImageHints
        val admitServerAi = !lowResolution && session.source != VisualSearchSource.BARCODE_MODE
        val reasonCodes = buildList {
            if (!categoryCode.isNullOrBlank()) add("CATEGORY_HINT_PRESENT")
            if (categoryCandidates.isNotEmpty()) add("CATEGORY_SHORTLIST_PRESENT")
            if (exactRouteReady) add("BARCODE_EXACT_STRONG")
            if (cheapProjectionReady) add("CHEAP_PROJECTION_READY")
            if (admitServerAi) add("SERVER_AI_ADMISSIBLE")
            if (requiresObjectPicker) add("OBJECT_PICKER_RECOMMENDED")
            if (wideAspect) add("WIDE_ASPECT_FRAME")
            if (lowResolution) add("LOW_RESOLUTION")
            if (hasTextHints) add("OCR_HINT_PRESENT")
            if (hasImageHints) add("IMAGE_LABEL_HINT_PRESENT")
            when (session.selectionMode) {
                VisualSearchSelectionMode.MANUAL_CROP -> add("MANUAL_CROP_SELECTED")
                VisualSearchSelectionMode.AUTO_TARGET -> add("AUTO_TARGET_SELECTED")
                VisualSearchSelectionMode.WHOLE_FRAME -> add("WHOLE_FRAME_SELECTED")
            }
            if (session.selectedRegion != null) add("FOCUS_REGION_PROVIDED")
            if (session.source == VisualSearchSource.SCREENSHOT) add("SCREENSHOT_SOURCE")
        }.distinct()
        val hintLabels = buildList {
            if (!categoryCode.isNullOrBlank()) add("Категория выбрана")
            if (categoryCandidates.isNotEmpty()) add("Есть shortlist категорий")
            if (exactRouteReady) add("Штрихкод найден")
            if (requiresObjectPicker) add("Лучше выбрать предмет")
            if (session.selectedRegion != null) add("Предмет выделен")
            if (session.selectionMode == VisualSearchSelectionMode.AUTO_TARGET) add("Автовыбор предмета")
            if (hasTextHints) add("Есть текст")
            if (hasImageHints) add("Есть подсказки по фото")
            if (lowResolution) add("Качество может снизить точность")
        }
        val summary = when {
            exactRouteReady ->
                "Нашли штрихкод. Сначала попробуем самый точный поиск."
            categoryCode.isNullOrBlank() && (hasTextHints || hasImageHints) ->
                "Сначала покажем наиболее вероятные результаты по фото. Уточнения понадобятся только если выдача окажется слишком широкой."
            requiresObjectPicker && session.selectionMode == VisualSearchSelectionMode.WHOLE_FRAME ->
                "В кадре несколько зон. Если результаты будут слишком широкими, лучше выбрать один предмет."
            session.selectionMode == VisualSearchSelectionMode.MANUAL_CROP ->
                "Предмет уже выделен. Это поможет точнее сузить результаты."
            cheapProjectionReady && admitServerAi ->
                "Собрали ориентиры по фото и готовы открыть первую выдачу."
            cheapProjectionReady ->
                "Есть достаточно подсказок, чтобы открыть первую выдачу даже без категории."
            else ->
                "Сначала попробуем определить предмет по фото. Если уверенности не хватит, предложим уточнения."
        }
        return VisualSearchPreflightUi(
            signals = VisualSearchPreflightSignals(
                reasonCodes = reasonCodes,
                exactRouteReady = exactRouteReady,
                cheapProjectionReady = cheapProjectionReady,
                admitServerAi = admitServerAi,
                requiresObjectPicker = requiresObjectPicker,
                exactCategoryCode = categoryCode,
                barcodeValue = barcodeValue,
                captureMode = session.captureMode,
                ocrTextHints = ocrTextHints,
                imageLabelHints = imageLabelHints,
                objectLabel = insight?.objectLabel,
                objectConfidence = insight?.objectConfidence,
                categoryCandidates = categoryCandidates,
            ),
            summary = summary,
            hintLabels = hintLabels,
        )
    }

    private fun buildVisualSearchCheapProjection(
        session: VisualSearchSessionState,
        categoryCode: String?,
        preflight: VisualSearchPreflightSignals,
    ): VisualSearchCandidateProjection {
        val categoryCandidate = preflight.categoryCandidates
            .maxByOrNull { candidate -> candidate.confidence ?: 0f }
        val resolvedCategoryCode = categoryCode
            ?: session.selectedCategoryCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: session.insight
                ?.takeIf { insight -> insight.promoteSuggestedCategory }
                ?.suggestedCategoryCode
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: preflight.exactCategoryCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: categoryCandidate?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val categoryTitle = session.selectedCategoryTitle
            ?.takeIf { it.isNotBlank() }
            ?: session.insight
                ?.takeIf { insight -> insight.promoteSuggestedCategory }
                ?.suggestedCategoryTitle
                ?.takeIf { it.isNotBlank() }
            ?: resolveVisualSearchCategoryTitle(resolvedCategoryCode)
        val rawTitle = session.insight?.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isSafeVisualSearchPreviewTitle(it) }
        val safeImageLabelTitle = session.insight?.imageLabelHints.orEmpty().firstSafeVisualSearchHint()
        val safeObjectTitle = firstSafeVisualSearchHint(session.insight?.objectLabel)
        val safeRecognizedTextTitle = firstSafeVisualSearchHint(session.insight?.recognizedText?.take(64))
        return VisualSearchCandidateProjection(
            categoryCode = resolvedCategoryCode,
            categoryConfidence = when {
                categoryCode != null -> 1f
                !session.selectedCategoryCode.isNullOrBlank() -> 1f
                session.insight?.promoteSuggestedCategory == true &&
                    !session.insight.suggestedCategoryCode.isNullOrBlank() -> 0.74f
                !preflight.exactCategoryCode.isNullOrBlank() -> 0.92f
                categoryCandidate != null -> categoryCandidate.confidence
                else -> null
            },
            title = rawTitle
                ?: safeObjectTitle
                ?: safeImageLabelTitle
                ?: categoryTitle
                ?: safeRecognizedTextTitle,
            reasonCodes = preflight.reasonCodes,
        )
    }

    private fun buildVisualResultsPhotoUris(
        session: VisualSearchSessionState,
    ): List<String> = buildVisualSearchAssetStrip(session)
        .mapNotNull { asset ->
            asset.localUri
                .trim()
                .takeIf { it.isNotEmpty() }
        }

    private fun appendVisualSearchCapturedAsset(
        current: List<VisualSearchAssetUi>,
        incoming: VisualSearchAssetUi,
    ): List<VisualSearchAssetUi> = buildList {
        current
            .filterNot { item -> item.fingerprint == incoming.fingerprint }
            .takeLast(VISUAL_SEARCH_MAX_CAPTURE_ASSETS - 1)
            .forEach(::add)
        add(incoming)
    }

    private fun buildVisualSearchAssetStrip(
        session: VisualSearchSessionState,
    ): List<VisualSearchAssetUi> = buildList {
        session.asset?.let(::add)
        session.capturedAssets.forEach(::add)
    }
        .distinctBy { asset -> asset.fingerprint.trim() }
        .take(VISUAL_SEARCH_MAX_CAPTURE_ASSETS)

    private fun buildVisualSearchContextAssets(
        session: VisualSearchSessionState,
        primaryAsset: VisualSearchAssetUi,
    ): List<VisualSearchImageAsset> = buildVisualSearchAssetStrip(session)
        .filterNot { asset -> asset.fingerprint == primaryAsset.fingerprint }
        .map { asset -> asset.toDomainModel() }

    private fun mapResultsVisualCandidates(
        selected: com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundQuery,
        rankedCandidates: List<com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate>,
    ): List<com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate> {
        val normalizedCandidates = rankedCandidates
            .mapIndexed { index, candidate ->
                candidate.copy(
                    rank = candidate.rank.coerceAtLeast(index + 1),
                    isPrimary = candidate.query == selected,
                )
            }
        val selectedCandidate = (
            normalizedCandidates.firstOrNull { candidate -> candidate.query == selected }
                ?: com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate(
                    rank = 1,
                    confidence = null,
                    query = selected,
                    reasonCodes = emptyList(),
                    isPrimary = true,
                )
        ).copy(
            rank = 1,
            isPrimary = true,
        )
        val candidatesWithSelected = buildList {
            add(selectedCandidate)
            normalizedCandidates
                .filterNot { candidate -> candidate.query == selected }
                .forEach { candidate ->
                    add(
                        candidate.copy(
                            rank = candidate.rank.coerceAtLeast(size + 1),
                            isPrimary = false,
                        ),
                    )
                }
        }
        return candidatesWithSelected
            .distinctBy { candidate ->
                listOf(
                    candidate.query.categoryCode,
                    candidate.query.routeKind.name,
                    candidate.query.previewTitle.orEmpty(),
                    candidate.query.searchCriteria.brand.orEmpty(),
                    candidate.query.searchCriteria.model.orEmpty(),
                    candidate.query.searchCriteria.facetCollectionCode.orEmpty(),
                    candidate.query.searchCriteria.facetPresetCode.orEmpty(),
                )
                    .joinToString("|")
            }
            .sortedWith(
                compareByDescending<com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate> { candidate ->
                    candidate.isPrimary
                }.thenBy { candidate -> candidate.rank },
            )
            .mapIndexed { index, candidate ->
                candidate.copy(rank = index + 1)
            }
            .take(3)
    }

    private fun resolveResultsVisualSelectedCandidateRank(
        selected: com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundQuery,
        rankedCandidates: List<com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate>,
    ): Int? = rankedCandidates
        .firstOrNull { candidate -> candidate.query == selected }
        ?.rank

    private fun shouldPreferRetakeForVisualSearch(
        session: VisualSearchSessionState,
        preflight: VisualSearchPreflightSignals,
    ): Boolean {
        if (session.captureMode != VisualSearchCaptureMode.IMAGE) return false
        if (!preflight.barcodeValue.isNullOrBlank()) return false
        val objectConfidence = preflight.objectConfidence ?: 0f
        val hasStrongObject = objectConfidence >= 0.56f
        val hasStrongTextHint = preflight.ocrTextHints.any(::isStrongVisualSearchFallbackText)
        val hasStrongHint = hasStrongTextHint ||
            preflight.imageLabelHints.any(::isStrongVisualSearchFallbackText) ||
            preflight.objectLabel?.let(::isStrongVisualSearchFallbackText) == true
        val hasCategory = !session.selectedCategoryCode.isNullOrBlank() ||
            (
                session.insight?.promoteSuggestedCategory == true &&
                    !session.insight.suggestedCategoryCode.isNullOrBlank()
                )
        val lowResolution = preflight.reasonCodes.any { code -> code == "LOW_RESOLUTION" }
        return (!hasStrongObject && !hasStrongHint && !hasCategory) ||
            (lowResolution && !hasStrongObject && !hasCategory)
    }

    private fun isStrongVisualSearchFallbackText(raw: String): Boolean {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        if (isWeakVisualSearchFallbackTextValue(normalized)) return false
        if (isComputerMouseVisualSearchText(normalized)) return true
        if (normalized.isBlank()) return false
        if (normalized.length < 3) return false
        if (BrandModelRules.fromKnownFamily(raw) != null) return true
        if (strongVisualSearchTokens.any { token -> normalized.contains(token) }) return true
        return looksLikeProductModelHint(normalized)
    }

    private fun isWeakVisualSearchFallbackTextValue(raw: String): Boolean {
        val normalized = SearchTextNormalizer.normalizeKey(raw, Locale.ROOT)
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        if (normalized.isBlank()) return true
        if (normalized in weakVisualSearchFallbackTerms) return true
        return weakVisualSearchFallbackTokens.any { token -> normalized.contains(token) }
    }

    private fun isComputerMouseVisualSearchText(raw: String): Boolean {
        val normalized = SearchTextNormalizer.normalizeKey(raw, Locale.ROOT)
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        return normalized in computerMouseVisualSearchPhrases ||
            computerMouseVisualSearchTokens.any { token -> normalized.contains(token) }
    }

    private fun looksLikeProductModelHint(normalized: String): Boolean {
        val tokens = normalized
            .split(' ')
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 2) return false
        if (tokens.count { token -> token.length == 1 } > 0) return false
        val hasMixedAlphaNumericToken = tokens.any { token ->
            token.any { ch -> ch.isLetter() } &&
                token.any { ch -> ch.isDigit() } &&
                token.length in 4..18
        }
        val compactLength = tokens.joinToString("").length
        return hasMixedAlphaNumericToken && compactLength in 5..24
    }

    private fun collectVisualSearchNormalizeReasonCodes(
        response: com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse?,
    ): List<String> = buildList {
        addAll(response?.draft?.reasonCodes.orEmpty())
        addAll(parseVisualSearchReasonCodes(response?.error?.details?.get("reasonCodes")))
        response?.error?.details?.get("reason")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(it.uppercase(Locale.ROOT)) }
        when (response?.error?.messageKey) {
            "visual_search.error.offline" -> add("OFFLINE_DURING_AI")
            "visual_search.error.feature_disabled" -> add("FEATURE_DISABLED_DURING_AI")
        }
    }.distinct()

    private fun defaultVisualSearchRegion(): VisualSearchRegionUi = VisualSearchRegionUi(
        label = "Объект",
        left = 0.15f,
        top = 0.10f,
        width = 0.70f,
        height = 0.80f,
    )

    private fun defaultVisualSearchRecoveryActions(): List<VisualSearchRecoveryActionUi> = listOf(
        VisualSearchRecoveryActionUi(
            type = VisualSearchRecoveryActionType.RETAKE_PHOTO,
            label = visualSearchRecoveryActionLabel(VisualSearchRecoveryActionType.RETAKE_PHOTO),
        ),
        VisualSearchRecoveryActionUi(
            type = VisualSearchRecoveryActionType.ADD_TEXT,
            label = visualSearchRecoveryActionLabel(VisualSearchRecoveryActionType.ADD_TEXT),
        ),
        VisualSearchRecoveryActionUi(
            type = VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY,
            label = visualSearchRecoveryActionLabel(VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY),
        ),
    )

    private fun lowConfidenceVisualSearchRecoveryActions(): List<VisualSearchRecoveryActionUi> = listOf(
        VisualSearchRecoveryActionUi(
            type = VisualSearchRecoveryActionType.RETAKE_PHOTO,
            label = visualSearchRecoveryActionLabel(VisualSearchRecoveryActionType.RETAKE_PHOTO),
        ),
        VisualSearchRecoveryActionUi(
            type = VisualSearchRecoveryActionType.ADD_TEXT,
            label = visualSearchRecoveryActionLabel(VisualSearchRecoveryActionType.ADD_TEXT),
        ),
        VisualSearchRecoveryActionUi(
            type = VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY,
            label = visualSearchRecoveryActionLabel(VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY),
        ),
    )

    private fun resolveVisualSearchCategoryTitle(categoryCode: String?): String? {
        val normalizedCode = categoryCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val breadcrumb = normalizeBreadcrumb(categoryIndex?.breadcrumbByCode?.get(normalizedCode))
        return breadcrumbTail(breadcrumb).ifBlank { normalizedCode }
    }

    fun visualSearchCategoryTitle(categoryCode: String?): String? =
        resolveVisualSearchCategoryTitle(categoryCode)

    private fun parseVisualSearchReasonCodes(raw: String?): List<String> {
        val normalized = raw
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            .orEmpty()
        if (normalized.isBlank()) return emptyList()
        return normalized.split(',')
            .map { token -> token.trim().trim('"') }
            .filter { token -> token.isNotBlank() }
            .distinct()
    }

    private fun VisualSearchAssetUi.toDomainModel(): VisualSearchImageAsset = VisualSearchImageAsset(
        sha256 = fingerprint,
        mimeType = guessVisualSearchMimeType(localUri),
        widthPx = widthPx,
        heightPx = heightPx,
        byteSize = byteSize,
        storageKey = localUri,
        inlineBase64 = inlineBase64,
    )

    private fun VisualSearchRegionUi.toDomainModel(): VisualSearchSelectedRegion = VisualSearchSelectedRegion(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        width = width.coerceIn(0.05f, 1f),
        height = height.coerceIn(0.05f, 1f),
    )

    private fun guessVisualSearchMimeType(localUri: String): String {
        val extension = localUri.substringAfterLast('.', missingDelimiterValue = "")
            .substringBefore('?')
            .lowercase(Locale.ROOT)
        return when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic",
            "heif",
                -> "image/heif"
            else -> "image/jpeg"
        }
    }

    private fun visualSearchErrorMessage(messageKey: String?): String = when (messageKey) {
        "visual_search.error.invalid_asset" ->
            "Это фото не удалось прочитать. Попробуйте выбрать другой файл."
        "visual_search.error.offline",
        "visual_search.error.feature_disabled",
            -> "Сейчас доступны только базовые подсказки по фото. Можно продолжить без уточнения."
        else -> "Не удалось уверенно распознать товар по этому кадру."
    }

    private fun visualSearchRecoveryMessage(messageKey: String?): String = when (messageKey) {
        "visual_search.recovery.hard_stop" ->
            "Попробуйте выбрать предмет, добавить категорию или сделать новый снимок."
        "visual_search.recovery.weak_results" ->
            "Поиск получился слишком широким. Уточните цель или категорию."
        else -> "Можно немного уточнить фото и повторить поиск."
    }

    private fun visualSearchRecoveryActionLabel(actionType: VisualSearchRecoveryActionType): String = when (actionType) {
        VisualSearchRecoveryActionType.RETAKE_PHOTO -> "Переснять фото"
        VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY -> "Выбрать категорию"
        VisualSearchRecoveryActionType.ADD_TEXT -> "Добавить текст"
        VisualSearchRecoveryActionType.REFINE_INTENT -> "Уточнить цель"
        VisualSearchRecoveryActionType.CONTINUE_WITHOUT_AI -> "Показать шире"
        VisualSearchRecoveryActionType.RETRY -> "Повторить"
    }

    private fun trackVisualSearchEvent(
        sessionId: String,
        name: String,
        payload: Map<String, String> = emptyMap(),
    ) {
        viewModelScope.launch {
            runCatching {
                trackVisualSearchEvents(
                    metadata = VisualSearchTransportMetadata(visualSessionId = sessionId),
                    request = VisualSearchEventBatchRequest(
                        events = listOf(
                            VisualSearchEvent(
                                name = name,
                                happenedAtMs = System.currentTimeMillis(),
                                payload = payload,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    fun onQueryChange(value: String) {
        val prev = _state.value
        if (value == prev.template.inputText) return
        if (value.isBlank()) {
            resetTemplate()
            updateSuggestions("")
            return
        }
        val updated = templateEngine.onInputTextChanged(prev.template, value, categoryDictionary)
        applyTemplate(updated) { it.copy(chosenText = null, boundSegments = emptyList()) }
        enforceTemplateConsistency(value.trimStart())
        updateSuggestions(value)
    }

    fun refreshSuggestionsForCurrentInput() {
        updateSuggestions(_state.value.template.inputText)
    }

    fun onSuggestionChosen(text: String, product: Product?) {
        val heading = listOfNotNull(product?.brand, product?.model)
            .joinToString(" ")
            .ifBlank { text }
            .replace("\\s+".toRegex(), " ")
            .trim()
        val desiredInput = buildSuggestedInput(heading, text)
        val resolvedCategoryCode = product?.categoryCode ?: _state.value.template.categoryCode

        val baseAttrs = buildMap<String, TemplateAttribute> {
            product?.brand?.takeIf { it.isNotBlank() }?.let { b ->
                put(
                    "brand",
                    TemplateAttribute(
                        code = "brand",
                        canonicalValue = b,
                        source = ValueSource.FromSuggestion,
                    )
                )
            }
            product?.model?.takeIf { it.isNotBlank() }?.let { m ->
                put(
                    "model",
                    TemplateAttribute(
                        code = "model",
                        canonicalValue = m,
                        source = ValueSource.FromSuggestion,
                    )
                )
            }
        }
        val decoratedAttrs = injectCategoryAttributes(baseAttrs, resolvedCategoryCode, null)

        val base = UiTemplate(
            inputText = desiredInput,
            anchorType = com.example.shoppingassistant.domain.template.TemplateAnchorType.PRODUCT,
            anchorId = product?.id?.takeIf { it.isNotBlank() } ?: heading,
            lockedTitle = heading,
            isLocked = true,
            categoryCode = resolvedCategoryCode,
            attributes = decoratedAttrs,
            mode = TemplateMode.SearchOrSubscribe,
        )
        reduce { it.copy(suggestions = emptyList()) }
        viewModelScope.launch {
            if (isAtomicCategory(resolvedCategoryCode)) {
                recordAtomicTemplate(base)
                return@launch
            }
            rebuildDictionary(base.categoryCode, constraints = emptyList())
            val updated = templateEngine.onInputTextChanged(base, desiredInput, categoryDictionary)
            applyTemplate(updated) {
                it.copy(
                    chosenText = heading,
                    product = product,
                    linkMeta = null,
                    photoUrl = null,
                    photoPlaceholderCategory = product?.model ?: product?.brand,
                    templatePrefillDone = false,
                )
            }
            refreshAttributes(product, resolvedCategoryCode)
            recordTemplateUsage()
        }
    }

    fun resetTemplate() {
        categoryRequiredIfRules = emptyList()
        categoryConstraints = emptyList()
        categoryDictionary = buildCategoryDictionary(null, emptyList(), emptyList(), emptyList())
        reduce {
            it.copy(
                template = UiTemplate.Empty,
                queryText = "",
                chosenText = null,
                product = null,
                attributeDefs = emptyList(),
                attributeLiveValuesByKey = emptyMap(),
                selectedFilters = emptyMap(),
                requiredKeys = emptySet(),
                errorKeys = emptySet(),
                errorMessages = emptyMap(),
                photoUrl = null,
                photoUrls = emptyList(),
                photoPlaceholderCategory = null,
                linkMeta = null,
                templateMode = TemplateMode.SearchOrSubscribe,
                isTemplateActive = false,
                templatePrefillDone = false,
                showFilterSheet = false,
                filterStage = FilterStage.ATTRS,
                currentAttrKey = null,
                facetCountsKey = null,
                facetCounts = null,
                facetCountsLoading = false,
                foundCount = null,
                prefetchedOffers = emptyList(),
                createOfferLoading = false,
                createOfferStatus = null,
                createOfferMessage = null,
                lastCreatedOfferId = null,
                suggestions = emptyList(),
                visualSearch = VisualSearchSessionState(),
            )
        }
        updateSuggestions("")
    }

    private fun dropTemplatePreserveQuery(currentQuery: String) {
        reduce {
            it.copy(
                queryText = currentQuery,
                chosenText = null,
                product = null,
                attributeDefs = emptyList(),
                attributeLiveValuesByKey = emptyMap(),
                selectedFilters = emptyMap(),
                requiredKeys = emptySet(),
                errorKeys = emptySet(),
                errorMessages = emptyMap(),
                photoUrl = null,
                photoUrls = emptyList(),
                photoPlaceholderCategory = null,
                linkMeta = null,
                templateMode = TemplateMode.SearchOrSubscribe,
                isTemplateActive = false,
                templatePrefillDone = false,
                showFilterSheet = false,
                filterStage = FilterStage.ATTRS,
                currentAttrKey = null,
                facetCountsKey = null,
                facetCounts = null,
                facetCountsLoading = false,
                foundCount = null,
                prefetchedOffers = emptyList(),
                createOfferLoading = false,
                createOfferStatus = null,
                createOfferMessage = null,
                lastCreatedOfferId = null,
            )
        }
    }

    fun refreshCurrentUser() {
        viewModelScope.launch {
            val user = runCatching { getCurrentUser() }.getOrNull()
            val resolved = user?.id?.toString()
            realUserId = resolved
            reduce { it.copy(currentUserId = debugAuthStore.user.value?.id ?: resolved) }
        }
    }

    fun updatePrefetched(count: Int?, offers: List<ExplainedItem>) {
        reduce {
            it.copy(
                nearbyFoundCount = count,
                nearbyOffers = offers,
            )
        }
    }

    fun setAttributeValue(key: String, value: String) {
        if (key == categorySelectorKey) {
            applyCategorySelectionFromFilter(value)
            return
        }
        val updated = templateEngine.onAttributeChanged(_state.value.template, key, value, categoryDictionary)
        applyTemplate(updated) {
            it.copy(
                templatePrefillDone = true,
                currentAttrKey = null,
                showFilterSheet = false,
                facetCountsKey = null,
                facetCounts = null,
                facetCountsLoading = false,
            )
        }
        if (key == "brand" || key == "model") {
            refreshAttributes(product = _state.value.product, categoryCode = _state.value.template.categoryCode)
        }
    }

    private fun applyCategorySelectionFromFilter(value: String) {
        viewModelScope.launch {
            val index = ensureCategoryIndex()
            val code = resolveCategoryCodeFromBreadcrumb(value, index) ?: return@launch
            val rawBreadcrumb = index.breadcrumbByCode[code] ?: value
            val breadcrumb = normalizeBreadcrumb(rawBreadcrumb) ?: rawBreadcrumb
            val current = _state.value
            val remainder = extractFreeTextRemainder(current.template.inputText, current.template.lockedTitle)
            val heading = breadcrumbTail(breadcrumb).ifBlank { code }
            val desiredInput = listOfNotNull(
                heading.takeIf { it.isNotBlank() },
                remainder.takeIf { it.isNotBlank() },
            ).joinToString(" ").trim()

            val baseAttrs = injectCategoryAttributes(
                emptyMap(),
                code,
                breadcrumb,
            )
            val base = current.template.copy(
                inputText = desiredInput,
                anchorType = TemplateAnchorType.CATEGORY,
                anchorId = code,
                lockedTitle = heading,
                isLocked = true,
                categoryCode = code,
                attributes = baseAttrs,
            )
            if (isAtomicCategory(code)) {
                recordAtomicTemplate(base)
                return@launch
            }
            rebuildDictionary(code, constraints = emptyList())
            val updated = templateEngine.onInputTextChanged(base, desiredInput, categoryDictionary)
            applyTemplate(updated) {
                it.copy(
                    chosenText = breadcrumb.takeIf { text -> text.isNotBlank() },
                    product = null,
                    templatePrefillDone = false,
                )
            }
            refreshAttributes(product = null, categoryCode = code)
            recordTemplateUsage()
        }
    }

    fun removeAttribute(key: String) {
        val updated = templateEngine.onAttributeChanged(_state.value.template, key, null, categoryDictionary)
        applyTemplate(updated)
    }

    fun openAttributesSheet() {
        reduce {
            it.copy(
                showFilterSheet = true,
                filterStage = FilterStage.ATTRS,
                currentAttrKey = null,
                facetCountsKey = null,
                facetCounts = null,
                facetCountsLoading = false,
            )
        }
        refreshAttrFacetCountsIfVisible()
    }

    fun onPickAttribute(def: AttributeDef) {
        reduce {
            it.copy(
                showFilterSheet = true,
                filterStage = FilterStage.VALUES,
                currentAttrKey = def.key,
            )
        }
        requestFacetCounts(def)
    }

    fun onPickAttributeValue(def: AttributeDef, value: String) {
        setAttributeValue(def.key, value)
        reduce {
            it.copy(
                showFilterSheet = false,
                filterStage = FilterStage.ATTRS,
                currentAttrKey = null,
            )
        }
    }

    fun focusAttribute(key: String) {
        reduce {
            it.copy(
                showFilterSheet = true,
                filterStage = FilterStage.VALUES,
                currentAttrKey = key,
            )
        }
        val def = _state.value.attributeDefs.firstOrNull { it.key == key }
        requestFacetCounts(def)
    }

    fun dismissFilterSheet() {
        val wasValues = _state.value.filterStage == FilterStage.VALUES && _state.value.currentAttrKey != null
        reduce {
            if (it.filterStage == FilterStage.VALUES && it.currentAttrKey != null) {
                it.copy(
                    filterStage = FilterStage.ATTRS,
                    currentAttrKey = null,
                    facetCountsKey = null,
                    facetCounts = null,
                    facetCountsLoading = false,
                )
            } else {
                it.copy(
                    showFilterSheet = false,
                    currentAttrKey = null,
                    facetCountsKey = null,
                    facetCounts = null,
                    facetCountsLoading = false,
                )
            }
        }
        if (wasValues) {
            refreshAttrFacetCountsIfVisible()
        }
    }

    private fun refreshFacetCountsIfVisible() {
        val state = _state.value
        if (!state.showFilterSheet || state.filterStage != FilterStage.VALUES) return
        val key = state.currentAttrKey ?: return
        val def = state.attributeDefs.firstOrNull { it.key == key }
        requestFacetCounts(def)
    }

    private fun refreshAttrFacetCountsIfVisible() {
        val state = _state.value
        if (!state.showFilterSheet || state.filterStage != FilterStage.ATTRS) return
        val selected = state.template.asSelectedFilters()
            .filterValues { it.isNotBlank() }
            .filterKeys { key -> !isCategoryLevelKey(key) }
        val selectedKeys = selected.keys
        if (selectedKeys.isEmpty()) {
            reduce { it.copy(attrFacetCounts = emptyMap(), attrFacetCountsLoading = emptySet()) }
            return
        }
        attrFacetCountsJobs.keys
            .filter { it !in selectedKeys }
            .forEach { key ->
                attrFacetCountsJobs.remove(key)?.cancel()
                lastAttrFacetRequestKey.remove(key)
            }
        reduce {
            it.copy(
                attrFacetCounts = it.attrFacetCounts.filterKeys { key -> key in selectedKeys },
                attrFacetCountsLoading = it.attrFacetCountsLoading.filter { it in selectedKeys }.toSet(),
            )
        }
        selected.forEach { (key, value) ->
            val def = state.attributeDefs.firstOrNull { it.key == key } ?: return@forEach
            requestAttrFacetCount(def, value)
        }
    }

    private fun requestFacetCounts(def: AttributeDef?) {
        val key = def?.key?.takeIf { it.isNotBlank() } ?: return
        if (isCategoryLevelKey(key)) return
        val query = buildFacetCountsQuery(key, def) ?: return
        val requestKey = buildFacetCountsRequestKey(query)
        val state = _state.value
        if (requestKey == lastFacetCountsRequestKey &&
            state.facetCountsKey == key &&
            state.facetCountsLoading == false &&
            state.facetCounts != null
        ) {
            return
        }
        lastFacetCountsRequestKey = requestKey
        facetCountsJob?.cancel()
        facetCountsJob = viewModelScope.launch {
            reduce { it.copy(facetCountsLoading = true, facetCountsKey = key, facetCounts = null) }
            val countsResult = runCatching { getFacetCounts(query) }
                .onFailure { throwable ->
                    showCatalogError(
                        throwable.message
                            ?: "Не удалось загрузить facet-данные. Повторите попытку.",
                    )
                }
            val counts = countsResult.getOrNull()
            if (counts != null) {
                clearCatalogError()
            }
            val map = counts?.associate { normalizeFacetValue(it.value) to it.count }
            reduce { current ->
                if (current.currentAttrKey != key || current.filterStage != FilterStage.VALUES) {
                    current
                } else {
                    current.copy(
                        facetCountsLoading = false,
                        facetCountsKey = key,
                        facetCounts = map,
                    )
                }
            }
        }
    }

    private fun requestAttrFacetCount(def: AttributeDef, selectedValue: String) {
        val key = def.key.takeIf { it.isNotBlank() } ?: return
        if (isCategoryLevelKey(key)) return
        val value = selectedValue.trim()
        if (value.isBlank()) return
        val query = buildFacetCountsQuery(key, def) ?: return
        val requestKey = buildFacetCountsRequestKey(query) + "|sel=" + normalizeFacetValue(value)
        val state = _state.value
        val lastKey = lastAttrFacetRequestKey[key]
        if (
            requestKey == lastKey &&
            key !in state.attrFacetCountsLoading &&
            state.attrFacetCounts.containsKey(key)
        ) {
            return
        }
        lastAttrFacetRequestKey[key] = requestKey
        attrFacetCountsJobs[key]?.cancel()
        attrFacetCountsJobs[key] = viewModelScope.launch {
            reduce { it.copy(attrFacetCountsLoading = it.attrFacetCountsLoading + key) }
            val countsResult = runCatching { getFacetCounts(query) }
                .onFailure { throwable ->
                    showCatalogError(
                        throwable.message
                            ?: "Не удалось загрузить facet-данные. Повторите попытку.",
                    )
                }
            val counts = countsResult.getOrNull()
            if (counts != null) {
                clearCatalogError()
            }
            val map = counts?.associate { normalizeFacetValue(it.value) to it.count }
            val resolved = if (map == null) null else map[normalizeFacetValue(value)] ?: 0
            reduce { current ->
                val updated = current.attrFacetCounts.toMutableMap()
                if (resolved == null) {
                    updated.remove(key)
                } else {
                    updated[key] = resolved
                }
                current.copy(
                    attrFacetCounts = updated,
                    attrFacetCountsLoading = current.attrFacetCountsLoading - key,
                )
            }
        }
    }

    private fun buildFacetCountsQuery(
        targetKey: String,
        def: AttributeDef?,
    ): FacetCountsQuery? {
        val state = _state.value
        val template = state.template
        val categoryCode = template.categoryCode ?: state.product?.categoryCode
        if (categoryCode.isNullOrBlank()) return null
        val selected = stripCategoryFilters(template.asSelectedFilters())
        val attributeFilters = selected
            .filterKeys { it != "brand" && it != "model" }
            .mapValues { listOf(it.value) }

        val anchor = when (template.anchorType) {
            TemplateAnchorType.PRODUCT -> {
                val prod = state.product
                if (prod != null) prod.brand to prod.model
                else {
                    val heading = template.lockedTitle ?: template.inputText
                    val q = BrandModelRules.fromRaw(heading)
                    q.brand to q.model
                }
            }
            TemplateAnchorType.CATEGORY -> {
                val brand = selected["brand"].orEmpty()
                val model = selected["model"].orEmpty()
                brand to model
            }
            null -> selected["brand"] to selected["model"]
        }

        var brand = anchor.first?.takeIf { it.isNotBlank() }
        var model = anchor.second?.takeIf { it.isNotBlank() }
        if (targetKey.equals("brand", ignoreCase = true)) brand = null
        if (targetKey.equals("model", ignoreCase = true)) model = null

        val mode = if (def?.multiValued == true) FacetCountMode.ADD else FacetCountMode.REPLACE

        return FacetCountsQuery(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
            selectedFilters = attributeFilters,
            targetFacetKey = targetKey,
            mode = mode,
            excludeTargetFacet = true,
        )
    }

    private fun buildFacetCountsRequestKey(query: FacetCountsQuery): String {
        val sb = StringBuilder()
        sb.append(query.categoryCode.trim().lowercase())
        sb.append("|k=").append(query.targetFacetKey.trim().lowercase())
        sb.append("|mode=").append(query.mode.name)
        sb.append("|ex=").append(query.excludeTargetFacet)
        sb.append("|b=").append(query.brand?.trim()?.lowercase().orEmpty())
        sb.append("|m=").append(query.model?.trim()?.lowercase().orEmpty())
        query.selectedFilters.entries.sortedBy { it.key.lowercase() }.forEach { (key, values) ->
            sb.append("|f=").append(key.trim().lowercase()).append("=")
            sb.append(values.map(::normalizeFacetValue).sorted().joinToString(","))
        }
        return sb.toString()
    }

    private fun normalizeFacetValue(value: String): String =
        value.trim().lowercase()

    private fun ValueFacet.toNearbyValueFacet(): NearbyValueFacet {
        val normalizedName = name.trim().ifBlank { id.trim() }
        return NearbyValueFacet(
            id = id.trim(),
            name = normalizedName,
            count = count,
        )
    }

    fun toExpressWithPhoto(photoUrl: String?, placeholderCategory: String?) {
        val current = _state.value
        val urls = buildList {
            if (!photoUrl.isNullOrBlank()) add(photoUrl)
            addAll(current.template.photoUrls)
            addAll(current.photoUrls)
        }.filter { it.isNotBlank() }.distinct()
        val lockedHeading = (current.template.lockedTitle ?: placeholderCategory)
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
        val desiredInput = when {
            !current.template.isLocked && !lockedHeading.isNullOrBlank() -> lockedHeading
            current.template.inputText.isBlank() && !lockedHeading.isNullOrBlank() -> lockedHeading
            else -> current.template.inputText
        }
        val base = current.template.copy(
            inputText = desiredInput,
            lockedTitle = lockedHeading ?: current.template.lockedTitle,
            isLocked = current.template.isLocked || !lockedHeading.isNullOrBlank(),
            mode = TemplateMode.ExpressFromPhoto,
            primaryPhotoUrl = urls.firstOrNull(),
            photoUrls = urls,
            linkMeta = null,
            linkTemplate = null,
        )
        val updated = recomputeTemplate(base)
        applyTemplate(updated) {
            it.copy(photoPlaceholderCategory = placeholderCategory ?: it.photoPlaceholderCategory)
        }
        refreshAttributes()
    }

    fun clearPhoto() {
        val updated = _state.value.template.copy(
            primaryPhotoUrl = null,
            photoUrls = emptyList(),
        )
        applyTemplate(updated)
    }

    fun attachLinkTemplate(linkTemplate: LinkTemplateRaw) {
        val resolvedCategoryCode = linkTemplate.categoryCode.takeIf { it.isNotBlank() }
        val uiCatalogResult = runCatching {
            runBlocking {
                attributeCatalogFor(
                    product = null,
                    liveValuesRepository = liveValuesRepository,
                    catalog = catalogRepository,
                    constraintsResolver = constraintsResolver,
                    categoryCode = resolvedCategoryCode,
                )
            }
        }
        uiCatalogResult.onFailure { throwable ->
            showCatalogError(
                throwable.message
                    ?: "Не удалось загрузить атрибуты каталога. Доступен упрощённый режим.",
            )
        }
        val uiCatalog = uiCatalogResult.getOrElse {
            com.example.shoppingassistant.feature.pages.main.context.CategoryUiCatalog(
                categoryCode = resolvedCategoryCode,
                defs = emptyList(),
                requiredIfRules = emptyList(),
                constraints = emptyList(),
            )
        }
        if (uiCatalogResult.isSuccess) {
            clearCatalogError()
        }

        if (uiCatalog.defs.isNotEmpty()) {
            rebuildDictionary(resolvedCategoryCode, uiCatalog.defs, uiCatalog.requiredIfRules)
            _state.update {
                it.copy(
                    attributeDefs = uiCatalog.defs,
                    attributeLiveValuesByKey = uiCatalog.liveValuesByKey,
                )
            }
        } else {
            rebuildDictionary(resolvedCategoryCode, _state.value.attributeDefs, uiCatalog.requiredIfRules)
        }
        val base = _state.value.template.copy(mode = TemplateMode.OfferFromLink, categoryCode = resolvedCategoryCode)
        val updated = templateEngine.attachLinkTemplate(base, linkTemplate, categoryDictionary)
            .copy(
                anchorType = com.example.shoppingassistant.domain.template.TemplateAnchorType.PRODUCT,
                anchorId = base.anchorId ?: linkTemplate.title.orEmpty().ifBlank { linkTemplate.sourceMeta.url },
            )
        val product = productFromTemplate(linkTemplate)
        applyTemplate(updated.copy(categoryCode = resolvedCategoryCode)) {
            it.copy(
                product = product ?: it.product,
                photoPlaceholderCategory = product?.model ?: product?.brand ?: it.photoPlaceholderCategory,
                templatePrefillDone = true,
                chosenText = updated.inputText.takeIf { txt -> txt.isNotBlank() } ?: it.chosenText,
            )
        }
        refreshAttributes(product ?: state.value.product, resolvedCategoryCode)
    }

    fun loadLinkTemplate(
        source: SourceType,
        url: String,
        onResult: (Result<LinkTemplateRaw>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching { linkTemplateBuilder.build(source, url) }
            result.onSuccess { template ->
                if (template.ingestStatus == IngestStatus.OK) {
                    attachLinkTemplate(template)
                }
            }
            onResult(result)
        }
    }

    fun loadLinkTemplate(
        url: String,
        onResult: (Result<LinkTemplateRaw>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching { linkTemplateBuilder.build(url) }
            result.onSuccess { template ->
                if (template.ingestStatus == IngestStatus.OK) {
                    attachLinkTemplate(template)
                }
            }
            onResult(result)
        }
    }

    fun detachLink() {
        val cleared = _state.value.template.copy(
            linkMeta = null,
            linkTemplate = null,
            mode = TemplateMode.ExpressFromPhoto,
        )
        val recomputed = recomputeTemplate(cleared)
        applyTemplate(recomputed) {
            it.copy(
                createOfferStatus = null,
                createOfferMessage = null,
                lastCreatedOfferId = null,
            )
        }
    }

    private fun productFromTemplate(linkTemplate: LinkTemplateRaw): Product? {
        val brand = linkTemplate.brand?.takeIf { it.isNotBlank() } ?: return null
        val model = linkTemplate.model?.takeIf { it.isNotBlank() }
        val title = linkTemplate.title ?: listOfNotNull(brand, model).joinToString(" ").ifBlank { brand }
        return Product(
            id = "",
            title = title,
            brand = brand,
            model = model ?: "",
        )
    }

    fun removePhoto(url: String) {
        val remaining = _state.value.template.photoUrls.filterNot { it == url }
        val updated = _state.value.template.copy(
            photoUrls = remaining,
            primaryPhotoUrl = remaining.firstOrNull(),
        )
        applyTemplate(updated)
    }

    fun validateRequired(): String? {
        val template = recomputeTemplate(_state.value.template)
        val missing = template.requiredKeys.filter { key ->
            template.attributes[key]?.canonicalValue.isNullOrBlank()
        }.toSet()
        val withErrors = template.copy(
            errorKeys = missing,
            errorMessages = missing.associateWith { "Нужно заполнить" },
        )
        applyTemplate(withErrors)
        return missing.firstOrNull()
    }

    data class TemplateValidationResult(
        val isValid: Boolean,
        val priceValue: Double?,
        val currency: String?,
        val brand: String?,
        val firstErrorKey: String?,
    )

    fun validateTemplateForTrackedOffer(): TemplateValidationResult {
        val template = recomputeTemplate(_state.value.template.copy(mode = TemplateMode.OfferFromLink))
        val validation = templateEngine.validateForOfferFromLink(template, categoryDictionary)
        val attrs = template.asSelectedFilters()
        val priceValue = attrs["price"]?.toDoubleOrNull()
        val currencyRaw = attrs["currency"]?.trim()
        val brand = attrs["brand"] ?: _state.value.product?.brand

        val errors = linkedMapOf<String, String>().apply { putAll(validation.errorMessages) }
        if (brand.isNullOrBlank()) {
            errors["brand"] = "Укажите бренд"
        }

        val templWithErrors = template.copy(
            requiredKeys = template.requiredKeys,
            errorKeys = errors.keys.toSet(),
            errorMessages = errors,
        )
        applyTemplate(templWithErrors)

        val firstError = errors.keys.firstOrNull()
        return TemplateValidationResult(
            isValid = errors.isEmpty(),
            priceValue = priceValue,
            currency = currencyRaw?.takeIf { it.length == 3 },
            brand = brand,
            firstErrorKey = firstError,
        )
    }

    fun submitTrackedOffer(onCompleted: (CreateTrackedOfferResult?) -> Unit = {}) {
        val state = _state.value
        val template = state.template
        val linkTemplate = template.linkTemplate ?: run {
            reduce {
                it.copy(
                    createOfferStatus = CreateTrackedOfferStatus.INVALID_INPUT,
                    createOfferMessage = "Нет активной ссылки для создания предложения.",
                )
            }
            return
        }
        if (template.mode != TemplateMode.OfferFromLink) {
            reduce {
                it.copy(
                    createOfferStatus = CreateTrackedOfferStatus.INVALID_INPUT,
                    createOfferMessage = "Шаблон не в режиме ссылки.",
                )
            }
            return
        }

        val validation = validateTemplateForTrackedOffer()
        if (!validation.isValid) {
            validation.firstErrorKey?.let { focusAttribute(it) }
            return
        }

        val imageUrls = template.primaryPhotoUrl?.let { listOf(it) }
            ?: template.photoUrls.takeIf { it.isNotEmpty() }
            ?: state.photoUrl?.let { listOf(it) }
            ?: emptyList()
        if (imageUrls.isEmpty()) {
            reduce {
                it.copy(
                    createOfferStatus = CreateTrackedOfferStatus.INVALID_INPUT,
                    createOfferMessage = "Добавьте фото объявления",
                )
            }
            return
        }

        viewModelScope.launch {
            reduce { it.copy(createOfferLoading = true, createOfferMessage = null) }
            val userId = state.currentUserId
                ?: runCatching { getCurrentUser()?.id?.toString() }.getOrNull()?.also { id ->
                    reduce { st -> st.copy(currentUserId = id) }
                }
                ?: run {
                    reduce {
                        it.copy(
                            createOfferLoading = false,
                            createOfferStatus = CreateTrackedOfferStatus.INVALID_INPUT,
                            createOfferMessage = "Войдите, чтобы создать предложение по ссылке",
                        )
                    }
                    onCompleted(null)
                    return@launch
                }

            val mergedFilters = template.asSelectedFilters().toMutableMap()
            state.product?.brand?.takeIf { it.isNotBlank() }?.let { mergedFilters.putIfAbsent("brand", it) }
            state.product?.model?.takeIf { it.isNotBlank() }?.let { mergedFilters.putIfAbsent("model", it) }

            val request = linkTemplateMapper.map(
                template = linkTemplate,
                selectedFilters = mergedFilters,
                userId = userId,
                photoUrls = imageUrls,
            )

            val localId = "temp-${System.currentTimeMillis()}"
            val localUi = UserOfferCardUi(
                id = localId,
                title = request.title,
                category = state.product?.model ?: state.chosenText,
                priceMajor = request.priceValue,
                currency = request.currency,
                status = UserOfferStatus.ACTIVE,
                coverUrl = imageUrls.firstOrNull(),
                publishedAtMillis = System.currentTimeMillis(),
                sourceUpdatedAtMillis = System.currentTimeMillis(),
                sourceName = request.source.domainName,
                sourceIconUrl = request.source.sourceIconUrl,
            )
            createdStore.add(localUi)

            val result: CreateTrackedOfferResult = runCatching {
                userOffersSyncTask.enqueueAndSync(localId, request)
            }
                .getOrElse { throwable ->
                    reduce {
                        it.copy(
                            createOfferLoading = false,
                            createOfferStatus = CreateTrackedOfferStatus.INVALID_INPUT,
                            createOfferMessage = throwable.message ?: "Не удалось создать предложение",
                        )
                    }
                    onCompleted(null)
                    return@launch
                }

            val status = result.status
            val message = when (status) {
                CreateTrackedOfferStatus.CREATED -> "Предложение создано"
                CreateTrackedOfferStatus.ALREADY_EXISTS -> "Предложение уже существует"
                CreateTrackedOfferStatus.INVALID_INPUT -> result.message ?: "Некорректные данные"
            }

            reduce {
                it.copy(
                    createOfferLoading = false,
                    createOfferStatus = status,
                    createOfferMessage = message,
                    lastCreatedOfferId = result.offerId ?: result.existingOfferId,
                )
            }
            onCompleted(result)
        }
    }

    fun applyTemplateMode(mode: TemplateMode) {
        val updated = _state.value.template.copy(mode = mode)
        val recalculated = recomputeTemplate(updated)
        applyTemplate(recalculated) { it.copy(errorKeys = emptySet(), errorMessages = emptyMap()) }
        refreshAttributes()
    }

    fun refreshAttributes(
        product: Product? = _state.value.product,
        categoryCode: String? = _state.value.template.categoryCode,
        focusOnKey: String? = null,
    ) {
        viewModelScope.launch {
            val resolvedCategory = categoryCode?.takeIf { it.isNotBlank() }
                ?: product?.categoryCode?.takeIf { it.isNotBlank() }
            val uiCatalogResult = runCatching {
                attributeCatalogFor(
                    product = product,
                    liveValuesRepository = liveValuesRepository,
                    catalog = catalogRepository,
                    constraintsResolver = constraintsResolver,
                    categoryCode = resolvedCategory,
                    selectedFilters = stripCategoryFilters(_state.value.template.asSelectedFilters()),
                )
            }
            uiCatalogResult.onFailure { throwable ->
                showCatalogError(
                    throwable.message
                        ?: "Не удалось загрузить атрибуты каталога. Доступен упрощённый режим.",
                )
            }
            val uiCatalog = uiCatalogResult.getOrElse {
                com.example.shoppingassistant.feature.pages.main.context.CategoryUiCatalog(
                    categoryCode = resolvedCategory,
                    defs = emptyList(),
                    requiredIfRules = emptyList(),
                    constraints = emptyList(),
                )
            }
            if (uiCatalogResult.isSuccess) {
                clearCatalogError()
            }
            runCatching { ensureCategoryIndex() }
            val template = _state.value.template
            val lockedKeys: Set<String> = if (template.isLocked && template.anchorType == TemplateAnchorType.PRODUCT) {
                template.attributes
                    .filterValues { it.source == ValueSource.FromSuggestion }
                    .keys
                    .toSet()
            } else emptySet()

            val defs = uiCatalog.defs
                .map { def -> def.copy(locked = def.key in lockedKeys) }

            var extended = ensureTemplateAttributes(defs, template.mode)
            extended = withCategoryDefs(extended)
            val dictDefs = extended.filterNot { def -> isCategoryLevelKey(def.key) }
            rebuildDictionary(resolvedCategory, dictDefs, uiCatalog.requiredIfRules, uiCatalog.constraints)
            _state.update {
                it.copy(
                    attributeDefs = extended,
                    attributeLiveValuesByKey = uiCatalog.liveValuesByKey,
                )
            }
            val cleanedAttrs = filterTemplateAttributes(
                attrs = template.attributes,
                allowedKeys = dictDefs.map { it.key }.toSet(),
                categoryCode = resolvedCategory,
            )
            val templ = template.copy(
                categoryCode = resolvedCategory,
                attributes = cleanedAttrs,
            )
            applyTemplate(recomputeTemplate(templ))
            autoPrefillAttributes(extended)
            focusOnKey?.takeIf { it.isNotBlank() }?.let { focusAttribute(it) }
        }
    }

    private fun requiredKeysFor(mode: TemplateMode): Set<String> = when (mode) {
        TemplateMode.SearchOrSubscribe -> emptySet()
        TemplateMode.OfferFromLink -> linkRequiredKeys
        TemplateMode.ExpressFromPhoto -> expressRequiredKeys
        TemplateMode.OfferFromVoice -> expressRequiredKeys
    }

    private fun ensureTemplateAttributes(
        defs: List<AttributeDef>,
        mode: TemplateMode,
    ): List<AttributeDef> = defs

    private fun enforceTemplateConsistency(newQuery: String) {
        // Раньше мы жёстко сбрасывали шаблон при рассинхроне текста и бренда/модели.
        // Теперь сохраняем шаблон, чтобы при редактировании текста не пропадали фото/фильтры.
        // Потерю консистентности решаем через валидацию/requiredKeys.
    }

    private fun autoPrefillAttributes(defs: List<AttributeDef>) {
        val state = _state.value
        val template = state.template
        if (!state.isTemplateActive || state.templatePrefillDone) return
        if (template.mode == TemplateMode.SearchOrSubscribe) return
        if (template.attributes.isNotEmpty()) {
            _state.update { it.copy(templatePrefillDone = true) }
            return
        }

        val candidate = firstPrefillCandidate(defs)
        if (candidate != null) {
            val updated = templateEngine.onAttributeChanged(template, candidate.first, candidate.second, categoryDictionary)
            applyTemplate(updated) { it.copy(templatePrefillDone = true) }
        } else {
            _state.update { it.copy(templatePrefillDone = true) }
        }
    }

    private fun firstPrefillCandidate(defs: List<AttributeDef>): Pair<String, String>? {
        val filtered = defs.filter {
            it.options.isNotEmpty() &&
                    it.key !in expressRequiredKeys &&
                    it.key !in linkRequiredKeys &&
                    !isCategoryLevelKey(it.key)
        }
        val color = filtered.firstOrNull { it.key.contains("color") || it.title.contains("цвет", ignoreCase = true) }
        val pick = color ?: filtered.firstOrNull()
        return pick?.options?.firstOrNull()?.let { pick.key to it }
    }

    private fun syncFilterIntoQuery(
        query: String,
        segments: List<BoundSegment>,
        key: String,
        value: String,
    ): Pair<String, List<BoundSegment>> {
        if (value.isBlank()) return query to segments
        val mutable = segments.toMutableList()
        var newQuery = query
        val existingIdx = mutable.indexOfFirst { it.key == key }
        if (existingIdx >= 0) {
            val seg = mutable[existingIdx]
            val start = seg.range.first
            val endExclusive = seg.range.last + 1
            val diff = value.length - (endExclusive - start)
            newQuery = newQuery.removeRange(start, endExclusive)
            newQuery = newQuery.substring(0, start) + value + newQuery.substring(start)
            mutable[existingIdx] = BoundSegment(key, value, IntRange(start, start + value.length - 1))
            if (diff != 0) {
                for (i in mutable.indices) {
                    if (i == existingIdx) continue
                    val r = mutable[i].range
                    if (r.first >= start) {
                        val shiftedStart = r.first + diff
                        val shiftedEnd = r.last + diff
                        mutable[i] = mutable[i].copy(range = IntRange(shiftedStart, shiftedEnd))
                    }
                }
            }
            return newQuery to mutable
        }

        val prefix = if (newQuery.isBlank()) "" else " "
        val insertPos = newQuery.length + prefix.length
        newQuery = newQuery + prefix + value
        mutable.add(BoundSegment(key, value, IntRange(insertPos - value.length, insertPos - 1)))
        return newQuery to mutable
    }

    private fun removeSegmentForKey(
        query: String,
        segments: List<BoundSegment>,
        key: String,
    ): Pair<String, List<BoundSegment>> {
        val idx = segments.indexOfFirst { it.key == key }
        if (idx < 0) return query to segments
        val seg = segments[idx]
        val start = seg.range.first
        val endExclusive = seg.range.last + 1
        var newQuery = StringBuilder(query).apply { delete(start, endExclusive) }.toString()
        newQuery = newQuery.replace(Regex("\\s{2,}"), " ").trim()
        // Диапазоны остальных сегментов станут недействительными после сильной очистки строки.
        val remaining = segments.filterIndexed { i, _ -> i != idx }.mapNotNull { s ->
            // Попробуем переопределить диапазон по первому вхождению значения.
            val pos = newQuery.indexOf(s.value)
            if (pos >= 0) s.copy(range = IntRange(pos, pos + s.value.length - 1)) else null
        }
        return newQuery to remaining
    }

    private fun diffRange(old: String, new: String): Pair<Int, Int> {
        if (old == new) return 0 to old.length
        val prefix = old.commonPrefixWith(new)
        val suffix = old.commonSuffixWith(new)
        val start = prefix.length
        val endOld = old.length - suffix.length
        return start to endOld
    }

    private fun shiftSegments(
        segments: List<BoundSegment>,
        diffStart: Int,
        diffEndOld: Int,
        delta: Int,
    ): List<BoundSegment> {
        if (delta == 0) return segments
        return segments.mapNotNull { seg ->
            val start = seg.range.first
            val end = seg.range.last
            if (end < diffStart) {
                seg
            } else if (start >= diffEndOld) {
                val shiftedStart = start + delta
                val shiftedEnd = end + delta
                seg.copy(range = IntRange(shiftedStart, shiftedEnd))
            } else {
                seg // будет переоценен validateSegments
            }
        }
    }

    private fun validateSegments(
        newQuery: String,
        segments: List<BoundSegment>,
        attributeDefs: List<AttributeDef>,
        selected: Map<String, String>,
    ): Pair<List<BoundSegment>, Map<String, String>> {
        val byKey = attributeDefs.associateBy { it.key }
        val kept = mutableListOf<BoundSegment>()
        val filters = selected.toMutableMap()
        segments.forEach { seg ->
            val def = byKey[seg.key]
            val range = seg.range
            if (range.first < 0 || range.last >= newQuery.length) return@forEach
            val fragment = newQuery.substring(range.first, range.last + 1)
            val matched = def?.let { matchFreeQueryAttributeValue(it, fragment) }
            if (matched != null) {
                kept.add(seg.copy(value = matched, range = range))
                filters[seg.key] = matched
            } else {
                filters.remove(seg.key)
            }
        }
        return kept to filters
    }

    private fun detectNewSegment(
        newQuery: String,
        rangeStart: Int,
        rangeEnd: Int,
        attributeDefs: List<AttributeDef>,
        existingKeys: Set<String>,
    ): BoundSegment? {
        if (rangeStart >= rangeEnd || attributeDefs.isEmpty()) return null
        val snippet = newQuery.substring(rangeStart, rangeEnd).trim()
        if (snippet.isBlank()) return null
        val tokens = snippet.split(" ", "\n", "\t").filter { it.isNotBlank() }
        val focus = tokens.lastOrNull() ?: return null

        var foundKey: String? = null
        var foundCanonical: String? = null
        attributeDefs.forEach { def ->
            val canon = matchFreeQueryAttributeValue(def, focus)
            if (canon != null) {
                if (foundCanonical != null && foundCanonical != canon) {
                    return null // неоднозначно
                }
                foundKey = def.key
                foundCanonical = canon
            }
        }
        if (foundKey == null || foundCanonical == null) return null
        if (existingKeys.contains(foundKey!!)) return null
        val idx = newQuery.indexOf(focus, rangeStart)
        if (idx < 0) return null
        return BoundSegment(
            key = foundKey!!,
            value = foundCanonical!!,
            range = IntRange(idx, idx + focus.length - 1),
        )
    }

    private fun autoBindFreeTokens(
        newQuery: String,
        attributeDefs: List<AttributeDef>,
        existing: List<BoundSegment>,
        existingFilters: Map<String, String>,
    ): List<BoundSegment> {
        if (attributeDefs.isEmpty()) return emptyList()
        val existingKeys = existing.map { it.key }.toSet()
        val tokens = newQuery.split(" ", "\n", "\t")
            .mapIndexedNotNull { idx, token ->
                val trimmed = token.trim()
                if (trimmed.isBlank()) null else idx to trimmed
            }
        val result = mutableListOf<BoundSegment>()
        tokens.forEach { (index, token) ->
            val startPos = newQuery.indexOf(token)
            if (startPos < 0) return@forEach
            val overlapping = existing.any { seg ->
                val r = seg.range
                startPos in r.first..r.last || (startPos + token.length - 1) in r.first..r.last
            }
            if (overlapping) return@forEach
            var matchedKey: String? = null
            var matchedCanon: String? = null
            attributeDefs.forEach { def ->
                val m = matchFreeQueryAttributeValue(def, token)
                if (m != null) {
                    if (matchedCanon != null && matchedCanon != m) {
                        matchedKey = null
                        matchedCanon = null
                        return@forEach
                    }
                    matchedKey = def.key
                    matchedCanon = m
                }
            }
            if (matchedKey != null && matchedCanon != null && matchedKey !in existingKeys) {
                if (existingFilters[matchedKey] == matchedCanon || existing.any { it.key == matchedKey }) return@forEach
                result.add(
                    BoundSegment(
                        key = matchedKey!!,
                        value = matchedCanon!!,
                        range = IntRange(startPos, startPos + token.length - 1),
                    ),
                )
            }
        }
        return result
    }

    private fun parseAttributesFromFreeQuery(
        queryText: String,
        attributeDefs: List<AttributeDef>,
    ): Map<String, String> {
        val defs = attributeDefs.filterNot { def -> isCategoryLevelKey(def.key) }
        return parseFreeQueryAttributes(
            queryText = queryText,
            attributeDefs = defs,
        )
    }

    fun submitQuery(displayText: String? = null) {
        val s = _state.value
        val qText = s.template.inputText.trim()
        if (qText.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val q = BrandModelRules.fromRaw(qText, Normalization.normalizeAttrs(stripCategoryFilters(s.template.asSelectedFilters())))
                val explained = repository.searchTopExplained(q, limit = 3, rankService = rankService)
                val offers = explained.map { it.dto }
                _state.update {
                    it.copy(
                        isLoading = false,
                        foundCount = offers.size,
                        topOffers = offers,
                        showTopOffersSheet = offers.isNotEmpty()
                    )
                }
            } catch (_: Throwable) {
                _state.update { it.copy(isLoading = false, foundCount = 0, topOffers = emptyList()) }
            }
        }
    }

    // Счётчик для InputSection; удобно мокать в тестах
    suspend fun countOffers(normalized: String, attrs: Map<String, String> = emptyMap()): Int = try {
        val rawAttrs = attrs.ifEmpty { _state.value.template.asSelectedFilters() }
        val q = BrandModelRules.fromRaw(normalized, Normalization.normalizeAttrs(stripCategoryFilters(rawAttrs)))
        repository.searchTopExplained(q, 3, rankService).size
    } catch (_: Throwable) { 0 }

    private companion object {
        private const val NEARBY_INITIAL_FETCH_LIMIT = 20
        private const val NEARBY_INITIAL_VISIBLE_COUNT = 12
        private const val NEARBY_FETCH_STEP = 20
        private const val NEARBY_COUNT_LIMIT = 50
        private const val NEARBY_FETCH_DEBOUNCE_MS = 250L
        private const val NEARBY_COUNT_DEBOUNCE_MS = 250L
        private const val NEARBY_PRIVACY_DECIMALS = 3
        private const val visualSearchFallbackAnchorConfidence = 0.55f
        private const val visualSearchFallbackModelCandidateConfidence = 0.42f
        private const val visualSearchFallbackExactModelConfidence = 0.92f
        private val textLineBreakRegex = Regex("[\\r\\n]+")
        private val visualSearchExactIdentitySources = setOf(
            "TEXT_EXACT",
            "LOGO_EXACT",
            "BARCODE_EXACT",
            "USER_HINT",
            "CATALOG_SHORTLIST",
        )
        private val visualSearchVisualAnchorSources = setOf(
            "VISUAL_DISTINCTIVE",
            "VISUAL_PATTERN",
        )
        private val genericVisualSearchPreviewTitles = setOf(
            "item from photo",
            "photo search",
            "product from photo",
            "search by photo",
            "visual search",
            "поиск по фото",
            "товар по фото",
            "предмет по фото",
            "объект по фото",
        )
        private val genericVisualSearchIdentityAnchors = setOf(
            "accessory",
            "battery",
            "bracelet",
            "camera",
            "candy",
            "charger",
            "chocolate",
            "coffee",
            "console",
            "cutlery",
            "drink",
            "grocery",
            "headphones",
            "honey",
            "hoodie",
            "jacket",
            "jeans",
            "laptop",
            "mouse",
            "pants",
            "phone",
            "product",
            "shoe",
            "shoes",
            "shirt",
            "smartphone",
            "sneakers",
            "speaker",
            "suit",
            "sweater",
            "tea",
            "thermometer",
            "watch",
            "wrist watch",
        )
        private val strongVisualSearchTokens = setOf(
            "мыш",
            "клавиат",
            "ноутбук",
            "смартфон",
            "телефон",
            "науш",
            "монитор",
            "телевиз",
            "пульт",
            "часы",
            "сумк",
            "обув",
            "камера",
            "bar",
            "model",
            "mouse",
            "keyboard",
            "headphone",
            "laptop",
            "smartphone",
            "phone",
            "monitor",
            "tv",
        )
        private val weakVisualSearchFallbackTerms = setOf(
            "товар по фото",
            "предмет",
            "объект",
            "текст",
            "изображение",
            "tableware",
            "cutlery",
            "dishware",
            "kitchenware",
            "serveware",
            "flatware",
            "silverware",
            "utensil",
            "utensils",
            "посуда",
            "посуда и кухня",
            "кухня",
            "wall",
            "room",
            "home",
            "interior",
            "furniture",
            "screen",
            "display",
        )
        private val weakVisualSearchFallbackTokens = setOf(
            "tableware",
            "cutlery",
            "dishware",
            "kitchenware",
            "serveware",
            "flatware",
            "silverware",
            "utensil",
            "посуда",
            "кухн",
            "interior",
            "room",
            "wall",
            "floor",
            "ceiling",
            "furniture",
        )
        private val computerMouseVisualSearchPhrases = setOf(
            "computer mouse",
            "wireless mouse",
            "gaming mouse",
            "pc mouse",
            "optical mouse",
            "компьютерная мышь",
            "беспроводная мышь",
            "игровая мышь",
            "оптическая мышь",
        )
        private val computerMouseVisualSearchTokens = setOf(
            "mouse",
            "мышь",
        )
    }
}

