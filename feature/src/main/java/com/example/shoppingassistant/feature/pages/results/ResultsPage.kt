package com.example.shoppingassistant.feature.pages.results

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.shoppingassistant.feature.ui.cards.CardActionRules
import com.example.shoppingassistant.feature.ui.cards.CardBadge
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
import com.example.shoppingassistant.core.data.nearby.DEFAULT_NEARBY_RADIUS_KM
import com.example.shoppingassistant.core.data.nearby.NEARBY_RADIUS_PRESETS
import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.ui.LocalProfileSettings
import com.example.shoppingassistant.core.usecase.SearchOffersUseCase
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.navigation.AppRoutes
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
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.GetFacetCollectionTask
import com.example.shoppingassistant.domain.facet.GetFacetPresetTask
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get as koinGet
import java.util.Locale

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
    val searchOffers: SearchOffersUseCase = remember { koinGet(SearchOffersUseCase::class.java) }
    val catalogRepository: CatalogRepository = remember { koinGet(CatalogRepository::class.java) }
    val trackRepository: TrackRepository = remember { koinGet(TrackRepository::class.java) }
    val getFacetCollectionTask: GetFacetCollectionTask = remember { koinGet(GetFacetCollectionTask::class.java) }
    val getFacetPresetTask: GetFacetPresetTask = remember { koinGet(GetFacetPresetTask::class.java) }
    val profileSettings = LocalProfileSettings.current

    var filters by remember { mutableStateOf(initialFilterState(payload)) }
    var workingFilters by remember { mutableStateOf(filters) }
    var sheetScreen by remember { mutableStateOf<ResultsSheet?>(null) }
    var sheetTarget by remember { mutableStateOf(FilterSheetTarget.Applied) }
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    var brandQuery by rememberSaveable { mutableStateOf("") }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var categoryTreePath by remember { mutableStateOf<List<String>>(emptyList()) }
    val hiddenIds = remember { mutableStateListOf<String>() }
    var savedOfferIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        FlowMetrics.markResultsOpened()
    }

    LaunchedEffect(Unit) {
        categories = runCatching { catalogRepository.listCategories() }
            .getOrElse { emptyList() }
    }

    LaunchedEffect(payload.facetCollectionCode, payload.facetPresetCode, payload.categoryCode) {
        val collectionCode = payload.facetCollectionCode?.trim()?.takeIf { it.isNotEmpty() }
        val collection = collectionCode?.let { code ->
            runCatching { getFacetCollectionTask(code) }.getOrNull()
        }
        val presetCode = payload.facetPresetCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: collection?.presetCode?.trim()?.takeIf { it.isNotEmpty() }
        val preset = presetCode?.let { code ->
            runCatching { getFacetPresetTask(code) }.getOrNull()
        }
        val next = applyFacetPreset(base = filters, collection = collection, preset = preset)
        if (next != filters) {
            filters = next
            workingFilters = next
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

    val criteria = remember(filters) {
        buildCriteria(filters)
    }

    var items by remember(criteria) { mutableStateOf<List<ExplainedItem>>(emptyList()) }
    var isLoading by remember(criteria) { mutableStateOf(true) }
    var isLoadingMore by remember(criteria) { mutableStateOf(false) }
    var appendErrorMessage by remember(criteria) { mutableStateOf<String?>(null) }
    var currentLimit by remember(criteria) { mutableStateOf(20) }
    var errorMessage by remember(criteria) { mutableStateOf<String?>(null) }

    LaunchedEffect(criteria) {
        if (criteria == null) {
            items = emptyList()
            isLoading = false
            errorMessage = null
            appendErrorMessage = null
            return@LaunchedEffect
        }
        currentLimit = 20
        isLoading = true
        errorMessage = null
        appendErrorMessage = null
        val result = runCatching { searchOffers(criteria.copy(limit = currentLimit)) }
        items = result.getOrElse { emptyList() }
        if (result.isFailure) {
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

    val canLoadMore = !isLoading &&
        !isLoadingMore &&
        items.size >= currentLimit
    val showLoadMore = items.isNotEmpty() && (canLoadMore || isLoadingMore || appendErrorMessage != null)

    val loadMore: () -> Unit = loadMore@{
        if (!canLoadMore || criteria == null) return@loadMore
        val nextLimit = currentLimit + 20
        isLoadingMore = true
        errorMessage = null
        appendErrorMessage = null
        scope.launch {
            val result = runCatching { searchOffers(criteria.copy(limit = nextLimit)) }
            items = result.getOrElse { emptyList() }
            if (result.isSuccess) {
                currentLimit = nextLimit
            } else {
                appendErrorMessage = "Не удалось загрузить ещё предложения"
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
            val result = runCatching { searchOffers(criteria.copy(limit = currentLimit)) }
            items = result.getOrElse { emptyList() }
            if (result.isFailure) {
                errorMessage = "Не удалось загрузить предложения"
            }
            isLoading = false
        }
    }

    fun openExternal(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val canOpen = intent.resolveActivity(context.packageManager) != null
            if (canOpen) {
                context.startActivity(intent)
            } else {
                Toast.makeText(
                    context,
                    "Не найдено приложение для открытия ссылки",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.onFailure {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    fun openChatFor(item: ExplainedItem) {
        val sellerLabel = item.dto.sellerName ?: item.dto.brand ?: item.dto.model ?: "Продавец"
        val route = buildString {
            append("chat?")
            append("offerId=${Uri.encode(item.dto.id)}")
            append("&sellerName=${Uri.encode(sellerLabel)}")
            append("&offerTitle=${Uri.encode(item.dto.title)}")
            append("&price=${Uri.encode(item.dto.price?.toString() ?: "")}")
            append("&status=")
        }
        navController?.navigate(route)
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

    fun handleOverflow(action: OfferOverflowAction, item: ExplainedItem) {
        val url = item.dto.externalUrl
        when (action) {
            OfferOverflowAction.Share -> {
                if (!url.isNullOrBlank()) shareExternal(url)
            }
            OfferOverflowAction.Hide -> hiddenIds.add(item.dto.id)
            OfferOverflowAction.Report -> {
                Toast.makeText(context, "Thanks, we will review this offer.", Toast.LENGTH_SHORT).show()
            }
            OfferOverflowAction.CopyLink -> {
                if (!url.isNullOrBlank()) copyLink(url)
            }
        }
    }

    fun handleTrack() {
        FlowMetrics.markTrackClicked("results")
        val queryText = filters.queryText.trim().ifBlank { buildRecognitionTitle(filters).trim() }
        val categoryText = filters.categoryPath.joinToString(" → ").trim().takeIf { it.isNotBlank() }
            ?: filters.categoryCode?.trim()?.takeIf { it.isNotBlank() }
        val effectiveQuery = queryText.ifBlank { categoryText.orEmpty() }.trim()

        if (effectiveQuery.isBlank()) {
            Toast.makeText(context, "Введите запрос или выберите категорию", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val existing = runCatching { trackRepository.listTracks() }
                .getOrNull()
                ?.firstOrNull { it.type == TrackType.SEARCH && it.target.query == effectiveQuery }

            val trackId = if (existing != null) {
                Toast.makeText(context, "Отслеживание уже активно", Toast.LENGTH_SHORT).show()
                existing.id
            } else {
                val now = System.currentTimeMillis()
                val created = runCatching {
                    trackRepository.upsertTrack(
                        Track(
                            id = "new",
                            title = buildQuerySummary(filters, defaultLabel = effectiveQuery).take(80),
                            categoryCode = filters.categoryCode,
                            type = TrackType.SEARCH,
                            target = TrackTarget(query = effectiveQuery),
                            filters = TrackFilters(),
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

            navController?.navigate(AppRoutes.trackedItemsTop10(trackId))
        }
    }

    fun openFiltersHub() {
        workingFilters = filters
        sheetTarget = FilterSheetTarget.Draft
        sheetScreen = ResultsSheet.Filters
    }

    fun openCategoryShortcut() {
        workingFilters = filters
        categoryQuery = ""
        categoryTreePath = buildCategoryPath(filters.categoryCode, categoriesByCode)
        sheetTarget = FilterSheetTarget.Applied
        sheetScreen = ResultsSheet.Categories
    }

    LaunchedEffect(payload.mode) {
        if (payload.mode == ResultsMode.Categories) {
            openCategoryShortcut()
        }
    }

    fun openSortSheet() {
        workingFilters = filters
        sheetTarget = FilterSheetTarget.Applied
        sheetScreen = ResultsSheet.Sort
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

    val backAction = onBack ?: navController?.let { { it.popBackStack(); Unit } }
    val activeFiltersCount = filters.activeFilterCount()
    val categorySummary = filters.categorySummary()
    val brandSummary = filters.brandSummary()
    val priceSummary = filters.priceSummary()
    val recognitionTitle = buildRecognitionTitle(filters)
    val recognitionDetails = buildRecognitionDetails(filters)
    val showRecognitionCard = recognitionTitle.isNotBlank() ||
        recognitionDetails.isNotBlank() ||
        filters.categoryCode != null
    val shouldShowEmptyState = !isLoading && errorMessage == null && visibleItems.isEmpty()
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
            onAction = { filters = filters.resetNonQuery() },
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
            onFilters = { openFiltersHub() },
            onCategories = { openCategoryShortcut() },
            onSort = { openSortSheet() },
        )

        ResultsSummaryRow(
            category = categorySummary,
            brand = brandSummary,
            price = priceSummary,
        )

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
            onCreate = { payload.onCreate(navController, onOpenDraft, filters.query, filters.queryText, filters.categoryCode) },
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
            },
        ) { paddingValues ->
            LazyColumn(
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
                items(visibleItems, key = { it.dto.id }) { item ->
                    val ui = buildOfferCardUi(
                        item = item,
                        isSaved = savedOfferIds.contains(item.dto.id),
                        onToggleSave = { toggleSavedOffer(item.dto.id) },
                        onOpenDetails = { openChatFor(item) },
                        onOverflowAction = { action -> handleOverflow(action, item) },
                    )
                    OfferCard(
                        ui = ui,
                        modifier = Modifier.fillMaxWidth(),
                        photoPeekEnabled = profileSettings.photoPeekEnabled,
                        overflowActions = CardActionRules.offerOverflowActions(
                            includeCopyLink = !item.dto.externalUrl.isNullOrBlank(),
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

    if (sheetScreen != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetScreen = null },
            sheetState = sheetState,
        ) {
            when (sheetScreen) {
                ResultsSheet.Filters -> FilterHubSheet(
                    filters = workingFilters,
                    activeCount = workingFilters.activeFilterCount(),
                    onReset = { workingFilters = workingFilters.resetNonQuery() },
                    onDismiss = { sheetScreen = null },
                    onOpenSort = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Sort
                    },
                    onOpenFormat = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.PurchaseFormat
                    },
                    onOpenCondition = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Condition
                    },
                    onOpenPrice = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Price
                    },
                    onOpenCategory = {
                        sheetTarget = FilterSheetTarget.Draft
                        categoryQuery = ""
                        categoryTreePath = buildCategoryPath(workingFilters.categoryCode, categoriesByCode)
                        sheetScreen = ResultsSheet.Categories
                    },
                    onOpenBrand = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Brands
                    },
                    onOpenLocation = {
                        sheetTarget = FilterSheetTarget.Draft
                        sheetScreen = ResultsSheet.Location
                    },
                    onToggleDelivery = { enabled ->
                        workingFilters = workingFilters.copy(deliverableOnly = enabled)
                    },
                    onApply = {
                        filters = workingFilters
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
                        workingFilters = workingFilters.copy(categoryCode = code, categoryPath = path)
                    },
                    onReset = {
                        workingFilters = workingFilters.copy(categoryCode = null, categoryPath = emptyList())
                        categoryTreePath = emptyList()
                    },
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
                ResultsSheet.Brands -> BrandPickerSheet(
                    query = brandQuery,
                    onQueryChange = { brandQuery = it },
                    availableBrands = availableBrands(items = items, query = filters.query),
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
                    onSelect = { sort ->
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            workingFilters = workingFilters.copy(sort = sort)
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = filters.copy(sort = sort)
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
                ResultsSheet.PurchaseFormat -> PurchaseFormatSheet(
                    current = workingFilters.purchaseFormat,
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
                ResultsSheet.Location -> LocationSheet(
                    current = workingFilters.location,
                    radiusKm = workingFilters.radiusKm,
                    onApply = { location, radius ->
                        workingFilters = workingFilters.copy(location = location, radiusKm = radius)
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            filters = workingFilters
                            sheetScreen = null
                        }
                    },
                    onReset = {
                        workingFilters = workingFilters.copy(location = null, radiusKm = null)
                    },
                    onBack = {
                        if (sheetTarget == FilterSheetTarget.Draft) {
                            sheetScreen = ResultsSheet.Filters
                        } else {
                            sheetScreen = null
                        }
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
    onFilters: () -> Unit,
    onCategories: () -> Unit,
    onSort: () -> Unit,
) {
    val filtersLabel = if (activeFiltersCount > 0) "Фильтры ($activeFiltersCount)" else "Фильтры"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChipButton(label = filtersLabel, onClick = onFilters)
        FilterChipButton(label = "Категория", onClick = onCategories)
        Spacer(modifier = Modifier.weight(1f))
        FilterChipButton(label = "Сортировка", onClick = onSort)
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
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(trackLabel)
                }
            } else {
                Button(
                    onClick = onTrack,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = buttonColors ?: ButtonDefaults.buttonColors(),
                ) {
                    Text(trackLabel)
                }
            }
            if (showCreate) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
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
private fun ResultsSummaryRow(
    category: String,
    brand: String,
    price: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SummaryLine(label = "Категория", value = category)
        SummaryLine(label = "Бренд", value = brand)
        SummaryLine(label = "Цена", value = price)
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterHubSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun FilterHubSheet(
    filters: FilterState,
    activeCount: Int,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenFormat: () -> Unit,
    onOpenCondition: () -> Unit,
    onOpenPrice: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenBrand: () -> Unit,
    onOpenLocation: () -> Unit,
    onToggleDelivery: (Boolean) -> Unit,
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
            onReset = onReset,
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
            item { FilterHubRow("Формат покупки", filters.purchaseFormat.label, onOpenFormat) }
            item { FilterHubRow("Состояние товара", filters.conditionsSummary(), onOpenCondition) }
            item { FilterHubRow("Цена", filters.priceSummary(), onOpenPrice) }
            item { FilterHubRow("Категория", filters.categorySummary(), onOpenCategory) }
            item { FilterHubRow("Бренд", filters.brandSummary(), onOpenBrand) }
            item { FilterHubRow("Где находится / Радиус", filters.locationSummary(), onOpenLocation) }
            item {
                FilterHubSwitchRow(
                    title = "Только с доставкой",
                    summary = if (filters.deliverableOnly) "Только доставка" else "Любая",
                    checked = filters.deliverableOnly,
                    onCheckedChange = onToggleDelivery,
                )
            }
        }
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("Показать результаты")
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
    onReset: () -> Unit,
    onDone: () -> Unit,
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
    val popular = remember(categories) { popularCategories(categories) }

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
        CategorySearchField(
            value = query,
            placeholder = "Поиск по категориям",
            onValueChange = onQueryChange,
        )
        if (!isSearching && popular.isNotEmpty()) {
            Text(
                text = "Популярные",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(popular) { category ->
                    CategoryChip(
                        text = category.title ?: category.code,
                        onClick = {
                            onSelectCategory(category.code, categoryPathTitles(category.code, categoriesByCode))
                        },
                    )
                }
            }
        }
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
                        partial = false,
                        hasChildren = false,
                        onSelect = { onSelectCategory(null, emptyList()) },
                        onOpen = {},
                    )
                }
            }
            items(list, key = { it.code }) { category ->
                val hasChildren = categoriesByParent[category.code].orEmpty().isNotEmpty()
                val isSelected = category.code == selectedCategoryCode
                val isPartial = isAncestor(category.code, selectedCategoryCode, categoriesByParent)
                val subtitle = if (isSearching) {
                    categoryPathTitles(category.code, categoriesByCode).joinToString(" → ")
                } else null
                CategoryOptionRow(
                    title = category.title ?: category.code,
                    subtitle = subtitle,
                    selected = isSelected,
                    partial = isPartial,
                    hasChildren = hasChildren,
                    onSelect = {
                        onSelectCategory(category.code, categoryPathTitles(category.code, categoriesByCode))
                    },
                    onOpen = {
                        if (hasChildren) {
                            onPathChange(pathCodes + category.code)
                        }
                    },
                )
            }
        }
        CategoryActionsRow(
            selectedCount = if (selectedCategoryCode.isNullOrBlank()) 0 else 1,
            onReset = onReset,
            onDone = onDone,
        )
    }
}

@Composable
private fun CategoryOptionRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    partial: Boolean,
    hasChildren: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = onSelect,
            modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp),
        ) {
            val icon = if (selected || partial) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            }
            val tint = when {
                selected -> MaterialTheme.colorScheme.primary
                partial -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasChildren) {
            IconButton(
                onClick = onOpen,
                modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = "Внутрь",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BrandPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    availableBrands: List<String>,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val searchText = query.trim()
    val filtered = if (searchText.isBlank()) {
        availableBrands
    } else {
        availableBrands.filter { it.contains(searchText, ignoreCase = true) }
    }
    val popular = availableBrands.take(8)
    val grouped = filtered.groupBy { it.firstOrNull()?.uppercaseChar() ?: '#' }
        .toSortedMap()

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
            onValueChange = onQueryChange,
        )
        if (searchText.isBlank() && popular.isNotEmpty()) {
            Text(
                text = "Популярные бренды",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(popular) { brand ->
                    CategoryChip(
                        text = brand,
                        onClick = { onSelect(brand) },
                        highlighted = brand in selected,
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
                items(brands, key = { it }) { brand ->
                    BrandRow(
                        brand = brand,
                        selected = brand in selected,
                        onClick = { onSelect(brand) },
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
            text = brand,
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
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("Готово")
        }
    }
}

@Composable
private fun SortPickerSheet(
    current: OfferSort,
    onSelect: (OfferSort) -> Unit,
    onBack: () -> Unit,
) {
    val options = listOf(
        "По релевантности" to OfferSort.RANK,
        "Цена: по возрастанию" to OfferSort.PRICE_ASC,
        "Цена: по убыванию" to OfferSort.PRICE_DESC,
        "По времени: новые" to OfferSort.NEWEST,
        "По расстоянию: ближе" to OfferSort.DELIVERY_ASC,
        "Рейтинг продавца" to OfferSort.RATING_DESC,
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SheetTopBar(title = "Сортировать", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { (label, value) ->
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PurchaseFormatSheet(
    current: PurchaseFormat,
    onSelect: (PurchaseFormat) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(title = "Формат покупки", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PurchaseFormat.values().forEach { option ->
                SegmentedOption(
                    label = option.label,
                    selected = option == current,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
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
            items(ConditionOption.values().toList()) { option ->
                SortOptionRow(
                    label = option.label,
                    selected = option in selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun LocationSheet(
    current: String?,
    radiusKm: Int?,
    onApply: (String?, Int?) -> Unit,
    onReset: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTopBar(title = "Где находится", onBack = onBack, onReset = onReset)
        TextField(
            value = locationText,
            onValueChange = { locationText = it },
            singleLine = true,
            placeholder = { Text("Город или страна") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Радиус",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(radiusOptions) { radius ->
                CategoryChip(
                    text = "$radius км",
                    onClick = { radiusValue = radius },
                    highlighted = radiusValue == radius,
                )
            }
        }
        Button(
            onClick = {
                val trimmed = locationText.trim().ifBlank { null }
                val effectiveRadius = if (trimmed == null) null else radiusValue
                onApply(trimmed, effectiveRadius)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("Готово")
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
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun isAncestor(
    code: String,
    selected: String?,
    categoriesByParent: Map<String?, List<Category>>,
): Boolean {
    if (selected.isNullOrBlank()) return false
    var current: String? = selected
    while (!current.isNullOrBlank()) {
        val parent = categoriesByParent.entries
            .firstOrNull { entry -> entry.value.any { it.code == current } }
            ?.key
        if (parent == code) return true
        current = parent
    }
    return false
}

private fun buildCriteria(filters: FilterState): OfferSearchCriteria? {
    val query = filters.query
    val hasAnyFilter = query != null ||
        !filters.categoryCode.isNullOrBlank() ||
        filters.brands.isNotEmpty() ||
        filters.priceMin != null ||
        filters.priceMax != null ||
        filters.conditions.isNotEmpty() ||
        filters.purchaseFormat != PurchaseFormat.All ||
        !filters.location.isNullOrBlank() ||
        filters.deliverableOnly
    if (!hasAnyFilter) return null
    val locale = Locale.getDefault()
    val safeRadius = filters.radiusKm?.coerceIn(NEARBY_RADIUS_PRESETS.first(), NEARBY_RADIUS_PRESETS.last())
    val resolvedRadius = if (filters.location.isNullOrBlank()) null else safeRadius
    val rawConditions = buildSet {
        filters.conditions.forEach { option -> add(option.value) }
        query?.attributes
            ?.entries
            ?.firstOrNull { (key, _) -> key.equals("condition", ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it) }
    }
    val normalizedConditions = rawConditions
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
    val attrs = buildMap {
        putAll(filters.presetAttributes)
        if (query != null) {
            putAll(query.attributes.filterKeys { key -> !key.equals("condition", ignoreCase = true) })
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
        deliverableOnly = filters.deliverableOnly,
        condition = normalizedConditions.firstOrNull(),
        conditions = normalizedConditions,
        attributes = attrs,
        userCountry = locale.country.takeIf { it.isNotBlank() },
        userLanguage = locale.language.takeIf { it.isNotBlank() },
        limit = 20,
        sort = filters.sort,
    )
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
    val purchaseFormat: PurchaseFormat = PurchaseFormat.All,
    val location: String? = null,
    val radiusKm: Int? = null,
    val deliverableOnly: Boolean = false,
    val sort: OfferSort = OfferSort.RANK,
)

private enum class ResultsSheet {
    Filters,
    Categories,
    Brands,
    Price,
    Sort,
    PurchaseFormat,
    Condition,
    Location,
}

private enum class FilterSheetTarget { Applied, Draft }

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
        purchaseFormat = PurchaseFormat.All,
        location = null,
        radiusKm = null,
        deliverableOnly = false,
        sort = OfferSort.RANK,
    )

private fun FilterState.activeFilterCount(): Int {
    var count = 0
    if (!categoryCode.isNullOrBlank()) count += 1
    if (presetAttributes.isNotEmpty()) count += 1
    if (brands.isNotEmpty()) count += 1
    if (priceMin != null || priceMax != null) count += 1
    if (conditions.isNotEmpty()) count += 1
    if (purchaseFormat != PurchaseFormat.All) count += 1
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

private fun FilterState.locationSummary(): String {
    val locationText = location?.trim().orEmpty()
    if (locationText.isBlank()) return "Любое"
    val safeRadius = radiusKm?.coerceIn(NEARBY_RADIUS_PRESETS.first(), NEARBY_RADIUS_PRESETS.last())
    return safeRadius?.let { "$locationText · $it км" } ?: locationText
}

private fun FilterState.toggleBrand(brand: String): FilterState {
    val updated = brands.toMutableSet()
    if (brand in updated) updated.remove(brand) else updated.add(brand)
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

private fun sortLabel(sort: OfferSort): String = when (sort) {
    OfferSort.RANK -> "По релевантности"
    OfferSort.PRICE_ASC -> "Цена: по возрастанию"
    OfferSort.PRICE_DESC -> "Цена: по убыванию"
    OfferSort.NEWEST -> "По времени: новые"
    OfferSort.DELIVERY_ASC, OfferSort.DISTANCE_ASC -> "По расстоянию: ближе"
    OfferSort.RATING_DESC -> "Рейтинг продавца"
}

private fun availableBrands(
    items: List<ExplainedItem>,
    query: NormalizedQuery?,
): List<String> {
    val out = items.mapNotNull { it.dto.brand?.trim()?.takeIf { value -> value.isNotBlank() } }.toMutableSet()
    query?.brand?.takeIf { it.isNotBlank() }?.let { out.add(it) }
    return out.toList().sortedBy { it.lowercase() }
}

private fun availablePriceBounds(items: List<ExplainedItem>): PriceBounds? {
    val prices = items.mapNotNull { it.dto.price?.toInt() }
    val min = prices.minOrNull() ?: return null
    val max = prices.maxOrNull() ?: return null
    return PriceBounds(min = min, max = max)
}

private fun popularCategories(categories: List<Category>): List<Category> {
    val popularCodes = listOf(
        "TECH",
        "FOOD",
        "FASH",
        "HOME",
        "KIDS",
        "APPL",
        "BEAUTY",
        "TECH.PHONES",
        "TECH.LAPTOPS",
        "FOOD.READY_MEALS",
    )
    val map = categories.associateBy { it.code }
    return popularCodes.mapNotNull { map[it] }.ifEmpty {
        categories.filter { it.parentCode == null }.take(8)
    }
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
    String.format(Locale("ru", "RU"), "%,d ₽", value)

private fun applyFacetPreset(
    base: FilterState,
    collection: FacetCollection?,
    preset: FacetPreset?,
): FilterState {
    if (collection == null && preset == null) return base

    val presetAttributes = LinkedHashMap(base.presetAttributes)
    var nextPriceMin = base.priceMin
    var nextPriceMax = base.priceMax
    val nextBrands = base.brands.toMutableSet()
    val nextConditions = base.conditions.toMutableSet()
    var nextPurchaseFormat = base.purchaseFormat

    preset?.rules?.forEach { rule ->
        val facetKey = rule.facetKey.trim().lowercase()
        if (facetKey.isBlank()) return@forEach

        when (facetKey) {
            "brand" -> {
                rule.includeValues
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { nextBrands.add(it) }
            }

            "condition" -> {
                nextConditions += mapConditionOptions(rule.includeValues.map { it.trim() })
            }

            "price",
            "price_rub",
            -> {
                rule.minValue?.toInt()?.let { min ->
                    nextPriceMin = if (nextPriceMin == null) min else maxOf(nextPriceMin ?: min, min)
                }
                rule.maxValue?.toInt()?.let { max ->
                    nextPriceMax = if (nextPriceMax == null) max else minOf(nextPriceMax ?: max, max)
                }
            }

            "purchase_format",
            "delivery_channel",
            "delivery",
            -> {
                val v = rule.includeValues.firstOrNull()?.trim()?.lowercase().orEmpty()
                nextPurchaseFormat = when (v) {
                    "pickup" -> PurchaseFormat.Pickup
                    "delivery" -> PurchaseFormat.Delivery
                    else -> nextPurchaseFormat
                }
            }

            else -> {
                val includeValue = rule.includeValues.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                if (includeValue != null) {
                    presetAttributes[facetKey] = includeValue
                } else if (rule.boolValue != null) {
                    presetAttributes[facetKey] = rule.boolValue.toString()
                }
            }
        }
    }

    val nextCategoryCode = collection?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        ?: preset?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        ?: base.categoryCode

    return base.copy(
        categoryCode = nextCategoryCode,
        categoryPath = emptyList(),
        facetCollectionCode = collection?.collectionCode ?: base.facetCollectionCode,
        facetPresetCode = preset?.presetCode ?: base.facetPresetCode,
        presetAttributes = presetAttributes,
        brands = nextBrands,
        priceMin = nextPriceMin,
        priceMax = nextPriceMax,
        conditions = nextConditions,
        purchaseFormat = nextPurchaseFormat,
    )
}

private fun buildOfferCardUi(
    item: ExplainedItem,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDetails: () -> Unit,
    onOverflowAction: (OfferOverflowAction) -> Unit,
): OfferCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Offer" }
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
                Text(if (isLoading) "Loading..." else "Load more")
            }
        }
    }
}
