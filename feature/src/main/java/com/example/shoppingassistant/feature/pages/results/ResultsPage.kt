package com.example.shoppingassistant.feature.pages.results

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.shoppingassistant.feature.ui.cards.CardActionRules
import com.example.shoppingassistant.feature.ui.cards.CardBadge
import com.example.shoppingassistant.feature.ui.cards.CardDensity
import com.example.shoppingassistant.feature.ui.cards.CardMediaItem
import com.example.shoppingassistant.feature.ui.cards.CardTrustData
import com.example.shoppingassistant.feature.ui.cards.OfferCard
import com.example.shoppingassistant.feature.ui.cards.OfferCardSkeleton
import com.example.shoppingassistant.feature.ui.cards.OfferCardUi
import com.example.shoppingassistant.feature.ui.cards.OfferOverflowAction
import com.example.shoppingassistant.feature.ui.cards.formatLocationText
import com.example.shoppingassistant.feature.ui.cards.formatPriceText
import com.example.shoppingassistant.feature.ui.cards.formatRatingText
import com.example.shoppingassistant.feature.ui.cards.formatUpdatedAtText
import com.example.shoppingassistant.feature.ui.cards.normalizeBadgeLabel
import com.example.shoppingassistant.core.config.SearchFeatureDecision
import com.example.shoppingassistant.core.config.SearchFeatureFlagKey
import com.example.shoppingassistant.core.config.SearchFeatureGate
import com.example.shoppingassistant.core.config.SearchFeatureGateContext
import com.example.shoppingassistant.core.config.SearchRemoteConfigService
import com.example.shoppingassistant.core.data.nearby.DEFAULT_NEARBY_RADIUS_KM
import com.example.shoppingassistant.core.data.nearby.NEARBY_RADIUS_PRESETS
import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.ui.LocalProfileSettings
import com.example.shoppingassistant.core.usecase.SearchOffersWithFacetsUseCase
import com.example.shoppingassistant.core.usecase.TrackPresetObservabilityEventsUseCase
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.model.BrandFacet
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferFacetType
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.PresetObservabilityEvent
import com.example.shoppingassistant.domain.model.PresetObservabilityEventType
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.TypedAttributeFilter
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.ValueFacet
import com.example.shoppingassistant.domain.model.toRawStringAttributes
import com.example.shoppingassistant.domain.model.toTypedAttributesGuess
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.navigation.AppRoutes
import com.example.shoppingassistant.feature.pages.chat.normalizeExternalUrl
import com.example.shoppingassistant.feature.pages.offers.isDeliverableToUser
import com.example.shoppingassistant.feature.pages.offers.tasks.determineOfferNominations
import com.example.shoppingassistant.feature.R
import com.example.shoppingassistant.feature.ui.layout.AppTopBar
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.layout.ScreenRoot
import com.example.shoppingassistant.feature.ui.state.StateHost
import com.example.shoppingassistant.feature.ui.state.model.EmptyReason
import com.example.shoppingassistant.feature.ui.state.model.LoadingPhase
import com.example.shoppingassistant.feature.ui.state.model.ScreenState
import com.example.shoppingassistant.feature.ui.state.model.StateAction
import com.example.shoppingassistant.feature.ui.state.model.StateActionType
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackMatchKeyFactory
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackTargetSpec
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPurchaseFormat
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetRuntimeFilters
import com.example.shoppingassistant.domain.facet.FacetRuntimeFiltersApplier
import com.example.shoppingassistant.domain.facet.GetFacetCollectionTask
import com.example.shoppingassistant.domain.facet.GetFacetDefinitionsTask
import com.example.shoppingassistant.domain.facet.GetFacetPresetTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get as koinGet
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val FILTERS_APPLY_RATE_LIMIT_MS = 600L
private const val RESULTS_CARD_OPEN_COOLDOWN_MS = 800L

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ResultsPage(
    payload: ResultsPayload,
    navController: NavHostController? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onOpenDraft: ((com.example.shoppingassistant.feature.pages.draft.DraftPayload) -> Unit)? = null,
    onOpenCategories: (() -> Unit)? = null,
    onEditQuery: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    applySafeInsets: Boolean = true,
    extraBottomPadding: Dp = LayoutDefaults.ContentBottomSpacing,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val searchOffersWithFacets: SearchOffersWithFacetsUseCase = remember {
        koinGet(SearchOffersWithFacetsUseCase::class.java)
    }
    val trackPresetEvents: TrackPresetObservabilityEventsUseCase = remember {
        koinGet(TrackPresetObservabilityEventsUseCase::class.java)
    }
    val catalogRepository: CatalogRepository = remember { koinGet(CatalogRepository::class.java) }
    val trackRepository: TrackRepository = remember { koinGet(TrackRepository::class.java) }
    val getFacetDefinitionsTask: GetFacetDefinitionsTask = remember { koinGet(GetFacetDefinitionsTask::class.java) }
    val getFacetCollectionTask: GetFacetCollectionTask = remember { koinGet(GetFacetCollectionTask::class.java) }
    val getFacetPresetTask: GetFacetPresetTask = remember { koinGet(GetFacetPresetTask::class.java) }
    val searchRemoteConfigService: SearchRemoteConfigService = remember {
        koinGet(SearchRemoteConfigService::class.java)
    }
    val searchFeatureGate: SearchFeatureGate = remember {
        koinGet(SearchFeatureGate::class.java)
    }
    val configuration = LocalConfiguration.current
    val profileSettings = LocalProfileSettings.current
    val querySessionId = rememberSaveable(payload.querySessionId) {
        payload.querySessionId?.trim()?.takeIf { it.isNotEmpty() } ?: "qs-${UUID.randomUUID()}"
    }
    val sentImpressionKeys = remember(querySessionId) { hashSetOf<String>() }
    val sentActionKeys = remember(querySessionId) { hashSetOf<String>() }
    val exposedFlagDecisionSignatures = remember(querySessionId) { hashSetOf<String>() }

    var filters by remember { mutableStateOf(initialFilterState(payload)) }
    var workingFilters by remember { mutableStateOf(filters) }
    var viewMode by rememberSaveable { mutableStateOf(ResultsViewMode.List) }
    var sheetScreen by remember { mutableStateOf<ResultsSheet?>(null) }
    var sheetTarget by remember { mutableStateOf(FilterSheetTarget.Applied) }
    var activeTypedFacetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    var brandQuery by rememberSaveable { mutableStateOf("") }
    var isFiltersApplyRateLimited by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var facetDefinitions by remember { mutableStateOf<List<FacetDefinition>>(emptyList()) }
    var categoryTreePath by remember { mutableStateOf<List<String>>(emptyList()) }
    val hiddenIds = remember { mutableStateListOf<String>() }
    var savedOfferIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var catalogLoadError by remember { mutableStateOf<String?>(null) }
    var catalogReloadToken by rememberSaveable { mutableStateOf(0) }
    var lastAppliedFacetPresetSignature by rememberSaveable { mutableStateOf<String?>(null) }
    val cardOpenTapMsByOfferId = remember { mutableMapOf<String, Long>() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var locationPermissionState by remember {
        mutableStateOf(resolveLocationPermissionState(context, requestedBefore = false))
    }
    var isResolvingCurrentLocation by remember { mutableStateOf(false) }
    var remoteConfigSnapshot by remember(querySessionId) {
        mutableStateOf(searchRemoteConfigService.currentSnapshot())
    }
    val featureGateContext = remember(
        querySessionId,
        filters.categoryCode,
        locationPermissionState,
        configuration.smallestScreenWidthDp,
    ) {
        SearchFeatureGateContext(
            searchSessionId = querySessionId,
            canonicalCode = filters.categoryCode?.trim()?.takeIf { it.isNotEmpty() },
            hasGeoPermission = locationPermissionState == ResultsGeoPermissionState.Granted,
            geoContextSupported = true,
            isTabletDevice = configuration.smallestScreenWidthDp >= 600,
            hasCache = false,
            reducedMotionEnabled = false,
        )
    }
    val sortSheetExplicitApplyDecision = remember(featureGateContext, remoteConfigSnapshot.fetchedAtMs) {
        searchFeatureGate.decide(
            key = SearchFeatureFlagKey.SEARCH_SORT_SHEET_EXPLICIT_APPLY,
            context = featureGateContext,
        )
    }
    val geoRadiusSliderDecision = remember(featureGateContext, remoteConfigSnapshot.fetchedAtMs) {
        searchFeatureGate.decide(
            key = SearchFeatureFlagKey.FILTERS_GEO_RADIUS_SLIDER,
            context = featureGateContext,
        )
    }
    val viewToggleSegmentedDecision = remember(featureGateContext, remoteConfigSnapshot.fetchedAtMs) {
        searchFeatureGate.decide(
            key = SearchFeatureFlagKey.SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET,
            context = featureGateContext,
        )
    }

    fun exposeFlagDecision(decision: SearchFeatureDecision) {
        val signature = listOf(
            decision.key.code,
            decision.effectiveValue.toString(),
            decision.remoteValue.toString(),
            decision.capabilityEnabled.toString(),
            decision.policyEnabled.toString(),
            decision.fallbackReason?.code ?: "none",
            remoteConfigSnapshot.source.name,
        ).joinToString("|")
        if (!exposedFlagDecisionSignatures.add(signature)) return
        FlowMetrics.markEvent(
            "feature_flag_exposure",
            "search_session_id=$querySessionId flag_key=${decision.key.code} value=${decision.effectiveValue} " +
                "remote_value=${decision.remoteValue} capability=${decision.capabilityEnabled} " +
                "policy=${decision.policyEnabled} source=${remoteConfigSnapshot.source.name.lowercase()}",
        )
        decision.fallbackReason?.let { reason ->
            FlowMetrics.markEvent(
                "feature_flag_fallback",
                "search_session_id=$querySessionId flag_key=${decision.key.code} reason=${reason.code} " +
                    "remote_value=${decision.remoteValue} capability=${decision.capabilityEnabled} " +
                    "policy=${decision.policyEnabled} source=${remoteConfigSnapshot.source.name.lowercase()}",
            )
        }
    }

    fun markFeatureFlagUsage(decision: SearchFeatureDecision, action: String) {
        FlowMetrics.markEvent(
            "feature_flag_usage",
            "search_session_id=$querySessionId flag_key=${decision.key.code} " +
                "action=$action value=${decision.effectiveValue} " +
                "remote_value=${decision.remoteValue} capability=${decision.capabilityEnabled} " +
                "policy=${decision.policyEnabled} source=${remoteConfigSnapshot.source.name.lowercase()}",
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationPermissionRequested = true
        locationPermissionState = resolveLocationPermissionState(
            context = context,
            requestedBefore = true,
        )
        FlowMetrics.markEvent(
            "filters_geo_permission_prompt_result",
            "screen=results origin=geo_radius granted=$granted",
        )
        if (granted) {
            scope.launch {
                isResolvingCurrentLocation = true
                val snapshot = resolveCurrentLocationForResults(context)
                isResolvingCurrentLocation = false
                if (snapshot?.city.isNullOrBlank()) {
                    Toast.makeText(
                        context,
                        "Не удалось определить текущее местоположение",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val city = snapshot?.city?.trim().orEmpty()
                val lat = snapshot?.lat
                val lon = snapshot?.lon
                workingFilters = workingFilters.copy(
                    location = city,
                    centerLat = lat,
                    centerLon = lon,
                )
                sheetScreen = ResultsSheet.Location
                Toast.makeText(context, "Локация определена: $city", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val retryCatalogLoad: () -> Unit = {
        catalogLoadError = null
        catalogReloadToken += 1
    }

    LaunchedEffect(querySessionId) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val result = runCatching {
            searchRemoteConfigService.refreshAsync(reason = "results_open")
        }
        result.onSuccess { snapshot ->
            remoteConfigSnapshot = snapshot
            val latencyMs = SystemClock.elapsedRealtime() - startedAtMs
            FlowMetrics.markEvent(
                "remote_config_fetch",
                "search_session_id=$querySessionId success=true latency_ms=$latencyMs " +
                    "source=${snapshot.source.name.lowercase()} reason=results_open",
            )
        }.onFailure {
            val latencyMs = SystemClock.elapsedRealtime() - startedAtMs
            FlowMetrics.markEvent(
                "remote_config_fetch",
                "search_session_id=$querySessionId success=false latency_ms=$latencyMs " +
                    "error_type=unknown reason=results_open",
            )
        }
    }

    LaunchedEffect(querySessionId, sortSheetExplicitApplyDecision, geoRadiusSliderDecision, viewToggleSegmentedDecision) {
        exposeFlagDecision(sortSheetExplicitApplyDecision)
        exposeFlagDecision(geoRadiusSliderDecision)
        exposeFlagDecision(viewToggleSegmentedDecision)
    }

    LaunchedEffect(Unit) {
        FlowMetrics.markResultsOpened()
        locationPermissionState = resolveLocationPermissionState(
            context = context,
            requestedBefore = locationPermissionRequested,
        )
    }

    LaunchedEffect(
        filters.categoryCode,
        filters.facetCollectionCode,
        filters.facetPresetCode,
        payload.categoryCode,
        catalogReloadToken,
    ) {
        var hadFailure = false

        val loadedCategories = runCatching { catalogRepository.listCategories() }
            .onFailure { hadFailure = true }
            .getOrElse { categories }
        categories = loadedCategories

        val categoryCode = filters.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val loadedFacetDefinitions = runCatching { getFacetDefinitionsTask(categoryCode) }
            .onFailure { hadFailure = true }
            .getOrElse { facetDefinitions }
        facetDefinitions = loadedFacetDefinitions

        val collectionCode = filters.facetCollectionCode?.trim()?.takeIf { it.isNotEmpty() }
        val collectionResult = collectionCode?.let { code ->
            runCatching { getFacetCollectionTask(code) }
                .onFailure { hadFailure = true }
        }
        val collection = collectionResult?.getOrNull()

        val presetCode = filters.facetPresetCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: collection?.presetCode?.trim()?.takeIf { it.isNotEmpty() }
        val presetResult = presetCode?.let { code ->
            runCatching { getFacetPresetTask(code) }
                .onFailure { hadFailure = true }
        }
        val preset = presetResult?.getOrNull()

        val presetSignature = listOf(
            categoryCode.orEmpty(),
            collectionCode.orEmpty(),
            presetCode.orEmpty(),
        ).joinToString("|").takeIf { signature ->
            signature.isNotBlank() && signature.replace("|", "").isNotBlank()
        }
        if (presetSignature == null) {
            lastAppliedFacetPresetSignature = null
        } else if (presetSignature != lastAppliedFacetPresetSignature) {
            val next = applyFacetPreset(base = filters, collection = collection, preset = preset)
            if (next != filters) {
                filters = next
                workingFilters = next
            }
            lastAppliedFacetPresetSignature = presetSignature
        }

        catalogLoadError = if (hadFailure) {
            "Не удалось загрузить часть данных каталога. Доступен базовый режим фильтров."
        } else {
            null
        }
    }

    val categoriesByCode = remember(categories) { categories.associateBy { it.code } }
    val categoriesByParent = remember(categories) { categories.groupBy { it.parentCode } }

    val selectedPathCodes = remember(filters.categoryCode, categoriesByCode) {
        buildCategoryPath(filters.categoryCode, categoriesByCode)
    }
    val selectedPathTitles = remember(selectedPathCodes, categoriesByCode) {
        selectedPathCodes.map { code -> categoriesByCode[code]?.title ?: code }
    }
    LaunchedEffect(filters.categoryCode, categoriesByCode) {
        if (selectedPathTitles != filters.categoryPath) {
            filters = filters.copy(categoryPath = selectedPathTitles)
        }
    }

    val facetUiFilters = remember(facetDefinitions) {
        buildFacetUiFilters(facetDefinitions)
    }

    val criteria = remember(filters, querySessionId, facetDefinitions) {
        buildCriteria(
            filters = filters,
            querySessionId = querySessionId,
            facetDefinitions = facetDefinitions,
        )
    }

    LaunchedEffect(criteria) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    var items by remember(criteria) { mutableStateOf<List<ExplainedItem>>(emptyList()) }
    var totalItems by remember(criteria) { mutableStateOf(0) }
    var runtimeBrandFacets by remember(criteria) { mutableStateOf<List<BrandFacet>>(emptyList()) }
    var runtimeConditionFacets by remember(criteria) { mutableStateOf<List<ValueFacet>>(emptyList()) }
    var runtimeDeliveryChannelFacets by remember(criteria) { mutableStateOf<List<ValueFacet>>(emptyList()) }
    var runtimeAttributeFacets by remember(criteria) { mutableStateOf<Map<String, List<ValueFacet>>>(emptyMap()) }
    var isLoading by remember(criteria) { mutableStateOf(true) }
    var isLoadingMore by remember(criteria) { mutableStateOf(false) }
    var appendErrorMessage by remember(criteria) { mutableStateOf<String?>(null) }
    var currentLimit by remember(criteria) { mutableStateOf(20) }
    var errorMessage by remember(criteria) { mutableStateOf<String?>(null) }

    LaunchedEffect(criteria) {
        if (criteria == null) {
            items = emptyList()
            totalItems = 0
            runtimeBrandFacets = emptyList()
            runtimeConditionFacets = emptyList()
            runtimeDeliveryChannelFacets = emptyList()
            runtimeAttributeFacets = emptyMap()
            isLoading = false
            errorMessage = null
            appendErrorMessage = null
            return@LaunchedEffect
        }
        currentLimit = 20
        isLoading = true
        errorMessage = null
        appendErrorMessage = null
        val request = criteria.copy(limit = currentLimit).toResultsFacetedRequest(
            includeRuntimeFacets = true,
            facetDefinitions = facetDefinitions,
        )
        val result = runCatching { searchOffersWithFacets(request) }
        result.onSuccess { payload ->
            items = payload.items
            totalItems = payload.total
            runtimeBrandFacets = payload.brandFacets
            runtimeConditionFacets = payload.conditionFacets
            runtimeDeliveryChannelFacets = payload.deliveryChannelFacets
            runtimeAttributeFacets = payload.attributeFacets
        }.onFailure {
            items = emptyList()
            totalItems = 0
            runtimeBrandFacets = emptyList()
            runtimeConditionFacets = emptyList()
            runtimeDeliveryChannelFacets = emptyList()
            runtimeAttributeFacets = emptyMap()
            errorMessage = "Не удалось загрузить предложения"
        }
        isLoading = false
    }

    val filteredItems = remember(
        items,
        profileSettings.hideUndeliverable,
        criteria?.userCountry,
        filters.deliverableOnly,
        filters.location,
    ) {
        val mustBeDeliverable = profileSettings.hideUndeliverable || filters.deliverableOnly
        val location = filters.location?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
        items.filter { item ->
            val deliverable = !mustBeDeliverable ||
                isDeliverableToUser(item.dto.sellerShippingCountries, criteria?.userCountry)
            if (!deliverable) return@filter false
            if (location.isNullOrBlank()) return@filter true
            val city = item.dto.sellerCity?.lowercase().orEmpty()
            val country = item.dto.sellerCountry?.lowercase().orEmpty()
            city.contains(location) || country.contains(location)
        }
    }

    val visibleItems = remember(filteredItems, hiddenIds) {
        filteredItems.filterNot { hiddenIds.contains(it.dto.id) }
    }
    val nominations = remember(visibleItems) { determineOfferNominations(visibleItems) }
    var wasAppliedChipsRowVisible by rememberSaveable(criteria) { mutableStateOf(false) }

    LaunchedEffect(visibleItems, filters.categoryCode, filters.facetCollectionCode, filters.facetPresetCode, querySessionId) {
        val categoryCode = filters.categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val presetCode = filters.facetPresetCode?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val collectionCode = filters.facetCollectionCode?.trim()?.takeIf { it.isNotEmpty() }
        if (visibleItems.isEmpty()) return@LaunchedEffect

        val context = PresetEventContext(
            querySessionId = querySessionId,
            categoryCode = categoryCode,
            facetCollectionCode = collectionCode,
            facetPresetCode = presetCode,
        )
        val batch = buildList {
            visibleItems.forEachIndexed { idx, item ->
                val position = idx + 1
                val idempotencyKey = "imp|${context.querySessionId}|${item.dto.id}|$position"
                if (sentImpressionKeys.contains(idempotencyKey)) return@forEachIndexed
                add(
                    buildPresetEvent(
                        context = context,
                        eventType = PresetObservabilityEventType.IMPRESSION,
                        idempotencyKey = idempotencyKey,
                        offerId = item.dto.id,
                        position = position,
                    ),
                )
            }
        }
        if (batch.isEmpty()) return@LaunchedEffect
        val response = runCatching { trackPresetEvents(batch) }.getOrNull() ?: return@LaunchedEffect
        if (response.acceptedCount > 0 || response.dedupedCount > 0) {
            batch.forEach { event -> sentImpressionKeys.add(event.idempotencyKey) }
        }
    }

    val canLoadMore = !isLoading &&
        !isLoadingMore &&
        items.size < totalItems
    val showLoadMore = items.isNotEmpty() && (canLoadMore || isLoadingMore || appendErrorMessage != null)
    val showStaleResultsBanner = errorMessage != null && visibleItems.isNotEmpty()
    val staleLastUpdatedAtMillis = remember(visibleItems) {
        visibleItems.mapNotNull { item -> item.dto.updatedAt }.maxOrNull()
    }
    val staleLastUpdatedLabel = remember(staleLastUpdatedAtMillis) {
        formatUpdatedAtText(staleLastUpdatedAtMillis)?.let { value -> "Обновлено $value" }
    }

    LaunchedEffect(showStaleResultsBanner) {
        if (showStaleResultsBanner) {
            FlowMetrics.markEvent("ui_offline_stale_banner_shown", "screen=results")
        }
    }

    val loadMore: () -> Unit = loadMore@{
        if (!canLoadMore || criteria == null) return@loadMore
        val beforeCount = items.size
        val nextLimit = currentLimit + 20
        isLoadingMore = true
        errorMessage = null
        appendErrorMessage = null
        FlowMetrics.markEvent(
            "results_paging_append",
            "screen=results stage=request current_count=$beforeCount next_limit=$nextLimit",
        )
        scope.launch {
            val request = criteria.copy(limit = nextLimit).toResultsFacetedRequest(
                includeRuntimeFacets = true,
                facetDefinitions = facetDefinitions,
            )
            val result = runCatching { searchOffersWithFacets(request) }
            result.onSuccess { payload ->
                val appendedCount = (payload.items.size - beforeCount).coerceAtLeast(0)
                items = payload.items
                totalItems = payload.total
                runtimeBrandFacets = payload.brandFacets
                runtimeConditionFacets = payload.conditionFacets
                runtimeDeliveryChannelFacets = payload.deliveryChannelFacets
                runtimeAttributeFacets = payload.attributeFacets
                currentLimit = nextLimit
                FlowMetrics.markEvent(
                    "results_paging_append",
                    "screen=results stage=success appended_count=$appendedCount total=${payload.total}",
                )
            }.onFailure {
                appendErrorMessage = "Не удалось загрузить ещё предложения"
                FlowMetrics.markEvent(
                    "results_paging_append",
                    "screen=results stage=error current_count=$beforeCount",
                )
            }
            isLoadingMore = false
        }
    }
    val retryLoad: () -> Unit = retry@{
        if (criteria == null || isLoading) return@retry
        currentLimit = 20
        isLoading = true
        errorMessage = null
        appendErrorMessage = null
        scope.launch {
            val request = criteria.copy(limit = currentLimit).toResultsFacetedRequest(
                includeRuntimeFacets = true,
                facetDefinitions = facetDefinitions,
            )
            val result = runCatching { searchOffersWithFacets(request) }
            result.onSuccess { payload ->
                items = payload.items
                totalItems = payload.total
                runtimeBrandFacets = payload.brandFacets
                runtimeConditionFacets = payload.conditionFacets
                runtimeDeliveryChannelFacets = payload.deliveryChannelFacets
                runtimeAttributeFacets = payload.attributeFacets
            }.onFailure {
                items = emptyList()
                totalItems = 0
                runtimeBrandFacets = emptyList()
                runtimeConditionFacets = emptyList()
                runtimeDeliveryChannelFacets = emptyList()
                runtimeAttributeFacets = emptyMap()
                errorMessage = "Не удалось загрузить предложения"
            }
            isLoading = false
        }
    }

    fun openChatFor(item: ExplainedItem, position: Int) {
        val sellerLabel = item.dto.sellerName ?: item.dto.brand ?: item.dto.model ?: "Продавец"
        val offerOpenUrls = resolveOfferOpenUrlsForResults(item.dto)
        val sourceName = item.dto.sourceName?.trim()?.takeIf { it.isNotEmpty() }
        FlowMetrics.markEvent(
            "offer_page_open",
            "screen=results offer_id=${item.dto.id} position=$position has_deeplink=${offerOpenUrls.hasRawDeeplink} host=${offerOpenUrls.host}",
        )
        val route = AppRoutes.chat(
            offerId = item.dto.id,
            sellerName = sellerLabel,
            offerTitle = item.dto.title,
            price = item.dto.price?.toString(),
            status = null,
            externalUrl = item.dto.externalUrl,
            redirectUrl = item.dto.redirectUrl,
            deeplinkUrl = item.dto.deeplinkUrl ?: item.dto.externalUrl,
            sourceName = sourceName,
            querySessionId = querySessionId,
            position = position,
        )
        navController?.navigate(route)
    }

    suspend fun submitPresetEvents(events: List<PresetObservabilityEvent>) {
        if (events.isEmpty()) return
        runCatching { trackPresetEvents(events) }
    }

    fun eventContext(): PresetEventContext? {
        val categoryCode = filters.categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val presetCode = filters.facetPresetCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val collectionCode = filters.facetCollectionCode?.trim()?.takeIf { it.isNotEmpty() }
        return PresetEventContext(
            querySessionId = querySessionId,
            categoryCode = categoryCode,
            facetCollectionCode = collectionCode,
            facetPresetCode = presetCode,
        )
    }

    fun trackClickEvent(
        offerId: String,
        position: Int,
        hasDeeplink: Boolean,
        cooldownHit: Boolean,
    ) {
        FlowMetrics.markEvent(
            "results_item_click",
            "screen=results offer_id=$offerId position=$position open_target=offer_page has_deeplink=$hasDeeplink cooldown_hit=$cooldownHit",
        )
        if (cooldownHit) return
        val context = eventContext() ?: return
        val idempotencyKey = "clk|${context.querySessionId}|$offerId|$position"
        if (!sentActionKeys.add(idempotencyKey)) return
        scope.launch {
            submitPresetEvents(
                listOf(
                    buildPresetEvent(
                        context = context,
                        eventType = PresetObservabilityEventType.CLICK,
                        idempotencyKey = idempotencyKey,
                        offerId = offerId,
                        position = position,
                    ),
                ),
            )
        }
    }

    fun trackConversionEvent(actionCode: String) {
        val context = eventContext() ?: return
        val idempotencyKey = "cnv|${context.querySessionId}|$actionCode"
        if (!sentActionKeys.add(idempotencyKey)) return
        scope.launch {
            submitPresetEvents(
                listOf(
                    buildPresetEvent(
                        context = context,
                        eventType = PresetObservabilityEventType.CONVERSION,
                        idempotencyKey = idempotencyKey,
                    ),
                ),
            )
        }
    }

    fun toggleSavedOffer(offerId: String) {
        savedOfferIds = if (savedOfferIds.contains(offerId)) {
            savedOfferIds - offerId
        } else {
            savedOfferIds + offerId
        }
    }

    fun shareExternal(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            val chooser = Intent.createChooser(intent, "Поделиться ссылкой")
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Не удалось поделиться ссылкой.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyLink(url: String) {
        scope.launch {
            val clipData = ClipData.newPlainText("link", url)
            clipboard.setClipEntry(ClipEntry(clipData))
        }
        Toast.makeText(context, "Ссылка скопирована.", Toast.LENGTH_SHORT).show()
    }

    fun handleOverflow(action: OfferOverflowAction, item: ExplainedItem) {
        val offerOpenUrls = resolveOfferOpenUrlsForResults(item.dto)
        val fallbackReason = when {
            !offerOpenUrls.hasRawDeeplink -> "url_missing"
            offerOpenUrls.fallbackUrl == null -> "invalid_url"
            else -> null
        }
        when (action) {
            OfferOverflowAction.Share -> {
                if (offerOpenUrls.fallbackUrl != null) {
                    FlowMetrics.markEvent(
                        "offer_open_fallback",
                        "screen=results action=share offer_id=${item.dto.id} has_deeplink=true",
                    )
                    shareExternal(offerOpenUrls.fallbackUrl)
                } else {
                    FlowMetrics.markEvent(
                        "offer_open_fallback",
                        "screen=results action=share offer_id=${item.dto.id} has_deeplink=${offerOpenUrls.hasRawDeeplink} fallback_reason=$fallbackReason",
                    )
                    val text = if (offerOpenUrls.hasRawDeeplink) {
                        "Ссылка оффера повреждена или недоступна."
                    } else {
                        "У этого оффера нет внешней ссылки."
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
            OfferOverflowAction.Hide -> hiddenIds.add(item.dto.id)
            OfferOverflowAction.Report -> {
                Toast.makeText(context, "Спасибо, мы проверим это объявление.", Toast.LENGTH_SHORT).show()
            }
            OfferOverflowAction.CopyLink -> {
                val copyUrl = offerOpenUrls.fallbackUrl ?: offerOpenUrls.rawPreferredUrl
                if (!copyUrl.isNullOrBlank()) {
                    FlowMetrics.markEvent(
                        "offer_open_fallback",
                        "screen=results action=copy_link offer_id=${item.dto.id} has_deeplink=${offerOpenUrls.hasRawDeeplink}" +
                            (if (offerOpenUrls.fallbackUrl == null) " fallback_reason=invalid_url" else ""),
                    )
                    copyLink(copyUrl)
                } else {
                    FlowMetrics.markEvent(
                        "offer_open_fallback",
                        "screen=results action=copy_link offer_id=${item.dto.id} has_deeplink=false fallback_reason=url_missing",
                    )
                    Toast.makeText(context, "У этого оффера нет внешней ссылки.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleTrack() {
        FlowMetrics.markTrackClicked("results")
        val normalizedCategoryCode = filters.categoryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        val queryBrand = filters.query?.brand?.trim()?.takeIf { it.isNotBlank() }
        val queryModel = filters.query?.model?.trim()?.takeIf { it.isNotBlank() }
        val trackMatchKey = TrackMatchKeyFactory.fromBrandModel(queryBrand, queryModel)
        if (normalizedCategoryCode == null) {
            Toast.makeText(
                context,
                "Для отслеживания выберите категорию",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val trackFilters = TrackFilters(extra = buildTrackFiltersExtra(filters))
        val hasTargetAttributes = trackFilters.extra.isNotEmpty() || trackMatchKey != null
        val trackType = if (hasTargetAttributes) TrackType.PRODUCT else TrackType.CATEGORY
        val effectiveMatchKey = trackMatchKey.takeIf { trackFilters.extra.isEmpty() }
        val targetSpec = TrackTargetSpec(
            categoryCode = normalizedCategoryCode,
            attributes = trackFilters.extra,
            matchKey = effectiveMatchKey,
            queryText = filters.queryText.trim().takeIf { it.isNotBlank() },
            schemaVersion = 1,
            taxonomyVersion = CatalogDataVersion.current,
            locale = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() },
        )
        val trackTitle = buildQuerySummary(
            filters = filters,
            defaultLabel = buildRecognitionTitle(filters)
                .ifBlank { normalizedCategoryCode ?: "Отслеживание" },
        ).take(80)

        scope.launch {
            val normalizedExtra = trackFilters.extra
                .mapKeys { it.key.trim().lowercase(Locale.ROOT) }
                .mapValues { it.value.trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
            val existing = runCatching { trackRepository.listTracks() }
                .getOrNull()
                ?.firstOrNull { track ->
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

                    val sameMatch =
                        if (normalizedExtra.isNotEmpty()) {
                            true
                        } else {
                            TrackMatchKeyFactory.parse(track.target.spec?.matchKey) ==
                                TrackMatchKeyFactory.parse(effectiveMatchKey)
                        }
                    sameMatch
                }

            val trackId = if (existing != null) {
                Toast.makeText(context, "Отслеживание уже активно", Toast.LENGTH_SHORT).show()
                existing.id
            } else {
                val now = System.currentTimeMillis()
                val created = runCatching {
                    trackRepository.upsertTrack(
                        Track(
                            id = "new",
                            title = trackTitle,
                            categoryCode = normalizedCategoryCode,
                            type = trackType,
                            target = if (trackType == TrackType.PRODUCT) {
                                TrackTarget(
                                    spec = targetSpec,
                                    categoryCode = normalizedCategoryCode,
                                    attributes = trackFilters.extra,
                                    matchKey = effectiveMatchKey,
                                )
                            } else {
                                TrackTarget(
                                    spec = targetSpec,
                                    categoryCode = normalizedCategoryCode,
                                    attributes = trackFilters.extra,
                                )
                            },
                            filters = trackFilters,
                            alertRules = emptyList(),
                            state = TrackState.ACTIVE,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                }.getOrNull()

                if (created == null) {
                    Toast.makeText(context, "Не удалось создать отслеживание", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(context, "Отслеживание включено", Toast.LENGTH_SHORT).show()
                created.id
            }

            trackConversionEvent(actionCode = "track")
            navController?.navigate(AppRoutes.trackedItemsTop10(trackId))
        }
    }

    fun openFiltersHub() {
        FlowMetrics.markEvent(
            "filters_overlay_open",
            "screen=results active_count=${filters.activeFilterCount()}",
        )
        workingFilters = filters
        sheetTarget = FilterSheetTarget.Draft
        sheetScreen = ResultsSheet.Filters
    }

    fun categorySheetInitialPath(selectedCategoryCode: String?): List<String> =
        buildCategoryPath(selectedCategoryCode, categoriesByCode).dropLast(1)

    fun openCategoryShortcut() {
        workingFilters = filters
        categoryQuery = ""
        categoryTreePath = categorySheetInitialPath(filters.categoryCode)
        sheetTarget = FilterSheetTarget.Applied
        sheetScreen = ResultsSheet.Categories
    }

    LaunchedEffect(payload.mode) {
        if (payload.mode == ResultsMode.Categories) {
            openCategoryShortcut()
        }
    }

    fun openSortSheet() {
        FlowMetrics.markEvent("open_sort_sheet", "screen=results")
        markFeatureFlagUsage(
            decision = sortSheetExplicitApplyDecision,
            action = "open_sort_sheet",
        )
        workingFilters = filters
        sheetTarget = FilterSheetTarget.Applied
        sheetScreen = ResultsSheet.Sort
    }

    fun openViewModeSheet() {
        FlowMetrics.markEvent("open_view_sheet", "screen=results")
        markFeatureFlagUsage(
            decision = viewToggleSegmentedDecision,
            action = "open_view_sheet",
        )
        sheetScreen = ResultsSheet.View
    }

    LaunchedEffect(sheetScreen) {
        if (sheetScreen == ResultsSheet.Location) {
            markFeatureFlagUsage(
                decision = geoRadiusSliderDecision,
                action = "open_location_sheet",
            )
        }
    }

    fun openChipFilterEditor(chipKey: String) {
        workingFilters = filters
        sheetTarget = FilterSheetTarget.Draft
        when {
            chipKey == AppliedChipKey.Category -> {
                categoryQuery = ""
                categoryTreePath = categorySheetInitialPath(filters.categoryCode)
                sheetScreen = ResultsSheet.Categories
            }
            chipKey == AppliedChipKey.Brand -> sheetScreen = ResultsSheet.Brands
            chipKey == AppliedChipKey.Price -> sheetScreen = ResultsSheet.Price
            chipKey == AppliedChipKey.Condition -> sheetScreen = ResultsSheet.Condition
            chipKey == AppliedChipKey.PurchaseFormat -> sheetScreen = ResultsSheet.PurchaseFormat
            chipKey == AppliedChipKey.Location -> sheetScreen = ResultsSheet.Location
            chipKey.startsWith(AppliedChipKey.TypedPrefix) -> {
                activeTypedFacetKey = chipKey.removePrefix(AppliedChipKey.TypedPrefix)
                sheetScreen = ResultsSheet.TypedAttribute
            }
            else -> sheetScreen = ResultsSheet.Filters
        }
    }

    fun clearAppliedChip(chipKey: String) {
        val result = filters.clearAppliedChipWithDependencies(chipKey)
        filters = result.state
        if (sheetTarget == FilterSheetTarget.Applied || sheetScreen == null) {
            workingFilters = result.state
        }
        if (result.invalidatedFilterIds.isNotEmpty()) {
            FlowMetrics.markEvent(
                "filters_dependency_invalidation",
                "screen=results origin=chips changed=$chipKey invalidated=${result.invalidatedFilterIds.joinToString(",")}",
            )
        }
    }

    fun handleResultCardOpenTap(item: ExplainedItem, analyticsPosition: Int) {
        val nowMs = SystemClock.elapsedRealtime()
        val lastTapMs = cardOpenTapMsByOfferId[item.dto.id] ?: Long.MIN_VALUE
        val cooldownHit = nowMs - lastTapMs < RESULTS_CARD_OPEN_COOLDOWN_MS
        val offerOpenUrls = resolveOfferOpenUrlsForResults(item.dto)
        trackClickEvent(
            offerId = item.dto.id,
            position = analyticsPosition,
            hasDeeplink = offerOpenUrls.hasRawDeeplink,
            cooldownHit = cooldownHit,
        )
        if (cooldownHit) {
            Toast.makeText(context, "Подождите перед повторным открытием.", Toast.LENGTH_SHORT).show()
            return
        }
        cardOpenTapMsByOfferId[item.dto.id] = nowMs
        openChatFor(item, analyticsPosition)
    }

    fun openEmptyCategories() {
        if (onOpenCategories != null) {
            onOpenCategories()
            return
        }
        openCategoryShortcut()
    }

    fun openPhotoSearch() {
        val payload = com.example.shoppingassistant.feature.pages.draft.DraftPayload(
            source = com.example.shoppingassistant.feature.pages.draft.DraftSource.Photo,
            mode = com.example.shoppingassistant.feature.pages.draft.DraftMode.Search,
            photoInput = com.example.shoppingassistant.feature.pages.draft.DraftPhotoInput.Camera,
        )
        FlowMetrics.markCreateAction("photo", "search")
        FlowMetrics.startDraft(taps = 1)
        if (onOpenDraft != null) {
            onOpenDraft(payload)
            return
        }
        if (navController == null) return
        navController.navigate(com.example.shoppingassistant.feature.pages.draft.DraftRoutes.build(payload))
    }

    fun resolveCurrentLocationAndApply() {
        scope.launch {
            isResolvingCurrentLocation = true
            val snapshot = resolveCurrentLocationForResults(context)
            isResolvingCurrentLocation = false
            if (snapshot?.city.isNullOrBlank()) {
                Toast.makeText(context, "Не удалось определить текущее местоположение", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val city = snapshot?.city?.trim().orEmpty()
            val lat = snapshot?.lat
            val lon = snapshot?.lon
            workingFilters = workingFilters.copy(
                location = city,
                centerLat = lat,
                centerLon = lon,
            )
            Toast.makeText(context, "Локация определена: $city", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestGeoPermissionFromLocationSheet() {
        val currentPermission = resolveLocationPermissionState(
            context = context,
            requestedBefore = locationPermissionRequested,
        )
        locationPermissionState = currentPermission
        when (currentPermission) {
            ResultsGeoPermissionState.Granted -> {
                resolveCurrentLocationAndApply()
            }
            ResultsGeoPermissionState.DeniedPermanent,
            ResultsGeoPermissionState.CanAsk,
            ResultsGeoPermissionState.DeniedCanAsk,
            -> {
                FlowMetrics.markEvent(
                    "filters_geo_permission_priming_shown",
                    "screen=results origin=geo_radius state=${currentPermission.name.lowercase()}",
                )
                sheetScreen = ResultsSheet.LocationPermissionPriming
            }
        }
    }

    fun openLocationPermissionSettings() {
        FlowMetrics.markEvent("filters_geo_permission_open_settings_tap", "screen=results origin=geo_radius")
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    val backAction = onBack ?: navController?.let { { it.popBackStack(); Unit } }
    val activeFiltersCount = filters.activeFilterCount()
    val appliedChips = remember(filters) { buildAppliedFilterChips(filters) }
    val effectiveViewMode = viewMode.normalized()
    val sortOptions = remember(items, filters.location) {
        buildSortOptions(
            items = items,
            location = filters.location,
        )
    }
    val currentSortAvailable = sortOptions
        .firstOrNull { option -> option.sort == filters.sort }
        ?.enabled
        ?: true
    val categorySummary = filters.categorySummary()
    val brandSummary = filters.brandSummary()
    val priceSummary = filters.priceSummary()
    val recognitionTitle = buildRecognitionTitle(filters)
    val recognitionDetails = buildRecognitionDetails(filters)

    LaunchedEffect(appliedChips.isNotEmpty(), activeFiltersCount) {
        val isVisible = appliedChips.isNotEmpty()
        if (isVisible && !wasAppliedChipsRowVisible) {
            FlowMetrics.markEvent(
                "ui_applied_chips_row_shown",
                "screen=results active_count=$activeFiltersCount",
            )
        } else if (!isVisible && wasAppliedChipsRowVisible) {
            FlowMetrics.markEvent(
                "ui_applied_chips_row_hidden",
                "screen=results",
            )
        }
        wasAppliedChipsRowVisible = isVisible
    }

    val showRecognitionCard = recognitionTitle.isNotBlank() ||
        recognitionDetails.isNotBlank() ||
        filters.categoryCode != null
    val shouldShowEmptyStateImmediate = !isLoading && errorMessage == null && visibleItems.isEmpty()
    var shouldShowEmptyState by remember(criteria) { mutableStateOf(false) }
    val hasActiveFilters = activeFiltersCount > 0
    val emptyTitle = if (hasActiveFilters) {
        stringResource(R.string.results_empty_filters_too_strict_title)
    } else {
        stringResource(R.string.results_empty_no_results_title)
    }
    val emptyMessage = if (hasActiveFilters) {
        stringResource(R.string.results_empty_filters_too_strict_body)
    } else {
        stringResource(R.string.results_empty_no_results_body)
    }
    val emptyPrimaryAction = if (hasActiveFilters) {
        StateAction(
            label = stringResource(R.string.results_empty_action_reset_filters),
            onAction = { filters = filters.clearAllAppliedFilters() },
            type = StateActionType.RESET_FILTERS,
        )
    } else {
        StateAction(
            label = stringResource(R.string.results_empty_action_open_categories),
            onAction = { openEmptyCategories() },
            type = StateActionType.CHANGE_QUERY,
        )
    }
    val emptySecondaryAction = if (hasActiveFilters && (filters.priceMin != null || filters.priceMax != null)) {
        StateAction(
            label = stringResource(R.string.results_empty_action_expand_price),
            onAction = {
                workingFilters = filters
                sheetTarget = FilterSheetTarget.Applied
                sheetScreen = ResultsSheet.Price
            },
            type = StateActionType.CHANGE_QUERY,
        )
    } else if (hasActiveFilters) {
        StateAction(
            label = stringResource(R.string.results_empty_action_choose_category),
            onAction = { openCategoryShortcut() },
            type = StateActionType.CHANGE_QUERY,
        )
    } else {
        StateAction(
            label = stringResource(R.string.results_empty_action_take_photo),
            onAction = { openPhotoSearch() },
            type = StateActionType.CHANGE_QUERY,
        )
    }

    LaunchedEffect(shouldShowEmptyStateImmediate) {
        if (shouldShowEmptyStateImmediate) {
            delay(200)
            shouldShowEmptyState = shouldShowEmptyStateImmediate
        } else {
            shouldShowEmptyState = false
        }
    }

    LaunchedEffect(currentSortAvailable, filters.sort) {
        if (!currentSortAvailable && filters.sort != OfferSort.RANK) {
            val previousSort = filters.sort
            filters = filters.copy(sort = OfferSort.RANK)
            workingFilters = workingFilters.copy(sort = OfferSort.RANK)
            FlowMetrics.markEvent(
                "fallback_sort_applied",
                "screen=results from=${previousSort.name.lowercase()} to=${OfferSort.RANK.name.lowercase()} reason=unavailable",
            )
            Toast.makeText(
                context,
                "Сортировка сброшена: недоступна в этом контексте",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val screenState: ScreenState = when {
        errorMessage != null && visibleItems.isEmpty() -> ScreenState.Error(
            message = errorMessage ?: "Не удалось загрузить предложения",
            primaryAction = StateAction(
                label = stringResource(R.string.state_error_retry),
                onAction = retryLoad,
                type = StateActionType.RETRY,
            ),
            icon = Icons.Outlined.ErrorOutline,
        )
        shouldShowEmptyState -> ScreenState.Empty(
            reason = if (hasActiveFilters) EmptyReason.FILTERS_TOO_STRICT else EmptyReason.NO_RESULTS,
            title = emptyTitle,
            message = emptyMessage,
            primaryAction = emptyPrimaryAction,
            secondaryAction = emptySecondaryAction,
            icon = Icons.Outlined.Search,
        )
        isLoading && visibleItems.isEmpty() -> ScreenState.Loading(LoadingPhase.INITIAL)
        isLoading && visibleItems.isNotEmpty() -> ScreenState.Loading(LoadingPhase.REFRESHING)
        isLoadingMore -> ScreenState.Loading(LoadingPhase.APPEND)
        else -> ScreenState.Content()
    }
    val resultsListState = when {
        errorMessage != null && visibleItems.isEmpty() -> "error"
        showStaleResultsBanner -> "offline_stale"
        shouldShowEmptyState -> "empty"
        isLoading && visibleItems.isEmpty() -> "loading"
        isLoadingMore -> "append_loading"
        else -> "content"
    }
    val resultsListStateReason = when (resultsListState) {
        "empty" -> if (hasActiveFilters) "filters_too_strict" else "no_results"
        "error" -> "load_failed"
        "offline_stale" -> "refresh_failed_with_cache"
        else -> null
    }

    LaunchedEffect(resultsListState, resultsListStateReason) {
        val reasonPart = resultsListStateReason?.let { value -> " reason=$value" }.orEmpty()
        FlowMetrics.markEvent(
            "results_list_state_changed",
            "screen=results state=$resultsListState$reasonPart visible_count=${visibleItems.size} active_filters=$activeFiltersCount",
        )
    }

    val querySummary = buildQuerySummary(
        filters = filters,
        defaultLabel = stringResource(R.string.results_query_summary_default),
    )

    ScreenRoot(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        applySafeInsets = applySafeInsets,
        extraBottomPadding = extraBottomPadding,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LayoutDefaults.HorizontalPadding, vertical = LayoutDefaults.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing),
        ) {
            ResultsTopBar(
                title = "Результаты",
                onBack = backAction,
                onClose = onClose,
                applySafeInsets = !applySafeInsets,
            )

            ResultsQuerySummary(
                summary = querySummary,
                onEditQuery = { (onEditQuery ?: { openFiltersHub() })() },
            )

            ResultsControlsRow(
                activeFiltersCount = activeFiltersCount,
                viewMode = effectiveViewMode,
                onFilters = {
                    FlowMetrics.markEvent("search_action_row_tap", "screen=results control=filters")
                    openFiltersHub()
                },
                onSort = {
                    FlowMetrics.markEvent("search_action_row_tap", "screen=results control=sort")
                    openSortSheet()
                },
                onToggleView = {
                    FlowMetrics.markEvent("search_action_row_tap", "screen=results control=view")
                    openViewModeSheet()
                },
            )

            if (appliedChips.isNotEmpty()) {
                ResultsAppliedChipsRow(
                    chips = appliedChips,
                    onChipTap = { chip ->
                        FlowMetrics.markEvent(
                            "ui_applied_chip_tap",
                            "screen=results chip=${chip.key}",
                        )
                        openChipFilterEditor(chip.key)
                    },
                    onChipClear = { chip ->
                        FlowMetrics.markEvent(
                            "ui_applied_chip_clear",
                            "screen=results chip=${chip.key}",
                        )
                        clearAppliedChip(chip.key)
                    },
                    onClearAll = {
                        FlowMetrics.markEvent(
                            "ui_applied_chips_clear_all",
                            "screen=results active_count=$activeFiltersCount",
                        )
                        filters = filters.clearAllAppliedFilters()
                    },
                )
            }

            catalogLoadError?.let { message ->
                ResultsCatalogErrorBanner(
                    message = message,
                    onRetry = retryCatalogLoad,
                )
            }

            if (showStaleResultsBanner) {
                ResultsStaleResultsBanner(
                    message = "Показаны последние загруженные результаты. Обновление не удалось.",
                    updatedAtLabel = staleLastUpdatedLabel,
                    onRetry = {
                        FlowMetrics.markEvent("ui_offline_stale_banner_retry", "screen=results")
                        retryLoad()
                    },
                )
            }

            if (showRecognitionCard) {
                RecognitionCard(
                    title = recognitionTitle,
                    details = recognitionDetails,
                    category = categorySummary,
                    onCategoryClick = { openCategoryShortcut() },
                    onCopy = {
                        val text = filters.queryText.ifBlank {
                            filters.query?.let { "${it.brand} ${it.model}".trim() }.orEmpty()
                        }
                        if (text.isBlank()) {
                            Toast.makeText(context, "Нет запроса для копирования", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                val clipData = ClipData.newPlainText("query", text.trim())
                                clipboard.setClipEntry(ClipEntry(clipData))
                            }
                            Toast.makeText(context, "Запрос скопирован", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReset = { filters = filters.copy(queryText = "", query = null) },
                    onReport = { Toast.makeText(context, "Спасибо, мы проверим распознавание", Toast.LENGTH_SHORT).show() },
                )
            }

            ResultsActionsRow(
                showCreate = payload.origin != ResultsOrigin.Text,
                hasResults = visibleItems.isNotEmpty(),
                onTrack = { handleTrack() },
                onCreate = {
                    trackConversionEvent(actionCode = "create")
                    payload.onCreate(navController, onOpenDraft, filters.query, filters.queryText, filters.categoryCode)
                },
            )

        StateHost(
            state = screenState,
            contentPadding = contentPadding,
            screenName = "results",
            showAppendOverlay = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            loadingContent = { paddingValues ->
                when (effectiveViewMode) {
                    ResultsViewMode.Grid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 4.dp,
                                end = 4.dp,
                                top = 4.dp,
                                bottom = paddingValues.calculateBottomPadding(),
                            ),
                        ) {
                            repeat(6) { index ->
                                item(key = "results_grid_skeleton_$index") {
                                    OfferCardSkeleton(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    end = 4.dp,
                                    bottom = paddingValues.calculateBottomPadding(),
                                ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(4) {
                                OfferCardSkeleton(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            when (effectiveViewMode) {
                ResultsViewMode.Grid -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 4.dp,
                            end = 4.dp,
                            top = 4.dp,
                            bottom = paddingValues.calculateBottomPadding(),
                        ),
                    ) {
                        gridItemsIndexed(visibleItems, key = { _, item -> item.dto.id }) { idx, item ->
                            val position = idx
                            val offerOpenUrls = resolveOfferOpenUrlsForResults(item.dto)
                            val ui = buildOfferCardUi(
                                item = item,
                                isSaved = savedOfferIds.contains(item.dto.id),
                                onToggleSave = { toggleSavedOffer(item.dto.id) },
                                onOpenDetails = {
                                    handleResultCardOpenTap(item, position)
                                },
                                onOverflowAction = { action -> handleOverflow(action, item) },
                            )
                            OfferCard(
                                ui = ui,
                                modifier = Modifier.fillMaxWidth(),
                                density = CardDensity.Regular,
                                photoPeekEnabled = profileSettings.photoPeekEnabled,
                                overflowActions = CardActionRules.offerOverflowActions(
                                    includeCopyLink = offerOpenUrls.hasRawDeeplink,
                                ),
                            )
                        }
                        if (showLoadMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LoadMoreRow(
                                    isLoading = isLoadingMore,
                                    errorMessage = appendErrorMessage,
                                    onLoadMore = loadMore,
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 4.dp,
                            end = 4.dp,
                            top = 4.dp,
                            bottom = paddingValues.calculateBottomPadding(),
                        ),
                    ) {
                        itemsIndexed(visibleItems, key = { _, item -> item.dto.id }) { idx, item ->
                            val position = idx
                            val offerOpenUrls = resolveOfferOpenUrlsForResults(item.dto)
                            val ui = buildOfferCardUi(
                                item = item,
                                isSaved = savedOfferIds.contains(item.dto.id),
                                onToggleSave = { toggleSavedOffer(item.dto.id) },
                                onOpenDetails = {
                                    handleResultCardOpenTap(item, position)
                                },
                                onOverflowAction = { action -> handleOverflow(action, item) },
                            )
                            OfferCard(
                                ui = ui,
                                modifier = Modifier.fillMaxWidth(),
                                density = when (effectiveViewMode) {
                                    ResultsViewMode.Compact -> CardDensity.Dense
                                    else -> CardDensity.Regular
                                },
                                photoPeekEnabled = profileSettings.photoPeekEnabled,
                                overflowActions = CardActionRules.offerOverflowActions(
                                    includeCopyLink = offerOpenUrls.hasRawDeeplink,
                                ),
                            )
                        }
                        if (showLoadMore) {
                            item {
                                LoadMoreRow(
                                    isLoading = isLoadingMore,
                                    errorMessage = appendErrorMessage,
                                    onLoadMore = loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (sheetScreen != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                when (sheetScreen) {
                    ResultsSheet.Filters -> FlowMetrics.markEvent(
                        "filters_overlay_close_cancel",
                        "screen=results reason=scrim_or_swipe",
                    )

                    ResultsSheet.Sort -> FlowMetrics.markEvent(
                        "dismiss_sort_sheet",
                        "screen=results reason=scrim_or_swipe",
                    )

                    ResultsSheet.View -> FlowMetrics.markEvent(
                        "dismiss_view_sheet",
                        "screen=results reason=scrim_or_swipe",
                    )

                    ResultsSheet.LocationPermissionPriming -> FlowMetrics.markEvent(
                        "filters_geo_permission_priming_secondary_tap",
                        "screen=results origin=geo_radius reason=scrim_or_swipe",
                    )

                    else -> Unit
                }
                sheetScreen = if (sheetScreen == ResultsSheet.LocationPermissionPriming) {
                    ResultsSheet.Location
                } else {
                    null
                }
            },
            sheetState = sheetState,
        ) {
            when (sheetScreen) {
                ResultsSheet.Filters -> FilterHubSheet(
                    filters = workingFilters,
                    facetFilters = facetUiFilters,
                    activeCount = workingFilters.activeFilterCount(),
                    isDirty = workingFilters != filters,
                    isApplyEnabled = workingFilters != filters && !isFiltersApplyRateLimited,
                    onReset = {
                        FlowMetrics.markEvent(
                            "filters_overlay_reset_draft",
                            "screen=results active_count=${workingFilters.activeFilterCount()}",
                        )
                        workingFilters = workingFilters.clearAllAppliedFilters()
                    },
                    onDismiss = {
                        FlowMetrics.markEvent(
                            "filters_overlay_close_cancel",
                            "screen=results reason=close_button",
                        )
                        sheetScreen = null
                    },
                    onOpenSort = {
                        FlowMetrics.markEvent(
                            "filters_overlay_open_detail",
                            "screen=results detail=sort",
                        )
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Sort
                    },
                    onOpenCategory = {
                        FlowMetrics.markEvent(
                            "filters_overlay_open_detail",
                            "screen=results detail=category",
                        )
                        sheetTarget = FilterSheetTarget.Draft
                        categoryQuery = ""
                        categoryTreePath = categorySheetInitialPath(workingFilters.categoryCode)
                        sheetScreen = ResultsSheet.Categories
                    },
                    onOpenFacet = { facetFilter ->
                        FlowMetrics.markEvent(
                            "filters_overlay_open_detail",
                            "screen=results detail=${facetFilter.facetKey}",
                        )
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = when (facetFilter.type) {
                            ResultsFacetFilterType.Brand -> ResultsSheet.Brands
                            ResultsFacetFilterType.PriceRange -> ResultsSheet.Price
                            ResultsFacetFilterType.Condition -> ResultsSheet.Condition
                            ResultsFacetFilterType.DeliveryChannel -> ResultsSheet.PurchaseFormat
                            ResultsFacetFilterType.TypedAttribute -> {
                                activeTypedFacetKey = facetFilter.facetKey
                                ResultsSheet.TypedAttribute
                            }
                            ResultsFacetFilterType.Unsupported -> ResultsSheet.Filters
                        }
                    },
                    onOpenLocation = {
                        FlowMetrics.markEvent(
                            "filters_overlay_open_detail",
                            "screen=results detail=location",
                        )
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Location
                    },
                    onApply = {
                        if (isFiltersApplyRateLimited) {
                            FlowMetrics.markEvent(
                                "filters_overlay_apply_tap_ignored",
                                "screen=results reason=rate_limit",
                            )
                            return@FilterHubSheet
                        }
                        isFiltersApplyRateLimited = true
                        scope.launch {
                            delay(FILTERS_APPLY_RATE_LIMIT_MS)
                            isFiltersApplyRateLimited = false
                        }
                        FlowMetrics.markEvent(
                            "filters_overlay_apply_tap",
                            "screen=results active_count=${workingFilters.activeFilterCount()}",
                        )
                        val appliedCount = workingFilters.activeFilterCount()
                        filters = workingFilters
                        FlowMetrics.markEvent(
                            "filters_overlay_apply_success",
                            "screen=results active_count=$appliedCount",
                        )
                        sheetScreen = null
                    },
                )
                ResultsSheet.View -> ViewModeSheet(
                    current = effectiveViewMode,
                    onSelect = { mode ->
                        FlowMetrics.markEvent(
                            "select_view_mode",
                            "screen=results mode=${mode.name.lowercase()}",
                        )
                        FlowMetrics.markEvent(
                            "apply_view_mode",
                            "screen=results mode=${mode.name.lowercase()}",
                        )
                        viewMode = mode
                        sheetScreen = null
                    },
                    onBack = {
                        FlowMetrics.markEvent(
                            "dismiss_view_sheet",
                            "screen=results reason=back",
                        )
                        sheetScreen = null
                    },
                )
                ResultsSheet.Categories -> CategoryPickerSheet(
                    categories = categories,
                    categoriesByParent = categoriesByParent,
                    categoriesByCode = categoriesByCode,
                    query = categoryQuery,
                    pathCodes = categoryTreePath,
                    selectedCategoryCode = workingFilters.categoryCode,
                    onQueryChange = { categoryQuery = it },
                    onPathChange = { categoryTreePath = it },
                    onSelectCategory = { code, path ->
                        val result = workingFilters.applyCategoryWithDependencies(
                            code = code,
                            path = path,
                        )
                        workingFilters = result.state
                        if (result.invalidatedFilterIds.isNotEmpty()) {
                            FlowMetrics.markEvent(
                                "filters_dependency_invalidation",
                                "screen=results origin=category_select changed=category invalidated=${result.invalidatedFilterIds.joinToString(",")}",
                            )
                        }
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = result.state
                            sheetScreen = null
                        }
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.Brands -> BrandPickerSheet(
                    query = brandQuery,
                    onQueryChange = { brandQuery = it },
                    availableBrands = availableBrandOptions(
                        items = items,
                        query = filters.query,
                        runtimeFacets = runtimeBrandFacets,
                    ),
                    selected = workingFilters.brands,
                    onSelect = { brand ->
                        workingFilters = workingFilters.toggleBrand(brand)
                    },
                    onReset = { workingFilters = workingFilters.copy(brands = emptySet()) },
                    onDone = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = workingFilters
                            sheetScreen = null
                        }
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.Price -> PricePickerSheet(
                    filters = workingFilters,
                    priceBounds = availablePriceBounds(items),
                    onReset = { workingFilters = workingFilters.copy(priceMin = null, priceMax = null) },
                    onApply = { min, max ->
                        workingFilters = workingFilters.copy(priceMin = min, priceMax = max)
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = workingFilters
                            sheetScreen = null
                        }
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.Sort -> SortPickerSheet(
                    current = workingFilters.sort,
                    options = sortOptions,
                    explicitApply = sortSheetExplicitApplyDecision.effectiveValue,
                    onSelect = { sort ->
                        FlowMetrics.markEvent(
                            "select_sort_option",
                            "screen=results sort=${sort.name.lowercase()}",
                        )
                        FlowMetrics.markEvent(
                            "apply_sort",
                            "screen=results sort=${sort.name.lowercase()}",
                        )
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            workingFilters = workingFilters.copy(sort = sort)
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = filters.copy(sort = sort)
                            sheetScreen = null
                        }
                    },
                    onBack = {
                        FlowMetrics.markEvent(
                            "dismiss_sort_sheet",
                            "screen=results reason=back",
                        )
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.PurchaseFormat -> PurchaseFormatSheet(
                    current = workingFilters.purchaseFormat,
                    options = purchaseFormatOptions(
                        runtimeFacets = runtimeDeliveryChannelFacets,
                        current = workingFilters.purchaseFormat,
                    ),
                    onSelect = { format ->
                        workingFilters = workingFilters.copy(purchaseFormat = format)
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = workingFilters
                            sheetScreen = null
                        }
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.Condition -> ConditionSheet(
                    selected = workingFilters.conditions,
                    options = conditionOptions(
                        runtimeFacets = runtimeConditionFacets,
                        selected = workingFilters.conditions,
                    ),
                    onSelect = { option ->
                        workingFilters = workingFilters.withCondition(option)
                    },
                    onReset = { workingFilters = workingFilters.copy(conditions = emptySet()) },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.TypedAttribute -> {
                    val facetKey = activeTypedFacetKey
                    val facetFilter = facetUiFilters.firstOrNull { it.facetKey == facetKey }
                    val definition = facetDefinitions.firstOrNull { definition ->
                        definition.facetKey.trim().lowercase() == facetKey
                    }
                    if (facetFilter == null || facetKey.isNullOrBlank()) {
                        sheetScreen = ResultsSheet.Filters
                    } else {
                        TypedAttributeFilterSheet(
                            title = facetFilter.title,
                            valueType = definition?.valueType,
                            runtimeValues = runtimeAttributeFacets[facetKey].orEmpty(),
                            draft = workingFilters.typedAttributeFilters[facetKey],
                            onApply = { draft ->
                                val updated = workingFilters.typedAttributeFilters.toMutableMap().apply {
                                    if (draft == null) remove(facetKey) else put(facetKey, draft)
                                }
                                workingFilters = workingFilters.copy(
                                    typedAttributeFilters = updated,
                                )
                                if (sheetTarget == FilterSheetTarget.Draft) {
                                    sheetScreen = ResultsSheet.Filters
                                } else {
                                    filters = workingFilters
                                    sheetScreen = null
                                }
                            },
                            onBack = {
                                if (sheetTarget == FilterSheetTarget.Draft) {
                                    sheetScreen = ResultsSheet.Filters
                                } else {
                                    sheetScreen = null
                                }
                            },
                        )
                    }
                }
                ResultsSheet.Location -> LocationSheet(
                    current = workingFilters.location,
                    radiusKm = workingFilters.radiusKm,
                    isResolvingCurrentLocation = isResolvingCurrentLocation,
                    useSliderRadius = geoRadiusSliderDecision.effectiveValue,
                    onApply = { location, radius ->
                        workingFilters = workingFilters.copy(
                            location = location,
                            radiusKm = radius,
                            centerLat = null,
                            centerLon = null,
                        )
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = workingFilters
                            sheetScreen = null
                        }
                    },
                    onReset = {
                        workingFilters = workingFilters.copy(
                            location = null,
                            radiusKm = null,
                            centerLat = null,
                            centerLon = null,
                        )
                    },
                    onUseCurrentLocation = { requestGeoPermissionFromLocationSheet() },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.LocationPermissionPriming -> GeoPermissionPrimingSheet(
                    permissionState = locationPermissionState,
                    onPrimary = {
                        FlowMetrics.markEvent(
                            "filters_geo_permission_priming_primary_tap",
                            "screen=results origin=geo_radius state=${locationPermissionState.name.lowercase()}",
                        )
                        locationPermissionRequested = true
                        if (locationPermissionState == ResultsGeoPermissionState.DeniedPermanent) {
                            openLocationPermissionSettings()
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onSecondary = {
                        FlowMetrics.markEvent(
                            "filters_geo_permission_priming_secondary_tap",
                            "screen=results origin=geo_radius reason=not_now",
                        )
                        sheetScreen = ResultsSheet.Location
                    },
                    onDismiss = {
                        FlowMetrics.markEvent(
                            "filters_geo_permission_priming_secondary_tap",
                            "screen=results origin=geo_radius reason=dismiss",
                        )
                        sheetScreen = ResultsSheet.Location
                    },
                )
                null -> Unit
            }
        }
    }
}
}

private fun ResultsPayload.onCreate(
    navController: NavHostController?,
    onOpenDraft: ((com.example.shoppingassistant.feature.pages.draft.DraftPayload) -> Unit)?,
    query: NormalizedQuery?,
    queryText: String,
    categoryCode: String?,
) {
    val payload = com.example.shoppingassistant.feature.pages.draft.DraftPayload(
        source = com.example.shoppingassistant.feature.pages.draft.DraftSource.Text,
        mode = com.example.shoppingassistant.feature.pages.draft.DraftMode.Create,
        query = query,
        queryText = queryText,
        categoryCode = categoryCode,
    )
    FlowMetrics.markCreateAction("text", "create")
    FlowMetrics.startDraft(taps = 1)
    if (onOpenDraft != null) {
        onOpenDraft(payload)
        return
    }
    if (navController == null) return
    navController.navigate(com.example.shoppingassistant.feature.pages.draft.DraftRoutes.build(payload))
}

@Composable
private fun ResultsTopBar(
    title: String,
    onBack: (() -> Unit)?,
    onClose: (() -> Unit)?,
    applySafeInsets: Boolean,
) {
    // Results contract: top bar stays navigation-only, without photo/link/voice entry actions.
    AppTopBar(
        title = title,
        onBack = onBack,
        onClose = if (onBack == null) onClose else null,
        applySafeInsets = applySafeInsets,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
    )
}

@Composable
private fun ResultsControlsRow(
    activeFiltersCount: Int,
    viewMode: ResultsViewMode,
    onFilters: () -> Unit,
    onSort: () -> Unit,
    onToggleView: () -> Unit,
) {
    val filtersLabel = if (activeFiltersCount > 0) "Фильтры ($activeFiltersCount)" else "Фильтры"
    val viewLabel = when (viewMode) {
        ResultsViewMode.List -> "Вид: список"
        ResultsViewMode.Grid -> "Вид: сетка"
        ResultsViewMode.Compact -> "Вид: компактно"
        ResultsViewMode.Dense -> "Вид: компактно"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChipButton(
            label = filtersLabel,
            onClick = onFilters,
            testTag = "results_control_filters",
        )
        FilterChipButton(
            label = "Сортировка",
            onClick = onSort,
            testTag = "results_control_sort",
        )
        Spacer(modifier = Modifier.weight(1f))
        FilterChipButton(
            label = viewLabel,
            onClick = onToggleView,
            testTag = "results_control_view",
        )
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CategoryActionsRow(
    selectedCount: Int,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Выбрано: $selectedCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Сбросить") }
        Button(
            onClick = onDone,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Готово") }
    }
}

@Composable
private fun ResultsActionsRow(
    showCreate: Boolean,
    hasResults: Boolean,
    onTrack: () -> Unit,
    onCreate: () -> Unit,
) {
    val trackLabel = if (hasResults) "Отслеживать" else "Создать отслеживание"
    val buttonColors = if (hasResults) {
        null
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasResults) {
                OutlinedButton(
                    onClick = onTrack,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("results_action_track"),
                ) {
                    Text(trackLabel)
                }
            } else {
                Button(
                    onClick = onTrack,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("results_action_track"),
                    colors = buttonColors ?: ButtonDefaults.buttonColors(),
                ) {
                    Text(trackLabel)
                }
            }
            if (showCreate) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("results_action_create"),
                ) {
                    Text("Создать объявление")
                }
            }
        }
        if (!hasResults) {
            Text(
                text = "Уведомим, когда появятся подходящие предложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsQuerySummary(
    summary: String,
    onEditQuery: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEditQuery) {
            Text(stringResource(R.string.results_query_edit_action))
        }
    }
}

@Composable
private fun ResultsAppliedChipsRow(
    chips: List<AppliedFilterChipUi>,
    onChipTap: (AppliedFilterChipUi) -> Unit,
    onChipClear: (AppliedFilterChipUi) -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips, key = { chip -> chip.key }) { chip ->
                AppliedFilterChip(
                    chip = chip,
                    onTap = { onChipTap(chip) },
                    onClear = { onChipClear(chip) },
                )
            }
        }
        TextButton(
            onClick = onClearAll,
            enabled = chips.any { it.removable },
        ) {
            Text("Сбросить все")
        }
    }
}

@Composable
internal fun ResultsCatalogErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_catalog_error_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("results_catalog_error_retry"),
            ) {
                Text(stringResource(R.string.state_error_retry))
            }
        }
    }
}

@Composable
internal fun ResultsStaleResultsBanner(
    message: String,
    updatedAtLabel: String? = null,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_stale_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (!updatedAtLabel.isNullOrBlank()) {
                    Text(
                        text = updatedAtLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                    )
                }
            }
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("results_stale_banner_retry"),
            ) {
                Text(stringResource(R.string.state_error_retry))
            }
        }
    }
}

@Composable
private fun AppliedFilterChip(
    chip: AppliedFilterChipUi,
    onTap: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.clickable(onClick = onTap),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${chip.label}: ${chip.value}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sizeIn(maxWidth = 220.dp),
            )
            if (chip.removable) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Удалить фильтр ${chip.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionCard(
    title: String,
    details: String,
    category: String,
    onCategoryClick: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    onReport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Распознано",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Действия")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Скопировать запрос") },
                            onClick = {
                                expanded = false
                                onCopy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Сбросить распознавание") },
                            onClick = {
                                expanded = false
                                onReset()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Сообщить об ошибке") },
                            onClick = {
                                expanded = false
                                onReport()
                            },
                        )
                    }
                }
            }
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Категория: $category",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onCategoryClick)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SheetTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Сбросить",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onBack ?: onClose ?: {},
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            val icon = if (onBack != null) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close
            Icon(icon, contentDescription = "Назад")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onReset != null) {
            TextButton(onClick = onReset) {
                Text(resetLabel)
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun FilterHubRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterHubSheet(
    filters: FilterState,
    facetFilters: List<ResultsFacetFilter>,
    activeCount: Int,
    isDirty: Boolean,
    isApplyEnabled: Boolean,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenFacet: (ResultsFacetFilter) -> Unit,
    onOpenLocation: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(
            title = "Фильтры",
            onClose = onDismiss,
        )
        if (activeCount > 0) {
            Text(
                text = "Активно: $activeCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { FilterHubRow("Сортировка", sortLabel(filters.sort), onOpenSort) }
            item { FilterHubRow("Категория", filters.categorySummary(), onOpenCategory) }
            items(
                items = facetFilters,
                key = { facetFilter -> facetFilter.facetKey.lowercase() },
            ) { facetFilter ->
                FilterHubRow(
                    title = facetFilter.title,
                    summary = filters.summaryForFacet(facetFilter),
                    onClick = { onOpenFacet(facetFilter) },
                    enabled = facetFilter.type != ResultsFacetFilterType.Unsupported,
                )
            }
            item { FilterHubRow("Где находится / Радиус", filters.locationSummary(), onOpenLocation) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onReset,
                enabled = activeCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .testTag("results_filters_reset"),
            ) {
                Text("Сбросить")
            }
            Button(
                onClick = onApply,
                enabled = isDirty && isApplyEnabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .testTag("results_filters_apply"),
            ) {
                Text("Показать результаты")
            }
        }
    }
}

@Composable
private fun CategoryPickerSheet(
    categories: List<Category>,
    categoriesByParent: Map<String?, List<Category>>,
    categoriesByCode: Map<String, Category>,
    query: String,
    pathCodes: List<String>,
    selectedCategoryCode: String?,
    onQueryChange: (String) -> Unit,
    onPathChange: (List<String>) -> Unit,
    onSelectCategory: (String?, List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val searchText = query.trim()
    val isSearching = searchText.isNotBlank()
    val currentParent = if (isSearching) null else pathCodes.lastOrNull()
    val list = if (isSearching) {
        categories.filter { category ->
            val title = category.title ?: category.code
            title.contains(searchText, ignoreCase = true)
        }.sortedBy { it.title ?: it.code }
    } else {
        categoriesByParent[currentParent].orEmpty()
            .sortedBy { it.title ?: it.code }
    }
    val breadcrumb = if (pathCodes.isNotEmpty()) {
        pathCodes.joinToString(" → ") { code -> categoriesByCode[code]?.title ?: code }
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val handleBack = {
            if (!isSearching && pathCodes.isNotEmpty()) {
                onPathChange(pathCodes.dropLast(1))
            } else {
                onBack()
            }
        }
        SheetTopBar(title = "Категории", onBack = handleBack)
        if (!isSearching && breadcrumb.isNotBlank()) {
            Text(
                text = breadcrumb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isSearching && pathCodes.isEmpty()) {
                item {
                    CategoryOptionRow(
                        title = "Все категории",
                        selected = selectedCategoryCode == null,
                        hasChildren = false,
                        onClick = { onSelectCategory(null, emptyList()) },
                    )
                }
            }
            items(list, key = { it.code }) { category ->
                val hasChildren = categoriesByParent[category.code].orEmpty().isNotEmpty()
                val isSelected = category.code == selectedCategoryCode
                val subtitle = if (isSearching) {
                    categoryPathTitles(category.code, categoriesByCode).joinToString(" → ")
                } else null
                CategoryOptionRow(
                    title = category.title ?: category.code,
                    subtitle = subtitle,
                    selected = isSelected,
                    hasChildren = hasChildren,
                    onClick = {
                        if (hasChildren) {
                            val nextPath = if (isSearching) {
                                buildCategoryPath(category.code, categoriesByCode)
                            } else {
                                pathCodes + category.code
                            }
                            onPathChange(nextPath)
                            if (isSearching) onQueryChange("")
                        } else {
                            onSelectCategory(category.code, categoryPathTitles(category.code, categoriesByCode))
                        }
                    },
                )
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        text = "Категории не найдены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        CategorySearchField(
            value = query,
            placeholder = "Поиск по категориям",
            testTag = "results_category_search",
            onValueChange = onQueryChange,
        )
    }
}

@Composable
private fun CategoryOptionRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    hasChildren: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.primaryContainer else colors.surfaceVariant
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    val subtitleColor = if (selected) colors.onPrimaryContainer.copy(alpha = 0.82f) else colors.onSurfaceVariant
    val border = if (selected) colors.primary.copy(alpha = 0.28f) else colors.outlineVariant.copy(alpha = 0.55f)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasChildren) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = "Внутрь",
                    tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            } else if (selected) {
                Text(
                    text = "Выбрано",
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                )
            }
        }
    }
}

@Composable
private fun BrandPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    availableBrands: List<BrandOptionItem>,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val searchText = query.trim()
    fun isSelected(name: String): Boolean = selected.any { value -> value.equals(name, ignoreCase = true) }
    val filtered = if (searchText.isBlank()) {
        availableBrands
    } else {
        availableBrands.filter { it.name.contains(searchText, ignoreCase = true) }
    }
    val popular = availableBrands
        .sortedWith(compareByDescending<BrandOptionItem> { it.count ?: 0 }.thenBy { it.name.lowercase() })
        .take(8)
    val grouped = filtered
        .groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
        .toSortedMap()
        .mapValues { (_, options) ->
            options.sortedWith(
                compareByDescending<BrandOptionItem> { option -> isSelected(option.name) }
                    .thenByDescending { option -> option.count ?: 0 }
                    .thenBy { option -> option.name.lowercase() },
            )
        }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Бренд", onBack = onBack)
        CategorySearchField(
            value = query,
            placeholder = "Искать среди брендов",
            testTag = "results_brand_search",
            onValueChange = onQueryChange,
        )
        if (searchText.isBlank() && popular.isNotEmpty()) {
            Text(
                text = "Популярные бренды",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("results_brand_popular_row"),
            ) {
                items(popular, key = { option -> option.name.lowercase() }) { option ->
                    val selectedOption = isSelected(option.name)
                    val chipLabel = option.count?.takeIf { it > 0 }?.let { count ->
                        "${option.name} ($count)"
                    } ?: option.name
                    CategoryChip(
                        text = chipLabel,
                        onClick = { onSelect(option.name) },
                        highlighted = selectedOption,
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            grouped.forEach { (letter, brands) ->
                item {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(brands, key = { option -> option.name.lowercase() }) { option ->
                    BrandRow(
                        brand = option.name,
                        count = option.count,
                        selected = isSelected(option.name),
                        onClick = { onSelect(option.name) },
                    )
                }
            }
            if (grouped.isEmpty()) {
                item {
                    Text(
                        text = "Бренды не найдены",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        CategoryActionsRow(
            selectedCount = selected.size,
            onReset = onReset,
            onDone = onDone,
        )
    }
}

@Composable
private fun BrandRow(
    brand: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text = count?.takeIf { it >= 0 }?.let { value -> "$brand ($value)" } ?: brand,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PricePickerSheet(
    filters: FilterState,
    priceBounds: PriceBounds?,
    onReset: () -> Unit,
    onApply: (Int?, Int?) -> Unit,
    onBack: () -> Unit,
) {
    var minText by remember(filters.priceMin) { mutableStateOf(filters.priceMin?.toString().orEmpty()) }
    var maxText by remember(filters.priceMax) { mutableStateOf(filters.priceMax?.toString().orEmpty()) }
    val minValue = minText.toIntOrNull()
    val maxValue = maxText.toIntOrNull()
    val invalid = minValue != null && maxValue != null && minValue > maxValue
    val quickRanges = quickPriceRanges(priceBounds)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(title = "Цена", onBack = onBack, onReset = onReset, resetLabel = "Очистить")
        if (quickRanges.isNotEmpty()) {
            Text(
                text = "Популярные диапазоны",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickRanges) { range ->
                    CategoryChip(
                        text = range.label,
                        onClick = {
                            minText = range.min?.toString().orEmpty()
                            maxText = range.max?.toString().orEmpty()
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = minText,
                onValueChange = { minText = it.filter { ch -> ch.isDigit() } },
                placeholder = { Text("Мин") },
                singleLine = true,
                trailingIcon = {
                    Text(
                        text = "₽",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("results_price_min"),
            )
            TextField(
                value = maxText,
                onValueChange = { maxText = it.filter { ch -> ch.isDigit() } },
                placeholder = { Text("Макс") },
                singleLine = true,
                trailingIcon = {
                    Text(
                        text = "₽",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("results_price_max"),
            )
        }
        if (priceBounds != null && priceBounds.max > priceBounds.min) {
            var sliderRange by remember(priceBounds, minValue, maxValue) {
                mutableStateOf(
                    (minValue ?: priceBounds.min).toFloat()..(maxValue ?: priceBounds.max).toFloat()
                )
            }
            RangeSlider(
                value = sliderRange,
                onValueChange = { range ->
                    sliderRange = range
                    minText = range.start.toInt().toString()
                    maxText = range.endInclusive.toInt().toString()
                },
                valueRange = priceBounds.min.toFloat()..priceBounds.max.toFloat(),
            )
        }
        if (invalid) {
            Text(
                text = "Минимум не может быть больше максимума",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = { onApply(minValue, maxValue) },
            enabled = !invalid,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("results_price_apply"),
        ) {
            Text("Готово")
        }
    }
}

@Composable
private fun SortPickerSheet(
    current: OfferSort,
    options: List<SortOptionItem>,
    explicitApply: Boolean,
    onSelect: (OfferSort) -> Unit,
    onBack: () -> Unit,
) {
    var draftSort by remember(current) { mutableStateOf(current) }
    val selectedSort = if (explicitApply) draftSort else current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .testTag("results_sheet_sort")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Сортировать", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { option -> option.sort.name }) { option ->
                SortOptionRow(
                    label = option.label,
                    selected = selectedSort == option.sort,
                    enabled = option.enabled,
                    supporting = option.disabledReason,
                    onClick = {
                        if (explicitApply) {
                            draftSort = option.sort
                        } else {
                            onSelect(option.sort)
                        }
                    },
                )
            }
        }
        if (explicitApply) {
            Button(
                onClick = { onSelect(draftSort) },
                enabled = draftSort != current,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Применить")
            }
        }
    }
}

@Composable
private fun ViewModeSheet(
    current: ResultsViewMode,
    onSelect: (ResultsViewMode) -> Unit,
    onBack: () -> Unit,
) {
    val options = listOf(
        ResultsViewMode.List to "Список",
        ResultsViewMode.Grid to "Сетка",
        ResultsViewMode.Compact to "Компактно",
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .testTag("results_sheet_view")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Вид", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { (value, label) ->
                SortOptionRow(
                    label = label,
                    selected = current == value,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked
        val tint = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!enabled && !supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PurchaseFormatSheet(
    current: PurchaseFormat,
    options: List<PurchaseFormatOptionItem>,
    onSelect: (PurchaseFormat) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Формат покупки", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { option ->
                val label = if (option.count != null) {
                    "${option.format.label} (${option.count})"
                } else {
                    option.format.label
                }
                SortOptionRow(
                    label = label,
                    selected = option.format == current,
                    onClick = { onSelect(option.format) },
                )
            }
        }
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConditionSheet(
    selected: Set<ConditionOption>,
    options: List<ConditionOptionItem>,
    onSelect: (ConditionOption) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Состояние товара", onBack = onBack, onReset = onReset)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { option ->
                val label = if (option.count != null) {
                    "${option.condition.label} (${option.count})"
                } else {
                    option.condition.label
                }
                SortOptionRow(
                    label = label,
                    selected = option.condition in selected,
                    onClick = { onSelect(option.condition) },
                )
            }
        }
    }
}

@Composable
internal fun TypedAttributeFilterSheet(
    title: String,
    valueType: FacetDataType?,
    runtimeValues: List<ValueFacet>,
    draft: TypedAttributeFilterDraft?,
    onApply: (TypedAttributeFilterDraft?) -> Unit,
    onBack: () -> Unit,
) {
    val operators = remember(valueType) { typedOperatorOptions(valueType) }
    var expanded by remember { mutableStateOf(false) }
    var op by remember(draft, valueType) {
        mutableStateOf(draft?.op ?: operators.firstOrNull() ?: TypedAttributeOperator.EQ)
    }
    var valueText by remember(draft) { mutableStateOf(draft?.value.orEmpty()) }
    var toText by remember(draft) { mutableStateOf(draft?.to.orEmpty()) }
    var valuesCsv by remember(draft) { mutableStateOf(draft?.valuesCsv.orEmpty()) }
    val selectedCsvValues = remember(valuesCsv) { parseFacetCsv(valuesCsv) }
    val runtimeValueOptions = remember(runtimeValues) {
        runtimeValues
            .mapNotNull { facet ->
                val name = facet.name.trim()
                if (name.isEmpty()) null else facet.copy(name = name)
            }
            .distinctBy { facet -> facet.name.lowercase() }
            .take(20)
    }

    val canApply = when (op) {
        TypedAttributeOperator.EXISTS,
        TypedAttributeOperator.NOT_EXISTS,
        -> true

        TypedAttributeOperator.BETWEEN -> valueText.trim().isNotEmpty() || toText.trim().isNotEmpty()
        TypedAttributeOperator.IN -> valuesCsv.trim().isNotEmpty()
        else -> valueText.trim().isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .testTag("results_sheet_filters")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(
            title = title,
            onBack = onBack,
            onReset = {
                valueText = ""
                toText = ""
                valuesCsv = ""
                onApply(null)
            },
            resetLabel = "Очистить",
        )
        Text(
            text = "Оператор",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("results_typed_operator_field")
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = typedOperatorLabel(op), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            operators.forEach { operator ->
                DropdownMenuItem(
                    text = { Text(typedOperatorLabel(operator)) },
                    onClick = {
                        expanded = false
                        op = operator
                    },
                )
            }
        }

        when (op) {
            TypedAttributeOperator.EXISTS,
            TypedAttributeOperator.NOT_EXISTS,
            -> {
                Text(
                    text = "Значение не требуется для выбранного оператора.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TypedAttributeOperator.BETWEEN -> {
                TextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    singleLine = true,
                    label = { Text("От") },
                    keyboardOptions = typedKeyboardOptions(valueType),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_typed_from"),
                )
                TextField(
                    value = toText,
                    onValueChange = { toText = it },
                    singleLine = true,
                    label = { Text("До") },
                    keyboardOptions = typedKeyboardOptions(valueType),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_typed_to"),
                )
            }

            TypedAttributeOperator.IN -> {
                TextField(
                    value = valuesCsv,
                    onValueChange = { valuesCsv = it },
                    singleLine = false,
                    minLines = 2,
                    label = { Text("Значения через запятую") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_typed_values_csv"),
                )
                if (runtimeValueOptions.isNotEmpty()) {
                    Text(
                        text = "Популярные значения",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(runtimeValueOptions, key = { option -> option.id.ifBlank { option.name } }) { option ->
                            val selected = selectedCsvValues.any { value ->
                                value.equals(option.name, ignoreCase = true)
                            }
                            OutlinedButton(
                                onClick = {
                                    valuesCsv = toggleFacetCsvValue(valuesCsv, option.name)
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                            ) {
                                val label = if (option.count > 0) {
                                    "${option.name} (${option.count})"
                                } else {
                                    option.name
                                }
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            else -> {
                TextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    singleLine = true,
                    label = { Text("Значение") },
                    keyboardOptions = typedKeyboardOptions(valueType),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_typed_value"),
                )
                if (runtimeValueOptions.isNotEmpty() &&
                    op != TypedAttributeOperator.GT &&
                    op != TypedAttributeOperator.GTE &&
                    op != TypedAttributeOperator.LT &&
                    op != TypedAttributeOperator.LTE
                ) {
                    Text(
                        text = "Популярные значения",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(runtimeValueOptions, key = { option -> option.id.ifBlank { option.name } }) { option ->
                            val selected = valueText.trim().equals(option.name, ignoreCase = true)
                            OutlinedButton(
                                onClick = { valueText = option.name },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                            ) {
                                val label = if (option.count > 0) {
                                    "${option.name} (${option.count})"
                                } else {
                                    option.name
                                }
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                onApply(
                    TypedAttributeFilterDraft(
                        op = op,
                        value = valueText.trim(),
                        to = toText.trim(),
                        valuesCsv = valuesCsv.trim(),
                    ),
                )
            },
            enabled = canApply,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("results_typed_apply"),
        ) {
            Text("Применить")
        }
    }
}

private fun typedKeyboardOptions(valueType: FacetDataType?): KeyboardOptions =
    when (valueType) {
        FacetDataType.RANGE -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else -> KeyboardOptions.Default
    }

private fun typedOperatorOptions(valueType: FacetDataType?): List<TypedAttributeOperator> =
    when (valueType) {
        FacetDataType.BOOL -> listOf(
            TypedAttributeOperator.EQ,
            TypedAttributeOperator.NEQ,
            TypedAttributeOperator.EXISTS,
            TypedAttributeOperator.NOT_EXISTS,
        )

        FacetDataType.RANGE -> listOf(
            TypedAttributeOperator.EQ,
            TypedAttributeOperator.GT,
            TypedAttributeOperator.GTE,
            TypedAttributeOperator.LT,
            TypedAttributeOperator.LTE,
            TypedAttributeOperator.BETWEEN,
            TypedAttributeOperator.EXISTS,
            TypedAttributeOperator.NOT_EXISTS,
        )

        FacetDataType.ENUM -> listOf(
            TypedAttributeOperator.EQ,
            TypedAttributeOperator.NEQ,
            TypedAttributeOperator.IN,
            TypedAttributeOperator.EXISTS,
            TypedAttributeOperator.NOT_EXISTS,
        )

        FacetDataType.TEXT,
        null,
        -> listOf(
            TypedAttributeOperator.EQ,
            TypedAttributeOperator.CONTAINS,
            TypedAttributeOperator.IN,
            TypedAttributeOperator.EXISTS,
            TypedAttributeOperator.NOT_EXISTS,
        )
    }

private fun typedOperatorLabel(operator: TypedAttributeOperator): String =
    when (operator) {
        TypedAttributeOperator.EQ -> "="
        TypedAttributeOperator.NEQ -> "!="
        TypedAttributeOperator.GT -> ">"
        TypedAttributeOperator.GTE -> ">="
        TypedAttributeOperator.LT -> "<"
        TypedAttributeOperator.LTE -> "<="
        TypedAttributeOperator.BETWEEN -> "between"
        TypedAttributeOperator.IN -> "in"
        TypedAttributeOperator.CONTAINS -> "contains"
        TypedAttributeOperator.EXISTS -> "exists"
        TypedAttributeOperator.NOT_EXISTS -> "not exists"
    }

private fun parseFacetCsv(raw: String): List<String> =
    raw.split(',', ';', '|')
        .map { item -> item.trim() }
        .filter { item -> item.isNotEmpty() }
        .distinctBy { item -> item.lowercase() }

private fun toggleFacetCsvValue(csv: String, value: String): String {
    val normalizedValue = value.trim()
    if (normalizedValue.isEmpty()) return csv
    val current = parseFacetCsv(csv)
    val exists = current.any { item -> item.equals(normalizedValue, ignoreCase = true) }
    val updated = if (exists) {
        current.filterNot { item -> item.equals(normalizedValue, ignoreCase = true) }
    } else {
        current + normalizedValue
    }
    return updated.joinToString(", ")
}

@Composable
private fun LocationSheet(
    current: String?,
    radiusKm: Int?,
    isResolvingCurrentLocation: Boolean,
    useSliderRadius: Boolean,
    onApply: (String?, Int?) -> Unit,
    onReset: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onBack: () -> Unit,
) {
    var locationText by remember(current) { mutableStateOf(current.orEmpty()) }
    val radiusOptions = NEARBY_RADIUS_PRESETS
    val minRadius = radiusOptions.first()
    val maxRadius = radiusOptions.last()
    var radiusValue by remember(radiusKm) {
        mutableStateOf(radiusKm?.coerceIn(minRadius, maxRadius) ?: DEFAULT_NEARBY_RADIUS_KM)
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .testTag("results_sheet_location")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(title = "Где находится", onBack = onBack, onReset = onReset)
        TextField(
            value = locationText,
            onValueChange = { locationText = it },
            singleLine = true,
            placeholder = { Text("Город или страна") },
            modifier = Modifier.fillMaxWidth().testTag("results_location_input"),
        )
        OutlinedButton(
            onClick = onUseCurrentLocation,
            enabled = !isResolvingCurrentLocation,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .testTag("results_location_use_current"),
        ) {
            Text(if (isResolvingCurrentLocation) "Определяем местоположение..." else "Использовать моё местоположение")
        }
        Text(
            text = "Радиус",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (useSliderRadius) {
            val sliderValue = radiusValue.toFloat()
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    val snapped = value.toInt().coerceIn(minRadius, maxRadius)
                    val nearest = radiusOptions.minByOrNull { option -> kotlin.math.abs(option - snapped) } ?: snapped
                    radiusValue = nearest
                },
                valueRange = minRadius.toFloat()..maxRadius.toFloat(),
                steps = (radiusOptions.size - 2).coerceAtLeast(0),
            )
            Text(
                text = "$radiusValue км",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(radiusOptions) { radius ->
                    CategoryChip(
                        text = "$radius км",
                        onClick = { radiusValue = radius },
                        highlighted = radiusValue == radius,
                    )
                }
            }
        }
        Button(
            onClick = {
                val trimmed = locationText.trim().ifBlank { null }
                val effectiveRadius = if (trimmed == null) null else radiusValue
                onApply(trimmed, effectiveRadius)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("results_location_apply"),
        ) {
            Text("Готово")
        }
    }
}

@Composable
private fun GeoPermissionPrimingSheet(
    permissionState: ResultsGeoPermissionState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primaryLabel = if (permissionState == ResultsGeoPermissionState.DeniedPermanent) {
        "Открыть настройки"
    } else {
        "Разрешить"
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(
            title = "Доступ к геолокации",
            onClose = onDismiss,
        )
        Text(
            text = "Чтобы искать предложения рядом, нужен доступ к местоположению.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Мы используем только примерный район поиска и не отправляем точные координаты в аналитику.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (permissionState == ResultsGeoPermissionState.DeniedPermanent) {
            Text(
                text = "Разрешение было ранее отключено. Включите его в настройках приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(primaryLabel)
        }
        TextButton(
            onClick = onSecondary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            Text("Не сейчас")
        }
    }
}


@Composable
private fun CategoryChip(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (highlighted) {
        colors.primaryContainer
    } else {
        colors.surfaceVariant
    }
    val fg = if (highlighted) {
        colors.onPrimaryContainer
    } else {
        colors.onSurfaceVariant
    }
    val border = if (highlighted) {
        colors.primary.copy(alpha = 0.25f)
    } else {
        colors.outlineVariant.copy(alpha = 0.8f)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CategorySearchField(
    value: String,
    placeholder: String,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        maxLines = 1,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = { Text(placeholder) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

private fun buildCriteria(
    filters: FilterState,
    querySessionId: String,
    facetDefinitions: List<FacetDefinition>,
): OfferSearchCriteria? {
    val query = filters.query
    val hasAnyFilter = query != null ||
        !filters.categoryCode.isNullOrBlank() ||
        !filters.facetCollectionCode.isNullOrBlank() ||
        !filters.facetPresetCode.isNullOrBlank() ||
        filters.presetAttributes.isNotEmpty() ||
        filters.brands.isNotEmpty() ||
        filters.priceMin != null ||
        filters.priceMax != null ||
        filters.conditions.isNotEmpty() ||
        filters.typedAttributeFilters.isNotEmpty() ||
        filters.purchaseFormat != PurchaseFormat.All ||
        filters.sellerTrustSignals.isNotEmpty() ||
        !filters.location.isNullOrBlank() ||
        filters.deliverableOnly
    if (!hasAnyFilter) return null
    val locale = Locale.getDefault()
    val safeRadius = filters.radiusKm?.coerceIn(NEARBY_RADIUS_PRESETS.first(), NEARBY_RADIUS_PRESETS.last())
    val resolvedRadius = if (filters.location.isNullOrBlank()) null else safeRadius
    val hasResolvedGeoCenter = resolvedRadius != null &&
        filters.centerLat != null &&
        filters.centerLon != null
    val rawConditions = buildSet {
        filters.conditions.forEach { option -> add(option.value) }
        query?.attributes
            ?.entries
            ?.firstOrNull { (key, _) -> key.equals("condition", ignoreCase = true) }
            ?.value
            ?.asRawString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it) }
    }
    val normalizedConditions = rawConditions
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
    val typedAttributeFilters = buildTypedAttributeFilters(
        drafts = filters.typedAttributeFilters,
        definitions = facetDefinitions,
    )
    val typedKeys = typedAttributeFilters.keys
    val attrs = buildMap {
        putAll(filters.presetAttributes)
        if (query != null) {
            putAll(
                query.attributes
                    .toRawStringAttributes()
                    .filterKeys { key -> !key.equals("condition", ignoreCase = true) },
            )
        }
        if (filters.purchaseFormat != PurchaseFormat.All) {
            put("purchase_format", filters.purchaseFormat.value)
        }
        if (filters.deliverableOnly) {
            put("delivery", "true")
        }
        if (normalizedConditions.size == 1) {
            put("condition", normalizedConditions.first())
        }
        filters.sellerTrustSignals
            .sortedBy { signal -> signal.signalId }
            .forEach { signal ->
                put(signal.attributeKey, "true")
            }
    }
    val deliveryChannels = when (filters.purchaseFormat) {
        PurchaseFormat.Pickup -> listOf("pickup")
        PurchaseFormat.Delivery -> listOf("delivery")
        PurchaseFormat.All -> emptyList()
    }
    return OfferSearchCriteria(
        brand = if (filters.brands.isEmpty()) query?.brand?.takeIf { it.isNotBlank() } else null,
        model = query?.model?.takeIf { it.isNotBlank() },
        brands = filters.brands.toList(),
        categoryCode = filters.categoryCode,
        priceMin = filters.priceMin?.toDouble(),
        priceMax = filters.priceMax?.toDouble(),
        location = filters.location,
        radiusKm = resolvedRadius,
        centerLat = if (hasResolvedGeoCenter) filters.centerLat else null,
        centerLon = if (hasResolvedGeoCenter) filters.centerLon else null,
        geoMode = if (hasResolvedGeoCenter) com.example.shoppingassistant.domain.model.GeoMode.RADIUS else null,
        deliverableOnly = filters.deliverableOnly,
        condition = normalizedConditions.firstOrNull(),
        conditions = normalizedConditions,
        deliveryChannels = deliveryChannels,
        attributes = attrs
            .filterKeys { key -> key !in typedKeys }
            .toTypedAttributesGuess(),
        attributeFilters = typedAttributeFilters,
        userCountry = locale.country.takeIf { it.isNotBlank() },
        userLanguage = locale.language.takeIf { it.isNotBlank() },
        limit = 20,
        sort = filters.sort,
        querySessionId = querySessionId,
        facetCollectionCode = filters.facetCollectionCode,
        facetPresetCode = filters.facetPresetCode,
    )
}

private fun buildTypedAttributeFilters(
    drafts: Map<String, TypedAttributeFilterDraft>,
    definitions: List<FacetDefinition>,
): Map<String, TypedAttributeFilter> {
    if (drafts.isEmpty()) return emptyMap()
    val definitionTypeByKey = definitions.associate { definition ->
        definition.facetKey.trim().lowercase() to definition.valueType
    }
    return drafts.entries
        .mapNotNull { (rawKey, draft) ->
            val key = rawKey.trim().lowercase()
            if (key.isBlank()) return@mapNotNull null
            val valueType = definitionTypeByKey[key]
            val filter = draft.toTypedFilter(valueType) ?: return@mapNotNull null
            key to filter
        }
        .toMap(LinkedHashMap())
}

private fun TypedAttributeFilterDraft.toTypedFilter(
    valueType: FacetDataType?,
): TypedAttributeFilter? {
    val parsedValue = parseTypedAttributeValue(value, valueType)
    val parsedTo = parseTypedAttributeValue(to, valueType)
    val parsedValues = valuesCsv
        .split(',', ';', '|')
        .map { item -> item.trim() }
        .filter { item -> item.isNotEmpty() }
        .mapNotNull { item -> parseTypedAttributeValue(item, valueType) }
        .distinct()

    return when (op) {
        TypedAttributeOperator.EXISTS,
        TypedAttributeOperator.NOT_EXISTS,
        -> TypedAttributeFilter(op = op)

        TypedAttributeOperator.BETWEEN -> {
            if (parsedValue == null && parsedTo == null) return null
            TypedAttributeFilter(op = op, from = parsedValue, to = parsedTo)
        }

        TypedAttributeOperator.IN -> {
            if (parsedValues.isEmpty()) return null
            TypedAttributeFilter(op = op, values = parsedValues)
        }

        else -> {
            if (parsedValue == null) return null
            TypedAttributeFilter(op = op, value = parsedValue)
        }
    }
}

private fun parseTypedAttributeValue(
    raw: String,
    valueType: FacetDataType?,
): TypedAttributeValue? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return when (valueType) {
        FacetDataType.BOOL -> parseBooleanTypedValue(trimmed)
        FacetDataType.RANGE -> parseNumericTypedValue(trimmed)
        FacetDataType.ENUM,
        FacetDataType.TEXT,
        null,
        -> TypedAttributeValue.Text(trimmed)
    }
}

private fun parseNumericTypedValue(raw: String): TypedAttributeValue.Number? {
    val normalized = raw.replace(',', '.')
    val number = normalized.toDoubleOrNull() ?: return null
    return TypedAttributeValue.Number(number)
}

private fun parseBooleanTypedValue(raw: String): TypedAttributeValue.Bool? {
    val normalized = raw.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "true",
        "1",
        "yes",
        "y",
        "да",
        -> TypedAttributeValue.Bool(true)

        "false",
        "0",
        "no",
        "n",
        "нет",
        -> TypedAttributeValue.Bool(false)

        else -> null
    }
}

private fun buildCategoryPath(
    code: String?,
    categoriesByCode: Map<String, Category>,
): List<String> {
    if (code.isNullOrBlank()) return emptyList()
    val out = ArrayList<String>()
    var cur = categoriesByCode[code]
    val seen = HashSet<String>()
    while (cur != null && seen.add(cur.code)) {
        out.add(cur.code)
        cur = cur.parentCode?.let { categoriesByCode[it] }
    }
    return out.asReversed()
}

private data class FilterState(
    val queryText: String = "",
    val query: NormalizedQuery? = null,
    val categoryCode: String? = null,
    val categoryPath: List<String> = emptyList(),
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val presetAttributes: Map<String, String> = emptyMap(),
    val brands: Set<String> = emptySet(),
    val priceMin: Int? = null,
    val priceMax: Int? = null,
    val conditions: Set<ConditionOption> = emptySet(),
    val typedAttributeFilters: Map<String, TypedAttributeFilterDraft> = emptyMap(),
    val purchaseFormat: PurchaseFormat = PurchaseFormat.All,
    val sellerTrustPreset: SellerTrustPreset = SellerTrustPreset.Any,
    val sellerTrustSignals: Set<SellerTrustSignal> = emptySet(),
    val location: String? = null,
    val radiusKm: Int? = null,
    val centerLat: Double? = null,
    val centerLon: Double? = null,
    val deliverableOnly: Boolean = false,
    val sort: OfferSort = OfferSort.RANK,
)

private enum class ResultsSheet {
    Filters,
    View,
    Categories,
    Brands,
    Price,
    Sort,
    PurchaseFormat,
    Condition,
    SellerTrust,
    TypedAttribute,
    Location,
    LocationPermissionPriming,
}

private enum class FilterSheetTarget { Applied, Draft }

private enum class ResultsGeoPermissionState {
    Granted,
    CanAsk,
    DeniedCanAsk,
    DeniedPermanent,
}

private enum class ResultsViewMode {
    List,
    Grid,
    Compact,
    Dense,
    ;
}

private fun ResultsViewMode.normalized(): ResultsViewMode = when (this) {
    ResultsViewMode.Dense -> ResultsViewMode.Compact
    else -> this
}

private object AppliedChipKey {
    const val Category = "category"
    const val Brand = "brand"
    const val Price = "price"
    const val Condition = "condition"
    const val PurchaseFormat = "purchase_format"
    const val SellerTrust = "seller_trust"
    const val Location = "location"
    const val TypedPrefix = "typed:"
}

private data class AppliedFilterChipUi(
    val key: String,
    val label: String,
    val value: String,
    val removable: Boolean = true,
)

private enum class PurchaseFormat(
    val label: String,
    val value: String,
) {
    All("Любой", "any"),
    Pickup("Самовывоз", "pickup"),
    Delivery("Доставка", "delivery"),
}

private enum class ConditionOption(
    val label: String,
    val value: String,
) {
    New("Новый", "new"),
    LikeNew("Как новый", "like_new"),
    Used("Б/У", "used"),
}

private enum class SellerTrustSignal(
    val signalId: String,
    val title: String,
    val description: String,
    val attributeKey: String,
    val unsupportedHint: String,
    val partialHint: String,
) {
    VerifiedSeller(
        signalId = "VERIFIED_SELLER",
        title = "Проверенный продавец",
        description = "Профиль или источник подтверждён.",
        attributeKey = "verified_seller",
        unsupportedHint = "Для текущей выдачи нет стабильных данных о верификации.",
        partialHint = "Доступно не у всех продавцов.",
    ),
    HighRating(
        signalId = "HIGH_RATING",
        title = "Высокий рейтинг (>=4.5)",
        description = "Отбирает продавцов с высоким рейтингом.",
        attributeKey = "high_rating",
        unsupportedHint = "Для текущей выдачи нет рейтингов продавцов.",
        partialHint = "Рейтинг есть не у всех продавцов.",
    ),
    LowDisputeRate(
        signalId = "LOW_DISPUTE_RATE",
        title = "Низкий риск споров",
        description = "Опирается на trust score источника.",
        attributeKey = "low_dispute_rate",
        unsupportedHint = "Для текущей выдачи нет trust score.",
        partialHint = "Trust score есть не у всех продавцов.",
    ),
    ReturnAvailable(
        signalId = "RETURN_AVAILABLE",
        title = "Есть возврат/гарантия",
        description = "У продавца доступны сигналы возврата или гарантии.",
        attributeKey = "return_available",
        unsupportedHint = "Для текущей выдачи нет сигналов возврата/гарантии.",
        partialHint = "Сигналы возврата есть не у всех продавцов.",
    ),
    ProfileAge90d(
        signalId = "PROFILE_AGE_90D",
        title = "Профиль старше 90 дней",
        description = "Продавец активен на площадке не менее 90 дней.",
        attributeKey = "profile_age_90d",
        unsupportedHint = "Данных о возрасте профиля пока нет в модели.",
        partialHint = "Данные о возрасте профиля частичные.",
    ),
}

private enum class SellerTrustPreset(
    val presetId: String,
    val label: String,
    val shortDescription: String,
    val signals: Set<SellerTrustSignal>,
) {
    Any(
        presetId = "ANY",
        label = "Любой",
        shortDescription = "Без ограничений",
        signals = emptySet(),
    ),
    Balanced(
        presetId = "BALANCED",
        label = "Безопаснее",
        shortDescription = "Проверенный продавец + высокий рейтинг",
        signals = setOf(SellerTrustSignal.VerifiedSeller, SellerTrustSignal.HighRating),
    ),
    Strict(
        presetId = "STRICT",
        label = "Самое надёжное",
        shortDescription = "Добавляет низкий риск споров",
        signals = setOf(
            SellerTrustSignal.VerifiedSeller,
            SellerTrustSignal.HighRating,
            SellerTrustSignal.LowDisputeRate,
        ),
    ),
    VerifiedOnly(
        presetId = "VERIFIED_ONLY",
        label = "Только проверенные",
        shortDescription = "Только верифицированные продавцы",
        signals = setOf(SellerTrustSignal.VerifiedSeller),
    ),
    Custom(
        presetId = "CUSTOM",
        label = "Пользовательский",
        shortDescription = "Ручная комбинация сигналов",
        signals = emptySet(),
    ),
}

private data class SellerTrustSignalStats(
    val signal: SellerTrustSignal,
    val totalCount: Int,
    val knownCount: Int,
    val positiveCount: Int,
    val available: Boolean,
    val partial: Boolean,
    val supportingText: String,
)

private data class SellerTrustSignalOption(
    val signal: SellerTrustSignal,
    val selected: Boolean,
    val enabled: Boolean,
    val count: Int?,
    val supportingText: String,
)

private data class PurchaseFormatOptionItem(
    val format: PurchaseFormat,
    val count: Int? = null,
)

private data class ConditionOptionItem(
    val condition: ConditionOption,
    val count: Int? = null,
)

internal data class TypedAttributeFilterDraft(
    val op: TypedAttributeOperator,
    val value: String = "",
    val to: String = "",
    val valuesCsv: String = "",
)

internal val resultsFacetTypes: Set<OfferFacetType> = setOf(
    OfferFacetType.BRAND,
    OfferFacetType.CONDITION,
    OfferFacetType.DELIVERY_CHANNEL,
)

private val systemFacetKeys: Set<String> = setOf(
    "brand",
    "price",
    "price_rub",
    "condition",
    "delivery_channel",
    "delivery",
    "purchase_format",
    "seller_trust",
)

internal fun OfferSearchCriteria.toResultsFacetedRequest(
    includeRuntimeFacets: Boolean = true,
    facetDefinitions: List<FacetDefinition> = emptyList(),
): OfferSearchWithFacetsRequest {
    val facets = if (includeRuntimeFacets) resultsFacetTypes else emptySet()
    val attributeFacetKeys = if (!includeRuntimeFacets) {
        emptySet()
    } else {
        facetDefinitions
            .asSequence()
            .filterNot { definition -> definition.ui.hidden }
            .map { definition -> definition.facetKey.trim().lowercase() }
            .filter { key -> key.isNotBlank() }
            .filterNot { key -> key in systemFacetKeys }
            .toCollection(LinkedHashSet())
    }
    return OfferSearchWithFacetsRequest(
        criteria = this,
        facets = facets,
        excludeFacetFilters = facets,
        attributeFacetKeys = attributeFacetKeys,
    )
}

private data class PriceBounds(
    val min: Int,
    val max: Int,
)

private data class PriceRangeOption(
    val label: String,
    val min: Int?,
    val max: Int?,
)

private fun initialFilterState(payload: ResultsPayload): FilterState =
    FilterState(
        queryText = payload.queryText,
        query = payload.query,
        categoryCode = payload.categoryCode,
        facetCollectionCode = payload.facetCollectionCode,
        facetPresetCode = payload.facetPresetCode,
        location = payload.location,
        radiusKm = payload.radiusKm,
        conditions = mapConditionOptions(payload.conditions),
        sort = payload.sort,
    )

private fun mapConditionOptions(raw: List<String>): Set<ConditionOption> {
    if (raw.isEmpty()) return emptySet()
    val normalized = raw
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    if (normalized.isEmpty()) return emptySet()
    return ConditionOption.entries
        .filter { option ->
            option.value.lowercase() in normalized ||
                option.label.lowercase() in normalized ||
                (option == ConditionOption.Used && normalized.any { it in setOf("б/у", "бу", "used") }) ||
                (option == ConditionOption.New && normalized.any { it in setOf("новый", "new") }) ||
                (option == ConditionOption.LikeNew && normalized.any { it in setOf("как новый", "like_new", "likenew") })
        }
        .toSet()
}

private fun FilterState.resetNonQuery(): FilterState =
    copy(
        categoryCode = null,
        categoryPath = emptyList(),
        facetCollectionCode = null,
        facetPresetCode = null,
        presetAttributes = emptyMap(),
        brands = emptySet(),
        priceMin = null,
        priceMax = null,
        conditions = emptySet(),
        typedAttributeFilters = emptyMap(),
        purchaseFormat = PurchaseFormat.All,
        sellerTrustPreset = SellerTrustPreset.Any,
        sellerTrustSignals = emptySet(),
        location = null,
        radiusKm = null,
        centerLat = null,
        centerLon = null,
        deliverableOnly = false,
        sort = OfferSort.RANK,
    )

private fun FilterState.clearAllAppliedFilters(): FilterState {
    val currentSort = sort
    return resetNonQuery().copy(sort = currentSort)
}

private fun FilterState.activeFilterCount(): Int {
    var count = 0
    if (!categoryCode.isNullOrBlank()) count += 1
    if (presetAttributes.isNotEmpty()) count += 1
    if (brands.isNotEmpty()) count += 1
    if (priceMin != null || priceMax != null) count += 1
    if (conditions.isNotEmpty()) count += 1
    if (typedAttributeFilters.isNotEmpty()) count += typedAttributeFilters.size
    if (purchaseFormat != PurchaseFormat.All) count += 1
    if (sellerTrustSignals.isNotEmpty()) count += 1
    if (!location.isNullOrBlank()) count += 1
    if (deliverableOnly) count += 1
    return count
}

private fun FilterState.categorySummary(): String =
    when {
        categoryPath.isNotEmpty() -> categoryPath.joinToString(" → ")
        !categoryCode.isNullOrBlank() -> categoryCode
        else -> "Все категории"
    }

private fun FilterState.brandSummary(): String {
    if (brands.isEmpty()) return "Не выбрано"
    val list = brands.sorted()
    val preview = list.take(2).joinToString(", ")
    return if (list.size <= 2) preview else "$preview (${list.size})"
}

private fun FilterState.priceSummary(): String {
    return when {
        priceMin == null && priceMax == null -> "Любая"
        priceMin != null && priceMax != null -> "${formatPrice(priceMin)}–${formatPrice(priceMax)}"
        priceMin != null -> "от ${formatPrice(priceMin)}"
        else -> "до ${formatPrice(priceMax ?: 0)}"
    }
}

private fun FilterState.conditionsSummary(): String {
    if (conditions.isEmpty()) return "Любое"
    val list = conditions.map { it.label }.sorted()
    val preview = list.take(2).joinToString(", ")
    return if (list.size <= 2) preview else "$preview (${list.size})"
}

private fun FilterState.purchaseFormatSummary(): String =
    when (purchaseFormat) {
        PurchaseFormat.All -> "Любой"
        else -> purchaseFormat.label
    }

private fun FilterState.sellerTrustSummary(): String {
    if (sellerTrustSignals.isEmpty()) return "Любой"
    return when (sellerTrustPreset) {
        SellerTrustPreset.Any -> "Любой"
        SellerTrustPreset.Custom -> "Пользовательский (${sellerTrustSignals.size})"
        else -> sellerTrustPreset.label
    }
}

private fun FilterState.typedFilterSummary(facetKey: String): String {
    val draft = typedAttributeFilters[facetKey] ?: return "Не задано"
    return when (draft.op) {
        TypedAttributeOperator.EXISTS -> "заполнено"
        TypedAttributeOperator.NOT_EXISTS -> "не заполнено"
        TypedAttributeOperator.BETWEEN -> {
            val left = draft.value.ifBlank { "…" }
            val right = draft.to.ifBlank { "…" }
            "$left..$right"
        }
        TypedAttributeOperator.IN -> draft.valuesCsv.ifBlank { "в списке (…) " }.take(40)
        else -> "${typedOperatorLabel(draft.op)} ${draft.value}".trim().take(40)
    }
}

private fun FilterState.locationSummary(): String {
    val locationText = location?.trim().orEmpty()
    if (locationText.isBlank()) return "Любое"
    val safeRadius = radiusKm?.coerceIn(NEARBY_RADIUS_PRESETS.first(), NEARBY_RADIUS_PRESETS.last())
    return safeRadius?.let { "$locationText · $it км" } ?: locationText
}

private fun buildAppliedFilterChips(filters: FilterState): List<AppliedFilterChipUi> {
    val chips = mutableListOf<AppliedFilterChipUi>()

    if (!filters.categoryCode.isNullOrBlank()) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Category,
            label = "Категория",
            value = filters.categorySummary(),
        )
    }

    if (filters.brands.isNotEmpty()) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Brand,
            label = "Бренд",
            value = filters.brandSummary(),
        )
    }

    if (filters.priceMin != null || filters.priceMax != null) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Price,
            label = "Цена",
            value = filters.priceSummary(),
        )
    }

    if (filters.conditions.isNotEmpty()) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Condition,
            label = "Состояние",
            value = filters.conditionsSummary(),
        )
    }

    if (filters.purchaseFormat != PurchaseFormat.All || filters.deliverableOnly) {
        val purchaseParts = buildList {
            if (filters.purchaseFormat != PurchaseFormat.All) {
                add(filters.purchaseFormat.label)
            }
            if (filters.deliverableOnly) {
                add("Только доставка")
            }
        }
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.PurchaseFormat,
            label = "Получение",
            value = purchaseParts.joinToString(" · ").ifBlank { "Любой" },
        )
    }

    if (filters.sellerTrustSignals.isNotEmpty()) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.SellerTrust,
            label = "Доверие",
            value = filters.sellerTrustSummary(),
        )
    }

    if (!filters.location.isNullOrBlank()) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Location,
            label = "Локация",
            value = filters.locationSummary(),
        )
    }

    filters.typedAttributeFilters
        .toSortedMap()
        .forEach { (facetKey, _) ->
            val normalizedKey = facetKey.trim()
            if (normalizedKey.isBlank()) return@forEach
            val title = normalizedKey
                .replace('_', ' ')
                .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
            chips += AppliedFilterChipUi(
                key = "${AppliedChipKey.TypedPrefix}$normalizedKey",
                label = title,
                value = filters.typedFilterSummary(normalizedKey),
            )
        }

    return chips
}

private data class FilterDependencyResult(
    val state: FilterState,
    val invalidatedFilterIds: List<String> = emptyList(),
)

private data class FilterDependencyContext(
    val allowedTypedFacetKeys: Set<String> = emptySet(),
    val availableSellerTrustSignals: Set<SellerTrustSignal> = SellerTrustSignal.entries.toSet(),
)

private fun FilterState.normalizeWithDependencies(
    context: FilterDependencyContext,
): FilterDependencyResult {
    val invalidated = linkedSetOf<String>()
    var next = this

    if (next.categoryCode.isNullOrBlank()) {
        if (!next.facetCollectionCode.isNullOrBlank()) invalidated += "facet_collection"
        if (!next.facetPresetCode.isNullOrBlank()) invalidated += "facet_preset"
        if (next.presetAttributes.isNotEmpty()) invalidated += "preset_attributes"
        if (invalidated.isNotEmpty()) {
            next = next.copy(
                facetCollectionCode = null,
                facetPresetCode = null,
                presetAttributes = emptyMap(),
            )
        }
    }

    if (next.typedAttributeFilters.isNotEmpty()) {
        val normalizedTyped = next.typedAttributeFilters
            .mapNotNull { (rawKey, value) ->
                val normalizedKey = rawKey.trim().lowercase()
                if (normalizedKey.isBlank()) null else normalizedKey to value
            }
            .toMap(LinkedHashMap())
        val filteredTyped = if (context.allowedTypedFacetKeys.isEmpty()) {
            normalizedTyped
        } else {
            normalizedTyped.filterKeys { facetKey -> facetKey in context.allowedTypedFacetKeys }
        }
        if (filteredTyped != next.typedAttributeFilters) {
            invalidated += "typed_attributes"
            next = next.copy(typedAttributeFilters = filteredTyped)
        }
    }

    val normalizedLocation = next.location?.trim()?.takeIf { value -> value.isNotEmpty() }
    val normalizedRadius = next.radiusKm?.coerceIn(NEARBY_RADIUS_PRESETS.first(), NEARBY_RADIUS_PRESETS.last())
    val shouldDropGeoContext = normalizedLocation.isNullOrBlank()
    if (shouldDropGeoContext) {
        if (next.radiusKm != null || next.centerLat != null || next.centerLon != null) {
            invalidated += "geo_radius"
        }
        if (next.location != normalizedLocation ||
            next.radiusKm != null ||
            next.centerLat != null ||
            next.centerLon != null
        ) {
            next = next.copy(
                location = normalizedLocation,
                radiusKm = null,
                centerLat = null,
                centerLon = null,
            )
        }
    } else if (next.location != normalizedLocation || next.radiusKm != normalizedRadius) {
        if (next.radiusKm != normalizedRadius) invalidated += "geo_radius"
        next = next.copy(
            location = normalizedLocation,
            radiusKm = normalizedRadius,
        )
    }

    val normalizedTrustSignals = normalizeSellerTrustSignals(next.sellerTrustSignals)
        .filterTo(LinkedHashSet()) { signal -> signal in context.availableSellerTrustSignals }
    if (normalizedTrustSignals != next.sellerTrustSignals) {
        invalidated += "seller_trust"
    }
    val inferredPreset = inferSellerTrustPreset(normalizedTrustSignals)
    if (normalizedTrustSignals != next.sellerTrustSignals || inferredPreset != next.sellerTrustPreset) {
        next = next.copy(
            sellerTrustSignals = normalizedTrustSignals,
            sellerTrustPreset = inferredPreset,
        )
    }

    return FilterDependencyResult(
        state = next,
        invalidatedFilterIds = invalidated.toList(),
    )
}

private fun FilterState.applyCategoryWithDependencies(
    code: String?,
    path: List<String>,
    context: FilterDependencyContext,
): FilterDependencyResult {
    val normalizedCode = code?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedPath = path.filter { node -> node.isNotBlank() }
    val categoryChanged = normalizedCode != categoryCode
    if (!categoryChanged && normalizedPath == categoryPath) {
        return normalizeWithDependencies(context)
    }

    var next = copy(
        categoryCode = normalizedCode,
        categoryPath = normalizedPath,
    )
    if (!categoryChanged) {
        return next.normalizeWithDependencies(context)
    }

    val invalidated = linkedSetOf<String>()
    if (!next.facetCollectionCode.isNullOrBlank()) invalidated += "facet_collection"
    if (!next.facetPresetCode.isNullOrBlank()) invalidated += "facet_preset"
    if (next.presetAttributes.isNotEmpty()) invalidated += "preset_attributes"
    if (next.typedAttributeFilters.isNotEmpty()) invalidated += "typed_attributes"

    next = next.copy(
        facetCollectionCode = null,
        facetPresetCode = null,
        presetAttributes = emptyMap(),
        typedAttributeFilters = emptyMap(),
    )
    val normalized = next.normalizeWithDependencies(context)
    return FilterDependencyResult(
        state = normalized.state,
        invalidatedFilterIds = (invalidated + normalized.invalidatedFilterIds).toList(),
    )
}

private fun FilterState.clearAppliedChipWithDependencies(
    chipKey: String,
    context: FilterDependencyContext,
): FilterDependencyResult = when {
    chipKey == AppliedChipKey.Category -> applyCategoryWithDependencies(
        code = null,
        path = emptyList(),
        context = context,
    )

    chipKey == AppliedChipKey.Brand -> copy(brands = emptySet()).normalizeWithDependencies(context)
    chipKey == AppliedChipKey.Price -> FilterDependencyResult(
        state = copy(
            priceMin = null,
            priceMax = null,
        ),
    )

    chipKey == AppliedChipKey.Condition -> copy(conditions = emptySet()).normalizeWithDependencies(context)
    chipKey == AppliedChipKey.PurchaseFormat -> FilterDependencyResult(
        state = copy(
            purchaseFormat = PurchaseFormat.All,
            deliverableOnly = false,
        ),
    )
    chipKey == AppliedChipKey.SellerTrust -> copy(
        sellerTrustPreset = SellerTrustPreset.Any,
        sellerTrustSignals = emptySet(),
    ).normalizeWithDependencies(context)

    chipKey == AppliedChipKey.Location -> FilterDependencyResult(
        state = copy(
            location = null,
            radiusKm = null,
            centerLat = null,
            centerLon = null,
        ),
    )

    chipKey.startsWith(AppliedChipKey.TypedPrefix) -> {
        val facetKey = chipKey.removePrefix(AppliedChipKey.TypedPrefix).trim()
        if (facetKey.isBlank() || facetKey !in typedAttributeFilters.keys) {
            normalizeWithDependencies(context)
        } else {
            copy(typedAttributeFilters = typedAttributeFilters - facetKey).normalizeWithDependencies(context)
        }
    }

    else -> normalizeWithDependencies(context)
}

private enum class ResultsFacetFilterType {
    Brand,
    PriceRange,
    Condition,
    DeliveryChannel,
    SellerTrust,
    TypedAttribute,
    Unsupported,
}

private data class ResultsFacetFilter(
    val facetKey: String,
    val title: String,
    val type: ResultsFacetFilterType,
)

private val defaultResultsFacetFilters = listOf(
    ResultsFacetFilter(
        facetKey = "brand",
        title = "Бренд",
        type = ResultsFacetFilterType.Brand,
    ),
    ResultsFacetFilter(
        facetKey = "price",
        title = "Цена",
        type = ResultsFacetFilterType.PriceRange,
    ),
    ResultsFacetFilter(
        facetKey = "condition",
        title = "Состояние",
        type = ResultsFacetFilterType.Condition,
    ),
    ResultsFacetFilter(
        facetKey = "delivery_channel",
        title = "Способ получения",
        type = ResultsFacetFilterType.DeliveryChannel,
    ),
    ResultsFacetFilter(
        facetKey = "seller_trust",
        title = "Доверие продавца",
        type = ResultsFacetFilterType.SellerTrust,
    ),
)

private fun FacetDefinition.isActiveForToday(today: LocalDate = LocalDate.now()): Boolean {
    val startsAt = runCatching { effectiveFrom?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) }
        .getOrNull()
    val endsAt = runCatching { effectiveTo?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) }
        .getOrNull()
    if (startsAt != null && today.isBefore(startsAt)) return false
    if (endsAt != null && today.isAfter(endsAt)) return false
    return true
}

private fun buildFacetUiFilters(definitions: List<FacetDefinition>): List<ResultsFacetFilter> {
    val mapped = definitions
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .sortedWith(
            compareBy<FacetDefinition> { definition -> definition.ui.order }
                .thenBy { definition -> definition.titleRu.lowercase() },
        )
        .map { definition ->
            val normalizedKey = definition.facetKey.trim().lowercase()
            val type = when (normalizedKey) {
                "brand" -> ResultsFacetFilterType.Brand
                "price",
                "price_rub",
                -> ResultsFacetFilterType.PriceRange

                "condition" -> ResultsFacetFilterType.Condition
                "delivery_channel",
                "delivery",
                "purchase_format",
                -> ResultsFacetFilterType.DeliveryChannel
                "seller_trust" -> ResultsFacetFilterType.SellerTrust
                else -> if (definition.valueType == FacetDataType.BOOL ||
                    definition.valueType == FacetDataType.ENUM ||
                    definition.valueType == FacetDataType.RANGE ||
                    definition.valueType == FacetDataType.TEXT
                ) {
                    ResultsFacetFilterType.TypedAttribute
                } else {
                    ResultsFacetFilterType.Unsupported
                }
            }
            ResultsFacetFilter(
                facetKey = normalizedKey,
                title = definition.titleRu.ifBlank { definition.facetKey },
                type = type,
            )
        }
        .distinctBy { facetFilter -> facetFilter.facetKey }
        .toList()
    if (mapped.isEmpty()) return defaultResultsFacetFilters
    return (mapped + defaultResultsFacetFilters)
        .distinctBy { facetFilter -> facetFilter.facetKey }
}

private fun FilterState.summaryForFacet(facetFilter: ResultsFacetFilter): String = when (facetFilter.type) {
    ResultsFacetFilterType.Brand -> brandSummary()
    ResultsFacetFilterType.PriceRange -> priceSummary()
    ResultsFacetFilterType.Condition -> conditionsSummary()
    ResultsFacetFilterType.DeliveryChannel -> purchaseFormatSummary()
    ResultsFacetFilterType.SellerTrust -> sellerTrustSummary()
    ResultsFacetFilterType.TypedAttribute -> typedFilterSummary(facetFilter.facetKey)
    ResultsFacetFilterType.Unsupported -> {
        presetAttributes[facetFilter.facetKey]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Задаётся пресетом"
    }
}

private fun FilterState.toggleBrand(brand: String): FilterState {
    val normalized = brand.trim()
    if (normalized.isBlank()) return this
    val updated = brands.toMutableSet()
    val existing = updated.firstOrNull { item -> item.equals(normalized, ignoreCase = true) }
    if (existing != null) {
        updated.remove(existing)
    } else {
        updated.add(normalized)
    }
    return copy(brands = updated)
}

private fun FilterState.withCondition(option: ConditionOption): FilterState {
    val updated = conditions.toMutableSet()
    if (option in updated) {
        updated.remove(option)
    } else {
        updated.add(option)
    }
    return copy(conditions = updated)
}

private fun buildRecognitionTitle(filters: FilterState): String {
    val parts = listOfNotNull(
        filters.query?.brand?.takeIf { it.isNotBlank() },
        filters.query?.model?.takeIf { it.isNotBlank() },
    )
    return parts.joinToString(" ").ifBlank { filters.queryText }
}

private fun buildRecognitionDetails(filters: FilterState): String {
    val parts = mutableListOf<String>()
    filters.query?.brand?.takeIf { it.isNotBlank() }?.let { parts.add("$it → бренд") }
    filters.query?.model?.takeIf { it.isNotBlank() }?.let { parts.add("$it → модель") }
    if (filters.categoryPath.isNotEmpty()) {
        parts.add(filters.categoryPath.joinToString(" → "))
    }
    return parts.joinToString(" · ")
}

private fun buildQuerySummary(
    filters: FilterState,
    defaultLabel: String,
): String {
    val parts = mutableListOf<String>()
    val title = buildRecognitionTitle(filters).trim()
    if (title.isNotBlank()) {
        parts += title
    }
    val details = buildRecognitionDetails(filters).trim()
    if (details.isNotBlank() && details != title) {
        parts += details
    }
    val location = filters.locationSummary()
    if (location.isNotBlank() && location != "Любое") {
        parts += location
    }
    return parts.joinToString(" · ").ifBlank { defaultLabel }
}

private fun buildTrackFiltersExtra(filters: FilterState): Map<String, String> {
    val extra = LinkedHashMap<String, String>()

    fun putExtra(rawKey: String, rawValue: String?) {
        val key = rawKey.trim().lowercase()
        val value = rawValue?.trim().orEmpty()
        if (key.isEmpty() || value.isEmpty()) return
        extra[key] = value
    }

    putExtra("brand", filters.query?.brand)
    putExtra("model", filters.query?.model)

    filters.query
        ?.attributes
        ?.toRawStringAttributes()
        ?.forEach { (key, value) ->
            if (!key.equals("condition", ignoreCase = true)) {
                putExtra(key, value)
            }
        }
    filters.presetAttributes.forEach { (key, value) -> putExtra(key, value) }

    if (filters.conditions.size == 1) {
        putExtra("condition", filters.conditions.first().value)
    }
    when (filters.purchaseFormat) {
        PurchaseFormat.Pickup -> putExtra("delivery_channel", "pickup")
        PurchaseFormat.Delivery -> putExtra("delivery_channel", "delivery")
        PurchaseFormat.All -> Unit
    }
    if (filters.deliverableOnly) {
        putExtra("delivery", "true")
    }

    filters.typedAttributeFilters.forEach { (rawKey, draft) ->
        val key = rawKey.trim().lowercase()
        if (key.isEmpty()) return@forEach
        val value = when (draft.op) {
            TypedAttributeOperator.EQ,
            TypedAttributeOperator.CONTAINS,
            -> draft.value.trim().takeIf { it.isNotEmpty() }

            TypedAttributeOperator.IN -> {
                val values = parseFacetCsv(draft.valuesCsv)
                if (values.size == 1) values.first() else null
            }

            else -> null
        }
        putExtra(key, value)
    }

    return extra.entries
        .sortedBy { (key, _) -> key }
        .associate { it.toPair() }
}

private fun sortLabel(sort: OfferSort): String = when (sort) {
    OfferSort.RANK -> "По релевантности"
    OfferSort.PRICE_ASC -> "Цена: по возрастанию"
    OfferSort.PRICE_DESC -> "Цена: по убыванию"
    OfferSort.NEWEST -> "По времени: новые"
    OfferSort.DELIVERY_ASC, OfferSort.DISTANCE_ASC -> "По расстоянию: ближе"
    OfferSort.RATING_DESC -> "Рейтинг продавца"
}

private data class SortOptionItem(
    val sort: OfferSort,
    val label: String,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

private data class BrandOptionItem(
    val name: String,
    val count: Int? = null,
)

private fun buildSortOptions(
    items: List<ExplainedItem>,
    location: String?,
): List<SortOptionItem> {
    fun coverage(predicate: (ExplainedItem) -> Boolean): Double {
        if (items.isEmpty()) return 1.0
        val covered = items.count(predicate)
        return covered.toDouble() / items.size.toDouble()
    }

    val priceCoverage = coverage { item -> item.dto.price != null }
    val dateCoverage = coverage { item -> item.dto.updatedAt != null }
    val trustCoverage = coverage { item -> item.dto.trustScore != null || item.dto.sellerRating != null }
    val distanceCoverage = coverage { item -> item.dto.distanceKm != null }
    val hasLocationContext = !location.isNullOrBlank() || distanceCoverage > 0.0

    val isPriceAvailable = priceCoverage >= 0.70
    val isDateAvailable = dateCoverage >= 0.60
    val isTrustAvailable = trustCoverage >= 0.60
    val isDistanceAvailable = hasLocationContext && distanceCoverage >= 0.60

    return listOf(
        SortOptionItem(
            sort = OfferSort.RANK,
            label = sortLabel(OfferSort.RANK),
        ),
        SortOptionItem(
            sort = OfferSort.PRICE_ASC,
            label = sortLabel(OfferSort.PRICE_ASC),
            enabled = isPriceAvailable,
            disabledReason = if (!isPriceAvailable) "Недостаточно данных о цене" else null,
        ),
        SortOptionItem(
            sort = OfferSort.PRICE_DESC,
            label = sortLabel(OfferSort.PRICE_DESC),
            enabled = isPriceAvailable,
            disabledReason = if (!isPriceAvailable) "Недостаточно данных о цене" else null,
        ),
        SortOptionItem(
            sort = OfferSort.NEWEST,
            label = sortLabel(OfferSort.NEWEST),
            enabled = isDateAvailable,
            disabledReason = if (!isDateAvailable) "Недостаточно данных о времени обновления" else null,
        ),
        SortOptionItem(
            sort = OfferSort.DELIVERY_ASC,
            label = sortLabel(OfferSort.DELIVERY_ASC),
            enabled = isDistanceAvailable,
            disabledReason = if (!isDistanceAvailable) "Нужна локация и данные о расстоянии" else null,
        ),
        SortOptionItem(
            sort = OfferSort.RATING_DESC,
            label = sortLabel(OfferSort.RATING_DESC),
            enabled = isTrustAvailable,
            disabledReason = if (!isTrustAvailable) "Недостаточно данных о надёжности продавца" else null,
        ),
    )
}

private fun conditionOptions(
    runtimeFacets: List<ValueFacet>,
    selected: Set<ConditionOption>,
): List<ConditionOptionItem> {
    val counts = LinkedHashMap<ConditionOption, Int>()
    runtimeFacets.forEach { facet ->
        val option = mapConditionOption(facet.id) ?: mapConditionOption(facet.name) ?: return@forEach
        counts[option] = (counts[option] ?: 0) + facet.count
    }
    selected.forEach { option ->
        if (counts.isNotEmpty() && option !in counts.keys) {
            counts[option] = 0
        }
    }
    val values = if (counts.isEmpty()) {
        ConditionOption.entries
    } else {
        ConditionOption.entries.filter { option -> option in counts.keys }
    }
    return values.map { option -> ConditionOptionItem(condition = option, count = counts[option]) }
}

private fun purchaseFormatOptions(
    runtimeFacets: List<ValueFacet>,
    current: PurchaseFormat,
): List<PurchaseFormatOptionItem> {
    val counts = LinkedHashMap<PurchaseFormat, Int>()
    runtimeFacets.forEach { facet ->
        val option = mapPurchaseFormat(facet.id) ?: mapPurchaseFormat(facet.name) ?: return@forEach
        if (option == PurchaseFormat.All) return@forEach
        counts[option] = (counts[option] ?: 0) + facet.count
    }
    if (counts.isNotEmpty() && current != PurchaseFormat.All && current !in counts.keys) {
        counts[current] = 0
    }
    val values = if (counts.isEmpty()) {
        PurchaseFormat.entries
    } else {
        PurchaseFormat.entries.filter { option ->
            option == PurchaseFormat.All || option in counts.keys
        }
    }
    return values.map { option ->
        val count = if (option == PurchaseFormat.All) null else counts[option]
        PurchaseFormatOptionItem(format = option, count = count)
    }
}

private fun mapConditionOption(raw: String): ConditionOption? {
    val token = normalizeFacetToken(raw)
    return when (token) {
        "new",
        "новый",
        "новое",
        -> ConditionOption.New

        "like_new",
        "likenew",
        "like-new",
        "как_новый",
        "какновый",
        -> ConditionOption.LikeNew

        "used",
        "бу",
        "б_у",
        "б/у",
        "second_hand",
        "secondhand",
        -> ConditionOption.Used

        else -> null
    }
}

private fun mapPurchaseFormat(raw: String): PurchaseFormat? {
    val token = normalizeFacetToken(raw)
    return when (token) {
        "delivery",
        "доставка",
        "ship",
        "shipping",
        -> PurchaseFormat.Delivery

        "pickup",
        "самовывоз",
        "self_pickup",
        -> PurchaseFormat.Pickup

        else -> null
    }
}

private fun normalizeFacetToken(raw: String): String =
    raw.trim()
        .lowercase(Locale.ROOT)
        .replace("ё", "е")
        .replace("\\s+".toRegex(), "_")

private fun availableBrandOptions(
    items: List<ExplainedItem>,
    query: NormalizedQuery?,
    runtimeFacets: List<BrandFacet>,
): List<BrandOptionItem> {
    val countsByKey = LinkedHashMap<String, Int>()
    val labelsByKey = LinkedHashMap<String, String>()

    runtimeFacets.forEach { facet ->
        val label = facet.name.trim().takeIf { value -> value.isNotBlank() } ?: return@forEach
        val key = label.lowercase()
        countsByKey[key] = (countsByKey[key] ?: 0) + facet.count.coerceAtLeast(0)
        labelsByKey[key] = labelsByKey[key] ?: label
    }

    items.mapNotNull { item -> item.dto.brand?.trim()?.takeIf { value -> value.isNotBlank() } }
        .forEach { brand ->
            val key = brand.lowercase()
            labelsByKey[key] = labelsByKey[key] ?: brand
        }

    query?.brand?.takeIf { it.isNotBlank() }?.let { brand ->
        val key = brand.lowercase()
        labelsByKey[key] = labelsByKey[key] ?: brand
    }

    return labelsByKey.entries
        .map { (key, label) ->
            BrandOptionItem(
                name = label,
                count = countsByKey[key],
            )
        }
        .sortedWith(compareBy<BrandOptionItem> { it.name.lowercase() })
}

private fun availablePriceBounds(items: List<ExplainedItem>): PriceBounds? {
    val prices = items.mapNotNull { it.dto.price?.toInt() }
    val min = prices.minOrNull() ?: return null
    val max = prices.maxOrNull() ?: return null
    return PriceBounds(min = min, max = max)
}

private fun categoryPathTitles(
    code: String?,
    categoriesByCode: Map<String, Category>,
): List<String> =
    buildCategoryPath(code, categoriesByCode).map { pathCode ->
        categoriesByCode[pathCode]?.title ?: pathCode
    }

private fun quickPriceRanges(priceBounds: PriceBounds?): List<PriceRangeOption> {
    val raw = listOf(
        PriceRangeOption("До 10 000", null, 10_000),
        PriceRangeOption("10–20", 10_000, 20_000),
        PriceRangeOption("20–30", 20_000, 30_000),
        PriceRangeOption("30–50", 30_000, 50_000),
        PriceRangeOption("50–100", 50_000, 100_000),
    )
    val max = priceBounds?.max
    return if (max == null) raw else raw.filter { it.max == null || it.max <= max }
}

private fun formatPrice(value: Int): String =
    String.format(Locale.forLanguageTag("ru-RU"), "%,d ₽", value)

private fun applyFacetPreset(
    base: FilterState,
    collection: FacetCollection?,
    preset: FacetPreset?,
): FilterState {
    val applied = FacetRuntimeFiltersApplier.apply(
        base = base.toFacetRuntimeFilters(),
        collection = collection,
        preset = preset,
    )
    return base.copy(
        categoryCode = applied.categoryCode,
        categoryPath = emptyList(),
        facetCollectionCode = applied.facetCollectionCode,
        facetPresetCode = applied.facetPresetCode,
        presetAttributes = applied.attributes.toRawStringAttributes(),
        brands = applied.brands,
        priceMin = applied.priceMin,
        priceMax = applied.priceMax,
        conditions = mapConditionOptions(applied.conditions.toList()),
        purchaseFormat = applied.purchaseFormat.toPurchaseFormat(base.purchaseFormat),
    )
}

private fun FilterState.toFacetRuntimeFilters(): FacetRuntimeFilters = FacetRuntimeFilters(
    categoryCode = categoryCode,
    facetCollectionCode = facetCollectionCode,
    facetPresetCode = facetPresetCode,
    attributes = presetAttributes.toTypedAttributesGuess(),
    brands = brands,
    priceMin = priceMin,
    priceMax = priceMax,
    conditions = conditions.map { option -> option.value }.toSet(),
    purchaseFormat = purchaseFormat.toFacetPurchaseFormat(),
)

private fun PurchaseFormat.toFacetPurchaseFormat(): FacetPurchaseFormat? = when (this) {
    PurchaseFormat.Pickup -> FacetPurchaseFormat.PICKUP
    PurchaseFormat.Delivery -> FacetPurchaseFormat.DELIVERY
    PurchaseFormat.All -> null
}

private fun FacetPurchaseFormat?.toPurchaseFormat(fallback: PurchaseFormat): PurchaseFormat = when (this) {
    FacetPurchaseFormat.PICKUP -> PurchaseFormat.Pickup
    FacetPurchaseFormat.DELIVERY -> PurchaseFormat.Delivery
    null -> fallback
}

private data class OfferOpenUrlsForResults(
    val hasRawDeeplink: Boolean,
    val fallbackUrl: String?,
    val rawPreferredUrl: String?,
    val host: String,
)

private fun resolveOfferOpenUrlsForResults(dto: ProductDto): OfferOpenUrlsForResults {
    val rawRedirect = dto.redirectUrl?.trim()?.takeIf { it.isNotEmpty() }
    val rawDeeplink = dto.deeplinkUrl?.trim()?.takeIf { it.isNotEmpty() }
    val rawExternal = dto.externalUrl?.trim()?.takeIf { it.isNotEmpty() }
    val hasRawDeeplink = rawRedirect != null || rawDeeplink != null || rawExternal != null

    val normalizedRedirect = normalizeExternalUrl(rawRedirect)
    val normalizedDeeplink = normalizeExternalUrl(rawDeeplink)
    val normalizedExternal = normalizeExternalUrl(rawExternal)
    val fallbackUrl = normalizedRedirect ?: normalizedDeeplink ?: normalizedExternal

    val rawPreferredUrl = rawRedirect ?: rawDeeplink ?: rawExternal
    val host = fallbackUrl?.let { url ->
        runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()
    } ?: "none"

    return OfferOpenUrlsForResults(
        hasRawDeeplink = hasRawDeeplink,
        fallbackUrl = fallbackUrl,
        rawPreferredUrl = rawPreferredUrl,
        host = host,
    )
}

private data class PresetEventContext(
    val querySessionId: String,
    val categoryCode: String,
    val facetCollectionCode: String?,
    val facetPresetCode: String,
)

private fun buildPresetEvent(
    context: PresetEventContext,
    eventType: PresetObservabilityEventType,
    idempotencyKey: String,
    offerId: String? = null,
    position: Int? = null,
): PresetObservabilityEvent = PresetObservabilityEvent(
    idempotencyKey = idempotencyKey,
    eventType = eventType,
    querySessionId = context.querySessionId,
    categoryCode = context.categoryCode,
    facetCollectionCode = context.facetCollectionCode,
    facetPresetCode = context.facetPresetCode,
    offerId = offerId,
    position = position,
    occurredAtMs = System.currentTimeMillis(),
    dataVersion = CatalogDataVersion.current,
)

private fun buildOfferCardUi(
    item: ExplainedItem,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDetails: () -> Unit,
    onOverflowAction: (OfferOverflowAction) -> Unit,
): OfferCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Объявление" }
    val badges = dto.sellerBadges.map { CardBadge(label = normalizeBadgeLabel(it)) }
    val trust = CardTrustData(
        updatedAtText = formatUpdatedAtText(dto.updatedAt),
        sourceText = dto.sourceName?.takeIf { it.isNotBlank() },
        ratingText = formatRatingText(dto.sellerRating),
    )
    return OfferCardUi(
        id = dto.id,
        title = title,
        priceText = formatPriceText(dto.price),
        media = dto.imageUrls.map { url -> CardMediaItem(url = url, contentDescription = title) },
        photoCount = dto.imageUrls.size,
        locationText = formatLocationText(dto.sellerCity, dto.sellerCountry),
        isSaved = isSaved,
        badges = badges,
        trust = trust,
        onOpenDetails = onOpenDetails,
        onToggleSave = onToggleSave,
        onOverflowAction = onOverflowAction,
    )
}

@Composable
private fun LoadMoreRow(
    isLoading: Boolean,
    errorMessage: String?,
    onLoadMore: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onLoadMore) {
                    Text("Повторить")
                }
            }
        } else {
            TextButton(
                onClick = onLoadMore,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (isLoading) "Загрузка..." else "Загрузить ещё")
            }
        }
    }
}

private data class ResultsLocationSnapshot(
    val city: String?,
    val lat: Double?,
    val lon: Double?,
)

private fun resolveLocationPermissionState(
    context: Context,
    requestedBefore: Boolean,
): ResultsGeoPermissionState {
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (coarseGranted || fineGranted) return ResultsGeoPermissionState.Granted
    if (!requestedBefore) return ResultsGeoPermissionState.CanAsk

    val activity = context.findActivity() ?: return ResultsGeoPermissionState.DeniedCanAsk
    val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    return if (shouldShow) {
        ResultsGeoPermissionState.DeniedCanAsk
    } else {
        ResultsGeoPermissionState.DeniedPermanent
    }
}

@SuppressLint("MissingPermission")
private suspend fun resolveCurrentLocationForResults(context: Context): ResultsLocationSnapshot? {
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!coarseGranted && !fineGranted) return null

    return withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
            .ifEmpty { listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER) }
        val location = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { last -> last.time }
            ?: return@withContext null

        val geocoder = Geocoder(context, Locale.getDefault())
        val address = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            }
        }.getOrNull()

        val city = address?.locality
            ?: address?.subAdminArea
            ?: address?.adminArea
            ?: address?.countryName
        ResultsLocationSnapshot(
            city = city?.trim()?.takeIf { value -> value.isNotEmpty() },
            lat = location.latitude,
            lon = location.longitude,
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
