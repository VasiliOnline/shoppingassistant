
package com.example.shoppingassistant.feature.pages.main.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.data.menu.UserPanelDefaults
import com.example.shoppingassistant.core.data.menu.UserPanelStore
import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.UrlNormalizer
import com.example.shoppingassistant.domain.catalog.BrowseTargetType
import com.example.shoppingassistant.domain.catalog.GetBrowseNodeTask
import com.example.shoppingassistant.domain.catalog.QueryRouteType
import com.example.shoppingassistant.domain.catalog.RouteQueryTask
import com.example.shoppingassistant.domain.menu.ActionKey
import com.example.shoppingassistant.domain.menu.ModeKey
import com.example.shoppingassistant.domain.menu.UserPanel
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.search.InterpretedSearchIntent
import com.example.shoppingassistant.domain.search.SearchInterpretationDependencies
import com.example.shoppingassistant.domain.search.SearchInterpretationPipeline
import com.example.shoppingassistant.domain.search.SearchInterpretationRequest
import com.example.shoppingassistant.domain.search.SearchRequestSource
import com.example.shoppingassistant.domain.search.SearchRouteResult
import com.example.shoppingassistant.domain.search.SearchSuggestionCandidate
import com.example.shoppingassistant.domain.search.SearchSuggestionKind
import com.example.shoppingassistant.domain.search.SearchTemplateContext
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.ugc.draft.DraftInputOrigin
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.navigation.AppRoutes
import com.example.shoppingassistant.feature.pages.categories.FeedCategoriesPage
import com.example.shoppingassistant.feature.pages.common.normalizedQueryFromSnapshot
import com.example.shoppingassistant.feature.pages.draft.create.DraftMediaImporter
import com.example.shoppingassistant.feature.pages.main.link.LinkInputState
import com.example.shoppingassistant.feature.pages.main.state.CategoryChipUi
import com.example.shoppingassistant.feature.pages.main.state.MainPageViewModel
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchAssetUi
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchInsightUi
import com.example.shoppingassistant.feature.pages.main.suggest.AutoPresetSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.AutoTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.CategoryAnchorSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.HistoryTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.MainSuggestItem
import com.example.shoppingassistant.feature.pages.main.suggest.PresetTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.ProductAnchorSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.TextFixSuggest
import com.example.shoppingassistant.feature.pages.main.visualsearch.VisualSearchCameraScreen
import com.example.shoppingassistant.feature.pages.main.visualsearch.VisualSearchCaptureAnalysis
import com.example.shoppingassistant.feature.pages.main.visualsearch.VisualSearchCaptureAnalyzer
import com.example.shoppingassistant.feature.pages.main.state.PopularSearchQueryUi
import com.example.shoppingassistant.feature.pages.main.state.RecentSearchQueryUi
import com.example.shoppingassistant.feature.pages.results.ResultsOrigin
import com.example.shoppingassistant.feature.pages.results.ResultsPage
import com.example.shoppingassistant.feature.pages.results.ResultsPayload
import com.example.shoppingassistant.feature.pages.results.toResultsQuery
import com.example.shoppingassistant.feature.pages.localoffer.LocalOfferSheet
import com.example.shoppingassistant.feature.pages.trackeditems.TrackedItemsLoadState
import com.example.shoppingassistant.feature.pages.trackeditems.TrackedItemsState
import com.example.shoppingassistant.feature.pages.trackeditems.TrackedItemsViewModel
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOffersTab
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import com.example.shoppingassistant.feature.ui.cards.CardActionRules
import com.example.shoppingassistant.feature.ui.cards.CardBadge
import com.example.shoppingassistant.feature.ui.cards.CardMediaItem
import com.example.shoppingassistant.feature.ui.cards.CardTrustData
import com.example.shoppingassistant.feature.ui.cards.CompactCard
import com.example.shoppingassistant.feature.ui.cards.CompactCardUi
import com.example.shoppingassistant.feature.ui.cards.OfferCard
import com.example.shoppingassistant.feature.ui.cards.OfferCardUi
import com.example.shoppingassistant.feature.ui.cards.OfferOverflowAction
import com.example.shoppingassistant.feature.ui.cards.ScenarioCard
import com.example.shoppingassistant.feature.ui.cards.ScenarioCardUi
import com.example.shoppingassistant.feature.ui.cards.ScenarioCardSkeleton
import com.example.shoppingassistant.feature.ui.cards.formatLocationText
import com.example.shoppingassistant.feature.ui.cards.formatDistanceLabel
import com.example.shoppingassistant.feature.ui.cards.formatTimeOfDay
import com.example.shoppingassistant.feature.ui.cards.formatPriceText
import com.example.shoppingassistant.feature.ui.cards.formatRatingText
import com.example.shoppingassistant.feature.ui.cards.formatUpdatedAtText
import com.example.shoppingassistant.feature.ui.cards.normalizeBadgeLabel
import com.example.shoppingassistant.feature.ui.animations.SwipeBackSurface
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.layout.AppTopBar
import com.example.shoppingassistant.feature.ui.layout.ScreenRoot
import com.example.shoppingassistant.feature.ui.state.StateHost
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import com.example.shoppingassistant.feature.ui.state.model.ScreenState
import com.example.shoppingassistant.feature.ui.menu.UserPanelBar
import com.example.shoppingassistant.feature.ui.menu.UserPanelEditor
import kotlin.math.roundToInt
import com.example.shoppingassistant.feature.ui.menu.defaultActionCatalog
import com.example.shoppingassistant.feature.ui.menu.defaultModeCatalog
import com.example.shoppingassistant.feature.R
import com.example.shoppingassistant.core.ui.LocalProfileSettings
import com.example.shoppingassistant.core.data.nearby.DEFAULT_NEARBY_RADIUS_KM
import com.example.shoppingassistant.core.data.nearby.MAX_NEARBY_RADIUS_KM
import com.example.shoppingassistant.core.data.nearby.NEARBY_RADIUS_PRESETS
import com.example.shoppingassistant.core.data.nearby.NearbyBrand
import com.example.shoppingassistant.core.data.nearby.NearbyCondition
import com.example.shoppingassistant.core.data.nearby.NearbyFiltersState
import com.example.shoppingassistant.core.data.nearby.NearbyPlace
import com.example.shoppingassistant.core.data.nearby.NearbyPostedAt
import com.example.shoppingassistant.core.data.nearby.NearbyScope
import com.example.shoppingassistant.core.data.nearby.NearbySort
import com.example.shoppingassistant.feature.pages.main.state.NearbyFilterSection
import com.example.shoppingassistant.feature.pages.main.state.NearbyBrandFacet
import com.example.shoppingassistant.feature.pages.main.state.NearbyErrorKind
import com.example.shoppingassistant.feature.pages.main.state.NearbyErrorState
import com.example.shoppingassistant.feature.pages.main.state.NearbyLocationPermission
import com.example.shoppingassistant.feature.pages.main.state.NearbyRetryAction
import com.example.shoppingassistant.feature.pages.main.state.NearbyValueFacet
import com.example.shoppingassistant.feature.pages.main.state.defaultSort
import com.example.shoppingassistant.feature.pages.main.state.resetSection
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.io.File
import java.util.Base64
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.abs
import kotlin.coroutines.resume
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shoppingassistant.core.data.nearby.NearbyDelivery
import org.koin.java.KoinJavaComponent.get as koinGet

@Composable
fun MainPage(
    modifier: Modifier = Modifier,
    navController: NavHostController? = null,
    openSearchHubOnStart: Boolean = false,
    viewModel: MainPageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isGuest = state.currentUserId.isNullOrBlank()
    val trackedItemsViewModel: TrackedItemsViewModel? = if (isGuest) null else koinViewModel()
    val trackedItemsState by (trackedItemsViewModel?.state?.collectAsState()
        ?: remember { mutableStateOf(TrackedItemsState()) })
    val retryTrackedItems: () -> Unit = { trackedItemsViewModel?.load() }
    val context = LocalContext.current
    val visualMediaImporter = remember(context) { DraftMediaImporter(context) }
    val localIntentLabels = LocalSearchIntent.entries.associateWith { intent ->
        stringResource(localIntentLabelRes(intent))
    }
    val localIntentLocationRequired = stringResource(R.string.search_local_intent_location_required)
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val profileSettings = LocalProfileSettings.current
    val scope = rememberCoroutineScope()
    val sourceRegistry: SourceRegistry = remember { koinGet(SourceRegistry::class.java) }
    val urlNormalizer: UrlNormalizer = remember { koinGet(UrlNormalizer::class.java) }
    val createdStore: UserOffersCreatedStore = remember { koinGet(UserOffersCreatedStore::class.java) }
    val createdItems by createdStore.items.collectAsState()
    val userPanelStore: UserPanelStore = remember { koinGet(UserPanelStore::class.java) }
    val routeQueryTask: RouteQueryTask = remember { koinGet(RouteQueryTask::class.java) }
    val getBrowseNodeTask: GetBrowseNodeTask = remember { koinGet(GetBrowseNodeTask::class.java) }
    val userPanel by userPanelStore.panel.collectAsState()
    var nearbyPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var nearbyLocationResolving by rememberSaveable { mutableStateOf(false) }

    fun refreshNearbyCurrentLocation() {
        scope.launch {
            nearbyLocationResolving = true
            try {
                val snapshot = resolveCurrentLocation(context)
                if (snapshot.city == null && snapshot.countryCode == null &&
                    snapshot.lat == null && snapshot.lon == null
                ) {
                    viewModel.updateNearbyCurrentLocation(
                        city = null,
                        country = null,
                        lat = null,
                        lon = null,
                        addressLine = null,
                        replaceMissing = true,
                    )
                    viewModel.setNearbyError(
                        kind = NearbyErrorKind.Location,
                        message = "Не удалось определить местоположение",
                        action = null,
                    )
                } else {
                    viewModel.updateNearbyCurrentLocation(
                        snapshot.city,
                        snapshot.countryCode,
                        snapshot.lat,
                        snapshot.lon,
                        snapshot.addressLine,
                    )
                }
            } finally {
                nearbyLocationResolving = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        nearbyPermissionRequested = true
        val deniedPermanent = if (!granted) {
            val activity = context.findActivity()
            activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            } == true
        } else {
            false
        }
        val status = when {
            granted -> NearbyLocationPermission.Granted
            deniedPermanent -> NearbyLocationPermission.DeniedPermanent
            else -> NearbyLocationPermission.DeniedTemporary
        }
        viewModel.updateNearbyLocationPermission(status)
        FlowMetrics.markNearbyLocationPermissionResult(granted)
        if (granted) {
            refreshNearbyCurrentLocation()
        }
    }

    var visualCameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val visualCaptureAnalyzer = remember(context.applicationContext) {
        VisualSearchCaptureAnalyzer(context.applicationContext)
    }
    DisposableEffect(visualCaptureAnalyzer) {
        onDispose { visualCaptureAnalyzer.close() }
    }

    val visualGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val media = visualMediaImporter.importPhoto(uri)
            if (media == null) {
                Toast.makeText(context, "Не удалось импортировать фото.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val localUri = media.localUri?.toString() ?: return@launch
            val inlineBase64 = context.encodeVisualSearchInlineBase64(localUri)
            val captureMode = state.visualSearch.captureMode
            val analysis = runCatching {
                visualCaptureAnalyzer.analyzeUri(Uri.parse(localUri), captureMode)
            }.getOrNull()
            val insight = buildVisualSearchInsight(
                analysis = analysis,
                routeQueryTask = routeQueryTask,
                getBrowseNodeTask = getBrowseNodeTask,
                viewModel = viewModel,
            )
            viewModel.onVisualSearchAssetPicked(
                source = VisualSearchSource.GALLERY,
                captureMode = captureMode,
                insight = insight,
                asset = VisualSearchAssetUi(
                    fingerprint = media.id,
                    localUri = localUri,
                    widthPx = media.width,
                    heightPx = media.height,
                    byteSize = media.byteSize,
                    inlineBase64 = inlineBase64,
                ),
            )
        }
    }

    val visualCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        visualCameraPermissionGranted = granted
        val deniedPermanent = if (!granted) {
            val activity = context.findActivity()
            activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.CAMERA,
                )
            } == true
        } else {
            false
        }
        when {
            granted -> Unit
            deniedPermanent -> {
                Toast.makeText(
                    context,
                    "Доступ к камере отключён. Откройте настройки приложения.",
                    Toast.LENGTH_LONG,
                ).show()
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            else -> {
                Toast.makeText(context, "Для камеры нужен доступ.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val status = if (granted) {
            NearbyLocationPermission.Granted
        } else if (nearbyPermissionRequested) {
            val activity = context.findActivity()
            val deniedPermanent = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            } == true
            if (deniedPermanent) NearbyLocationPermission.DeniedPermanent else NearbyLocationPermission.DeniedTemporary
        } else {
            NearbyLocationPermission.Unknown
        }
        viewModel.updateNearbyLocationPermission(status)
        if (granted) {
            refreshNearbyCurrentLocation()
        }
    }

    var savedOfferIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var hiddenFeedOfferIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    var rootMode by rememberSaveable { mutableStateOf(RootMode.Dashboard) }
    var dashboardState by rememberSaveable { mutableStateOf(DashboardState.Overview) }
    var searchStage by rememberSaveable { mutableStateOf(SearchStage.Idle) }
    var searchHubOpenedFromRoute by rememberSaveable { mutableStateOf(false) }
    var createDraftOrigin by rememberSaveable { mutableStateOf<DraftInputOrigin?>(null) }
    var createDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var createDraftVisible by rememberSaveable { mutableStateOf(false) }
    var searchPayload by remember { mutableStateOf<ResultsPayload?>(null) }
    var searchLoadingQuery by rememberSaveable { mutableStateOf("") }
    var searchRequestVersion by rememberSaveable { mutableStateOf(0) }
    var linkOverlayVisible by rememberSaveable { mutableStateOf(false) }
    var linkOverlayMode by rememberSaveable { mutableStateOf(LinkOverlayMode.Create) }
    var linkInput by remember { mutableStateOf(LinkInputState()) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val nearbyFiltersApplied = state.nearbyFilters
    val showNearbyBrandFilter = state.nearbyLeafCategoryCode != null
    var nearbyFiltersDraft by remember { mutableStateOf(nearbyFiltersApplied) }
    var nearbyFiltersSection by rememberSaveable { mutableStateOf<NearbyFilterSection?>(null) }
    var nearbyCategoriesPickerVisible by rememberSaveable { mutableStateOf(false) }
    var nearbyReturnSection by rememberSaveable { mutableStateOf<NearbyFilterSection?>(null) }
    var nearbyDraftCategoryChips by remember { mutableStateOf<List<CategoryChipUi>>(emptyList()) }
    var nearbyDraftLeafCategoryCode by remember { mutableStateOf<String?>(null) }

    val speechToTextLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val transcript = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (transcript.isBlank()) return@rememberLauncherForActivityResult
        val normalizedTranscript = SearchTextNormalizer.normalize(transcript)
        viewModel.onQueryChange(normalizedTranscript)
        rootMode = RootMode.Search
        searchStage = SearchStage.Focused
    }
    var showAllSelectedFeed by remember { mutableStateOf(false) }
    var panelEditMode by rememberSaveable { mutableStateOf(false) }
    var panelDraft by remember { mutableStateOf<UserPanel?>(null) }
    val searchInterpretationPipeline = remember { SearchInterpretationPipeline() }
    val searchInterpretationDeps = remember(viewModel, routeQueryTask, getBrowseNodeTask) {
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
                runCatching { viewModel.resolveCategoryRedirect(categoryCode) }.getOrNull()

            override suspend fun inferCategory(
                queryText: String,
                baseFilters: Map<String, String>,
            ): String? = viewModel.inferLeafCategoryByFacets(
                queryText = queryText,
                baseFilters = baseFilters,
            )

            override suspend fun parseAttributes(
                queryText: String,
                categoryCode: String?,
                baseFilters: Map<String, String>,
            ): Map<String, String> = viewModel.parseSubmitAttributes(
                queryText = queryText,
                categoryCode = categoryCode,
                baseFilters = baseFilters,
            )
        }
    }

    val supportedSourcesHint = remember {
        val names = sourceRegistry.all()
            .filter { it.rolloutEnabled && it.capabilities.canIngest }
            .map { it.displayName }
            .sorted()
        if (names.isEmpty()) {
            "Поддерживаемые площадки пока недоступны."
        } else {
            "Поддерживаемые площадки: ${names.joinToString(", ")}."
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentUser()
        userPanelStore.load()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentUser()
                visualCameraPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(nearbyFiltersApplied, nearbyFiltersSection, nearbyCategoriesPickerVisible) {
        if (nearbyFiltersSection == null && !nearbyCategoriesPickerVisible) {
            nearbyFiltersDraft = nearbyFiltersApplied
        }
    }

    LaunchedEffect(nearbyFiltersDraft.categoryCodes) {
        nearbyDraftCategoryChips = viewModel.resolveNearbyCategoryChips(nearbyFiltersDraft.categoryCodes)
        nearbyDraftLeafCategoryCode = viewModel.resolveNearbyLeafCategoryCode(nearbyFiltersDraft.categoryCodes)
    }

    LaunchedEffect(nearbyFiltersDraft, nearbyFiltersSection, nearbyFiltersApplied) {
        val isDirty = nearbyFiltersDraft != nearbyFiltersApplied
        if (nearbyFiltersSection != null && isDirty) {
            viewModel.requestNearbyDraftCount(nearbyFiltersDraft)
        }
    }
    LaunchedEffect(
        nearbyFiltersApplied,
        state.nearbyOffers,
        nearbyFiltersSection,
        nearbyCategoriesPickerVisible,
    ) {
        if (nearbyFiltersSection != null || nearbyCategoriesPickerVisible) return@LaunchedEffect
        val hasActiveFilters = nearbyFiltersApplied.activeCount(nearbyFiltersApplied.categoryCodes.isNotEmpty()) > 0
        if (!hasActiveFilters || state.nearbyOffers.isNotEmpty()) return@LaunchedEffect
        viewModel.requestNearbyDraftCount(NearbyFiltersState())
    }
    LaunchedEffect(state.nearbyError) {
        state.nearbyError?.let { error ->
            FlowMetrics.markNearbyFiltersErrorShown(error.kind.name.lowercase())
        }
    }

    val visualSearchCategoryOptions = remember(
        state.template.categoryCode,
        state.template.lockedTitle,
        state.frequentCategories,
        state.popularCategories,
        state.nearbyCategoryChips,
        state.visualSearch.selectedCategoryCode,
        state.visualSearch.selectedCategoryTitle,
        state.visualSearch.insight?.suggestedCategoryCode,
        state.visualSearch.insight?.suggestedCategoryTitle,
    ) {
        buildList {
            val selectedCode = state.visualSearch.selectedCategoryCode
            val selectedTitle = state.visualSearch.selectedCategoryTitle
                ?: state.template.lockedTitle
                ?: selectedCode
            if (!selectedCode.isNullOrBlank() && !selectedTitle.isNullOrBlank()) {
                add(
                    CategoryChipUi(
                        code = selectedCode,
                        title = selectedTitle,
                    ),
                )
            }
            val suggestedCode = state.visualSearch.insight?.suggestedCategoryCode
            val suggestedTitle = state.visualSearch.insight?.suggestedCategoryTitle
            if (!suggestedCode.isNullOrBlank() && !suggestedTitle.isNullOrBlank() && suggestedCode != selectedCode) {
                add(
                    CategoryChipUi(
                        code = suggestedCode,
                        title = suggestedTitle,
                    ),
                )
            }
            addAll(state.frequentCategories)
            addAll(state.popularCategories)
            addAll(state.nearbyCategoryChips)
        }.distinctBy { chip -> chip.code }
    }

    val panelConfig = UserPanelDefaults.config
    val modeCatalog = remember(state.currentUserId) {
        defaultModeCatalog(isLoggedIn = !isGuest)
    }
    val actionCatalog = remember { defaultActionCatalog() }

    fun startPanelEdit() {
        panelDraft = (panelDraft ?: userPanel).copy(isEditMode = true)
        panelEditMode = true
    }

    fun updatePanelDraft(updated: UserPanel) {
        panelDraft = updated.copy(isEditMode = true)
    }

    fun commitPanelEdit() {
        val draft = (panelDraft ?: userPanel).copy(isEditMode = false)
        scope.launch { userPanelStore.update(draft) }
        panelEditMode = false
        panelDraft = null
    }

    fun cancelPanelEdit() {
        panelEditMode = false
        panelDraft = null
    }

    fun resetPanelEdit() {
        panelDraft = UserPanelDefaults.defaultPanel().copy(isEditMode = true)
    }

    val navigateToResults: (ResultsPayload, Int) -> Unit = { payload, taps ->
        FlowMetrics.startResults(taps)
        searchLoadingQuery = payload.queryText
        searchPayload = payload
        searchStage = SearchStage.Results
        rootMode = RootMode.Search
    }

    fun invalidateSearchRequest() {
        searchRequestVersion += 1
    }

    fun showResultsLoading(queryText: String): Int {
        val requestVersion = searchRequestVersion + 1
        searchRequestVersion = requestVersion
        searchLoadingQuery = queryText.trim()
        searchPayload = null
        searchStage = SearchStage.Results
        rootMode = RootMode.Search
        return requestVersion
    }

    fun stopResultsLoading() {
        invalidateSearchRequest()
        searchPayload = null
        searchLoadingQuery = ""
        searchStage = if (state.template.inputText.isBlank()) SearchStage.Idle else SearchStage.Focused
        rootMode = RootMode.Search
    }

    fun closeResultsToSearchHub() {
        invalidateSearchRequest()
        focusManager.clearFocus(force = true)
        searchPayload = null
        searchLoadingQuery = ""
        searchStage = SearchStage.Idle
        rootMode = RootMode.Search
        viewModel.resetTemplate()
    }

    LaunchedEffect(state.visualSearch.pendingResultsPayload) {
        val payload = state.visualSearch.pendingResultsPayload ?: return@LaunchedEffect
        focusManager.clearFocus(force = true)
        viewModel.recordSearchHistory(
            queryText = payload.queryText,
            query = payload.query,
            categoryCode = payload.categoryCode,
        )
        navigateToResults(payload, 1)
        viewModel.consumePendingVisualResults()
    }

    fun openCreateFlow(origin: DraftInputOrigin?) {
        createDraftVisible = true
        createDraftOrigin = origin
        createDraftId = null
    }

    fun openVisualSearch() {
        focusManager.clearFocus(force = true)
        viewModel.openVisualSearchEntry()
        if (!visualCameraPermissionGranted) {
            visualCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openVisualGallery() {
        focusManager.clearFocus(force = true)
        visualGalleryLauncher.launch("image/*")
    }

    fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите поисковый запрос")
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "Распознавание речи недоступно на устройстве.", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus(force = true)
        speechToTextLauncher.launch(intent)
    }

    fun showLegacySearchFlowRemoved(channel: String) {
        val message = when (channel) {
            "photo" -> "Поиск по фото будет возвращён новым flow. Legacy wizard удалён."
            "link" -> "Поиск по ссылке будет возвращён новым flow. Legacy wizard удалён."
            "voice" -> "Поиск голосом будет возвращён новым flow. Legacy wizard удалён."
            else -> "Legacy search flow удалён."
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun isCategoryLevelFilterKey(key: String): Boolean =
        key.startsWith("category_level_") || key == "category_selector"

    fun stripCategoryLevelFilters(filters: Map<String, String>): Map<String, String> =
        filters.filterKeys { key -> !isCategoryLevelFilterKey(key) }

    fun candidateFromSuggestion(item: MainSuggestItem): SearchSuggestionCandidate? = when (item) {
        is TextFixSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.TEXT_FIX,
            text = item.fixedText,
        )
        is ProductAnchorSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.PRODUCT_ANCHOR,
            text = item.text,
            categoryCode = item.categoryCode,
            attrs = buildMap {
                item.brand?.takeIf { value -> value.isNotBlank() }?.let { put("brand", it) }
                item.model?.takeIf { value -> value.isNotBlank() }?.let { put("model", it) }
            },
        )
        is CategoryAnchorSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.CATEGORY_ANCHOR,
            text = item.breadcrumb.ifBlank { item.categoryCode },
            categoryCode = item.categoryCode,
        )
        is PresetTemplateSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.PRESET_TEMPLATE,
            text = item.text,
            categoryCode = item.preset.snapshot.categoryCode,
            attrs = item.preset.snapshot.attrs
                .mapNotNull { attr ->
                    val key = attr.key.trim()
                    val value = attr.value.trim()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                .toMap(LinkedHashMap()),
        )
        is HistoryTemplateSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.HISTORY_TEMPLATE,
            text = item.text,
            categoryCode = item.entry.snapshot.data.categoryCode,
            attrs = item.entry.snapshot.data.attrs
                .mapNotNull { attr ->
                    val key = attr.key.trim()
                    val value = attr.value.trim()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                .toMap(LinkedHashMap()),
        )
        is AutoPresetSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.AUTO_PRESET,
            text = item.text,
        )
        is AutoTemplateSuggest -> SearchSuggestionCandidate(
            kind = SearchSuggestionKind.AUTO_TEMPLATE,
            text = item.text,
            categoryCode = item.categoryCode,
            attrs = buildMap {
                item.brand.takeIf { value -> value.isNotBlank() }?.let { put("brand", it) }
                item.model.takeIf { value -> value.isNotBlank() }?.let { put("model", it) }
                item.modelLine?.takeIf { value -> value.isNotBlank() }?.let { put("model_line", it) }
            },
        )
        else -> null
    }

    fun suggestionCandidates(): List<SearchSuggestionCandidate> =
        state.suggestions.mapNotNull(::candidateFromSuggestion)

    fun templateContextForInterpretation(): SearchTemplateContext = SearchTemplateContext(
        inputText = state.template.inputText,
        categoryCode = state.template.categoryCode,
        selectedFilters = stripCategoryLevelFilters(state.template.asSelectedFilters()),
        isLocked = state.template.isLocked,
    )

    suspend fun interpretSearchIntent(
        text: String,
        source: SearchRequestSource,
        selectedSuggestion: SearchSuggestionCandidate? = null,
    ): InterpretedSearchIntent {
        val request = SearchInterpretationRequest(
            inputText = text,
            source = source,
            selectedSuggestion = selectedSuggestion,
            suggestions = suggestionCandidates(),
            templateContext = templateContextForInterpretation(),
        )
        return searchInterpretationPipeline.interpret(request, searchInterpretationDeps)
    }

    fun resultsOriginFromIntent(intent: InterpretedSearchIntent): ResultsOrigin = when {
        intent.provenance.source == SearchRequestSource.SUGGESTION &&
            intent.provenance.suggestionKind == SearchSuggestionKind.CATEGORY_ANCHOR -> ResultsOrigin.Category
        intent.provenance.source == SearchRequestSource.SUGGESTION -> ResultsOrigin.Suggestion
        else -> ResultsOrigin.Text
    }

    fun payloadFromIntent(intent: InterpretedSearchIntent): ResultsPayload {
        return ResultsPayload(
            query = intent.toResultsQuery(),
            queryText = intent.queryText,
            categoryCode = intent.categoryCode,
            facetCollectionCode = intent.facetCollectionCode,
            facetPresetCode = intent.facetPresetCode,
            origin = resultsOriginFromIntent(intent),
        )
    }

    suspend fun openResultsFromIntent(
        intent: InterpretedSearchIntent,
        openedFromSuggestion: Boolean,
    ) {
        FlowMetrics.markEvent(
            "search_interpretation_applied",
            "source=${intent.provenance.source.name.lowercase()} kind=${intent.provenance.suggestionKind?.name?.lowercase() ?: "none"} template_ctx=${intent.provenance.usedTemplateContext} route_category=${intent.provenance.usedRouteCategory} inferred_category=${intent.provenance.usedInferredCategory} parsed_attrs=${intent.provenance.usedParsedAttributes}",
        )
        val payload = payloadFromIntent(intent)
        if (intent.provenance.usedInferredCategory && !intent.categoryCode.isNullOrBlank()) {
            FlowMetrics.markEvent(
                "search_submit_category_inferred",
                "source=facet_fallback category=${intent.categoryCode}",
            )
        }
        if (openedFromSuggestion) {
            FlowMetrics.markOpenResultsFromSuggestion()
        } else {
            FlowMetrics.markOpenResultsFromText()
        }
        viewModel.recordSearchHistory(
            queryText = payload.queryText,
            query = payload.query,
            categoryCode = payload.categoryCode,
        )
        navigateToResults(payload, 1)
    }

    fun startSearchByText(text: String) {
        val normalizedInput = SearchTextNormalizer.normalize(text)
        if (normalizedInput.isBlank()) {
            Toast.makeText(context, "Введите запрос", Toast.LENGTH_SHORT).show()
            return
        }
        val requestVersion = showResultsLoading(normalizedInput)
        scope.launch {
            val intent = runCatching {
                interpretSearchIntent(
                    text = normalizedInput,
                    source = SearchRequestSource.RAW_TEXT,
                )
            }.getOrElse { throwable ->
                stopResultsLoading()
                Toast.makeText(
                    context,
                    throwable.message ?: "Не удалось подготовить результаты поиска.",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            if (requestVersion != searchRequestVersion) return@launch
            openResultsFromIntent(intent, openedFromSuggestion = false)
        }
    }

    fun startSearchBySuggestion(item: MainSuggestItem) {
        val selectedSuggestion = candidateFromSuggestion(item)
        if (selectedSuggestion == null) {
            viewModel.onSuggestChosen(item)
            return
        }
        val requestVersion = showResultsLoading(selectedSuggestion.text)
        scope.launch {
            val intent = runCatching {
                interpretSearchIntent(
                    text = selectedSuggestion.text,
                    source = SearchRequestSource.SUGGESTION,
                    selectedSuggestion = selectedSuggestion,
                )
            }.getOrElse { throwable ->
                stopResultsLoading()
                Toast.makeText(
                    context,
                    throwable.message ?: "Не удалось подготовить результаты поиска.",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            if (requestVersion != searchRequestVersion) return@launch
            openResultsFromIntent(intent, openedFromSuggestion = true)
        }
    }

    fun startLocalIntent(
        intent: LocalSearchIntent,
        label: String,
        locationRequiredMessage: String,
    ) {
        val location = listOf(state.nearbyUserLocation, state.nearbyProfileCity)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
        if (location.isNullOrBlank()) {
            Toast.makeText(
                context,
                locationRequiredMessage,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val radius = state.nearbyFilters.radiusKm.coerceIn(
            NEARBY_RADIUS_PRESETS.first(),
            NEARBY_RADIUS_PRESETS.last(),
        )
        val conditions = when (intent) {
            LocalSearchIntent.Used -> listOf("used")
            LocalSearchIntent.New -> listOf("new")
            else -> emptyList()
        }
        val sort = when (intent) {
            LocalSearchIntent.Today -> OfferSort.NEWEST
            else -> OfferSort.RANK
        }
        val payload = ResultsPayload(
            queryText = label,
            location = location,
            radiusKm = radius,
            conditions = conditions,
            sort = sort,
            origin = ResultsOrigin.Suggestion,
        )
        FlowMetrics.markOpenResultsFromSuggestion()
        navigateToResults(payload, 1)
    }

    fun openSearchHub(expanded: Boolean) {
        invalidateSearchRequest()
        rootMode = RootMode.Search
        searchStage = if (expanded) SearchStage.Focused else SearchStage.Idle
        searchPayload = null
        searchLoadingQuery = ""
        if (expanded) {
            viewModel.refreshSuggestionsForCurrentInput()
        }
    }

    LaunchedEffect(openSearchHubOnStart) {
        if (!openSearchHubOnStart) {
            searchHubOpenedFromRoute = false
            return@LaunchedEffect
        }
        if (!searchHubOpenedFromRoute) {
            openSearchHub(expanded = true)
            searchHubOpenedFromRoute = true
        }
    }

    fun startManualCreate() {
        createDraftOrigin = DraftInputOrigin.TEXT
        createDraftId = null
        createDraftVisible = true
        linkOverlayVisible = false
        linkError = null
    }

    fun openCreateSheet() {
        createDraftVisible = true
        createDraftOrigin = null
        createDraftId = null
        linkOverlayVisible = false
        linkError = null
    }

    fun dismissCreateSheet() {
        createDraftVisible = false
        createDraftOrigin = null
        createDraftId = null
        linkOverlayVisible = false
        linkError = null
    }

    fun openCreateOffer() {
        openCreateSheet()
        focusManager.clearFocus(force = true)
    }

    fun openDraftById(draftId: String) {
        createDraftVisible = true
        createDraftOrigin = null
        createDraftId = draftId
        linkOverlayVisible = false
        linkError = null
        focusManager.clearFocus(force = true)
    }

    fun toggleSavedOffer(offerId: String) {
        savedOfferIds = if (savedOfferIds.contains(offerId)) {
            savedOfferIds - offerId
        } else {
            savedOfferIds + offerId
        }
    }

    fun hideFeedOffer(offerId: String) {
        hiddenFeedOfferIds = hiddenFeedOfferIds + offerId
    }

    fun openExternal(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val canOpen = intent.resolveActivity(context.packageManager) != null
            if (canOpen) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No app available to open the link.", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, "Could not open the link.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareExternal(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            val chooser = Intent.createChooser(intent, "Share link")
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Could not share the link.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyLink(url: String) {
        scope.launch {
            val clipData = ClipData.newPlainText("link", url)
            clipboard.setClipEntry(ClipEntry(clipData))
        }
        Toast.makeText(context, "Link copied.", Toast.LENGTH_SHORT).show()
    }

    fun openFeedOffer(item: ExplainedItem) {
        if (navController != null) {
            navController.navigate(
                AppRoutes.offer(
                    offerId = item.dto.id,
                ),
            )
        } else {
            val url = item.dto.externalUrl
            if (!url.isNullOrBlank()) {
                openExternal(url)
            } else {
                Toast.makeText(context, "No details link available.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleFeedOverflow(action: OfferOverflowAction, item: ExplainedItem) {
        val url = item.dto.externalUrl
        when (action) {
            OfferOverflowAction.Share -> {
                if (!url.isNullOrBlank()) shareExternal(url)
            }
            OfferOverflowAction.Hide -> hideFeedOffer(item.dto.id)
            OfferOverflowAction.Report -> {
                Toast.makeText(context, "Thanks, we will review this offer.", Toast.LENGTH_SHORT).show()
            }
            OfferOverflowAction.CopyLink -> {
                if (!url.isNullOrBlank()) copyLink(url)
            }
        }
    }

    fun handleModeClick(modeKey: ModeKey) {
        when (modeKey) {
            ModeKey.DASHBOARD -> {
                rootMode = RootMode.Dashboard
                dashboardState = DashboardState.Overview
                searchStage = SearchStage.Idle
            }
            ModeKey.CHAT -> {
                rootMode = RootMode.Dashboard
                dashboardState = DashboardState.MessagesExpanded
                searchStage = SearchStage.Idle
            }
            ModeKey.PROFILE -> {
                navController?.navigate(AppRoutes.profile())
            }
            else -> Unit
        }
        focusManager.clearFocus(force = true)
    }

    val openTrackedItemsAll: () -> Unit = {
        navController?.navigate(AppRoutes.TrackedItems)
        Unit
    }
    val openTrackedItem: (String) -> Unit = { trackId ->
        if (trackId.isNotBlank()) {
            navController?.navigate(AppRoutes.trackedItemsTop10(trackId))
        }
    }

    fun handleActionClick(actionKey: ActionKey) {
        when (actionKey) {
            ActionKey.POST -> openSearchHub(expanded = true)
            else -> Unit
        }
        focusManager.clearFocus(force = true)
    }

    fun requestNearbyLocationPermission() {
        nearbyPermissionRequested = true
        FlowMetrics.markNearbyLocationPermissionPrompt()
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.updateNearbyLocationPermission(NearbyLocationPermission.Granted)
            refreshNearbyCurrentLocation()
            return
        }
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openLocationSettings() {
        openAppSettings()
    }

    fun expandNearbyRadius() {
        val current = nearbyFiltersApplied.radiusKm
        val next = MAX_NEARBY_RADIUS_KM
        if (next == current) return
        viewModel.applyNearbyFilters(
            nearbyFiltersApplied.copy(locationScope = NearbyScope.NEARBY, radiusKm = next)
        )
    }

    fun showCityPopular() {
        if (state.nearbyLocationPermission == NearbyLocationPermission.Granted) return
        val updatedSort =
            if (nearbyFiltersApplied.sort == NearbySort.Distance) NearbySort.Newest else nearbyFiltersApplied.sort
        viewModel.applyNearbyFilters(
            nearbyFiltersApplied.copy(
                locationScope = NearbyScope.CITY,
                sort = updatedSort,
            )
        )
    }

    fun openNearbyFilters(section: NearbyFilterSection) {
        FlowMetrics.markNearbyFiltersOpen("chip_${section.name.lowercase()}")
        if (section == NearbyFilterSection.Category) {
            nearbyCategoriesPickerVisible = true
            nearbyReturnSection = null
            nearbyFiltersSection = null
            return
        }
        nearbyFiltersSection = section
    }

    fun dismissNearbyFilters() {
        nearbyFiltersSection = null
        nearbyFiltersDraft = nearbyFiltersApplied
        nearbyCategoriesPickerVisible = false
        nearbyReturnSection = null
    }

    fun closeNearbyCategoriesPicker(reopenSheet: Boolean) {
        nearbyCategoriesPickerVisible = false
        if (reopenSheet && nearbyReturnSection != null) {
            nearbyFiltersSection = nearbyReturnSection
        }
        nearbyReturnSection = null
    }

    fun applyNearbyFilters() {
        val activeCount = nearbyFiltersDraft.activeCount(nearbyFiltersDraft.categoryCodes.isNotEmpty())
        val summary = buildNearbySummary(
            filters = nearbyFiltersDraft,
            categories = nearbyDraftCategoryChips,
            userLocation = state.nearbyUserLocation,
            userCountry = state.nearbyUserCountry,
        )
        FlowMetrics.markNearbyFiltersApply(activeCount, summary)
        viewModel.applyNearbyFilters(nearbyFiltersDraft)
        nearbyFiltersSection = null
    }

    fun resetNearbyFilter(section: NearbyFilterSection) {
        viewModel.resetNearbyFilter(section)
    }

    fun resetNearbyFiltersAll() {
        viewModel.applyNearbyFilters(NearbyFiltersState())
        FlowMetrics.markNearbyFiltersClearAll()
    }

    fun validateUrl(raw: String): Boolean =
        urlNormalizer.normalize(raw).host != null

    fun openLinkOverlay(mode: LinkOverlayMode) {
        linkOverlayMode = mode
        linkInput = LinkInputState()
        linkError = null
        linkOverlayVisible = true
    }

    fun submitLink() {
        val url = linkInput.url.trim()
        if (!validateUrl(url)) {
            linkInput = linkInput.copy(isValid = false)
            linkError = "Некорректная ссылка. Проверьте домен и формат."
            return
        }
        linkError = null
        linkOverlayVisible = false
        when (linkOverlayMode) {
            LinkOverlayMode.Create -> {
                FlowMetrics.markCreateAction("link", "create")
                openCreateFlow(DraftInputOrigin.LINK)
            }
            LinkOverlayMode.Search -> {
                showLegacySearchFlowRemoved("link")
            }
        }
    }

    fun normalizedQueryForText(
        text: String,
        attrs: Map<String, String> = emptyMap(),
    ): NormalizedQuery = BrandModelRules.fromRaw(
        text,
        Normalization.normalizeAttrs(attrs),
    )

    fun autoTemplateAttrs(item: AutoTemplateSuggest): Map<String, String> = buildMap {
        item.brand.takeIf { it.isNotBlank() }?.let { put("brand", it) }
        item.model.takeIf { it.isNotBlank() }?.let { put("model", it) }
        item.modelLine?.takeIf { it.isNotBlank() }?.let { put("model_line", it) }
    }

    fun trackSuggestion(item: MainSuggestItem) {
        FlowMetrics.markTrackClicked("main")
        navController?.navigate(AppRoutes.TrackedItems)
        Toast.makeText(
            context,
            "Создайте отслеживание в разделе «Отслеживаемые товары».",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val offerCounts = remember(createdItems) {
        val drafts = createdItems.count { it.status == UserOfferStatus.DRAFT }
        val active = createdItems.count {
            it.status == UserOfferStatus.ACTIVE ||
                it.status == UserOfferStatus.PAUSED
        }
        val completed = createdItems.count {
            it.status == UserOfferStatus.FINISHED || it.status == UserOfferStatus.ARCHIVED
        }
        OfferCounts(total = createdItems.size, active = active, drafts = drafts, completed = completed)
    }

    val offersPreviewItems = remember(createdItems, offerCounts) {
        mapOf(
            UserOffersTab.ACTIVE to createdItems.filter {
                it.status == UserOfferStatus.ACTIVE || it.status == UserOfferStatus.PAUSED
            },
            UserOffersTab.DRAFTS to createdItems.filter { it.status == UserOfferStatus.DRAFT },
            UserOffersTab.COMPLETED to createdItems.filter {
                it.status == UserOfferStatus.FINISHED || it.status == UserOfferStatus.ARCHIVED
            },
        )
    }

    val notificationsUnread = (trackedItemsState.tracks as? TrackedItemsLoadState.Content)
        ?.items
        ?.sumOf { track -> track.stats.newEventsCount }
        ?: 0

    val messageThreads = remember(createdItems) {
        createdItems
            .filter { (it.messagesCount ?: 0) > 0 }
            .sortedByDescending { it.updatedAtMillis ?: it.publishedAtMillis ?: 0L }
            .map { offer ->
                val lastMessage = "Новый отклик по объявлению"
                MessageThreadPreview(
                    id = offer.id,
                    contactName = offer.sourceName ?: "Покупатель",
                    offerTitle = offer.title,
                    lastMessage = lastMessage,
                    timeLabel = formatMessageTime(offer.updatedAtMillis ?: offer.publishedAtMillis),
                    unreadCount = offer.messagesCount ?: 0,
                    avatarUrl = offer.coverUrl,
                )
            }
    }
    val messagesUnread = messageThreads.sumOf { it.unreadCount }

    val locationLabel = remember(nearbyFiltersApplied, state.nearbyUserLocation, state.nearbyUserCountry) {
        nearbyFiltersApplied.locationLabel(state.nearbyUserLocation, state.nearbyUserCountry)
    }
    val hasResolvedNearbyLocation = remember(
        state.nearbyUserLocation,
        state.nearbyUserAddressLine,
        state.nearbyUserLat,
        state.nearbyUserLon,
    ) {
        !state.nearbyUserLocation.isNullOrBlank() ||
            !state.nearbyUserAddressLine.isNullOrBlank() ||
            (state.nearbyUserLat != null && state.nearbyUserLon != null)
    }
    val showLocationPermissionHint = !hasResolvedNearbyLocation && !nearbyLocationResolving
    val locationDisplayLabel = remember(
        nearbyFiltersApplied,
        state.nearbyUserLocation,
        state.nearbyUserAddressLine,
        state.nearbyUserLat,
        state.nearbyUserLon,
        nearbyLocationResolving,
    ) {
        when {
            nearbyLocationResolving -> "Определяем местоположение..."
            hasResolvedNearbyLocation -> nearbyFiltersApplied.locationDisplayLabel(
                userLocation = state.nearbyUserLocation,
                userAddressLine = state.nearbyUserAddressLine,
            )
            else -> "Не удалось определить местоположение"
        }
    }
    val feedError = remember(state.nearbyError, state.nearbyLastAction) {
        state.nearbyError?.takeIf { state.nearbyLastAction != NearbyRetryAction.Count }
    }
    val categorySummary = remember(state.nearbyCategoryChips) { categorySummary(state.nearbyCategoryChips) }
    val categoryActive = remember(nearbyFiltersApplied) { nearbyFiltersApplied.categoryCodes.isNotEmpty() }
    val nearbyActiveCount = remember(nearbyFiltersApplied, categoryActive) {
        nearbyFiltersApplied.activeCount(categoryActive)
    }
    val draftCategorySummary = remember(nearbyDraftCategoryChips) { categorySummary(nearbyDraftCategoryChips) }
    val draftCategoryActive = remember(nearbyFiltersDraft) { nearbyFiltersDraft.categoryCodes.isNotEmpty() }
    val draftActiveCount = remember(nearbyFiltersDraft, draftCategoryActive) {
        nearbyFiltersDraft.activeCount(draftCategoryActive)
    }
    val nearbyFiltersSummary = remember(
        nearbyFiltersApplied,
        state.nearbyCategoryChips,
        state.nearbyUserLocation,
        state.nearbyUserCountry,
    ) {
        buildNearbySummary(
            filters = nearbyFiltersApplied,
            categories = state.nearbyCategoryChips,
            userLocation = state.nearbyUserLocation,
            userCountry = state.nearbyUserCountry,
        )
    }
    val nearbyFilteredOutCount = remember(nearbyActiveCount, state.nearbyOffers, state.nearbyDraftCount) {
        if (nearbyActiveCount > 0 && state.nearbyOffers.isEmpty()) {
            state.nearbyDraftCount?.takeIf { it > 0 }
        } else {
            null
        }
    }
    val openFeedCategories = { dashboardState = DashboardState.FeedCategories }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val railAlpha by animateFloatAsState(
        targetValue = if (imeBottom > 0) 0.35f else 1f,
        label = "railAlpha",
    )
    val menuBadges = remember(notificationsUnread, messagesUnread) {
        mapOf(
            ActionKey.NOTIFICATIONS to notificationsUnread,
            ActionKey.MESSAGES to messagesUnread,
        )
    }
    val activePanel = panelDraft ?: userPanel
    val panelForBar = remember(activePanel, rootMode) {
        if (rootMode != RootMode.Search) return@remember activePanel
        // Hide the "Search" side quick-action while user is already on the Search screen.
        activePanel.copy(
            sideActions = activePanel.sideActions.map { slot ->
                if (slot.key == ActionKey.POST) slot.copy(key = null) else slot
            },
        )
    }
    val currentMode = when (rootMode) {
        RootMode.Dashboard -> when (dashboardState) {
            DashboardState.MessagesExpanded -> ModeKey.CHAT
            else -> ModeKey.DASHBOARD
        }
        RootMode.Search -> ModeKey.DASHBOARD
        RootMode.Post -> ModeKey.DASHBOARD
    }
    val overlayUsesOwnInsets = nearbyCategoriesPickerVisible ||
        dashboardState == DashboardState.FeedCategories ||
        searchStage == SearchStage.Results
    val showUserPanel = !createDraftVisible &&
        !linkOverlayVisible &&
        !overlayUsesOwnInsets
    val showBottomBar = showUserPanel && !panelEditMode
    val bottomBarHeight = if (showBottomBar) LayoutDefaults.BottomNavHeight else 0.dp

    LaunchedEffect(showUserPanel) {
        if (!showUserPanel && panelEditMode) {
            cancelPanelEdit()
        }
    }

    val isAtRoot = !panelEditMode &&
        !createDraftVisible &&
        !linkOverlayVisible &&
        !nearbyCategoriesPickerVisible &&
        rootMode == RootMode.Dashboard &&
        dashboardState == DashboardState.Overview

    BackHandler(enabled = !isAtRoot) {
        if (nearbyCategoriesPickerVisible) {
            closeNearbyCategoriesPicker(reopenSheet = true)
            return@BackHandler
        }
        if (panelEditMode) {
            cancelPanelEdit()
            return@BackHandler
        }
        if (linkOverlayVisible) {
            linkOverlayVisible = false
            linkError = null
            return@BackHandler
        }
        if (createDraftVisible) {
            dismissCreateSheet()
            return@BackHandler
        }
        when (rootMode) {
            RootMode.Dashboard -> {
                if (dashboardState != DashboardState.Overview) {
                    dashboardState = DashboardState.Overview
                }
            }
            RootMode.Search -> {
                when (searchStage) {
                    SearchStage.Results -> {
                        closeResultsToSearchHub()
                    }
                    SearchStage.Focused -> {
                        searchStage = SearchStage.Idle
                        focusManager.clearFocus(force = true)
                    }
                    SearchStage.Idle -> {
                        rootMode = RootMode.Dashboard
                        dashboardState = DashboardState.Overview
                    }
                }
            }
            RootMode.Post -> Unit
        }
    }

    ScreenRoot(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        applySafeInsets = !overlayUsesOwnInsets,
        bottomBarHeight = bottomBarHeight,
        bottomBar = if (showBottomBar) {
            {
                UserPanelBar(
                    panel = panelForBar,
                    modeCatalog = modeCatalog,
                    actionCatalog = actionCatalog,
                    currentMode = currentMode,
                    badges = menuBadges,
                    onModeClick = ::handleModeClick,
                    onActionClick = ::handleActionClick,
                    onEnterEdit = { startPanelEdit() },
                    modifier = Modifier.fillMaxSize(),
                    alpha = railAlpha,
                    bottomBarStyle = profileSettings.bottomBarStyle,
                )
            }
        } else null,
    ) { contentPadding ->
        StateHost(
            state = ScreenState.Content(),
            contentPadding = contentPadding,
            screenName = "main",
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
                ) {
                    state.catalogErrorMessage
                        ?.takeIf { it.isNotBlank() }
                        ?.let { message ->
                            MainCatalogErrorBanner(
                                message = message,
                                onRetry = { viewModel.retryCatalogLoad() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LayoutDefaults.HorizontalPadding),
                            )
                        }
                    when (rootMode) {
                        RootMode.Dashboard -> DashboardScreen(
                    state = dashboardState,
                    locationLabel = locationLabel,
                    locationDisplayLabel = locationDisplayLabel,
                    nearbyFilters = nearbyFiltersApplied,
                    nearbyActiveCount = nearbyActiveCount,
                    nearbyError = feedError,
                    nearbyFiltersSummary = nearbyFiltersSummary,
                    onOpenNearbyFilters = ::openNearbyFilters,
                    onResetNearbyFilter = ::resetNearbyFilter,
                    onResetNearbyCategories = { viewModel.updateFeedCategories(emptySet()) },
                    onResetNearbyFilters = ::resetNearbyFiltersAll,
                    onRetryNearby = { viewModel.retryNearby(nearbyFiltersApplied) },
                    onExpandRadius = { expandNearbyRadius() },
                    onShowCityPopular = { showCityPopular() },
                    showLocationPermissionHint = showLocationPermissionHint,
                    onRequestLocationPermission = {
                        if (state.nearbyLocationPermission == NearbyLocationPermission.DeniedPermanent) {
                            openLocationSettings()
                        } else {
                            requestNearbyLocationPermission()
                        }
                    },
                    nearbyCategoryChips = state.nearbyCategoryChips,
                    nearbyFilteredOutCount = nearbyFilteredOutCount,
                    onOpenFeedCategories = openFeedCategories,
                    onShowAllCategories = { showAllSelectedFeed = true },
                    showBrandFilter = showNearbyBrandFilter,
                    feedItems = state.nearbyOffers,
                    nearbyFetchLimit = state.nearbyFetchLimit,
                    nearbyVisibleCount = state.nearbyVisibleCount,
                    nearbyIsLoadingMore = state.nearbyIsLoadingMore,
                    onLoadMore = { viewModel.loadMoreNearby() },
                    savedOfferIds = savedOfferIds,
                    hiddenOfferIds = hiddenFeedOfferIds,
                    onToggleSave = { toggleSavedOffer(it) },
                    onOpenOffer = { openFeedOffer(it) },
                    onOverflowAction = { action, item -> handleFeedOverflow(action, item) },
                    photoPeekEnabled = profileSettings.photoPeekEnabled,
                    offerCounts = offerCounts,
                    offersPreviewItems = offersPreviewItems,
                    messageThreads = messageThreads,
                    trackedItemsState = trackedItemsState,
                    onOpenTrackedItemsAll = openTrackedItemsAll,
                    onOpenTrackedItem = openTrackedItem,
                    onCreateTrack = openTrackedItemsAll,
                    onRetryTrackedItems = retryTrackedItems,
                    onExpandSection = { dashboardState = it },
                    onProfileClick = { handleModeClick(ModeKey.PROFILE) },
                    onOpenDraft = { openDraftById(it) },
                    onOpenSearchHub = { openSearchHub(expanded = true) },
                    onSearchByPhoto = ::openVisualSearch,
                    onSearchByLink = { showLegacySearchFlowRemoved("link") },
                    onSearchByVoice = { showLegacySearchFlowRemoved("voice") },
                    isGuest = isGuest,
                    viewModel = viewModel,
                )
                        RootMode.Search,
                        RootMode.Post -> SearchScreen(
                    input = state.template.inputText,
                    suggestions = state.suggestions,
                    frequentCategories = state.frequentCategories,
                    popularCategories = state.popularCategories,
                    recentSearchQueries = state.recentSearchQueries,
                    popularSearchQueries = state.popularSearchQueries,
                    localIntents = LocalSearchIntent.values().toList(),
                    onLocalIntent = { intent ->
                        val label = localIntentLabels[intent].orEmpty()
                        startLocalIntent(intent, label, localIntentLocationRequired)
                    },
                    onClearHistory = { viewModel.clearSearchHistory() },
                    stage = searchStage,
                    onStageChange = { searchStage = it },
                    onInputChange = { viewModel.onQueryChange(it) },
                    onSearch = {
                        focusManager.clearFocus(force = true)
                        startSearchByText(state.template.inputText)
                    },
                    onSearchByRawText = { rawText ->
                        val normalizedRawText = SearchTextNormalizer.normalize(rawText)
                        viewModel.onQueryChange(normalizedRawText)
                        focusManager.clearFocus(force = true)
                        startSearchByText(normalizedRawText)
                    },
                    onSuggestionClick = { item ->
                        focusManager.clearFocus(force = true)
                        startSearchBySuggestion(item)
                    },
                    onSuggestionAction = { item, action ->
                        when (action) {
                            SuggestionAction.Search -> {
                                focusManager.clearFocus(force = true)
                                startSearchBySuggestion(item)
                            }
                            SuggestionAction.Track -> trackSuggestion(item)
                            SuggestionAction.Create -> openCreateFlow(DraftInputOrigin.TEXT)
                        }
                    },
                    onOpenResults = { payload ->
                        focusManager.clearFocus(force = true)
                        viewModel.recordSearchHistory(
                            queryText = payload.queryText,
                            query = payload.query,
                            categoryCode = payload.categoryCode,
                        )
                        navigateToResults(payload, 1)
                    },
                    onPhotoSearch = ::openVisualSearch,
                    onLinkSearch = {
                        focusManager.clearFocus(force = true)
                        showLegacySearchFlowRemoved("link")
                    },
                    onVoiceSearch = ::startSpeechToText,
                    hasSelection = state.template.isLocked,
                    resultsPayload = searchPayload,
                    resultsLoadingQuery = searchLoadingQuery,
                    onCloseResults = ::closeResultsToSearchHub,
                    onCloseToDashboard = ::closeResultsToSearchHub,
                    onOpenCreate = { openCreateFlow(DraftInputOrigin.TEXT) },
                    navController = navController,
                    viewModel = viewModel,
                )
                }
            }

                if (showUserPanel && panelEditMode) {
                    AnimatedVisibility(
                        visible = panelEditMode,
                        enter = fadeIn(animationSpec = tween(200)) +
                            scaleIn(animationSpec = tween(200), initialScale = 0.98f),
                        exit = fadeOut(animationSpec = tween(200)) +
                            scaleOut(animationSpec = tween(200), targetScale = 0.98f),
                    ) {
                        UserPanelEditor(
                            panel = activePanel,
                            config = panelConfig,
                            modeCatalog = modeCatalog,
                            actionCatalog = actionCatalog,
                            onPanelChange = ::updatePanelDraft,
                            onDone = ::commitPanelEdit,
                            onCancel = ::cancelPanelEdit,
                            onReset = ::resetPanelEdit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (state.visualSearch.visible) {
        VisualSearchCameraScreen(
            state = state.visualSearch,
            hasCameraPermission = visualCameraPermissionGranted,
            onDismiss = viewModel::dismissVisualSearch,
            onRequestPermission = {
                if (!visualCameraPermissionGranted) {
                    visualCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickGallery = ::openVisualGallery,
            onCaptureModeChange = viewModel::updateVisualSearchCaptureMode,
            onCaptureBitmap = { bitmap, captureMode ->
                val media = visualMediaImporter.importBitmap(bitmap)
                if (media == null) {
                    Toast.makeText(context, "Не удалось обработать снимок.", Toast.LENGTH_SHORT).show()
                    return@VisualSearchCameraScreen
                }
                val localUri = media.localUri?.toString()
                if (localUri == null) {
                    Toast.makeText(context, "Не удалось сохранить снимок.", Toast.LENGTH_SHORT).show()
                    return@VisualSearchCameraScreen
                }
                val inlineBase64 = context.encodeVisualSearchInlineBase64(localUri)
                val analysis = runCatching {
                    visualCaptureAnalyzer.analyzeUri(Uri.parse(localUri), captureMode)
                }.getOrNull()
                val insight = buildVisualSearchInsight(
                    analysis = analysis,
                    routeQueryTask = routeQueryTask,
                    getBrowseNodeTask = getBrowseNodeTask,
                    viewModel = viewModel,
                )
                viewModel.onVisualSearchAssetPicked(
                    source = VisualSearchSource.CAMERA,
                    captureMode = captureMode,
                    insight = insight,
                    asset = VisualSearchAssetUi(
                        fingerprint = media.id,
                        localUri = localUri,
                        widthPx = media.width,
                        heightPx = media.height,
                        byteSize = media.byteSize,
                        inlineBase64 = inlineBase64,
                    ),
                )
            },
            onSubmitSearch = { viewModel.submitVisualSearch(autoTriggered = false) },
        )
    }

    if (showAllSelectedFeed) {
        val visibleCategories =
            if (nearbyFiltersSection != null) nearbyDraftCategoryChips else state.nearbyCategoryChips
        SelectedCategoriesSheet(
            title = "Выбранные категории",
            items = visibleCategories,
            onDismiss = { showAllSelectedFeed = false },
        )
    }

    if (nearbyCategoriesPickerVisible) {
        FeedCategoriesPage(
            viewModel = viewModel,
            onBack = { closeNearbyCategoriesPicker(reopenSheet = true) },
            onClose = { closeNearbyCategoriesPicker(reopenSheet = true) },
            applySafeInsets = true,
            extraBottomPadding = 0.dp,
            initialSelectedCodes = nearbyFiltersDraft.categoryCodes,
            onApplySelected = { codes ->
                scope.launch {
                    val (compacted, chips) = viewModel.resolveNearbyCategorySelection(codes)
                    val categoryChanged = compacted != nearbyFiltersDraft.categoryCodes
                    val updatedDraft = nearbyFiltersDraft.copy(
                        categoryCodes = compacted,
                        brands = if (categoryChanged) emptyList() else nearbyFiltersDraft.brands,
                    )
                    nearbyFiltersDraft = updatedDraft
                    nearbyDraftCategoryChips = chips
                    if (nearbyReturnSection == null) {
                        val activeCount = updatedDraft.activeCount(updatedDraft.categoryCodes.isNotEmpty())
                        val summary = buildNearbySummary(
                            filters = updatedDraft,
                            categories = chips,
                            userLocation = state.nearbyUserLocation,
                            userCountry = state.nearbyUserCountry,
                        )
                        FlowMetrics.markNearbyFiltersApply(activeCount, summary)
                        viewModel.applyNearbyFilters(updatedDraft)
                        closeNearbyCategoriesPicker(reopenSheet = false)
                    } else {
                        closeNearbyCategoriesPicker(reopenSheet = true)
                    }
                }
            },
            isModal = true,
        )
    }

    val isDraftDirty = remember(nearbyFiltersDraft, nearbyFiltersApplied) {
        nearbyFiltersDraft != nearbyFiltersApplied
    }
    val nearbyApplyCount = if (isDraftDirty) state.nearbyDraftCount else state.nearbyFoundCount
    val nearbyApplyLabel = when {
        nearbyApplyCount == null -> "Показать"
        nearbyApplyCount > 0 -> "Показать $nearbyApplyCount"
        else -> "Показать 0"
    }
    val nearbyApplyHint = when {
        nearbyApplyCount == null -> "Обновим результаты после применения"
        nearbyApplyCount == 0 -> "Нет результатов. Сбросьте фильтры или расширьте радиус."
        else -> null
    }
    val nearbyApplyCountLoading = nearbyApplyCount == null
    val nearbyApplyShowZeroActions = nearbyApplyCount == 0

    nearbyFiltersSection?.let { section ->
        NearbyFiltersSheet(
            section = section,
            draft = nearbyFiltersDraft,
            categorySummary = draftCategorySummary,
            activeCount = draftActiveCount,
            showBrandFilter = nearbyDraftLeafCategoryCode != null,
            brandFacets = state.nearbyBrandFacets,
            conditionFacets = state.nearbyConditionFacets,
            deliveryChannelFacets = state.nearbyDeliveryChannelFacets,
            userLocation = state.nearbyUserLocation,
            userCountry = state.nearbyUserCountry,
            locationPermission = state.nearbyLocationPermission,
            nearbyError = state.nearbyError,
            applyLoading = false,
            applyCountLoading = nearbyApplyCountLoading,
            showZeroActions = nearbyApplyShowZeroActions,
            onRequestLocationPermission = { requestNearbyLocationPermission() },
            onOpenLocationSettings = { openLocationSettings() },
            onRetry = { viewModel.retryNearby(nearbyFiltersDraft) },
            onSectionChange = { nearbyFiltersSection = it },
            onDraftChange = { nearbyFiltersDraft = it },
            onResetDraft = { nearbyFiltersDraft = NearbyFiltersState() },
            onOpenCategories = {
                nearbyCategoriesPickerVisible = true
                nearbyReturnSection = NearbyFilterSection.All
                nearbyFiltersSection = null
            },
            onShowAllCategories = {
                showAllSelectedFeed = true
            },
            onZeroReset = { nearbyFiltersDraft = NearbyFiltersState() },
            onZeroExpandRadius = { nearbyFiltersSection = NearbyFilterSection.Location },
            onApply = ::applyNearbyFilters,
            onDismiss = ::dismissNearbyFilters,
            applyLabel = nearbyApplyLabel,
            applyHint = nearbyApplyHint,
        )
    }

    if (createDraftVisible) {
        LocalOfferSheet(
            visible = true,
            draftId = createDraftId,
            origin = createDraftOrigin,
            onDismiss = { dismissCreateSheet() },
        )
    }

    if (linkOverlayVisible) {
        LinkInputOverlay(
            input = linkInput,
            error = linkError,
            supportedSourcesHint = supportedSourcesHint,
            onChange = { value ->
                linkInput = linkInput.copy(url = value, isValid = validateUrl(value))
                linkError = null
            },
            onSubmit = { submitLink() },
            onDismiss = { linkOverlayVisible = false },
        )
    }
}

private enum class RootMode {
    Dashboard,
    Search,
    Post,
}

private enum class DashboardState {
    Overview,
    OffersExpanded,
    FeedCategories,
    AdsExpanded,
    MessagesExpanded,
}

private fun Context.encodeVisualSearchInlineBase64(localUri: String): String? {
    val bytes = runCatching {
        val uri = Uri.parse(localUri)
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> {
                val file = File(java.net.URI(localUri))
                if (file.exists()) file.readBytes() else null
            }
            else -> contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        }
    }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.size > 3_500_000) return null
    return Base64.getEncoder().encodeToString(bytes)
}

private enum class SearchStage {
    Idle,
    Focused,
    Results,
}

private enum class LocalSearchIntent {
    TopNearby,
    Today,
    Used,
    New,
}

private fun localIntentLabelRes(intent: LocalSearchIntent): Int = when (intent) {
    LocalSearchIntent.TopNearby -> R.string.search_local_intent_top_nearby
    LocalSearchIntent.Today -> R.string.search_local_intent_today
    LocalSearchIntent.Used -> R.string.search_local_intent_used
    LocalSearchIntent.New -> R.string.search_local_intent_new
}

private enum class LinkOverlayMode {
    Create,
    Search,
}

private data class OfferCounts(
    val total: Int,
    val active: Int,
    val drafts: Int,
    val completed: Int,
)

private data class ActivitySummary(
    val notificationsUnread: Int,
    val messagesUnread: Int,
    val activeSubscriptions: Int,
    val triggeredSubscriptions: Int,
)

private data class MessageThreadPreview(
    val id: String,
    val contactName: String,
    val offerTitle: String,
    val lastMessage: String,
    val timeLabel: String,
    val unreadCount: Int,
    val avatarUrl: String?,
)

private data class NotificationPreview(
    val id: Long,
    val message: String,
    val details: String?,
    val timeLabel: String,
)

private fun NearbyFiltersState.locationLabel(
    userLocation: String?,
    userCountry: String?,
): String {
    val city = selectedPlace?.city?.trim()?.takeIf { it.isNotBlank() }
    val resolvedCity = city ?: userLocation?.trim()?.takeIf { it.isNotBlank() }
    val fallback = resolvedCity ?: "Город"
    val radius = radiusKm.coerceIn(1, MAX_NEARBY_RADIUS_KM)
    return when (locationScope) {
        NearbyScope.NEARBY -> "${resolvedCity ?: "Рядом"} · $radius км"
        NearbyScope.CITY, NearbyScope.COUNTRY -> resolvedCity ?: fallback
    }
}

private fun NearbyFiltersState.locationDisplayLabel(
    userLocation: String?,
    userAddressLine: String?,
): String {
    val city = selectedPlace?.city?.trim()?.takeIf { it.isNotBlank() }
        ?: userLocation?.trim()?.takeIf { it.isNotBlank() }
    val address = if (locationScope == NearbyScope.NEARBY) {
        userAddressLine?.trim()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    return when {
        city != null && address != null -> "$city, $address"
        city != null -> city
        address != null -> address
        else -> "Город"
    }
}

private fun NearbyFiltersState.locationIsActive(): Boolean =
    locationScope != NearbyScope.NEARBY || radiusKm != DEFAULT_NEARBY_RADIUS_KM

private fun NearbyFiltersState.priceIsActive(): Boolean =
    priceMin != null || priceMax != null

private fun NearbyFiltersState.conditionIsActive(): Boolean =
    condition != NearbyCondition.Any

private fun NearbyFiltersState.deliveryIsActive(): Boolean =
    delivery.any { option -> option != NearbyDelivery.Meeting }

private fun NearbyFiltersState.brandsIsActive(): Boolean =
    brands.isNotEmpty()

private fun NearbyFiltersState.postedAtIsActive(): Boolean =
    postedAt != defaultPostedAt()

private fun NearbyFiltersState.defaultPostedAt(): NearbyPostedAt =
    NearbyPostedAt.D3

private fun NearbyFiltersState.sortIsActive(): Boolean =
    sort != defaultSort()

private fun NearbyFiltersState.activeCount(categoryActive: Boolean): Int {
    var count = 0
    if (categoryActive) count += 1
    if (locationIsActive()) count += 1
    if (brandsIsActive()) count += 1
    if (priceIsActive()) count += 1
    if (conditionIsActive()) count += 1
    if (deliveryIsActive()) count += 1
    if (postedAtIsActive()) count += 1
    if (sortIsActive()) count += 1
    return count
}

private fun NearbyFiltersState.priceLabel(): String {
    val min = priceMin
    val max = priceMax
    return when {
        min == null && max == null -> "Цена"
        min != null && max != null -> "${formatNearbyPrice(min)}–${formatNearbyPrice(max)}"
        min != null -> "от ${formatNearbyPrice(min)}"
        else -> "до ${formatNearbyPrice(max ?: 0)}"
    }
}

private fun NearbyFiltersState.priceSummary(): String {
    val min = priceMin
    val max = priceMax
    return when {
        min == null && max == null -> "Любая"
        min != null && max != null -> "${formatNearbyPrice(min)}–${formatNearbyPrice(max)}"
        min != null -> "от ${formatNearbyPrice(min)}"
        else -> "до ${formatNearbyPrice(max ?: 0)}"
    }
}

private fun NearbyFiltersState.brandsLabel(): String =
    if (brandsIsActive()) brandsSummary() else "Бренд"

private fun NearbyFiltersState.brandsSummary(): String {
    val names = brands
        .mapNotNull { it.name.trim().takeIf { name -> name.isNotBlank() } }
        .distinct()
    if (names.isEmpty()) return "Все бренды"
    if (names.size == 1) return names.first()
    return "${names.size} бренда"
}

private fun NearbyFiltersState.conditionLabel(): String =
    if (condition == NearbyCondition.Any) "Состояние" else condition.label

private fun NearbyFiltersState.conditionSummary(): String =
    if (condition == NearbyCondition.Any) "Любое" else condition.label

private fun NearbyFiltersState.deliveryLabel(): String =
    if (deliveryIsActive()) deliverySummary() else "Способ получения"

private fun NearbyFiltersState.deliverySummary(): String {
    val labels = delivery
        .filterNot { option -> option == NearbyDelivery.Meeting }
        .map { option -> option.label }
    if (labels.isEmpty()) return "Любой"
    return if (labels.size == 1) labels.first() else "${labels.size} варианта"
}

private data class NearbyConditionOptionUi(
    val condition: NearbyCondition,
    val count: Int?,
)

private data class NearbyDeliveryOptionUi(
    val delivery: NearbyDelivery,
    val count: Int?,
)

private fun nearbyConditionOptions(
    facets: List<NearbyValueFacet>,
    selected: NearbyCondition,
): List<NearbyConditionOptionUi> {
    val counts = LinkedHashMap<NearbyCondition, Int>()
    facets.forEach { facet ->
        val option = nearbyConditionFromFacet(facet) ?: return@forEach
        counts[option] = (counts[option] ?: 0) + facet.count
    }
    if (selected != NearbyCondition.Any && selected !in counts.keys && counts.isNotEmpty()) {
        counts[selected] = 0
    }
    val values = if (counts.isEmpty()) {
        NearbyCondition.entries.filterNot { it == NearbyCondition.Any }
    } else {
        NearbyCondition.entries.filter { option -> option != NearbyCondition.Any && option in counts.keys }
    }
    return buildList {
        add(NearbyConditionOptionUi(condition = NearbyCondition.Any, count = null))
        values.forEach { option ->
            add(NearbyConditionOptionUi(condition = option, count = counts[option]))
        }
    }
}

private fun nearbyDeliveryOptions(
    facets: List<NearbyValueFacet>,
    selected: Set<NearbyDelivery>,
): List<NearbyDeliveryOptionUi> {
    val counts = LinkedHashMap<NearbyDelivery, Int>()
    facets.forEach { facet ->
        val option = nearbyDeliveryFromFacet(facet) ?: return@forEach
        if (option == NearbyDelivery.Meeting) return@forEach
        counts[option] = (counts[option] ?: 0) + facet.count
    }
    selected.forEach { option ->
        if (option != NearbyDelivery.Meeting && option !in counts.keys && counts.isNotEmpty()) {
            counts[option] = 0
        }
    }
    val values = if (counts.isEmpty()) {
        NearbyDelivery.entries.filterNot { option -> option == NearbyDelivery.Meeting }
    } else {
        NearbyDelivery.entries.filter { option -> option != NearbyDelivery.Meeting && option in counts.keys }
    }
    return values.map { option -> NearbyDeliveryOptionUi(delivery = option, count = counts[option]) }
}

private fun nearbyConditionFromFacet(facet: NearbyValueFacet): NearbyCondition? {
    val normalized = normalizeNearbyFacetToken(facet.id)
    val byName = normalizeNearbyFacetToken(facet.name)
    val candidates = listOf(normalized, byName).filter { token -> token.isNotBlank() }
    candidates.forEach { candidate ->
        when (candidate) {
            "new",
            "novoe",
            "novyi",
            "новое",
            "новый",
            -> return NearbyCondition.New

            "like_new",
            "likenew",
            "like-new",
            "как_новый",
            "какновый",
            -> return NearbyCondition.LikeNew

            "used",
            "second_hand",
            "secondhand",
            "бу",
            "б_у",
            "б/у",
            -> return NearbyCondition.Used
        }
    }
    return null
}

private fun nearbyDeliveryFromFacet(facet: NearbyValueFacet): NearbyDelivery? {
    val normalized = normalizeNearbyFacetToken(facet.id)
    val byName = normalizeNearbyFacetToken(facet.name)
    val candidates = listOf(normalized, byName).filter { token -> token.isNotBlank() }
    candidates.forEach { candidate ->
        when (candidate) {
            "delivery",
            "доставка",
            "ship",
            "shipping",
            -> return NearbyDelivery.Delivery

            "pickup",
            "self_pickup",
            "самовывоз",
            -> return NearbyDelivery.Pickup

            "meeting",
            "meetup",
            "встреча",
            -> return NearbyDelivery.Meeting
        }
    }
    return null
}

private fun normalizeNearbyFacetToken(raw: String): String =
    raw.trim()
        .lowercase(Locale.ROOT)
        .replace("ё", "е")
        .replace("\\s+".toRegex(), "_")

private fun NearbyFiltersState.postedAtLabel(): String =
    if (postedAtIsActive()) postedAt.label else "Дата"

private fun NearbyFiltersState.postedAtSummary(): String =
    postedAt.label

private fun NearbyFiltersState.sortLabel(): String =
    if (sortIsActive()) sort.label else "Сорт"

private fun NearbyFiltersState.sortSummary(): String = sort.label

private fun formatNearbyPrice(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}м"
    value >= 1_000 -> "${value / 1_000}к"
    else -> value.toString()
}

private fun nearbyPriceSliderMax(
    minValue: Int?,
    maxValue: Int?,
): Int {
    val base = max(100_000, max(minValue ?: 0, maxValue ?: 0))
    val rounded = ((base + 9_999) / 10_000) * 10_000
    return rounded.coerceIn(100_000, 2_000_000)
}

private fun formatPriceInput(value: Int): String =
    String.format(Locale.forLanguageTag("ru-RU"), "%,d", value).replace(',', ' ')

private fun hasActiveCategories(categories: List<CategoryChipUi>): Boolean =
    categories.isNotEmpty()

private fun categorySummary(categories: List<CategoryChipUi>): String {
    val filtered = categories.filterNot { it.title.equals("Все", ignoreCase = true) }
    if (filtered.isEmpty()) return "Все категории"
    if (filtered.size == 1) {
        val item = filtered.first()
        val raw = item.breadcrumb?.takeIf { it.isNotBlank() } ?: item.title
        val parts = raw.split(CategoryBreadcrumbSplitRegex).map { it.trim() }.filter { it.isNotBlank() }
        return parts.lastOrNull() ?: item.title
    }
    return "${filtered.size} категорий"
}

private val CategoryBreadcrumbSplitRegex = Regex("\\s*(?:→|/|>|\\u001A|->)\\s*")

@Composable
internal fun MainCatalogErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemNoticeCard(
        body = message,
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_catalog_error_banner"),
        tone = SystemNoticeTone.Error,
        bodyModifier = Modifier.testTag("main_catalog_error_message"),
        actionLabel = stringResource(R.string.state_error_retry),
        onAction = onRetry,
        actionModifier = Modifier.testTag("main_catalog_error_retry"),
        compact = true,
    )
}

@Composable
private fun DashboardScreen(
    state: DashboardState,
    locationLabel: String,
    locationDisplayLabel: String,
    nearbyFilters: NearbyFiltersState,
    nearbyActiveCount: Int,
    nearbyError: NearbyErrorState?,
    nearbyFiltersSummary: String?,
    onOpenNearbyFilters: (NearbyFilterSection) -> Unit,
    onResetNearbyFilter: (NearbyFilterSection) -> Unit,
    onResetNearbyCategories: () -> Unit,
    onResetNearbyFilters: () -> Unit,
    onRetryNearby: () -> Unit,
    onExpandRadius: () -> Unit,
    onShowCityPopular: () -> Unit,
    showLocationPermissionHint: Boolean,
    onRequestLocationPermission: () -> Unit,
    nearbyCategoryChips: List<CategoryChipUi>,
    nearbyFilteredOutCount: Int?,
    onOpenFeedCategories: () -> Unit,
    onShowAllCategories: () -> Unit,
    showBrandFilter: Boolean,
    feedItems: List<ExplainedItem>,
    nearbyFetchLimit: Int,
    nearbyVisibleCount: Int,
    nearbyIsLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    savedOfferIds: Set<String>,
    hiddenOfferIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onOpenOffer: (ExplainedItem) -> Unit,
    onOverflowAction: (OfferOverflowAction, ExplainedItem) -> Unit,
    photoPeekEnabled: Boolean,
    offerCounts: OfferCounts,
    offersPreviewItems: Map<UserOffersTab, List<UserOfferCardUi>>,
    messageThreads: List<MessageThreadPreview>,
    trackedItemsState: TrackedItemsState,
    onOpenTrackedItemsAll: () -> Unit,
    onOpenTrackedItem: (String) -> Unit,
    onCreateTrack: () -> Unit,
    onRetryTrackedItems: () -> Unit,
    onExpandSection: (DashboardState) -> Unit,
    onProfileClick: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onOpenSearchHub: () -> Unit,
    onSearchByPhoto: () -> Unit,
    onSearchByLink: () -> Unit,
    onSearchByVoice: () -> Unit,
    isGuest: Boolean,
    viewModel: MainPageViewModel,
) {
    var offersTab by rememberSaveable { mutableStateOf(UserOffersTab.ACTIVE) }

    when (state) {
        DashboardState.Overview -> DashboardOverview(
            locationLabel = locationLabel,
            locationDisplayLabel = locationDisplayLabel,
            nearbyFilters = nearbyFilters,
            nearbyActiveCount = nearbyActiveCount,
            nearbyError = nearbyError,
            nearbyFiltersSummary = nearbyFiltersSummary,
            onOpenNearbyFilters = onOpenNearbyFilters,
            onResetNearbyFilter = onResetNearbyFilter,
            onResetNearbyCategories = onResetNearbyCategories,
            onResetNearbyFilters = onResetNearbyFilters,
            onRetryNearby = onRetryNearby,
            nearbyCategoryChips = nearbyCategoryChips,
            onOpenFeedCategories = onOpenFeedCategories,
            onShowAllFeed = { onExpandSection(DashboardState.OffersExpanded) },
            onShowAllCategories = onShowAllCategories,
            onExpandRadius = onExpandRadius,
            onShowCityPopular = onShowCityPopular,
            showLocationPermissionHint = showLocationPermissionHint,
            onRequestLocationPermission = onRequestLocationPermission,
            showBrandFilter = showBrandFilter,
            feedItems = feedItems,
            nearbyFilteredOutCount = nearbyFilteredOutCount,
            savedOfferIds = savedOfferIds,
            hiddenOfferIds = hiddenOfferIds,
            onToggleSave = onToggleSave,
            onOpenOffer = onOpenOffer,
            trackedItemsState = trackedItemsState,
            onOpenTrackedItemsAll = onOpenTrackedItemsAll,
            onOpenTrackedItem = onOpenTrackedItem,
            onCreateTrack = onCreateTrack,
            onRetryTrackedItems = onRetryTrackedItems,
            onSearchByPhoto = onSearchByPhoto,
            onSearchByLink = onSearchByLink,
            onSearchByVoice = onSearchByVoice,
            onOpenSearchHub = onOpenSearchHub,
            isGuest = isGuest,
            onLoginClick = onProfileClick,
        )
        DashboardState.OffersExpanded -> DashboardExpandedScaffold(
            title = "Рядом",
            onBack = { onExpandSection(DashboardState.Overview) },
        ) {
            NewOffersExpanded(
                locationLabel = locationLabel,
                locationDisplayLabel = locationDisplayLabel,
                nearbyFilters = nearbyFilters,
                nearbyActiveCount = nearbyActiveCount,
                nearbyError = nearbyError,
                nearbyFiltersSummary = nearbyFiltersSummary,
                onOpenNearbyFilters = onOpenNearbyFilters,
                onResetNearbyFilter = onResetNearbyFilter,
                onResetNearbyCategories = onResetNearbyCategories,
                onResetNearbyFilters = onResetNearbyFilters,
                onRetryNearby = onRetryNearby,
                onExpandRadius = onExpandRadius,
                onShowCityPopular = onShowCityPopular,
                nearbyCategoryChips = nearbyCategoryChips,
                nearbyFilteredOutCount = nearbyFilteredOutCount,
                onOpenFeedCategories = onOpenFeedCategories,
                items = feedItems,
                nearbyFetchLimit = nearbyFetchLimit,
                nearbyVisibleCount = nearbyVisibleCount,
                nearbyIsLoadingMore = nearbyIsLoadingMore,
                savedOfferIds = savedOfferIds,
                hiddenOfferIds = hiddenOfferIds,
                onToggleSave = onToggleSave,
                onOpenOffer = onOpenOffer,
                onOverflowAction = onOverflowAction,
                photoPeekEnabled = photoPeekEnabled,
                showBrandFilter = showBrandFilter,
                onLoadMore = onLoadMore,
            )
        }
        DashboardState.FeedCategories -> FeedCategoriesPage(
            viewModel = viewModel,
            onBack = { onExpandSection(DashboardState.Overview) },
            onClose = { onExpandSection(DashboardState.Overview) },
            applySafeInsets = true,
            extraBottomPadding = 0.dp,
            isModal = false,
        )
        DashboardState.AdsExpanded -> DashboardExpandedScaffold(
            title = "Мои объявления",
            onBack = { onExpandSection(DashboardState.Overview) },
        ) {
            AdsExpandedList(
                counts = offerCounts,
                selectedTab = offersTab,
                onTabChange = { offersTab = it },
                offersPreviewItems = offersPreviewItems,
                onOpenDraft = onOpenDraft,
            )
        }
        DashboardState.MessagesExpanded -> DashboardExpandedScaffold(
            title = "Сообщения",
            onBack = { onExpandSection(DashboardState.Overview) },
        ) {
            MessagesExpanded(threads = messageThreads)
        }
    }
}

@Composable
private fun DashboardOverview(
    locationLabel: String,
    locationDisplayLabel: String,
    nearbyFilters: NearbyFiltersState,
    nearbyActiveCount: Int,
    nearbyError: NearbyErrorState?,
    nearbyFiltersSummary: String?,
    onOpenNearbyFilters: (NearbyFilterSection) -> Unit,
    onResetNearbyFilter: (NearbyFilterSection) -> Unit,
    onResetNearbyCategories: () -> Unit,
    onResetNearbyFilters: () -> Unit,
    onRetryNearby: () -> Unit,
    nearbyCategoryChips: List<CategoryChipUi>,
    onOpenFeedCategories: () -> Unit,
    onShowAllFeed: () -> Unit,
    onShowAllCategories: () -> Unit,
    onExpandRadius: () -> Unit,
    onShowCityPopular: () -> Unit,
    showLocationPermissionHint: Boolean,
    onRequestLocationPermission: () -> Unit,
    showBrandFilter: Boolean,
    feedItems: List<ExplainedItem>,
    nearbyFilteredOutCount: Int?,
    savedOfferIds: Set<String>,
    hiddenOfferIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onOpenOffer: (ExplainedItem) -> Unit,
    trackedItemsState: TrackedItemsState,
    onOpenTrackedItemsAll: () -> Unit,
    onOpenTrackedItem: (String) -> Unit,
    onCreateTrack: () -> Unit,
    onRetryTrackedItems: () -> Unit,
    onSearchByPhoto: () -> Unit,
    onSearchByLink: () -> Unit,
    onSearchByVoice: () -> Unit,
    onOpenSearchHub: () -> Unit,
    isGuest: Boolean,
    onLoginClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = LayoutDefaults.HorizontalPadding, vertical = LayoutDefaults.SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
    ) {
        NearbyFeedSection(
            locationLabel = locationLabel,
            locationDisplayLabel = locationDisplayLabel,
            nearbyFilters = nearbyFilters,
            nearbyActiveCount = nearbyActiveCount,
            nearbyError = nearbyError,
            nearbyFiltersSummary = nearbyFiltersSummary,
            onOpenNearbyFilters = onOpenNearbyFilters,
            onResetNearbyFilter = onResetNearbyFilter,
            onResetNearbyCategories = onResetNearbyCategories,
            onResetNearbyFilters = onResetNearbyFilters,
            onRetryNearby = onRetryNearby,
            nearbyCategoryChips = nearbyCategoryChips,
            onOpenFeedCategories = onOpenFeedCategories,
            onShowAllFeed = onShowAllFeed,
            onShowAllCategories = onShowAllCategories,
            onExpandRadius = onExpandRadius,
            onShowCityPopular = onShowCityPopular,
            showLocationPermissionHint = showLocationPermissionHint,
            onRequestLocationPermission = onRequestLocationPermission,
            showBrandFilter = showBrandFilter,
            items = feedItems,
            nearbyFilteredOutCount = nearbyFilteredOutCount,
            savedOfferIds = savedOfferIds,
            hiddenOfferIds = hiddenOfferIds,
            onToggleSave = onToggleSave,
            onOpenOffer = onOpenOffer,
        )

        TrackedItemsSignalsSection(
            trackedItemsState = trackedItemsState,
            onOpenAll = onOpenTrackedItemsAll,
            onOpenTrack = onOpenTrackedItem,
            onCreateTrack = onCreateTrack,
            onRetry = onRetryTrackedItems,
            isGuest = isGuest,
            onLoginClick = onLoginClick,
        )
    }
}

@Composable
private fun DashboardExpandedScaffold(
    title: String,
    onBack: () -> Unit,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LayoutDefaults.HorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
    ) {
        ExpandedTopBar(
            title = title,
            onBack = onBack,
            trailingContent = trailingContent,
        )
        content()
    }
}

@Composable
private fun ExpandedTopBar(
    title: String,
    onBack: () -> Unit,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    AppTopBar(
        title = title,
        onBack = onBack,
        applySafeInsets = false,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        trailingContent = trailingContent,
    )
}

@Composable
private fun NewOffersExpanded(
    locationLabel: String,
    locationDisplayLabel: String,
    nearbyFilters: NearbyFiltersState,
    nearbyActiveCount: Int,
    nearbyError: NearbyErrorState?,
    nearbyFiltersSummary: String?,
    onOpenNearbyFilters: (NearbyFilterSection) -> Unit,
    onResetNearbyFilter: (NearbyFilterSection) -> Unit,
    onResetNearbyCategories: () -> Unit,
    onResetNearbyFilters: () -> Unit,
    onRetryNearby: () -> Unit,
    onExpandRadius: () -> Unit,
    onShowCityPopular: () -> Unit,
    nearbyCategoryChips: List<CategoryChipUi>,
    nearbyFilteredOutCount: Int?,
    onOpenFeedCategories: () -> Unit,
    items: List<ExplainedItem>,
    nearbyFetchLimit: Int,
    nearbyVisibleCount: Int,
    nearbyIsLoadingMore: Boolean,
    savedOfferIds: Set<String>,
    hiddenOfferIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onOpenOffer: (ExplainedItem) -> Unit,
    onOverflowAction: (OfferOverflowAction, ExplainedItem) -> Unit,
    photoPeekEnabled: Boolean,
    showBrandFilter: Boolean,
    onLoadMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing)) {
        NewOffersFilters(
            locationDisplayLabel = locationDisplayLabel,
            nearbyFilters = nearbyFilters,
            nearbyActiveCount = nearbyActiveCount,
            onOpenNearbyFilters = onOpenNearbyFilters,
            onResetNearbyFilter = onResetNearbyFilter,
            onResetNearbyCategories = onResetNearbyCategories,
            nearbyCategoryChips = nearbyCategoryChips,
            onShowAllFeed = null,
            showBrandFilter = showBrandFilter,
            showTitle = false,
            showFilterChips = true,
        )
        FeedOffersColumn(
            items = items,
            nearbyFetchLimit = nearbyFetchLimit,
            nearbyVisibleCount = nearbyVisibleCount,
            nearbyIsLoadingMore = nearbyIsLoadingMore,
            nearbySort = nearbyFilters.sort,
            savedOfferIds = savedOfferIds,
            hiddenOfferIds = hiddenOfferIds,
            onToggleSave = onToggleSave,
            onOpenOffer = onOpenOffer,
            onOverflowAction = onOverflowAction,
            nearbyActiveCount = nearbyActiveCount,
            nearbyError = nearbyError,
            onRetry = onRetryNearby,
            filtersSummary = nearbyFiltersSummary,
            nearbyFilteredOutCount = nearbyFilteredOutCount,
            onResetFilters = onResetNearbyFilters,
            onExpandRadius = onExpandRadius,
            photoPeekEnabled = photoPeekEnabled,
            onLoadMore = onLoadMore,
        )
    }
}

private data class UserOffersTabItem(
    val label: String,
    val count: Int,
    val value: UserOffersTab,
)

@Composable
private fun UserOffersTabs(
    items: List<UserOffersTabItem>,
    selected: UserOffersTab,
    onSelect: (UserOffersTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val isSelected = item.value == selected
            val label = if (item.count > 0) "${item.label} ${item.count}" else item.label
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.surfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clickable { onSelect(item.value) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdsExpandedList(
    counts: OfferCounts,
    selectedTab: UserOffersTab,
    onTabChange: (UserOffersTab) -> Unit,
    offersPreviewItems: Map<UserOffersTab, List<UserOfferCardUi>>,
    onOpenDraft: (String) -> Unit,
) {
    val items = offersPreviewItems[selectedTab].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing)) {
        UserOffersTabs(
            items = listOf(
                UserOffersTabItem("Активные", counts.active, UserOffersTab.ACTIVE),
                UserOffersTabItem("Черновики", counts.drafts, UserOffersTab.DRAFTS),
                UserOffersTabItem("Завершённые", counts.completed, UserOffersTab.COMPLETED),
            ),
            selected = selectedTab,
            onSelect = onTabChange,
        )

        if (items.isEmpty()) {
            EmptyInlineCard(
                icon = Icons.Outlined.Add,
                title = "Пока нет объявлений",
                subtitle = "Здесь появятся ваши объявления",
                actionLabel = null,
                onAction = {},
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing)) {
                items.take(10).forEach { offer ->
                    OfferPreviewCard(
                        offer = offer,
                        modifier = Modifier.fillMaxWidth(),
                        onContinueDraft = { onOpenDraft(offer.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesExpanded(
    threads: List<MessageThreadPreview>,
) {
    if (threads.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing)) {
            EmptyInlineCard(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Нет сообщений",
                subtitle = "Диалоги появятся после откликов",
                actionLabel = null,
                onAction = {},
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing)) {
        threads.take(12).forEach { thread ->
            MessageThreadCard(thread = thread)
        }
    }
}

@Composable
private fun MessageThreadCard(
    thread: MessageThreadPreview,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MessageAvatar(avatarUrl = thread.avatarUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = thread.contactName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = thread.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = thread.offerTitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = thread.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (thread.unreadCount > 0) {
                val label = if (thread.unreadCount > 99) "99+" else thread.unreadCount.toString()
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageAvatar(
    avatarUrl: String?,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(44.dp),
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotificationsExpanded() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EmptyInlineCard(
            icon = Icons.Outlined.NotificationsNone,
            title = "Нет уведомлений",
            subtitle = "Здесь появятся важные события",
            actionLabel = null,
            onAction = {},
        )
    }
}

@Composable
private fun SearchScreen(
    input: String,
    suggestions: List<MainSuggestItem>,
    frequentCategories: List<CategoryChipUi>,
    popularCategories: List<CategoryChipUi>,
    recentSearchQueries: List<RecentSearchQueryUi>,
    popularSearchQueries: List<PopularSearchQueryUi>,
    localIntents: List<LocalSearchIntent>,
    onLocalIntent: (LocalSearchIntent) -> Unit,
    onClearHistory: () -> Unit,
    stage: SearchStage,
    onStageChange: (SearchStage) -> Unit,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchByRawText: (String) -> Unit,
    onSuggestionClick: (MainSuggestItem) -> Unit,
    onSuggestionAction: (MainSuggestItem, SuggestionAction) -> Unit,
    onOpenResults: (ResultsPayload) -> Unit,
    onPhotoSearch: () -> Unit,
    onLinkSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    hasSelection: Boolean,
    resultsPayload: ResultsPayload?,
    resultsLoadingQuery: String?,
    onCloseResults: () -> Unit,
    onCloseToDashboard: () -> Unit,
    onOpenCreate: () -> Unit,
    navController: NavHostController?,
    viewModel: MainPageViewModel,
) {
    val handleInputChange: (String) -> Unit = { text ->
        onInputChange(text)
        if (text.isBlank()) {
            if (stage != SearchStage.Idle) {
                onStageChange(SearchStage.Idle)
            }
        } else if (stage != SearchStage.Focused) {
            onStageChange(SearchStage.Focused)
        }
    }
    when (stage) {
        SearchStage.Results -> {
            if (resultsPayload == null) {
                SearchResultsLoading(
                    queryText = resultsLoadingQuery ?: input,
                    onBack = onCloseResults,
                    onClose = onCloseToDashboard,
                )
                return
            }
            SwipeBackSurface(
                onDismiss = onCloseResults,
                background = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                },
            ) {
                ResultsPage(
                    payload = resultsPayload,
                    navController = navController,
                    onBack = onCloseResults,
                    onClose = onCloseToDashboard,
                    onEditQuery = {
                        onStageChange(SearchStage.Focused)
                        viewModel.onQueryChange(resultsPayload.queryText)
                        viewModel.refreshSuggestionsForCurrentInput()
                    },
                    onOpenCreate = onOpenCreate,
                    applySafeInsets = true,
                    extraBottomPadding = 0.dp,
                )
            }
        }
        SearchStage.Idle,
        SearchStage.Focused -> {
            SearchStart(
                input = input,
                suggestions = suggestions,
                recentSearchQueries = recentSearchQueries,
                popularSearchQueries = popularSearchQueries,
                focused = stage == SearchStage.Focused,
                onFocusChange = { focused ->
                    when {
                        focused && stage != SearchStage.Focused -> onStageChange(SearchStage.Focused)
                        !focused && input.isBlank() -> onStageChange(SearchStage.Idle)
                    }
                },
                onInputChange = handleInputChange,
                onSearch = onSearch,
                onSearchByRawText = onSearchByRawText,
                onSuggestionClick = onSuggestionClick,
                onSuggestionAction = onSuggestionAction,
                onClearHistory = onClearHistory,
                onPhotoSearch = onPhotoSearch,
                onLinkSearch = onLinkSearch,
                onVoiceSearch = onVoiceSearch,
                hasSelection = hasSelection,
            )
        }
    }
}

@Composable
private fun SearchStart(
    input: String,
    suggestions: List<MainSuggestItem>,
    recentSearchQueries: List<RecentSearchQueryUi>,
    popularSearchQueries: List<PopularSearchQueryUi>,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchByRawText: (String) -> Unit,
    onSuggestionClick: (MainSuggestItem) -> Unit,
    onSuggestionAction: (MainSuggestItem, SuggestionAction) -> Unit,
    onClearHistory: () -> Unit,
    onPhotoSearch: () -> Unit,
    onLinkSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    hasSelection: Boolean,
) {
    val hubMode = input.isBlank()
    val hasHubContent = recentSearchQueries.isNotEmpty() || popularSearchQueries.isNotEmpty()
    val showTabsPanel = hubMode && (focused || hasHubContent)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        )

        Surface(
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InputRow(
                            input = input,
                            onInputChange = onInputChange,
                            onSubmit = { onSearch() },
                            suggestions = suggestions,
                            onSuggestionClick = onSuggestionClick,
                            onSuggestionAction = onSuggestionAction,
                            hideSuggestions = !focused || hubMode,
                            hasSelection = hasSelection,
                            onPhotoClick = onPhotoSearch,
                            onLinkClick = onLinkSearch,
                            onVoiceClick = onVoiceSearch,
                            onClear = {
                                onInputChange("")
                                onFocusChange(false)
                            },
                            showMediaActions = true,
                            showInlineMediaActions = true,
                            showPhotoAction = true,
                            showLinkAction = false,
                            showVoiceAction = true,
                            showHubSuggestionsWhenEmpty = false,
                            showHubSuggestionsWhenUnfocused = false,
                            inputFontWeight = FontWeight.SemiBold,
                            enforceFocus = focused,
                            autoFocus = focused,
                            onFocusChange = onFocusChange,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .align(Alignment.CenterHorizontally),
                        )
                        if (showTabsPanel) {
                            SearchHubTabsPanel(
                                recentSearchQueries = recentSearchQueries,
                                popularSearchQueries = popularSearchQueries,
                                onRecentQueryClick = { queryText ->
                                    onSearchByRawText(queryText)
                                },
                                onPopularQueryClick = { queryText ->
                                    onSearchByRawText(queryText)
                                },
                                onClearHistory = onClearHistory,
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsLoading(
    queryText: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    SwipeBackSurface(
        onDismiss = onBack,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(
                    title = "Результаты",
                    onBack = onBack,
                    applySafeInsets = true,
                    trailingContent = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Закрыть",
                            )
                        }
                    },
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = queryText.ifBlank { "Ищем результаты…" },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    items(count = 5) {
                        ScenarioCardSkeleton(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private enum class SearchHubTab {
    Recent,
    Popular,
}

private enum class SearchPopularPeriod {
    Today,
    Month,
    Year,
    AllTime,
}

@Composable
private fun SearchHubTabsPanel(
    recentSearchQueries: List<RecentSearchQueryUi>,
    popularSearchQueries: List<PopularSearchQueryUi>,
    onRecentQueryClick: (String) -> Unit,
    onPopularQueryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(SearchHubTab.Recent) }
    var activePeriod by rememberSaveable { mutableStateOf(SearchPopularPeriod.Today) }

    val popularByPeriod = remember(popularSearchQueries, activePeriod) {
        popularSearchQueries
            .map { item ->
                val count = when (activePeriod) {
                    SearchPopularPeriod.Today -> item.todayCount
                    SearchPopularPeriod.Month -> item.monthCount
                    SearchPopularPeriod.Year -> item.yearCount
                    SearchPopularPeriod.AllTime -> item.totalCount
                }
                item to count
            }
            .filter { (_, count) -> count > 0 }
            .sortedWith(
                compareByDescending<Pair<PopularSearchQueryUi, Int>> { (_, count) -> count }
                    .thenByDescending { (item, _) -> item.lastUsedAtMillis },
            )
            .take(30)
    }
    val hasAnyPopular = popularSearchQueries.any { item -> item.totalCount > 0 }

    LaunchedEffect(recentSearchQueries.isNotEmpty(), hasAnyPopular) {
        if (recentSearchQueries.isEmpty() && hasAnyPopular) {
            activeTab = SearchHubTab.Popular
        }
    }

    LaunchedEffect(activeTab, popularByPeriod.isEmpty(), hasAnyPopular) {
        if (activeTab == SearchHubTab.Popular && popularByPeriod.isEmpty() && hasAnyPopular) {
            activePeriod = SearchPopularPeriod.AllTime
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SlidingUnderlineTabs(
                    items = listOf(
                        SlidingUnderlineTabItem(label = "Недавние", value = SearchHubTab.Recent),
                        SlidingUnderlineTabItem(label = "Популярное", value = SearchHubTab.Popular),
                    ),
                    selected = activeTab,
                    onSelect = { activeTab = it },
                    modifier = Modifier.weight(1f),
                )
                if (activeTab == SearchHubTab.Recent && recentSearchQueries.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        shape = RectangleShape,
                        modifier = Modifier.heightIn(min = 34.dp),
                    ) {
                        Text("Очистить")
                    }
                }
            }

            when (activeTab) {
                SearchHubTab.Recent -> {
                    if (recentSearchQueries.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(recentSearchQueries, key = { query -> "${query.text}:${query.usedAtMillis}" }) { query ->
                                SearchRecentQueryRow(
                                    text = query.text,
                                    relativeTime = formatSearchRelativeTime(query.usedAtMillis),
                                    onClick = { onRecentQueryClick(query.text) },
                                )
                            }
                        }
                    } else {
                        SearchHubEmptyLine(
                            text = "Недавние запросы появятся после первого поиска.",
                        )
                    }
                }

                SearchHubTab.Popular -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SearchHubPeriodChip(
                            text = "Сегодня",
                            active = activePeriod == SearchPopularPeriod.Today,
                            onClick = { activePeriod = SearchPopularPeriod.Today },
                        )
                        SearchHubPeriodChip(
                            text = "Месяц",
                            active = activePeriod == SearchPopularPeriod.Month,
                            onClick = { activePeriod = SearchPopularPeriod.Month },
                        )
                        SearchHubPeriodChip(
                            text = "Год",
                            active = activePeriod == SearchPopularPeriod.Year,
                            onClick = { activePeriod = SearchPopularPeriod.Year },
                        )
                        SearchHubPeriodChip(
                            text = "Всё",
                            active = activePeriod == SearchPopularPeriod.AllTime,
                            onClick = { activePeriod = SearchPopularPeriod.AllTime },
                        )
                    }

                    if (popularByPeriod.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(popularByPeriod, key = { (query, _) -> query.text }) { (query, count) ->
                                SearchQueryChip(
                                    text = query.text,
                                    countText = count.toString(),
                                    onClick = { onPopularQueryClick(query.text) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        SearchHubEmptyLine(
                            text = "Популярные запросы появятся после нескольких поисков.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHubEmptyLine(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private data class SlidingUnderlineTabItem<T>(
    val label: String,
    val value: T,
)

@Composable
private fun <T> SlidingUnderlineTabs(
    items: List<SlidingUnderlineTabItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tabWidths = remember(items) { mutableStateMapOf<Int, Int>() }
    val tabOffsets = remember(items) { mutableStateMapOf<Int, Int>() }
    val selectedIndex = items.indexOfFirst { item -> item.value == selected }.coerceAtLeast(0)
    val indicatorWidthPx = tabWidths[selectedIndex] ?: 0
    val indicatorOffsetPx = tabOffsets[selectedIndex] ?: 0
    val animatedWidth by animateFloatAsState(
        targetValue = indicatorWidthPx.toFloat(),
        animationSpec = tween(durationMillis = 220),
        label = "search_tab_indicator_width",
    )
    val animatedOffset by animateFloatAsState(
        targetValue = indicatorOffsetPx.toFloat(),
        animationSpec = tween(durationMillis = 220),
        label = "search_tab_indicator_offset",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = item.value == selected
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            tabWidths[index] = coordinates.size.width
                            tabOffsets[index] = coordinates.positionInParent().x.roundToInt()
                        }
                        .clickable { onSelect(item.value) }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = if (isSelected) Color(0xFF202124) else Color(0xFF5F6368),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            if (animatedWidth > 0f) {
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { animatedOffset.toDp() })
                        .width(with(density) { animatedWidth.toDp() })
                        .height(2.dp)
                        .background(Color.Black),
                )
            }
        }
    }
}

@Composable
private fun SearchHubPeriodChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (active) {
            Color(0xFF3C4043)
        } else {
            Color(0xFFF1F3F4)
        },
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
            color = if (active) Color.White else Color(0xFF202124),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SearchQueryChip(
    text: String,
    countText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!countText.isNullOrBlank()) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchRecentQueryRow(
    text: String,
    relativeTime: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = relativeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun formatSearchRelativeTime(usedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMillis - usedAtMillis).coerceAtLeast(0L)
    val minuteMs = 60_000L
    val hourMs = 60 * minuteMs
    val dayMs = 24 * hourMs
    val monthMs = 30 * dayMs
    val yearMs = 365 * dayMs

    return when {
        diffMs < minuteMs -> "только что"
        diffMs < hourMs -> {
            val value = (diffMs / minuteMs).toInt().coerceAtLeast(1)
            "$value ${ruPlural(value, "минута", "минуты", "минут")} назад"
        }
        diffMs < dayMs -> {
            val value = (diffMs / hourMs).toInt().coerceAtLeast(1)
            "$value ${ruPlural(value, "час", "часа", "часов")} назад"
        }
        diffMs < monthMs -> {
            val value = (diffMs / dayMs).toInt().coerceAtLeast(1)
            "$value ${ruPlural(value, "день", "дня", "дней")} назад"
        }
        diffMs < yearMs -> {
            val value = (diffMs / monthMs).toInt().coerceAtLeast(1)
            "$value ${ruPlural(value, "месяц", "месяца", "месяцев")} назад"
        }
        else -> {
            val value = (diffMs / yearMs).toInt().coerceAtLeast(1)
            "$value ${ruPlural(value, "год", "года", "лет")} назад"
        }
    }
}

private fun ruPlural(value: Int, one: String, few: String, many: String): String {
    val mod10 = value % 10
    val mod100 = value % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

@Composable
private fun GreetingBlock(
    title: String,
    subtitle: String,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Профиль",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class DigestTileData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String,
    val label: String,
    val secondary: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun ValueDigestSection(
    activitySummary: ActivitySummary,
    visibleOffersCount: Int,
    locationLabel: String,
    offerCounts: OfferCounts,
    onOpenNotifs: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenOffers: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    val tiles = buildList {
        add(
            DigestTileData(
                icon = Icons.Outlined.StarOutline,
                value = formatDigestCount(activitySummary.triggeredSubscriptions),
                label = "Сработали отслеживания",
                secondary = null,
                onClick = onOpenNotifs,
            )
        )
        add(
            DigestTileData(
                icon = Icons.Outlined.Visibility,
                value = formatDigestCount(visibleOffersCount),
                label = "Рядом сейчас",
                secondary = locationLabel,
                onClick = onOpenNearby,
            )
        )
        if (offerCounts.total > 0) {
            add(
                DigestTileData(
                    icon = Icons.AutoMirrored.Outlined.ViewList,
                    value = formatDigestCount(offerCounts.total),
                    label = "Мои объявления",
                    secondary = null,
                    onClick = onOpenOffers,
                )
            )
        }
        if (activitySummary.messagesUnread > 0) {
            add(
                DigestTileData(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    value = formatDigestCount(activitySummary.messagesUnread),
                    label = "Сообщения",
                    secondary = null,
                    onClick = onOpenMessages,
                )
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tiles.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { tile ->
                        DigestTile(
                            data = tile,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DigestTile(
    data: DigestTileData,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        modifier = modifier
            .heightIn(min = 70.dp)
            .clickable(onClick = data.onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = data.value,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = data.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            data.secondary?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDigestCount(value: Int): String =
    when {
        value > 99 -> "99+"
        value < 0 -> "0"
        else -> value.toString()
    }

@Composable
private fun NearbyFeedSection(
    locationLabel: String,
    locationDisplayLabel: String,
    nearbyFilters: NearbyFiltersState,
    nearbyActiveCount: Int,
    nearbyError: NearbyErrorState?,
    nearbyFiltersSummary: String?,
    onOpenNearbyFilters: (NearbyFilterSection) -> Unit,
    onResetNearbyFilter: (NearbyFilterSection) -> Unit,
    onResetNearbyCategories: () -> Unit,
    onResetNearbyFilters: () -> Unit,
    onRetryNearby: () -> Unit,
    nearbyCategoryChips: List<CategoryChipUi>,
    onOpenFeedCategories: () -> Unit,
    onShowAllFeed: (() -> Unit)?,
    onShowAllCategories: () -> Unit,
    onExpandRadius: () -> Unit,
    onShowCityPopular: () -> Unit,
    showLocationPermissionHint: Boolean,
    onRequestLocationPermission: () -> Unit,
    showBrandFilter: Boolean,
    items: List<ExplainedItem>,
    nearbyFilteredOutCount: Int?,
    savedOfferIds: Set<String>,
    hiddenOfferIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onOpenOffer: (ExplainedItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NewOffersFilters(
            locationDisplayLabel = locationDisplayLabel,
            nearbyFilters = nearbyFilters,
            nearbyActiveCount = nearbyActiveCount,
            onOpenNearbyFilters = onOpenNearbyFilters,
            onResetNearbyFilter = onResetNearbyFilter,
            onResetNearbyCategories = onResetNearbyCategories,
            nearbyCategoryChips = nearbyCategoryChips,
            onShowAllFeed = onShowAllFeed,
            showBrandFilter = showBrandFilter,
            showTitle = false,
            showFilterChips = false,
        )
        if (showLocationPermissionHint) {
            NearbyLocationHintCard(onAction = onRequestLocationPermission)
        } else {
            FeedOffersRow(
                items = items,
                savedOfferIds = savedOfferIds,
                hiddenOfferIds = hiddenOfferIds,
                onToggleSave = onToggleSave,
                onOpenOffer = onOpenOffer,
                nearbySort = nearbyFilters.sort,
                nearbyActiveCount = nearbyActiveCount,
                nearbyError = nearbyError,
                onRetry = onRetryNearby,
                filtersSummary = nearbyFiltersSummary,
                nearbyFilteredOutCount = nearbyFilteredOutCount,
                onResetFilters = onResetNearbyFilters,
                onExpandRadius = onExpandRadius,
            )
        }
    }
}

@Composable
private fun NewOffersFilters(
    locationDisplayLabel: String,
    nearbyFilters: NearbyFiltersState,
    nearbyActiveCount: Int,
    onOpenNearbyFilters: (NearbyFilterSection) -> Unit,
    onResetNearbyFilter: (NearbyFilterSection) -> Unit,
    onResetNearbyCategories: () -> Unit,
    nearbyCategoryChips: List<CategoryChipUi>,
    onShowAllFeed: (() -> Unit)?,
    showBrandFilter: Boolean,
    showTitle: Boolean = true,
    showFilterChips: Boolean = true,
) {
    val categoryActive = remember(nearbyFilters) { nearbyFilters.categoryCodes.isNotEmpty() }
    val locationActive = nearbyFilters.locationIsActive()
    val priceActive = nearbyFilters.priceIsActive()
    val conditionActive = nearbyFilters.conditionIsActive()
    val deliveryActive = nearbyFilters.deliveryIsActive()
    val brandActive = nearbyFilters.brandsIsActive()
    val postedAtActive = nearbyFilters.postedAtIsActive()
    val sortActive = nearbyFilters.sortIsActive()

    val categoryLabel = remember(nearbyCategoryChips) {
        if (categoryActive) categorySummary(nearbyCategoryChips) else "Категория"
    }
    val brandLabel = remember(nearbyFilters) { nearbyFilters.brandsLabel() }
    val priceLabel = remember(nearbyFilters) { nearbyFilters.priceLabel() }
    val conditionLabel = remember(nearbyFilters) { nearbyFilters.conditionLabel() }
    val deliveryLabel = remember(nearbyFilters) { nearbyFilters.deliveryLabel() }
    val postedAtLabel = remember(nearbyFilters) { nearbyFilters.postedAtLabel() }
    val sortLabel = remember(nearbyFilters) { nearbyFilters.sortLabel() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTitle || onShowAllFeed != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showTitle) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Рядом",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        NearbyInlineLocationLabel(
                            label = locationDisplayLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    NearbyInlineLocationLabel(
                        label = locationDisplayLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
                onShowAllFeed?.let { action ->
                    IconButton(
                        onClick = action,
                        modifier = Modifier.sizeIn(minWidth = 36.dp, minHeight = 36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Показать все",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (showFilterChips) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    NearbyFilterChip(
                        label = "Радиус поиска",
                        icon = Icons.Outlined.Place,
                        selected = locationActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.Location) },
                        onRemove = if (locationActive) {
                            { onResetNearbyFilter(NearbyFilterSection.Location) }
                        } else {
                            null
                        },
                    )
                }
                item {
                    NearbyFilterChip(
                        label = categoryLabel,
                        icon = Icons.Outlined.GridView,
                        selected = categoryActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.Category) },
                        onRemove = if (categoryActive) onResetNearbyCategories else null,
                    )
                }
                if (showBrandFilter) {
                    item {
                        NearbyFilterChip(
                            label = brandLabel,
                            icon = Icons.Outlined.StarOutline,
                            selected = brandActive,
                            onClick = { onOpenNearbyFilters(NearbyFilterSection.Brand) },
                            onRemove = if (brandActive) {
                                { onResetNearbyFilter(NearbyFilterSection.Brand) }
                            } else null,
                        )
                    }
                }
                item {
                    NearbyFilterChip(
                        label = priceLabel,
                        icon = Icons.Outlined.LocalOffer,
                        selected = priceActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.Price) },
                        onRemove = if (priceActive) {
                            { onResetNearbyFilter(NearbyFilterSection.Price) }
                        } else null,
                    )
                }
                item {
                    NearbyFilterChip(
                        label = conditionLabel,
                        icon = Icons.Outlined.AutoAwesome,
                        selected = conditionActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.Condition) },
                        onRemove = if (conditionActive) {
                            { onResetNearbyFilter(NearbyFilterSection.Condition) }
                        } else null,
                    )
                }
                item {
                    NearbyFilterChip(
                        label = deliveryLabel,
                        icon = Icons.Outlined.LocalShipping,
                        selected = deliveryActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.Delivery) },
                        onRemove = if (deliveryActive) {
                            { onResetNearbyFilter(NearbyFilterSection.Delivery) }
                        } else {
                            null
                        },
                    )
                }
                item {
                    NearbyFilterChip(
                        label = postedAtLabel,
                        icon = Icons.Outlined.Schedule,
                        selected = postedAtActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.PostedAt) },
                        onRemove = if (postedAtActive) {
                            { onResetNearbyFilter(NearbyFilterSection.PostedAt) }
                        } else null,
                    )
                }
                item {
                    NearbyFilterChip(
                        label = sortLabel,
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        selected = sortActive,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.Sort) },
                        onRemove = if (sortActive) {
                            { onResetNearbyFilter(NearbyFilterSection.Sort) }
                        } else null,
                    )
                }
                item {
                    NearbyFilterChip(
                        label = "Все фильтры",
                        icon = Icons.Outlined.Tune,
                        selected = nearbyActiveCount > 0,
                        badgeCount = nearbyActiveCount,
                        onClick = { onOpenNearbyFilters(NearbyFilterSection.All) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedCategoriesSummary(
    selected: List<CategoryChipUi>,
    onAllCategories: () -> Unit,
    onShowAll: () -> Unit,
) {
    val filtered = selected.filterNot { it.title.equals("Все", ignoreCase = true) }
    if (filtered.isEmpty()) return

    TwoLineChipRow(
        labels = filtered.map { it.title },
        onChipClick = onAllCategories,
        onShowAll = onShowAll,
    )
}

@Composable
private fun TwoLineChipRow(
    labels: List<String>,
    onChipClick: () -> Unit,
    onShowAll: () -> Unit,
) {
    val spacing = 8.dp
    SubcomposeLayout { constraints ->
        val spacingPx = spacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val lines = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        val lineWidths = mutableListOf<Int>()
        val lineHeights = mutableListOf<Int>()
        lines.add(mutableListOf())
        lineWidths.add(0)
        lineHeights.add(0)

        var lineIndex = 0
        var processed = 0
        var overflow = false

        labels.forEachIndexed { index, label ->
            if (overflow) return@forEachIndexed
            val placeable = subcompose("chip_$index") {
                CategoryChip(label = label, highlighted = false, onClick = onChipClick)
            }.first().measure(constraints.copy(minWidth = 0, minHeight = 0))

            val currentWidth = lineWidths[lineIndex]
            val nextWidth = if (lines[lineIndex].isEmpty()) {
                placeable.width
            } else {
                currentWidth + spacingPx + placeable.width
            }
            if (nextWidth <= maxWidth || lines[lineIndex].isEmpty()) {
                lines[lineIndex].add(placeable)
                lineWidths[lineIndex] = nextWidth
                lineHeights[lineIndex] = max(lineHeights[lineIndex], placeable.height)
                processed += 1
            } else if (lineIndex == 0) {
                lineIndex = 1
                lines.add(mutableListOf(placeable))
                lineWidths.add(placeable.width)
                lineHeights.add(placeable.height)
                processed += 1
            } else {
                overflow = true
            }
        }

        if (processed < labels.size) {
            overflow = true
        }

        if (overflow) {
            if (lines.size < 2) {
                lines.add(mutableListOf())
                lineWidths.add(0)
                lineHeights.add(0)
            }
            val showAllPlaceable = subcompose("show_all") {
                CategoryChip(label = "Показать все", highlighted = true, onClick = onShowAll)
            }.first().measure(constraints.copy(minWidth = 0, minHeight = 0))

            val targetLine = 1
            var currentWidth = lineWidths[targetLine]
            while (lines[targetLine].isNotEmpty()) {
                val needed = (if (lines[targetLine].isEmpty()) 0 else currentWidth + spacingPx) + showAllPlaceable.width
                if (needed <= maxWidth) break
                val removedIndex = lines[targetLine].lastIndex
                val removed = lines[targetLine].removeAt(removedIndex)
                currentWidth -= removed.width
                if (lines[targetLine].isNotEmpty()) {
                    currentWidth -= spacingPx
                }
                lineHeights[targetLine] = lines[targetLine].maxOfOrNull { it.height } ?: 0
            }
            val neededWidth = (if (lines[targetLine].isEmpty()) 0 else currentWidth + spacingPx) + showAllPlaceable.width
            lineWidths[targetLine] = neededWidth
            lineHeights[targetLine] = max(lineHeights[targetLine], showAllPlaceable.height)
            lines[targetLine].add(showAllPlaceable)
        }

        val lineCount = lines.indices.count { lines[it].isNotEmpty() }
        val totalHeight = if (lineCount == 0) {
            0
        } else {
            val heightSum = lines.indices.sumOf { index ->
                if (lines[index].isEmpty()) 0 else lineHeights[index]
            }
            heightSum + spacingPx * (lineCount - 1)
        }

        layout(maxWidth, totalHeight) {
            var y = 0
            lines.forEachIndexed { index, line ->
                if (line.isEmpty()) return@forEachIndexed
                var x = 0
                line.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + spacingPx
                }
                y += lineHeights[index] + spacingPx
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
private fun CategoryChip(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Normal,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NearbyFilterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    badgeCount: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val labelColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val iconColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val border = if (selected) colors.primary.copy(alpha = 0.3f) else colors.outlineVariant.copy(alpha = 0.6f)
    Box {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Сбросить",
                            tint = iconColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (selected) colors.primaryContainer else colors.surfaceVariant,
                labelColor = labelColor,
                iconColor = iconColor,
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer,
                selectedLeadingIconColor = colors.onPrimaryContainer,
                selectedTrailingIconColor = colors.onPrimaryContainer,
            ),
            border = BorderStroke(1.dp, border),
        )
        if (badgeCount != null && badgeCount > 0) {
            val badgeLabel = if (badgeCount > 99) "99+" else badgeCount.toString()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp),
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NearbyFiltersSheet(
    section: NearbyFilterSection,
    draft: NearbyFiltersState,
    categorySummary: String,
    activeCount: Int,
    showBrandFilter: Boolean,
    brandFacets: List<NearbyBrandFacet>,
    conditionFacets: List<NearbyValueFacet>,
    deliveryChannelFacets: List<NearbyValueFacet>,
    applyLabel: String,
    applyHint: String?,
    userLocation: String?,
    userCountry: String?,
    locationPermission: NearbyLocationPermission,
    nearbyError: NearbyErrorState?,
    applyLoading: Boolean,
    applyCountLoading: Boolean,
    showZeroActions: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    onSectionChange: (NearbyFilterSection) -> Unit,
    onDraftChange: (NearbyFiltersState) -> Unit,
    onResetDraft: () -> Unit,
    onOpenCategories: () -> Unit,
    onShowAllCategories: (() -> Unit)?,
    onZeroReset: () -> Unit,
    onZeroExpandRadius: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val canReset = draft != NearbyFiltersState()
    val minPrice = draft.priceMin
    val maxPrice = draft.priceMax
    val priceInvalid = minPrice != null && maxPrice != null && minPrice > maxPrice
    val cityValue = draft.selectedPlace?.city?.trim().orEmpty()
    val cityInvalid = draft.locationScope == NearbyScope.CITY && cityValue.isBlank()
    val locationInvalid = cityInvalid
    LaunchedEffect(section) {
        runCatching { sheetState.expand() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (section) {
                NearbyFilterSection.All -> {
                    NearbySheetTopBar(
                        title = "Фильтры",
                        onClose = onDismiss,
                        onReset = if (canReset) onResetDraft else null,
                    )
                    nearbyError?.let { error ->
                        NearbyErrorInline(
                            message = error.message,
                            onRetry = onRetry,
                        )
                    }
                    if (activeCount > 0) {
                        Text(
                            text = "Активно: $activeCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            NearbySheetRow(
                                title = "Радиус поиска",
                                summary = draft.locationLabel(userLocation, userCountry),
                                onClick = { onSectionChange(NearbyFilterSection.Location) },
                            )
                        }
                        item {
                            NearbySheetRow(
                                title = "Категория",
                                summary = categorySummary,
                                onClick = onOpenCategories,
                            )
                        }
                        if (showBrandFilter) {
                            item {
                                NearbySheetRow(
                                    title = "Бренд",
                                    summary = draft.brandsSummary(),
                                    onClick = { onSectionChange(NearbyFilterSection.Brand) },
                                )
                            }
                        }
                        item {
                            NearbySheetRow(
                                title = "Цена",
                                summary = draft.priceSummary(),
                                onClick = { onSectionChange(NearbyFilterSection.Price) },
                            )
                        }
                        item {
                            NearbySheetRow(
                                title = "Состояние",
                                summary = draft.conditionSummary(),
                                onClick = { onSectionChange(NearbyFilterSection.Condition) },
                            )
                        }
                        item {
                            NearbySheetRow(
                                title = "Способ получения",
                                summary = draft.deliverySummary(),
                                onClick = { onSectionChange(NearbyFilterSection.Delivery) },
                            )
                        }
                        item {
                            NearbySheetRow(
                                title = "Дата размещения",
                                summary = draft.postedAtSummary(),
                                onClick = { onSectionChange(NearbyFilterSection.PostedAt) },
                            )
                        }
                        item {
                            NearbySheetRow(
                                title = "Сортировка",
                                summary = draft.sortSummary(),
                                onClick = { onSectionChange(NearbyFilterSection.Sort) },
                            )
                        }
                    }
                }
                NearbyFilterSection.Location -> {
                    NearbySheetTopBar(
                        title = "Радиус поиска",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.resetSection(NearbyFilterSection.Location)) },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (locationPermission == NearbyLocationPermission.DeniedTemporary ||
                            locationPermission == NearbyLocationPermission.DeniedPermanent
                        ) {
                            NearbyPermissionBanner(
                                onRequestPermission = onRequestLocationPermission,
                                onOpenSettings = if (locationPermission == NearbyLocationPermission.DeniedPermanent) {
                                    onOpenLocationSettings
                                } else {
                                    null
                                },
                                onPickCity = { onDraftChange(draft.copy(locationScope = NearbyScope.CITY)) },
                            )
                        }
                        val showCityOption = locationPermission != NearbyLocationPermission.Granted
                        if (showCityOption || draft.locationScope == NearbyScope.CITY) {
                            NearbyRadioRow(
                                label = "Город",
                                selected = draft.locationScope == NearbyScope.CITY,
                                onSelect = {
                                    val updatedSort =
                                        if (draft.sort == NearbySort.Distance) NearbySort.Newest else draft.sort
                                    onDraftChange(
                                        draft.copy(
                                            locationScope = NearbyScope.CITY,
                                            sort = updatedSort,
                                        )
                                    )
                                },
                            )
                        }
                        if (draft.locationScope == NearbyScope.CITY) {
                            var cityText by remember(draft.selectedPlace?.city) {
                                mutableStateOf(draft.selectedPlace?.city.orEmpty())
                            }
                            TextField(
                                value = cityText,
                                onValueChange = { value ->
                                    cityText = value
                                    onDraftChange(
                                        draft.copy(
                                            selectedPlace = NearbyPlace(
                                                city = value.trim().ifBlank { null },
                                            )
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isError = cityInvalid,
                                supportingText = if (cityInvalid) {
                                    { Text("Укажите город") }
                                } else null,
                                placeholder = { Text("Город") },
                            )
                        }
                        if (draft.locationScope == NearbyScope.NEARBY) {
                            val presets = NEARBY_RADIUS_PRESETS
                            val minRadius = presets.first()
                            val maxRadius = presets.last()
                            val shownRadius = draft.radiusKm.coerceIn(minRadius, maxRadius)
                            Text(
                                text = "Радиус: $shownRadius км",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = shownRadius.toFloat(),
                                onValueChange = { value ->
                                    val snapped = presets.minByOrNull { preset -> abs(preset - value) } ?: minRadius
                                    onDraftChange(draft.copy(radiusKm = snapped))
                                },
                                valueRange = minRadius.toFloat()..maxRadius.toFloat(),
                                steps = (presets.size - 2).coerceAtLeast(0),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                presets.forEach { value ->
                                    FilterChip(
                                        selected = draft.radiusKm == value,
                                        onClick = { onDraftChange(draft.copy(radiusKm = value)) },
                                        label = { Text("$value км") },
                                    )
                                }
                            }
                        }
                    }
                }
                NearbyFilterSection.Category -> {
                    NearbySheetTopBar(
                        title = "Категория",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Сейчас: $categorySummary",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenCategories, modifier = Modifier.fillMaxWidth()) {
                            Text("Выбрать категорию")
                        }
                        onShowAllCategories?.let { action ->
                            TextButton(onClick = action, modifier = Modifier.align(Alignment.End)) {
                                Text("Показать выбранные")
                            }
                        }
                    }
                }
                NearbyFilterSection.Brand -> {
                    NearbySheetTopBar(
                        title = "Бренд",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.copy(brands = emptyList())) },
                    )
                    val facets = brandFacets
                    val selectedIds = remember(draft.brands) {
                        draft.brands.mapNotNull { brand -> brand.id.takeIf { it.isNotBlank() } }.toSet()
                    }
                    var query by remember { mutableStateOf("") }
                    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
                    val showSearch = facets.size > 20
                    val filtered = if (normalizedQuery.isBlank()) {
                        facets
                    } else {
                        facets.filter { facet -> facet.name.lowercase(Locale.getDefault()).contains(normalizedQuery) }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (showSearch) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Поиск бренда") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (facets.isEmpty()) {
                            Text(
                                text = "Пока нет брендов в текущей выдаче.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    NearbyBrandRow(
                                        label = "Все бренды",
                                        count = null,
                                        selected = selectedIds.isEmpty(),
                                        onClick = { onDraftChange(draft.copy(brands = emptyList())) },
                                    )
                                }
                                items(filtered, key = { it.id }) { facet ->
                                    val isSelected = selectedIds.contains(facet.id)
                                    NearbyBrandRow(
                                        label = facet.name,
                                        count = facet.count,
                                        selected = isSelected,
                                        onClick = {
                                            val updated = if (isSelected) {
                                                draft.brands.filterNot { brand -> brand.id == facet.id }
                                            } else {
                                                (draft.brands + NearbyBrand(id = facet.id, name = facet.name))
                                                    .distinctBy { brand -> brand.id }
                                            }
                                            onDraftChange(draft.copy(brands = updated))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                NearbyFilterSection.Price -> {
                    val sliderMin = 0
                    val sliderMax = remember(draft.priceMin, draft.priceMax) {
                        nearbyPriceSliderMax(draft.priceMin, draft.priceMax)
                    }
                    var minText by remember(draft.priceMin) {
                        mutableStateOf(draft.priceMin?.toString().orEmpty())
                    }
                    var maxText by remember(draft.priceMax) {
                        mutableStateOf(draft.priceMax?.toString().orEmpty())
                    }
                    NearbySheetTopBar(
                        title = "Цена",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.resetSection(NearbyFilterSection.Price)) },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val sliderDraftMin = draft.priceMin?.coerceIn(sliderMin, sliderMax) ?: sliderMin
                        val sliderDraftMax = draft.priceMax?.coerceIn(sliderMin, sliderMax) ?: sliderMax
                        val sliderStart = if (sliderDraftMin <= sliderDraftMax) sliderDraftMin else sliderDraftMax
                        val sliderEnd = if (sliderDraftMax >= sliderDraftMin) sliderDraftMax else sliderDraftMin
                        Text(
                            text = "${formatPriceInput(sliderStart)} ₽ — ${formatPriceInput(sliderEnd)} ₽",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        RangeSlider(
                            value = sliderStart.toFloat()..sliderEnd.toFloat(),
                            onValueChange = { value ->
                                val nextMin = value.start.toInt().coerceIn(sliderMin, sliderMax)
                                val nextMax = value.endInclusive.toInt().coerceIn(nextMin, sliderMax)
                                minText = nextMin.toString()
                                maxText = nextMax.toString()
                                onDraftChange(
                                    draft.copy(
                                        priceMin = nextMin,
                                        priceMax = nextMax,
                                    )
                                )
                            },
                            valueRange = sliderMin.toFloat()..sliderMax.toFloat(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextField(
                                value = minText,
                                onValueChange = { value ->
                                    minText = value.filter { it.isDigit() }
                                    val parsedMin = minText.toIntOrNull()?.coerceIn(sliderMin, sliderMax)
                                    val parsedMax = maxText.toIntOrNull()?.coerceIn(sliderMin, sliderMax)
                                    val normalizedMax = if (parsedMin != null && parsedMax != null && parsedMax < parsedMin) {
                                        parsedMin
                                    } else {
                                        parsedMax
                                    }
                                    if (normalizedMax != null && parsedMax != null && normalizedMax != parsedMax) {
                                        maxText = normalizedMax.toString()
                                    }
                                    onDraftChange(
                                        draft.copy(
                                            priceMin = parsedMin,
                                            priceMax = normalizedMax,
                                        )
                                    )
                                },
                                placeholder = { Text("От") },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f),
                            )
                            TextField(
                                value = maxText,
                                onValueChange = { value ->
                                    maxText = value.filter { it.isDigit() }
                                    val parsedMin = minText.toIntOrNull()?.coerceIn(sliderMin, sliderMax)
                                    val parsedMax = maxText.toIntOrNull()?.coerceIn(sliderMin, sliderMax)
                                    val normalizedMax = if (parsedMin != null && parsedMax != null && parsedMax < parsedMin) {
                                        parsedMin
                                    } else {
                                        parsedMax
                                    }
                                    if (normalizedMax != null && parsedMax != null && normalizedMax != parsedMax) {
                                        maxText = normalizedMax.toString()
                                    }
                                    onDraftChange(
                                        draft.copy(
                                            priceMin = parsedMin,
                                            priceMax = normalizedMax,
                                        )
                                    )
                                },
                                placeholder = { Text("До") },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (priceInvalid) {
                            Text(
                                text = "Минимум не может быть больше максимума",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                NearbyFilterSection.Condition -> {
                    NearbySheetTopBar(
                        title = "Состояние",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.resetSection(NearbyFilterSection.Condition)) },
                    )
                    val options = remember(conditionFacets, draft.condition) {
                        nearbyConditionOptions(
                            facets = conditionFacets,
                            selected = draft.condition,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { option ->
                            NearbyRadioRow(
                                label = option.condition.label,
                                count = option.count,
                                selected = draft.condition == option.condition,
                                onSelect = { onDraftChange(draft.copy(condition = option.condition)) },
                            )
                        }
                    }
                }
                NearbyFilterSection.Delivery -> {
                    NearbySheetTopBar(
                        title = "Способ получения",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.resetSection(NearbyFilterSection.Delivery)) },
                    )
                    val options = remember(deliveryChannelFacets, draft.delivery) {
                        nearbyDeliveryOptions(
                            facets = deliveryChannelFacets,
                            selected = draft.delivery,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { option ->
                            val checked = draft.delivery.contains(option.delivery)
                            NearbyCheckboxRow(
                                label = option.delivery.label,
                                count = option.count,
                                checked = checked,
                                onToggle = {
                                    val updated = if (checked) {
                                        draft.delivery - option.delivery
                                    } else {
                                        draft.delivery + option.delivery
                                    }
                                    onDraftChange(draft.copy(delivery = updated))
                                },
                            )
                        }
                    }
                }
                NearbyFilterSection.PostedAt -> {
                    NearbySheetTopBar(
                        title = "Дата размещения",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.resetSection(NearbyFilterSection.PostedAt)) },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(NearbyPostedAt.H24, NearbyPostedAt.D3).forEach { option ->
                            NearbyRadioRow(
                                label = option.label,
                                selected = draft.postedAt == option,
                                onSelect = { onDraftChange(draft.copy(postedAt = option)) },
                            )
                        }
                    }
                }
                NearbyFilterSection.Sort -> {
                    NearbySheetTopBar(
                        title = "Сортировка",
                        onBack = { onSectionChange(NearbyFilterSection.All) },
                        onReset = { onDraftChange(draft.resetSection(NearbyFilterSection.Sort)) },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NearbySort.entries.forEach { option ->
                            NearbyRadioRow(
                                label = option.label,
                                selected = draft.sort == option,
                                onSelect = { onDraftChange(draft.copy(sort = option)) },
                            )
                        }
                    }
                }
            }
            NearbySheetActionsRow(
                onApply = onApply,
                onCancel = onDismiss,
                applyEnabled = !priceInvalid && !applyLoading && !locationInvalid,
                applyLoading = applyLoading,
                applyLabel = applyLabel,
                applyHint = applyHint,
                applyCountLoading = applyCountLoading,
                showZeroActions = showZeroActions,
                onZeroReset = onZeroReset,
                onZeroExpandRadius = onZeroExpandRadius,
            )
        }
    }
}

@Composable
private fun NearbyInlineLocationLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var animationInProgress by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.clickable {
            if (animationInProgress || scrollState.maxValue == 0) return@clickable
            scope.launch {
                animationInProgress = true
                scrollState.scrollTo(0)
                scrollState.animateScrollTo(scrollState.maxValue)
                delay(120)
                scrollState.animateScrollTo(0)
                animationInProgress = false
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState, enabled = false),
        )
    }
}

@Composable
private fun NearbySheetTopBar(
    title: String,
    onClose: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            onBack != null -> {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                }
            }
            onClose != null -> {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                }
            }
            else -> Spacer(modifier = Modifier.size(48.dp))
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
                Text("Сбросить")
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun NearbySheetRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NearbySheetStaticRow(
    title: String,
    summary: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NearbyBrandRow(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.primaryContainer else colors.surfaceVariant
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                count?.let {
                    Text(
                        text = "($it)",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyRadioRow(
    label: String,
    count: Int? = null,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                text = "($it)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NearbyCheckboxRow(
    label: String,
    count: Int? = null,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                text = "($it)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NearbySheetActionsRow(
    onApply: () -> Unit,
    onCancel: () -> Unit,
    applyEnabled: Boolean,
    applyLoading: Boolean,
    applyLabel: String,
    applyHint: String?,
    applyCountLoading: Boolean,
    showZeroActions: Boolean,
    onZeroReset: () -> Unit,
    onZeroExpandRadius: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        applyHint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showZeroActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InlineActionChip(label = "Сбросить фильтры", onClick = onZeroReset)
                InlineActionChip(label = "Расширить радиус", onClick = onZeroExpandRadius)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("Отмена")
            }
            Button(
                onClick = onApply,
                enabled = applyEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                if (applyLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(applyLabel)
                        if (applyCountLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun NearbyLocationHintCard(
    onAction: () -> Unit,
) {
    val secondaryButtonBackground = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
    val secondaryButtonContent = MaterialTheme.colorScheme.secondary
    SystemNoticeCard(
        body = "Включите геолокацию, чтобы видеть объявления рядом.",
        tone = SystemNoticeTone.Info,
        iconOverride = Icons.Outlined.Place,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
        bottomContent = {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = secondaryButtonBackground,
                    contentColor = secondaryButtonContent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp),
            ) {
                Text(
                    text = "Включить геолокацию",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        },
    )
}

@Composable
private fun FeedOffersRow(
    items: List<ExplainedItem>,
    savedOfferIds: Set<String>,
    hiddenOfferIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onOpenOffer: (ExplainedItem) -> Unit,
    nearbySort: NearbySort,
    nearbyActiveCount: Int,
    nearbyError: NearbyErrorState?,
    onRetry: () -> Unit,
    filtersSummary: String?,
    nearbyFilteredOutCount: Int?,
    onResetFilters: () -> Unit,
    onExpandRadius: () -> Unit,
) {
    val visibleItems = items.filterNot { hiddenOfferIds.contains(it.dto.id) }
    if (nearbyError != null) {
        NearbyErrorCard(
            message = nearbyError.message,
            onRetry = onRetry,
        )
        return
    }
    if (visibleItems.isEmpty()) {
        FlowMetrics.markNearbyFiltersEmptyShown(nearbyActiveCount)
        val subtitle = nearbyFilteredOutCount?.let { count ->
            "Найдено $count-объявлений , сбросьте фильтры чтобы увидеть"
        }
        EmptyNewOffersCard(
            title = "Пока нету объявлений по близости",
            subtitle = subtitle,
            showResetAction = nearbyActiveCount > 0,
            onResetFilters = {
                FlowMetrics.markNearbyFiltersEmptyCta("reset_filters")
                onResetFilters()
            },
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(visibleItems.take(8)) { item ->
                val ui = buildCompactCardUi(
                    item = item,
                    isSaved = savedOfferIds.contains(item.dto.id),
                    onToggleSave = { onToggleSave(item.dto.id) },
                    onOpenDetails = { onOpenOffer(item) },
                    sort = nearbySort,
                )
                CompactCard(
                    ui = ui,
                    modifier = Modifier.width(220.dp),
                    photoPeekEnabled = false,
                )
            }
        }
    }
}

@Composable
private fun FeedOffersColumn(
    items: List<ExplainedItem>,
    nearbyFetchLimit: Int,
    nearbyVisibleCount: Int,
    nearbyIsLoadingMore: Boolean,
    nearbySort: NearbySort,
    savedOfferIds: Set<String>,
    hiddenOfferIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onOpenOffer: (ExplainedItem) -> Unit,
    onOverflowAction: (OfferOverflowAction, ExplainedItem) -> Unit,
    nearbyActiveCount: Int,
    nearbyError: NearbyErrorState?,
    onRetry: () -> Unit,
    filtersSummary: String?,
    nearbyFilteredOutCount: Int?,
    onResetFilters: () -> Unit,
    onExpandRadius: () -> Unit,
    photoPeekEnabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val visibleItems = items.filterNot { hiddenOfferIds.contains(it.dto.id) }
    if (nearbyError != null) {
        NearbyErrorCard(
            message = nearbyError.message,
            onRetry = onRetry,
        )
        return
    }
    if (visibleItems.isEmpty()) {
        FlowMetrics.markNearbyFiltersEmptyShown(nearbyActiveCount)
        val subtitle = nearbyFilteredOutCount?.let { count ->
            "Найдено $count-объявлений , сбросьте фильтры чтобы увидеть"
        }
        EmptyNewOffersCard(
            title = "Пока нету объявлений по близости",
            subtitle = subtitle,
            showResetAction = nearbyActiveCount > 0,
            onResetFilters = {
                FlowMetrics.markNearbyFiltersEmptyCta("reset_filters")
                onResetFilters()
            },
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val visibleLimit = nearbyVisibleCount.coerceAtLeast(0)
            val visibleList = visibleItems.take(visibleLimit)
            visibleList.forEach { item ->
                val ui = buildOfferCardUi(
                    item = item,
                    isSaved = savedOfferIds.contains(item.dto.id),
                    onToggleSave = { onToggleSave(item.dto.id) },
                    onOpenDetails = { onOpenOffer(item) },
                    onOverflowAction = { action -> onOverflowAction(action, item) },
                    sort = nearbySort,
                )
                OfferCard(
                    ui = ui,
                    modifier = Modifier.fillMaxWidth(),
                    photoPeekEnabled = photoPeekEnabled,
                    overflowActions = CardActionRules.offerOverflowActions(
                        includeCopyLink = !item.dto.externalUrl.isNullOrBlank(),
                    ),
                )
            }
            val canRevealMore = visibleItems.size > visibleList.size
            val canFetchMore = visibleItems.size >= nearbyFetchLimit
            if (canRevealMore || canFetchMore) {
                Button(
                    onClick = onLoadMore,
                    enabled = !nearbyIsLoadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (nearbyIsLoadingMore) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp),
                        )
                        Text("Загружаем")
                    } else {
                        Text("Показать ещё")
                    }
                }
            }
        }
    }
}

private fun buildOfferCardUi(
    item: ExplainedItem,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDetails: () -> Unit,
    onOverflowAction: (OfferOverflowAction) -> Unit,
    sort: NearbySort,
): OfferCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Offer" }
    val badges = mapPositivePriceBadges(dto.sellerBadges)
    val secondaryLabel = when (sort) {
        NearbySort.Distance -> formatDistanceLabel(dto.distanceKm)
        NearbySort.Newest -> formatTimeOfDay(dto.updatedAt)
        else -> null
    }
    val locationLabel = secondaryLabel ?: formatLocationText(dto.sellerCity, dto.sellerCountry)
    val trust = when (sort) {
        NearbySort.Newest -> CardTrustData(
            updatedAtText = null,
            sourceText = dto.sourceName?.takeIf { it.isNotBlank() },
            ratingText = formatRatingText(dto.sellerRating),
        )
        else -> CardTrustData(
            updatedAtText = formatUpdatedAtText(dto.updatedAt),
            sourceText = dto.sourceName?.takeIf { it.isNotBlank() },
            ratingText = formatRatingText(dto.sellerRating),
        )
    }
    return OfferCardUi(
        id = dto.id,
        title = title,
        priceText = formatPriceText(dto.price),
        media = dto.imageUrls.map { url -> CardMediaItem(url = url, contentDescription = title) },
        photoCount = dto.imageUrls.size,
        locationText = locationLabel,
        isSaved = isSaved,
        badges = badges,
        trust = trust,
        onOpenDetails = onOpenDetails,
        onToggleSave = onToggleSave,
        onOverflowAction = onOverflowAction,
    )
}

private fun buildCompactCardUi(
    item: ExplainedItem,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDetails: () -> Unit,
    sort: NearbySort,
): CompactCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Offer" }
    val secondaryLabel = when (sort) {
        NearbySort.Distance -> formatDistanceLabel(dto.distanceKm)
        NearbySort.Newest -> formatTimeOfDay(dto.updatedAt)
        else -> null
    }
    val badge = secondaryLabel?.let { CardBadge(label = it) } ?: firstPositivePriceBadge(dto.sellerBadges)
    return CompactCardUi(
        id = dto.id,
        title = title,
        priceText = formatPriceText(dto.price),
        media = dto.imageUrls.map { url -> CardMediaItem(url = url, contentDescription = title) },
        photoCount = dto.imageUrls.size,
        badge = badge,
        isSaved = isSaved,
        showSave = false,
        onOpenDetails = onOpenDetails,
        onToggleSave = onToggleSave,
        onOverflowAction = null,
    )
}

private fun mapPositivePriceBadges(raw: List<String>): List<CardBadge> =
    raw.map(::normalizeBadgeLabel)
        .filter(::isPositivePriceBadge)
        .map { CardBadge(label = it) }

private fun firstPositivePriceBadge(raw: List<String>): CardBadge? =
    raw.asSequence()
        .map(::normalizeBadgeLabel)
        .firstOrNull(::isPositivePriceBadge)
        ?.let { CardBadge(label = it) }

private fun isPositivePriceBadge(label: String): Boolean {
    if (label.isBlank()) return false
    val lowered = label.lowercase(Locale.getDefault())
    val hasPriceContext = lowered.contains("цена") ||
        lowered.contains("цене") ||
        lowered.contains("цену") ||
        lowered.contains("стоим") ||
        lowered.contains("рынк") ||
        lowered.contains("price")
    if (!hasPriceContext) return false
    val hasPositive = lowered.contains("ниже") ||
        lowered.contains("дешев") ||
        lowered.contains("выгод") ||
        lowered.contains("скид") ||
        lowered.contains("лучш")
    if (!hasPositive) return false
    val hasNegative = lowered.contains("выше") || lowered.contains("дороже")
    return !hasNegative
}
@Composable
private fun MyOffersSection(
    counts: OfferCounts,
    offersPreviewItems: Map<UserOffersTab, List<UserOfferCardUi>>,
    selectedTab: UserOffersTab,
    onTabChange: (UserOffersTab) -> Unit,
    onOpenAll: () -> Unit,
    showCreateOfferCta: Boolean,
    onCreateOffer: () -> Unit,
    onOpenDraft: (String) -> Unit,
) {
    val items = offersPreviewItems[selectedTab].orEmpty()
    val previewItems = items.take(6)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NavSectionHeader(
            title = "Мои объявления",
            onClick = onOpenAll,
            trailingLabel = counts.total.takeIf { it > 0 }?.let { "Всего: $it" },
        )

        UserOffersTabs(
            items = listOf(
                UserOffersTabItem("Активные", counts.active, UserOffersTab.ACTIVE),
                UserOffersTabItem("Черновики", counts.drafts, UserOffersTab.DRAFTS),
                UserOffersTabItem("Завершённые", counts.completed, UserOffersTab.COMPLETED),
            ),
            selected = selectedTab,
            onSelect = onTabChange,
        )

        if (items.isEmpty()) {
            EmptyInlineCard(
                icon = Icons.Outlined.Add,
                title = "Пока нет объявлений",
                subtitle = "Создайте объявление за 10 секунд",
                actionLabel = if (showCreateOfferCta) "Создать за 10 секунд" else null,
                onAction = onCreateOffer,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(previewItems) { offer ->
                    OfferPreviewCard(
                        offer = offer,
                        modifier = Modifier.width(240.dp),
                        onContinueDraft = { onOpenDraft(offer.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyNewOffersCard(
    title: String,
    subtitle: String?,
    showResetAction: Boolean,
    onResetFilters: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showResetAction) {
                TextButton(
                    onClick = onResetFilters,
                    modifier = Modifier.align(Alignment.Start).heightIn(min = 36.dp),
                ) {
                    Text("Сбросить фильтры")
                }
            }
        }
    }
}

@Composable
private fun NearbyErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    SystemNoticeCard(
        body = "Не удалось обновить предложения рядом. $message",
        tone = SystemNoticeTone.Error,
        compact = true,
        actionLabel = "Повторить",
        onAction = {
            FlowMetrics.markNearbyFiltersErrorCta("retry")
            onRetry()
        },
    )
}

@Composable
private fun NearbyErrorInline(
    message: String,
    onRetry: () -> Unit,
) {
    SystemNoticeCard(
        body = message,
        tone = SystemNoticeTone.Error,
        compact = true,
        actionLabel = "Повторить",
        onAction = {
            FlowMetrics.markNearbyFiltersErrorCta("retry")
            onRetry()
        },
    )
}

@Composable
private fun NearbyPermissionBanner(
    onRequestPermission: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onPickCity: () -> Unit,
) {
    SystemNoticeCard(
        body = "Используем город из профиля, пока вы не разрешите доступ к геолокации или не выберете город вручную.",
        tone = SystemNoticeTone.Info,
        compact = true,
        bottomContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val primaryLabel = if (onOpenSettings != null) "Открыть настройки" else "Разрешить доступ"
                val primaryAction = onOpenSettings ?: onRequestPermission
                InlineActionChip(label = primaryLabel, onClick = primaryAction)
                InlineActionChip(label = "Выбрать город", onClick = onPickCity)
            }
        },
    )
}

@Composable
private fun InlineActionChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun buildNearbySummary(
    filters: NearbyFiltersState,
    categories: List<CategoryChipUi>,
    userLocation: String?,
    userCountry: String?,
): String? {
    val parts = mutableListOf<String>()
    if (filters.locationIsActive()) {
        parts += filters.locationLabel(userLocation, userCountry)
    }
    if (hasActiveCategories(categories)) {
        parts += categorySummary(categories)
    }
    if (filters.brandsIsActive()) {
        parts += filters.brandsSummary()
    }
    if (filters.priceIsActive()) parts += filters.priceSummary()
    if (filters.conditionIsActive()) parts += filters.conditionSummary()
    if (filters.deliveryIsActive()) parts += filters.deliverySummary()
    if (filters.postedAtIsActive()) parts += filters.postedAtSummary()
    if (filters.sortIsActive()) parts += filters.sortSummary()
    return parts.joinToString(" · ").ifBlank { null }
}

private data class NearbyLocationSnapshot(
    val city: String?,
    val addressLine: String?,
    val countryCode: String?,
    val lat: Double?,
    val lon: Double?,
)

@SuppressLint("MissingPermission")
private suspend fun resolveCurrentLocation(context: Context): NearbyLocationSnapshot {
    return withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext NearbyLocationSnapshot(null, null, null, null, null)

        val providers = runCatching { locationManager.getProviders(true) }
            .getOrDefault(emptyList())
            .ifEmpty { listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) }

        val currentLocation: Location? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            providers.firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(2_500L) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            locationManager.getCurrentLocation(
                                provider,
                                null,
                                ContextCompat.getMainExecutor(context),
                            ) { location ->
                                if (cont.isActive) cont.resume(location)
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            }
        } else {
            null
        }

        val lastLocation: Location? = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { location ->
                val ageMs = System.currentTimeMillis() - location.time
                ageMs in 0..(2 * 60 * 1000L)
            }

        val resolvedLocation = currentLocation ?: lastLocation
            ?: return@withContext NearbyLocationSnapshot(null, null, null, null, null)

        val geocoder = Geocoder(context, Locale.getDefault())
        val address = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        resolvedLocation.latitude,
                        resolvedLocation.longitude,
                        1,
                    ) { addresses ->
                        if (cont.isActive) {
                            cont.resume(addresses.firstOrNull())
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(resolvedLocation.latitude, resolvedLocation.longitude, 1)?.firstOrNull()
            }
        }.getOrNull()
        val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        val street = address?.thoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val house = address?.subThoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val addressLine = listOfNotNull(street, house).joinToString(" ").ifBlank {
            address?.featureName?.trim()?.takeIf { it.isNotBlank() }
        }
        val countryCode = address?.countryCode
        if (isLikelyEmulatorPlaceholderLocation(resolvedLocation.latitude, resolvedLocation.longitude, city, addressLine)) {
            return@withContext NearbyLocationSnapshot(null, null, null, null, null)
        }
        NearbyLocationSnapshot(
            city = city,
            addressLine = addressLine,
            countryCode = countryCode,
            lat = resolvedLocation.latitude,
            lon = resolvedLocation.longitude,
        )
    }
}

private fun isLikelyEmulatorPlaceholderLocation(
    lat: Double,
    lon: Double,
    city: String?,
    addressLine: String?,
): Boolean {
    val isEmulator =
        Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
    if (!isEmulator) return false

    val nearGoogleplex = abs(lat - 37.4219983) <= 0.002 && abs(lon - (-122.084)) <= 0.002
    val label = listOfNotNull(city, addressLine).joinToString(" ").lowercase(Locale.ROOT)
    val hasGoogleplexText = label.contains("mountain view") || label.contains("amphitheatre")
    return nearGoogleplex && hasGoogleplexText
}

private data class ResolvedVisualCategoryHint(
    val chip: CategoryChipUi,
    val promote: Boolean,
)

private suspend fun buildVisualSearchInsight(
    analysis: VisualSearchCaptureAnalysis?,
    routeQueryTask: RouteQueryTask,
    getBrowseNodeTask: GetBrowseNodeTask,
    viewModel: MainPageViewModel,
): VisualSearchInsightUi? {
    if (analysis == null) return null
    val categoryHint = resolveVisualSearchCategoryHint(
        analysis = analysis,
        routeQueryTask = routeQueryTask,
        getBrowseNodeTask = getBrowseNodeTask,
        viewModel = viewModel,
    )
    val title = preferredVisualSearchInsightTitle(analysis = analysis, categoryHint = categoryHint)
    val subtitle = buildString {
        when (analysis.captureMode) {
            VisualSearchCaptureMode.BARCODE -> {
                append("Считали штрихкод и сначала покажем самую точную выдачу.")
            }
            VisualSearchCaptureMode.OCR -> {
                analysis.textHints.firstOrNull()?.let { text ->
                    append("Нашли текст в кадре и покажем товары по нему.")
                }
            }
            VisualSearchCaptureMode.IMAGE -> {
                append("Сначала покажем наиболее вероятные товары по фото.")
            }
        }
        val categoryTitle = categoryHint?.chip?.title
        if (!categoryTitle.isNullOrBlank() && !sameVisualLabel(categoryTitle, title)) {
            if (isNotEmpty()) append("  ·  ")
            append("Раздел: $categoryTitle")
        }
    }.ifBlank {
        when (analysis.captureMode) {
            VisualSearchCaptureMode.BARCODE -> "Попробуем сразу открыть точную выдачу."
            VisualSearchCaptureMode.OCR -> "Сначала покажем товары по найденному тексту."
            VisualSearchCaptureMode.IMAGE -> "Сначала покажем наиболее вероятные товары по фото."
        }
    }
    val hintLabels = buildList {
        if (!analysis.barcodeValue.isNullOrBlank()) add("Штрихкод")
        if (analysis.textHints.isNotEmpty()) add("Текст")
        analysis.objectLabel
            ?.takeIf { label -> label.isNotBlank() && !sameVisualLabel(label, title) }
            ?.let { add(it) }
        categoryHint?.chip?.title
            ?.takeIf { text -> text.isNotBlank() && !sameVisualLabel(text, title) }
            ?.let { add(it) }
    }.distinct().take(3)
    return VisualSearchInsightUi(
        title = title,
        subtitle = subtitle,
        suggestedCategoryCode = categoryHint?.chip?.code,
        suggestedCategoryTitle = categoryHint?.chip?.title,
        promoteSuggestedCategory = categoryHint?.promote == true,
        suggestedRegion = analysis.suggestedRegion,
        barcodeValue = analysis.barcodeValue,
        recognizedText = analysis.recognizedText,
        imageLabelHints = analysis.imageLabels,
        objectLabel = analysis.objectLabel,
        objectConfidence = analysis.objectConfidence,
        hintLabels = hintLabels,
    )
}

private fun preferredVisualSearchInsightTitle(
    analysis: VisualSearchCaptureAnalysis,
    categoryHint: ResolvedVisualCategoryHint?,
): String = when (analysis.captureMode) {
    VisualSearchCaptureMode.BARCODE ->
        categoryHint?.chip?.title
            ?.takeIf { it.isNotBlank() }
            ?: "Товар по штрихкоду"
    VisualSearchCaptureMode.OCR ->
        bestVisualSearchHint(analysis)
            ?: analysis.imageLabels.firstOrNull()
            ?: categoryHint?.chip?.title
            ?: "Товар по фото"
    VisualSearchCaptureMode.IMAGE ->
        bestVisualSearchHint(analysis)
            ?: categoryHint?.chip?.title
            ?: "Товар по фото"
}

private fun sameVisualLabel(left: String?, right: String?): Boolean =
    left?.trim()?.lowercase(Locale.ROOT) == right?.trim()?.lowercase(Locale.ROOT)

private fun bestVisualSearchHint(
    analysis: VisualSearchCaptureAnalysis,
): String? = buildList {
    analysis.objectLabel?.let(::add)
    addAll(analysis.queryHints)
    addAll(analysis.imageLabels)
    addAll(analysis.textHints)
}
    .map { candidate -> candidate.trim() }
    .filter { candidate -> candidate.isNotEmpty() }
    .distinct()
    .map(::humanizeVisualSearchHint)
    .filter { candidate -> !isWeakVisualInsight(candidate) }
    .maxByOrNull(::visualInsightScore)

private fun humanizeVisualSearchHint(raw: String): String {
    val normalized = raw.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "mouse",
        "computer mouse",
            -> "Компьютерная мышь"
        "wireless mouse" -> "Беспроводная мышь"
        "keyboard" -> "Клавиатура"
        "laptop",
        "laptop computer",
            -> "Ноутбук"
        "smartphone",
        "cell phone",
            -> "Смартфон"
        "tv",
        "television",
            -> "Телевизор"
        "computer monitor",
        "monitor",
            -> "Монитор"
        "headphones",
        "earphones",
            -> "Наушники"
        else -> raw.trim().replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
        }
    }
}

private fun visualInsightScore(raw: String): Int {
    val normalized = raw.trim().lowercase(Locale.ROOT)
    var score = 0
    if (BrandModelRules.fromKnownFamily(raw) != null) score += 10
    if (normalized in strongVisualInsightPhrases) score += 8
    if (looksLikeProductModelInsight(normalized)) score += 5
    strongVisualInsightTokens.forEach { token ->
        if (normalized.contains(token)) score += 4
    }
    weakVisualInsightTokens.forEach { token ->
        if (normalized.contains(token)) score -= 6
    }
    return score
}

private fun isWeakVisualInsight(raw: String): Boolean {
    val normalized = raw.trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return true
    if (normalized.length < 3) return true
    if (normalized in weakVisualInsightPhrases) return true
    return visualInsightScore(normalized) <= 0
}

private fun looksLikeProductModelInsight(normalized: String): Boolean {
    val tokens = normalized
        .split(' ')
        .map { token -> token.trim() }
        .filter { token -> token.isNotEmpty() }
    if (tokens.isEmpty() || tokens.size > 2) return false
    if (tokens.any { token -> token.length == 1 }) return false
    val hasMixedAlphaNumericToken = tokens.any { token ->
        token.any { ch -> ch.isLetter() } &&
            token.any { ch -> ch.isDigit() } &&
            token.length in 4..18
    }
    val compactLength = tokens.joinToString("").length
    return hasMixedAlphaNumericToken && compactLength in 5..24
}

private suspend fun resolveVisualSearchCategoryHint(
    analysis: VisualSearchCaptureAnalysis,
    routeQueryTask: RouteQueryTask,
    getBrowseNodeTask: GetBrowseNodeTask,
    viewModel: MainPageViewModel,
): ResolvedVisualCategoryHint? {
    val queryHints = analysis.queryHints
        .map { hint -> hint.trim() }
        .filter { hint -> hint.length >= 3 && !isWeakVisualInsight(hint) }
        .distinct()
    queryHints.forEach { hint ->
        val routed = runCatching { routeQueryTask(hint) }.getOrNull() ?: return@forEach
        val candidateCode = when (routed.routeType) {
            QueryRouteType.OPEN_CATEGORY -> routed.primaryTargetCode
            QueryRouteType.OPEN_BROWSE -> routed.primaryTargetCode
                ?.let { browseCode -> getBrowseNodeTask(browseCode) }
                ?.takeIf { node -> node.targetType == BrowseTargetType.CATEGORY }
                ?.targetCategoryCode
            QueryRouteType.RUN_SEARCH -> null
        }
            ?.let { rawCode -> viewModel.resolveCategoryRedirect(rawCode) ?: rawCode }
            ?.trim()
            ?.takeIf { code -> code.isNotEmpty() }
            ?: return@forEach
        val confidence = routed.confidence
        val title = viewModel.visualSearchCategoryTitle(candidateCode) ?: hint
        return ResolvedVisualCategoryHint(
            chip = CategoryChipUi(code = candidateCode, title = title),
            promote = shouldPromoteResolvedVisualCategoryHint(
                analysis = analysis,
                hint = hint,
                confidence = confidence,
            ),
        )
    }
    val textFallback = analysis.recognizedText
        ?.trim()
        ?.takeIf { text -> text.isNotEmpty() }
        ?: return null
    val categoryCode = viewModel.inferLeafCategoryByFacets(textFallback) ?: return null
    val title = viewModel.visualSearchCategoryTitle(categoryCode) ?: textFallback.take(48)
    return ResolvedVisualCategoryHint(
        chip = CategoryChipUi(code = categoryCode, title = title),
        promote = true,
    )
}

private fun shouldPromoteResolvedVisualCategoryHint(
    analysis: VisualSearchCaptureAnalysis,
    hint: String,
    confidence: Double,
): Boolean {
    if (analysis.captureMode == VisualSearchCaptureMode.OCR) return true
    if (confidence >= 0.78) return true
    val normalizedHint = humanizeVisualSearchHint(hint)
    return confidence >= 0.58 && visualInsightScore(normalizedHint) >= 8
}

private val strongVisualInsightPhrases = setOf(
    "компьютерная мышь",
    "беспроводная мышь",
    "мышь",
    "клавиатура",
    "смартфон",
    "ноутбук",
    "монитор",
    "телевизор",
    "наушники",
    "computer mouse",
    "wireless mouse",
    "mouse",
    "keyboard",
    "smartphone",
    "laptop",
    "monitor",
    "tv",
)

private val strongVisualInsightTokens = setOf(
    "мыш",
    "mouse",
    "клавиат",
    "keyboard",
    "смартфон",
    "smartphone",
    "ноутбук",
    "laptop",
    "монитор",
    "monitor",
    "телевиз",
    "tv",
    "науш",
    "headphone",
    "barcode",
    "model",
)

private val weakVisualInsightPhrases = setOf(
    "tableware",
    "cutlery",
    "dishware",
    "kitchenware",
    "serveware",
    "flatware",
    "silverware",
    "utensil",
    "utensils",
    "wall",
    "room",
    "interior",
    "home",
    "property",
    "floor",
    "ceiling",
    "screen",
    "display",
    "object",
)

private val weakVisualInsightTokens = setOf(
    "tableware",
    "cutlery",
    "dishware",
    "kitchenware",
    "serveware",
    "flatware",
    "silverware",
    "utensil",
    "interior",
    "room",
    "wall",
    "floor",
    "ceiling",
    "property",
    "home",
)

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ActivityCenterSection(
    activitySummary: ActivitySummary,
    messagePreview: MessageThreadPreview?,
    notificationPreview: NotificationPreview?,
    onOpenSubs: () -> Unit,
    onOpenNotifs: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    val hasNotifications = activitySummary.notificationsUnread > 0 && notificationPreview != null
    val hasMessages = activitySummary.messagesUnread > 0 && messagePreview != null
    val hasSubscriptions = activitySummary.triggeredSubscriptions > 0

    if (!hasNotifications && !hasMessages && !hasSubscriptions) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Центр активности",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hasNotifications) {
                notificationPreview.let { preview ->
                    ActivitySignalCard(
                        icon = Icons.Outlined.NotificationsNone,
                        title = preview.message,
                        subtitle = preview.details,
                        meta = preview.timeLabel,
                        badgeCount = activitySummary.notificationsUnread,
                        onClick = onOpenNotifs,
                    )
                }
            }
            if (hasMessages) {
                messagePreview?.let { preview ->
                    MessageThreadCard(
                        thread = preview,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenMessages,
                    )
                }
            }
            if (hasSubscriptions) {
                ActivitySignalCard(
                    icon = Icons.Outlined.StarOutline,
                    title = "Сработало отслеживание",
                    subtitle = "Активных: ${activitySummary.activeSubscriptions} · Сработало: ${activitySummary.triggeredSubscriptions}",
                    meta = null,
                    badgeCount = null,
                    onClick = onOpenSubs,
                )
            }
        }
    }
}


@Composable
private fun TrackedItemsSignalsSection(
    trackedItemsState: TrackedItemsState,
    onOpenAll: () -> Unit,
    onOpenTrack: (String) -> Unit,
    onCreateTrack: () -> Unit,
    onRetry: () -> Unit,
    isGuest: Boolean,
    onLoginClick: () -> Unit,
) {
    val emptyTitle = "Пока нет новых событий"
    val emptySubtitle = "Отслеживания сообщат, когда появится выгодное"
    val headerAction = if (isGuest) onLoginClick else onOpenAll

    Column {
        SectionHeader(
            title = "Новое по отслеживаниям",
            trailingActionLabel = "Показать все",
            onTrailingAction = headerAction,
            topPadding = 24.dp,
            startPadding = 0.dp,
            endPadding = 0.dp,
        )
        if (isGuest) {
            EmptyInlineCard(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.hub_subscriptions_need_auth_title),
                subtitle = stringResource(R.string.hub_subscriptions_need_auth_body),
                actionLabel = stringResource(R.string.profile_unauth_login),
                onAction = onLoginClick,
            )
            return
        }
        when (val loadState = trackedItemsState.tracks) {
            TrackedItemsLoadState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    ScenarioCardSkeleton(modifier = Modifier.fillMaxWidth())
                }
            }
            TrackedItemsLoadState.Empty -> {
                EmptyInlineCard(
                    icon = Icons.Outlined.StarOutline,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    actionLabel = "Создать отслеживание",
                    onAction = onCreateTrack,
                )
            }
            is TrackedItemsLoadState.Error -> {
                EmptyInlineCard(
                    icon = Icons.Outlined.NotificationsNone,
                    title = stringResource(R.string.state_offline_no_cache_title),
                    subtitle = stringResource(R.string.state_offline_no_cache_body),
                    actionLabel = stringResource(R.string.state_offline_no_cache_action),
                    onAction = onRetry,
                )
            }
            is TrackedItemsLoadState.Content -> {
                val items = loadState.items
                    .filter { it.stats.newEventsCount > 0 }
                    .sortedByDescending { it.stats.lastEventAt ?: 0L }
                if (items.isEmpty()) {
                    EmptyInlineCard(
                        icon = Icons.Outlined.StarOutline,
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        actionLabel = "Создать отслеживание",
                        onAction = onCreateTrack,
                    )
                } else {
                    val preview = items.take(4)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        preview.forEach { track ->
                            TrackedItemSignalCard(track = track, onClick = { onOpenTrack(track.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackedItemSignalCard(
    track: com.example.shoppingassistant.domain.tracks.Track,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val updatedAt = track.stats.lastEventAt?.let { formatUpdatedAtText(it) }
    val details = "Новых событий: ${track.stats.newEventsCount}"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            updatedAt?.let {
                Text(
                    text = "Обновлено $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActivitySignalCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    meta: String?,
    badgeCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    meta?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badgeCount != null && badgeCount > 0) {
                val label = if (badgeCount > 99) "99+" else badgeCount.toString()
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private data class UnderlineTabItem<T>(
    val label: String,
    val count: Int,
    val value: T,
)

@Composable
private fun <T> UnderlineTabs(
    items: List<UnderlineTabItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            val isSelected = item.value == selected
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(item.value) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${item.label} ${item.count}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(32.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun NotificationsSection(
    hasUnread: Boolean,
    onOpenAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NavSectionHeader(
            title = "Уведомления",
            onClick = onOpenAll,
            trailingLabel = null,
            showDot = hasUnread,
        )
        EmptyInlineCard(
            icon = Icons.Outlined.NotificationsNone,
            title = "Нет новых уведомлений",
            subtitle = "Здесь появятся важные события",
            actionLabel = null,
            onAction = {},
        )
    }
}

@Composable
private fun MessagesSection(
    onOpenAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NavSectionHeader(
            title = "Сообщения",
            onClick = onOpenAll,
            trailingLabel = null,
        )
        EmptyInlineCard(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = "Нет новых сообщений",
            subtitle = "Диалоги появятся после откликов",
            actionLabel = null,
            onAction = {},
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailingActionLabel: String?,
    onTrailingAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    topPadding: Dp = 24.dp,
    bottomPadding: Dp = 12.dp,
    startPadding: Dp = 16.dp,
    endPadding: Dp = 16.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding, top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().weight(1f),
            )
            if (!trailingActionLabel.isNullOrBlank() && onTrailingAction != null) {
                Text(
                    text = trailingActionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .alignByBaseline()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onTrailingAction)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NavSectionHeader(
    title: String,
    onClick: () -> Unit,
    trailingLabel: String?,
    showDot: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailingLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun OfferPreviewCard(
    offer: UserOfferCardUi,
    modifier: Modifier = Modifier,
    onContinueDraft: (() -> Unit)? = null,
) {
    val price = offer.priceMajor?.let { "${"%.0f".format(it)} ${offer.currency}" }
    val statusLabel = when (offer.status) {
        UserOfferStatus.DRAFT -> "Черновик"
        UserOfferStatus.ACTIVE -> "Активно"
        UserOfferStatus.PAUSED -> "Пауза"
        UserOfferStatus.FINISHED -> "Завершено"
        UserOfferStatus.ARCHIVED -> "Архив"
    }
    val statusColors = when (offer.status) {
        UserOfferStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        UserOfferStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        UserOfferStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        UserOfferStatus.FINISHED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        UserOfferStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val timeLabel = formatOfferTime(offer)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .heightIn(min = 120.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OfferPreviewCover(coverUrl = offer.coverUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = offer.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (price != null) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusChip(
                        label = statusLabel,
                        background = statusColors.first,
                        contentColor = statusColors.second,
                    )
                    timeLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (offer.status == UserOfferStatus.DRAFT) {
                    OfferDraftRow(
                        progressPercent = draftProgressPercent(offer),
                        onContinue = onContinueDraft,
                    )
                } else {
                    OfferMetricsRow(
                        views = offer.viewsCount,
                        favorites = offer.favoritesCount,
                        messages = offer.messagesCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferPreviewCover(
    coverUrl: String?,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(72.dp),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfferMetricsRow(
    views: Int?,
    favorites: Int?,
    messages: Int?,
) {
    val viewsLabel = views ?: 0
    val favoritesLabel = favorites ?: 0
    val messagesLabel = messages ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricItem(icon = Icons.Outlined.Visibility, value = viewsLabel)
        MetricItem(icon = Icons.Outlined.FavoriteBorder, value = favoritesLabel)
        MetricItem(icon = Icons.Outlined.ChatBubbleOutline, value = messagesLabel)
    }
}

@Composable
private fun OfferDraftRow(
    progressPercent: Int,
    onContinue: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Готово на $progressPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { onContinue?.invoke() },
            enabled = onContinue != null,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text("Продолжить", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun draftProgressPercent(offer: UserOfferCardUi): Int {
    var filled = 1
    if (!offer.category.isNullOrBlank()) filled += 1
    if (offer.priceMajor != null) filled += 1
    if (!offer.coverUrl.isNullOrBlank()) filled += 1
    val total = 4
    val percent = (filled * 100) / total
    return percent.coerceIn(25, 100)
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    background: Color,
    contentColor: Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatOfferTime(offer: UserOfferCardUi): String? {
    val updated = offer.updatedAtMillis
    val published = offer.publishedAtMillis
    val ts = updated ?: published ?: return null
    val prefix = if (updated != null) "Обновлено" else "Опубликовано"
    return "$prefix ${formatShortDate(ts)}"
}

private fun formatMessageTime(timestamp: Long?): String {
    if (timestamp == null) return "сейчас"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minuteMs = 60_000L
    val hourMs = 3_600_000L
    val dayMs = 86_400_000L
    return when {
        diff < hourMs -> "${max(1, (diff / minuteMs).toInt())} мин"
        diff < dayMs -> "${max(1, (diff / hourMs).toInt())} ч"
        else -> formatShortDate(timestamp)
    }
}

private fun formatShortDate(timestamp: Long): String {
    val df = SimpleDateFormat("dd.MM", Locale.getDefault())
    return df.format(Date(timestamp))
}

@Composable
private fun EmptyInlineCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val cardBackground = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
    val secondaryButtonBackground = MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
    val secondaryButtonContent = MaterialTheme.colorScheme.secondary
    val message = remember(title, subtitle) {
        buildString {
            append(title.trim())
            subtitle.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { details ->
                    if (isNotEmpty()) append(". ")
                    append(details)
                }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackground),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (!actionLabel.isNullOrBlank()) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = secondaryButtonBackground,
                    contentColor = secondaryButtonContent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 0.dp)
                    .heightIn(min = 38.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SelectedCategoriesSheet(
    title: String,
    items: List<CategoryChipUi>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Закрыть",
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { chip ->
                    CategoryChip(label = chip.title, highlighted = false, onClick = {})
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFabMenuSheet(
    isGuest: Boolean,
    onPhoto: () -> Unit,
    onLink: () -> Unit,
    onManual: () -> Unit,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.hub_create_menu_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.hub_create_menu_close))
                }
            }

            if (isGuest) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.hub_create_menu_guest_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onLoginClick) {
                            Text(stringResource(R.string.profile_unauth_login))
                        }
                    }
                }
            }

            CreateMenuItem(
                icon = Icons.Outlined.CameraAlt,
                label = stringResource(R.string.hub_create_menu_photo),
                onClick = onPhoto,
            )
            CreateMenuItem(
                icon = Icons.Outlined.Link,
                label = stringResource(R.string.hub_create_menu_link),
                onClick = onLink,
            )
            CreateMenuItem(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.hub_create_menu_manual),
                onClick = onManual,
            )
        }
    }
}

@Composable
private fun CreateMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}
@Composable
private fun LinkInputOverlay(
    input: LinkInputState,
    error: String?,
    supportedSourcesHint: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Вставить ссылку",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextField(
                    value = input.url,
                    onValueChange = onChange,
                    singleLine = true,
                    maxLines = 1,
                    placeholder = { Text("https://", maxLines = 1) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                )
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = supportedSourcesHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = input.isValid,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Продолжить", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
