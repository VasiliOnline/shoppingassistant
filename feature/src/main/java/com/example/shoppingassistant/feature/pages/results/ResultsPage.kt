package com.example.shoppingassistant.feature.pages.results

import android.annotation.SuppressLint
import android.Manifest
import android.animation.ValueAnimator
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
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
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
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
import com.example.shoppingassistant.domain.catalog.BrowseTargetType
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistry
import com.example.shoppingassistant.domain.catalog.CatalogFacetPresentationProfile
import com.example.shoppingassistant.domain.catalog.CatalogFacetPresentationProfiles
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CategoryReplacementResolver
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.catalog.GetBrowseNodeTask
import com.example.shoppingassistant.domain.catalog.RouteQueryTask
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
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
import com.example.shoppingassistant.domain.model.asBooleanOrNull
import com.example.shoppingassistant.domain.model.asDoubleOrNull
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBinderStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChip
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChipKind
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRouteKind
import com.example.shoppingassistant.domain.i18n.displayLabel
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.model.toRawStringAttributes
import com.example.shoppingassistant.domain.model.toTypedAttributesGuess
import com.example.shoppingassistant.domain.profile.activeDeliveryAddress
import com.example.shoppingassistant.domain.profile.normalized
import com.example.shoppingassistant.domain.profile.ProfileSettings
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.navigation.AppRoutes
import com.example.shoppingassistant.feature.pages.chat.normalizeExternalUrl
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
import com.example.shoppingassistant.feature.BuildConfig
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
import com.example.shoppingassistant.domain.facet.FacetSelectionMode
import com.example.shoppingassistant.domain.facet.FacetUiConfig
import com.example.shoppingassistant.domain.facet.FacetUiWidget
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetRuntimeFilters
import com.example.shoppingassistant.domain.facet.FacetRuntimeFiltersApplier
import com.example.shoppingassistant.domain.facet.GetFacetCollectionTask
import com.example.shoppingassistant.domain.facet.GetFacetDefinitionsTask
import com.example.shoppingassistant.domain.facet.GetFacetPresetTask
import com.example.shoppingassistant.domain.facet.runtimeFilterKey
import com.example.shoppingassistant.domain.catalog.resolveCatalogGovernanceScope
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.search.SearchInterpretationDependencies
import com.example.shoppingassistant.domain.search.InterpretedSearchIntent
import com.example.shoppingassistant.domain.search.SearchInterpretationPipeline
import com.example.shoppingassistant.domain.search.SearchInterpretationRequest
import com.example.shoppingassistant.domain.search.SearchRequestSource
import com.example.shoppingassistant.domain.search.SearchRouteResult
import com.example.shoppingassistant.domain.search.SearchTemplateContext
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.feature.pages.main.context.attributeCatalogFor
import com.example.shoppingassistant.feature.pages.model.AttributeDef
import com.example.shoppingassistant.feature.pages.model.ValueDef
import com.example.shoppingassistant.feature.pages.model.parseFreeQueryAttributes
import com.example.shoppingassistant.feature.pages.model.toFeatureParseableAttributeDefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get as koinGet
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.coroutines.resume

private const val FILTERS_APPLY_RATE_LIMIT_MS = 600L
private const val RESULTS_CARD_OPEN_COOLDOWN_MS = 800L
private const val FILTERS_SIDE_SHEET_WIDTH_FRACTION = 0.84f
private const val FILTER_HUB_INLINE_VALUE_THRESHOLD = 6
private const val PRICE_FILTER_FALLBACK_MAX = 10_000_000
private const val RESULTS_UI_LOCALE = "ru-RU"
private val FILTERS_EDGE_GESTURE_ZONE: Dp = 28.dp
private val FILTERS_EDGE_GESTURE_TRIGGER: Dp = 96.dp
private val FILTERS_TOP_DOCK_Y: Dp = 156.dp
private val FILTERS_BOTTOM_DOCK_MARGIN: Dp = 92.dp

private enum class FiltersHandleDock {
    Top,
    Left,
    Right,
    Bottom,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ResultsPage(
    payload: ResultsPayload,
    navController: NavHostController? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onOpenCreate: (() -> Unit)? = null,
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
    val liveValuesRepository: CatalogLiveValuesRepository = remember {
        koinGet(CatalogLiveValuesRepository::class.java)
    }
    val catalogRepository: CatalogReadRepository = remember { koinGet(CatalogReadRepository::class.java) }
    val catalogTaxonomyRepository: CatalogTaxonomyRepository = remember {
        koinGet(CatalogTaxonomyRepository::class.java)
    }
    val constraintsResolver: CatalogConstraintsResolver = remember {
        koinGet(CatalogConstraintsResolver::class.java)
    }
    val trackRepository: TrackRepository = remember { koinGet(TrackRepository::class.java) }
    val routeQueryTask: RouteQueryTask = remember { koinGet(RouteQueryTask::class.java) }
    val getBrowseNodeTask: GetBrowseNodeTask = remember { koinGet(GetBrowseNodeTask::class.java) }
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
    val isPhotoMode = payload.origin == ResultsOrigin.Photo
    var activeVisualContext by remember(payload.visualContext) { mutableStateOf(payload.visualContext) }
    val visualContext = activeVisualContext
    val querySessionId = rememberSaveable(payload.querySessionId) {
        payload.querySessionId?.trim()?.takeIf { it.isNotEmpty() } ?: "qs-${UUID.randomUUID()}"
    }
    val sentImpressionKeys = remember(querySessionId) { hashSetOf<String>() }
    val sentActionKeys = remember(querySessionId) { hashSetOf<String>() }
    val exposedFlagDecisionSignatures = remember(querySessionId) { hashSetOf<String>() }
    val searchInterpretationPipeline = remember { SearchInterpretationPipeline() }

    var filters by remember { mutableStateOf(initialFilterState(payload)) }
    var workingFilters by remember { mutableStateOf(filters) }
    var inlineQueryInput by rememberSaveable {
        mutableStateOf(initialResultsInlineQuery(filters))
    }
    var viewMode by rememberSaveable { mutableStateOf(ResultsViewMode.List) }
    var filtersHandleDock by rememberSaveable { mutableStateOf(FiltersHandleDock.Bottom) }
    var sheetScreen by remember { mutableStateOf<ResultsSheet?>(null) }
    var sheetTarget by remember { mutableStateOf(FilterSheetTarget.Applied) }
    var activeTypedFacetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var brandQuery by rememberSaveable { mutableStateOf("") }
    var isFiltersApplyRateLimited by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var allFacetDefinitions by remember { mutableStateOf<List<FacetDefinition>>(emptyList()) }
    var facetDefinitions by remember { mutableStateOf<List<FacetDefinition>>(emptyList()) }
    var categoryTreePath by remember { mutableStateOf<List<String>>(emptyList()) }
    val hiddenIds = remember { mutableStateListOf<String>() }
    var savedOfferIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var catalogLoadError by remember { mutableStateOf<String?>(null) }
    var catalogReloadToken by rememberSaveable { mutableStateOf(0) }
    var inlineLeafAttributeDefsCache by remember { mutableStateOf<Map<String, List<AttributeDef>>?>(null) }
    var inlineQuerySubmitToken by rememberSaveable { mutableStateOf(0) }
    val queryTypedFacetRelaxationResetKey = remember(filters) {
        buildQueryTypedFacetRelaxationResetKey(filters)
    }
    var relaxedQueryTypedFacetKeys by rememberSaveable(queryTypedFacetRelaxationResetKey) {
        mutableStateOf(emptySet<String>())
    }
    val catalogScopeLoadKey = remember(
        filters.categoryCode,
        filters.facetCollectionCode,
        filters.facetPresetCode,
        payload.categoryCode,
        catalogReloadToken,
    ) {
        listOf(
            filters.categoryCode.orEmpty(),
            filters.facetCollectionCode.orEmpty(),
            filters.facetPresetCode.orEmpty(),
            payload.categoryCode.orEmpty(),
            catalogReloadToken.toString(),
        ).joinToString("|")
    }
    var catalogScopeReadyKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAppliedFacetPresetSignature by rememberSaveable { mutableStateOf<String?>(null) }
    val cardOpenTapMsByOfferId = remember { mutableMapOf<String, Long>() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var locationPermissionState by remember {
        mutableStateOf(resolveLocationPermissionState(context, requestedBefore = false))
    }
    var isResolvingCurrentLocation by remember { mutableStateOf(false) }
    var hasSearchCache by rememberSaveable { mutableStateOf(false) }
    val reducedMotionEnabled = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { !ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
        } else {
            false
        }
    }
    var remoteConfigSnapshot by remember(querySessionId) {
        mutableStateOf(searchRemoteConfigService.currentSnapshot())
    }
    val featureGateContext = remember(
        querySessionId,
        filters.categoryCode,
        locationPermissionState,
        configuration.smallestScreenWidthDp,
        hasSearchCache,
        reducedMotionEnabled,
    ) {
        SearchFeatureGateContext(
            searchSessionId = querySessionId,
            canonicalCode = filters.categoryCode?.trim()?.takeIf { it.isNotEmpty() },
            hasGeoPermission = locationPermissionState == ResultsGeoPermissionState.Granted,
            geoContextSupported = true,
            isTabletDevice = configuration.smallestScreenWidthDp >= 600,
            hasCache = hasSearchCache,
            reducedMotionEnabled = reducedMotionEnabled,
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

    val isCatalogScopeReady = catalogScopeReadyKey == catalogScopeLoadKey

    LaunchedEffect(catalogScopeLoadKey) {
        var hadFailure = false

        val loadedCategories = runCatching { catalogTaxonomyRepository.listCategories() }
            .onFailure { hadFailure = true }
            .getOrElse { categories }
        categories = loadedCategories.filter { category -> category.status == CategoryStatus.ACTIVE }

        val categoryCode = resolveResultsCategoryCode(
            categoryCode = filters.categoryCode,
            categories = loadedCategories,
        )
        val loadedFacetDefinitions = runCatching { getFacetDefinitionsTask() }
            .onFailure { hadFailure = true }
            .getOrElse { emptyList() }
        allFacetDefinitions = loadedFacetDefinitions

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
        var nextFilters = if (filters.categoryCode == categoryCode) {
            filters
        } else {
            filters.copy(
                categoryCode = categoryCode,
                categoryPath = emptyList(),
            )
        }
        if (presetSignature == null) {
            lastAppliedFacetPresetSignature = null
        } else if (presetSignature != lastAppliedFacetPresetSignature) {
            val presetScopedFacetDefinitions = resolveResultsPresetFacetDefinitions(
                definitions = loadedFacetDefinitions,
                currentCategoryCode = nextFilters.categoryCode,
                collection = collection,
                preset = preset,
                categories = loadedCategories,
            )
            val next = applyFacetPreset(
                base = nextFilters,
                collection = collection,
                preset = preset,
                definitions = presetScopedFacetDefinitions,
            )
            nextFilters = next
            lastAppliedFacetPresetSignature = presetSignature
        }
        val effectiveCategoryCode = resolveResultsCategoryCode(
            categoryCode = nextFilters.categoryCode,
            categories = loadedCategories,
        )
        if (effectiveCategoryCode != nextFilters.categoryCode) {
            nextFilters = nextFilters.copy(
                categoryCode = effectiveCategoryCode,
                categoryPath = emptyList(),
            )
        }
        val scopedFacetDefinitions = scopeFacetDefinitionsForCategory(
            definitions = loadedFacetDefinitions,
            categoryCode = effectiveCategoryCode,
        )
        val presentationFacetDefinitions = filterFacetDefinitionsForPresentation(
            definitions = scopedFacetDefinitions,
            categoryCode = effectiveCategoryCode,
        )
        facetDefinitions = presentationFacetDefinitions
        val typedDependencyContext = buildResultsDependencyContext(
            facetDefinitions = presentationFacetDefinitions,
            categoriesByCode = loadedCategories.associateBy { category -> category.code },
            availableSellerTrustSignals = SellerTrustSignal.entries.toSet(),
        )
        val normalizedFilters = nextFilters.normalizeWithDependencies(typedDependencyContext).state
        if (normalizedFilters != filters) {
            val shouldSyncWorking = workingFilters == filters
            filters = normalizedFilters
            if (shouldSyncWorking) {
                workingFilters = normalizedFilters
            }
        }

        catalogLoadError = if (hadFailure) {
            "Не удалось загрузить часть данных каталога. Доступен базовый режим фильтров."
        } else {
            null
        }
        catalogScopeReadyKey = catalogScopeLoadKey
    }

    val categoriesByCode = remember(categories) { categories.associateBy { it.code } }
    val categoriesByParent = remember(categories) { categories.groupBy { it.parentCode } }

    val selectedPathCodes = remember(filters.categoryCode, categoriesByCode) {
        buildCategoryPath(filters.categoryCode, categoriesByCode)
    }
    val selectedPathTitles = remember(selectedPathCodes, categoriesByCode) {
        selectedPathCodes.map { code ->
            categoriesByCode[code]?.displayTitle(locale = RESULTS_UI_LOCALE) ?: code
        }
    }
    LaunchedEffect(filters.queryText, filters.query?.brand, filters.query?.model) {
        val candidate = initialResultsInlineQuery(filters)
        if (candidate != inlineQueryInput) {
            inlineQueryInput = candidate
        }
    }
    LaunchedEffect(filters.categoryCode, categoriesByCode) {
        if (selectedPathTitles != filters.categoryPath) {
            filters = filters.copy(categoryPath = selectedPathTitles)
        }
    }
    val workingSelectedPathTitles = remember(workingFilters.categoryCode, categoriesByCode) {
        categoryPathTitles(workingFilters.categoryCode, categoriesByCode)
    }
    LaunchedEffect(workingFilters.categoryCode, categoriesByCode) {
        if (workingSelectedPathTitles != workingFilters.categoryPath) {
            workingFilters = workingFilters.copy(categoryPath = workingSelectedPathTitles)
        }
    }

    val uiFacetFiltersState = remember(sheetTarget, sheetScreen, workingFilters, filters) {
        if (sheetTarget == FilterSheetTarget.Draft && sheetScreen != null) {
            workingFilters
        } else {
            filters
        }
    }
    val uiFacetCategoryCode = uiFacetFiltersState.categoryCode
    val appliedFacetPresentationProfile = remember(filters.categoryCode) {
        CatalogFacetPresentationProfiles.resolve(filters.categoryCode)
    }
    val scopedFacetDefinitionsForUi = remember(allFacetDefinitions, uiFacetCategoryCode) {
        scopeFacetDefinitionsForCategory(
            definitions = allFacetDefinitions,
            categoryCode = uiFacetCategoryCode,
        )
    }
    val facetDefinitionsForUi = remember(scopedFacetDefinitionsForUi, uiFacetCategoryCode) {
        filterFacetDefinitionsForPresentation(
            definitions = scopedFacetDefinitionsForUi,
            categoryCode = uiFacetCategoryCode,
        )
    }
    val facetPresentationProfile = remember(uiFacetCategoryCode) {
        CatalogFacetPresentationProfiles.resolve(uiFacetCategoryCode)
    }
    val facetUiFilters = remember(facetDefinitionsForUi, uiFacetCategoryCode) {
        buildFacetUiFilters(
            definitions = facetDefinitionsForUi,
            categoryCode = uiFacetCategoryCode,
        )
    }
    var appliedTypedFacetUniverse by remember(filters.categoryCode, facetDefinitions) {
        mutableStateOf(ResultsTypedFacetUniverse())
    }
    LaunchedEffect(filters.categoryCode, facetDefinitions, filters.query, filters.brands, filters.typedAttributeFilters) {
        appliedTypedFacetUniverse = loadTypedFacetUniverse(
            catalogRepository = catalogRepository,
            liveValuesRepository = liveValuesRepository,
            categoryCode = filters.categoryCode,
            facetDefinitions = facetDefinitions,
            lookupScopes = resolveTypedFacetLookupScopes(filters),
        )
    }
    var uiTypedFacetUniverse by remember(uiFacetCategoryCode, facetDefinitionsForUi) {
        mutableStateOf(ResultsTypedFacetUniverse())
    }
    LaunchedEffect(uiFacetCategoryCode, facetDefinitionsForUi, uiFacetFiltersState) {
        uiTypedFacetUniverse = loadTypedFacetUniverse(
            catalogRepository = catalogRepository,
            liveValuesRepository = liveValuesRepository,
            categoryCode = uiFacetCategoryCode,
            facetDefinitions = facetDefinitionsForUi,
            lookupScopes = resolveTypedFacetLookupScopes(uiFacetFiltersState),
        )
    }

    val criteriaFacetResolution = remember(
        filters.query,
        filters.presetAttributes,
        filters.typedAttributeFilters,
        facetDefinitions,
        appliedTypedFacetUniverse,
        relaxedQueryTypedFacetKeys,
    ) {
        resolveResultsFacetResolution(
            query = filters.query,
            rawQueryText = filters.queryText,
            presetAttributes = filters.presetAttributes,
            explicitTypedAttributeFilters = filters.typedAttributeFilters,
            facetDefinitions = facetDefinitions,
            typedFacetUniverse = appliedTypedFacetUniverse,
            relaxedQueryTypedFacetKeys = relaxedQueryTypedFacetKeys,
            noticePriorityTypedFacetKeys = appliedFacetPresentationProfile
                ?.noticePriorityTypedFacetKeys
                .takeUnless { it.isNullOrEmpty() }
                ?: appliedFacetPresentationProfile?.orderedTypedFacetKeys.orEmpty(),
        )
    }

    val criteria = remember(
        filters,
        querySessionId,
        facetDefinitions,
        isCatalogScopeReady,
        criteriaFacetResolution,
        profileSettings.countryCode,
        profileSettings.hideUndeliverable,
    ) {
        if (!isCatalogScopeReady && resultsCatalogScopeRequired(filters)) {
            null
        } else {
            buildCriteria(
                filters = filters,
                querySessionId = querySessionId,
                facetDefinitions = facetDefinitions,
                facetResolution = criteriaFacetResolution,
                profileSettings = profileSettings,
            )
        }
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
    var rememberedQueryPriceBounds by remember(filters.queryText, filters.categoryCode) {
        mutableStateOf<PriceBounds?>(null)
    }

    LaunchedEffect(items, filters.queryText, filters.categoryCode) {
        if (items.isNotEmpty()) {
            rememberedQueryPriceBounds = availablePriceBounds(items)
        }
    }
    val effectivePriceBounds = rememberedQueryPriceBounds ?: availablePriceBounds(items)

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
            if (payload.items.isNotEmpty()) hasSearchCache = true
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

    val sellerTrustStats = remember(items) { buildSellerTrustSignalStats(items) }
    val availableSellerTrustSignals = remember(items, sellerTrustStats) {
        if (items.isEmpty()) {
            SellerTrustSignal.entries.toSet()
        } else {
            sellerTrustStats
                .filter { stats -> stats.available }
                .mapTo(LinkedHashSet()) { stats -> stats.signal }
        }
    }
    val dependencyContextBase = remember(facetDefinitions, categoriesByCode, availableSellerTrustSignals) {
        buildResultsDependencyContext(
            facetDefinitions = facetDefinitions,
            categoriesByCode = categoriesByCode,
            availableSellerTrustSignals = availableSellerTrustSignals,
        )
    }
    val workingDependencyContextBase = remember(facetDefinitionsForUi, categoriesByCode, availableSellerTrustSignals) {
        buildResultsDependencyContext(
            facetDefinitions = facetDefinitionsForUi,
            categoriesByCode = categoriesByCode,
            availableSellerTrustSignals = availableSellerTrustSignals,
        )
    }
    val dependencyContext = remember(dependencyContextBase, appliedTypedFacetUniverse, runtimeAttributeFacets) {
        dependencyContextBase.copy(
            typedFacetUniverse = appliedTypedFacetUniverse,
            runtimeAttributeFacets = runtimeAttributeFacets,
        )
    }
    val workingDependencyContext = remember(workingDependencyContextBase, uiTypedFacetUniverse, runtimeAttributeFacets) {
        workingDependencyContextBase.copy(
            typedFacetUniverse = uiTypedFacetUniverse,
            runtimeAttributeFacets = runtimeAttributeFacets,
        )
    }
    val sheetDependencyContext = remember(
        sheetTarget,
        dependencyContext,
        workingDependencyContext,
    ) {
        if (sheetTarget == FilterSheetTarget.Draft) workingDependencyContext else dependencyContext
    }

    val visibleItems = remember(items, hiddenIds) {
        items.filterNot { hiddenIds.contains(it.dto.id) }
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
                if (payload.items.isNotEmpty()) hasSearchCache = true
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
                if (payload.items.isNotEmpty()) hasSearchCache = true
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

    fun openOfferPageFor(item: ExplainedItem, position: Int) {
        val offerOpenUrls = resolveOfferOpenUrlsForResults(item.dto)
        FlowMetrics.markEvent(
            "offer_page_open",
            "screen=results offer_id=${item.dto.id} position=$position has_deeplink=${offerOpenUrls.hasRawDeeplink} host=${offerOpenUrls.host}",
        )
        val route = AppRoutes.offer(
            offerId = item.dto.id,
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
                .ifBlank { normalizedCategoryCode },
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
        workingFilters = filters.normalizeWithDependencies(dependencyContext).state
        sheetTarget = FilterSheetTarget.Draft
        sheetScreen = ResultsSheet.Filters
    }

    fun categorySheetInitialPath(selectedCategoryCode: String?): List<String> =
        buildCategoryPath(selectedCategoryCode, categoriesByCode).dropLast(1)

    fun openCategoryShortcut() {
        workingFilters = filters.normalizeWithDependencies(dependencyContext).state
        categoryTreePath = categorySheetInitialPath(filters.categoryCode)
        sheetTarget = FilterSheetTarget.Applied
        sheetScreen = ResultsSheet.Categories
    }

    LaunchedEffect(payload.mode) {
        if (payload.mode == ResultsMode.Categories) {
            openCategoryShortcut()
        }
    }

    LaunchedEffect(catalogReloadToken, categories) {
        inlineLeafAttributeDefsCache = null
    }

    suspend fun currentResultsCategories(): List<Category> {
        if (categories.isNotEmpty()) return categories
        val loaded = runCatching { catalogTaxonomyRepository.listCategories() }.getOrElse { emptyList() }
        if (loaded.isNotEmpty()) {
            categories = loaded
        }
        return loaded
    }

    suspend fun resolveInlineCategoryRedirect(categoryCode: String): String? {
        val normalizedCode = categoryCode.trim().uppercase(Locale.ROOT)
        if (normalizedCode.isBlank()) return null
        val availableCategories = currentResultsCategories()
        return resolveResultsCategoryCode(
            categoryCode = normalizedCode,
            categories = availableCategories,
        ) ?: normalizedCode
    }

    suspend fun parseInlineSubmitAttributes(
        queryText: String,
        categoryCode: String?,
        baseFilters: Map<String, String>,
    ): Map<String, String> {
        val normalizedQuery = SearchTextNormalizer.normalize(queryText)
        if (normalizedQuery.isBlank()) return emptyMap()
        val base = sanitizeResultsInterpretationFilters(baseFilters)
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
        val parsed = parseResultsAttributesFromFreeQuery(
            queryText = normalizedQuery,
            attributeDefs = defs,
        )
        if (parsed.isEmpty()) return base
        return (base + parsed).toMap(LinkedHashMap())
    }

    suspend fun resolveInlineLeafSubmitAttributeDefs(): Map<String, List<AttributeDef>> {
        inlineLeafAttributeDefsCache?.let { return it }
        val availableCategories = currentResultsCategories()
        val parentCodes = availableCategories.mapNotNull { it.parentCode }.toSet()
        val leafCodes = availableCategories
            .map { it.code }
            .filterNot { code -> code in parentCodes }
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
        inlineLeafAttributeDefsCache = resolved
        return resolved
    }

    suspend fun inferInlineLeafCategoryByFacets(
        queryText: String,
        baseFilters: Map<String, String>,
    ): String? {
        val normalizedQuery = SearchTextNormalizer.normalize(queryText)
        if (normalizedQuery.isBlank()) return null
        val cleanedBase = sanitizeResultsInterpretationFilters(baseFilters)
        val defsByLeaf = resolveInlineLeafSubmitAttributeDefs()
        if (defsByLeaf.isEmpty()) return null

        data class Candidate(
            val categoryCode: String,
            val score: Int,
            val totalMatches: Int,
            val nonIdentityMatches: Int,
        )

        val candidates = defsByLeaf.mapNotNull { (leafCode, defs) ->
            val parsed = parseResultsAttributesFromFreeQuery(
                queryText = normalizedQuery,
                attributeDefs = defs,
            )
            val merged = (cleanedBase + parsed)
            val totalMatches = merged.keys.count { key -> !isResultsInterpretationCategoryLevelKey(key) }
            val nonIdentityMatches = merged.keys.count { key ->
                !isResultsInterpretationCategoryLevelKey(key) && key !in resultsInterpretationIdentityKeys
            }
            if (totalMatches < 2 || nonIdentityMatches == 0) return@mapNotNull null
            val score = merged.keys
                .filterNot { key -> isResultsInterpretationCategoryLevelKey(key) }
                .sumOf { key -> if (key in resultsInterpretationIdentityKeys) 1 else 2 }
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

    fun inlineTemplateContext(currentFilters: FilterState): SearchTemplateContext =
        SearchTemplateContext(
            inputText = currentFilters.queryText,
            categoryCode = currentFilters.categoryCode,
            selectedFilters = currentFilters.toInterpretationSeedFilters(),
            isLocked = true,
        )

    val inlineSearchInterpretationDeps = remember(
        routeQueryTask,
        getBrowseNodeTask,
        catalogRepository,
        liveValuesRepository,
        constraintsResolver,
    ) {
        object : SearchInterpretationDependencies {
            override suspend fun route(queryText: String): SearchRouteResult? {
                val routed = runCatching { routeQueryTask(queryText) }.getOrNull() ?: return null
                return SearchRouteResult(
                    routeType = routed.routeType,
                    primaryTargetCode = routed.primaryTargetCode,
                    facetCollectionCode = routed.facetCollectionCode,
                    facetPresetCode = routed.facetPresetCode,
                )
            }

            override suspend fun resolveBrowseCategory(browseCode: String): String? =
                runCatching {
                    getBrowseNodeTask(browseCode)
                        ?.takeIf { node -> node.targetType == BrowseTargetType.CATEGORY }
                        ?.targetCategoryCode
                }.getOrNull()

            override suspend fun resolveCategoryRedirect(categoryCode: String): String? =
                resolveInlineCategoryRedirect(categoryCode)

            override suspend fun inferCategory(
                queryText: String,
                baseFilters: Map<String, String>,
            ): String? = inferInlineLeafCategoryByFacets(
                queryText = queryText,
                baseFilters = baseFilters,
            )

            override suspend fun parseAttributes(
                queryText: String,
                categoryCode: String?,
                baseFilters: Map<String, String>,
            ): Map<String, String> = parseInlineSubmitAttributes(
                queryText = queryText,
                categoryCode = categoryCode,
                baseFilters = baseFilters,
            )
        }
    }

    fun openSortSheet() {
        FlowMetrics.markEvent("open_sort_sheet", "screen=results")
        markFeatureFlagUsage(
            decision = sortSheetExplicitApplyDecision,
            action = "open_sort_sheet",
        )
        workingFilters = filters.normalizeWithDependencies(dependencyContext).state
        sheetTarget = FilterSheetTarget.Applied
        sheetScreen = ResultsSheet.Sort
    }

    fun applyInlineQuery(raw: String) {
        val normalizedText = SearchTextNormalizer.normalize(raw)
        if (normalizedText.isBlank()) {
            val normalized = filters
                .dropInterpretedSeedsForNewQuery()
                .copy(
                    queryText = "",
                    query = null,
                    facetCollectionCode = null,
                    facetPresetCode = null,
                )
                .normalizeWithDependencies(dependencyContext)
            filters = normalized.state
            workingFilters = normalized.state
            FlowMetrics.markEvent(
                "results_query_apply",
                "screen=results has_query=false query_len=0",
            )
            return
        }

        val currentFilters = filters
        val requestToken = inlineQuerySubmitToken + 1
        inlineQuerySubmitToken = requestToken
        scope.launch {
            val intent = searchInterpretationPipeline.interpret(
                SearchInterpretationRequest(
                    inputText = normalizedText,
                    source = SearchRequestSource.RAW_TEXT,
                    templateContext = inlineTemplateContext(currentFilters),
                ),
                inlineSearchInterpretationDeps,
            )
            if (inlineQuerySubmitToken != requestToken) return@launch
            val nextState = currentFilters
                .dropInterpretedSeedsForNewQuery()
                .applyInlineSearchIntent(intent)
            val normalized = nextState
                .normalizeWithDependencies(dependencyContext)
            inlineQueryInput = intent.queryText
            filters = normalized.state
            workingFilters = normalized.state
            FlowMetrics.markEvent(
                "results_query_apply",
                "screen=results has_query=${normalized.state.query != null} query_len=${intent.queryText.length}",
            )
        }
    }

    fun applyVisualCandidate(candidate: VisualSearchBoundCandidate) {
        val candidatePayload = ResultsPayload(
            query = candidate.query.normalizedQuery,
            queryText = resultsVisualCandidateLabel(candidate),
            categoryCode = candidate.query.categoryCode,
            facetCollectionCode = candidate.query.searchCriteria.facetCollectionCode,
            facetPresetCode = candidate.query.searchCriteria.facetPresetCode,
            sellerId = candidate.query.searchCriteria.sellerId,
            querySessionId = querySessionId,
            location = candidate.query.searchCriteria.location,
            radiusKm = candidate.query.searchCriteria.radiusKm,
            conditions = candidate.query.searchCriteria.conditions,
            sort = filters.sort,
            origin = payload.origin,
        )
        val baseState = initialFilterState(candidatePayload).copy(
            purchaseFormat = filters.purchaseFormat,
            sellerTrustPreset = filters.sellerTrustPreset,
            sellerTrustSignals = filters.sellerTrustSignals,
            location = filters.location ?: candidate.query.searchCriteria.location,
            radiusKm = filters.radiusKm ?: candidate.query.searchCriteria.radiusKm,
            centerLat = filters.centerLat,
            centerLon = filters.centerLon,
            deliverableOnly = filters.deliverableOnly,
            sort = filters.sort,
        )
        val normalized = baseState.normalizeWithDependencies(dependencyContext)
        inlineQueryInput = candidatePayload.queryText
        filters = normalized.state
        workingFilters = normalized.state
        activeVisualContext = activeVisualContext?.selectCandidate(candidate)
        FlowMetrics.markEvent(
            "ui_results_visual_candidate_selected",
            "screen=results rank=${candidate.rank} route=${candidate.query.routeKind.name.lowercase(Locale.ROOT)}",
        )
    }

    fun toggleSortIfPriceOrOpenSheet() {
        openSortSheet()
    }

    fun openViewModeSheet() {
        val nextMode = when (viewMode.normalized()) {
            ResultsViewMode.Grid -> ResultsViewMode.List
            else -> ResultsViewMode.Grid
        }
        FlowMetrics.markEvent("toggle_view_mode", "screen=results mode=${nextMode.name.lowercase()}")
        FlowMetrics.markEvent("select_view_mode", "screen=results mode=${nextMode.name.lowercase()}")
        FlowMetrics.markEvent("apply_view_mode", "screen=results mode=${nextMode.name.lowercase()}")
        markFeatureFlagUsage(
            decision = viewToggleSegmentedDecision,
            action = "toggle_view_mode",
        )
        viewMode = nextMode
    }

    LaunchedEffect(sheetScreen) {
        if (sheetScreen == ResultsSheet.Location) {
            markFeatureFlagUsage(
                decision = geoRadiusSliderDecision,
                action = "open_location_sheet",
            )
        }
    }

    LaunchedEffect(workingFilters, sheetTarget, sheetScreen, workingDependencyContext) {
        if (sheetTarget != FilterSheetTarget.Draft) return@LaunchedEffect
        val currentSheet = sheetScreen ?: return@LaunchedEffect
        val shouldAutoRefreshResults = when (currentSheet) {
            ResultsSheet.Filters,
            ResultsSheet.Categories,
            ResultsSheet.Brands,
            ResultsSheet.Price,
            ResultsSheet.PurchaseFormat,
            ResultsSheet.Condition,
            ResultsSheet.SellerTrust,
            ResultsSheet.TypedAttribute,
            ResultsSheet.Location,
                -> true

            else -> false
        }
        if (!shouldAutoRefreshResults) return@LaunchedEffect

        val normalized = workingFilters.normalizeWithDependencies(workingDependencyContext)
        if (normalized.invalidatedFilterIds.isNotEmpty()) {
            FlowMetrics.markEvent(
                "filters_dependency_invalidation",
                "screen=results origin=live_refresh invalidated=${normalized.invalidatedFilterIds.joinToString(",")}",
            )
        }
        if (filters != normalized.state) {
            filters = normalized.state
        }
        if (workingFilters != normalized.state) {
            workingFilters = normalized.state
        }
    }

    fun openChipFilterEditor(chipKey: String) {
        workingFilters = filters.normalizeWithDependencies(dependencyContext).state
        sheetTarget = FilterSheetTarget.Draft
        when {
            chipKey == AppliedChipKey.Category -> {
                categoryTreePath = categorySheetInitialPath(filters.categoryCode)
                sheetScreen = ResultsSheet.Categories
            }
            chipKey == AppliedChipKey.Brand -> sheetScreen = ResultsSheet.Brands
            chipKey == AppliedChipKey.Price -> sheetScreen = ResultsSheet.Price
            chipKey == AppliedChipKey.Condition -> sheetScreen = ResultsSheet.Condition
            chipKey == AppliedChipKey.PurchaseFormat -> sheetScreen = ResultsSheet.Filters
            chipKey == AppliedChipKey.SellerTrust -> sheetScreen = ResultsSheet.SellerTrust
            chipKey == AppliedChipKey.Location -> sheetScreen = ResultsSheet.Location
            chipKey.startsWith(AppliedChipKey.TypedPrefix) -> {
                activeTypedFacetKey = chipKey.removePrefix(AppliedChipKey.TypedPrefix)
                sheetScreen = ResultsSheet.TypedAttribute
            }
            else -> sheetScreen = ResultsSheet.Filters
        }
    }

    fun clearAppliedChip(chipKey: String) {
        val result = filters.clearAppliedChipWithDependencies(
            chipKey = chipKey,
            context = dependencyContext,
        )
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
        openOfferPageFor(item, analyticsPosition)
    }

    fun openEmptyCategories() {
        if (onOpenCategories != null) {
            onOpenCategories()
            return
        }
        openCategoryShortcut()
    }

    fun openPhotoSearch() {
        Toast.makeText(
            context,
            "Поиск по фото будет возвращён новым flow. Legacy wizard удалён.",
            Toast.LENGTH_SHORT,
        ).show()
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

    val appliedFacetResolution = remember(
        filters.query,
        filters.presetAttributes,
        filters.typedAttributeFilters,
        facetDefinitions,
        appliedTypedFacetUniverse,
        runtimeAttributeFacets,
        relaxedQueryTypedFacetKeys,
    ) {
        resolveResultsFacetResolution(
            query = filters.query,
            rawQueryText = filters.queryText,
            presetAttributes = filters.presetAttributes,
            explicitTypedAttributeFilters = filters.typedAttributeFilters,
            facetDefinitions = facetDefinitions,
            typedFacetUniverse = appliedTypedFacetUniverse,
            runtimeAttributeFacets = runtimeAttributeFacets,
            relaxedQueryTypedFacetKeys = relaxedQueryTypedFacetKeys,
            noticePriorityTypedFacetKeys = appliedFacetPresentationProfile
                ?.noticePriorityTypedFacetKeys
                .takeUnless { it.isNullOrEmpty() }
                ?: appliedFacetPresentationProfile?.orderedTypedFacetKeys.orEmpty(),
        )
    }
    val uiFacetResolution = remember(
        uiFacetFiltersState.query,
        uiFacetFiltersState.presetAttributes,
        uiFacetFiltersState.typedAttributeFilters,
        facetDefinitionsForUi,
        uiTypedFacetUniverse,
        runtimeAttributeFacets,
        if (uiFacetFiltersState == filters) relaxedQueryTypedFacetKeys else emptySet(),
    ) {
        resolveResultsFacetResolution(
            query = uiFacetFiltersState.query,
            rawQueryText = uiFacetFiltersState.queryText,
            presetAttributes = uiFacetFiltersState.presetAttributes,
            explicitTypedAttributeFilters = uiFacetFiltersState.typedAttributeFilters,
            facetDefinitions = facetDefinitionsForUi,
            typedFacetUniverse = uiTypedFacetUniverse,
            runtimeAttributeFacets = runtimeAttributeFacets,
            relaxedQueryTypedFacetKeys = if (uiFacetFiltersState == filters) {
                relaxedQueryTypedFacetKeys
            } else {
                emptySet()
            },
            noticePriorityTypedFacetKeys = facetPresentationProfile
                ?.noticePriorityTypedFacetKeys
                .takeUnless { it.isNullOrEmpty() }
                ?: facetPresentationProfile?.orderedTypedFacetKeys.orEmpty(),
        )
    }
    val facetResolutionNotices = remember(appliedFacetResolution) {
        appliedFacetResolution.noticesByRuntimeKey.values.toList()
    }
    val resultsScopeMessage = remember(filters, appliedFacetResolution.effectiveTypedAttributeFilters) {
        resolveResultsFiltersScopeMessage(
            filters = filters,
            effectiveTypedAttributeFilters = appliedFacetResolution.effectiveTypedAttributeFilters,
        )
    }
    val uiResultsScopeMessage = remember(uiFacetFiltersState, uiFacetResolution.effectiveTypedAttributeFilters) {
        resolveResultsFiltersScopeMessage(
            filters = uiFacetFiltersState,
            effectiveTypedAttributeFilters = uiFacetResolution.effectiveTypedAttributeFilters,
        )
    }
    val isFilterHubVisible = sheetScreen == ResultsSheet.Filters
    val isWorkingFiltersDirty = workingFilters != filters
    val workingCriteria = remember(
        uiFacetFiltersState,
        querySessionId,
        facetDefinitionsForUi,
        isCatalogScopeReady,
        uiFacetResolution,
        profileSettings.countryCode,
        profileSettings.hideUndeliverable,
    ) {
        if (!isCatalogScopeReady && resultsCatalogScopeRequired(uiFacetFiltersState)) {
            null
        } else {
            buildCriteria(
                filters = uiFacetFiltersState,
                querySessionId = querySessionId,
                facetDefinitions = facetDefinitionsForUi,
                facetResolution = uiFacetResolution,
                profileSettings = profileSettings,
            )
        }
    }
    var workingPreviewTotalItems by remember(workingCriteria, isFilterHubVisible) { mutableStateOf<Int?>(null) }

    LaunchedEffect(
        isFilterHubVisible,
        isWorkingFiltersDirty,
        workingCriteria,
        totalItems,
    ) {
        if (!isFilterHubVisible) {
            workingPreviewTotalItems = null
            return@LaunchedEffect
        }
        if (!isWorkingFiltersDirty) {
            workingPreviewTotalItems = totalItems
            return@LaunchedEffect
        }
        val previewCriteria = workingCriteria ?: run {
            workingPreviewTotalItems = null
            return@LaunchedEffect
        }
        workingPreviewTotalItems = null
        delay(180)
        val request = previewCriteria.copy(limit = 1).toResultsFacetedRequest(
            includeRuntimeFacets = false,
        )
        workingPreviewTotalItems = runCatching { searchOffersWithFacets(request) }
            .getOrNull()
            ?.total
    }

    LaunchedEffect(criteria, runtimeAttributeFacets, criteriaFacetResolution.queryBackedTypedAttributeFilters) {
        if (criteria == null) return@LaunchedEffect
        val conflictingKeys = collectRuntimeRelaxedQueryTypedFacetKeys(
            queryBackedTypedAttributeFilters = criteriaFacetResolution.queryBackedTypedAttributeFilters,
            runtimeAttributeFacets = runtimeAttributeFacets,
        )
        if (conflictingKeys.isEmpty()) return@LaunchedEffect
        val nextRelaxedKeys = relaxedQueryTypedFacetKeys + conflictingKeys
        if (nextRelaxedKeys != relaxedQueryTypedFacetKeys) {
            relaxedQueryTypedFacetKeys = nextRelaxedKeys
        }
    }

    val backAction = onBack ?: navController?.let { { it.popBackStack(); Unit } }
    val activeFiltersCount = remember(filters, appliedFacetResolution.effectiveTypedAttributeFilters) {
        filters.activeFilterCount(appliedFacetResolution.effectiveTypedAttributeFilters)
    }
    val typedFacetTitlesByRuntimeKey = remember(facetDefinitions) { buildTypedFacetTitlesByRuntimeKey(facetDefinitions) }
    val appliedChips = remember(
        filters,
        typedFacetTitlesByRuntimeKey,
        categoriesByCode,
        appliedFacetResolution.effectiveTypedAttributeFilters,
    ) {
        buildAppliedFilterChips(
            filters = filters,
            typedFacetTitlesByRuntimeKey = typedFacetTitlesByRuntimeKey,
            categoriesByCode = categoriesByCode,
            effectiveTypedAttributeFilters = appliedFacetResolution.effectiveTypedAttributeFilters,
        )
    }
    val photoModeCategoryLabel = remember(filters.categoryPath, filters.categoryCode, payload.categoryCode) {
        filters.categoryPath.joinToString(" → ")
            .trim()
            .ifBlank { filters.categoryCode ?: payload.categoryCode.orEmpty() }
            .ifBlank { "" }
    }
    val inlinePhotoUris = remember(visualContext) {
        visualContext?.photoUris
            ?.map { uri -> uri.trim() }
            ?.filter { uri -> uri.isNotEmpty() }
            .orEmpty()
    }
    val inlinePhotoPrimaryChips = remember(filters, visualContext, photoModeCategoryLabel) {
        buildResultsPhotoPrimaryChipLabels(
            filters = filters,
            visualContext = visualContext,
            categoryLabel = photoModeCategoryLabel.takeIf { it.isNotBlank() },
        )
    }
    val visualCandidates = remember(visualContext) {
        buildResultsVisualCandidates(visualContext)
    }
    val showInlinePhotoContext = isPhotoMode && visualContext != null && inlinePhotoUris.isNotEmpty()
    val effectiveViewMode = viewMode.normalized()
    val showAppliedChipsRow = false
    val sortOptions = remember(items, filters.location, filters.categoryCode, payload.categoryCode) {
        buildSortOptions(
            items = items,
            location = filters.location,
            categoryCode = filters.categoryCode ?: payload.categoryCode,
        )
    }
    val currentSortAvailable = sortOptions
        .firstOrNull { option -> option.sort == filters.sort }
        ?.enabled
        ?: true

    LaunchedEffect(showAppliedChipsRow, activeFiltersCount) {
        val isVisible = showAppliedChipsRow
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

    LaunchedEffect(isPhotoMode, visualContext?.visualSessionId, photoModeCategoryLabel) {
        if (!isPhotoMode || visualContext == null) return@LaunchedEffect
        FlowMetrics.markEvent(
            "ui_results_photo_mode_shown",
            "screen=results binder_status=${visualContext.binderStatus?.name?.lowercase(Locale.ROOT) ?: "none"} " +
                "exact_route=${visualContext.exactRoute} intent=${visualContext.intent?.name?.lowercase(Locale.ROOT) ?: "none"} " +
                "chips=${visualContext.chips.size} category=${photoModeCategoryLabel.ifBlank { "none" }}",
        )
    }

    val shouldShowEmptyStateImmediate = !isLoading && errorMessage == null && visibleItems.isEmpty()
    var shouldShowEmptyState by remember(criteria) { mutableStateOf(false) }
    val hasActiveFilters = activeFiltersCount > 0
    val emptyTitle = when {
        hasActiveFilters && isPhotoMode -> "Фото-ориентиры сузили выдачу"
        hasActiveFilters -> stringResource(R.string.results_empty_filters_too_strict_title)
        isPhotoMode -> "Пока нет предложений по фото"
        else -> stringResource(R.string.results_empty_no_results_title)
    }
    val emptyMessage = when {
        hasActiveFilters -> "Активно фильтров: $activeFiltersCount. Сбросьте их одним действием или измените набор."
        isPhotoMode -> "Мы сохранили найденные ориентиры. Откройте категорию или уточните бренд, чтобы расширить выдачу."
        else -> stringResource(R.string.results_empty_no_results_body)
    }
    val emptyPrimaryAction = if (hasActiveFilters) {
        StateAction(
            label = "Сбросить фильтры ($activeFiltersCount)",
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
    val emptySecondaryAction = if (hasActiveFilters) {
        StateAction(
            label = "Изменить фильтры",
            onAction = { openFiltersHub() },
            type = StateActionType.CHANGE_QUERY,
        )
    } else if (isPhotoMode) {
        StateAction(
            label = "Уточнить запрос",
            onAction = {
                if (onEditQuery != null) {
                    onEditQuery()
                } else {
                    openCategoryShortcut()
                }
            },
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
    ScreenRoot(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        applySafeInsets = applySafeInsets,
        extraBottomPadding = extraBottomPadding,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LayoutDefaults.HorizontalPadding, vertical = LayoutDefaults.SectionSpacing),
                verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing),
            ) {
            ResultsTopBar(
                title = when {
                    !filters.sellerName.isNullOrBlank() -> "Объявления продавца"
                    isPhotoMode -> "Результаты по фото"
                    else -> "Результаты"
                },
                onBack = backAction,
                onClose = onClose,
                applySafeInsets = !applySafeInsets,
            )

            ResultsInlineQueryField(
                value = inlineQueryInput,
                onValueChange = { inlineQueryInput = it },
                onSubmit = { submitted ->
                    val trimmed = submitted.trim()
                    if (trimmed.isEmpty()) {
                        applyInlineQuery("")
                    } else {
                        applyInlineQuery(trimmed)
                    }
                },
                onClear = {
                    inlineQueryInput = ""
                    if (showInlinePhotoContext) {
                        activeVisualContext = null
                    }
                    applyInlineQuery("")
                },
                onFocus = onEditQuery,
                placeholder = if (showInlinePhotoContext) "" else stringResource(R.string.results_query_summary_default),
                photoUris = if (showInlinePhotoContext) inlinePhotoUris else emptyList(),
                primaryChips = if (showInlinePhotoContext) inlinePhotoPrimaryChips else emptyList(),
            )

            if (isPhotoMode && visualContext != null) {
                ResultsPhotoModeRail(
                    querySummary = filters.queryText,
                    categoryLabel = photoModeCategoryLabel.takeIf { it.isNotBlank() },
                    visualContext = visualContext,
                    visualCandidates = visualCandidates,
                    onSelectCandidate = ::applyVisualCandidate,
                    onEditQuery = onEditQuery,
                )
            }

            if (showAppliedChipsRow) {
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

            if (!shouldShowEmptyStateImmediate &&
                (
                    facetResolutionNotices.isNotEmpty() ||
                        appliedFacetResolution.autoAppliedTypedAttributeFilters.isNotEmpty() ||
                        !resultsScopeMessage.isNullOrBlank()
                    )
            ) {
                ResultsFacetResolutionBanner(
                    notices = facetResolutionNotices,
                    autoApplied = appliedFacetResolution.autoAppliedTypedAttributeFilters,
                    typedFacetTitlesByRuntimeKey = typedFacetTitlesByRuntimeKey,
                    suppressedAutoAppliedRuntimeKeys = appliedFacetPresentationProfile
                        ?.suppressedAutoAppliedTypedFacetKeys
                        .orEmpty(),
                    scopeMessage = resultsScopeMessage,
                )
            }

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
                            columns = GridCells.Fixed(2),
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
                        columns = GridCells.Fixed(2),
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
                                categoryCode = filters.categoryCode ?: payload.categoryCode,
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
                                categoryCode = filters.categoryCode ?: payload.categoryCode,
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
            FiltersHandleOverlay(
                activeCount = activeFiltersCount,
                dock = FiltersHandleDock.Bottom,
                currentSort = filters.sort,
                currentViewMode = effectiveViewMode,
                onDockChange = { filtersHandleDock = FiltersHandleDock.Bottom },
                onOpenFilters = {
                    FlowMetrics.markEvent("search_action_row_tap", "screen=results control=filters")
                    openFiltersHub()
                },
                onOpenSort = {
                    FlowMetrics.markEvent("search_action_row_tap", "screen=results control=sort")
                    openSortSheet()
                },
                onOpenView = {
                    FlowMetrics.markEvent("search_action_row_tap", "screen=results control=view")
                    openViewModeSheet()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val useSideFiltersFlow = false

    val showDraftDetailSideSheet = useSideFiltersFlow &&
        sheetTarget == FilterSheetTarget.Draft && when (sheetScreen) {
        ResultsSheet.Filters,
        ResultsSheet.Categories,
        ResultsSheet.Brands,
        ResultsSheet.Price,
        ResultsSheet.Sort,
        ResultsSheet.View,
        ResultsSheet.PurchaseFormat,
        ResultsSheet.Condition,
        ResultsSheet.SellerTrust,
        ResultsSheet.TypedAttribute,
        ResultsSheet.Location,
            -> true

        else -> false
    }

    if (showDraftDetailSideSheet) {
        FilterHubSideSheet(
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
                    else -> Unit
                }
                sheetScreen = null
            },
            dock = filtersHandleDock,
        ) {
            when (sheetScreen) {
                ResultsSheet.Filters -> FilterHubSheet(
                    filters = workingFilters,
                    facetFilters = facetUiFilters,
                    runtimeAttributeFacets = runtimeAttributeFacets,
                    typedFacetUniverse = uiTypedFacetUniverse,
                    typedFacetResolution = uiFacetResolution,
                    categoriesByCode = categoriesByCode,
                    currentViewMode = effectiveViewMode,
                    showSortAndViewRows = true,
                    activeCount = workingFilters.activeFilterCount(uiFacetResolution.effectiveTypedAttributeFilters),
                    isDirty = workingFilters != filters,
                    isApplyEnabled = workingFilters != filters && !isFiltersApplyRateLimited,
                    applyResultsCount = if (isWorkingFiltersDirty) workingPreviewTotalItems else totalItems,
                    scopeMessage = uiResultsScopeMessage,
                    dependencyContext = workingDependencyContext,
                    priceBounds = effectivePriceBounds,
                    conditionOptions = conditionOptions(
                        runtimeFacets = runtimeConditionFacets,
                        selected = workingFilters.conditions,
                    ),
                    onFiltersChange = { updated -> workingFilters = updated },
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
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Sort
                    },
                    onOpenCategory = {
                        FlowMetrics.markEvent(
                            "filters_overlay_open_detail",
                            "screen=results detail=category",
                        )
                        sheetTarget = FilterSheetTarget.Draft
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
                            ResultsFacetFilterType.DeliveryChannel -> ResultsSheet.Filters
                            ResultsFacetFilterType.SellerTrust -> ResultsSheet.SellerTrust
                            ResultsFacetFilterType.TypedAttribute -> {
                                activeTypedFacetKey = facetFilter.runtimeKey
                                ResultsSheet.TypedAttribute
                            }
                            ResultsFacetFilterType.Unsupported -> ResultsSheet.Filters
                        }
                    },
                    onOpenSellerTrust = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.SellerTrust
                    },
                    onInlineTypedValueSelect = { facetKey, value ->
                        val normalizedKey = facetKey.trim().lowercase()
                        if (normalizedKey.isBlank()) return@FilterHubSheet
                        val current = workingFilters.typedAttributeFilters[normalizedKey]
                        val isSameValue = current?.op == TypedAttributeOperator.EQ &&
                            current.value.trim().equals(value, ignoreCase = true)
                        workingFilters = workingFilters.withTypedAttributeDraft(
                            runtimeKey = normalizedKey,
                            draft = if (isSameValue) {
                                null
                            } else {
                                TypedAttributeFilterDraft(
                                    op = TypedAttributeOperator.EQ,
                                    value = value,
                                )
                            },
                        )
                    },
                    onViewModeChange = { nextMode ->
                        val normalized = nextMode.normalized()
                        FlowMetrics.markEvent(
                            "select_view_mode",
                            "screen=results mode=${normalized.name.lowercase()}",
                        )
                        FlowMetrics.markEvent(
                            "apply_view_mode",
                            "screen=results mode=${normalized.name.lowercase()}",
                        )
                        viewMode = normalized
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
                        val normalized = workingFilters.normalizeWithDependencies(workingDependencyContext)
                        if (normalized.invalidatedFilterIds.isNotEmpty()) {
                            FlowMetrics.markEvent(
                                "filters_dependency_invalidation",
                                "screen=results origin=apply changed=overlay invalidated=${normalized.invalidatedFilterIds.joinToString(",")}",
                            )
                        }
                        val appliedCount = normalized.state.activeFilterCount()
                        filters = normalized.state
                        workingFilters = normalized.state
                        FlowMetrics.markEvent(
                            "filters_overlay_apply_success",
                            "screen=results active_count=$appliedCount",
                        )
                        sheetScreen = null
                    },
                )
                ResultsSheet.Categories -> CategoryPickerSheet(
                    categoriesByParent = categoriesByParent,
                    categoriesByCode = categoriesByCode,
                    pathCodes = categoryTreePath,
                    selectedCategoryCode = workingFilters.categoryCode,
                    onPathChange = { categoryTreePath = it },
                    onSelectCategory = { code, path ->
                        val result = workingFilters.applyCategoryWithDependencies(
                            code = code,
                            path = path,
                            context = workingDependencyContext,
                        )
                        workingFilters = result.state
                        if (result.invalidatedFilterIds.isNotEmpty()) {
                            FlowMetrics.markEvent(
                                "filters_dependency_invalidation",
                                "screen=results origin=category_select changed=category invalidated=${result.invalidatedFilterIds.joinToString(",")}",
                            )
                        }
                        sheetScreen = ResultsSheet.Filters
                    },
                    onBack = { sheetScreen = ResultsSheet.Filters },
                )
                ResultsSheet.Brands -> BrandPickerSheet(
                    query = brandQuery,
                    onQueryChange = { brandQuery = it },
                    availableBrands = availableBrandOptions(
                        items = items,
                        runtimeFacets = runtimeBrandFacets,
                        categoryCode = uiFacetFiltersState.categoryCode ?: filters.categoryCode,
                        categoriesByCode = categoriesByCode,
                        selectedBrands = workingFilters.brands,
                        typedAttributeFilters = uiFacetResolution.effectiveTypedAttributeFilters,
                        query = uiFacetFiltersState.query,
                    ),
                    selected = workingFilters.brands,
                    onSelect = { brand ->
                        workingFilters = workingFilters.toggleBrand(brand)
                    },
                    onReset = {
                        workingFilters = workingFilters.clearBrandSelection()
                    },
                    onDone = { sheetScreen = ResultsSheet.Filters },
                    onBack = { sheetScreen = ResultsSheet.Filters },
                )
                ResultsSheet.Price -> PricePickerSheet(
                    filters = workingFilters,
                    priceBounds = effectivePriceBounds,
                    onReset = { workingFilters = workingFilters.copy(priceMin = null, priceMax = null) },
                    onApply = { min, max ->
                        workingFilters = workingFilters.copy(priceMin = min, priceMax = max)
                        sheetScreen = ResultsSheet.Filters
                    },
                    onBack = { sheetScreen = ResultsSheet.Filters },
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
                        workingFilters = workingFilters.copy(sort = sort)
                        sheetScreen = ResultsSheet.Filters
                    },
                    onBack = {
                        FlowMetrics.markEvent(
                            "dismiss_sort_sheet",
                            "screen=results reason=back",
                        )
                        sheetScreen = ResultsSheet.Filters
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
                        sheetScreen = ResultsSheet.Filters
                    },
                    onBack = {
                        FlowMetrics.markEvent(
                            "dismiss_view_sheet",
                            "screen=results reason=back",
                        )
                        sheetScreen = ResultsSheet.Filters
                    },
                )
                ResultsSheet.PurchaseFormat -> PurchaseFormatSheet(
                    current = workingFilters.purchaseFormat,
                    deliverableOnly = workingFilters.deliverableOnly,
                    options = purchaseFormatOptions(
                        runtimeFacets = runtimeDeliveryChannelFacets,
                        current = workingFilters.purchaseFormat,
                    ),
                    onSelect = { format ->
                        workingFilters = workingFilters.copy(purchaseFormat = format)
                        sheetScreen = ResultsSheet.Filters
                    },
                    onToggleDeliverableOnly = { enabled ->
                        workingFilters = workingFilters.copy(deliverableOnly = enabled)
                    },
                    onBack = { sheetScreen = ResultsSheet.Filters },
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
                    onReset = {
                        workingFilters = workingFilters.clearConditionSelection()
                    },
                    onBack = { sheetScreen = ResultsSheet.Filters },
                )
                ResultsSheet.SellerTrust -> SellerTrustSheet(
                    currentPreset = workingFilters.sellerTrustPreset,
                    selectedSignals = workingFilters.sellerTrustSignals,
                    options = sellerTrustOptions(
                        stats = sellerTrustStats,
                        selectedSignals = workingFilters.sellerTrustSignals,
                    ),
                    onSelectPreset = { preset ->
                        workingFilters = workingFilters.withSellerTrustPreset(
                            preset = preset,
                            availableSignals = workingDependencyContext.availableSellerTrustSignals,
                        )
                    },
                    onToggleSignal = { signal, enabled ->
                        workingFilters = workingFilters.toggleSellerTrustSignal(
                            signal = signal,
                            enabled = enabled,
                            availableSignals = workingDependencyContext.availableSellerTrustSignals,
                        )
                    },
                    onReset = {
                        workingFilters = workingFilters.withSellerTrustPreset(
                            preset = SellerTrustPreset.Any,
                            availableSignals = workingDependencyContext.availableSellerTrustSignals,
                        )
                    },
                    onBack = { sheetScreen = ResultsSheet.Filters },
                )
                ResultsSheet.TypedAttribute -> {
                    val facetKey = activeTypedFacetKey
                    val facetFilter = facetUiFilters.firstOrNull { it.runtimeKey == facetKey }
                    val definition = facetDefinitionsForUi.firstOrNull { definition ->
                        definition.normalizedRuntimeKey() == facetKey
                    }
                    if (facetFilter == null || facetKey.isNullOrBlank()) {
                        sheetScreen = ResultsSheet.Filters
                    } else {
                        TypedAttributeFilterSheet(
                            runtimeKey = facetKey,
                            title = facetFilter.title,
                            valueType = definition?.valueType,
                            selectionMode = definition?.selectionMode,
                            uiWidget = definition?.uiWidget,
                            runtimeValues = runtimeAttributeFacets[facetKey].orEmpty(),
                            availableSeedValues = uiTypedFacetUniverse
                                .entriesByRuntimeKey[facetKey]
                                ?.seedAvailableValues
                                .orEmpty(),
                            knownValues = uiTypedFacetUniverse
                                .entriesByRuntimeKey[facetKey]
                                ?.knownValues
                                .orEmpty(),
                            draft = uiFacetResolution.effectiveDraftForRuntimeKey(facetKey),
                            onApply = { draft ->
                                workingFilters = workingFilters.withTypedAttributeDraft(
                                    runtimeKey = facetKey,
                                    draft = draft,
                                )
                                sheetScreen = ResultsSheet.Filters
                            },
                            onBack = { sheetScreen = ResultsSheet.Filters },
                        )
                    }
                }
                ResultsSheet.Location -> LocationSheet(
                    current = workingFilters.location,
                    radiusKm = workingFilters.radiusKm,
                    isResolvingCurrentLocation = isResolvingCurrentLocation,
                    useSliderRadius = geoRadiusSliderDecision.effectiveValue,
                    onApply = { location, radius ->
                        val keepGeoCenter = location != null &&
                            workingFilters.location
                                ?.trim()
                                ?.equals(location.trim(), ignoreCase = true) == true &&
                            workingFilters.centerLat != null &&
                            workingFilters.centerLon != null
                        workingFilters = workingFilters.copy(
                            location = location,
                            radiusKm = radius,
                            centerLat = if (keepGeoCenter) workingFilters.centerLat else null,
                            centerLon = if (keepGeoCenter) workingFilters.centerLon else null,
                        )
                        sheetScreen = ResultsSheet.Filters
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
                    onBack = { sheetScreen = ResultsSheet.Filters },
                )
                else -> Unit
            }
        }
    }

    if (sheetScreen != null && ((sheetScreen != ResultsSheet.Filters && !showDraftDetailSideSheet) || !useSideFiltersFlow)) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                when (sheetScreen) {
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
            containerColor = Color.White,
            scrimColor = if (sheetScreen == ResultsSheet.Filters) {
                Color.Transparent
            } else {
                BottomSheetDefaults.ScrimColor
            },
        ) {
            when (sheetScreen) {
                ResultsSheet.Filters -> FilterHubSheet(
                    filters = workingFilters,
                    facetFilters = facetUiFilters,
                    runtimeAttributeFacets = runtimeAttributeFacets,
                    typedFacetUniverse = uiTypedFacetUniverse,
                    typedFacetResolution = uiFacetResolution,
                    categoriesByCode = categoriesByCode,
                    currentViewMode = effectiveViewMode,
                    showSortAndViewRows = false,
                    activeCount = workingFilters.activeFilterCount(uiFacetResolution.effectiveTypedAttributeFilters),
                    isDirty = workingFilters != filters,
                    isApplyEnabled = workingFilters != filters && !isFiltersApplyRateLimited,
                    applyResultsCount = if (isWorkingFiltersDirty) workingPreviewTotalItems else totalItems,
                    scopeMessage = uiResultsScopeMessage,
                    dependencyContext = workingDependencyContext,
                    priceBounds = effectivePriceBounds,
                    conditionOptions = conditionOptions(
                        runtimeFacets = runtimeConditionFacets,
                        selected = workingFilters.conditions,
                    ),
                    onFiltersChange = { updated -> workingFilters = updated },
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
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Sort
                    },
                    onOpenCategory = {
                        FlowMetrics.markEvent(
                            "filters_overlay_open_detail",
                            "screen=results detail=category",
                        )
                        sheetTarget = FilterSheetTarget.Draft
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
                            ResultsFacetFilterType.DeliveryChannel -> ResultsSheet.Filters
                            ResultsFacetFilterType.SellerTrust -> ResultsSheet.SellerTrust
                            ResultsFacetFilterType.TypedAttribute -> {
                                activeTypedFacetKey = facetFilter.runtimeKey
                                ResultsSheet.TypedAttribute
                            }
                            ResultsFacetFilterType.Unsupported -> ResultsSheet.Filters
                        }
                    },
                    onOpenSellerTrust = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.SellerTrust
                    },
                    onInlineTypedValueSelect = { facetKey, value ->
                        val normalizedKey = facetKey.trim().lowercase()
                        if (normalizedKey.isBlank()) return@FilterHubSheet
                        val current = workingFilters.typedAttributeFilters[normalizedKey]
                        val isSameValue = current?.op == TypedAttributeOperator.EQ &&
                            current.value.trim().equals(value, ignoreCase = true)
                        workingFilters = workingFilters.withTypedAttributeDraft(
                            runtimeKey = normalizedKey,
                            draft = if (isSameValue) {
                                null
                            } else {
                                TypedAttributeFilterDraft(
                                    op = TypedAttributeOperator.EQ,
                                    value = value,
                                )
                            },
                        )
                    },
                    onViewModeChange = { nextMode ->
                        val normalized = nextMode.normalized()
                        FlowMetrics.markEvent(
                            "select_view_mode",
                            "screen=results mode=${normalized.name.lowercase()}",
                        )
                        FlowMetrics.markEvent(
                            "apply_view_mode",
                            "screen=results mode=${normalized.name.lowercase()}",
                        )
                        viewMode = normalized
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
                        val normalized = workingFilters.normalizeWithDependencies(workingDependencyContext)
                        if (normalized.invalidatedFilterIds.isNotEmpty()) {
                            FlowMetrics.markEvent(
                                "filters_dependency_invalidation",
                                "screen=results origin=apply changed=overlay invalidated=${normalized.invalidatedFilterIds.joinToString(",")}",
                            )
                        }
                        val appliedCount = normalized.state.activeFilterCount()
                        filters = normalized.state
                        workingFilters = normalized.state
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
                    categoriesByParent = categoriesByParent,
                    categoriesByCode = categoriesByCode,
                    pathCodes = categoryTreePath,
                    selectedCategoryCode = workingFilters.categoryCode,
                    onPathChange = { categoryTreePath = it },
                    onSelectCategory = { code, path ->
                        val result = workingFilters.applyCategoryWithDependencies(
                            code = code,
                            path = path,
                            context = sheetDependencyContext,
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
                        runtimeFacets = runtimeBrandFacets,
                        categoryCode = uiFacetFiltersState.categoryCode ?: filters.categoryCode,
                        categoriesByCode = categoriesByCode,
                        selectedBrands = workingFilters.brands,
                        typedAttributeFilters = uiFacetResolution.effectiveTypedAttributeFilters,
                        query = uiFacetFiltersState.query,
                    ),
                    selected = workingFilters.brands,
                    onSelect = { brand ->
                        workingFilters = workingFilters.toggleBrand(brand)
                    },
                    onReset = {
                        workingFilters = workingFilters.clearBrandSelection()
                    },
                    onDone = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            val normalized = workingFilters.normalizeWithDependencies(sheetDependencyContext)
                            filters = normalized.state
                            workingFilters = normalized.state
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
                    priceBounds = effectivePriceBounds,
                    onReset = { workingFilters = workingFilters.copy(priceMin = null, priceMax = null) },
                    onApply = { min, max ->
                        workingFilters = workingFilters.copy(priceMin = min, priceMax = max)
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            val normalized = workingFilters.normalizeWithDependencies(sheetDependencyContext)
                            filters = normalized.state
                            workingFilters = normalized.state
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
                    deliverableOnly = workingFilters.deliverableOnly,
                    options = purchaseFormatOptions(
                        runtimeFacets = runtimeDeliveryChannelFacets,
                        current = workingFilters.purchaseFormat,
                    ),
                    onSelect = { format ->
                        workingFilters = workingFilters.copy(purchaseFormat = format)
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            val normalized = workingFilters.normalizeWithDependencies(sheetDependencyContext)
                            filters = normalized.state
                            workingFilters = normalized.state
                            sheetScreen = null
                        }
                    },
                    onToggleDeliverableOnly = { enabled ->
                        workingFilters = workingFilters.copy(deliverableOnly = enabled)
                        if (sheetTarget != FilterSheetTarget.Draft) {
                            val normalized = workingFilters.normalizeWithDependencies(sheetDependencyContext)
                            filters = normalized.state
                            workingFilters = normalized.state
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
                    onReset = {
                        workingFilters = workingFilters.clearConditionSelection()
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.SellerTrust -> SellerTrustSheet(
                    currentPreset = workingFilters.sellerTrustPreset,
                    selectedSignals = workingFilters.sellerTrustSignals,
                    options = sellerTrustOptions(
                        stats = sellerTrustStats,
                        selectedSignals = workingFilters.sellerTrustSignals,
                    ),
                    onSelectPreset = { preset ->
                        workingFilters = workingFilters.withSellerTrustPreset(
                            preset = preset,
                            availableSignals = sheetDependencyContext.availableSellerTrustSignals,
                        )
                    },
                    onToggleSignal = { signal, enabled ->
                        workingFilters = workingFilters.toggleSellerTrustSignal(
                            signal = signal,
                            enabled = enabled,
                            availableSignals = sheetDependencyContext.availableSellerTrustSignals,
                        )
                    },
                    onReset = {
                        workingFilters = workingFilters.withSellerTrustPreset(
                            preset = SellerTrustPreset.Any,
                            availableSignals = sheetDependencyContext.availableSellerTrustSignals,
                        )
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = workingFilters.normalizeWithDependencies(sheetDependencyContext).state
                            sheetScreen = null
                        }
                    },
                )
                ResultsSheet.TypedAttribute -> {
                    val facetKey = activeTypedFacetKey
                    val facetFilter = facetUiFilters.firstOrNull { it.runtimeKey == facetKey }
                    val definition = facetDefinitionsForUi.firstOrNull { definition ->
                        definition.normalizedRuntimeKey() == facetKey
                    }
                    if (facetFilter == null || facetKey.isNullOrBlank()) {
                        sheetScreen = ResultsSheet.Filters
                    } else {
                        TypedAttributeFilterSheet(
                            runtimeKey = facetKey,
                            title = facetFilter.title,
                            valueType = definition?.valueType,
                            selectionMode = definition?.selectionMode,
                            uiWidget = definition?.uiWidget,
                            runtimeValues = runtimeAttributeFacets[facetKey].orEmpty(),
                            availableSeedValues = uiTypedFacetUniverse
                                .entriesByRuntimeKey[facetKey]
                                ?.seedAvailableValues
                                .orEmpty(),
                            knownValues = uiTypedFacetUniverse
                                .entriesByRuntimeKey[facetKey]
                                ?.knownValues
                                .orEmpty(),
                            draft = uiFacetResolution.effectiveDraftForRuntimeKey(facetKey),
                            onApply = { draft ->
                                workingFilters = workingFilters.withTypedAttributeDraft(
                                    runtimeKey = facetKey,
                                    draft = draft,
                                )
                                if (sheetTarget == FilterSheetTarget.Draft) {
                                    sheetScreen = ResultsSheet.Filters
                                } else {
                                    val normalized = workingFilters.normalizeWithDependencies(sheetDependencyContext)
                                    filters = normalized.state
                                    workingFilters = normalized.state
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
                        val keepGeoCenter = location != null &&
                            workingFilters.location
                                ?.trim()
                                ?.equals(location.trim(), ignoreCase = true) == true &&
                            workingFilters.centerLat != null &&
                            workingFilters.centerLon != null
                        workingFilters = workingFilters.copy(
                            location = location,
                            radiusKm = radius,
                            centerLat = if (keepGeoCenter) workingFilters.centerLat else null,
                            centerLon = if (keepGeoCenter) workingFilters.centerLon else null,
                        )
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            val normalized = workingFilters.normalizeWithDependencies(sheetDependencyContext)
                            filters = normalized.state
                            workingFilters = normalized.state
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

private fun ResultsPayload.onCreate(
    onOpenCreate: (() -> Unit)?,
) {
    FlowMetrics.markCreateAction("text", "create")
    onOpenCreate?.invoke()
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
private fun FiltersHandleOverlay(
    activeCount: Int,
    dock: FiltersHandleDock,
    currentSort: OfferSort,
    currentViewMode: ResultsViewMode,
    onDockChange: (FiltersHandleDock) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val sideHandleWidth = 46.dp
    val sideHandleHeight = 94.dp
    val maxTopBottomHandleWidth = (configuration.screenWidthDp.dp - 8.dp).coerceAtLeast(220.dp)
    val topBottomHandleWidth = (configuration.screenWidthDp.dp - 36.dp).coerceIn(
        minimumValue = 220.dp,
        maximumValue = maxTopBottomHandleWidth,
    )
    val topBottomHandleHeight = 48.dp
    val sideMargin = 8.dp
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val sideMarginPx = with(density) { sideMargin.toPx() }
    val topDockYPx = with(density) { FILTERS_TOP_DOCK_Y.toPx() }
    val bottomDockMarginPx = with(density) { FILTERS_BOTTOM_DOCK_MARGIN.toPx() }
    val sideTriggerPx = with(density) { FILTERS_EDGE_GESTURE_TRIGGER.toPx() }
    val sideZonePx = with(density) { FILTERS_EDGE_GESTURE_ZONE.toPx() }

    fun handleSize(targetDock: FiltersHandleDock): Pair<Float, Float> = when (targetDock) {
        FiltersHandleDock.Left,
        FiltersHandleDock.Right,
        -> with(density) { sideHandleWidth.toPx() to sideHandleHeight.toPx() }

        FiltersHandleDock.Top,
        FiltersHandleDock.Bottom,
        -> with(density) { topBottomHandleWidth.toPx() to topBottomHandleHeight.toPx() }
    }

    fun anchor(targetDock: FiltersHandleDock): Offset {
        val (handleWidthPx, handleHeightPx) = handleSize(targetDock)
        val safeMaxX = (screenWidthPx - handleWidthPx).coerceAtLeast(0f)
        val safeMaxY = (screenHeightPx - handleHeightPx).coerceAtLeast(0f)
        return when (targetDock) {
            FiltersHandleDock.Left -> Offset(
                x = sideMarginPx,
                y = ((screenHeightPx - handleHeightPx) / 2f).coerceIn(0f, safeMaxY),
            )

            FiltersHandleDock.Right -> Offset(
                x = (screenWidthPx - handleWidthPx - sideMarginPx).coerceIn(0f, safeMaxX),
                y = ((screenHeightPx - handleHeightPx) / 2f).coerceIn(0f, safeMaxY),
            )

            FiltersHandleDock.Top -> Offset(
                x = ((screenWidthPx - handleWidthPx) / 2f).coerceIn(0f, safeMaxX),
                y = topDockYPx.coerceIn(0f, safeMaxY),
            )

            FiltersHandleDock.Bottom -> Offset(
                x = ((screenWidthPx - handleWidthPx) / 2f).coerceIn(0f, safeMaxX),
                y = (screenHeightPx - handleHeightPx - bottomDockMarginPx).coerceIn(0f, safeMaxY),
            )
        }
    }

    fun nearestDock(center: Offset): FiltersHandleDock {
        val distances = mapOf(
            FiltersHandleDock.Top to center.y,
            FiltersHandleDock.Left to center.x,
            FiltersHandleDock.Right to (screenWidthPx - center.x),
            FiltersHandleDock.Bottom to (screenHeightPx - center.y),
        )
        return distances.minByOrNull { (_, distance) -> distance }?.key ?: dock
    }

    val anchorOffset = remember(dock, screenWidthPx, screenHeightPx) { anchor(dock) }
    val (handleWidthPx, handleHeightPx) = handleSize(dock)
    val handleWidthDp = with(density) { handleWidthPx.toDp() }
    val handleHeightDp = with(density) { handleHeightPx.toDp() }
    var draggedOffset by remember { mutableStateOf<Offset?>(null) }
    var showDockTargets by remember { mutableStateOf(false) }
    var previewDock by remember { mutableStateOf<FiltersHandleDock?>(null) }
    var sidePullDistancePx by remember(dock) { mutableStateOf(0f) }
    var sidePullEnabled by remember(dock) { mutableStateOf(false) }
    val sidePullOffset = when (dock) {
        FiltersHandleDock.Left -> sidePullDistancePx
        FiltersHandleDock.Right -> -sidePullDistancePx
        else -> 0f
    }
    val visualOffset = (draggedOffset ?: anchorOffset).let { base ->
        Offset(
            x = base.x + sidePullOffset,
            y = base.y,
        )
    }
    val isSideDock = dock == FiltersHandleDock.Left || dock == FiltersHandleDock.Right
    val isTopDock = dock == FiltersHandleDock.Top
    val badgeValue = activeCount.coerceAtLeast(0)
    val badgeText = if (badgeValue > 99) "99+" else badgeValue.toString()
    val filtersChipColor = Color(0xFF121212)
    val dockHint = previewDock ?: dock
    val surfaceColor = when (dock) {
        FiltersHandleDock.Top -> Color.Transparent
        else -> Color.White
    }
    val surfaceShape = when (dock) {
        FiltersHandleDock.Left,
        FiltersHandleDock.Right,
        -> RoundedCornerShape(20.dp)

        FiltersHandleDock.Top -> RoundedCornerShape(0.dp)
        FiltersHandleDock.Bottom -> RoundedCornerShape(18.dp)
    }
    val surfaceShadow = when (dock) {
        FiltersHandleDock.Top -> 0.dp
        FiltersHandleDock.Bottom -> 6.dp
        else -> 8.dp
    }
    val surfaceBorder = when (dock) {
        FiltersHandleDock.Top -> null
        else -> BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        )
    }

    Box(modifier = modifier) {
        if (showDockTargets) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f)),
            )
            DockPlacementHint(
                label = "Сверху",
                active = dockHint == FiltersHandleDock.Top,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = FILTERS_TOP_DOCK_Y - 8.dp),
            )
            DockPlacementHint(
                label = "Слева",
                active = dockHint == FiltersHandleDock.Left,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
            )
            DockPlacementHint(
                label = "Справа",
                active = dockHint == FiltersHandleDock.Right,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            )
            DockPlacementHint(
                label = "Снизу",
                active = dockHint == FiltersHandleDock.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FILTERS_BOTTOM_DOCK_MARGIN - 8.dp),
            )
        }

        Surface(
            shape = surfaceShape,
            color = surfaceColor,
            tonalElevation = 0.dp,
            shadowElevation = surfaceShadow,
            border = surfaceBorder,
            modifier = Modifier
                .offset {
                    IntOffset(
                        visualOffset.x.roundToInt(),
                        visualOffset.y.roundToInt(),
                    )
                }
                .size(width = handleWidthDp, height = handleHeightDp)
                .pointerInput(dock, screenWidthPx, screenHeightPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            sidePullDistancePx = 0f
                            sidePullEnabled = false
                            draggedOffset = anchorOffset
                            previewDock = dock
                            showDockTargets = true
                        },
                        onDragEnd = {
                            val releasedOffset = draggedOffset ?: anchorOffset
                            val center = Offset(
                                x = releasedOffset.x + handleWidthPx / 2f,
                                y = releasedOffset.y + handleHeightPx / 2f,
                            )
                            onDockChange(previewDock ?: nearestDock(center))
                            draggedOffset = null
                            showDockTargets = false
                            previewDock = null
                        },
                        onDragCancel = {
                            draggedOffset = null
                            showDockTargets = false
                            previewDock = null
                        },
                        onDrag = { change, dragAmount ->
                            val currentOffset = draggedOffset ?: anchorOffset
                            val maxX = (screenWidthPx - handleWidthPx).coerceAtLeast(0f)
                            val maxY = (screenHeightPx - handleHeightPx).coerceAtLeast(0f)
                            val nextOffset = Offset(
                                x = (currentOffset.x + dragAmount.x).coerceIn(0f, maxX),
                                y = (currentOffset.y + dragAmount.y).coerceIn(0f, maxY),
                            )
                            draggedOffset = nextOffset
                            val center = Offset(
                                x = nextOffset.x + handleWidthPx / 2f,
                                y = nextOffset.y + handleHeightPx / 2f,
                            )
                            previewDock = nearestDock(center)
                            change.consume()
                        },
                    )
                }
                .then(
                    if (isSideDock) {
                        Modifier.pointerInput(dock, sideTriggerPx, sideZonePx, handleWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    if (draggedOffset != null) {
                                        sidePullEnabled = false
                                        return@detectHorizontalDragGestures
                                    }
                                    sidePullEnabled = when (dock) {
                                        FiltersHandleDock.Left -> {
                                            offset.x >= (handleWidthPx - sideZonePx).coerceAtLeast(0f)
                                        }

                                        FiltersHandleDock.Right -> {
                                            offset.x <= sideZonePx
                                        }

                                        else -> false
                                    }
                                    if (sidePullEnabled) sidePullDistancePx = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!sidePullEnabled || draggedOffset != null) return@detectHorizontalDragGestures
                                    sidePullDistancePx = when (dock) {
                                        FiltersHandleDock.Left -> {
                                            (sidePullDistancePx + dragAmount)
                                                .coerceIn(0f, sideTriggerPx * 1.35f)
                                        }

                                        FiltersHandleDock.Right -> {
                                            (sidePullDistancePx - dragAmount)
                                                .coerceIn(0f, sideTriggerPx * 1.35f)
                                        }

                                        else -> 0f
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (sidePullEnabled && sidePullDistancePx >= sideTriggerPx) {
                                        onOpenFilters()
                                    }
                                    sidePullDistancePx = 0f
                                    sidePullEnabled = false
                                },
                                onDragCancel = {
                                    sidePullDistancePx = 0f
                                    sidePullEnabled = false
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (isSideDock) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onOpenFilters,
                        )
                    } else {
                        Modifier
                    },
                )
                .testTag("results_filters_handle"),
        ) {
            if (isSideDock) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Фильтры",
                        tint = filtersChipColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = filtersChipColor,
                        tonalElevation = 0.dp,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isTopDock) 4.dp else 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1.1f)
                            .heightIn(min = 36.dp)
                            .clickable(onClick = onOpenFilters)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Фильтры",
                            tint = filtersChipColor,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = "Фильтры",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = filtersChipColor,
                            tonalElevation = 0.dp,
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .clickable(onClick = onOpenSort)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Сортировка",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = sortLabel(currentSort),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 36.dp)
                            .clickable(onClick = onOpenView)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = viewModeIcon(currentViewMode),
                            contentDescription = "Показывать",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = viewModeLabel(currentViewMode),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockPlacementHint(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            Color(0xFFECECEC)
        } else {
            Color.White.copy(alpha = 0.92f)
        },
        border = BorderStroke(
            1.dp,
            if (active) {
                Color(0xFF1A1A1A).copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (active) 5.dp else 0.dp,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (active) {
                Color(0xFF121212)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ResultsControlsRow(
    activeFiltersCount: Int,
    sort: OfferSort,
    viewMode: ResultsViewMode,
    onFilters: () -> Unit,
    onSort: () -> Unit,
    onToggleView: () -> Unit,
) {
    val filtersLabel = if (activeFiltersCount > 0) "Фильтры ($activeFiltersCount)" else "Фильтры"
    val viewLabel = "Показывать"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChipButton(
            label = filtersLabel,
            onClick = onFilters,
            leadingIcon = Icons.Outlined.Tune,
            testTag = "results_control_filters",
        )
        FilterChipButton(
            label = sortControlLabel(sort),
            onClick = onSort,
            testTag = "results_control_sort",
        )
        Spacer(modifier = Modifier.weight(1f))
        FilterChipButton(
            label = viewLabel,
            onClick = onToggleView,
            leadingIcon = viewModeIcon(viewMode),
            testTag = "results_control_view",
        )
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val neutralColor = Color(0xFF4A4D52)
    Row(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = neutralColor,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = neutralColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = neutralColor,
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
            modifier = Modifier.heightIn(min = 46.dp),
            border = BorderStroke(1.dp, Color(0xFFD0D5DD)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF202124),
            ),
        ) {
            Text(
                "Сбросить",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
            ),
            modifier = Modifier.heightIn(min = 46.dp),
        ) {
            Text(
                "Готово",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ResultsInlineQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onFocus: (() -> Unit)? = null,
    placeholder: String = "",
    photoUris: List<String> = emptyList(),
    primaryChips: List<String> = emptyList(),
) {
    if (photoUris.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onFocus != null) { onFocus?.invoke() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(
                            items = photoUris,
                            key = { uri -> uri },
                        ) { uri ->
                            ResultsInlinePhotoThumbnail(uri = uri)
                        }
                    }
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Очистить изображения",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (primaryChips.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        items = primaryChips,
                        key = { label -> label },
                    ) { label ->
                        ResultsFilterTokenChip(
                            text = label,
                            onClick = null,
                            highlighted = true,
                        )
                    }
                }
            }
        }
    } else {
        ResultsEditableTextQueryField(
            value = value,
            onValueChange = onValueChange,
            onSubmit = onSubmit,
            onClear = onClear,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun ResultsEditableTextQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Поиск",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal,
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank() && placeholder.isNotBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Очистить запрос",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsInlinePhotoThumbnail(
    uri: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(34.dp),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Фото для поиска",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ResultsPhotoModeRail(
    querySummary: String,
    categoryLabel: String?,
    visualContext: ResultsVisualContext,
    visualCandidates: List<ResultsVisualCandidateUi>,
    onSelectCandidate: (VisualSearchBoundCandidate) -> Unit,
    onEditQuery: (() -> Unit)?,
) {
    val anchorStatusLabel = visualAnchorStatusLabel(visualContext)
    val binderStatusMessage = visualBinderStatusMessage(
        binderStatus = visualContext.binderStatus,
        routeKind = visualContext.routeKind,
        exactRoute = visualContext.exactRoute,
        qualityApproved = visualContext.qualityApproved,
    )
    val anchorChips = buildList {
        add(
            ResultsPhotoModeChipUi(
                label = visualRouteLabel(visualContext),
                kind = ResultsPhotoModeChipKind.Route,
            ),
        )
        categoryLabel?.takeIf { it.isNotBlank() }?.let { label ->
            add(
                ResultsPhotoModeChipUi(
                    label = label,
                    kind = ResultsPhotoModeChipKind.Category,
                ),
            )
        }
        visualContext.chips.forEach { chip ->
            if (chip.label.isBlank()) return@forEach
            if (chip.kind == VisualSearchChipKind.CATEGORY && categoryLabel?.equals(chip.label, ignoreCase = true) == true) {
                return@forEach
            }
            add(
                ResultsPhotoModeChipUi(
                    label = chip.label,
                    kind = chip.toResultsPhotoModeChipKind(),
                ),
            )
        }
    }.distinctBy { chip -> "${chip.kind.name}|${chip.label}" }
    val modelCandidateChips = visualContext.modelCandidates
        .mapNotNull { candidate ->
            candidate.text
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { text ->
                    ResultsPhotoModeChipUi(
                        label = candidate.confidence
                            ?.coerceIn(0f, 1f)
                            ?.takeIf { it >= 0.45f }
                            ?.let { confidence -> "$text · ${((confidence * 100).roundToInt())}%" }
                            ?: text,
                        kind = ResultsPhotoModeChipKind.ModelCandidate,
                    )
                }
        }
        .distinctBy { chip -> chip.label.lowercase(Locale.ROOT) }
        .take(2)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_photo_mode_rail"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Ориентиры по фото",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = visualContext.previewTitle
                            ?.trim()
                            .takeUnless { it.isNullOrBlank() || isGenericResultsPhotoPreviewTitle(it) }
                            ?: querySummary.takeIf { it.isNotBlank() && !isGenericResultsPhotoPreviewTitle(it) }
                            ?: "Поиск по фото",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    visualContext.previewSubtitle
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    Text(
                        text = binderStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = anchorStatusLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (anchorChips.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        items = anchorChips,
                        key = { chip -> "${chip.kind.name}:${chip.label}" },
                    ) { chip ->
                        ResultsPhotoModeChip(chip = chip)
                    }
                }
            }

            if (modelCandidateChips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Возможные модели",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                            items = modelCandidateChips,
                            key = { chip -> chip.label },
                        ) { chip ->
                            ResultsPhotoModeChip(chip = chip)
                        }
                    }
                }
            }

            if (visualCandidates.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Вероятные варианты",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                        items = visualCandidates,
                            key = { candidate -> candidate.stableKey },
                        ) { candidate ->
                            ResultsFilterTokenChip(
                                text = "${candidate.label} · ${candidate.supportingLabel}",
                                onClick = { onSelectCandidate(candidate.candidate) },
                                highlighted = candidate.highlighted,
                            )
                        }
                    }
                }
            }

            if (onEditQuery != null) {
                TextButton(
                    onClick = onEditQuery,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Уточнить запрос")
                }
            }
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
    SystemNoticeCard(
        body = message,
        tone = SystemNoticeTone.Error,
        compact = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_catalog_error_banner"),
        actionLabel = stringResource(R.string.state_error_retry),
        onAction = onRetry,
        actionModifier = Modifier.testTag("results_catalog_error_retry"),
    )
}

@Composable
internal fun ResultsStaleResultsBanner(
    message: String,
    updatedAtLabel: String? = null,
    onRetry: () -> Unit,
) {
    SystemNoticeCard(
        body = message,
        tone = SystemNoticeTone.Warning,
        compact = true,
        footer = updatedAtLabel,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_stale_banner"),
        actionLabel = stringResource(R.string.state_error_retry),
        onAction = onRetry,
        actionModifier = Modifier.testTag("results_stale_banner_retry"),
    )
}

@Composable
internal fun ResultsFacetResolutionBanner(
    notices: List<ResultsTypedFacetNotice>,
    autoApplied: Map<String, TypedAttributeFilterDraft>,
    typedFacetTitlesByRuntimeKey: Map<String, String>,
    suppressedAutoAppliedRuntimeKeys: Set<String> = emptySet(),
    scopeMessage: String? = null,
) {
    val lines = buildList {
        notices.forEach { notice ->
            val title = typedFacetTitlesByRuntimeKey[notice.runtimeKey]
                ?: notice.title
            val suffix = if (notice.availableValues.isNotEmpty()) {
                "Показываем все доступные значения."
            } else {
                "Снимите часть других фильтров, чтобы увидеть варианты."
            }
            add("$title: «${notice.requestedValue}» не найдено. $suffix")
        }
        autoApplied.entries
            .filterNot { (runtimeKey, _) -> runtimeKey in suppressedAutoAppliedRuntimeKeys }
            .sortedBy { (runtimeKey, _) -> runtimeKey }
            .forEach { (runtimeKey, draft) ->
                val value = draft.singleExactValueOrNull() ?: return@forEach
                val title = typedFacetTitlesByRuntimeKey[runtimeKey]
                    ?: runtimeKey
                        .replace('_', ' ')
                        .replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                }
                add("$title: $value определено автоматически.")
            }
        scopeMessage
            ?.trim()
            ?.takeIf { message -> message.isNotEmpty() }
            ?.let(::add)
    }
    if (lines.isEmpty()) return

    val tone = if (notices.isNotEmpty()) SystemNoticeTone.Warning else SystemNoticeTone.Info
    SystemNoticeCard(
        title = when {
            notices.isNotEmpty() -> "Уточнили фильтры"
            autoApplied.isNotEmpty() -> "Фильтры определены автоматически"
            else -> "Показ значений ограничен"
        },
        body = lines.joinToString(separator = "\n"),
        tone = tone,
        compact = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_facet_resolution_banner"),
    )
}

private enum class ResultsPhotoModeChipKind {
    Intent,
    Route,
    Category,
    ItemType,
    Brand,
    Model,
    ModelCandidate,
    Attribute,
    Barcode,
}

private data class ResultsPhotoModeChipUi(
    val label: String,
    val kind: ResultsPhotoModeChipKind,
)

private data class ResultsVisualCandidateUi(
    val stableKey: String,
    val rank: Int,
    val label: String,
    val supportingLabel: String,
    val highlighted: Boolean,
    val candidate: VisualSearchBoundCandidate,
)

@Composable
private fun ResultsPhotoModeChip(
    chip: ResultsPhotoModeChipUi,
) {
    val (containerColor, contentColor, borderColor) = when (chip.kind) {
        ResultsPhotoModeChipKind.Intent -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
        )
        ResultsPhotoModeChipKind.Route -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
        )
        ResultsPhotoModeChipKind.Category -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        )
        ResultsPhotoModeChipKind.ItemType -> Triple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
        )
        ResultsPhotoModeChipKind.Brand,
        ResultsPhotoModeChipKind.Model,
        ResultsPhotoModeChipKind.ModelCandidate,
            -> Triple(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            )
        ResultsPhotoModeChipKind.Attribute,
        ResultsPhotoModeChipKind.Barcode,
            -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
            )
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = chip.label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AppliedFilterChip(
    chip: AppliedFilterChipUi,
    onTap: () -> Unit,
    onClear: () -> Unit,
) {
    val textValue = if (chip.showLabel && chip.label.isNotBlank()) {
        "${chip.label}: ${chip.value}"
    } else {
        chip.value
    }
    ResultsFilterTokenChip(
        text = textValue,
        onClick = onTap,
        onRemove = if (chip.removable) onClear else null,
        leadingIcon = chip.icon,
    )
}

@Composable
private fun ResultsFilterTokenChip(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onRemove: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
) {
    val backgroundColor = if (highlighted) Color(0xFF3C4043) else Color(0xFFF1F3F4)
    val contentColor = if (highlighted) Color.White else Color(0xFF202124)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 8.dp,
                end = if (onRemove != null) 7.dp else 8.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) Color.White else Color(0xFF5F6368),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sizeIn(maxWidth = 240.dp),
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Удалить фильтр $text",
                        tint = if (highlighted) Color.White else Color(0xFF5F6368),
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
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
                Text(resetLabel, color = Color(0xFF4A4D52))
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun FilterHubRow(
    title: String,
    summary: String?,
    icon: ImageVector,
    active: Boolean,
    onClick: (() -> Unit)?,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
    inlineValues: List<FilterHubInlineValue> = emptyList(),
    onInlineValueSelect: ((String) -> Unit)? = null,
    summaryStyle: FilterHubSummaryStyle = FilterHubSummaryStyle.Chip,
    showChevron: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val rowClickable = enabled && onClick != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowClickable) Modifier.clickable(onClick = { onClick?.invoke() }) else Modifier),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!summary.isNullOrBlank()) {
                FilterHubSummaryValue(
                    text = summary,
                    highlighted = active,
                    enabled = enabled,
                    summaryStyle = summaryStyle,
                    onClear = onClear.takeIf { active && enabled },
                    modifier = Modifier.sizeIn(maxWidth = 220.dp),
                )
            }
            if (rowClickable && showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (inlineValues.isNotEmpty() && onInlineValueSelect != null) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                items(
                    items = inlineValues,
                    key = { value -> value.key },
                ) { value ->
                    CategoryChip(
                        text = value.label,
                        onClick = { onInlineValueSelect(value.value) },
                        highlighted = value.selected,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun FilterHubSummaryValue(
    text: String,
    highlighted: Boolean,
    enabled: Boolean,
    summaryStyle: FilterHubSummaryStyle,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (summaryStyle) {
        FilterHubSummaryStyle.Chip -> FilterHubSummaryChip(
            text = text,
            highlighted = highlighted,
            onClear = onClear,
            modifier = modifier,
        )

        FilterHubSummaryStyle.PlainText -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onClear != null) {
                FilterHubValueClearButton(onClear = onClear)
            }
        }
    }
}

@Composable
private fun FilterHubViewModeRow(
    currentViewMode: ResultsViewMode,
    onViewModeChange: (ResultsViewMode) -> Unit,
) {
    val normalizedViewMode = currentViewMode.normalized()
    val colors = MaterialTheme.colorScheme
    val options = listOf(
        ResultsViewMode.List to "Список",
        ResultsViewMode.Grid to "Сетка",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Показывать",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { (mode, contentDescription) ->
                    val selected = normalizedViewMode == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFFF1F3F4) else Color.Transparent,
                        border = if (selected) {
                            null
                        } else {
                            BorderStroke(1.dp, Color(0xFFDADCE0))
                        },
                        modifier = Modifier
                            .size(width = 48.dp, height = 36.dp)
                            .clickable { onViewModeChange(mode) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FilterHubViewModeGlyph(
                                mode = mode,
                                selected = selected,
                                contentDescription = contentDescription,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun FilterHubSummaryChip(
    text: String,
    highlighted: Boolean = false,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) Color(0xFFF1F3F4) else Color.Transparent,
        border = if (highlighted) null else BorderStroke(1.dp, Color(0xFFDADCE0)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                top = 5.dp,
                end = if (onClear == null) 10.dp else 4.dp,
                bottom = 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = Color(0xFF202124),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onClear != null) {
                FilterHubValueClearButton(onClear = onClear)
            }
        }
    }
}

@Composable
private fun FilterHubValueClearButton(
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clickable(onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Сбросить значение",
            tint = Color(0xFF5F6368),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun FilterHubViewModeGlyph(
    mode: ResultsViewMode,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val tint = Color(0xFF202124)
    when (mode.normalized()) {
        ResultsViewMode.List -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(tint, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }

        ResultsViewMode.Grid -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(2) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(tint, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
        }

        ResultsViewMode.Compact,
        ResultsViewMode.Dense,
        -> FilterHubViewModeGlyph(
            mode = ResultsViewMode.List,
            selected = selected,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun FilterHubDisclosureRow(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (expanded) {
                Icons.Outlined.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Outlined.KeyboardArrowRight
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TypedFacetDebugRow(
    entry: TypedFacetDebugEntry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${entry.title} [${entry.runtimeKey}]",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = buildString {
                append("placement=")
                append(entry.placement.name.lowercase(Locale.ROOT))
                append(" · known=")
                append(entry.knownCount)
                append(" · seed=")
                append(entry.seedCount)
                append(" · available=")
                append(entry.availableCount)
                if (entry.explicitActive) append(" · explicit")
                if (entry.autoApplied) append(" · auto")
                if (entry.hasNotice) append(" · notice")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class FilterHubInlineValue(
    val key: String,
    val value: String,
    val label: String,
    val selected: Boolean,
)

private enum class FilterHubSummaryStyle {
    Chip,
    PlainText,
}

private data class FilterHubEntry(
    val key: String,
    val title: String,
    val summary: String?,
    val active: Boolean,
    val enabled: Boolean,
    val icon: ImageVector,
    val onClick: (() -> Unit)?,
    val onClear: (() -> Unit)? = null,
    val inlineValues: List<FilterHubInlineValue> = emptyList(),
    val onInlineValueSelect: ((String) -> Unit)? = null,
    val summaryStyle: FilterHubSummaryStyle = FilterHubSummaryStyle.Chip,
    val showChevron: Boolean = true,
)

private data class TypedFacetDebugEntry(
    val runtimeKey: String,
    val title: String,
    val knownCount: Int,
    val seedCount: Int,
    val availableCount: Int,
    val explicitActive: Boolean,
    val autoApplied: Boolean,
    val hasNotice: Boolean,
    val placement: ResultsTypedFacetPlacement,
    val reason: String,
)

@Composable
private fun sheetMaxHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp.dp * 0.9f).coerceAtLeast(320.dp)

@Composable
private fun FilterHubSideSheet(
    onDismissRequest: () -> Unit,
    dock: FiltersHandleDock,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dismissInteractionSource = remember { MutableInteractionSource() }
        val density = LocalDensity.current
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val isRightDock = dock == FiltersHandleDock.Right
            val sheetWidth = maxWidth * FILTERS_SIDE_SHEET_WIDTH_FRACTION
            val sheetWidthPx = with(density) { sheetWidth.toPx() }
            val closeTriggerPx = with(density) { 84.dp.toPx() }
            val closeZonePx = with(density) { 24.dp.toPx() }
            var dragOffsetPx by remember { mutableStateOf(0f) }
            var closeDragEnabled by remember { mutableStateOf(false) }
            val baseOffsetPx = 0f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = dismissInteractionSource,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )

            Surface(
                shape = if (isRightDock) {
                    RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
                } else {
                    RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
                },
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(FILTERS_SIDE_SHEET_WIDTH_FRACTION)
                    .align(if (isRightDock) Alignment.CenterEnd else Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            x = (baseOffsetPx + dragOffsetPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .pointerInput(dock, sheetWidthPx, closeTriggerPx, closeZonePx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                closeDragEnabled = if (isRightDock) {
                                    offset.x >= (sheetWidthPx - closeZonePx).coerceAtLeast(0f)
                                } else {
                                    offset.x <= closeZonePx
                                }
                                if (closeDragEnabled) dragOffsetPx = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!closeDragEnabled) return@detectHorizontalDragGestures
                                dragOffsetPx = when (dock) {
                                    FiltersHandleDock.Left -> (dragOffsetPx + dragAmount).coerceAtMost(0f)
                                    FiltersHandleDock.Right -> (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                                    else -> 0f
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                val shouldClose = when (dock) {
                                    FiltersHandleDock.Left -> abs(dragOffsetPx) >= closeTriggerPx
                                    FiltersHandleDock.Right -> dragOffsetPx >= closeTriggerPx
                                    else -> false
                                }
                                if (shouldClose) onDismissRequest()
                                dragOffsetPx = 0f
                                closeDragEnabled = false
                            },
                            onDragCancel = {
                                dragOffsetPx = 0f
                                closeDragEnabled = false
                            },
                        )
                    }
                    .testTag("results_sheet_filters_side"),
            ) {
                content()
            }
        }
    }
}

private fun FilterState.selectedInlineTypedValue(facetKey: String): String? {
    val normalizedKey = facetKey.trim().lowercase()
    if (normalizedKey.isBlank()) return null
    val draft = typedAttributeFilters[normalizedKey]
        ?: typedAttributeFilters.entries.firstOrNull { (key, _) ->
            key.trim().equals(normalizedKey, ignoreCase = true)
        }?.value
        ?: return null
    return when (draft.op) {
        TypedAttributeOperator.EQ -> draft.value.trim().takeIf { value -> value.isNotBlank() }
        TypedAttributeOperator.IN -> {
            draft.valuesCsv
                .split(',')
                .map { item -> item.trim() }
                .filter { item -> item.isNotBlank() }
                .singleOrNull()
        }
        else -> null
    }
}

private fun buildInlineTypedValueChips(
    facetKey: String,
    filters: FilterState,
    runtimeValues: List<ValueFacet>,
    availableSeedValues: List<String> = emptyList(),
): List<FilterHubInlineValue> {
    val selectedValue = filters.selectedInlineTypedValue(facetKey)
    if (!selectedValue.isNullOrBlank()) {
        val normalized = selectedValue.trim()
        return listOf(
            FilterHubInlineValue(
                key = "${facetKey.lowercase()}::$normalized",
                value = normalized,
                label = normalized,
                selected = true,
            ),
        )
    }

    val deduplicatedValues = linkedMapOf<String, Pair<String, Int>>()
    runtimeValues
        .sortedWith(
            compareByDescending<ValueFacet> { facet -> facet.count }
                .thenBy { facet -> facet.name.lowercase(Locale.ROOT) },
        )
        .forEach { facet ->
            val label = facet.name.trim().ifBlank { facet.id.trim() }
            if (label.isBlank()) return@forEach
            val key = label.lowercase(Locale.ROOT)
            if (deduplicatedValues.containsKey(key)) return@forEach
            deduplicatedValues[key] = label to facet.count.coerceAtLeast(0)
        }

    if (deduplicatedValues.isEmpty()) {
        availableSeedValues.forEach { rawValue ->
            val value = rawValue.trim()
            if (value.isEmpty()) return@forEach
            val key = value.lowercase(Locale.ROOT)
            if (deduplicatedValues.containsKey(key)) return@forEach
            deduplicatedValues[key] = value to 0
        }
    }

    if (deduplicatedValues.isEmpty() || deduplicatedValues.size > FILTER_HUB_INLINE_VALUE_THRESHOLD) {
        return emptyList()
    }

    return deduplicatedValues.values.map { (value, count) ->
        FilterHubInlineValue(
            key = "${facetKey.lowercase()}::$value",
            value = value,
            label = if (count > 0) "$value ($count)" else value,
            selected = false,
        )
    }
}

@Composable
private fun FilterScopeNotice(
    message: String,
) {
    val compactMessage = remember(message) {
        val normalized = message.trim()
        if (normalized.contains("бренд", ignoreCase = true) ||
            normalized.contains("модел", ignoreCase = true)
        ) {
            "Значения зависят от выбранных брендов и моделей."
        } else {
            normalized
        }
    }
    if (compactMessage.isBlank()) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF3F6FA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF5F6368),
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = compactMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterHubSheet(
    filters: FilterState,
    facetFilters: List<ResultsFacetFilter>,
    runtimeAttributeFacets: Map<String, List<ValueFacet>>,
    typedFacetUniverse: ResultsTypedFacetUniverse,
    typedFacetResolution: ResultsFacetResolution,
    categoriesByCode: Map<String, Category>,
    currentViewMode: ResultsViewMode,
    showSortAndViewRows: Boolean,
    activeCount: Int,
    isDirty: Boolean,
    isApplyEnabled: Boolean,
    applyResultsCount: Int? = null,
    scopeMessage: String? = null,
    dependencyContext: FilterDependencyContext,
    priceBounds: PriceBounds?,
    conditionOptions: List<ConditionOptionItem>,
    onFiltersChange: (FilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenFacet: (ResultsFacetFilter) -> Unit,
    onOpenSellerTrust: () -> Unit,
    onInlineTypedValueSelect: (facetKey: String, value: String) -> Unit,
    onViewModeChange: (ResultsViewMode) -> Unit,
    onApply: () -> Unit,
) {
    val facetPresentationProfile = remember(filters.categoryCode) {
        CatalogFacetPresentationProfiles.resolve(filters.categoryCode)
    }
    val applyActionState = remember(isDirty, isApplyEnabled, applyResultsCount) {
        resolveFilterHubApplyActionState(
            isDirty = isDirty,
            isApplyEnabled = isApplyEnabled,
            resultsCount = applyResultsCount,
        )
    }
    val normalizedScopeMessage = remember(scopeMessage) {
        scopeMessage?.trim()?.takeIf { message -> message.isNotEmpty() }
    }
    val profileAdditionalTypedFacetKeys = facetPresentationProfile?.additionalTypedFacetKeys.orEmpty()
    val normalizedViewMode = currentViewMode.normalized()
    val priceFacetFilter = remember(facetFilters) {
        facetFilters.firstOrNull { facet -> facet.type == ResultsFacetFilterType.PriceRange }
    }
    val conditionFacetFilter = remember(facetFilters) {
        facetFilters.firstOrNull { facet -> facet.type == ResultsFacetFilterType.Condition }
    }
    fun clearHubValue(chipKey: String) {
        val result = filters.clearAppliedChipWithDependencies(
            chipKey = chipKey,
            context = dependencyContext,
        )
        onFiltersChange(result.state)
        if (result.invalidatedFilterIds.isNotEmpty()) {
            FlowMetrics.markEvent(
                "filters_dependency_invalidation",
                "screen=results origin=filter_hub_clear changed=$chipKey invalidated=${result.invalidatedFilterIds.joinToString(",")}",
            )
        }
    }
    val additionalEntries = mutableListOf<FilterHubEntry>()
    val typedFacetDebugEntries = mutableListOf<TypedFacetDebugEntry>()
    val entries = buildList {
        add(
            FilterHubEntry(
                key = "price",
                title = "Цена",
                summary = filters.priceSummary().takeIf { filters.priceMin != null || filters.priceMax != null } ?: "Любая",
                active = filters.priceMin != null || filters.priceMax != null,
                enabled = priceFacetFilter != null,
                icon = Icons.Outlined.Tune,
                onClick = priceFacetFilter?.let { facet -> { onOpenFacet(facet) } },
                onClear = if (filters.priceMin != null || filters.priceMax != null) {
                    { clearHubValue(AppliedChipKey.Price) }
                } else {
                    null
                },
                summaryStyle = if (filters.priceMin != null || filters.priceMax != null) {
                    FilterHubSummaryStyle.Chip
                } else {
                    FilterHubSummaryStyle.PlainText
                },
            ),
        )
        add(
            FilterHubEntry(
                key = "condition",
                title = "Состояние",
                summary = filters.conditionsSummary().takeIf { filters.conditions.isNotEmpty() } ?: "Все",
                active = filters.conditions.isNotEmpty(),
                enabled = conditionFacetFilter != null,
                icon = Icons.Outlined.RadioButtonUnchecked,
                onClick = conditionFacetFilter?.let { facet -> { onOpenFacet(facet) } },
                onClear = if (filters.conditions.isNotEmpty()) {
                    { clearHubValue(AppliedChipKey.Condition) }
                } else {
                    null
                },
                summaryStyle = if (filters.conditions.isNotEmpty()) {
                    FilterHubSummaryStyle.Chip
                } else {
                    FilterHubSummaryStyle.PlainText
                },
            ),
        )
        val categoryActive = !filters.categoryCode.isNullOrBlank()
        add(
            FilterHubEntry(
                key = "category",
                title = "Категория",
                summary = filters.categorySummary(categoriesByCode).takeIf { categoryActive } ?: "Все",
                active = categoryActive,
                enabled = true,
                icon = Icons.Outlined.AccountTree,
                onClick = onOpenCategory,
                onClear = if (categoryActive) {
                    { clearHubValue(AppliedChipKey.Category) }
                } else {
                    null
                },
                summaryStyle = if (categoryActive) FilterHubSummaryStyle.Chip else FilterHubSummaryStyle.PlainText,
            ),
        )
        if (showSortAndViewRows) {
            add(
                FilterHubEntry(
                    key = "sort",
                    title = "Сортировка",
                    summary = sortLabel(filters.sort),
                    active = filters.sort != OfferSort.RANK,
                    enabled = true,
                    icon = Icons.Outlined.Tune,
                    onClick = onOpenSort,
                    onClear = if (filters.sort != OfferSort.RANK) {
                        { onFiltersChange(filters.copy(sort = OfferSort.RANK)) }
                    } else {
                        null
                    },
                    summaryStyle = FilterHubSummaryStyle.PlainText,
                ),
            )
        }
        facetFilters
            .filterNot { facet ->
                facet.type == ResultsFacetFilterType.SellerTrust ||
                    facet.type == ResultsFacetFilterType.PriceRange ||
                    facet.type == ResultsFacetFilterType.Condition ||
                    facet.type == ResultsFacetFilterType.DeliveryChannel
            }
            .forEach { facetFilter ->
                if (facetFilter.type != ResultsFacetFilterType.TypedAttribute) {
                    val active = filters.isFacetActive(facetFilter)
                    val clearKey = when (facetFilter.type) {
                        ResultsFacetFilterType.Brand -> AppliedChipKey.Brand
                        else -> null
                    }
                    add(
                        FilterHubEntry(
                            key = facetFilter.runtimeKey.lowercase(),
                            title = facetFilter.title,
                            summary = filters.summaryForFacet(facetFilter).takeIf { active }
                                ?: filters.defaultHubSummaryForFacet(facetFilter),
                            active = active,
                            enabled = facetFilter.type != ResultsFacetFilterType.Unsupported,
                            icon = facetFilter.icon(),
                            onClick = { onOpenFacet(facetFilter) },
                            onClear = if (active && clearKey != null) {
                                { clearHubValue(clearKey) }
                            } else {
                                null
                            },
                            summaryStyle = if (active) {
                                FilterHubSummaryStyle.Chip
                            } else {
                                FilterHubSummaryStyle.PlainText
                            },
                        ),
                    )
                    return@forEach
                }

                val universeEntry = typedFacetUniverse.entriesByRuntimeKey[facetFilter.runtimeKey]
                val inlineValues = buildInlineTypedValueChips(
                    facetKey = facetFilter.runtimeKey,
                    filters = filters,
                    runtimeValues = runtimeAttributeFacets[facetFilter.runtimeKey].orEmpty(),
                    availableSeedValues = universeEntry?.seedAvailableValues.orEmpty(),
                )
                val typedNotice = typedFacetResolution.noticesByRuntimeKey[facetFilter.runtimeKey]
                val typedAutoApplied = typedFacetResolution.autoAppliedTypedAttributeFilters[facetFilter.runtimeKey]
                val explicitDraft = filters.typedAttributeFilters.draftForRuntimeKey(facetFilter.runtimeKey)
                val effectiveDraft = typedFacetResolution.effectiveDraftForRuntimeKey(facetFilter.runtimeKey)
                val queryBackedDraft = typedFacetResolution.queryBackedDraftForRuntimeKey(facetFilter.runtimeKey)
                val explicitActive = explicitDraft != null
                val effectiveActive = effectiveDraft != null
                val autoActive = typedAutoApplied != null
                val availableValues = resolveAvailableFacetValues(
                    runtimeKey = facetFilter.runtimeKey,
                    runtimeAttributeFacets = runtimeAttributeFacets,
                    universeEntry = universeEntry,
                )
                val presentationPolicy = resolveResultsTypedFacetPresentationPolicy(
                    runtimeKey = facetFilter.runtimeKey,
                    availableValueCount = availableValues.size,
                    inlineValueCount = inlineValues.size,
                    knownValueCount = universeEntry?.knownValues?.size ?: 0,
                    valueType = universeEntry?.valueType,
                    explicitActive = effectiveActive,
                    autoApplied = autoActive,
                    hasNotice = typedNotice != null,
                    hasBrandContext = filters.brands.isNotEmpty() ||
                        !filters.interpretedBrandSeed.isNullOrBlank(),
                    hiddenTypedFacetKeys = facetPresentationProfile?.hiddenTypedFacetKeys.orEmpty(),
                    mainTypedFacetKeys = facetPresentationProfile?.mainTypedFacetKeys.orEmpty(),
                    additionalTypedFacetKeys = facetPresentationProfile?.additionalTypedFacetKeys.orEmpty(),
                    liveOnlyTypedFacetKeys = facetPresentationProfile?.liveOnlyTypedFacetKeys.orEmpty(),
                    suppressedAutoAppliedTypedFacetKeys = facetPresentationProfile
                        ?.suppressedAutoAppliedTypedFacetKeys
                        .orEmpty(),
                    requiresBrandContextTypedFacetKeys = facetPresentationProfile
                        ?.requiresBrandContextTypedFacetKeys
                        .orEmpty(),
                )
                val entry = FilterHubEntry(
                    key = facetFilter.runtimeKey.lowercase(),
                    title = facetFilter.title,
                    summary = resolveTypedFacetHubRowSummary(
                        explicitDraft = explicitDraft,
                        queryBackedDraft = queryBackedDraft,
                        autoAppliedDraft = typedAutoApplied,
                        defaultSummary = filters.defaultHubSummaryForFacet(facetFilter).orEmpty(),
                        availableValueCount = availableValues.size,
                        hasScopedContext = normalizedScopeMessage != null,
                    ),
                    active = effectiveActive || autoActive,
                    enabled = facetFilter.type != ResultsFacetFilterType.Unsupported,
                    icon = facetFilter.icon(),
                    onClick = { onOpenFacet(facetFilter) },
                    onClear = if (explicitActive) {
                        { clearHubValue("${AppliedChipKey.TypedPrefix}${facetFilter.runtimeKey}") }
                    } else {
                        null
                    },
                    inlineValues = emptyList(),
                    onInlineValueSelect = null,
                    summaryStyle = if (effectiveActive || autoActive) {
                        FilterHubSummaryStyle.Chip
                    } else {
                        FilterHubSummaryStyle.PlainText
                    },
                )
                typedFacetDebugEntries += TypedFacetDebugEntry(
                    runtimeKey = facetFilter.runtimeKey,
                    title = facetFilter.title,
                    knownCount = universeEntry?.knownValues?.size ?: 0,
                    seedCount = universeEntry?.seedAvailableValues?.size ?: 0,
                    availableCount = availableValues.size,
                    explicitActive = effectiveActive,
                    autoApplied = autoActive,
                    hasNotice = typedNotice != null,
                    placement = presentationPolicy.placement,
                    reason = presentationPolicy.reason,
                )
                when (presentationPolicy.placement) {
                    ResultsTypedFacetPlacement.Main -> add(entry)
                    ResultsTypedFacetPlacement.Additional -> additionalEntries += entry
                    ResultsTypedFacetPlacement.Hidden -> Unit
                }
            }
    }
    val hasAdditionalSection = profileAdditionalTypedFacetKeys.isNotEmpty() || additionalEntries.isNotEmpty()
    var showAdditionalAttributes by remember(hasAdditionalSection, additionalEntries.size) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 660.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SheetTopBar(
            title = "Фильтры",
            onClose = onDismiss,
        )
        normalizedScopeMessage?.let { message ->
            FilterScopeNotice(message = message)
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.key },
            ) { _, entry ->
                FilterHubRow(
                    title = entry.title,
                    summary = entry.summary,
                    icon = entry.icon,
                    active = entry.active,
                    onClick = entry.onClick,
                    onClear = entry.onClear,
                    enabled = entry.enabled,
                    inlineValues = entry.inlineValues,
                    onInlineValueSelect = entry.onInlineValueSelect,
                    summaryStyle = entry.summaryStyle,
                    showChevron = entry.showChevron,
                )
                if (entry.key == "condition") {
                    QuickDeliveryAvailabilityFilterCard(
                        deliverableOnly = filters.deliverableOnly,
                        onToggleDeliverableOnly = { enabled ->
                            onFiltersChange(filters.copy(deliverableOnly = enabled))
                        },
                    )
                }
                if (showSortAndViewRows && entry.key == "sort") {
                    FilterHubViewModeRow(
                        currentViewMode = normalizedViewMode,
                        onViewModeChange = onViewModeChange,
                    )
                }
            }
            if (hasAdditionalSection) {
                item("typed_facet_disclosure") {
                    FilterHubDisclosureRow(
                        title = if (showAdditionalAttributes) {
                            "Скрыть дополнительные"
                        } else {
                            "Дополнительно"
                        },
                        expanded = showAdditionalAttributes,
                        onClick = { showAdditionalAttributes = !showAdditionalAttributes },
                    )
                }
            }
            if (showAdditionalAttributes) {
                if (additionalEntries.isEmpty()) {
                    item("typed_facet_disclosure_empty") {
                        Text(
                            text = "Дополнительные характеристики пока недоступны.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(
                        items = additionalEntries,
                        key = { entry -> "extra_${entry.key}" },
                    ) { entry ->
                        FilterHubRow(
                            title = entry.title,
                            summary = entry.summary,
                            icon = entry.icon,
                            active = entry.active,
                            onClick = entry.onClick,
                            onClear = entry.onClear,
                            enabled = entry.enabled,
                            inlineValues = entry.inlineValues,
                            onInlineValueSelect = entry.onInlineValueSelect,
                            summaryStyle = entry.summaryStyle,
                            showChevron = entry.showChevron,
                        )
                    }
                }
            }
            if (entries.isEmpty() && !hasAdditionalSection) {
                item {
                    Text(
                        text = "Категорийные характеристики пока недоступны. Основные фильтры уже доступны выше.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onReset,
                enabled = activeCount > 0,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF4A4D52),
                    disabledContentColor = Color(0xFF9AA0A6),
                ),
                border = BorderStroke(
                    1.dp,
                    if (activeCount > 0) Color(0xFFD0D4D9) else Color(0xFFE5E7EA),
                ),
                modifier = Modifier
                    .weight(0.9f)
                    .heightIn(min = 48.dp)
                    .testTag("results_filters_reset"),
            ) {
                Text(
                    text = "Сбросить",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Button(
                onClick = onApply,
                enabled = applyActionState.enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE5E7EA),
                    disabledContentColor = Color(0xFF9AA0A6),
                ),
                modifier = Modifier
                    .weight(1.1f)
                    .heightIn(min = 48.dp)
                    .testTag("results_filters_apply"),
            ) {
                Text(
                    text = applyActionState.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ResultsFiltersPatternPreview(
    modifier: Modifier = Modifier,
) {
    val categoriesByCode = remember {
        linkedMapOf(
            "TECH" to com.example.shoppingassistant.domain.catalog.Category(
                code = "TECH",
                segment = com.example.shoppingassistant.domain.catalog.CategorySegment.TECH,
                title = localizedTextOf("ru" to "Техника", "en" to "Tech"),
            ),
            "TECH.PHONES" to com.example.shoppingassistant.domain.catalog.Category(
                code = "TECH.PHONES",
                segment = com.example.shoppingassistant.domain.catalog.CategorySegment.TECH,
                title = localizedTextOf("ru" to "Смартфоны", "en" to "Phones"),
                parentCode = "TECH",
            ),
        )
    }
    val sampleDefinitions = remember {
        listOf(
            FacetDefinition(
                facetKey = "model",
                title = localizedTextOf("ru" to "Модель"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH.PHONES"),
                attributeCode = "model",
                selectionMode = FacetSelectionMode.SINGLE,
                uiWidget = FacetUiWidget.RADIO_GROUP,
            ),
            FacetDefinition(
                facetKey = "memory",
                title = localizedTextOf("ru" to "Память"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH.PHONES"),
                attributeCode = "memory_gb",
                selectionMode = FacetSelectionMode.MULTI,
                uiWidget = FacetUiWidget.CHECKBOX_GROUP,
                ui = com.example.shoppingassistant.domain.facet.FacetUiConfig(order = 10),
            ),
            FacetDefinition(
                facetKey = "color",
                title = localizedTextOf("ru" to "Цвет"),
                valueType = FacetDataType.ENUM,
                appliesToCategoryCodes = listOf("TECH.PHONES"),
                attributeCode = "color",
                selectionMode = FacetSelectionMode.MULTI,
                uiWidget = FacetUiWidget.CHECKBOX_GROUP,
                ui = com.example.shoppingassistant.domain.facet.FacetUiConfig(order = 20),
            ),
            FacetDefinition(
                facetKey = "release_year",
                title = localizedTextOf("ru" to "Год выпуска"),
                valueType = FacetDataType.RANGE,
                appliesToCategoryCodes = listOf("TECH.PHONES"),
                attributeCode = "release_year",
                selectionMode = FacetSelectionMode.RANGE,
                uiWidget = FacetUiWidget.RANGE_INPUT,
                ui = com.example.shoppingassistant.domain.facet.FacetUiConfig(order = 30),
            ),
        )
    }
    val filters = remember {
        FilterState(
            queryText = "sony 5",
            categoryCode = "TECH.PHONES",
            categoryPath = listOf("Техника", "Смартфоны"),
            brands = setOf("Sony"),
            priceMin = 18_000,
            priceMax = 42_000,
            conditions = setOf(ConditionOption.New),
            typedAttributeFilters = linkedMapOf(
                "memory_gb" to TypedAttributeFilterDraft(
                    op = TypedAttributeOperator.IN,
                    valuesCsv = "256, 512",
                ),
                "color" to TypedAttributeFilterDraft(
                    op = TypedAttributeOperator.IN,
                    valuesCsv = "Черный, Синий",
                ),
            ),
            sellerTrustPreset = SellerTrustPreset.Balanced,
            sellerTrustSignals = SellerTrustPreset.Balanced.signals,
            location = "Москва",
            radiusKm = 25,
            deliverableOnly = true,
        )
    }
    val facetFilters = remember(sampleDefinitions) {
        buildFacetUiFilters(
            definitions = sampleDefinitions,
            categoryCode = "TECH.PHONES",
        )
    }
    val runtimeAttributeFacets = remember {
        linkedMapOf(
            "memory_gb" to listOf(
                ValueFacet(id = "128", name = "128", count = 42),
                ValueFacet(id = "256", name = "256", count = 31),
                ValueFacet(id = "512", name = "512", count = 12),
            ),
            "color" to listOf(
                ValueFacet(id = "black", name = "Черный", count = 24),
                ValueFacet(id = "blue", name = "Синий", count = 16),
                ValueFacet(id = "white", name = "Белый", count = 10),
            ),
        )
    }
    val typedFacetUniverse = remember {
        ResultsTypedFacetUniverse(
            entriesByRuntimeKey = linkedMapOf(
                "model" to ResultsTypedFacetUniverseEntry(
                    runtimeKey = "model",
                    title = "Модель",
                    valueType = FacetDataType.ENUM,
                    knownValues = listOf("Xperia 5 V", "Xperia 1 V", "Xperia 10 VI"),
                    seedAvailableValues = listOf("Xperia 5 V", "Xperia 1 V"),
                    seedAvailabilityKnown = true,
                    isClosedSet = true,
                ),
                "memory_gb" to ResultsTypedFacetUniverseEntry(
                    runtimeKey = "memory_gb",
                    title = "Память",
                    valueType = FacetDataType.ENUM,
                    knownValues = listOf("128", "256", "512"),
                    seedAvailableValues = listOf("128", "256", "512"),
                    seedAvailabilityKnown = true,
                    isClosedSet = true,
                ),
                "color" to ResultsTypedFacetUniverseEntry(
                    runtimeKey = "color",
                    title = "Цвет",
                    valueType = FacetDataType.ENUM,
                    knownValues = listOf("Черный", "Синий", "Белый", "Зеленый"),
                    seedAvailableValues = listOf("Черный", "Синий", "Белый"),
                    seedAvailabilityKnown = true,
                    isClosedSet = true,
                ),
                "release_year" to ResultsTypedFacetUniverseEntry(
                    runtimeKey = "release_year",
                    title = "Год выпуска",
                    valueType = FacetDataType.RANGE,
                ),
            ),
        )
    }
    val typedFacetResolution = remember {
        ResultsFacetResolution(
            noticesByRuntimeKey = linkedMapOf(
                "model" to ResultsTypedFacetNotice(
                    runtimeKey = "model",
                    title = "Модель",
                    requestedValue = "Xperia 5 Pro",
                    availableValues = listOf("Xperia 5 V", "Xperia 1 V"),
                ),
            ),
            autoAppliedTypedAttributeFilters = linkedMapOf(
                "release_year" to TypedAttributeFilterDraft(
                    op = TypedAttributeOperator.GTE,
                    value = "2023",
                ),
            ),
        )
    }
    val dependencyContext = remember(sampleDefinitions, categoriesByCode) {
        buildResultsDependencyContext(
            facetDefinitions = sampleDefinitions,
            categoriesByCode = categoriesByCode,
            availableSellerTrustSignals = SellerTrustSignal.entries.toSet(),
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FilterHubSheet(
            filters = filters,
            facetFilters = facetFilters,
            runtimeAttributeFacets = runtimeAttributeFacets,
            typedFacetUniverse = typedFacetUniverse,
            typedFacetResolution = typedFacetResolution,
            categoriesByCode = categoriesByCode,
            currentViewMode = ResultsViewMode.List,
            showSortAndViewRows = true,
            activeCount = filters.activeFilterCount(),
            isDirty = true,
            isApplyEnabled = true,
            applyResultsCount = 12,
            scopeMessage = "Значения в фильтрах показаны только для выбранных брендов и моделей.",
            dependencyContext = dependencyContext,
            priceBounds = PriceBounds(min = 10_000, max = 90_000),
            conditionOptions = listOf(
                ConditionOptionItem(condition = ConditionOption.New, count = 34),
                ConditionOptionItem(condition = ConditionOption.LikeNew, count = 12),
                ConditionOptionItem(condition = ConditionOption.Used, count = 56),
            ),
            onFiltersChange = {},
            onReset = {},
            onDismiss = {},
            onOpenSort = {},
            onOpenCategory = {},
            onOpenFacet = {},
            onOpenSellerTrust = {},
            onInlineTypedValueSelect = { _, _ -> },
            onViewModeChange = {},
            onApply = {},
        )

        TypedAttributeFilterSheet(
            runtimeKey = "memory_gb",
            title = "Память",
            valueType = FacetDataType.ENUM,
            selectionMode = FacetSelectionMode.MULTI,
            uiWidget = FacetUiWidget.CHECKBOX_GROUP,
            runtimeValues = runtimeAttributeFacets["memory_gb"].orEmpty(),
            availableSeedValues = listOf("128", "256", "512"),
            knownValues = listOf("128", "256", "512", "1024"),
            draft = TypedAttributeFilterDraft(
                op = TypedAttributeOperator.IN,
                valuesCsv = "256, 512",
            ),
            onApply = {},
            onBack = {},
        )
    }
}

@Composable
private fun QuickCommonFiltersSection(
    filters: FilterState,
    priceBounds: PriceBounds?,
    conditionOptions: List<ConditionOptionItem>,
    onFiltersChange: (FilterState) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        QuickPriceFilterCard(
            priceBounds = priceBounds,
            currentMin = filters.priceMin,
            currentMax = filters.priceMax,
            onPriceChange = { min, max ->
                onFiltersChange(filters.copy(priceMin = min, priceMax = max))
            },
        )
        QuickConditionFilterCard(
            selected = filters.conditions,
            options = conditionOptions,
            onReset = {
                onFiltersChange(
                    filters.copy(
                        conditions = emptySet(),
                        interpretedConditionSeed = null,
                    ),
                )
            },
            onToggle = { option ->
                onFiltersChange(filters.withCondition(option))
            },
        )
        QuickDeliveryAvailabilityFilterCard(
            deliverableOnly = filters.deliverableOnly,
            onToggleDeliverableOnly = { enabled ->
                onFiltersChange(filters.copy(deliverableOnly = enabled))
            },
        )
    }
}

@Composable
private fun QuickFacetCard(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun QuickLocationFilterCard(
    currentLocation: String?,
    radiusKm: Int?,
    isResolvingCurrentLocation: Boolean,
    onLocationChange: (String?) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    var locationText by remember(currentLocation) { mutableStateOf(currentLocation.orEmpty()) }
    val selectedRadius = radiusKm?.coerceIn(
        NEARBY_RADIUS_PRESETS.first(),
        NEARBY_RADIUS_PRESETS.last(),
    ) ?: DEFAULT_NEARBY_RADIUS_KM
    QuickFacetCard(
        title = "Где искать",
        actionLabel = if (locationText.isNotBlank()) "Очистить" else null,
        onAction = if (locationText.isNotBlank()) {
            {
                locationText = ""
                onLocationChange(null)
            }
        } else {
            null
        },
    ) {
        TextField(
            value = locationText,
            onValueChange = {
                locationText = it
                onLocationChange(it)
            },
            singleLine = true,
            placeholder = { Text("Город, район или страна") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onUseCurrentLocation,
            enabled = !isResolvingCurrentLocation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isResolvingCurrentLocation) "Определяем местоположение..." else "Использовать моё местоположение")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NEARBY_RADIUS_PRESETS) { radius ->
                CategoryChip(
                    text = "$radius км",
                    onClick = { onRadiusChange(radius) },
                    highlighted = selectedRadius == radius,
                )
            }
        }
    }
}

@Composable
private fun QuickPriceFilterCard(
    priceBounds: PriceBounds?,
    currentMin: Int?,
    currentMax: Int?,
    onPriceChange: (Int?, Int?) -> Unit,
) {
    val bounds = remember(priceBounds, currentMin, currentMax) {
        resolvePriceBounds(
            priceBounds = priceBounds,
            currentMin = currentMin,
            currentMax = currentMax,
        )
    }
    var minText by remember(currentMin, bounds.max) {
        mutableStateOf(currentMin?.coerceIn(bounds.min, bounds.max)?.toString().orEmpty())
    }
    var maxText by remember(currentMax, bounds.max) {
        mutableStateOf(currentMax?.coerceIn(bounds.min, bounds.max)?.toString().orEmpty())
    }
    val minValue = minText.toIntOrNull()
    val maxValue = maxText.toIntOrNull()
    val invalid = minValue != null && maxValue != null && minValue > maxValue

    fun commit(nextMin: String = minText, nextMax: String = maxText) {
        val parsedMin = nextMin.toIntOrNull()?.coerceIn(bounds.min, bounds.max)
        val parsedMax = nextMax.toIntOrNull()?.coerceIn(bounds.min, bounds.max)
        if (parsedMin != null && parsedMax != null && parsedMin > parsedMax) return
        onPriceChange(parsedMin, parsedMax)
    }

    val sliderRange = remember(bounds, minValue, maxValue) {
        val start = (minValue ?: bounds.min).coerceIn(bounds.min, bounds.max)
        val end = (maxValue ?: bounds.max).coerceIn(start, bounds.max)
        start.toFloat()..end.toFloat()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Цена",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CompactPriceField(
                    value = minText,
                    onValueChange = { next ->
                        minText = normalizePriceInput(next, bounds.max)
                        commit(nextMin = minText, nextMax = maxText)
                    },
                    placeholderText = "0",
                    modifier = Modifier.weight(1f),
                )
                CompactPriceField(
                    value = maxText,
                    onValueChange = { next ->
                        maxText = normalizePriceInput(next, bounds.max)
                        commit(nextMin = minText, nextMax = maxText)
                    },
                    placeholderText = bounds.max.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            if (bounds.max > bounds.min) {
                RangeSlider(
                    value = sliderRange,
                    onValueChange = { range ->
                        val nextMin = range.start.toInt().toString()
                        val nextMax = range.endInclusive.toInt().toString()
                        minText = nextMin
                        maxText = nextMax
                        commit(nextMin = nextMin, nextMax = nextMax)
                    },
                    valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                    colors = neutralRangeSliderColors(),
                )
            }
            if (invalid) {
                Text(
                    text = "Минимум не может быть больше максимума",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun QuickConditionFilterCard(
    selected: Set<ConditionOption>,
    options: List<ConditionOptionItem>,
    onReset: () -> Unit,
    onToggle: (ConditionOption) -> Unit,
) {
    val orderedOptions = remember(options, selected) {
        options.sortedWith(
            compareByDescending<ConditionOptionItem> { it.condition in selected }
                .thenBy { it.condition.ordinal },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Состояние",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selected.isEmpty()) {
                    item("condition_any_selected") {
                        CategoryChip(
                            text = "Любое",
                            highlighted = true,
                            onClick = onReset,
                        )
                    }
                }
                items(orderedOptions, key = { option -> option.condition.value }) { option ->
                    val label = option.count?.let { count ->
                        "${option.condition.label} ($count)"
                    } ?: option.condition.label
                    CategoryChip(
                        text = label,
                        highlighted = option.condition in selected,
                        onClick = { onToggle(option.condition) },
                    )
                }
                if (selected.isNotEmpty()) {
                    item("condition_any") {
                        CategoryChip(
                            text = "Любое",
                            highlighted = false,
                            onClick = onReset,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun QuickDeliveryAvailabilityFilterCard(
    deliverableOnly: Boolean,
    onToggleDeliverableOnly: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Есть доставка",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = deliverableOnly,
                onCheckedChange = onToggleDeliverableOnly,
                colors = neutralSwitchColors(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun QuickSellerTrustFilterCard(
    currentPreset: SellerTrustPreset,
    selectedSignals: Set<SellerTrustSignal>,
    availableSignals: Set<SellerTrustSignal>,
    onSelectPreset: (SellerTrustPreset) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val presets = listOf(
        SellerTrustPreset.Any,
        SellerTrustPreset.Balanced,
        SellerTrustPreset.Strict,
        SellerTrustPreset.VerifiedOnly,
    )
    QuickFacetCard(
        title = "Доверие продавца",
        actionLabel = "Настроить",
        onAction = onOpenAdvanced,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentPreset == SellerTrustPreset.Custom && selectedSignals.isNotEmpty()) {
                item("custom_preset") {
                    CategoryChip(
                        text = "Пользовательский (${selectedSignals.size})",
                        highlighted = true,
                        onClick = onOpenAdvanced,
                    )
                }
            }
            items(presets, key = { preset -> preset.presetId }) { preset ->
                val selected = when {
                    preset == SellerTrustPreset.Any && selectedSignals.isEmpty() -> true
                    currentPreset == preset -> true
                    else -> false
                }
                CategoryChip(
                    text = preset.label,
                    highlighted = selected,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
        if (availableSignals.isEmpty()) {
            Text(
                text = "Сигналы доверия сейчас недоступны.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryPickerSheet(
    categoriesByParent: Map<String?, List<Category>>,
    categoriesByCode: Map<String, Category>,
    pathCodes: List<String>,
    selectedCategoryCode: String?,
    onPathChange: (List<String>) -> Unit,
    onSelectCategory: (String?, List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val localeTag = RESULTS_UI_LOCALE
    val currentParent = pathCodes.lastOrNull()
    val list = categoriesByParent[currentParent].orEmpty()
        .sortedBy { it.displayTitle(locale = localeTag) }
    val breadcrumb = if (pathCodes.isNotEmpty()) {
        pathCodes.joinToString(" → ") { code -> categoriesByCode[code]?.displayTitle(locale = localeTag) ?: code }
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val handleBack = {
            if (pathCodes.isNotEmpty()) {
                onPathChange(pathCodes.dropLast(1))
            } else {
                onBack()
            }
        }
        SheetTopBar(title = "Категории", onBack = handleBack)
        if (breadcrumb.isNotBlank()) {
            Text(
                text = breadcrumb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (pathCodes.isEmpty()) {
                item {
                    CategoryOptionRow(
                        title = "Все категории",
                        selected = selectedCategoryCode == null,
                        hasChildren = false,
                        showDivider = list.isNotEmpty(),
                        onClick = { onSelectCategory(null, emptyList()) },
                    )
                }
            }
            itemsIndexed(list, key = { _, category -> category.code }) { index, category ->
                val hasChildren = categoriesByParent[category.code].orEmpty().isNotEmpty()
                val isSelected = category.code == selectedCategoryCode
                CategoryOptionRow(
                    title = category.displayTitle(locale = localeTag),
                    selected = isSelected,
                    hasChildren = hasChildren,
                    showDivider = index < list.lastIndex,
                    onClick = {
                        if (hasChildren) {
                            val nextPath = pathCodes + category.code
                            onPathChange(nextPath)
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
    }
}

@Composable
private fun CategoryOptionRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    hasChildren: Boolean,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    FacetCheckboxListRow(
        label = title,
        subtitle = subtitle,
        selected = selected,
        onClick = onClick,
        showDivider = showDivider,
        trailingContent = if (hasChildren) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Внутрь",
                    tint = Color(0xFF5F6368),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
    )
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
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
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
        if (availableBrands.any { option -> option.count != null }) {
            FacetAvailabilityHeader()
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(0.dp),
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
private fun FacetAvailabilityHeader(
    title: String = "Доступно",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FacetAvailabilityCount(
    count: Int?,
    enabled: Boolean,
) {
    if (count == null) return
    Text(
        text = count.coerceAtLeast(0).toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) Color(0xFF5F6368) else Color(0xFF9AA0A6),
    )
}

@Composable
private fun FacetCheckboxListRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val textColor = when {
        !enabled -> Color(0xFF9AA0A6)
        else -> Color(0xFF202124)
    }
    val checkboxTint = when {
        !enabled -> Color(0xFFBDC1C6)
        else -> Color(0xFF202124)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = checkboxTint,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(18.dp))
            }
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF5F6368) else Color(0xFFBDC1C6),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) Color(0xFF5F6368) else Color(0xFF9AA0A6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailingContent?.invoke(this)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            )
        }
    }
}

@Composable
private fun BrandRow(
    brand: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FacetCheckboxListRow(
        label = brand,
        selected = selected,
        onClick = onClick,
        trailingContent = { FacetAvailabilityCount(count = count, enabled = true) },
    )
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
    val bounds = remember(priceBounds, filters.priceMin, filters.priceMax) {
        resolvePriceBounds(
            priceBounds = priceBounds,
            currentMin = filters.priceMin,
            currentMax = filters.priceMax,
        )
    }
    var minText by remember(filters.priceMin, bounds.max) {
        mutableStateOf(filters.priceMin?.coerceIn(bounds.min, bounds.max)?.toString().orEmpty())
    }
    var maxText by remember(filters.priceMax, bounds.max) {
        mutableStateOf(filters.priceMax?.coerceIn(bounds.min, bounds.max)?.toString().orEmpty())
    }
    val minValue = minText.toIntOrNull()
    val maxValue = maxText.toIntOrNull()
    val invalid = minValue != null && maxValue != null && minValue > maxValue
    val sliderRange = remember(bounds, minValue, maxValue) {
        val start = (minValue ?: bounds.min).coerceIn(bounds.min, bounds.max)
        val end = (maxValue ?: bounds.max).coerceIn(start, bounds.max)
        start.toFloat()..end.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(title = "Цена", onBack = onBack, onReset = onReset, resetLabel = "Очистить")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactPriceField(
                value = minText,
                onValueChange = { minText = normalizePriceInput(it, bounds.max) },
                placeholderText = "0",
                modifier = Modifier
                    .weight(1f)
                    .testTag("results_price_min"),
            )
            CompactPriceField(
                value = maxText,
                onValueChange = { maxText = normalizePriceInput(it, bounds.max) },
                placeholderText = bounds.max.toString(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("results_price_max"),
            )
        }
        if (bounds.max > bounds.min) {
            RangeSlider(
                value = sliderRange,
                onValueChange = { range ->
                    minText = range.start.toInt().toString()
                    maxText = range.endInclusive.toInt().toString()
                },
                valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                colors = neutralRangeSliderColors(),
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
            onClick = {
                onApply(
                    minValue?.coerceIn(bounds.min, bounds.max),
                    maxValue?.coerceIn(bounds.min, bounds.max),
                )
            },
            enabled = !invalid,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE5E7EA),
                disabledContentColor = Color(0xFF9AA0A6),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("results_price_apply"),
        ) {
            Text(
                text = "Готово",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                softWrap = false,
            )
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
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .testTag("results_sheet_sort")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Сортировать", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE5E7EA),
                    disabledContentColor = Color(0xFF9AA0A6),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = "Применить",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
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
        Triple(ResultsViewMode.List, "Список", viewModeIcon(ResultsViewMode.List)),
        Triple(ResultsViewMode.Grid, "Сетка", viewModeIcon(ResultsViewMode.Grid)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .testTag("results_sheet_view")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Показывать", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(options) { (value, label, icon) ->
                SortOptionRow(
                    label = label,
                    selected = current == value,
                    leadingIcon = icon,
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
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    supporting: String? = null,
    count: Int? = null,
    onClick: () -> Unit,
) {
    FacetCheckboxListRow(
        label = label,
        selected = selected,
        enabled = enabled,
        subtitle = if (!enabled) supporting else null,
        leadingIcon = leadingIcon,
        onClick = onClick,
        trailingContent = { FacetAvailabilityCount(count = count, enabled = enabled) },
    )
}

@Composable
private fun PurchaseFormatSheet(
    current: PurchaseFormat,
    deliverableOnly: Boolean,
    options: List<PurchaseFormatOptionItem>,
    onSelect: (PurchaseFormat) -> Unit,
    onToggleDeliverableOnly: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Формат покупки", onBack = onBack)
        if (options.any { option -> option.count != null }) {
            FacetAvailabilityHeader()
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(options) { option ->
                SortOptionRow(
                    label = option.format.label,
                    selected = option.format == current,
                    count = option.count,
                    onClick = { onSelect(option.format) },
                )
            }
            item {
                FlatSwitchRow(
                    title = "Есть доставка",
                    subtitle = "Оставляет только товары с доставкой в вашу страну.",
                    checked = deliverableOnly,
                    onCheckedChange = onToggleDeliverableOnly,
                )
            }
        }
    }
}

@Composable
private fun FlatSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = neutralSwitchColors(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF4A4D52) else Color(0xFFF1F3F4),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) Color.White else Color(0xFF202124),
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
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Состояние товара", onBack = onBack, onReset = onReset)
        if (options.any { option -> option.count != null }) {
            FacetAvailabilityHeader()
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(options) { option ->
                SortOptionRow(
                    label = option.condition.label,
                    selected = option.condition in selected,
                    count = option.count,
                    onClick = { onSelect(option.condition) },
                )
            }
        }
    }
}

@Composable
private fun SellerTrustSheet(
    currentPreset: SellerTrustPreset,
    selectedSignals: Set<SellerTrustSignal>,
    options: List<SellerTrustSignalOption>,
    onSelectPreset: (SellerTrustPreset) -> Unit,
    onToggleSignal: (SellerTrustSignal, Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val presetOptions = listOf(
        SellerTrustPreset.Any,
        SellerTrustPreset.Balanced,
        SellerTrustPreset.Strict,
        SellerTrustPreset.VerifiedOnly,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Доверие продавца", onBack = onBack, onReset = onReset, resetLabel = "Очистить")
        Text(
            text = "Режим",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presetOptions, key = { option -> option.presetId }) { preset ->
                val selected = when {
                    preset == SellerTrustPreset.Any && selectedSignals.isEmpty() -> true
                    currentPreset == preset -> true
                    else -> false
                }
                CategoryChip(
                    text = preset.label,
                    highlighted = selected,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
        if (currentPreset == SellerTrustPreset.Custom && selectedSignals.isNotEmpty()) {
            Text(
                text = "Режим: пользовательский (${selectedSignals.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Сигналы",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(options, key = { option -> option.signal.signalId }) { option ->
                val label = option.count?.let { value -> "${option.signal.title} ($value)" } ?: option.signal.title
                SortOptionRow(
                    label = label,
                    selected = option.selected,
                    enabled = option.enabled,
                    supporting = option.supportingText,
                    onClick = { onToggleSignal(option.signal, !option.selected) },
                )
            }
        }
        Text(
            text = "Сигналы применяются только после «Показать результаты».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TypedAttributeFilterSheet(
    runtimeKey: String,
    title: String,
    valueType: FacetDataType?,
    selectionMode: FacetSelectionMode? = null,
    uiWidget: FacetUiWidget? = null,
    runtimeValues: List<ValueFacet>,
    availableSeedValues: List<String> = emptyList(),
    knownValues: List<String> = emptyList(),
    draft: TypedAttributeFilterDraft?,
    onApply: (TypedAttributeFilterDraft?) -> Unit,
    onBack: () -> Unit,
) {
    val normalizedRuntimeKey = remember(runtimeKey) { runtimeKey.trim().lowercase(Locale.ROOT) }
    val isModelFacetSheet = normalizedRuntimeKey == "model"
    val editorMode = remember(
        runtimeKey,
        valueType,
        selectionMode,
        uiWidget,
        runtimeValues,
        availableSeedValues,
        knownValues,
        draft,
    ) {
        resolveTypedFacetEditorMode(
            runtimeKey = runtimeKey,
            valueType = valueType,
            selectionMode = selectionMode,
            uiWidget = uiWidget,
            availableValueCount = runtimeValues.size + availableSeedValues.size,
            knownValueCount = knownValues.size,
            draft = draft,
        )
    }
    val operators = remember(valueType) { typedOperatorOptions(valueType) }
    var expanded by remember { mutableStateOf(false) }
    var op by remember(draft, valueType) {
        mutableStateOf(draft?.op ?: operators.firstOrNull() ?: TypedAttributeOperator.EQ)
    }
    var valueText by remember(draft, editorMode) {
        mutableStateOf(initialTypedValueText(draft, editorMode))
    }
    var toText by remember(draft) { mutableStateOf(draft?.to.orEmpty()) }
    var valuesCsv by remember(draft, editorMode) {
        mutableStateOf(initialTypedValuesCsv(draft, editorMode))
    }
    val selectedCsvValues = remember(valuesCsv) { parseFacetCsv(valuesCsv) }
    val selectedOptionValues = remember(valueText, selectedCsvValues) {
        buildList {
            val trimmed = valueText.trim()
            if (trimmed.isNotEmpty()) add(trimmed)
            addAll(selectedCsvValues)
        }.distinctBy { value -> value.lowercase(Locale.ROOT) }
    }
    val valueOptions = remember(runtimeValues, availableSeedValues, knownValues, selectedOptionValues, valueType) {
        buildTypedFacetValueOptions(
            runtimeValues = runtimeValues,
            availableSeedValues = availableSeedValues,
            knownValues = knownValues,
            selectedValues = selectedOptionValues,
            valueType = valueType,
        )
    }
    val valueSections = remember(valueOptions) { valueOptions.partitionByAvailability() }
    val visibleCsvValueOptions = valueSections.available
    val visibleSingleValueOptions = valueSections.available
    val knownUnavailableValueOptions = valueSections.unavailable

    val canApply = when (editorMode) {
        TypedFacetEditorMode.MultiChoice -> true
        TypedFacetEditorMode.SingleChoice,
        TypedFacetEditorMode.BooleanChoice,
        -> true

        TypedFacetEditorMode.Generic -> when (op) {
            TypedAttributeOperator.EXISTS,
            TypedAttributeOperator.NOT_EXISTS,
            -> true

            TypedAttributeOperator.BETWEEN -> valueText.trim().isNotEmpty() || toText.trim().isNotEmpty()
            TypedAttributeOperator.IN -> valuesCsv.trim().isNotEmpty()
            else -> valueText.trim().isNotEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
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
        if (editorMode == TypedFacetEditorMode.Generic) {
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
        }

        when (editorMode) {
            TypedFacetEditorMode.BooleanChoice,
            TypedFacetEditorMode.SingleChoice,
            -> {
                if (!isModelFacetSheet) {
                    Text(
                        text = "Значение",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FacetAvailabilityHeader()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item("any_option") {
                        TypedFacetChoiceRow(
                            label = "Все",
                            selected = valueText.trim().isEmpty(),
                            multiSelect = false,
                            onClick = { valueText = "" },
                        )
                    }
                    if (visibleSingleValueOptions.isNotEmpty()) {
                        items(visibleSingleValueOptions, key = { option -> option.value.lowercase() }) { option ->
                            TypedFacetChoiceRow(
                                label = typedFacetOptionLabel(option, valueType),
                                selected = valueText.trim().equals(option.value, ignoreCase = true),
                                multiSelect = false,
                                count = option.count,
                                enabled = true,
                                onClick = { valueText = option.value },
                            )
                        }
                    }
                    if (knownUnavailableValueOptions.isNotEmpty()) {
                        items(knownUnavailableValueOptions, key = { option -> option.value.lowercase() }) { option ->
                            TypedFacetChoiceRow(
                                label = typedFacetOptionLabel(option, valueType),
                                selected = false,
                                multiSelect = false,
                                count = option.count,
                                enabled = false,
                                onClick = {},
                            )
                        }
                    }
                }
            }

            TypedFacetEditorMode.MultiChoice -> {
                if (!isModelFacetSheet) {
                    Text(
                        text = "Значения",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (visibleCsvValueOptions.isNotEmpty() || knownUnavailableValueOptions.isNotEmpty()) {
                    FacetAvailabilityHeader()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        if (visibleCsvValueOptions.isNotEmpty()) {
                            items(visibleCsvValueOptions, key = { option -> option.value.lowercase() }) { option ->
                                TypedFacetChoiceRow(
                                    label = typedFacetOptionLabel(option, valueType),
                                    selected = selectedCsvValues.any { value ->
                                        value.equals(option.value, ignoreCase = true)
                                    },
                                    multiSelect = true,
                                    count = option.count,
                                    onClick = { valuesCsv = toggleFacetCsvValue(valuesCsv, option.value) },
                                )
                            }
                        }
                        if (knownUnavailableValueOptions.isNotEmpty()) {
                            items(knownUnavailableValueOptions, key = { option -> option.value.lowercase() }) { option ->
                                TypedFacetChoiceRow(
                                    label = typedFacetOptionLabel(option, valueType),
                                    selected = false,
                                    multiSelect = true,
                                    count = option.count,
                                    enabled = false,
                                    onClick = {},
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Нет доступных значений для выбранного фильтра.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TypedFacetEditorMode.Generic -> {
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
                        if (visibleCsvValueOptions.isNotEmpty()) {
                            Text(
                                text = "Популярные значения",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(visibleCsvValueOptions, key = { option -> option.value.lowercase() }) { option ->
                                    val selected = selectedCsvValues.any { value ->
                                        value.equals(option.value, ignoreCase = true)
                                    }
                                    CategoryChip(
                                        text = typedFacetOptionLabel(option, valueType),
                                        highlighted = selected,
                                        onClick = { valuesCsv = toggleFacetCsvValue(valuesCsv, option.value) },
                                    )
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
                        if (visibleSingleValueOptions.isNotEmpty() &&
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
                                items(visibleSingleValueOptions, key = { option -> option.value.lowercase() }) { option ->
                                    val selected = valueText.trim().equals(option.value, ignoreCase = true)
                                    CategoryChip(
                                        text = typedFacetOptionLabel(option, valueType),
                                        highlighted = selected,
                                        onClick = { valueText = option.value },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val nextDraft = when (editorMode) {
                    TypedFacetEditorMode.MultiChoice -> parseFacetCsv(valuesCsv)
                        .takeIf { values -> values.isNotEmpty() }
                        ?.let { values ->
                            TypedAttributeFilterDraft(
                                op = TypedAttributeOperator.IN,
                                valuesCsv = values.joinToString(", "),
                            )
                        }

                    TypedFacetEditorMode.SingleChoice,
                    TypedFacetEditorMode.BooleanChoice,
                    -> valueText.trim()
                        .takeIf { value -> value.isNotEmpty() }
                        ?.let { value ->
                            TypedAttributeFilterDraft(
                                op = TypedAttributeOperator.EQ,
                                value = value,
                            )
                        }

                    TypedFacetEditorMode.Generic -> TypedAttributeFilterDraft(
                        op = op,
                        value = valueText.trim(),
                        to = toText.trim(),
                        valuesCsv = valuesCsv.trim(),
                    )
                }
                onApply(nextDraft)
            },
            enabled = canApply,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE5E7EA),
                disabledContentColor = Color(0xFF9AA0A6),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("results_typed_apply"),
        ) {
            Text(
                text = "Применить",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun TypedFacetChoiceRow(
    label: String,
    selected: Boolean,
    multiSelect: Boolean,
    count: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FacetCheckboxListRow(
        label = label,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        trailingContent = { FacetAvailabilityCount(count = count, enabled = enabled) },
    )
}

private fun typedKeyboardOptions(valueType: FacetDataType?): KeyboardOptions =
    when (valueType) {
        FacetDataType.RANGE -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else -> KeyboardOptions.Default
    }

internal enum class TypedFacetEditorMode {
    Generic,
    SingleChoice,
    MultiChoice,
    BooleanChoice,
}

internal fun resolveTypedFacetEditorMode(
    runtimeKey: String,
    valueType: FacetDataType?,
    selectionMode: FacetSelectionMode?,
    uiWidget: FacetUiWidget?,
    availableValueCount: Int,
    knownValueCount: Int,
    draft: TypedAttributeFilterDraft?,
): TypedFacetEditorMode {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    if (normalizedRuntimeKey == "model") {
        return TypedFacetEditorMode.MultiChoice
    }
    val explicitSemanticMode = selectionMode ?: when (uiWidget) {
        FacetUiWidget.CHECKBOX_GROUP -> FacetSelectionMode.MULTI
        FacetUiWidget.RADIO_GROUP -> FacetSelectionMode.SINGLE
        FacetUiWidget.TOGGLE -> FacetSelectionMode.BOOLEAN
        FacetUiWidget.RANGE_INPUT -> FacetSelectionMode.RANGE
        FacetUiWidget.TEXT_INPUT,
        null,
        -> null
    }
    val semanticMode = explicitSemanticMode ?: when (valueType) {
        FacetDataType.BOOL -> FacetSelectionMode.BOOLEAN
        FacetDataType.ENUM -> FacetSelectionMode.SINGLE
        FacetDataType.RANGE -> FacetSelectionMode.RANGE
        FacetDataType.TEXT,
        null,
        -> null
    }

    val shouldPreferChoiceEditor = shouldPreferTypedFacetChoiceEditor(
        runtimeKey = normalizedRuntimeKey,
        valueType = valueType,
        availableValueCount = availableValueCount,
        knownValueCount = knownValueCount,
    )
    val shouldDefaultToMultiChoice = shouldPreferMultiChoiceTypedFacetEditor(
        runtimeKey = normalizedRuntimeKey,
        valueType = valueType,
        explicitSemanticMode = explicitSemanticMode,
    )

    val resolved = when {
        shouldPreferChoiceEditor && semanticMode == FacetSelectionMode.MULTI -> TypedFacetEditorMode.MultiChoice
        shouldPreferChoiceEditor && shouldDefaultToMultiChoice -> TypedFacetEditorMode.MultiChoice
        shouldPreferChoiceEditor && valueType == FacetDataType.BOOL -> TypedFacetEditorMode.BooleanChoice
        shouldPreferChoiceEditor -> TypedFacetEditorMode.SingleChoice
        else -> when (semanticMode) {
        FacetSelectionMode.MULTI -> TypedFacetEditorMode.MultiChoice
        FacetSelectionMode.SINGLE -> TypedFacetEditorMode.SingleChoice
        FacetSelectionMode.BOOLEAN -> TypedFacetEditorMode.BooleanChoice
        FacetSelectionMode.RANGE,
        null,
        -> TypedFacetEditorMode.Generic
        }
    }
    if (draft == null) return resolved
    return when (resolved) {
        TypedFacetEditorMode.MultiChoice -> {
            if (draft.op == TypedAttributeOperator.IN || draft.op == TypedAttributeOperator.EQ) resolved
            else TypedFacetEditorMode.Generic
        }

        TypedFacetEditorMode.SingleChoice,
        TypedFacetEditorMode.BooleanChoice,
        -> {
            if (draft.op == TypedAttributeOperator.EQ || draft.op == TypedAttributeOperator.IN) resolved
            else TypedFacetEditorMode.Generic
        }

        TypedFacetEditorMode.Generic -> resolved
    }
}

private fun shouldPreferTypedFacetChoiceEditor(
    runtimeKey: String,
    valueType: FacetDataType?,
    availableValueCount: Int,
    knownValueCount: Int,
): Boolean {
    if (availableValueCount <= 0 && knownValueCount <= 0) return false
    if (valueType == FacetDataType.BOOL || valueType == FacetDataType.ENUM) return true
    return runtimeKey in productChoiceTypedFacetKeys
}

private fun shouldPreferMultiChoiceTypedFacetEditor(
    runtimeKey: String,
    valueType: FacetDataType?,
    explicitSemanticMode: FacetSelectionMode?,
): Boolean {
    if (runtimeKey !in productChoiceTypedFacetKeys) return false
    if (valueType == FacetDataType.BOOL) return false
    return explicitSemanticMode == null
}

private val productChoiceTypedFacetKeys: Set<String> = setOf(
    "model",
    "color",
    "memory_gb",
    "ram_gb",
    "network_type",
    "os_family",
    "refresh_rate_hz",
    "chipset_family",
)

private fun initialTypedValueText(
    draft: TypedAttributeFilterDraft?,
    editorMode: TypedFacetEditorMode,
): String = when (editorMode) {
    TypedFacetEditorMode.MultiChoice -> ""
    TypedFacetEditorMode.SingleChoice,
    TypedFacetEditorMode.BooleanChoice,
    -> when {
        draft?.op == TypedAttributeOperator.IN -> parseFacetCsv(draft.valuesCsv).firstOrNull().orEmpty()
        else -> draft?.value.orEmpty()
    }

    TypedFacetEditorMode.Generic -> draft?.value.orEmpty()
}

private fun initialTypedValuesCsv(
    draft: TypedAttributeFilterDraft?,
    editorMode: TypedFacetEditorMode,
): String = when (editorMode) {
    TypedFacetEditorMode.MultiChoice -> when {
        draft?.op == TypedAttributeOperator.IN -> draft.valuesCsv
        draft?.value?.isNotBlank() == true -> draft.value
        else -> draft?.valuesCsv.orEmpty()
    }

    TypedFacetEditorMode.SingleChoice,
    TypedFacetEditorMode.BooleanChoice,
    TypedFacetEditorMode.Generic,
    -> draft?.valuesCsv.orEmpty()
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

private fun typedFacetOptionLabel(
    option: TypedFacetValueOption,
    valueType: FacetDataType?,
): String {
    return when {
        valueType == FacetDataType.BOOL && option.value.equals("true", ignoreCase = true) -> "Да"
        valueType == FacetDataType.BOOL && option.value.equals("false", ignoreCase = true) -> "Нет"
        else -> option.value
    }
}

internal fun parseFacetCsv(raw: String): List<String> =
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
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .testTag("results_sheet_location")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(title = "Где находится", onBack = onBack, onReset = onReset)
        TextField(
            value = locationText,
            onValueChange = { locationText = it },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = resultsTextFieldColors(),
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
            .fillMaxWidth()
            .heightIn(max = sheetMaxHeight())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(
            title = "Доступ к геолокации",
            onClose = onDismiss,
        )
        SystemNoticeCard(
            title = "Нужен доступ к местоположению",
            body = "Чтобы искать предложения рядом, нужен доступ к местоположению. Мы используем только примерный район поиска и не отправляем точные координаты в аналитику.",
            tone = SystemNoticeTone.Info,
            footer = if (permissionState == ResultsGeoPermissionState.DeniedPermanent) {
                "Разрешение было ранее отключено. Включите его в настройках приложения."
            } else {
                null
            },
            compact = true,
        )
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
    val bg = if (highlighted) {
        Color(0xFFF1F3F4)
    } else {
        Color.Transparent
    }
    val fg = Color(0xFF202124)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = if (highlighted) null else BorderStroke(1.dp, Color(0xFFDADCE0)),
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun CompactPriceField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter(Char::isDigit)) },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholderText,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    Text(
                        text = "₽",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFCDD2D7)),
                )
            }
        },
    )
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
        colors = resultsTextFieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag),
    )
}

@Composable
private fun resultsTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFFF8FAFD),
    unfocusedContainerColor = Color(0xFFF8FAFD),
    disabledContainerColor = Color(0xFFF8FAFD),
    errorContainerColor = Color(0xFFF8FAFD),
    focusedIndicatorColor = Color(0xFF4A4D52),
    unfocusedIndicatorColor = Color(0xFFDADCE0),
    disabledIndicatorColor = Color(0xFFE4E7EB),
    errorIndicatorColor = MaterialTheme.colorScheme.error,
    cursorColor = Color(0xFF4A4D52),
)

@Composable
private fun neutralRangeSliderColors() = SliderDefaults.colors(
    thumbColor = Color(0xFF4A4D52),
    activeTrackColor = Color(0xFF4A4D52),
    inactiveTrackColor = Color(0xFFE5E7EA),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun neutralSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Color(0xFF4A4D52),
    checkedBorderColor = Color(0xFF4A4D52),
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFE5E7EA),
    uncheckedBorderColor = Color(0xFFE5E7EA),
)

internal fun resolveCriteriaQueryModel(
    query: NormalizedQuery?,
    facetDefinitions: List<FacetDefinition>,
): String? {
    val requestedQueryModel = query?.model?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return null
    val hasManagedModelFacet = buildResultsResolutionRuntimeKeyMap(facetDefinitions).containsKey("model")
    return if (hasManagedModelFacet) null else requestedQueryModel
}

private fun buildCriteria(
    filters: FilterState,
    querySessionId: String,
    facetDefinitions: List<FacetDefinition>,
    facetResolution: ResultsFacetResolution,
    profileSettings: ProfileSettings,
): OfferSearchCriteria? {
    val query = filters.query
    val typedAttributeFilters = buildTypedAttributeFilters(
        drafts = facetResolution.effectiveTypedAttributeFilters,
        definitions = facetDefinitions,
    )
    val effectiveDeliverableOnly = resolveResultsDeliverableOnly(
        explicitDeliverableOnly = filters.deliverableOnly,
        hideUndeliverable = profileSettings.hideUndeliverable,
    )
    val effectiveQueryBrand = if (filters.brands.isEmpty()) {
        query?.brand?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val effectiveQueryModel = resolveCriteriaQueryModel(
        query = query,
        facetDefinitions = facetDefinitions,
    )
    val effectiveQueryAttributes = facetResolution.effectiveFreeformQueryAttributes
    val hasAnyFilter = effectiveQueryBrand != null ||
        effectiveQueryModel != null ||
        effectiveQueryAttributes.isNotEmpty() ||
        !filters.categoryCode.isNullOrBlank() ||
        !filters.facetCollectionCode.isNullOrBlank() ||
        !filters.facetPresetCode.isNullOrBlank() ||
        filters.presetAttributes.isNotEmpty() ||
        filters.brands.isNotEmpty() ||
        filters.priceMin != null ||
        filters.priceMax != null ||
        filters.conditions.isNotEmpty() ||
        typedAttributeFilters.isNotEmpty() ||
        !filters.location.isNullOrBlank() ||
        effectiveDeliverableOnly
    if (!hasAnyFilter) return null
    val locale = Locale.getDefault()
    val activeDeliveryAddress = profileSettings.activeDeliveryAddress()?.location?.normalized()
    val resolvedUserCountry = resolveResultsUserCountryCode(
        profileCountryCode = profileSettings.countryCode,
        activeDeliveryAddressCountryCode = activeDeliveryAddress?.countryCode,
        locale = locale,
    )
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
    val typedKeys = typedAttributeFilters.keys
    val attrs = buildMap {
        putAll(filters.presetAttributes)
        putAll(effectiveQueryAttributes)
        if (normalizedConditions.size == 1) {
            put("condition", normalizedConditions.first())
        }
    }
    return OfferSearchCriteria(
        brand = effectiveQueryBrand,
        model = effectiveQueryModel,
        brands = filters.brands.toList(),
        categoryCode = filters.categoryCode,
        priceMin = filters.priceMin?.toDouble(),
        priceMax = filters.priceMax?.toDouble(),
        location = filters.location,
        radiusKm = resolvedRadius,
        centerLat = if (hasResolvedGeoCenter) filters.centerLat else null,
        centerLon = if (hasResolvedGeoCenter) filters.centerLon else null,
        geoMode = if (hasResolvedGeoCenter) com.example.shoppingassistant.domain.model.GeoMode.RADIUS else null,
        deliverableOnly = effectiveDeliverableOnly,
        condition = normalizedConditions.firstOrNull(),
        conditions = normalizedConditions,
        deliveryChannels = emptyList(),
        attributes = attrs
            .filterKeys { key -> key !in typedKeys }
            .toTypedAttributesGuess(),
        attributeFilters = typedAttributeFilters,
        deliveryAddress = activeDeliveryAddress,
        userCountry = resolvedUserCountry,
        userLanguage = locale.language.takeIf { it.isNotBlank() },
        limit = 20,
        sort = filters.sort,
        sellerId = filters.sellerId,
        querySessionId = querySessionId,
        facetCollectionCode = filters.facetCollectionCode,
        facetPresetCode = filters.facetPresetCode,
    )
}

internal fun resolveResultsDeliverableOnly(
    explicitDeliverableOnly: Boolean,
    hideUndeliverable: Boolean,
): Boolean = explicitDeliverableOnly || hideUndeliverable

internal fun resolveResultsUserCountryCode(
    profileCountryCode: String?,
    activeDeliveryAddressCountryCode: String? = null,
    locale: Locale = Locale.getDefault(),
): String? {
    val normalizedAddressCountry = activeDeliveryAddressCountryCode
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.takeIf { value -> value.isNotEmpty() }
    if (normalizedAddressCountry != null) return normalizedAddressCountry
    val normalizedProfileCountry = profileCountryCode
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.takeIf { value -> value.isNotEmpty() }
    if (normalizedProfileCountry != null) return normalizedProfileCountry
    return locale.country
        .trim()
        .uppercase(Locale.ROOT)
        .takeIf { value -> value.isNotEmpty() }
}

private fun resultsCatalogScopeRequired(filters: FilterState): Boolean =
    !filters.categoryCode.isNullOrBlank() ||
        !filters.facetCollectionCode.isNullOrBlank() ||
        !filters.facetPresetCode.isNullOrBlank() ||
        filters.typedAttributeFilters.isNotEmpty()

private fun buildTypedAttributeFilters(
    drafts: Map<String, TypedAttributeFilterDraft>,
    definitions: List<FacetDefinition>,
): Map<String, TypedAttributeFilter> {
    if (drafts.isEmpty() || definitions.isEmpty()) return emptyMap()
    val runtimeKeyByTypedKey = buildTypedFacetRuntimeKeyMap(definitions)
    val definitionTypeByKey = definitions.associate { definition ->
        definition.normalizedRuntimeKey() to definition.valueType
    }
    return drafts.entries
        .mapNotNull { (rawKey, draft) ->
            val key = rawKey.trim().lowercase()
            if (key.isBlank()) return@mapNotNull null
            val runtimeKey = runtimeKeyByTypedKey[key] ?: key
            val valueType = definitionTypeByKey[runtimeKey]
            val filter = draft.toTypedFilter(valueType) ?: return@mapNotNull null
            runtimeKey to filter
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

internal fun resolveResultsCategoryCode(
    categoryCode: String?,
    categories: List<Category>,
): String? {
    val normalizedCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return CategoryReplacementResolver.resolve(
        requestedCode = normalizedCode,
        categories = categories,
    )?.resolvedCode ?: normalizedCode
}

internal fun resolveResultsPresetCategoryCode(
    currentCategoryCode: String?,
    collection: FacetCollection?,
    preset: FacetPreset?,
    categories: List<Category>,
): String? {
    val requestedCode = collection?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        ?: preset?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        ?: currentCategoryCode
    return resolveResultsCategoryCode(
        categoryCode = requestedCode,
        categories = categories,
    )
}

internal fun resolveResultsPresetFacetDefinitions(
    definitions: List<FacetDefinition>,
    currentCategoryCode: String?,
    collection: FacetCollection?,
    preset: FacetPreset?,
    categories: List<Category>,
): List<FacetDefinition> = scopeFacetDefinitionsForCategory(
    definitions = definitions,
    categoryCode = resolveResultsPresetCategoryCode(
        currentCategoryCode = currentCategoryCode,
        collection = collection,
        preset = preset,
        categories = categories,
    ),
)

private data class FilterState(
    val queryText: String = "",
    val query: NormalizedQuery? = null,
    val sellerId: Long? = null,
    val sellerName: String? = null,
    val categoryCode: String? = null,
    val categoryPath: List<String> = emptyList(),
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val presetAttributes: Map<String, String> = emptyMap(),
    val brands: Set<String> = emptySet(),
    val interpretedCategoryCode: String? = null,
    val interpretedFacetCollectionCode: String? = null,
    val interpretedFacetPresetCode: String? = null,
    val interpretedBrandSeed: String? = null,
    val interpretedConditionSeed: String? = null,
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
    ResultsViewMode.Compact,
    ResultsViewMode.Dense,
    -> ResultsViewMode.List
    else -> this
}

private object AppliedChipKey {
    const val Seller = "seller"
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
    val icon: ImageVector? = null,
    val showLabel: Boolean = true,
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

internal data class ResultsTypedFacetLookupScope(
    val brand: String? = null,
    val model: String? = null,
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

private fun FacetDefinition.normalizedFacetKey(): String =
    facetKey.trim().lowercase(Locale.ROOT)

private fun FacetDefinition.normalizedRuntimeKey(): String =
    runtimeFilterKey().trim().lowercase(Locale.ROOT)

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
            .filter { definition -> definition.isActiveForToday() }
            .filterNot { definition -> definition.ui.hidden }
            .map { definition -> definition.normalizedRuntimeKey() }
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

private fun resolvePriceBounds(
    priceBounds: PriceBounds?,
    currentMin: Int?,
    currentMax: Int?,
): PriceBounds {
    val observedMax = priceBounds?.max ?: PRICE_FILTER_FALLBACK_MAX
    val selectedMax = listOfNotNull(currentMin, currentMax).maxOrNull() ?: 0
    return PriceBounds(
        min = 0,
        max = maxOf(observedMax, selectedMax),
    )
}

private fun normalizePriceInput(raw: String, upperBound: Int): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    val parsed = digits.toLongOrNull() ?: return digits
    return parsed.coerceAtMost(upperBound.toLong()).toString()
}

private fun initialFilterState(payload: ResultsPayload): FilterState {
    val materializedQuery = materializeResultsStructuredQuery(payload.query)
    val sanitizedBrandSelection = sanitizeSelectedBrandsForCategory(
        selectedBrands = materializedQuery.brand?.let { setOf(it) } ?: emptySet(),
        interpretedBrandSeed = materializedQuery.brand,
        categoryCode = payload.categoryCode,
    )
    return FilterState(
        queryText = payload.queryText,
        query = payload.query,
        sellerId = payload.sellerId,
        sellerName = payload.sellerName,
        categoryCode = payload.categoryCode,
        facetCollectionCode = payload.facetCollectionCode,
        facetPresetCode = payload.facetPresetCode,
        brands = sanitizedBrandSelection.brands,
        interpretedCategoryCode = payload.categoryCode,
        interpretedFacetCollectionCode = payload.facetCollectionCode,
        interpretedFacetPresetCode = payload.facetPresetCode,
        interpretedBrandSeed = sanitizedBrandSelection.interpretedBrandSeed,
        interpretedConditionSeed = materializedQuery.condition,
        location = payload.location,
        radiusKm = payload.radiusKm,
        conditions = mapConditionOptions(payload.conditions)
            .ifEmpty { materializedQuery.condition?.let { mapConditionOptions(listOf(it)) } ?: emptySet() },
        typedAttributeFilters = materializedQuery.typedAttributeFilters,
        sort = payload.sort,
    )
}

internal data class ResultsStructuredQueryMaterialization(
    val brand: String? = null,
    val condition: String? = null,
    val typedAttributeFilters: Map<String, TypedAttributeFilterDraft> = emptyMap(),
)

internal fun materializeResultsStructuredQuery(
    query: NormalizedQuery?,
): ResultsStructuredQueryMaterialization {
    if (query == null) return ResultsStructuredQueryMaterialization()
    val brand = query.brand.trim().takeIf { value -> value.isNotEmpty() }
    val condition = query.attributes
        .entries
        .firstOrNull { (key, _) -> key.trim().equals("condition", ignoreCase = true) }
        ?.value
        ?.asRawString()
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    val typedAttributeFilters = LinkedHashMap<String, TypedAttributeFilterDraft>()
    query.model.trim().takeIf { value -> value.isNotEmpty() }?.let { model ->
        typedAttributeFilters["model"] = TypedAttributeFilterDraft(
            op = TypedAttributeOperator.EQ,
            value = model,
        )
    }
    query.attributes
        .toRawStringAttributes()
        .forEach { (rawKey, rawValue) ->
            val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
            val normalizedValue = rawValue.trim()
            if (
                normalizedKey.isEmpty() ||
                normalizedValue.isEmpty() ||
                normalizedKey == "brand" ||
                normalizedKey == "model" ||
                normalizedKey == "condition"
            ) {
                return@forEach
            }
            typedAttributeFilters[normalizedKey] = TypedAttributeFilterDraft(
                op = TypedAttributeOperator.EQ,
                value = normalizedValue,
            )
        }
    return ResultsStructuredQueryMaterialization(
        brand = brand,
        condition = condition,
        typedAttributeFilters = typedAttributeFilters,
    )
}

internal fun resultsStructuredQueryTypedFacetKeys(
    query: NormalizedQuery?,
): Set<String> = materializeResultsStructuredQuery(query).typedAttributeFilters.keys

private fun ResultsPayload.querySeedBrand(): String? =
    query
        ?.brand
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }
        ?: query
            ?.attributes
            ?.entries
            ?.firstOrNull { (key, _) -> key.trim().equals("brand", ignoreCase = true) }
            ?.value
            ?.asRawString()
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }

private fun ResultsPayload.querySeedCondition(): String? =
    query
        ?.attributes
        ?.entries
        ?.firstOrNull { (key, _) -> key.trim().equals("condition", ignoreCase = true) }
        ?.value
        ?.asRawString()
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }

private fun FilterState.dropInterpretedSeedsForNewQuery(): FilterState {
    val interpretedTypedFacetKeys = resultsStructuredQueryTypedFacetKeys(query)
    val normalizedInterpretedBrand = interpretedBrandSeed?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    val normalizedInterpretedCondition = interpretedConditionSeed?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    val remainingBrands = if (normalizedInterpretedBrand == null) {
        brands
    } else {
        brands.filterNot { brand -> brand.equals(normalizedInterpretedBrand, ignoreCase = true) }.toSet()
    }
    val remainingConditions = if (normalizedInterpretedCondition == null) {
        conditions
    } else {
        conditions.filterNot { option -> option.value.equals(normalizedInterpretedCondition, ignoreCase = true) }.toSet()
    }
    val remainingTypedAttributeFilters = typedAttributeFilters
        .filterKeys { key -> key.trim().lowercase(Locale.ROOT) !in interpretedTypedFacetKeys }
        .toMap(LinkedHashMap())
    val shouldClearCategory = !interpretedCategoryCode.isNullOrBlank() &&
        categoryCode?.equals(interpretedCategoryCode, ignoreCase = true) == true
    val shouldClearCollection = !interpretedFacetCollectionCode.isNullOrBlank() &&
        facetCollectionCode?.equals(interpretedFacetCollectionCode, ignoreCase = true) == true
    val shouldClearPreset = !interpretedFacetPresetCode.isNullOrBlank() &&
        facetPresetCode?.equals(interpretedFacetPresetCode, ignoreCase = true) == true

    return copy(
        categoryCode = if (shouldClearCategory) null else categoryCode,
        categoryPath = if (shouldClearCategory) emptyList() else categoryPath,
        facetCollectionCode = if (shouldClearCollection) null else facetCollectionCode,
        facetPresetCode = if (shouldClearPreset) null else facetPresetCode,
        brands = remainingBrands,
        conditions = remainingConditions,
        interpretedCategoryCode = null,
        interpretedFacetCollectionCode = null,
        interpretedFacetPresetCode = null,
        interpretedBrandSeed = null,
        interpretedConditionSeed = null,
        typedAttributeFilters = remainingTypedAttributeFilters,
    )
}

private fun FilterState.applyInlineSearchIntent(intent: InterpretedSearchIntent): FilterState {
    val nextQuery = intent.toResultsQuery()
    val previousQueryTypedFacetKeys = resultsStructuredQueryTypedFacetKeys(query)
    val materializedQuery = materializeResultsStructuredQuery(nextQuery)
    val nextCategoryCode = intent.categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() } ?: categoryCode
    val nextFacetCollectionCode = intent.facetCollectionCode?.trim()?.takeIf { value -> value.isNotEmpty() } ?: facetCollectionCode
    val nextFacetPresetCode = intent.facetPresetCode?.trim()?.takeIf { value -> value.isNotEmpty() } ?: facetPresetCode
    val nextBrandSeed = materializedQuery.brand
    val nextConditionSeed = materializedQuery.condition
    val nextConditions = when {
        nextConditionSeed != null -> mapConditionOptions(listOf(nextConditionSeed)).ifEmpty { conditions }
        else -> conditions
    }
    val preservedTypedAttributeFilters = typedAttributeFilters
        .filterKeys { key -> key.trim().lowercase(Locale.ROOT) !in previousQueryTypedFacetKeys }
        .toMutableMap()
        .apply { putAll(materializedQuery.typedAttributeFilters) }
        .toMap(LinkedHashMap())

    return copy(
        queryText = intent.queryText,
        query = nextQuery,
        categoryCode = nextCategoryCode,
        categoryPath = if (nextCategoryCode != categoryCode) emptyList() else categoryPath,
        facetCollectionCode = nextFacetCollectionCode,
        facetPresetCode = nextFacetPresetCode,
        brands = nextBrandSeed?.let { setOf(it) } ?: brands,
        interpretedCategoryCode = intent.categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() },
        interpretedFacetCollectionCode = intent.facetCollectionCode?.trim()?.takeIf { value -> value.isNotEmpty() },
        interpretedFacetPresetCode = intent.facetPresetCode?.trim()?.takeIf { value -> value.isNotEmpty() },
        interpretedBrandSeed = nextBrandSeed,
        interpretedConditionSeed = nextConditionSeed,
        conditions = nextConditions,
        typedAttributeFilters = preservedTypedAttributeFilters,
    )
}

private fun Map<String, TypedAttributeFilter>.toTypedAttributeFilterDrafts(): Map<String, TypedAttributeFilterDraft> =
    entries
        .mapNotNull { (rawKey, filter) ->
            val key = rawKey.trim().lowercase()
            if (key.isBlank()) return@mapNotNull null
            key to filter.toDraft()
        }
        .toMap(LinkedHashMap())

private fun TypedAttributeFilter.toDraft(): TypedAttributeFilterDraft = when (op) {
    TypedAttributeOperator.EXISTS,
    TypedAttributeOperator.NOT_EXISTS,
    -> TypedAttributeFilterDraft(op = op)

    TypedAttributeOperator.BETWEEN -> TypedAttributeFilterDraft(
        op = op,
        value = from?.asRawString().orEmpty(),
        to = to?.asRawString().orEmpty(),
    )

    TypedAttributeOperator.IN -> TypedAttributeFilterDraft(
        op = op,
        valuesCsv = values.joinToString(",") { value -> value.asRawString() },
    )

    else -> TypedAttributeFilterDraft(
        op = op,
        value = value?.asRawString().orEmpty(),
    )
}

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
        interpretedCategoryCode = null,
        interpretedFacetCollectionCode = null,
        interpretedFacetPresetCode = null,
        interpretedBrandSeed = null,
        interpretedConditionSeed = null,
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
    return resetNonQuery().copy(
        sort = currentSort,
        location = location,
        radiusKm = radiusKm,
        centerLat = centerLat,
        centerLon = centerLon,
    )
}

private fun buildQueryTypedFacetRelaxationResetKey(filters: FilterState): String =
    buildList {
        add(filters.queryText.trim())
        add(filters.sellerId?.toString().orEmpty())
        add(filters.categoryCode.orEmpty())
        add(filters.facetCollectionCode.orEmpty())
        add(filters.facetPresetCode.orEmpty())
        add(filters.brands.sorted().joinToString(","))
        add(filters.priceMin?.toString().orEmpty())
        add(filters.priceMax?.toString().orEmpty())
        add(filters.conditions.map { option -> option.value }.sorted().joinToString(","))
        add(filters.deliverableOnly.toString())
        add(filters.location.orEmpty())
        add(filters.radiusKm?.toString().orEmpty())
        add(filters.presetAttributes.entries.sortedBy { (key, _) -> key }.joinToString("&") { (key, value) -> "$key=$value" })
        add(buildTrackFiltersExtra(filters).entries.joinToString("&") { (key, value) -> "$key=$value" })
    }.joinToString("|")

private fun FilterState.activeFilterCount(
    effectiveTypedAttributeFilters: Map<String, TypedAttributeFilterDraft> = typedAttributeFilters,
): Int {
    var count = 0
    if (sellerId != null) count += 1
    if (!categoryCode.isNullOrBlank()) count += 1
    if (presetAttributes.isNotEmpty()) count += 1
    if (brands.isNotEmpty()) count += 1
    if (priceMin != null || priceMax != null) count += 1
    if (conditions.isNotEmpty()) count += 1
    if (effectiveTypedAttributeFilters.isNotEmpty()) count += effectiveTypedAttributeFilters.size
    if (deliverableOnly) count += 1
    return count
}

private fun FilterState.categorySummary(
    categoriesByCode: Map<String, Category>,
    localeTag: String = RESULTS_UI_LOCALE,
): String =
    resolveResultsCategorySummary(
        categoryCode = categoryCode,
        categoryPath = categoryPath,
        categoriesByCode = categoriesByCode,
        localeTag = localeTag,
    )

internal data class FilterHubApplyActionState(
    val label: String,
    val enabled: Boolean,
)

internal fun resolveFilterHubApplyActionState(
    isDirty: Boolean,
    isApplyEnabled: Boolean,
    resultsCount: Int? = null,
): FilterHubApplyActionState = when {
    !isDirty -> FilterHubApplyActionState(
        label = "Готово",
        enabled = true,
    )

    else -> FilterHubApplyActionState(
        label = resultsCount?.let(::formatFilterHubResultsCountLabel) ?: "Показать результаты",
        enabled = isApplyEnabled,
    )
}

internal fun formatFilterHubResultsCountLabel(resultsCount: Int): String {
    val safeCount = resultsCount.coerceAtLeast(0)
    val mod10 = safeCount % 10
    val mod100 = safeCount % 100
    val noun = when {
        mod10 == 1 && mod100 != 11 -> "результат"
        mod10 in 2..4 && mod100 !in 12..14 -> "результата"
        else -> "результатов"
    }
    return "Показать $safeCount $noun"
}

private fun FilterState.brandSummary(): String {
    if (brands.isEmpty()) return "Не выбрано"
    return summarizeSelectedValues(brands.sorted(), emptyLabel = "Не выбрано")
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
    return summarizeSelectedValues(
        values = conditions.map { it.label }.sorted(),
        emptyLabel = "Любое",
    )
}

private fun FilterState.purchaseFormatSummary(): String =
    buildList {
        if (deliverableOnly) add("Есть доставка")
    }.ifEmpty { listOf("Любая") }
        .joinToString(" · ")

private fun FilterState.sellerTrustSummary(): String {
    if (sellerTrustSignals.isEmpty()) return "Любой"
    return when (sellerTrustPreset) {
        SellerTrustPreset.Any -> "Любой"
        SellerTrustPreset.Custom -> "Пользовательский (${sellerTrustSignals.size})"
        else -> sellerTrustPreset.label
    }
}

private fun FilterState.typedFilterSummary(facetKey: String): String {
    return typedFilterSummary(facetKey, typedAttributeFilters)
}

private fun typedFilterSummary(
    facetKey: String,
    typedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
): String {
    val normalizedKey = facetKey.trim().lowercase(Locale.ROOT)
    if (normalizedKey.isBlank()) return "Не задано"
    val draft = typedAttributeFilters.draftForRuntimeKey(normalizedKey)
        ?: return "Не задано"
    return typedFilterDraftSummary(draft)
}

private fun Map<String, TypedAttributeFilterDraft>.draftForRuntimeKey(runtimeKey: String): TypedAttributeFilterDraft? {
    val normalizedKey = runtimeKey.trim().lowercase(Locale.ROOT)
    if (normalizedKey.isBlank()) return null
    return this[normalizedKey]
        ?: entries.firstOrNull { (key, _) ->
            key.trim().equals(normalizedKey, ignoreCase = true)
        }?.value
}

private fun ResultsFacetResolution.effectiveDraftForRuntimeKey(runtimeKey: String): TypedAttributeFilterDraft? =
    effectiveTypedAttributeFilters.draftForRuntimeKey(runtimeKey)

private fun ResultsFacetResolution.queryBackedDraftForRuntimeKey(runtimeKey: String): TypedAttributeFilterDraft? =
    queryBackedTypedAttributeFilters.draftForRuntimeKey(runtimeKey)

private fun resolveResultsFiltersScopeMessage(
    filters: FilterState,
    effectiveTypedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
): String? {
    val hasBrandContext = filters.brands.isNotEmpty() ||
        !filters.interpretedBrandSeed.isNullOrBlank() ||
        (!filters.query?.brand.isNullOrBlank() && filters.brands.isEmpty())
    val hasModelContext = selectedTypedFacetValues(
        typedAttributeFilters = effectiveTypedAttributeFilters,
        facetKey = "model",
    ).isNotEmpty()
    return when {
        hasBrandContext && hasModelContext -> "Значения в фильтрах показаны только для выбранных брендов и моделей."
        hasModelContext -> "Значения в фильтрах показаны только для выбранных моделей."
        hasBrandContext -> "Значения в фильтрах показаны только для выбранных брендов."
        else -> null
    }
}

private fun typedFilterDraftSummary(draft: TypedAttributeFilterDraft): String = when (draft.op) {
    TypedAttributeOperator.EQ -> draft.value.trim().ifBlank { "…" }.take(40)
    TypedAttributeOperator.EXISTS -> "заполнено"
    TypedAttributeOperator.NOT_EXISTS -> "не заполнено"
    TypedAttributeOperator.BETWEEN -> {
        val left = draft.value.ifBlank { "…" }
        val right = draft.to.ifBlank { "…" }
        "$left..$right"
    }

    TypedAttributeOperator.IN -> summarizeSelectedValues(
        values = parseFacetCsv(draft.valuesCsv),
        emptyLabel = "в списке …",
    ).take(40)
    else -> "${typedOperatorLabel(draft.op)} ${draft.value}".trim().take(40)
}

internal fun resolveTypedFacetHubRowSummary(
    explicitDraft: TypedAttributeFilterDraft?,
    queryBackedDraft: TypedAttributeFilterDraft?,
    autoAppliedDraft: TypedAttributeFilterDraft?,
    defaultSummary: String,
    availableValueCount: Int,
    hasScopedContext: Boolean,
): String = when {
    explicitDraft != null -> typedFilterDraftSummary(explicitDraft)
    queryBackedDraft != null -> typedFilterDraftSummary(queryBackedDraft)
    autoAppliedDraft != null -> "${typedFilterDraftSummary(autoAppliedDraft)} · авто"
    hasScopedContext && availableValueCount > 0 -> formatScopedFacetAvailabilitySummary(availableValueCount)
    else -> defaultSummary
}

private fun formatScopedFacetAvailabilitySummary(availableValueCount: Int): String {
    val safeCount = availableValueCount.coerceAtLeast(0)
    val mod10 = safeCount % 10
    val mod100 = safeCount % 100
    val noun = when {
        mod10 == 1 && mod100 != 11 -> "вариант"
        mod10 in 2..4 && mod100 !in 12..14 -> "варианта"
        else -> "вариантов"
    }
    return "$safeCount $noun"
}

private fun summarizeSelectedValues(
    values: List<String>,
    emptyLabel: String,
): String {
    val normalized = values
        .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
    if (normalized.isEmpty()) return emptyLabel
    val first = normalized.first()
    return if (normalized.size == 1) first else "$first...+${normalized.size}"
}

private fun FilterState.locationSummary(): String {
    val locationText = location?.trim().orEmpty()
    if (locationText.isBlank()) return "Любое"
    val safeRadius = radiusKm?.coerceIn(NEARBY_RADIUS_PRESETS.first(), NEARBY_RADIUS_PRESETS.last())
    return safeRadius?.let { "$locationText · $it км" } ?: locationText
}

private fun buildAppliedFilterChips(
    filters: FilterState,
    typedFacetTitlesByRuntimeKey: Map<String, String>,
    categoriesByCode: Map<String, Category>,
    effectiveTypedAttributeFilters: Map<String, TypedAttributeFilterDraft> = filters.typedAttributeFilters,
): List<AppliedFilterChipUi> {
    val chips = mutableListOf<AppliedFilterChipUi>()

    if (filters.sellerId != null) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Seller,
            label = "Продавец",
            value = filters.sellerName?.takeIf { it.isNotBlank() } ?: "Профиль ${filters.sellerId}",
            removable = false,
        )
    }

    if (!filters.categoryCode.isNullOrBlank()) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.Category,
            label = "Категория",
            value = filters.categorySummary(categoriesByCode),
            icon = Icons.Outlined.AccountTree,
            showLabel = false,
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

    if (filters.deliverableOnly) {
        chips += AppliedFilterChipUi(
            key = AppliedChipKey.PurchaseFormat,
            label = "Доставка",
            value = "Есть доставка",
        )
    }

    effectiveTypedAttributeFilters
        .toSortedMap()
        .forEach { (facetKey, draft) ->
            val normalizedKey = facetKey.trim()
            if (normalizedKey.isBlank()) return@forEach
            val title = typedFacetTitlesByRuntimeKey[normalizedKey.lowercase(Locale.ROOT)]
                ?: normalizedKey
                    .replace('_', ' ')
                    .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
            chips += AppliedFilterChipUi(
                key = "${AppliedChipKey.TypedPrefix}$normalizedKey",
                label = title,
                value = typedFilterDraftSummary(draft),
                removable = filters.typedAttributeFilters.draftForRuntimeKey(normalizedKey) != null,
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
    val runtimeKeyByTypedKey: Map<String, String> = emptyMap(),
    val categoriesByCode: Map<String, Category> = emptyMap(),
    val availableSellerTrustSignals: Set<SellerTrustSignal> = SellerTrustSignal.entries.toSet(),
    val typedFacetUniverse: ResultsTypedFacetUniverse = ResultsTypedFacetUniverse(),
    val runtimeAttributeFacets: Map<String, List<ValueFacet>> = emptyMap(),
)

private fun buildResultsDependencyContext(
    facetDefinitions: List<FacetDefinition>,
    categoriesByCode: Map<String, Category> = emptyMap(),
    availableSellerTrustSignals: Set<SellerTrustSignal>,
    typedFacetUniverse: ResultsTypedFacetUniverse = ResultsTypedFacetUniverse(),
    runtimeAttributeFacets: Map<String, List<ValueFacet>> = emptyMap(),
): FilterDependencyContext = FilterDependencyContext(
    allowedTypedFacetKeys = buildAllowedTypedFacetKeys(facetDefinitions),
    runtimeKeyByTypedKey = buildTypedFacetRuntimeKeyMap(facetDefinitions),
    categoriesByCode = categoriesByCode,
    availableSellerTrustSignals = availableSellerTrustSignals,
    typedFacetUniverse = typedFacetUniverse,
    runtimeAttributeFacets = runtimeAttributeFacets,
)

private data class ScopedTypedFilterNormalization(
    val filters: Map<String, TypedAttributeFilterDraft>,
    val invalidatedKeys: Set<String> = emptySet(),
)

private fun normalizeTypedAttributeFiltersForScope(
    typedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
    typedFacetUniverse: ResultsTypedFacetUniverse,
    runtimeAttributeFacets: Map<String, List<ValueFacet>>,
): ScopedTypedFilterNormalization {
    if (typedAttributeFilters.isEmpty()) {
        return ScopedTypedFilterNormalization(filters = emptyMap())
    }
    val normalized = LinkedHashMap<String, TypedAttributeFilterDraft>()
    val invalidated = linkedSetOf<String>()
    typedAttributeFilters.forEach { (rawRuntimeKey, draft) ->
        val runtimeKey = rawRuntimeKey.trim().lowercase(Locale.ROOT)
        if (runtimeKey.isBlank()) return@forEach
        val sanitizedDraft = sanitizeTypedAttributeFilterDraftForScope(
            runtimeKey = runtimeKey,
            draft = draft,
            universeEntry = typedFacetUniverse.entriesByRuntimeKey[runtimeKey],
            runtimeAttributeFacets = runtimeAttributeFacets,
        )
        if (sanitizedDraft == null) {
            invalidated += runtimeKey
        } else {
            normalized[runtimeKey] = sanitizedDraft
        }
    }
    return ScopedTypedFilterNormalization(
        filters = normalized,
        invalidatedKeys = invalidated,
    )
}

private fun sanitizeTypedAttributeFilterDraftForScope(
    runtimeKey: String,
    draft: TypedAttributeFilterDraft,
    universeEntry: ResultsTypedFacetUniverseEntry?,
    runtimeAttributeFacets: Map<String, List<ValueFacet>>,
): TypedAttributeFilterDraft? {
    val selectedValues = when (draft.op) {
        TypedAttributeOperator.EQ -> listOfNotNull(
            draft.value.trim().takeIf { value -> value.isNotEmpty() },
        )

        TypedAttributeOperator.IN -> parseFacetCsv(draft.valuesCsv)
        else -> return draft
    }
    if (selectedValues.isEmpty()) return draft

    val scopedCandidates = buildList {
        addAll(
            resolveAvailableFacetValues(
                runtimeKey = runtimeKey,
                runtimeAttributeFacets = runtimeAttributeFacets,
                universeEntry = universeEntry,
            ),
        )
        addAll(universeEntry?.knownValues.orEmpty())
        addAll(universeEntry?.seedAvailableValues.orEmpty())
        if (universeEntry?.valueType == FacetDataType.BOOL) {
            add("true")
            add("false")
        }
    }
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }

    if (scopedCandidates.isEmpty()) return draft

    val matcher = if (runtimeKey == "model") {
        ::modelRequestedValueMatchesKnownCandidate
    } else {
        ::requestedFacetValueMatchesKnownCandidate
    }
    val matchedValues = selectedValues
        .mapNotNull { selectedValue ->
            scopedCandidates.firstOrNull { candidate ->
                matcher(
                    selectedValue,
                    candidate,
                    universeEntry?.knownValueAliases?.get(candidate).orEmpty(),
                )
            }
        }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }

    if (matchedValues.isEmpty()) return null

    return when (draft.op) {
        TypedAttributeOperator.EQ -> draft.copy(value = matchedValues.first())
        TypedAttributeOperator.IN -> draft.copy(valuesCsv = matchedValues.joinToString(", "))
        else -> draft
    }
}

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

    val sanitizedBrandSelection = sanitizeSelectedBrandsForCategory(
        selectedBrands = next.brands,
        interpretedBrandSeed = next.interpretedBrandSeed,
        categoryCode = next.categoryCode,
        categoriesByCode = context.categoriesByCode,
    )
    if (
        sanitizedBrandSelection.brands != next.brands ||
        sanitizedBrandSelection.interpretedBrandSeed != next.interpretedBrandSeed
    ) {
        if (sanitizedBrandSelection.brands != next.brands) invalidated += "brand"
        next = next.copy(
            brands = sanitizedBrandSelection.brands,
            interpretedBrandSeed = sanitizedBrandSelection.interpretedBrandSeed,
        )
    }

    if (next.typedAttributeFilters.isNotEmpty()) {
        val normalizedTyped = next.typedAttributeFilters
            .mapNotNull { (rawKey, value) ->
                val normalizedKey = rawKey.trim().lowercase()
                if (normalizedKey.isBlank()) {
                    null
                } else {
                    val runtimeKey = context.runtimeKeyByTypedKey[normalizedKey] ?: normalizedKey
                    runtimeKey to value
                }
            }
            .toMap(LinkedHashMap())
        val filteredTyped = if (context.allowedTypedFacetKeys.isEmpty()) {
            normalizedTyped
        } else {
            normalizedTyped.filterKeys { facetKey -> facetKey in context.allowedTypedFacetKeys }
        }
        val scopedNormalization = normalizeTypedAttributeFiltersForScope(
            typedAttributeFilters = filteredTyped,
            typedFacetUniverse = context.typedFacetUniverse,
            runtimeAttributeFacets = context.runtimeAttributeFacets,
        )
        val sanitizedTyped = scopedNormalization.filters
        if (scopedNormalization.invalidatedKeys.isNotEmpty()) {
            invalidated += "typed_attributes"
        }
        if (sanitizedTyped != next.typedAttributeFilters) {
            invalidated += "typed_attributes"
            next = next.copy(typedAttributeFilters = sanitizedTyped)
        }
    }

    if (next.purchaseFormat != PurchaseFormat.All) {
        invalidated += "purchase_format"
        next = next.copy(purchaseFormat = PurchaseFormat.All)
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

    if (next.sellerTrustSignals.isNotEmpty() || next.sellerTrustPreset != SellerTrustPreset.Any) {
        invalidated += "seller_trust"
        next = next.copy(
            // Legacy seller trust is intentionally inert for results filtering.
            sellerTrustSignals = emptySet(),
            sellerTrustPreset = SellerTrustPreset.Any,
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
        interpretedCategoryCode = null,
        interpretedFacetCollectionCode = null,
        interpretedFacetPresetCode = null,
    )
    if (!categoryChanged) {
        return next.normalizeWithDependencies(context)
    }

    val invalidated = linkedSetOf<String>()
    if (next.brands.isNotEmpty() || !next.interpretedBrandSeed.isNullOrBlank()) invalidated += "brand"
    if (next.conditions.isNotEmpty()) invalidated += "condition"
    if (next.priceMin != null || next.priceMax != null) invalidated += "price"
    if (next.deliverableOnly) invalidated += "deliverable_only"
    if (!next.facetCollectionCode.isNullOrBlank()) invalidated += "facet_collection"
    if (!next.facetPresetCode.isNullOrBlank()) invalidated += "facet_preset"
    if (next.presetAttributes.isNotEmpty()) invalidated += "preset_attributes"
    if (next.typedAttributeFilters.isNotEmpty()) invalidated += "typed_attributes"

    next = next.copy(
        brands = emptySet(),
        interpretedBrandSeed = null,
        interpretedConditionSeed = null,
        conditions = emptySet(),
        priceMin = null,
        priceMax = null,
        deliverableOnly = false,
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

    chipKey == AppliedChipKey.Brand -> clearBrandSelection().normalizeWithDependencies(context)
    chipKey == AppliedChipKey.Price -> copy(
        priceMin = null,
        priceMax = null,
    ).normalizeWithDependencies(context)

    chipKey == AppliedChipKey.Condition -> clearConditionSelection().normalizeWithDependencies(context)
    chipKey == AppliedChipKey.PurchaseFormat -> copy(
        purchaseFormat = PurchaseFormat.All,
        deliverableOnly = false,
    ).normalizeWithDependencies(context)
    chipKey == AppliedChipKey.SellerTrust -> copy(
        sellerTrustPreset = SellerTrustPreset.Any,
        sellerTrustSignals = emptySet(),
    ).normalizeWithDependencies(context)

    chipKey == AppliedChipKey.Location -> copy(
        location = null,
        radiusKm = null,
        centerLat = null,
        centerLon = null,
    ).normalizeWithDependencies(context)

    chipKey.startsWith(AppliedChipKey.TypedPrefix) -> {
        val facetKey = chipKey.removePrefix(AppliedChipKey.TypedPrefix).trim().lowercase(Locale.ROOT)
        val runtimeKey = context.runtimeKeyByTypedKey[facetKey] ?: facetKey
        val existingKey = typedAttributeFilters.keys.firstOrNull { key ->
            key.trim().equals(runtimeKey, ignoreCase = true)
        }
        if (runtimeKey.isBlank() || existingKey == null) {
            normalizeWithDependencies(context)
        } else {
            withTypedAttributeDraft(runtimeKey = runtimeKey, draft = null).normalizeWithDependencies(context)
        }
    }

    else -> normalizeWithDependencies(context)
}

internal enum class ResultsFacetFilterType {
    Brand,
    PriceRange,
    Condition,
    DeliveryChannel,
    SellerTrust,
    TypedAttribute,
    Unsupported,
}

internal data class ResultsFacetFilter(
    val facetKey: String,
    val runtimeKey: String,
    val title: String,
    val type: ResultsFacetFilterType,
)

private val defaultResultsFacetFilters = listOf(
    ResultsFacetFilter(
        facetKey = "brand",
        runtimeKey = "brand",
        title = "Бренд",
        type = ResultsFacetFilterType.Brand,
    ),
    ResultsFacetFilter(
        facetKey = "price",
        runtimeKey = "price",
        title = "Цена",
        type = ResultsFacetFilterType.PriceRange,
    ),
    ResultsFacetFilter(
        facetKey = "condition",
        runtimeKey = "condition",
        title = "Состояние",
        type = ResultsFacetFilterType.Condition,
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

internal fun scopeFacetDefinitionsForCategory(
    definitions: List<FacetDefinition>,
    categoryCode: String?,
): List<FacetDefinition> {
    val normalizedCategoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return definitions
        .asSequence()
        .filter { definition ->
            definition.appliesToCategoryCodes.any { code ->
                code.equals(normalizedCategoryCode, ignoreCase = true)
            }
        }
        .distinctBy { definition -> definition.facetKey.trim().lowercase() }
        .toList()
}

internal fun filterFacetDefinitionsForPresentation(
    definitions: List<FacetDefinition>,
    categoryCode: String?,
): List<FacetDefinition> {
    val profile = CatalogFacetPresentationProfiles.resolve(categoryCode) ?: return definitions
    val allowedTypedFacetKeys = profile.visibleTypedFacetKeys()
    if (allowedTypedFacetKeys.isEmpty() && profile.hiddenTypedFacetKeys.isEmpty()) return definitions

    return definitions
        .asSequence()
        .filterNot { definition ->
            val normalizedKey = definition.normalizedFacetKey()
            val normalizedRuntimeKey = definition.normalizedRuntimeKey()
            normalizedKey in profile.hiddenTypedFacetKeys ||
                normalizedRuntimeKey in profile.hiddenTypedFacetKeys
        }
        .filter { definition ->
            val normalizedKey = definition.normalizedFacetKey()
            val normalizedRuntimeKey = definition.normalizedRuntimeKey()
            if (normalizedKey in systemFacetKeys || normalizedRuntimeKey in systemFacetKeys) {
                true
            } else {
                allowedTypedFacetKeys.isEmpty() ||
                    normalizedKey in allowedTypedFacetKeys ||
                    normalizedRuntimeKey in allowedTypedFacetKeys
            }
        }
        .toList()
}

private fun CatalogFacetPresentationProfile.visibleTypedFacetKeys(): Set<String> =
    sequenceOf(
        mainTypedFacetKeys,
        additionalTypedFacetKeys,
        orderedTypedFacetKeys,
        liveOnlyTypedFacetKeys.toList(),
        noticePriorityTypedFacetKeys,
        requiresBrandContextTypedFacetKeys.toList(),
    )
        .flatMap { keys -> keys.asSequence() }
        .map { key -> key.trim().lowercase(Locale.ROOT) }
        .filter { key -> key.isNotEmpty() }
        .filterNot { key -> key in hiddenTypedFacetKeys }
        .toCollection(LinkedHashSet())

internal fun buildFacetUiFilters(
    definitions: List<FacetDefinition>,
    categoryCode: String? = null,
): List<ResultsFacetFilter> {
    val localeTag = Locale.getDefault().toLanguageTag()
    val profile = CatalogFacetPresentationProfiles.resolve(categoryCode)
    val hiddenSystemFacetKeys = profile?.hiddenSystemFacetKeys.orEmpty()
    val hiddenTypedFacetKeys = profile?.hiddenTypedFacetKeys.orEmpty()
    val pinnedSystemFacetKeys = profile?.pinnedSystemFacetKeys.orEmpty()
    val orderedTypedFacetKeys = profile?.orderedTypedFacetKeys.orEmpty()
    val mapped = filterFacetDefinitionsForPresentation(definitions, categoryCode)
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .filterNot { definition ->
            val normalizedKey = definition.normalizedFacetKey()
            val normalizedRuntimeKey = definition.normalizedRuntimeKey()
            normalizedKey in hiddenSystemFacetKeys ||
                normalizedRuntimeKey in hiddenTypedFacetKeys ||
                normalizedKey == "delivery_channel" ||
                normalizedRuntimeKey == "delivery_channel" ||
                normalizedKey == "delivery" ||
                normalizedRuntimeKey == "delivery" ||
                normalizedKey == "purchase_format" ||
                normalizedRuntimeKey == "purchase_format" ||
                // Legacy seller trust stays out of the active results filter hub.
                normalizedKey == "seller_trust" ||
                normalizedRuntimeKey == "seller_trust"
        }
        .sortedWith(
            compareBy<FacetDefinition> { definition -> definition.ui.order }
                .thenBy { definition -> definition.displayTitle(locale = localeTag).lowercase(Locale.ROOT) },
        )
        .map { definition ->
            val normalizedKey = definition.normalizedFacetKey()
            val runtimeKey = definition.normalizedRuntimeKey()
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
                runtimeKey = runtimeKey,
                title = definition.displayTitle(locale = localeTag),
                type = type,
            )
        }
        .distinctBy { facetFilter ->
            if (facetFilter.type == ResultsFacetFilterType.TypedAttribute) facetFilter.runtimeKey else facetFilter.facetKey
        }
        .toList()
    val defaultFilters = defaultResultsFacetFilters.filterNot { facetFilter ->
        facetFilter.facetKey.lowercase(Locale.ROOT) in hiddenSystemFacetKeys
    }
    if (mapped.isEmpty()) return defaultFilters
    return (mapped + defaultFilters)
        .distinctBy { facetFilter ->
            if (facetFilter.type == ResultsFacetFilterType.TypedAttribute) facetFilter.runtimeKey else facetFilter.facetKey
        }
        .sortedWith(
            compareBy<ResultsFacetFilter> { facetFilter ->
                if (facetFilter.type == ResultsFacetFilterType.TypedAttribute) 1 else 0
            }
                .thenBy { facetFilter ->
                    resultsFacetSystemPriority(
                        facetFilter = facetFilter,
                        pinnedSystemFacetKeys = pinnedSystemFacetKeys,
                    )
                }
                .thenBy { facetFilter ->
                    resultsFacetTypedPriority(
                        facetFilter = facetFilter,
                        orderedTypedFacetKeys = orderedTypedFacetKeys,
                    )
                }
                .thenBy { facetFilter -> facetFilter.title.lowercase(Locale.ROOT) },
        )
}

private fun resultsFacetSystemPriority(
    facetFilter: ResultsFacetFilter,
    pinnedSystemFacetKeys: List<String>,
): Int {
    if (facetFilter.type == ResultsFacetFilterType.TypedAttribute) return Int.MAX_VALUE
    val normalizedKey = facetFilter.facetKey.trim().lowercase(Locale.ROOT)
    val pinnedOrder = pinnedSystemFacetKeys
    val pinnedIndex = pinnedOrder.indexOf(normalizedKey)
    if (pinnedIndex >= 0) return pinnedIndex
    val fallbackOrder = listOf("brand", "price", "condition")
    val fallbackIndex = fallbackOrder.indexOf(normalizedKey)
    return if (fallbackIndex >= 0) pinnedOrder.size + fallbackIndex else pinnedOrder.size + fallbackOrder.size
}

private fun resultsFacetTypedPriority(
    facetFilter: ResultsFacetFilter,
    orderedTypedFacetKeys: List<String>,
): Int {
    if (facetFilter.type != ResultsFacetFilterType.TypedAttribute) return Int.MAX_VALUE
    val normalizedRuntimeKey = facetFilter.runtimeKey.trim().lowercase(Locale.ROOT)
    val orderedKeys = orderedTypedFacetKeys
    val orderedIndex = orderedKeys.indexOf(normalizedRuntimeKey)
    return if (orderedIndex >= 0) orderedIndex else orderedKeys.size
}

private fun buildAllowedTypedFacetKeys(definitions: List<FacetDefinition>): Set<String> =
    definitions
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .map { definition -> definition.normalizedRuntimeKey() }
        .filter { facetKey -> facetKey.isNotBlank() }
        .filterNot { facetKey -> facetKey in systemFacetKeys }
        .toCollection(LinkedHashSet())

private fun buildTypedFacetRuntimeKeyMap(definitions: List<FacetDefinition>): Map<String, String> =
    definitions
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .flatMap { definition ->
            sequenceOf(
                definition.normalizedFacetKey(),
                definition.normalizedRuntimeKey(),
            )
                .filter { key -> key.isNotBlank() && key !in systemFacetKeys }
                .map { key -> key to definition.normalizedRuntimeKey() }
        }
        .toMap(LinkedHashMap())

private fun buildTypedFacetTitlesByRuntimeKey(definitions: List<FacetDefinition>): Map<String, String> {
    val localeTag = Locale.getDefault().toLanguageTag()
    return definitions
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .map { definition -> definition.normalizedRuntimeKey() to definition.displayTitle(locale = localeTag) }
        .filter { (runtimeKey, _) -> runtimeKey.isNotBlank() && runtimeKey !in systemFacetKeys }
        .toMap(LinkedHashMap())
}

private suspend fun loadTypedFacetUniverse(
    catalogRepository: CatalogReadRepository,
    liveValuesRepository: CatalogLiveValuesRepository,
    categoryCode: String?,
    facetDefinitions: List<FacetDefinition>,
    lookupBrand: String? = null,
    lookupModel: String? = null,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
): ResultsTypedFacetUniverse = coroutineScope {
    val normalizedCategoryCode = categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() }
        ?: return@coroutineScope ResultsTypedFacetUniverse()
    val normalizedScopes = normalizeResultsTypedFacetLookupScopes(
        lookupScopes = lookupScopes,
        fallbackBrand = lookupBrand,
        fallbackModel = lookupModel,
    )
    val primaryScope = normalizedScopes.singleOrNull()
    val normalizedBrand = primaryScope?.brand
    val normalizedModel = primaryScope?.model
    val localeTag = Locale.getDefault().toLanguageTag()
    val presentationFacetDefinitions = filterFacetDefinitionsForPresentation(
        definitions = facetDefinitions,
        categoryCode = normalizedCategoryCode,
    )
    val attributeCodes = presentationFacetDefinitions
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .mapNotNull { definition ->
            val facetKey = definition.normalizedRuntimeKey()
            if (facetKey.isBlank() || facetKey in systemFacetKeys) {
                null
            } else {
                definition.attributeCode
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { value -> value.isNotEmpty() }
                    ?: facetKey
            }
        }
        .distinct()
        .toList()
    val spec = runCatching {
        catalogRepository.getCategoryEffectiveSpec(
            categoryCode = normalizedCategoryCode,
            brand = normalizedBrand,
            model = normalizedModel,
        )
    }.getOrNull()
    val liveSnapshots = when {
        normalizedScopes.isEmpty() -> listOfNotNull(
            loadTypedFacetLiveSnapshot(
                liveValuesRepository = liveValuesRepository,
                categoryCode = normalizedCategoryCode,
                localeTag = localeTag,
                attributeCodes = attributeCodes,
                scope = null,
            ),
        )

        normalizedScopes.size == 1 -> listOfNotNull(
            loadTypedFacetLiveSnapshot(
                liveValuesRepository = liveValuesRepository,
                categoryCode = normalizedCategoryCode,
                localeTag = localeTag,
                attributeCodes = attributeCodes,
                scope = normalizedScopes.first(),
            ),
        )

        else -> normalizedScopes
            .map { scope ->
                async {
                    loadTypedFacetLiveSnapshot(
                        liveValuesRepository = liveValuesRepository,
                        categoryCode = normalizedCategoryCode,
                        localeTag = localeTag,
                        attributeCodes = attributeCodes,
                        scope = scope,
                    )
                }
            }
            .awaitAll()
            .filterNotNull()
    }
    val liveSnapshot = mergeTypedFacetLiveSnapshots(liveSnapshots)
    val observedValuesByAttributeCode = liveSnapshot
        ?.valuesByAttributeCode
        .orEmpty()
        .mapKeys { (attributeCode, _) -> attributeCode.trim().lowercase(Locale.ROOT) }
    val runtimeKnownValuesByAttributeCode = liveSnapshot
        ?.knownValuesByAttributeCode
        .orEmpty()
        .mapKeys { (attributeCode, _) -> attributeCode.trim().lowercase(Locale.ROOT) }
    val runtimeKnownValueAliasesByAttributeCode = liveSnapshot
        ?.knownValueAliasesByAttributeCode
        .orEmpty()
        .mapKeys { (attributeCode, _) -> attributeCode.trim().lowercase(Locale.ROOT) }
    val liveModelOptions = liveSnapshot
        ?.modelOptions
        .orEmpty()
    val entriesByFacetKey = LinkedHashMap<String, ResultsTypedFacetUniverseEntry>()
    presentationFacetDefinitions
        .asSequence()
        .filter { definition -> definition.isActiveForToday() }
        .filterNot { definition -> definition.ui.hidden }
        .forEach { definition ->
            val facetKey = definition.normalizedRuntimeKey()
            if (facetKey.isBlank() || facetKey in systemFacetKeys) return@forEach
            val attributeCode = definition.attributeCode
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { value -> value.isNotEmpty() }
                ?: facetKey

            val observedValues = resolveTypedFacetObservedValues(
                attributeCode = attributeCode,
                observedValuesByAttributeCode = observedValuesByAttributeCode,
                liveModelOptions = liveModelOptions,
            )
            val knownValues = loadKnownTypedFacetValues(
                spec = spec,
                categoryCode = normalizedCategoryCode,
                lookupBrand = normalizedBrand,
                lookupModel = normalizedModel,
                lookupScopes = normalizedScopes,
                runtimeKey = facetKey,
                attributeCode = attributeCode,
                liveModelOptions = liveModelOptions,
                runtimeKnownValuesByAttributeCode = runtimeKnownValuesByAttributeCode,
            )
            val knownValueAliases = loadKnownTypedFacetValueAliases(
                spec = spec,
                categoryCode = normalizedCategoryCode,
                lookupBrand = normalizedBrand,
                lookupModel = normalizedModel,
                lookupScopes = normalizedScopes,
                runtimeKey = facetKey,
                attributeCode = attributeCode,
                runtimeKnownValuesByAttributeCode = runtimeKnownValuesByAttributeCode,
                runtimeKnownValueAliasesByAttributeCode = runtimeKnownValueAliasesByAttributeCode,
            )
            val fallbackSeedValues = when {
                knownValues.isNotEmpty() -> knownValues
                definition.valueType == FacetDataType.BOOL -> listOf("true", "false")
                else -> emptyList()
            }
            val seedAvailableValues = observedValues.ifEmpty { fallbackSeedValues }
            val isClosedSet = knownValues.isNotEmpty() ||
                definition.valueType == FacetDataType.BOOL
            entriesByFacetKey[facetKey] = ResultsTypedFacetUniverseEntry(
                runtimeKey = facetKey,
                title = definition.displayTitle(locale = localeTag),
                valueType = definition.valueType,
                knownValues = knownValues,
                knownValueAliases = knownValueAliases,
                seedAvailableValues = seedAvailableValues,
                seedAvailabilityKnown = seedAvailableValues.isNotEmpty(),
                isClosedSet = isClosedSet,
            )
        }
    ResultsTypedFacetUniverse(entriesByRuntimeKey = entriesByFacetKey)
}

private suspend fun loadTypedFacetLiveSnapshot(
    liveValuesRepository: CatalogLiveValuesRepository,
    categoryCode: String,
    localeTag: String,
    attributeCodes: List<String>,
    scope: ResultsTypedFacetLookupScope?,
): CatalogLiveValuesSnapshot? = runCatching {
    liveValuesRepository.getLiveValues(
        CatalogLiveValuesRequest(
            categoryCode = categoryCode,
            brand = scope?.brand,
            model = scope?.model,
            localeTag = localeTag,
            attributeCodes = attributeCodes,
        ),
    )
}.getOrNull()

private fun mergeTypedFacetLiveSnapshots(
    snapshots: List<CatalogLiveValuesSnapshot>,
): CatalogLiveValuesSnapshot? {
    if (snapshots.isEmpty()) return null
    return CatalogLiveValuesSnapshot(
        valuesByAttributeCode = mergeTypedFacetSnapshotValueMaps(snapshots.map { snapshot -> snapshot.valuesByAttributeCode }),
        knownValuesByAttributeCode = mergeTypedFacetSnapshotValueMaps(snapshots.map { snapshot -> snapshot.knownValuesByAttributeCode }),
        knownValueAliasesByAttributeCode = mergeTypedFacetSnapshotAliasMaps(snapshots.map { snapshot -> snapshot.knownValueAliasesByAttributeCode }),
        brandOptions = snapshots
            .asSequence()
            .flatMap { snapshot -> snapshot.brandOptions.asSequence() }
            .normalizeResultsTypedFacetValues(),
        modelOptions = snapshots
            .asSequence()
            .flatMap { snapshot -> snapshot.modelOptions.asSequence() }
            .normalizeResultsTypedFacetValues(),
    )
}

private fun mergeTypedFacetSnapshotValueMaps(
    valueMaps: List<Map<String, List<String>>>,
): Map<String, List<String>> {
    val merged = LinkedHashMap<String, MutableList<String>>()
    valueMaps.forEach { valuesByKey ->
        valuesByKey.forEach { (rawKey, rawValues) ->
            val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
            if (normalizedKey.isEmpty()) return@forEach
            val bucket = merged.getOrPut(normalizedKey) { mutableListOf() }
            rawValues.forEach { rawValue ->
                val value = rawValue.trim()
                if (value.isEmpty()) return@forEach
                if (bucket.none { existing -> existing.equals(value, ignoreCase = true) }) {
                    bucket += value
                }
            }
        }
    }
    return merged.mapValues { (_, values) -> values.toList() }
}

private fun mergeTypedFacetSnapshotAliasMaps(
    aliasMaps: List<Map<String, Map<String, List<String>>>>,
): Map<String, Map<String, List<String>>> {
    val merged = LinkedHashMap<String, MutableMap<String, MutableList<String>>>()
    aliasMaps.forEach { aliasesByAttribute ->
        aliasesByAttribute.forEach { (rawKey, aliasesByValue) ->
            val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
            if (normalizedKey.isEmpty()) return@forEach
            val attributeBucket = merged.getOrPut(normalizedKey) { LinkedHashMap() }
            aliasesByValue.forEach { (rawValue, rawAliases) ->
                val canonicalValue = rawValue.trim()
                if (canonicalValue.isEmpty()) return@forEach
                val valueKey = attributeBucket.keys.firstOrNull { value ->
                    value.equals(canonicalValue, ignoreCase = true)
                } ?: canonicalValue
                val aliasBucket = attributeBucket.getOrPut(valueKey) { mutableListOf() }
                rawAliases.forEach { rawAlias ->
                    val alias = rawAlias.trim()
                    if (alias.isEmpty()) return@forEach
                    if (aliasBucket.none { existing -> existing.equals(alias, ignoreCase = true) }) {
                        aliasBucket += alias
                    }
                }
            }
        }
    }
    return merged.mapValues { (_, aliasesByValue) ->
        aliasesByValue.mapValues { (_, aliases) -> aliases.toList() }
    }
}

private fun resolveTypedFacetLookupBrand(filters: FilterState): String? =
    resolveTypedFacetLookupBrand(
        selectedBrands = filters.brands,
        query = filters.query,
    )

private fun resolveTypedFacetLookupBrand(
    selectedBrands: Set<String>,
    query: NormalizedQuery?,
): String? =
    when {
        selectedBrands.size == 1 -> selectedBrands.first().trim().takeIf { value -> value.isNotEmpty() }
        selectedBrands.isNotEmpty() -> null
        else -> query?.brand?.trim()?.takeIf { value -> value.isNotEmpty() }
    }

private fun resolveTypedFacetLookupModel(filters: FilterState): String? =
    resolveTypedFacetLookupModel(
        selectedBrands = filters.brands,
        query = filters.query,
    )

private fun resolveTypedFacetLookupModel(
    selectedBrands: Set<String>,
    query: NormalizedQuery?,
): String? {
    if (selectedBrands.size > 1) return null
    val queryModel = query?.model?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return null
    val queryBrand = query.brand?.trim()?.takeIf { value -> value.isNotEmpty() }
    val effectiveBrand = resolveTypedFacetLookupBrand(
        selectedBrands = selectedBrands,
        query = query,
    )
    if (effectiveBrand != null && queryBrand != null && !queryBrand.equals(effectiveBrand, ignoreCase = true)) {
        return null
    }
    return queryModel
}

internal fun buildResultsTypedFacetLookupScopes(
    selectedBrands: Set<String>,
    query: NormalizedQuery?,
    typedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
    categoryCode: String? = null,
): List<ResultsTypedFacetLookupScope> {
    val explicitModelSelections = selectedTypedFacetValues(
        typedAttributeFilters = typedAttributeFilters,
        facetKey = "model",
    )
    val normalizedSelectedBrands = selectedBrands
        .asSequence()
        .map { brand -> brand.trim() }
        .filter { brand -> brand.isNotEmpty() }
        .distinctBy { brand -> brand.lowercase(Locale.ROOT) }
        .toList()
    val singleBrand = resolveTypedFacetLookupBrand(
        selectedBrands = selectedBrands,
        query = query,
    )
    val singleModel = resolveTypedFacetLookupModel(
        selectedBrands = selectedBrands,
        query = query,
    )

    if (explicitModelSelections.isNotEmpty()) {
        val modelScopes = explicitModelSelections
            .mapNotNull { model ->
                resolveTypedFacetLookupScopeForModel(
                    model = model,
                    categoryCode = categoryCode,
                    preferredBrands = normalizedSelectedBrands,
                    fallbackBrand = singleBrand ?: query?.brand,
                )
            }
            .toMutableList()
        val coveredBrands = modelScopes
            .mapNotNull { scope -> scope.brand?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .map { brand -> brand.lowercase(Locale.ROOT) }
            .toSet()
        normalizedSelectedBrands
            .filterNot { brand -> brand.lowercase(Locale.ROOT) in coveredBrands }
            .forEach { brand ->
                modelScopes += ResultsTypedFacetLookupScope(brand = brand)
            }
        if (modelScopes.isNotEmpty()) {
            return normalizeResultsTypedFacetLookupScopes(modelScopes)
        }
    }

    return when {
        normalizedSelectedBrands.size > 1 -> normalizedSelectedBrands.map { brand ->
            ResultsTypedFacetLookupScope(brand = brand)
        }

        singleBrand != null || singleModel != null -> listOf(
            ResultsTypedFacetLookupScope(
                brand = singleBrand,
                model = singleModel,
            ),
        )

        else -> emptyList()
    }
}

private fun resolveTypedFacetLookupScopes(filters: FilterState): List<ResultsTypedFacetLookupScope> =
    buildResultsTypedFacetLookupScopes(
        selectedBrands = filters.brands,
        query = filters.query,
        typedAttributeFilters = filters.typedAttributeFilters,
        categoryCode = filters.categoryCode,
    )

private fun FilterState.selectedTypedFacetValues(facetKey: String): List<String> =
    selectedTypedFacetValues(
        typedAttributeFilters = typedAttributeFilters,
        facetKey = facetKey,
    )

private fun selectedTypedFacetValues(
    typedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
    facetKey: String,
): List<String> {
    val normalizedKey = facetKey.trim().lowercase(Locale.ROOT)
    if (normalizedKey.isBlank()) return emptyList()
    val draft = typedAttributeFilters[normalizedKey]
        ?: typedAttributeFilters.entries.firstOrNull { (key, _) ->
            key.trim().equals(normalizedKey, ignoreCase = true)
        }?.value
        ?: return emptyList()
    return when (draft.op) {
        TypedAttributeOperator.EQ -> listOfNotNull(
            draft.value.trim().takeIf { value -> value.isNotEmpty() },
        )

        TypedAttributeOperator.IN -> parseFacetCsv(draft.valuesCsv)
        else -> emptyList()
    }
}

private fun resolveTypedFacetLookupScopeForModel(
    model: String,
    categoryCode: String?,
    preferredBrands: List<String>,
    fallbackBrand: String? = null,
): ResultsTypedFacetLookupScope? {
    val normalizedModel = model.trim().takeIf { value -> value.isNotEmpty() } ?: return null
    val normalizedCategoryCode = categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() }
    val normalizedPreferredBrands = preferredBrands
        .asSequence()
        .map { brand -> brand.trim() }
        .filter { brand -> brand.isNotEmpty() }
        .distinctBy { brand -> brand.lowercase(Locale.ROOT) }
        .toList()
    val fallbackNormalizedBrand = fallbackBrand?.trim()?.takeIf { value -> value.isNotEmpty() }

    val scopedMatch = CatalogCanonicalModelRegistry.models()
        .asSequence()
        .filter { entry ->
            normalizedCategoryCode == null ||
                entry.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)
        }
        .filter { entry ->
            normalizedPreferredBrands.isEmpty() ||
                normalizedPreferredBrands.any { brand ->
                    entry.brandCanonical.equals(brand, ignoreCase = true)
                }
        }
        .firstOrNull { entry ->
            entry.canonicalModel.equals(normalizedModel, ignoreCase = true) ||
                entry.modelAliases.any { alias -> alias.equals(normalizedModel, ignoreCase = true) }
        }
        ?: CatalogCanonicalModelRegistry.models()
            .asSequence()
            .filter { entry ->
                normalizedCategoryCode == null ||
                    entry.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)
            }
            .firstOrNull { entry ->
                entry.canonicalModel.equals(normalizedModel, ignoreCase = true) ||
                    entry.modelAliases.any { alias -> alias.equals(normalizedModel, ignoreCase = true) }
            }

    val resolvedBrand = scopedMatch?.brandCanonical
        ?: normalizedPreferredBrands.singleOrNull()
        ?: fallbackNormalizedBrand
        ?: return null
    val resolvedModel = scopedMatch?.canonicalModel ?: normalizedModel
    return ResultsTypedFacetLookupScope(
        brand = resolvedBrand,
        model = resolvedModel,
    )
}

internal fun normalizeResultsTypedFacetLookupScopes(
    lookupScopes: List<ResultsTypedFacetLookupScope>,
    fallbackBrand: String? = null,
    fallbackModel: String? = null,
): List<ResultsTypedFacetLookupScope> {
    val scopes = if (lookupScopes.isNotEmpty()) {
        lookupScopes
    } else {
        listOf(
            ResultsTypedFacetLookupScope(
                brand = fallbackBrand,
                model = fallbackModel,
            ),
        )
    }
    return scopes
        .asSequence()
        .mapNotNull { scope ->
            val brand = scope.brand?.trim()?.takeIf { value -> value.isNotEmpty() }
            val model = scope.model
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?.takeIf { brand != null }
            if (brand == null && model == null) {
                null
            } else {
                ResultsTypedFacetLookupScope(brand = brand, model = model)
            }
        }
        .distinctBy { scope ->
            "${scope.brand?.lowercase(Locale.ROOT).orEmpty()}::${scope.model?.lowercase(Locale.ROOT).orEmpty()}"
        }
        .toList()
}

private fun resolveTypedFacetObservedValues(
    attributeCode: String,
    observedValuesByAttributeCode: Map<String, List<String>>,
    liveModelOptions: List<String> = emptyList(),
): List<String> {
    if (attributeCode.equals("model", ignoreCase = true) && liveModelOptions.isNotEmpty()) {
        return liveModelOptions
            .asSequence()
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .distinctBy { value -> value.lowercase(Locale.ROOT) }
            .sortedBy { value -> value.lowercase(Locale.ROOT) }
            .toList()
    }
    val candidates = buildList {
        add(attributeCode)
        addAll(resultsTypedFacetAttributeAliases(attributeCode))
    }
    val observed = candidates
        .firstNotNullOfOrNull { candidate ->
            observedValuesByAttributeCode.entries.firstOrNull { (key, _) ->
                key.equals(candidate, ignoreCase = true)
            }?.value
        }
        .orEmpty()

    return observed
        .asSequence()
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
        .sortedBy { value -> value.lowercase(Locale.ROOT) }
        .toList()
}

internal fun loadKnownTypedFacetValues(
    spec: com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec?,
    categoryCode: String,
    lookupBrand: String?,
    lookupModel: String?,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
    runtimeKey: String,
    attributeCode: String?,
    liveModelOptions: List<String> = emptyList(),
    runtimeKnownValuesByAttributeCode: Map<String, List<String>> = emptyMap(),
    localeTag: String = Locale.getDefault().toLanguageTag(),
): List<String> {
    val normalizedLookupScopes = normalizeResultsTypedFacetLookupScopes(
        lookupScopes = lookupScopes,
        fallbackBrand = lookupBrand,
        fallbackModel = lookupModel,
    )
    val effectiveSpecValues = loadKnownValuesFromEffectiveSpec(
        spec = spec,
        runtimeKey = runtimeKey,
        attributeCode = attributeCode,
    )
    val runtimeKnownValues = loadRuntimeKnownTypedFacetValues(
        runtimeKey = runtimeKey,
        attributeCode = attributeCode,
        runtimeKnownValuesByAttributeCode = runtimeKnownValuesByAttributeCode,
    )
    val normalizedAttributeCode = attributeCode?.trim()?.lowercase(Locale.ROOT)
    if (normalizedAttributeCode != "model" && runtimeKey.trim().lowercase(Locale.ROOT) != "model") {
        if (runtimeKnownValues.isNotEmpty()) {
            return runtimeKnownValues
        }
        val scopedCanonicalValues = loadScopedKnownTypedFacetValuesForScopes(
            categoryCode = categoryCode,
            lookupBrand = lookupBrand,
            lookupModel = lookupModel,
            lookupScopes = normalizedLookupScopes,
            runtimeKey = runtimeKey,
            attributeCode = attributeCode,
            localeTag = localeTag,
        )
        return when {
            scopedCanonicalValues.isNotEmpty() -> scopedCanonicalValues
            else -> effectiveSpecValues
        }
    }

    if (runtimeKnownValues.isNotEmpty()) {
        return runtimeKnownValues
    }

    return loadKnownModelFacetValues(
        categoryCode = categoryCode,
        lookupBrand = lookupBrand,
        lookupScopes = normalizedLookupScopes,
        liveModelOptions = liveModelOptions,
        runtimeKnownValues = runtimeKnownValues,
        fallbackValues = effectiveSpecValues,
    )
}

private fun loadKnownModelFacetValues(
    categoryCode: String,
    lookupBrand: String?,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
    liveModelOptions: List<String>,
    runtimeKnownValues: List<String> = emptyList(),
    fallbackValues: List<String> = emptyList(),
): List<String> {
    val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
    val normalizedBrand = lookupBrand?.trim()?.takeIf { value -> value.isNotEmpty() }
    val scopedBrands = normalizeResultsTypedFacetLookupScopes(lookupScopes, lookupBrand)
        .mapNotNull { scope -> scope.brand }
        .distinctBy { brand -> brand.lowercase(Locale.ROOT) }
    val canonicalValues = when {
        scopedBrands.isEmpty() && normalizedBrand == null -> CatalogCanonicalModelRegistry.models()
            .asSequence()
            .filter { entry ->
                entry.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)
            }
            .map { entry -> entry.canonicalModel }
            .toList()

        else -> CatalogCanonicalModelRegistry.models()
            .asSequence()
            .filter { entry ->
                entry.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true) &&
                    (
                        scopedBrands.any { brand ->
                            entry.brandCanonical.equals(brand, ignoreCase = true)
                        } ||
                            (scopedBrands.isEmpty() && normalizedBrand != null &&
                                entry.brandCanonical.equals(normalizedBrand, ignoreCase = true))
                        )
            }
            .map { entry -> entry.canonicalModel }
            .toList()
    }

    return sequenceOf(liveModelOptions, runtimeKnownValues, canonicalValues, fallbackValues)
        .flatMap { values -> values.asSequence() }
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
        .sortedBy { value -> value.lowercase(Locale.ROOT) }
        .toList()
}

internal fun loadKnownTypedFacetValueAliases(
    spec: com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec?,
    categoryCode: String,
    lookupBrand: String?,
    lookupModel: String?,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
    runtimeKey: String,
    attributeCode: String?,
    runtimeKnownValuesByAttributeCode: Map<String, List<String>> = emptyMap(),
    runtimeKnownValueAliasesByAttributeCode: Map<String, Map<String, List<String>>> = emptyMap(),
    localeTag: String = Locale.getDefault().toLanguageTag(),
): Map<String, List<String>> {
    val normalizedLookupScopes = normalizeResultsTypedFacetLookupScopes(
        lookupScopes = lookupScopes,
        fallbackBrand = lookupBrand,
        fallbackModel = lookupModel,
    )
    val effectiveSpecAliases = loadKnownValueAliasesFromEffectiveSpec(
        spec = spec,
        runtimeKey = runtimeKey,
        attributeCode = attributeCode,
        localeTag = localeTag,
    )
    val runtimeKnownValues = loadRuntimeKnownTypedFacetValues(
        runtimeKey = runtimeKey,
        attributeCode = attributeCode,
        runtimeKnownValuesByAttributeCode = runtimeKnownValuesByAttributeCode,
    )
    val runtimeKnownAliases = loadRuntimeKnownTypedFacetAliases(
        runtimeKey = runtimeKey,
        attributeCode = attributeCode,
        runtimeKnownValueAliasesByAttributeCode = runtimeKnownValueAliasesByAttributeCode,
    )
    val normalizedAttributeCode = attributeCode?.trim()?.lowercase(Locale.ROOT)
    if (normalizedAttributeCode != "model" && runtimeKey.trim().lowercase(Locale.ROOT) != "model") {
        if (runtimeKnownValues.isNotEmpty()) {
            return mergeKnownValueAliasMaps(
                runtimeKnownAliases.withFallbackKnownValues(runtimeKnownValues),
                effectiveSpecAliases.filterToKnownValues(runtimeKnownValues),
            )
        }
        val scopedCanonicalAliases = loadScopedKnownTypedFacetValueAliasesForScopes(
            categoryCode = categoryCode,
            lookupBrand = lookupBrand,
            lookupModel = lookupModel,
            lookupScopes = normalizedLookupScopes,
            runtimeKey = runtimeKey,
            attributeCode = attributeCode,
            localeTag = localeTag,
        )
        return when {
            scopedCanonicalAliases.isNotEmpty() -> mergeKnownValueAliasMaps(
                scopedCanonicalAliases,
                effectiveSpecAliases.filterToKnownValues(scopedCanonicalAliases.keys.toList()),
            )

            else -> effectiveSpecAliases
        }
    }

    if (runtimeKnownValues.isNotEmpty()) {
        return mergeKnownValueAliasMaps(
            runtimeKnownAliases.withFallbackKnownValues(runtimeKnownValues),
            effectiveSpecAliases.filterToKnownValues(runtimeKnownValues),
        )
    }

    return mergeKnownValueAliasMaps(
        loadKnownModelFacetAliases(
            categoryCode = categoryCode,
            lookupBrand = lookupBrand,
            lookupScopes = normalizedLookupScopes,
        ),
        effectiveSpecAliases,
    )
}

private fun loadScopedKnownTypedFacetValuesForScopes(
    categoryCode: String,
    lookupBrand: String?,
    lookupModel: String?,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
    runtimeKey: String,
    attributeCode: String?,
    localeTag: String = Locale.getDefault().toLanguageTag(),
): List<String> {
    val normalizedScopes = normalizeResultsTypedFacetLookupScopes(
        lookupScopes = lookupScopes,
        fallbackBrand = lookupBrand,
        fallbackModel = lookupModel,
    )
    if (normalizedScopes.isEmpty()) {
        return loadScopedKnownTypedFacetValues(
            categoryCode = categoryCode,
            lookupBrand = lookupBrand,
            lookupModel = lookupModel,
            runtimeKey = runtimeKey,
            attributeCode = attributeCode,
            localeTag = localeTag,
        )
    }
    return normalizedScopes
        .asSequence()
        .flatMap { scope ->
            loadScopedKnownTypedFacetValues(
                categoryCode = categoryCode,
                lookupBrand = scope.brand,
                lookupModel = scope.model,
                runtimeKey = runtimeKey,
                attributeCode = attributeCode,
                localeTag = localeTag,
            ).asSequence()
        }
        .normalizeResultsTypedFacetValues()
}

private fun loadScopedKnownTypedFacetValueAliasesForScopes(
    categoryCode: String,
    lookupBrand: String?,
    lookupModel: String?,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
    runtimeKey: String,
    attributeCode: String?,
    localeTag: String = Locale.getDefault().toLanguageTag(),
): Map<String, List<String>> {
    val normalizedScopes = normalizeResultsTypedFacetLookupScopes(
        lookupScopes = lookupScopes,
        fallbackBrand = lookupBrand,
        fallbackModel = lookupModel,
    )
    if (normalizedScopes.isEmpty()) {
        return loadScopedKnownTypedFacetValueAliases(
            categoryCode = categoryCode,
            lookupBrand = lookupBrand,
            lookupModel = lookupModel,
            runtimeKey = runtimeKey,
            attributeCode = attributeCode,
            localeTag = localeTag,
        )
    }
    return normalizedScopes.fold(emptyMap()) { acc, scope ->
        mergeKnownValueAliasMaps(
            acc,
            loadScopedKnownTypedFacetValueAliases(
                categoryCode = categoryCode,
                lookupBrand = scope.brand,
                lookupModel = scope.model,
                runtimeKey = runtimeKey,
                attributeCode = attributeCode,
                localeTag = localeTag,
            ),
        )
    }
}

private fun loadScopedKnownTypedFacetValues(
    categoryCode: String,
    lookupBrand: String?,
    lookupModel: String?,
    runtimeKey: String,
    attributeCode: String?,
    localeTag: String = Locale.getDefault().toLanguageTag(),
): List<String> {
    val normalizedAttributeCode = attributeCode
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: runtimeKey.trim()
    val scope = resolveCatalogGovernanceScope(
        categoryCode = categoryCode,
        lookupBrand = lookupBrand,
        lookupModel = lookupModel,
    )
    return CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
        attributeCode = normalizedAttributeCode,
        scope = scope,
        locale = localeTag,
    )
        .asSequence()
        .map { value -> value.displayValue.trim() }
        .filter { value -> value.isNotEmpty() }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
        .toList()
}

private fun loadScopedKnownTypedFacetValueAliases(
    categoryCode: String,
    lookupBrand: String?,
    lookupModel: String?,
    runtimeKey: String,
    attributeCode: String?,
    localeTag: String = Locale.getDefault().toLanguageTag(),
): Map<String, List<String>> {
    val normalizedAttributeCode = attributeCode
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: runtimeKey.trim()
    val scope = resolveCatalogGovernanceScope(
        categoryCode = categoryCode,
        lookupBrand = lookupBrand,
        lookupModel = lookupModel,
    )
    return CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
        attributeCode = normalizedAttributeCode,
        scope = scope,
        locale = localeTag,
    )
        .groupBy { value -> value.displayValue }
        .mapValues { (_, values) ->
            values
                .flatMap { value -> value.aliases + value.displayValue }
                .asSequence()
                .map { alias -> alias.trim() }
                .filter { alias -> alias.isNotEmpty() }
                .distinctBy { alias -> alias.lowercase(Locale.ROOT) }
                .toList()
        }
}

private fun loadKnownValueAliasesFromEffectiveSpec(
    spec: com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec?,
    runtimeKey: String,
    attributeCode: String?,
    localeTag: String,
): Map<String, List<String>> {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    val normalizedAttributeCode = attributeCode?.trim()?.lowercase(Locale.ROOT)
    val attributeSpec = spec
        ?.allAttributes()
        ?.firstOrNull { attribute ->
            val candidateCode = attribute.code.trim().lowercase(Locale.ROOT)
            candidateCode == normalizedRuntimeKey || candidateCode == normalizedAttributeCode
        }
        ?: return emptyMap()
    return attributeSpec.resultsKnownValueAliases(localeTag)
}

private fun loadKnownModelFacetAliases(
    categoryCode: String,
    lookupBrand: String?,
    lookupScopes: List<ResultsTypedFacetLookupScope> = emptyList(),
): Map<String, List<String>> {
    val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
    val scopedBrands = normalizeResultsTypedFacetLookupScopes(lookupScopes, lookupBrand)
        .mapNotNull { scope -> scope.brand }
        .distinctBy { brand -> brand.lowercase(Locale.ROOT) }
        .ifEmpty {
            listOfNotNull(lookupBrand?.trim()?.takeIf { value -> value.isNotEmpty() })
        }
    if (scopedBrands.isEmpty()) return emptyMap()
    return CatalogCanonicalModelRegistry.models()
        .asSequence()
        .filter { entry ->
            entry.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true) &&
                scopedBrands.any { brand ->
                    entry.brandCanonical.equals(brand, ignoreCase = true)
                }
        }
        .groupBy { entry -> entry.canonicalModel }
        .mapValues { (canonicalModel, entries) ->
            (entries.flatMap { entry -> entry.modelAliases } + canonicalModel)
                .asSequence()
                .map { alias -> alias.trim() }
                .filter { alias -> alias.isNotEmpty() }
                .distinctBy { alias -> alias.lowercase(Locale.ROOT) }
                .toList()
        }
}

private fun mergeKnownValueAliasMaps(
    primary: Map<String, List<String>>,
    secondary: Map<String, List<String>>,
): Map<String, List<String>> {
    val merged = LinkedHashMap<String, MutableList<String>>()
    sequenceOf(primary, secondary).forEach { source ->
        source.forEach { (value, aliases) ->
            val key = merged.keys.firstOrNull { existing -> existing.equals(value, ignoreCase = true) } ?: value
            val bucket = merged.getOrPut(key) { mutableListOf() }
            aliases.forEach { alias ->
                if (bucket.none { existing -> existing.equals(alias, ignoreCase = true) }) {
                    bucket += alias
                }
            }
        }
    }
    return merged.mapValues { (_, aliases) -> aliases.toList() }
}

private fun Map<String, List<String>>.filterToKnownValues(
    allowedValues: List<String>,
): Map<String, List<String>> {
    if (allowedValues.isEmpty()) return emptyMap()
    return entries
        .filter { (value, _) ->
            allowedValues.any { allowed -> allowed.equals(value, ignoreCase = true) }
        }
        .associate { it.toPair() }
}

private fun loadRuntimeKnownTypedFacetValues(
    runtimeKey: String,
    attributeCode: String?,
    runtimeKnownValuesByAttributeCode: Map<String, List<String>>,
): List<String> {
    val candidates = buildRuntimeKnownTypedFacetCandidates(runtimeKey, attributeCode)
    return candidates.firstNotNullOfOrNull { candidate ->
        runtimeKnownValuesByAttributeCode.entries.firstOrNull { (key, _) ->
            key.equals(candidate, ignoreCase = true)
        }?.value
    }.orEmpty()
}

private fun loadRuntimeKnownTypedFacetAliases(
    runtimeKey: String,
    attributeCode: String?,
    runtimeKnownValueAliasesByAttributeCode: Map<String, Map<String, List<String>>>,
): Map<String, List<String>> {
    val candidates = buildRuntimeKnownTypedFacetCandidates(runtimeKey, attributeCode)
    return candidates.firstNotNullOfOrNull { candidate ->
        runtimeKnownValueAliasesByAttributeCode.entries.firstOrNull { (key, _) ->
            key.equals(candidate, ignoreCase = true)
        }?.value
    }.orEmpty()
}

private fun buildRuntimeKnownTypedFacetCandidates(
    runtimeKey: String,
    attributeCode: String?,
): List<String> {
    val normalizedAttributeCode = attributeCode?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedRuntimeKey = runtimeKey.trim().takeIf { it.isNotEmpty() }
    return buildList {
        normalizedAttributeCode?.let(::add)
        normalizedRuntimeKey?.let(::add)
        normalizedAttributeCode?.let { code -> addAll(resultsTypedFacetAttributeAliases(code)) }
        normalizedRuntimeKey?.let { key ->
            if (!normalizedAttributeCode.equals(key, ignoreCase = true)) {
                addAll(resultsTypedFacetAttributeAliases(key))
            }
        }
    }
        .map { candidate -> candidate.trim() }
        .filter { candidate -> candidate.isNotEmpty() }
        .distinctBy { candidate -> candidate.lowercase(Locale.ROOT) }
}

private fun Map<String, List<String>>.withFallbackKnownValues(
    knownValues: List<String>,
): Map<String, List<String>> =
    knownValues.associateWith { value ->
        (
            this[value].orEmpty() +
                entries.firstOrNull { (key, _) -> key.equals(value, ignoreCase = true) }?.value.orEmpty() +
                value
            )
            .asSequence()
            .map { alias -> alias.trim() }
            .filter { alias -> alias.isNotEmpty() }
            .distinctBy { alias -> alias.lowercase(Locale.ROOT) }
            .toList()
    }

private fun Sequence<String>.normalizeResultsTypedFacetValues(): List<String> =
    map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
        .toList()

private fun resultsTypedFacetAttributeAliases(attributeCode: String): List<String> = when (attributeCode.lowercase(Locale.ROOT)) {
    "storage" -> listOf("memory", "memory_gb")
    "memory" -> listOf("storage", "memory_gb")
    "memory_gb" -> listOf("memory", "storage")
    "ram_gb" -> listOf("ram")
    "ram" -> listOf("ram_gb")
    "state" -> listOf("condition")
    "condition" -> listOf("state")
    else -> emptyList()
}

private fun FilterState.summaryForFacet(facetFilter: ResultsFacetFilter): String = when (facetFilter.type) {
    ResultsFacetFilterType.Brand -> brandSummary()
    ResultsFacetFilterType.PriceRange -> priceSummary()
    ResultsFacetFilterType.Condition -> conditionsSummary()
    ResultsFacetFilterType.DeliveryChannel -> purchaseFormatSummary()
    ResultsFacetFilterType.SellerTrust -> "Недоступно"
    ResultsFacetFilterType.TypedAttribute -> typedFilterSummary(facetFilter.runtimeKey)
    ResultsFacetFilterType.Unsupported -> {
        presetAttributes[facetFilter.facetKey]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Задаётся пресетом"
    }
}

private fun FilterState.defaultHubSummaryForFacet(facetFilter: ResultsFacetFilter): String? = when (facetFilter.type) {
    ResultsFacetFilterType.Brand -> "Все"
    ResultsFacetFilterType.PriceRange -> "Любая"
    ResultsFacetFilterType.Condition -> "Все"
    ResultsFacetFilterType.DeliveryChannel -> "Все"
    ResultsFacetFilterType.SellerTrust -> null
    ResultsFacetFilterType.TypedAttribute -> "Все"
    ResultsFacetFilterType.Unsupported -> null
}

private fun FilterState.isFacetActive(facetFilter: ResultsFacetFilter): Boolean = when (facetFilter.type) {
    ResultsFacetFilterType.Brand -> brands.isNotEmpty()
    ResultsFacetFilterType.PriceRange -> priceMin != null || priceMax != null
    ResultsFacetFilterType.Condition -> conditions.isNotEmpty()
    ResultsFacetFilterType.DeliveryChannel -> deliverableOnly
    ResultsFacetFilterType.SellerTrust -> false
    ResultsFacetFilterType.TypedAttribute -> typedAttributeFilters[facetFilter.runtimeKey] != null
    ResultsFacetFilterType.Unsupported -> {
        presetAttributes[facetFilter.facetKey]
            ?.trim()
            ?.isNotEmpty() == true
    }
}

private fun ResultsFacetFilter.icon(): ImageVector = when (type) {
    ResultsFacetFilterType.Brand -> Icons.Outlined.CheckCircle
    ResultsFacetFilterType.PriceRange -> Icons.Outlined.KeyboardArrowDown
    ResultsFacetFilterType.Condition -> Icons.Outlined.RadioButtonUnchecked
    ResultsFacetFilterType.DeliveryChannel -> Icons.Outlined.CheckBoxOutlineBlank
    ResultsFacetFilterType.SellerTrust -> Icons.Outlined.CheckBox
    ResultsFacetFilterType.TypedAttribute -> Icons.Outlined.Search
    ResultsFacetFilterType.Unsupported -> Icons.Outlined.Search
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
    return copy(
        brands = updated,
        interpretedBrandSeed = null,
        query = query?.withoutStructuredFacet("brand"),
    )
}

private fun FilterState.withCondition(option: ConditionOption): FilterState {
    val updated = conditions.toMutableSet()
    if (option in updated) {
        updated.remove(option)
    } else {
        updated.add(option)
    }
    return copy(
        conditions = updated,
        interpretedConditionSeed = null,
        query = query?.withoutStructuredFacet("condition"),
    )
}

private fun normalizeSellerTrustSignals(signals: Set<SellerTrustSignal>): Set<SellerTrustSignal> =
    SellerTrustSignal.entries
        .filter { signal -> signal in signals }
        .toCollection(LinkedHashSet())

private fun inferSellerTrustPreset(signals: Set<SellerTrustSignal>): SellerTrustPreset {
    val normalized = normalizeSellerTrustSignals(signals)
    if (normalized.isEmpty()) return SellerTrustPreset.Any
    return when (normalized) {
        normalizeSellerTrustSignals(SellerTrustPreset.Balanced.signals) -> SellerTrustPreset.Balanced
        normalizeSellerTrustSignals(SellerTrustPreset.Strict.signals) -> SellerTrustPreset.Strict
        normalizeSellerTrustSignals(SellerTrustPreset.VerifiedOnly.signals) -> SellerTrustPreset.VerifiedOnly
        else -> SellerTrustPreset.Custom
    }
}

private fun FilterState.withSellerTrustPreset(
    preset: SellerTrustPreset,
    availableSignals: Set<SellerTrustSignal>,
): FilterState {
    val selectedSignals = when (preset) {
        SellerTrustPreset.Any -> emptySet()
        SellerTrustPreset.Custom -> sellerTrustSignals
        else -> preset.signals
    }
    val normalized = normalizeSellerTrustSignals(selectedSignals)
        .filterTo(LinkedHashSet()) { signal -> signal in availableSignals }
    return copy(
        sellerTrustSignals = normalized,
        sellerTrustPreset = inferSellerTrustPreset(normalized),
    )
}

private fun FilterState.toggleSellerTrustSignal(
    signal: SellerTrustSignal,
    enabled: Boolean,
    availableSignals: Set<SellerTrustSignal>,
): FilterState {
    if (signal !in availableSignals) return this
    val next = sellerTrustSignals.toMutableSet()
    if (enabled) {
        next += signal
    } else {
        next -= signal
    }
    val normalized = normalizeSellerTrustSignals(next)
    return copy(
        sellerTrustSignals = normalized,
        sellerTrustPreset = inferSellerTrustPreset(normalized),
    )
}

private fun sellerTrustOptions(
    stats: List<SellerTrustSignalStats>,
    selectedSignals: Set<SellerTrustSignal>,
): List<SellerTrustSignalOption> =
    stats.map { state ->
        SellerTrustSignalOption(
            signal = state.signal,
            selected = state.signal in selectedSignals,
            enabled = state.available,
            count = if (state.available && !state.partial) state.positiveCount else null,
            supportingText = state.supportingText,
        )
    }

private fun buildSellerTrustSignalStats(items: List<ExplainedItem>): List<SellerTrustSignalStats> {
    val total = items.size
    return SellerTrustSignal.entries.map { signal ->
        val evaluations = items.map { item -> evaluateSellerTrustSignal(item, signal) }
        val knownCount = evaluations.count { value -> value != null }
        val positiveCount = evaluations.count { value -> value == true }
        val available = when (signal) {
            SellerTrustSignal.ProfileAge90d -> false
            else -> knownCount > 0 || total == 0
        }
        val partial = total > 0 && knownCount in 1 until total
        val supporting = when {
            !available -> signal.unsupportedHint
            partial -> signal.partialHint
            else -> signal.description
        }
        SellerTrustSignalStats(
            signal = signal,
            totalCount = total,
            knownCount = knownCount,
            positiveCount = positiveCount,
            available = available,
            partial = partial,
            supportingText = supporting,
        )
    }
}

private fun matchesSellerTrustSignals(
    item: ExplainedItem,
    selectedSignals: Set<SellerTrustSignal>,
): Boolean {
    if (selectedSignals.isEmpty()) return true
    return selectedSignals.all { signal ->
        evaluateSellerTrustSignal(item, signal) == true
    }
}

private fun evaluateSellerTrustSignal(
    item: ExplainedItem,
    signal: SellerTrustSignal,
): Boolean? {
    val dto = item.dto
    val trustScore = dto.trustScore
    val badges = dto.sellerBadges
        .joinToString(separator = " ") { badge -> badge.trim().lowercase(Locale.ROOT) }
        .trim()
    return when (signal) {
        SellerTrustSignal.VerifiedSeller -> {
            when {
                badges.isNotEmpty() && (
                    badges.contains("verified") ||
                        badges.contains("trusted") ||
                        badges.contains("official") ||
                        badges.contains("провер")
                    ) -> true

                badges.isNotEmpty() || dto.sellerType != null -> false
                trustScore != null -> trustScore >= 0.75
                else -> null
            }
        }

        SellerTrustSignal.HighRating -> dto.sellerRating?.let { rating -> rating >= 4.5 }
        SellerTrustSignal.LowDisputeRate -> trustScore?.let { trust -> trust >= 0.70 }
        SellerTrustSignal.ReturnAvailable -> {
            when {
                badges.isNotEmpty() && (
                    badges.contains("return") ||
                        badges.contains("refund") ||
                        badges.contains("warranty") ||
                        badges.contains("гарант") ||
                        badges.contains("возврат")
                    ) -> true

                badges.isNotEmpty() -> false
                else -> null
            }
        }

        SellerTrustSignal.ProfileAge90d -> null
    }
}

private fun NormalizedQuery.withoutStructuredFacet(runtimeKey: String): NormalizedQuery? {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    if (normalizedRuntimeKey.isEmpty()) return this
    val nextBrand = if (normalizedRuntimeKey == "brand") "" else brand
    val nextModel = if (normalizedRuntimeKey == "model") "" else model
    val nextAttributes = attributes
        .filterKeys { key -> !key.trim().equals(normalizedRuntimeKey, ignoreCase = true) }
        .toMap(LinkedHashMap())
    if (nextBrand.isBlank() && nextModel.isBlank() && nextAttributes.isEmpty()) return null
    return copy(
        brand = nextBrand,
        model = nextModel,
        attributes = nextAttributes,
    )
}

private fun FilterState.clearBrandSelection(): FilterState = copy(
    brands = emptySet(),
    interpretedBrandSeed = null,
    query = query?.withoutStructuredFacet("brand"),
)

private fun FilterState.clearConditionSelection(): FilterState = copy(
    conditions = emptySet(),
    interpretedConditionSeed = null,
    query = query?.withoutStructuredFacet("condition"),
)

private fun FilterState.withTypedAttributeDraft(
    runtimeKey: String,
    draft: TypedAttributeFilterDraft?,
): FilterState {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    if (normalizedRuntimeKey.isEmpty()) return this
    val updatedFilters = typedAttributeFilters.toMutableMap().apply {
        val existingKey = keys.firstOrNull { key -> key.trim().equals(normalizedRuntimeKey, ignoreCase = true) }
        if (existingKey != null) remove(existingKey)
        if (draft != null) put(normalizedRuntimeKey, draft)
    }
    return copy(
        typedAttributeFilters = updatedFilters,
        query = query?.withoutStructuredFacet(normalizedRuntimeKey),
    )
}

private fun buildRecognitionTitle(filters: FilterState): String {
    val parts = listOfNotNull(
        filters.query?.brand?.takeIf { it.isNotBlank() },
        filters.query?.model?.takeIf { it.isNotBlank() },
    )
    return parts.joinToString(" ").ifBlank { filters.queryText }
}

private fun visualBinderStatusLabel(status: VisualSearchBinderStatus?): String = when (status) {
    VisualSearchBinderStatus.ACCEPTED -> "Подтверждено"
    VisualSearchBinderStatus.ACCEPTED_PARTIAL -> "Уточнено"
    VisualSearchBinderStatus.REJECTED -> "Широкий поиск"
    null -> "По фото"
}

private fun visualAnchorStatusLabel(visualContext: ResultsVisualContext): String = when {
    visualContext.exactRoute || visualContext.routeKind == VisualSearchRouteKind.EXACT -> "Точно"
    visualContext.chips.any { chip -> chip.kind == VisualSearchChipKind.BRAND || chip.kind == VisualSearchChipKind.MODEL } ->
        "Якоря"
    visualContext.chips.any { chip -> chip.kind == VisualSearchChipKind.ITEM_TYPE } -> "Тип товара"
    visualContext.modelCandidates.isNotEmpty() -> "Кандидаты"
    visualContext.routeKind == VisualSearchRouteKind.BRANCH_ONLY -> "Категория"
    visualContext.chips.any { chip -> chip.kind == VisualSearchChipKind.CATEGORY } -> "Категория"
    visualContext.binderStatus == VisualSearchBinderStatus.REJECTED -> "Нужно уточнить"
    visualContext.qualityApproved -> "Готово"
    else -> "По фото"
}

private fun isGenericResultsPhotoPreviewTitle(raw: String): Boolean {
    val normalized = raw
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalized in genericResultsPhotoPreviewTitles ||
        normalized in weakResultsPhotoPreviewTitles ||
        weakResultsPhotoPreviewTokens.any { token -> normalized.contains(token) }
}

private val genericResultsPhotoPreviewTitles = setOf(
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

private val weakResultsPhotoPreviewTitles = setOf(
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
)

private val weakResultsPhotoPreviewTokens = setOf(
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
)

@Composable
private fun visualBinderStatusContainerColor(status: VisualSearchBinderStatus?): Color = when (status) {
    VisualSearchBinderStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
    VisualSearchBinderStatus.ACCEPTED_PARTIAL -> MaterialTheme.colorScheme.secondary
    VisualSearchBinderStatus.REJECTED,
    null,
        -> MaterialTheme.colorScheme.surface
}

@Composable
private fun visualBinderStatusContentColor(status: VisualSearchBinderStatus?): Color = when (status) {
    VisualSearchBinderStatus.ACCEPTED -> MaterialTheme.colorScheme.onPrimary
    VisualSearchBinderStatus.ACCEPTED_PARTIAL -> MaterialTheme.colorScheme.onSecondary
    VisualSearchBinderStatus.REJECTED,
    null,
        -> MaterialTheme.colorScheme.onSurface
}

private fun visualBinderStatusMessage(
    binderStatus: VisualSearchBinderStatus?,
    routeKind: VisualSearchRouteKind?,
    exactRoute: Boolean,
    qualityApproved: Boolean,
): String = when (binderStatus) {
    VisualSearchBinderStatus.ACCEPTED -> {
        if (exactRoute || routeKind == VisualSearchRouteKind.EXACT) {
            "Выдача собрана по подтверждённым фото-признакам и точному маршруту."
        } else if (routeKind == VisualSearchRouteKind.FAMILY_ANCHOR) {
            "Используем категорию и визуальные якоря бренда или семейства. Модель можно выбрать ниже."
        } else if (routeKind == VisualSearchRouteKind.BRANCH_ONLY) {
            "Используем найденную категорию, не навязывая слабые признаки модели."
        } else if (routeKind == VisualSearchRouteKind.TYPED_ENTITY) {
            "Определён тип товара: выдача расширена, чтобы не потерять подходящие позиции."
        } else {
            "Выдача собрана по подтверждённым фото-признакам."
        }
    }
    VisualSearchBinderStatus.ACCEPTED_PARTIAL -> when (routeKind) {
        VisualSearchRouteKind.FAMILY_ANCHOR ->
            "Показываем категорию, бренд или семейство как якоря. Точную модель оставили кандидатом."
        VisualSearchRouteKind.BRANCH_ONLY,
        VisualSearchRouteKind.TYPED_ENTITY,
            -> "Показываем выдачу по найденной категории. Слабые признаки не стали фильтрами."
        else ->
            "Часть признаков отброшена, чтобы не сузить выдачу ошибочно."
    }
    VisualSearchBinderStatus.REJECTED ->
        "Показываем безопасную выдачу по фото. Уточните бренд или модель, если нужно сузить."
    null -> if (qualityApproved) {
        "Результаты собраны по фото и подсказкам из кадра."
    } else {
        "Показываем безопасную выдачу по фото."
    }
}

private fun visualIntentLabel(intent: VisualSearchIntent): String = when (intent) {
    VisualSearchIntent.EXACT_SAME -> "Точно такой"
    VisualSearchIntent.SIMILAR -> "Похожие"
    VisualSearchIntent.PART_ACCESSORY -> "Детали и аксессуары"
    VisualSearchIntent.IDENTIFY_FIRST -> "Сначала определить"
}

private fun visualCaptureModeLabel(mode: com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode): String = when (mode) {
    com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode.IMAGE -> "Камера"
    com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode.BARCODE -> "Штрихкод"
    com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode.OCR -> "Текст"
}

private fun visualRouteLabel(visualContext: ResultsVisualContext): String = when {
    visualContext.routeKind == VisualSearchRouteKind.EXACT ||
        visualContext.exactRoute -> "Точное совпадение"
    visualContext.routeKind == VisualSearchRouteKind.FAMILY_ANCHOR ->
        "Бренд или семейство"
    visualContext.routeKind == VisualSearchRouteKind.TYPED_ENTITY ->
        "Подходящий тип товара"
    visualContext.routeKind == VisualSearchRouteKind.BRANCH_ONLY ->
        "Подходящая категория"
    visualContext.intent == VisualSearchIntent.SIMILAR -> "Похожие товары"
    visualContext.intent == VisualSearchIntent.PART_ACCESSORY -> "Детали и аксессуары"
    visualContext.intent == VisualSearchIntent.EXACT_SAME -> "Точное совпадение"
    else -> "Подходящие товары"
}

private fun buildResultsVisualCandidates(
    visualContext: ResultsVisualContext?,
): List<ResultsVisualCandidateUi> {
    if (visualContext == null) return emptyList()
    val selectedRank = visualContext.selectedCandidateRank
        ?: visualContext.rankedCandidates.firstOrNull { candidate -> candidate.isPrimary }?.rank
    return visualContext.rankedCandidates
        .take(3)
        .mapIndexed { index, candidate ->
            ResultsVisualCandidateUi(
                stableKey = resultsVisualCandidateKey(candidate, index),
                rank = candidate.rank,
                label = resultsVisualCandidateLabel(candidate),
                supportingLabel = resultsVisualCandidateSupportingLabel(candidate),
                highlighted = candidate.rank == selectedRank,
                candidate = candidate,
            )
        }
        .takeIf { candidates -> candidates.size > 1 }
        .orEmpty()
}

private fun resultsVisualCandidateLabel(
    candidate: VisualSearchBoundCandidate,
): String = candidate.query.previewTitle
    ?.trim()
    ?.takeIf { value -> value.isNotEmpty() }
    ?: candidate.query.chips
        .firstOrNull { chip ->
            chip.kind == VisualSearchChipKind.MODEL ||
                chip.kind == VisualSearchChipKind.BRAND ||
                chip.kind == VisualSearchChipKind.ITEM_TYPE
        }
        ?.label
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    ?: candidate.query.categoryCode

private fun resultsVisualCandidateSupportingLabel(
    candidate: VisualSearchBoundCandidate,
): String {
    val routeLabel = when {
        candidate.query.routeKind == VisualSearchRouteKind.EXACT || candidate.query.exactRoute -> "точный"
        candidate.query.routeKind == VisualSearchRouteKind.FAMILY_ANCHOR -> "семейство"
        candidate.query.routeKind == VisualSearchRouteKind.TYPED_ENTITY -> "тип"
        candidate.query.routeKind == VisualSearchRouteKind.BRANCH_ONLY -> "категория"
        else -> "вариант"
    }
    val confidenceLabel = candidate.confidence
        ?.coerceIn(0f, 1f)
        ?.let { confidence -> " ${((confidence * 100).roundToInt())}%" }
        .orEmpty()
    return routeLabel + confidenceLabel
}

private fun resultsVisualCandidateKey(
    candidate: VisualSearchBoundCandidate,
    index: Int,
): String = buildList {
    add(candidate.rank.toString())
    add(index.toString())
    add(candidate.query.categoryCode)
    add(candidate.query.routeKind.name)
    add(candidate.query.previewTitle.orEmpty())
    add(candidate.query.searchCriteria.brand.orEmpty())
    add(candidate.query.searchCriteria.model.orEmpty())
    add(candidate.query.searchCriteria.facetCollectionCode.orEmpty())
    add(candidate.query.searchCriteria.facetPresetCode.orEmpty())
}.joinToString("|")

private fun ResultsVisualContext.selectCandidate(
    candidate: VisualSearchBoundCandidate,
): ResultsVisualContext = copy(
    binderStatus = candidate.query.binderStatus,
    routeKind = candidate.query.routeKind,
    qualityApproved = candidate.query.qualityApproved,
    previewTitle = resultsVisualCandidateLabel(candidate),
    previewSubtitle = if (candidate.isPrimary) {
        null
    } else {
        "Показан альтернативный вероятностный вариант."
    },
    chips = candidate.query.chips,
    modelCandidates = candidate.query.modelCandidates,
    selectedCandidateRank = candidate.rank,
    exactRoute = candidate.query.exactRoute,
    reusableFingerprint = candidate.query.reusableFingerprint,
)

private fun VisualSearchChip.toResultsPhotoModeChipKind(): ResultsPhotoModeChipKind = when (kind) {
    VisualSearchChipKind.CATEGORY -> ResultsPhotoModeChipKind.Category
    VisualSearchChipKind.ITEM_TYPE -> ResultsPhotoModeChipKind.ItemType
    VisualSearchChipKind.BRAND -> ResultsPhotoModeChipKind.Brand
    VisualSearchChipKind.MODEL -> ResultsPhotoModeChipKind.Model
    VisualSearchChipKind.BARCODE -> ResultsPhotoModeChipKind.Barcode
    VisualSearchChipKind.ATTRIBUTE -> ResultsPhotoModeChipKind.Attribute
}

private fun buildResultsPhotoPrimaryChipLabels(
    filters: FilterState,
    visualContext: ResultsVisualContext?,
    categoryLabel: String?,
): List<String> = buildList {
    categoryLabel
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::add)
    if (visualContext == null) {
        filters.query?.brand
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::add)
        filters.query?.model
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::add)
    }
    val visualContextValue = visualContext
    visualContextValue
        ?.chips
        .orEmpty()
        .forEach { chip ->
            val include = when (chip.kind) {
                VisualSearchChipKind.CATEGORY,
                VisualSearchChipKind.ITEM_TYPE,
                VisualSearchChipKind.BRAND,
                VisualSearchChipKind.MODEL,
                VisualSearchChipKind.BARCODE,
                VisualSearchChipKind.ATTRIBUTE,
                    -> visualContextValue?.qualityApproved == true || chip.kind == VisualSearchChipKind.CATEGORY
            }
            if (!include) return@forEach
            chip.label
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let(::add)
        }
}.distinctBy { label -> label.lowercase(Locale.ROOT) }

private fun initialResultsInlineQuery(filters: FilterState): String =
    filters.queryText.trim().ifBlank { buildRecognitionTitle(filters).trim() }

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

private val resultsInterpretationIdentityKeys = setOf("brand", "model", "model_line", "product_name")
private const val resultsInterpretationCategorySelectorKey = "category"

private fun isResultsInterpretationCategoryLevelKey(key: String): Boolean =
    key.startsWith("category_level_") || key == resultsInterpretationCategorySelectorKey

private fun sanitizeResultsInterpretationFilters(filters: Map<String, String>): Map<String, String> =
    filters
        .filterKeys { key -> !isResultsInterpretationCategoryLevelKey(key.trim()) }
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
            else normalizedKey to normalizedValue
        }
        .toMap(LinkedHashMap())

private fun FilterState.toInterpretationSeedFilters(): Map<String, String> {
    val seeded = linkedMapOf<String, String>()

    fun putSeed(rawKey: String, rawValue: String?) {
        val key = rawKey.trim()
        val value = rawValue?.trim().orEmpty()
        if (key.isEmpty() || value.isEmpty() || isResultsInterpretationCategoryLevelKey(key)) return
        seeded[key] = value
    }

    putSeed("brand", query?.brand)
    putSeed("model", query?.model)
    query?.attributes
        ?.toRawStringAttributes()
        ?.forEach { (key, value) ->
            if (!key.equals("brand", ignoreCase = true) && !key.equals("model", ignoreCase = true)) {
                putSeed(key, value)
            }
        }
    presetAttributes.forEach { (key, value) -> putSeed(key, value) }
    if (conditions.size == 1) {
        putSeed("condition", conditions.first().value)
    }
    typedAttributeFilters.forEach { (rawKey, draft) ->
        val key = rawKey.trim().lowercase(Locale.ROOT)
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
        putSeed(key, value)
    }
    return seeded
}

private fun parseResultsAttributesFromFreeQuery(
    queryText: String,
    attributeDefs: List<AttributeDef>,
): Map<String, String> {
    val defs = attributeDefs.filterNot { def -> isResultsInterpretationCategoryLevelKey(def.key) }
    return parseFreeQueryAttributes(
        queryText = queryText,
        attributeDefs = defs,
    )
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
            if (
                !key.equals("brand", ignoreCase = true) &&
                !key.equals("model", ignoreCase = true) &&
                !key.equals("condition", ignoreCase = true)
            ) {
                putExtra(key, value)
            }
        }
    filters.presetAttributes.forEach { (key, value) -> putExtra(key, value) }

    if (filters.conditions.size == 1) {
        putExtra("condition", filters.conditions.first().value)
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
    OfferSort.PRICE_ASC -> "Цена ↑"
    OfferSort.PRICE_DESC -> "Цена ↓"
    OfferSort.NEWEST -> "Новые объявления"
    OfferSort.MODEL_FRESHNESS_DESC -> "Новинки"
    OfferSort.DELIVERY_ASC, OfferSort.DISTANCE_ASC -> "Рядом"
    OfferSort.RATING_DESC -> "Рейтинг продавца"
}

private fun sortControlLabel(sort: OfferSort): String = when (sort) {
    OfferSort.RANK -> "Сортировка"
    else -> sortLabel(sort)
}

private fun viewModeLabel(viewMode: ResultsViewMode): String = when (viewMode.normalized()) {
    ResultsViewMode.List -> "Список"
    ResultsViewMode.Grid -> "Сетка"
    ResultsViewMode.Compact,
    ResultsViewMode.Dense,
    -> "Список"
}

private fun viewModeIcon(viewMode: ResultsViewMode): ImageVector = when (viewMode.normalized()) {
    ResultsViewMode.List -> Icons.AutoMirrored.Outlined.ViewList
    ResultsViewMode.Grid -> Icons.Outlined.GridView
    ResultsViewMode.Compact,
    ResultsViewMode.Dense,
    -> Icons.AutoMirrored.Outlined.ViewList
}

internal data class SortOptionItem(
    val sort: OfferSort,
    val label: String,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

internal data class BrandOptionItem(
    val name: String,
    val count: Int? = null,
)

internal fun buildSortOptions(
    items: List<ExplainedItem>,
    location: String?,
    categoryCode: String? = null,
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
    val modelFreshnessCoverage = coverage { item ->
        item.dto.resultsAttributeValue("release_year")?.asDoubleOrNull() != null
    }
    val hasLocationContext = !location.isNullOrBlank() || distanceCoverage > 0.0
    val supportsModelFreshness = supportsResultsModelFreshnessSort(categoryCode)

    val isPriceAvailable = priceCoverage >= 0.70
    val isDateAvailable = dateCoverage >= 0.60
    val isTrustAvailable = trustCoverage >= 0.60
    val isDistanceAvailable = hasLocationContext && distanceCoverage >= 0.60
    val isModelFreshnessAvailable = supportsModelFreshness && modelFreshnessCoverage >= 0.60

    val options = mutableListOf(
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
    if (supportsModelFreshness) {
        options.add(
            4,
            SortOptionItem(
                sort = OfferSort.MODEL_FRESHNESS_DESC,
                label = sortLabel(OfferSort.MODEL_FRESHNESS_DESC),
                enabled = isModelFreshnessAvailable,
                disabledReason = if (!isModelFreshnessAvailable) {
                    "Недостаточно данных о релизе модели"
                } else {
                    null
                },
            ),
        )
    }
    return options
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

internal fun availableBrandOptions(
    items: List<ExplainedItem>,
    runtimeFacets: List<BrandFacet>,
    categoryCode: String?,
    categoriesByCode: Map<String, Category> = emptyMap(),
    selectedBrands: Set<String> = emptySet(),
    typedAttributeFilters: Map<String, TypedAttributeFilterDraft> = emptyMap(),
    query: NormalizedQuery? = null,
): List<BrandOptionItem> {
    val countsByKey = LinkedHashMap<String, Int>()
    val labelsByKey = LinkedHashMap<String, String>()
    val selectedBrandKeys = selectedBrands
        .asSequence()
        .mapNotNull { brand -> brand.trim().takeIf { value -> value.isNotEmpty() } }
        .map { brand -> brand.lowercase(Locale.ROOT) }
        .toSet()
    val scopedBrands = resolveBrandScopeForBrandFacet(
        categoryCode = categoryCode,
        selectedBrands = selectedBrands,
        typedAttributeFilters = typedAttributeFilters,
        query = query,
    )
    val scopedBrandKeys = scopedBrands
        .map { brand -> brand.lowercase(Locale.ROOT) }
        .toSet()

    fun shouldIncludeBrand(label: String): Boolean {
        val brandKey = label.lowercase(Locale.ROOT)
        return scopedBrandKeys.isEmpty() ||
            brandKey in scopedBrandKeys ||
            brandKey in selectedBrandKeys
    }

    runtimeFacets.forEach { facet ->
        val label = facet.name.trim().takeIf { value -> value.isNotBlank() } ?: return@forEach
        if (!shouldIncludeBrand(label)) return@forEach
        val key = label.lowercase(Locale.ROOT)
        countsByKey[key] = (countsByKey[key] ?: 0) + facet.count.coerceAtLeast(0)
        labelsByKey[key] = labelsByKey[key] ?: label
    }

    items.mapNotNull { item -> item.dto.brand?.trim()?.takeIf { value -> value.isNotBlank() } }
        .forEach { brand ->
            if (!shouldIncludeBrand(brand)) return@forEach
            val key = brand.lowercase(Locale.ROOT)
            labelsByKey[key] = labelsByKey[key] ?: brand
        }

    canonicalBrandOptionsForCategory(
        categoryCode = categoryCode,
        categoriesByCode = categoriesByCode,
    ).forEach { brand ->
        if (!shouldIncludeBrand(brand)) return@forEach
        val key = brand.lowercase(Locale.ROOT)
        labelsByKey[key] = labelsByKey[key] ?: brand
    }

    selectedBrands
        .asSequence()
        .mapNotNull { brand -> brand.trim().takeIf { value -> value.isNotBlank() } }
        .forEach { brand ->
            val key = brand.lowercase(Locale.ROOT)
            labelsByKey[key] = labelsByKey[key] ?: brand
        }

    return labelsByKey.entries
        .map { (key, label) ->
            BrandOptionItem(
                name = label,
                count = countsByKey[key],
            )
        }
        .sortedWith(compareBy<BrandOptionItem> { it.name.lowercase(Locale.ROOT) })
}

private fun resolveBrandScopeForBrandFacet(
    categoryCode: String?,
    selectedBrands: Set<String>,
    typedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
    query: NormalizedQuery?,
): Set<String> {
    val selectedModels = selectedTypedFacetValues(
        typedAttributeFilters = typedAttributeFilters,
        facetKey = "model",
    )
    if (selectedModels.isEmpty()) return emptySet()
    val preferredBrands = selectedBrands
        .asSequence()
        .mapNotNull { brand -> brand.trim().takeIf { value -> value.isNotEmpty() } }
        .toList()
    val fallbackBrand = resolveTypedFacetLookupBrand(
        selectedBrands = selectedBrands,
        query = query,
    )
    return selectedModels
        .mapNotNull { model ->
            resolveTypedFacetLookupScopeForModel(
                model = model,
                categoryCode = categoryCode,
                preferredBrands = preferredBrands,
                fallbackBrand = fallbackBrand,
            )?.brand
        }
        .toCollection(LinkedHashSet())
}

internal fun canonicalBrandOptionsForCategory(
    categoryCode: String?,
    categoriesByCode: Map<String, Category> = emptyMap(),
): List<String> {
    val normalizedCategoryCode = resolveCanonicalFacetCategoryCode(
        categoryCode = categoryCode,
        categoriesByCode = categoriesByCode,
    ) ?: return emptyList()

    val labelsByKey = LinkedHashMap<String, String>()
    CatalogCanonicalProductFamilyRegistry.families()
        .asSequence()
        .filter { family -> family.defaultCategoryCode == normalizedCategoryCode }
        .map { family -> family.brandCanonical }
        .forEach { brand ->
            val normalizedBrand = brand.trim().takeIf { value -> value.isNotEmpty() } ?: return@forEach
            labelsByKey.putIfAbsent(normalizedBrand.lowercase(Locale.ROOT), normalizedBrand)
        }
    CatalogCanonicalModelRegistry.models()
        .asSequence()
        .filter { model -> model.defaultCategoryCode == normalizedCategoryCode }
        .map { model -> model.brandCanonical }
        .forEach { brand ->
            val normalizedBrand = brand.trim().takeIf { value -> value.isNotEmpty() } ?: return@forEach
            labelsByKey.putIfAbsent(normalizedBrand.lowercase(Locale.ROOT), normalizedBrand)
        }
    return labelsByKey.values.toList()
}

internal data class SanitizedBrandSelection(
    val brands: Set<String>,
    val interpretedBrandSeed: String?,
)

internal fun sanitizeSelectedBrandsForCategory(
    selectedBrands: Set<String>,
    interpretedBrandSeed: String?,
    categoryCode: String?,
    categoriesByCode: Map<String, Category> = emptyMap(),
): SanitizedBrandSelection {
    val normalizedBrands = selectedBrands
        .asSequence()
        .mapNotNull { brand -> brand.trim().takeIf { value -> value.isNotBlank() } }
        .distinctBy { brand -> brand.lowercase(Locale.ROOT) }
        .toCollection(LinkedHashSet())
    val normalizedSeed = interpretedBrandSeed?.trim()?.takeIf { value -> value.isNotBlank() }
    val canonicalBrands = canonicalBrandOptionsForCategory(
        categoryCode = categoryCode,
        categoriesByCode = categoriesByCode,
    )
    if (canonicalBrands.isEmpty()) {
        return SanitizedBrandSelection(
            brands = if (categoryCode.isNullOrBlank()) normalizedBrands else emptySet(),
            interpretedBrandSeed = if (categoryCode.isNullOrBlank()) normalizedSeed else null,
        )
    }

    val canonicalByKey = canonicalBrands.associateByTo(LinkedHashMap()) { brand ->
        brand.lowercase(Locale.ROOT)
    }
    val sanitizedBrands = normalizedBrands
        .mapNotNull { brand -> canonicalByKey[brand.lowercase(Locale.ROOT)] }
        .toCollection(LinkedHashSet())
    val sanitizedSeed = normalizedSeed?.let { brand -> canonicalByKey[brand.lowercase(Locale.ROOT)] }

    return SanitizedBrandSelection(
        brands = sanitizedBrands,
        interpretedBrandSeed = sanitizedSeed,
    )
}

private fun resolveCanonicalFacetCategoryCode(
    categoryCode: String?,
    categoriesByCode: Map<String, Category> = emptyMap(),
): String? {
    val normalizedCode = categoryCode
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?.uppercase(Locale.ROOT)
        ?: return null
    if (categoriesByCode.isEmpty()) {
        return normalizedCode
    }
    return resolveResultsCategoryCode(
        categoryCode = normalizedCode,
        categories = categoriesByCode.values.toList(),
    )
}

private fun availablePriceBounds(items: List<ExplainedItem>): PriceBounds {
    val observedMax = items
        .mapNotNull { it.dto.price?.toInt() }
        .maxOrNull()
        ?: PRICE_FILTER_FALLBACK_MAX
    return PriceBounds(
        min = 0,
        max = observedMax,
    )
}

private fun categoryPathTitles(
    code: String?,
    categoriesByCode: Map<String, Category>,
): List<String> =
    buildCategoryPath(code, categoriesByCode).map { pathCode ->
        categoriesByCode[pathCode]?.displayTitle(locale = RESULTS_UI_LOCALE) ?: pathCode
    }

internal fun resolveResultsCategorySummary(
    categoryCode: String?,
    categoryPath: List<String>,
    categoriesByCode: Map<String, Category>,
    localeTag: String = RESULTS_UI_LOCALE,
): String {
    val normalizedCode = categoryCode?.trim().orEmpty()
    if (normalizedCode.isBlank()) return "Все категории"

    categoriesByCode[normalizedCode]
        ?.displayTitle(locale = localeTag)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    buildCategoryPath(normalizedCode, categoriesByCode)
        .lastOrNull()
        ?.let { leafCode ->
            categoriesByCode[leafCode]
                ?.displayTitle(locale = localeTag)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

    categoryPath
        .lastOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return normalizedCode
}

private fun formatPrice(value: Int): String =
    String.format(Locale.forLanguageTag("ru-RU"), "%,d ₽", value)

private fun applyFacetPreset(
    base: FilterState,
    collection: FacetCollection?,
    preset: FacetPreset?,
    definitions: List<FacetDefinition>,
): FilterState {
    val applied = FacetRuntimeFiltersApplier.apply(
        base = base.toFacetRuntimeFilters(definitions),
        collection = collection,
        preset = preset,
        definitions = definitions,
    )
    return base.copy(
        categoryCode = applied.categoryCode,
        categoryPath = emptyList(),
        facetCollectionCode = applied.facetCollectionCode,
        facetPresetCode = applied.facetPresetCode,
        presetAttributes = applied.attributes.toRawStringAttributes(),
        typedAttributeFilters = applied.attributeFilters.toTypedAttributeFilterDrafts(),
        brands = applied.brands,
        priceMin = applied.priceMin,
        priceMax = applied.priceMax,
        conditions = mapConditionOptions(applied.conditions.toList()),
        purchaseFormat = applied.purchaseFormat.toPurchaseFormat(base.purchaseFormat),
    )
}

private fun FilterState.toFacetRuntimeFilters(
    definitions: List<FacetDefinition>,
): FacetRuntimeFilters = FacetRuntimeFilters(
    categoryCode = categoryCode,
    facetCollectionCode = facetCollectionCode,
    facetPresetCode = facetPresetCode,
    attributes = presetAttributes.toTypedAttributesGuess(),
    attributeFilters = buildTypedAttributeFilters(
        drafts = typedAttributeFilters,
        definitions = definitions,
    ),
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
    categoryCode: String?,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDetails: () -> Unit,
    onOverflowAction: (OfferOverflowAction) -> Unit,
): OfferCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Объявление" }
    val badges = (
        buildResultsProductBadges(dto = dto, categoryCode = categoryCode) +
            dto.sellerBadges.map { CardBadge(label = normalizeBadgeLabel(it)) }
        )
        .distinctBy { badge -> badge.label.lowercase(Locale.ROOT) }
        .sortedByDescending { badge -> badge.priority }
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

internal fun buildResultsProductBadges(
    dto: ProductDto,
    categoryCode: String?,
    nowYear: Int = LocalDate.now().year,
): List<CardBadge> {
    if (!isResultsPhoneCategory(categoryCode)) return emptyList()

    val badges = buildList {
        val releaseYear = dto.resultsAttributeValue("release_year")?.asDoubleOrNull()?.toInt()
        if (releaseYear != null && releaseYear >= nowYear - 1) {
            add(CardBadge(label = "Новинка", priority = 220))
        }
        if (dto.resultsAttributeValue("esim_support")?.asBooleanOrNull() == true) {
            add(CardBadge(label = "eSIM", priority = 210))
        }
        dto.resultsAttributeValue("ip_rating")
            ?.asRawString()
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?.let { value -> add(CardBadge(label = value, priority = 200)) }
        val wiredCharging = dto.resultsAttributeValue("wired_charging_w")?.asDoubleOrNull()
        if (wiredCharging != null && wiredCharging >= 45.0) {
            add(CardBadge(label = "${wiredCharging.toInt()}W+", priority = 190))
        }
        if (dto.resultsAttributeValue("wireless_charging")?.asBooleanOrNull() == true) {
            add(CardBadge(label = "Беспроводная", priority = 180))
        }
        val batteryMah = dto.resultsAttributeValue("battery_mah")?.asDoubleOrNull()
        if (batteryMah != null && batteryMah >= 5000.0) {
            add(CardBadge(label = "${batteryMah.toInt()} мА·ч", priority = 170))
        }
    }

    return badges
        .distinctBy { badge -> badge.label.lowercase(Locale.ROOT) }
        .sortedByDescending { badge -> badge.priority }
        .take(2)
}

internal fun supportsResultsModelFreshnessSort(categoryCode: String?): Boolean {
    val profile = CatalogFacetPresentationProfiles.resolve(categoryCode) ?: return false
    return buildSet {
        addAll(profile.mainTypedFacetKeys)
        addAll(profile.additionalTypedFacetKeys)
        addAll(profile.orderedTypedFacetKeys)
    }.contains("release_year")
}

internal fun isResultsPhoneCategory(categoryCode: String?): Boolean {
    val normalizedCategoryCode = categoryCode
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
        ?: return false
    return normalizedCategoryCode == "TECH.PHONES" || normalizedCategoryCode.startsWith("TECH.PHONES.")
}

private fun ProductDto.resultsAttributeValue(attributeCode: String): TypedAttributeValue? {
    val normalizedAttributeCode = attributeCode.trim().lowercase(Locale.ROOT)
    return attributes[normalizedAttributeCode]
        ?: attributes.entries.firstOrNull { (key, _) ->
            key.trim().lowercase(Locale.ROOT) == normalizedAttributeCode
        }?.value
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
